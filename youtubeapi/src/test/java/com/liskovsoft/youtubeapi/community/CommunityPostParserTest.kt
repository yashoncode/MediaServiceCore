package com.liskovsoft.youtubeapi.community

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CommunityPostParserTest {
    // Trimmed from the anonymous WEB response supplied with the feature request.
    private fun fixture() = JSONObject(javaClass.getResource("/community/community.json")!!.readText())

    private fun items(response: JSONObject): JSONArray = response.getJSONObject("contents")
        .getJSONObject("twoColumnBrowseResultsRenderer").getJSONArray("tabs")
        .getJSONObject(0).getJSONObject("tabRenderer").getJSONObject("content")
        .getJSONObject("sectionListRenderer").getJSONArray("contents")
        .getJSONObject(0).getJSONObject("itemSectionRenderer").getJSONArray("contents")

    private fun page(vararg renderers: JSONObject): JSONObject = JSONObject().put("onResponseReceivedEndpoints",
        JSONArray().put(JSONObject().put("appendContinuationItemsAction",
            JSONObject().put("continuationItems", JSONArray(renderers.toList())))))

    private fun thread(post: JSONObject): JSONObject = JSONObject().put("backstagePostThreadRenderer",
        JSONObject().put("post", JSONObject().put("backstagePostRenderer", post)))

    @Test fun parsesRealImageMultiImageAndVideoPosts() {
        val page = CommunityPostParser.parse(fixture())
        assertEquals(3, page.posts.size)
        val image = page.posts[0]
        assertEquals("UgkxX6gkDxQ0KRucvyG7NLui0LhjHp0CLlfZ", image.id)
        assertEquals("Marques Brownlee", image.author)
        assertEquals("UCBJycsmduvYEL83R_U4JriQ", image.authorChannelId)
        assertTrue(image.avatarUrl.startsWith("https://yt3.googleusercontent.com/"))
        assertEquals("2 years ago (edited)", image.timeText)
        assertEquals("12K", image.likeCountText)
        assertEquals("1K", image.replyCountText)
        assertEquals("Alright, it's that time: ", image.text[0].text)
        assertTrue(image.text[1].url.contains("vote.mkbhd.com"))
        assertNull(image.text[0].url)
        assertEquals(1, image.attachment.images.size)
        assertEquals(1024, image.attachment.images[0].width)
        assertEquals(1024, image.attachment.images[0].height)
        assertTrue(image.attachment.images[0].url.contains("=s1024-"))

        val multi = page.posts[1].attachment.images
        assertEquals(4, multi.size)
        assertEquals(listOf(1024, 1024, 1024, 1024), multi.map { it.width })
        assertEquals(4, multi.map { it.url }.distinct().size)
        val video = page.posts[2].attachment.video
        assertEquals("as_kxJn4Xwc", video.id)
        assertEquals("Can an iPad Control Our Camera Robot?", video.title)
        assertTrue(video.thumbnailUrl.contains("/vi/as_kxJn4Xwc/"))
        assertEquals("18:36", video.lengthText)
        assertEquals("The Studio", video.author)
        assertEquals("4qmFsgKBARIYVUNCSnljc21kdXZZRUw4M1JfVTRKcmlRGkxFZ1Z3YjNOMGM2b0RLQW9rVVRKak5WSkdVa1ZYYkdSb1ZtNUNlVlZxUWxkVGJVWjBVbTV3YUdGclJsSlJWVVU5S0FqeUJnUUtBa29BmgIWYmFja3N0YWdlLWl0ZW0tc2VjdGlvbg%3D%3D", page.nextPageToken)
    }

    @Test fun continuationUsesTheSamePostShapeAndDoesNotReadHeaderTokens() {
        val initial = fixture()
        val response = page(*(0 until items(initial).length()).map { items(initial).getJSONObject(it) }.toTypedArray())
        response.put("header", JSONObject().put("continuationItemRenderer", JSONObject().put("continuationEndpoint",
            JSONObject().put("continuationCommand", JSONObject().put("token", "unrelated-header-token")))))
        val parsed = CommunityPostParser.parse(response)
        assertEquals(3, parsed.posts.size)
        assertEquals(CommunityPostParser.parse(initial).nextPageToken, parsed.nextPageToken)
    }

    @Test fun onlySelectedTabContributesPosts() {
        val response = fixture()
        val tabs = response.getJSONObject("contents").getJSONObject("twoColumnBrowseResultsRenderer").getJSONArray("tabs")
        val other = fixture()
        val unselected = other.getJSONObject("contents").getJSONObject("twoColumnBrowseResultsRenderer")
            .getJSONArray("tabs").getJSONObject(0)
        unselected.getJSONObject("tabRenderer").put("selected", false)
        items(other).getJSONObject(0).getJSONObject("backstagePostThreadRenderer")
            .getJSONObject("post").getJSONObject("backstagePostRenderer").put("postId", "unselected-post")
        tabs.put(unselected)
        val parsed = CommunityPostParser.parse(response)
        assertEquals(3, parsed.posts.size)
        assertFalse(parsed.posts.any { it.id == "unselected-post" })
    }

    @Test fun parsesTextOnlyPollAndEndpointLinksWithoutDependingOnOptionalFields() {
        val plain = JSONObject("""{"postId":"text","contentText":{"runs":[
            {"text":"Watch","navigationEndpoint":{"watchEndpoint":{"videoId":"video"}}},
            {"text":"channel","navigationEndpoint":{"browseEndpoint":{"browseId":"UCchannel"}}},
            {"text":"site","navigationEndpoint":{"urlEndpoint":{"url":"https://example.com"}}}]}}""")
        val poll = JSONObject("""{"postId":"poll","backstageAttachment":{"pollRenderer":{
            "choices":[{"text":{"runs":[{"text":"First "},{"text":"choice"}]}},{"text":{"runs":[{"text":"Second"}]}}],
            "totalVotes":{"simpleText":"10K votes"}}}}""")
        val posts = CommunityPostParser.parse(page(thread(plain), thread(poll))).posts
        assertNull(posts[0].attachment)
        assertNull(posts[0].avatarUrl)
        assertNull(posts[0].likeCountText)
        assertEquals("video", posts[0].text[0].videoId)
        assertEquals("UCchannel", posts[0].text[1].channelId)
        assertEquals("https://example.com", posts[0].text[2].url)
        assertEquals(listOf("First choice", "Second"), posts[1].attachment.poll.choices)
        assertEquals("10K votes", posts[1].attachment.poll.totalVotesText)
    }

    @Test fun skipsRepostsAndKeepsTheirFollowingContinuation() {
        val response = page(JSONObject("""{"backstagePostThreadRenderer":{"post":{"sharedPostRenderer":{
            "originalPost":{"backstagePostThreadRenderer":{"post":{"backstagePostRenderer":{"postId":"original"}}}}}}}}"""),
            JSONObject("""{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next"}}}}"""))
        val parsed = CommunityPostParser.parse(response)
        assertTrue(parsed.posts.isEmpty())
        assertEquals("next", parsed.nextPageToken)
    }

    @Test fun emptySelectedTabIsAValidEmptyPage() {
        val response = fixture()
        items(response).let { while (it.length() > 0) it.remove(0) }
        val parsed = CommunityPostParser.parse(response)
        assertTrue(parsed.posts.isEmpty())
        assertNull(parsed.nextPageToken)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnrecognizedResponseInsteadOfShowingNoPosts() {
        CommunityPostParser.parse(JSONObject())
    }
}
