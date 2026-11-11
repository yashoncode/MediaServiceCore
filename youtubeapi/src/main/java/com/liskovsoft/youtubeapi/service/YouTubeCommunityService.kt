package com.liskovsoft.youtubeapi.service

import com.liskovsoft.mediaserviceinterfaces.CommunityService
import com.liskovsoft.mediaserviceinterfaces.data.CommunityPostPage
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.youtubeapi.browse.v2.BrowseApiHelper
import com.liskovsoft.youtubeapi.common.helpers.AppClient
import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem
import com.liskovsoft.youtubeapi.community.CommunityPostParser
import com.liskovsoft.youtubeapi.community.PostCommentsParser
import com.liskovsoft.youtubeapi.web.WebSession
import io.reactivex.rxjava3.core.Observable
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object YouTubeCommunityService : CommunityService {
    // WebSession currently requires credentials. Keep public posts on an isolated transport:
    // no TV auth interceptors, WebView cookies, cookie persistence or authenticators.
    private val http by lazy {
        OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES)
            .callTimeout(20, TimeUnit.SECONDS).pingInterval(10, TimeUnit.SECONDS).build()
    }

    override fun getChannelPostsObserve(channelId: String): Observable<CommunityPostPage> = RxHelper.fromCallable {
        var query = BrowseApiHelper.getChannelCommunityQuery(AppClient.WEB, channelId)
        // A channel opened from a link can still be an @handle while its header is loading.
        if (channelId.startsWith("@")) {
            val body = JSONObject().put("url", "https://www.youtube.com/$channelId")
                .put("context", JSONObject(query).getJSONObject("context"))
            val resolved = post("navigation/resolve_url", body.toString()).optJSONObject("endpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")
            if (resolved.isNullOrEmpty()) throw IOException("Could not resolve channel")
            query = BrowseApiHelper.getChannelCommunityQuery(AppClient.WEB, resolved)
        }
        CommunityPostParser.parse(post("browse", query))
    }

    override fun continueChannelPostsObserve(continuation: String): Observable<CommunityPostPage> =
        RxHelper.fromCallable {
            CommunityPostParser.parse(post("browse", BrowseApiHelper.getContinuationQuery(AppClient.WEB, continuation)))
        }

    override fun postActionObserve(action: String): Observable<Void> = RxHelper.fromRunnable {
        WebSession.post("comment/perform_comment_action", JSONObject().put("actions", org.json.JSONArray().put(action)))
    }

    /** A "post:" comments key (see [PostCommentsParser]): a post's first page, or any later one. */
    fun getPostComments(key: String): CommentGroup {
        val web = AppClient.WEB
        val token = if (key.startsWith(PostCommentsParser.DETAIL_PREFIX)) {
            val query = JSONObject(BrowseApiHelper.getContinuationQuery(web, "x")).apply { remove("continuation") }
                .put("browseId", "FEpost_detail").put("params", key.removePrefix(PostCommentsParser.DETAIL_PREFIX))
            PostCommentsParser.commentsToken(post("browse", query.toString())) ?: throw IOException("No comments on this post")
        } else key.removePrefix(PostCommentsParser.KEY_PREFIX)
        val page = post("browse", BrowseApiHelper.getContinuationQuery(web, token))
        if (key.startsWith(PostCommentsParser.DETAIL_PREFIX)) {
            PostCommentsParser.createParams(page)?.let { createParams[key] = it }
        }
        return PostCommentsParser.parse(page)
    }

    /** The "Add a comment" params of each post opened signed in, by its detail key. */
    private val createParams = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** A comment on a post, as the web sign-in's account. [key] is the post's "post:detail:" key. */
    fun createPostComment(key: String, text: String): CommentItem {
        val params = createParams[key] ?: run { getPostComments(key); createParams[key] }
            ?: throw IOException("Sign in to YouTube to comment on posts")
        val response = WebSession.post("comment/create_comment",
            JSONObject().put("createCommentParams", params).put("commentText", text))
        return PostCommentsParser.created(response, text)
    }

    /** A reply to [parentId], one of the comments loaded signed in. */
    fun createPostReply(parentId: String, text: String): CommentItem {
        val params = PostCommentsParser.replyParams[parentId] ?: throw IOException("Reload the comments to reply")
        val response = WebSession.post("comment/create_comment_reply",
            JSONObject().put("createReplyParams", params).put("commentText", text))
        return PostCommentsParser.created(response, text)
    }

    /**
     * With the web sign-in the page is fetched as that account, so it carries the like state and
     * the like action tokens. Any trouble there falls back to the anonymous page.
     */
    private fun post(endpoint: String, query: String): JSONObject {
        if (WebSession.isSignedIn()) {
            try {
                return WebSession.post(endpoint, JSONObject(query))
            } catch (e: Exception) {
                // The public page still works.
            }
        }
        return postAnonymous(endpoint, query)
    }

    /** Both pages share the same anonymous WEB headers and transport. Worker thread only. */
    private fun postAnonymous(endpoint: String, query: String): JSONObject {
        val web = AppClient.WEB
        val request = Request.Builder().url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(query.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Origin", "https://www.youtube.com").header("Referer", "https://www.youtube.com/")
            .header("X-Youtube-Client-Name", "1").header("X-Youtube-Client-Version", web.clientVersion)
            .header("User-Agent", web.userAgent).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not load posts: HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty posts response")
            return JSONObject(body)
        }
    }
}
