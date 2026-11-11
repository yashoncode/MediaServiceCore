package com.liskovsoft.youtubeapi.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveChatAllMessagesTest {
    @Test fun turnsTopChatIntoLiveChat() {
        // YouTube's own pair from a WEB /next (viewSelector: Top chat, Live chat).
        assertEquals("0ofMyAMWGgAwAYIBDAgBGAAgACgBMAA4AKgBAQ%3D%3D",
            LiveChatApiParams.allMessages("0ofMyAMWGgAwAYIBDAgEGAAgACgBMAA4AKgBAQ%3D%3D"))
    }

    @Test fun leavesOtherKeysAlone() {
        assertEquals("0ofMyAMWGgAwAYIBDAgBGAAgACgBMAA4AKgBAQ%3D%3D",
            LiveChatApiParams.allMessages("0ofMyAMWGgAwAYIBDAgBGAAgACgBMAA4AKgBAQ%3D%3D"))
        assertEquals("not base64 !", LiveChatApiParams.allMessages("not base64 !"))
    }
}
