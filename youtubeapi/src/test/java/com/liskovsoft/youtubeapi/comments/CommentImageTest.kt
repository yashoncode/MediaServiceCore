package com.liskovsoft.youtubeapi.comments

import com.google.gson.Gson
import com.liskovsoft.youtubeapi.comments.gen.CommentItemWrapper
import com.liskovsoft.youtubeapi.comments.impl.CommentItemImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MEETUBE(issue #190): a GIF comment has an empty contentText and its GIF in imageAttachmentViewModel.
 * Shape trimmed from a real TVHTML5 comments reply (dQw4w9WgXcQ, 2026-11-04).
 */
class CommentImageTest {
    private fun comment(extra: String) = CommentItemImpl(Gson().fromJson("""
        {"commentThreadRenderer": {"comment": {"commentRenderer": {
            "commentId": "UgzvznMAXym9kanIhnZ4AaABAg",
            "authorText": {"simpleText": "@someone"},
            "contentText": {}$extra
        }}}}
    """.trimIndent(), CommentItemWrapper::class.java))

    @Test
    fun aGifCommentCarriesItsImage() {
        val item = comment(""",
            "imageAttachmentViewModel": {"commentImageAttachmentViewModel": {
                "width": 498, "height": 279, "aspectRatio": 1.7849462,
                "image": {"sources": [{"url": "https://i.ytimg.com/c_exp/UgzvznMAXym9kanIhnZ4AaABAg/IMd-eCTg6JHcs_webp_animated_original_aspect_ratio.webp?sqp=CN2SrNcGGOCoAQ==&rs=AOn4CLDho92EEseUkUAWME4_ebYVGojj3g", "width": 498, "height": 279}]},
                "externalImageId": "IMd-eCTg6JHcs"
            }}""")

        assertEquals("https://i.ytimg.com/c_exp/UgzvznMAXym9kanIhnZ4AaABAg/IMd-eCTg6JHcs_webp_animated_original_aspect_ratio.webp?sqp=CN2SrNcGGOCoAQ==&rs=AOn4CLDho92EEseUkUAWME4_ebYVGojj3g",
            item.imageUrl)
        assertEquals(1.785f, item.imageAspectRatio, 0.001f)
    }

    @Test
    fun aTextCommentHasNone() {
        val item = comment("")

        assertNull(item.imageUrl)
        assertEquals(0f, item.imageAspectRatio, 0f)
    }
}
