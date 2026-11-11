package com.liskovsoft.youtubeapi.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveChatMessageResponseTest {
    @Test fun parsesTheSentItemWithThePollingRenderer() {
        val response = JSONObject("""{"actions":[{"addChatItemAction":{"item":{
            "liveChatTextMessageRenderer":{"id":"sent-1","authorName":{"simpleText":"Ada"},
            "message":{"runs":[{"text":"Hello "},{"text":"chat"}]}}
        }}}]}""")
        val result = LiveChatMessageSender.parseResponse(response)
        assertTrue(result.isSuccess)
        assertEquals("sent-1", result.item?.id)
        assertEquals("Ada", result.item?.authorName)
        assertEquals("Hello chat", result.item?.message)
    }

    @Test fun acceptsSuccessWithoutARenderer() {
        val result = LiveChatMessageSender.parseResponse(JSONObject("""{"actions":[]}"""))
        assertTrue(result.isSuccess)
        assertNull(result.item)
    }

    @Test fun returnsTheRuleErrorFromEitherActionShape() {
        for (key in listOf("liveChatActionErrorRenderer", "showLiveChatActionErrorAction")) {
            val response = JSONObject("""{"actions":[{"$key":{"errorText":{"runs":[
                {"text":"Wait before sending again."}]}}}]}""")
            val result = LiveChatMessageSender.parseResponse(response)
            assertFalse(result.isSuccess)
            assertEquals("Wait before sending again.", result.error)
        }
    }
}
