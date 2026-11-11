package com.liskovsoft.youtubeapi.community

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * A post's comments, as the WEB client returns them: the rows name a comment by key and the texts
 * arrive beside them as entity mutations. Signed in on the web, they also carry what a new
 * comment or a reply needs (see YouTubeCommunityService.createPostComment). Keys carry the "post:" prefix that routes
 * them here from the comments service.
 */
internal object PostCommentsParser {
    const val KEY_PREFIX = "post:"
    const val DETAIL_PREFIX = "post:detail:"

    /** The token of the comments section on a post's detail page. */
    fun commentsToken(detail: JSONObject): String? {
        var found: String? = null
        walk(detail) { node ->
            val section = node.optJSONObject("itemSectionRenderer")
            if (section?.optString("sectionIdentifier") == "comment-item-section") {
                walk(section) { inner -> token(inner.optJSONObject("continuationItemRenderer"))?.let { found = it } }
            }
        }
        return found
    }

    /** What a reply to a comment needs, by comment id (only on a signed-in page). */
    val replyParams: MutableMap<String, String> = java.util.concurrent.ConcurrentHashMap()

    /** The params of the "Add a comment" box: present when the page was fetched signed in. */
    fun createParams(page: JSONObject): String? {
        var found: String? = null
        walk(page) { node ->
            node.optJSONObject("createCommentEndpoint")?.optString("createCommentParams")
                ?.takeIf { it.isNotEmpty() }?.let { found = it }
        }
        return found
    }

    /** The comment YouTube answers a create with, or one without an id (the panel then just says it's posted). */
    fun created(response: JSONObject, text: String): CommentItem {
        val mutations = response.optJSONObject("frameworkUpdates")?.optJSONObject("entityBatchUpdate")
            ?.optJSONArray("mutations") ?: JSONArray()
        for (i in 0 until mutations.length()) {
            val payload = mutations.optJSONObject(i)?.optJSONObject("payload")?.optJSONObject("commentEntityPayload")
            if (payload != null) return Item(payload, null, null)
        }
        return Item(JSONObject().put("properties", JSONObject().put("content", JSONObject().put("content", text))), null, null)
    }

    fun parse(response: JSONObject): CommentGroup {
        val payloads = HashMap<String, JSONObject>()
        val toolbars = HashMap<String, JSONObject>()
        val mutations = response.optJSONObject("frameworkUpdates")?.optJSONObject("entityBatchUpdate")
            ?.optJSONArray("mutations") ?: JSONArray()
        for (i in 0 until mutations.length()) {
            val payload = mutations.optJSONObject(i)?.optJSONObject("payload")?.optJSONObject("commentEntityPayload")
                ?: continue
            payloads[payload.optString("key")] = payload
        }
        for (i in 0 until mutations.length()) {
            val payload = mutations.optJSONObject(i)?.optJSONObject("payload")
                ?.optJSONObject("engagementToolbarSurfaceEntityPayload") ?: continue
            toolbars[payload.optString("key")] = payload
        }
        val comments = ArrayList<CommentItem>()
        var next: String? = null
        for (name in arrayOf("onResponseReceivedEndpoints", "onResponseReceivedActions")) {
            val actions = response.optJSONArray(name) ?: continue
            for (i in 0 until actions.length()) {
                val action = actions.optJSONObject(i) ?: continue
                val items = (action.optJSONObject("reloadContinuationItemsCommand")
                    ?: action.optJSONObject("appendContinuationItemsAction"))?.optJSONArray("continuationItems") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val thread = item.optJSONObject("commentThreadRenderer")
                    val model = (thread?.optJSONObject("commentViewModel") ?: item).optJSONObject("commentViewModel")
                    if (model != null) {
                        val payload = payloads[model.optString("commentKey")] ?: continue
                        val id = payload.optJSONObject("properties")?.optString("commentId")
                        walk(toolbars[model.optString("toolbarSurfaceKey")]) { node ->
                            node.optJSONObject("createCommentReplyEndpoint")?.optString("createReplyParams")
                                ?.takeIf { it.isNotEmpty() && !id.isNullOrEmpty() }?.let { replyParams[id!!] = it }
                        }
                        val replies = thread?.optJSONObject("replies")?.optJSONObject("commentRepliesRenderer")
                        var repliesToken: String? = null
                        if (replies != null) walk(replies.opt("contents")) { node ->
                            if (repliesToken == null) repliesToken = token(node.optJSONObject("continuationItemRenderer"))
                        }
                        val label = replies?.optJSONObject("viewReplies")?.optJSONObject("buttonRenderer")
                            ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        comments.add(Item(payload, repliesToken, label))
                    } else {
                        token(item.optJSONObject("continuationItemRenderer"))?.let { next = it }
                    }
                }
            }
        }
        return Group(comments, next?.let { KEY_PREFIX + it })
    }

    /** A page's own continuation, or the one behind a "Show more replies" button. */
    private fun token(item: JSONObject?): String? {
        item ?: return null
        val endpoint = item.optJSONObject("continuationEndpoint")
            ?: item.optJSONObject("button")?.optJSONObject("buttonRenderer")?.optJSONObject("command")
        return endpoint?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }
    }

    private fun walk(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                visit(node)
                for (key in node.keys()) walk(node.opt(key), visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }

    private class Group(private val items: List<CommentItem>, private val next: String?) : CommentGroup {
        override fun getComments(): List<CommentItem> = items
        override fun getNextCommentsKey(): String? = next
    }

    private class Item(payload: JSONObject, repliesToken: String?, private val repliesLabel: String?) : CommentItem {
        private val properties = payload.optJSONObject("properties")
        private val author = payload.optJSONObject("author")
        private val toolbar = payload.optJSONObject("toolbar")
        private val repliesKey = repliesToken?.let { KEY_PREFIX + it }

        private fun JSONObject?.text(key: String): String? = this?.optString(key)?.trim()?.takeIf { it.isNotEmpty() }

        override fun getId(): String? = properties.text("commentId")
        override fun getMessage(): String? = properties?.optJSONObject("content").text("content")
        override fun getAuthorName(): String? = author.text("displayName")
        override fun getAuthorPhoto(): String? = author.text("avatarThumbnailUrl")
        override fun getPublishedDate(): String? = properties.text("publishedTime")
        override fun getNestedCommentsKey(): String? = repliesKey
        override fun isLiked(): Boolean = false
        override fun getLikeCount(): String? = toolbar.text("likeCountNotliked")
        override fun getReplyCount(): String? = repliesLabel
        override fun isEmpty(): Boolean = repliesLabel == null || repliesKey == null
    }
}
