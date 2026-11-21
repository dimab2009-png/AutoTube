package dev.example.autotube

import android.content.Context
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Хранилище настроек и токенов (локально на телефоне). */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("autotube", Context.MODE_PRIVATE)
    var clientId: String
        get() = sp.getString("cid", "") ?: ""
        set(v) = sp.edit().putString("cid", v.trim()).apply()
    var clientSecret: String
        get() = sp.getString("csec", "") ?: ""
        set(v) = sp.edit().putString("csec", v.trim()).apply()
    var apiKey: String
        get() = sp.getString("key", "") ?: ""
        set(v) = sp.edit().putString("key", v.trim()).apply()
    var accessToken: String
        get() = sp.getString("at", "") ?: ""
        set(v) = sp.edit().putString("at", v).apply()
    var refreshToken: String
        get() = sp.getString("rt", "") ?: ""
        set(v) = sp.edit().putString("rt", v).apply()
    var expiresAt: Long
        get() = sp.getLong("exp", 0L)
        set(v) = sp.edit().putLong("exp", v).apply()
    fun clearTokens() = sp.edit().remove("at").remove("rt").remove("exp").apply()
}

object Http {
    fun request(url: String, method: String, body: String? = null, token: String? = null): Pair<Int, JSONObject> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 15000
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
        return code to json
    }

    fun form(vararg p: Pair<String, String>): String =
        p.joinToString("&") { URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8") }
}

/** Вход в Google по коду на google.com/device (без SHA-1 и редиректов). */
class YouTubeAuth(private val store: Store) {
    data class DeviceCode(val deviceCode: String, val userCode: String, val url: String, val interval: Int, val expiresIn: Int)

    private val tokenUrl = "https://oauth2.googleapis.com/token"

    fun requestCode(): DeviceCode {
        val (c, j) = Http.request(
            "https://oauth2.googleapis.com/device/code", "POST",
            Http.form("client_id" to store.clientId, "scope" to "https://www.googleapis.com/auth/youtube.readonly")
        )
        if (c !in 200..299) throw IOException(j.optString("error_description", j.optString("error", "HTTP $c")))
        return DeviceCode(
            j.getString("device_code"), j.getString("user_code"),
            j.optString("verification_url", "https://www.google.com/device"),
            j.optInt("interval", 5), j.optInt("expires_in", 1800)
        )
    }

    /** Блокирующий опрос. true — вход выполнен. */
    fun pollToken(d: DeviceCode, isCancelled: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + d.expiresIn * 1000L
        var interval = d.interval
        while (System.currentTimeMillis() < end && !isCancelled()) {
            Thread.sleep(interval * 1000L)
            val (c, j) = Http.request(
                tokenUrl, "POST",
                Http.form(
                    "client_id" to store.clientId, "client_secret" to store.clientSecret,
                    "device_code" to d.deviceCode, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                )
            )
            if (c in 200..299) { save(j); return true }
            val err = j.optString("error")
            if (err == "slow_down") interval += 5 else if (err != "authorization_pending") return false
        }
        return false
    }

    private fun save(j: JSONObject) {
        store.accessToken = j.getString("access_token")
        if (j.has("refresh_token")) store.refreshToken = j.getString("refresh_token")
        store.expiresAt = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000 - 60000
    }

    fun validToken(): String? {
        if (store.accessToken.isNotEmpty() && System.currentTimeMillis() < store.expiresAt) return store.accessToken
        if (store.refreshToken.isEmpty()) return null
        val (c, j) = Http.request(
            tokenUrl, "POST",
            Http.form(
                "client_id" to store.clientId, "client_secret" to store.clientSecret,
                "refresh_token" to store.refreshToken, "grant_type" to "refresh_token"
            )
        )
        if (c !in 200..299) return null
        save(j)
        return store.accessToken
    }

    fun isSignedIn() = store.refreshToken.isNotEmpty() || store.accessToken.isNotEmpty()
    fun signOut() = store.clearTokens()
}

data class Item(val id: String, val title: String, val subtitle: String, val thumb: String, val isPlaylist: Boolean = false)

/** YouTube Data API v3 (официальный API, чтение). Работает с токеном аккаунта или с API-ключом без входа. */
class YouTubeApi(private val auth: YouTubeAuth, private val store: Store) {
    private fun get(path: String, vararg q: Pair<String, String>): JSONObject {
        val token = auth.validToken()
        if (token == null && store.apiKey.isBlank())
            throw IOException("Вставьте ссылку на видео в поиск или добавьте API-ключ / войдите (кнопка «Аккаунт»)")
        val params = q.toList() + (if (token == null) listOf("key" to store.apiKey) else emptyList())
        val (c, j) = Http.request(
            "https://www.googleapis.com/youtube/v3/$path?" + Http.form(*params.toTypedArray()), "GET", null, token
        )
        if (c !in 200..299) throw IOException(j.optJSONObject("error")?.optString("message") ?: "HTTP $c")
        return j
    }

    private fun requireLogin() {
        if (!auth.isSignedIn()) throw IOException("Для этого раздела нужен вход в аккаунт (кнопка «Аккаунт»)")
    }

    private fun thumb(sn: JSONObject) =
        sn.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url") ?: ""

    private fun clean(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()

    private fun parseVideos(arr: JSONArray?): List<Item> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val sn = o.getJSONObject("snippet")
            Item(o.getString("id"), clean(sn.optString("title")), clean(sn.optString("channelTitle")), thumb(sn))
        }
    }

    fun search(query: String): List<Item> {
        val arr = get("search", "part" to "snippet", "type" to "video", "maxResults" to "25", "q" to query)
            .optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optJSONObject("id")?.optString("videoId") ?: return@mapNotNull null
            val sn = o.getJSONObject("snippet")
            Item(id, clean(sn.optString("title")), clean(sn.optString("channelTitle")), thumb(sn))
        }
    }

    fun trending(): List<Item> {
        val region = java.util.Locale.getDefault().country.ifEmpty { "US" }
        return parseVideos(
            get("videos", "part" to "snippet", "chart" to "mostPopular", "regionCode" to region, "maxResults" to "25")
                .optJSONArray("items")
        )
    }

    fun liked(): List<Item> {
        requireLogin()
        return parseVideos(
            get("videos", "part" to "snippet", "myRating" to "like", "maxResults" to "50").optJSONArray("items")
        )
    }

    fun playlists(): List<Item> {
        requireLogin()
        val arr = get("playlists", "part" to "snippet", "mine" to "true", "maxResults" to "50")
            .optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val sn = o.getJSONObject("snippet")
            Item(o.getString("id"), clean(sn.optString("title")), "Плейлист", thumb(sn), true)
        }
    }

    fun playlistVideos(playlistId: String): List<Item> {
        val arr = get("playlistItems", "part" to "snippet", "playlistId" to playlistId, "maxResults" to "50")
            .optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val sn = arr.getJSONObject(i).getJSONObject("snippet")
            val id = sn.optJSONObject("resourceId")?.optString("videoId") ?: return@mapNotNull null
            val title = clean(sn.optString("title"))
            if (title == "Private video" || title == "Deleted video") null
            else Item(id, title, clean(sn.optString("videoOwnerChannelTitle")), thumb(sn))
        }
    }
}
