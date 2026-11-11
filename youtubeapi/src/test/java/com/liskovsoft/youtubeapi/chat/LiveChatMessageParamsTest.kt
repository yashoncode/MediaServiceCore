package com.liskovsoft.youtubeapi.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveChatMessageParamsTest {
    @Test fun encodesTheChannelAndVideoEnvelope() {
        // By hand: strings 0a 02 55 43 / 12 02 76 31, nested under field 5 then field 1,
        // followed by varints 2=1 and 3=4. Full hex: 0a0a2a080a0255431202763110011804.
        // Then base64 -> URL-encode -> base64, as YouTube's own send params are.
        val inner = java.util.Base64.getDecoder().decode(LiveChatMessageSender.params("UC", "v1"))
        assertEquals("CgoqCAoCVUMSAnYxEAEYBA%3D%3D", String(inner, Charsets.UTF_8))
    }
}
