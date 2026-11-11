package com.liskovsoft.youtubeapi.chat

import com.google.gson.Gson
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem
import com.liskovsoft.mediaserviceinterfaces.data.LiveChatMessageResult
import com.liskovsoft.youtubeapi.chat.gen.AddChatItemAction
import com.liskovsoft.youtubeapi.chat.gen.LiveChatAction
import com.liskovsoft.youtubeapi.chat.gen.LiveChatTextMessageRendererItem
import com.liskovsoft.youtubeapi.chat.impl.ChatItemImpl
import com.liskovsoft.youtubeapi.web.WebSession
import com.liskovsoft.youtubeapi.web.WebSessionException
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import okio.ByteString.Companion.toByteString
import java.util.UUID

internal object LiveChatMessageSender {
    @JvmStatic
    fun send(videoId: String, channelId: String, text: String): LiveChatMessageResult {
        if (videoId.isBlank() || channelId.isBlank() || text.isBlank() || text.length > 200) {
            throw WebSessionException(WebSessionException.Kind.FAILED, "Chat is not ready or the message is too long.")
        }
        val body = JSONObject().put("params", params(channelId, videoId))
            .put("clientMessageId", UUID.randomUUID().toString())
            .put("richMessage", JSONObject().put("textSegments", JSONArray().put(JSONObject().put("text", text))))
        val response = WebSession.post("live_chat/send_message", body)
        return try {
            parseResponse(response)
        } catch (e: WebSessionException) {
            throw e
        } catch (e: Exception) {
            throw WebSessionException(WebSessionException.Kind.FAILED, "Could not read YouTube's chat response.")
        }
    }

    internal fun parseResponse(root: JSONObject): LiveChatMessageResult {
        var error: String? = null
        var item: ChatItem? = null
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    for (key in listOf("liveChatActionErrorRenderer", "showLiveChatActionErrorAction")) {
                        value.optJSONObject(key)?.let { renderer ->
                            error = WebSession.text(renderer.opt("errorText")) ?: error
                        }
                    }
                    if (item == null && value.has("liveChatTextMessageRenderer")) {
                        // Same renderer and ChatItemImpl used by get_live_chat polling.
                        val renderer = Gson().fromJson(value.toString(), LiveChatTextMessageRendererItem::class.java)
                        item = ChatItemImpl(LiveChatAction(null, AddChatItemAction(renderer)))
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) visit(value.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i))
            }
        }
        visit(root)
        if (error == null && root.has("error")) {
            throw WebSessionException(WebSessionException.Kind.FAILED, "YouTube could not send the message.")
        }
        return LiveChatMessageResult(item, error)
    }

    // ponytail: params are built locally; switch to the sendLiveChatMessageEndpoint params from a
    // signed-in get_live_chat if YouTube starts rejecting these.
    internal fun params(channelId: String, videoId: String): String {
        fun bytes(field: Int, payload: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write((field shl 3) or 2)
            var remaining = payload.size
            while (remaining >= 128) {
                out.write((remaining and 127) or 128)
                remaining = remaining ushr 7
            }
            out.write(remaining)
            out.write(payload)
            return out.toByteArray()
        }
        val ids = bytes(1, channelId.toByteArray(Charsets.UTF_8)) + bytes(2, videoId.toByteArray(Charsets.UTF_8))
        val encoded = bytes(1, bytes(5, ids)) + byteArrayOf(0x10, 0x01, 0x18, 0x04)
        // YouTube wraps the protobuf twice: base64, URL-encode, base64 again (its own params start "Q2lr").
        val inner = java.net.URLEncoder.encode(encoded.toByteString().base64(), "UTF-8")
        return inner.toByteArray(Charsets.UTF_8).toByteString().base64()
    }
}
