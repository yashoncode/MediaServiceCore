package com.liskovsoft.googlecommon.common.helpers

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebAuthorizationInterceptorTest {
    @Test fun rebuiltWebRequestKeepsItsIdentityWithoutAnyTvAccountHeaders() {
        val previous = RetrofitOkHttpHelper.authHeaders.toMap()
        try {
            RetrofitOkHttpHelper.authHeaders.clear()
            RetrofitOkHttpHelper.authHeaders["Authorization"] = "Bearer test-tv-token"
            RetrofitOkHttpHelper.authHeaders["X-Goog-Pageid"] = "test-tv-channel"
            val builder = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("X-Rebuilt", "yes").build())
            }
            val install = RetrofitOkHttpHelper::class.java.getDeclaredMethod("addCommonHeaders", OkHttpClient.Builder::class.java)
            install.isAccessible = true
            install.invoke(RetrofitOkHttpHelper, builder)
            var received: Request? = null
            builder.addInterceptor { chain ->
                received = chain.request()
                // No real network, and no bearer refresh is allowed for this 401.
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_2).code(401)
                    .message("Unauthorized").body("{}".toResponseBody()).build()
            }
            val request = Request.Builder().url("https://www.youtube.com/youtubei/v1/live_chat/send_message")
                .header("Authorization", "SAPISIDHASH test-web-hash")
                .header("Cookie", "SAPISID=test-cookie")
                .header("User-Agent", "test-desktop-chrome").header("Referer", "https://www.youtube.com/").build()
            builder.build().newCall(request).execute().use { assertEquals(401, it.code) }
            assertEquals("yes", received?.header("X-Rebuilt"))
            assertEquals("SAPISIDHASH test-web-hash", received?.header("Authorization"))
            assertEquals("SAPISID=test-cookie", received?.header("Cookie"))
            assertEquals("test-desktop-chrome", received?.header("User-Agent"))
            assertEquals("https://www.youtube.com/", received?.header("Referer"))
            assertNull(received?.header("X-Goog-Pageid"))
            assertEquals("Bearer test-tv-token", RetrofitOkHttpHelper.authHeaders["Authorization"])
        } finally {
            RetrofitOkHttpHelper.authHeaders.clear()
            RetrofitOkHttpHelper.authHeaders.putAll(previous)
        }
    }
}
