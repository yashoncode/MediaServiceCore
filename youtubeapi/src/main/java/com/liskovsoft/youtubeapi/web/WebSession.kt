package com.liskovsoft.youtubeapi.web

import com.liskovsoft.googlecommon.common.helpers.RetrofitOkHttpHelper
import com.liskovsoft.googlecommon.common.locale.LocaleManager
import com.liskovsoft.youtubeapi.app.AppService
import com.liskovsoft.youtubeapi.common.helpers.AppClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

/** The WebView owns the only persisted copy. All calls to this bridge must run on a worker. */
interface CookieStore {
    fun get(url: String): String?
    fun put(url: String, setCookie: String)
}

class WebSessionException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { NOT_SIGNED_IN, REJECTED, FAILED }
}

/** Optional WEB credentials, independent of the TV OAuth account. No eager client or WebView work. */
object WebSession {
    private const val ORIGIN = "https://www.youtube.com"

    @Volatile
    @JvmStatic
    var cookieStore: CookieStore? = null

    /** Lets the app discard its UI hint even when the caller has already gone away. */
    @Volatile
    @JvmStatic
    var onRejected: Runnable? = null

    @JvmStatic
    fun authorization(cookieHeader: String?, nowSeconds: Long, origin: String): String? {
        val cookies = cookieHeader.orEmpty().split(';').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator < 1) null else part.substring(0, separator).trim() to
                part.substring(separator + 1).trim()
        }.toMap()
        val sapisid = cookies["SAPISID"]?.takeIf { it.isNotEmpty() }
            ?: cookies["__Secure-3PAPISID"]?.takeIf { it.isNotEmpty() } ?: return null
        fun token(name: String, secret: String): String {
            val bytes = MessageDigest.getInstance("SHA-1")
                .digest("$nowSeconds $secret $origin".toByteArray(Charsets.UTF_8))
            val hex = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
            return "$name ${nowSeconds}_$hex"
        }
        val tokens = mutableListOf(token("SAPISIDHASH", sapisid))
        cookies["__Secure-1PAPISID"]?.takeIf { it.isNotEmpty() }
            ?.let { tokens.add(token("SAPISID1PHASH", it)) }
        cookies["__Secure-3PAPISID"]?.takeIf { it.isNotEmpty() }
            ?.let { tokens.add(token("SAPISID3PHASH", it)) }
        return tokens.joinToString(" ")
    }

    /**
     * Which Google account of the jar speaks: one WebView session holds every account signed in
     * (Google multi-login), and X-Goog-AuthUser picks one by its slot.
     */
    @Volatile
    @JvmStatic
    var authUser: Int = 0

    @JvmStatic
    fun isSignedIn(): Boolean =
        authorization(cookieStore?.get("$ORIGIN/"), System.currentTimeMillis() / 1000, ORIGIN) != null

    @JvmStatic
    @JvmOverloads
    @Throws(WebSessionException::class)
    fun post(endpoint: String, body: JSONObject, user: Int = authUser): JSONObject {
        try {
            // Relative InnerTube paths only: credentials must never leave this origin.
            require(endpoint.matches(Regex("[a-z_]+(?:/[a-z_]+)*")))
            val store = cookieStore ?: throw WebSessionException(
                WebSessionException.Kind.NOT_SIGNED_IN, "Sign in to YouTube to continue.")
            // Sign-out takes the same lock, so a response in flight cannot restore cleared cookies.
            synchronized(store) {
                val cookies = store.get("$ORIGIN/")
                val auth = authorization(cookies, System.currentTimeMillis() / 1000, ORIGIN)
                    ?: throw WebSessionException(WebSessionException.Kind.NOT_SIGNED_IN, "Sign in to YouTube to continue.")
                val locale = LocaleManager.instance()
                val web = AppClient.WEB
                val visitor = runCatching { AppService.instance().visitorData }.getOrNull()
                val client = JSONObject().put("clientName", "WEB").put("clientVersion", web.clientVersion)
                    .put("hl", locale.language).put("gl", locale.country)
                if (!visitor.isNullOrEmpty()) client.put("visitorData", visitor)
                body.put("context", JSONObject().put("client", client))
                val request = Request.Builder().url("$ORIGIN/youtubei/v1/$endpoint")
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("Cookie", cookies!!).header("Authorization", auth)
                    .header("Origin", ORIGIN).header("X-Origin", ORIGIN).header("Referer", "$ORIGIN/")
                    .header("X-Goog-AuthUser", user.toString()).header("X-Youtube-Client-Name", "1")
                    .header("X-Youtube-Client-Version", web.clientVersion).header("User-Agent", web.userAgent)
                    .apply { if (!visitor.isNullOrEmpty()) header("X-Goog-Visitor-Id", visitor) }.build()
                RetrofitOkHttpHelper.client.newCall(request).execute().use { response ->
                    // Redirects can rotate cookies too. Apply them in wire order, newest last.
                    val responses = generateSequence(response) { it.priorResponse }.toList().asReversed()
                    for (received in responses) {
                        received.headers("Set-Cookie").forEach { store.put(received.request.url.toString(), it) }
                    }
                    if (response.code == 401 || response.code == 403) {
                        throw WebSessionException(WebSessionException.Kind.REJECTED, "YouTube rejected the web sign-in.")
                    }
                    if (!response.isSuccessful) {
                        throw WebSessionException(WebSessionException.Kind.FAILED, "YouTube returned HTTP ${response.code}.")
                    }
                    return JSONObject(response.body?.string().orEmpty())
                }
            }
        } catch (e: WebSessionException) {
            if (e.kind == WebSessionException.Kind.REJECTED) runCatching { onRejected?.run() }
            throw e
        } catch (e: Exception) {
            // Do not include exception/request/response contents: they can contain credentials.
            throw WebSessionException(WebSessionException.Kind.FAILED, "Could not reach YouTube.")
        }
    }

    /** Account header only, rather than the other accounts offered in the menu. */
    class WebAccount(@JvmField val name: String, @JvmField val photo: String?)

    @JvmStatic
    @JvmOverloads
    fun accountName(user: Int = authUser): String? = account(user)?.name

    /** The account in that slot: its name and avatar. */
    @JvmStatic
    @JvmOverloads
    fun account(user: Int = authUser): WebAccount? = try {
        findAccount(post("account/account_menu", JSONObject(), user))
    } catch (e: Exception) {
        null
    }

    private fun findAccount(value: Any?): WebAccount? {
        if (value is JSONObject) {
            value.optJSONObject("activeAccountHeaderRenderer")?.let { header ->
                val name = text(header.opt("accountName")) ?: text(header.opt("channelHandle")) ?: return null
                val thumbs = header.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")
                val photo = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
                return WebAccount(name, photo)
            }
            val keys = value.keys()
            while (keys.hasNext()) findAccount(value.opt(keys.next()))?.let { return it }
        } else if (value is JSONArray) {
            for (i in 0 until value.length()) findAccount(value.opt(i))?.let { return it }
        }
        return null
    }

    internal fun text(value: Any?): String? {
        if (value is String) return value.takeIf { it.isNotBlank() }
        if (value !is JSONObject) return null
        value.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = value.optJSONArray("runs") ?: return null
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
            .takeIf { it.isNotBlank() }
    }
}
