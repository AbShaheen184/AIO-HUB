package com.movies.premium.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.movies.premium.data.remote.model.EpornerSearchDetails
import com.movies.premium.data.remote.model.EpornerSourceDetail
import com.movies.premium.data.remote.model.EpornerVideo
import com.movies.premium.data.remote.model.EpornerVideoDetail
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Eporner catalogue behind the original's adult section.
 *
 * Mirrors the original's `com.app.mlounge.data.providers.c`:
 *
 * * the search endpoint is `api/v2/video/search/` with `per_page=30`,
 * * the detail endpoint is `api/v2/video/id/`,
 * * and the stream is resolved by reading the player hash out of the embed page
 *   and replaying the site's own XHR call.
 */
@Singleton
class AdultRepository @Inject constructor(
    okHttpClient: OkHttpClient,
) {

    private val client = okHttpClient.newBuilder()
        .cookieJar(JavaNetCookieJar())
        .build()
    private val gson = Gson()

    companion object {
        private const val TAG = "EpornerApi"
        private const val API_BASE = "https://www.eporner.com/api/v2"
        private const val SITE = "https://www.eporner.com"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
        private const val PER_PAGE = 30

        /** The player hash the site embeds in its player page. */
        val HASH_REGEX = Regex("EP\\.video\\.player\\.hash\\s*=\\s*'([a-f0-9]+)'")
    }

    /** Searches the catalogue. [query] may be empty (the original then lists everything). */
    suspend fun search(query: String, page: Int = 1): Result<EpornerSearchDetails> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$API_BASE/video/search/?query=${encode(query)}" +
                    "&per_page=$PER_PAGE&page=$page&thumbsize=big&order=&format=json"
                val body = get(url, null, null) ?: throw IllegalStateException("No response")
                gson.fromJson(body, EpornerSearchDetails::class.java)
            }.onFailure { Logger.e(TAG, "search failed", it) }
        }

    /** Loads one video's metadata. */
    suspend fun videoDetail(id: String): Result<EpornerVideoDetail> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$API_BASE/video/id/?id=${encode(id)}&thumbsize=big&format=json"
                val body = get(url, null, null) ?: throw IllegalStateException("No response")
                val root = JsonParser.parseString(body).asJsonObject
                val videos = root.getAsJsonArray("videos")
                if (videos == null || videos.size() == 0) throw IllegalStateException("Not found")
                gson.fromJson(videos[0], EpornerVideoDetail::class.java)
            }.onFailure { Logger.e(TAG, "videoDetail failed", it) }
        }

    /**
     * Resolves the playable sources of a video.
     *
     * The original warms the session up on the site, reads the 32-character player
     * hash from the embed page, converts it into the query hash the site expects
     * (four 8-hex-digit chunks, each as base-36) and replays the site's XHR call.
     */
    suspend fun resolve(id: String): Result<List<EpornerSourceDetail>> =
        withContext(Dispatchers.IO) {
            runCatching {
                get("$SITE/", null, null)

                val embedUrl = "$SITE/embed/$id/"
                val embed = get(embedUrl, null, null) ?: throw IllegalStateException("No embed page")

                val hash = HASH_REGEX.find(embed)?.groupValues?.getOrNull(1)
                if (hash == null || hash.length != 32) {
                    throw IllegalStateException("Player hash not found")
                }
                val queryHash = buildString {
                    for (chunk in 0 until 4) {
                        val start = chunk * 8
                        val value = hash.substring(start, start + 8).toLong(16)
                        append(value.toString(36))
                    }
                }

                val xhr = "$SITE/xhr/video/$id/?hash=$queryHash" +
                    "&domain=www.eporner.com&pixelRatio=1&playerWidth=0&playerHeight=0" +
                    "&fallback=false&embed=true&supportedFormats=hls,dash,h265,vp9,av1,mp4" +
                    "&_=${System.currentTimeMillis()}"
                val body = get(
                    xhr,
                    embedUrl,
                    mapOf(
                        "Accept" to "application/json, text/plain, */*",
                        "Origin" to SITE,
                        "X-Requested-With" to "XMLHttpRequest",
                    ),
                ) ?: throw IllegalStateException("No source response")

                val root = JsonParser.parseString(body).asJsonObject
                val sources = root.getAsJsonObject("sources") ?: throw IllegalStateException("No sources")
                val result = mutableListOf<EpornerSourceDetail>()
                for (format in listOf("mp4", "hls")) {
                    val group = sources.getAsJsonObject(format) ?: continue
                    for ((key, value) in group.entrySet()) {
                        val entry = value as? JsonObject ?: continue
                        result += EpornerSourceDetail(
                            labelShort = entry.get("labelShort")?.asString ?: key,
                            src = entry.get("src")?.asString,
                        )
                    }
                }
                result.filter { !it.src.isNullOrBlank() }
            }.onFailure { Logger.e(TAG, "resolve failed", it) }
        }

    /** Performs a GET and returns the body, or null when the request failed. */
    private fun get(
        url: String,
        referer: String?,
        extraHeaders: Map<String, String>?,
    ): String? = runCatching {
        val request = Request.Builder().url(url).apply {
            header("User-Agent", UA)
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9," +
                "application/json;q=0.8,*/*;q=0.7")
            header("Accept-Language", "en-US,en;q=0.5")
            header("Referer", referer ?: SITE)
            extraHeaders?.forEach { (name, value) -> header(name, value) }
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }.getOrNull()

    private fun encode(value: String): String =
        URLEncoder.encode(value.ifBlank { " " }, "UTF-8")
}

/**
 * A minimal cookie jar so the session cookies Eporner sets on the first request are
 * replayed on the follow-up calls (the original does the same with its own jar).
 */
private class JavaNetCookieJar : okhttp3.CookieJar {
    private val store = mutableMapOf<String, okhttp3.Cookie>()

    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        synchronized(store) {
            for (cookie in cookies) {
                store[cookie.name] = cookie
            }
        }
    }

    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
        synchronized(store) {
            store.values.filter { it.matches(url) }
        }
}
