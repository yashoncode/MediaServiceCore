package com.liskovsoft.youtubeapi.community

import com.liskovsoft.mediaserviceinterfaces.data.CommunityPost
import com.liskovsoft.mediaserviceinterfaces.data.CommunityPost.*
import com.liskovsoft.mediaserviceinterfaces.data.CommunityPostPage
import org.json.JSONArray
import org.json.JSONObject

internal object CommunityPostParser {
    fun parse(response: JSONObject): CommunityPostPage {
        require(!response.has("error")) { "YouTube could not load posts" }
        val posts = linkedMapOf<String, CommunityPost>()
        var continuation: String? = null

        fun collect(node: Any?) {
            when (node) {
                is JSONObject -> {
                    node.optJSONObject("backstagePostThreadRenderer")?.let { thread ->
                        // Shared posts have a different shape. Skip the whole thread, including
                        // its nested original, rather than showing it as an unrelated duplicate.
                        val post = thread.optJSONObject("post") ?: return
                        if (post.has("sharedPostRenderer")) return
                        post.optJSONObject("backstagePostRenderer")?.let { renderer ->
                            if (!renderer.has("sharedPostRenderer") &&
                                renderer.optJSONObject("backstageAttachment")?.has("sharedPostRenderer") != true) {
                                parsePost(renderer)?.let { posts[it.id] = it }
                            }
                        }
                        return
                    }
                    node.optJSONObject("continuationItemRenderer")?.let {
                        continuation = it.path("continuationEndpoint", "continuationCommand").string("token")
                        return
                    }
                    // Only traverse content containers, never endpoints, menus or attachments.
                    for (key in arrayOf("contents", "items", "sectionListRenderer", "itemSectionRenderer",
                        "richGridRenderer", "richItemRenderer", "content")) collect(node.opt(key))
                }
                is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i))
            }
        }

        val contents = response.optJSONObject("contents")
        val browse = contents?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?: contents?.optJSONObject("singleColumnBrowseResultsRenderer")
        val tabs = browse?.optJSONArray("tabs")
        if (tabs != null) {
            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                if (tab.optBoolean("selected")) collect(tab.opt("content"))
            }
        } else {
            var found = false
            for (key in arrayOf("onResponseReceivedEndpoints", "onResponseReceivedActions")) {
                val actions = response.optJSONArray(key) ?: continue
                for (i in 0 until actions.length()) {
                    val action = actions.optJSONObject(i) ?: continue
                    for (name in arrayOf("appendContinuationItemsAction", "reloadContinuationItemsCommand")) {
                        action.optJSONObject(name)?.let {
                            found = true
                            collect(it.opt("continuationItems"))
                        }
                    }
                }
            }
            require(found) { "Unrecognized posts response" }
        }
        return CommunityPostPage(posts.values.toList(), continuation)
    }

    private fun parsePost(post: JSONObject): CommunityPost? {
        val id = post.string("postId") ?: return null
        val author = post.optJSONObject("authorText")
        val authorRun = author?.optJSONArray("runs")?.optJSONObject(0)
        val buttons = post.path("actionButtons", "commentActionButtonsRenderer")
        val like = buttons.path("likeButton", "toggleButtonRenderer")
        return CommunityPost(id, text(author), authorRun.path("navigationEndpoint", "browseEndpoint").string("browseId"),
            image(post.optJSONObject("authorThumbnail"))?.url, text(post.optJSONObject("publishedTimeText")),
            runs(post.optJSONObject("contentText")), text(post.optJSONObject("voteCount")),
            text(post.path("actionButtons", "commentActionButtonsRenderer", "replyButton", "buttonRenderer", "text")),
            attachment(post.optJSONObject("backstageAttachment")),
            buttons.path("replyButton", "buttonRenderer", "navigationEndpoint", "browseEndpoint").string("params"),
            like?.optBoolean("isToggled") == true,
            like.path("defaultServiceEndpoint", "performCommentActionEndpoint").string("action"),
            like.path("toggledServiceEndpoint", "performCommentActionEndpoint").string("action"))
    }

    private fun runs(value: JSONObject?): List<TextRun> {
        val runs = value?.optJSONArray("runs")
            ?: return value.string("simpleText")?.let { listOf(TextRun(it, null, null, null)) } ?: emptyList()
        return (0 until runs.length()).mapNotNull { i ->
            val run = runs.optJSONObject(i) ?: return@mapNotNull null
            val endpoint = run.optJSONObject("navigationEndpoint")
            TextRun(run.optString("text"), endpoint.path("urlEndpoint").string("url"),
                endpoint.path("watchEndpoint").string("videoId"), endpoint.path("browseEndpoint").string("browseId"))
        }
    }

    private fun attachment(value: JSONObject?): Attachment? {
        value ?: return null
        val images = mutableListOf<Image>()
        image(value.path("backstageImageRenderer", "image"))?.let { images.add(it) }
        val multi = value.path("postMultiImageRenderer")?.optJSONArray("images")
        if (multi != null) for (i in 0 until multi.length()) {
            image(multi.optJSONObject(i).path("backstageImageRenderer", "image"))?.let { images.add(it) }
        }
        if (images.isNotEmpty()) return Attachment(images, null, null)
        value.optJSONObject("videoRenderer")?.let { video ->
            val id = video.string("videoId") ?: return null
            return Attachment(null, AttachedVideo(id, text(video.optJSONObject("title")),
                image(video.optJSONObject("thumbnail"))?.url, text(video.optJSONObject("lengthText")),
                text(video.optJSONObject("ownerText") ?: video.optJSONObject("longBylineText")
                    ?: video.optJSONObject("shortBylineText"))), null)
        }
        value.optJSONObject("pollRenderer")?.let { poll ->
            val choices = poll.optJSONArray("choices") ?: JSONArray()
            return Attachment(null, null, Poll((0 until choices.length()).mapNotNull {
                text(choices.optJSONObject(it)?.optJSONObject("text"))
            }, text(poll.optJSONObject("totalVotes"))))
        }
        return null
    }

    private fun image(value: JSONObject?): Image? {
        val thumbs = value?.optJSONArray("thumbnails") ?: return null
        return (0 until thumbs.length()).mapNotNull {
            val thumb = thumbs.optJSONObject(it) ?: return@mapNotNull null
            val url = thumb.string("url") ?: return@mapNotNull null
            Image(if (url.startsWith("//")) "https:$url" else url, thumb.optInt("width"), thumb.optInt("height"))
        }.let { images ->
            // The smallest one that still fills a phone's width: the largest is the original
            // (3740 px on a real post), which took seconds to fetch and decode per card.
            images.filter { it.width >= 1000 }.minByOrNull { it.width } ?: images.maxByOrNull { it.width }
        }
    }

    private fun text(value: JSONObject?): String? = value.string("simpleText")
        ?: value?.optJSONArray("runs")?.let { runs ->
            (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
                .takeIf { it.isNotEmpty() }
        }

    private fun JSONObject?.string(key: String): String? = this?.optString(key)?.takeIf { it.isNotEmpty() }
    private fun JSONObject?.path(vararg keys: String): JSONObject? {
        var node = this
        for (key in keys) node = node?.optJSONObject(key)
        return node
    }
}
