package com.liskovsoft.youtubeapi.chat

import com.liskovsoft.youtubeapi.common.helpers.PostDataHelper
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.net.URLDecoder

internal object LiveChatApiParams {
    fun getLiveChatQuery(chatKey: String): String {
        val chatData = String.format("\"continuation\":\"%s\"", chatKey)
        return PostDataHelper.createQueryTV(chatData)
    }

    /**
     * The same chat as "Live chat" (every message) instead of "Top chat" (filtered: a busy chat
     * hides most messages, the viewer's own included). YouTube's two continuations differ only in
     * field 16's chat type: 4 = top, 1 = all. Anything unexpected comes back unchanged.
     */
    fun allMessages(chatKey: String): String {
        val bytes = runCatching { URLDecoder.decode(chatKey, "UTF-8").decodeBase64() }.getOrNull()
            ?.toByteArray() ?: return chatKey
        for (i in 0 until bytes.size - 4) {
            // tag of field 16 (length-delimited) = 0x82 0x01, a length, then field 1 varint = 4
            if (bytes[i] == 0x82.toByte() && bytes[i + 1] == 0x01.toByte()
                && bytes[i + 3] == 0x08.toByte() && bytes[i + 4] == 0x04.toByte()) {
                bytes[i + 4] = 0x01
                return bytes.toByteString().base64().replace("=", "%3D")
            }
        }
        return chatKey
    }
}
