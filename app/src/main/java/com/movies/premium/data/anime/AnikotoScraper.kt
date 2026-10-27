package com.movies.premium.data.anime

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.movies.premium.data.remote.model.HiAnimeHome
import com.movies.premium.data.remote.model.HiAnimeItem
import com.movies.premium.data.remote.model.HiAnimeResponse
import com.movies.premium.data.remote.model.HiAnimeSearchData
import com.movies.premium.data.remote.model.HiAnimeServer
import com.movies.premium.data.remote.model.HiAnimeServersData
import com.movies.premium.data.remote.model.HiAnimeSource
import com.movies.premium.data.remote.model.HiAnimeSourcesData
import com.movies.premium.data.remote.model.HiAnimeTrack
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Anikoto scraper.
 *
 * This is the replica of the original's
 * `com.app.mlounge.data.anime.AnikotoScraper`. It talks to the same JSON API
 * (`https://anikoto.ddns.me/api`) with the same browser user agent, and it
 * returns the same models:
 *
 *  * `getHomePage` — the three shelves of the browse screen.
 *  * `searchAnime` — the result of a keyword search.
 *  * `getAnimeEpisodeServers` — the sub and dub servers of one episode.
 *  * `getAnimeEpisodeSources` — the playable url, the subtitle tracks and the
 *    headers of one server.
 *
 * The watch endpoint answers with a server-sent-event stream, so the scraper
 * parses the `data:` lines itself; the original does the same.
 */
@Singleton
class AnikotoScraper @Inject constructor(
    private val client: OkHttpClient,
) {

    private val gson = Gson()

    /** The parsed watch payloads, keyed by `"<slug>|<episode>"`. */
    private val watchCache = HashMap<String, JsonObject>()

    /** The client the watch endpoint is called with; it needs a long timeout. */
    private val watchClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    // --- the home page ----------------------------------------------------------

    /**
     * The three shelves of the browse screen.
     *
     * The API answers with `spotlight`, `latestEpisodes` and `topWeek`; the
     * original falls back to `topDay` when the weekly list is missing. When
     * [type] is given, every shelf is narrowed down to the entries of that
     * type.
     */
    suspend fun getHomePage(type: String?): HiAnimeResponse<HiAnimeHome> = withContext(Dispatchers.IO) {
        try {
            val body = fetch("$API_BASE/home") ?: return@withContext emptyHome()
            val envelope = gson.fromJson(body, Envelope::class.java)
            if (envelope?.ok != true) return@withContext emptyHome()
            val data = envelope.data?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext emptyHome()

            val spotlight = data.getAsJsonArray("spotlight").mapCards()
            val latest = data.getAsJsonArray("latestEpisodes").mapCards()
            val trending = (data.getAsJsonArray("topWeek") ?: data.getAsJsonArray("topDay"))
                .mapCards()

            if (type == null) {
                HiAnimeResponse(HiAnimeHome(trending, latest, spotlight))
            } else {
                val wanted = type.lowercase(Locale.ROOT)
                HiAnimeResponse(
                    HiAnimeHome(
                        trending = trending.filter { it.type?.lowercase(Locale.ROOT) == wanted },
                        latest = latest.filter { it.type?.lowercase(Locale.ROOT) == wanted },
                        spotlight = spotlight.filter { it.type?.lowercase(Locale.ROOT) == wanted },
                    ),
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "getHomePage failed: ${e.message}")
            emptyHome()
        }
    }

    // --- the search -------------------------------------------------------------

    /**
     * The result of a keyword search.
     *
     * The API answers with `data.results`; the original ignores the page and
     * the genre arguments and only narrows the list down to [type] when one is
     * given.
     */
    suspend fun searchAnime(
        query: String,
        page: Int = 1,
        type: String? = null,
        genres: String? = null,
    ): HiAnimeResponse<HiAnimeSearchData> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val body = fetch("$API_BASE/search?keyword=$encoded")
                ?: return@withContext HiAnimeResponse(HiAnimeSearchData(emptyList()))
            val envelope = gson.fromJson(body, Envelope::class.java)
            if (envelope?.ok != true) {
                return@withContext HiAnimeResponse(HiAnimeSearchData(emptyList()))
            }
            val data = envelope.data?.takeIf { it.isJsonObject }?.asJsonObject
            val results = data?.getAsJsonArray("results").mapCards()
            val narrowed = if (!type.isNullOrEmpty() && type != "all") {
                results.filter { it.type.equals(type, ignoreCase = true) }
            } else {
                results
            }
            HiAnimeResponse(HiAnimeSearchData(narrowed))
        } catch (e: Exception) {
            Logger.e(TAG, "searchAnime failed: ${e.message}")
            HiAnimeResponse(HiAnimeSearchData(emptyList()))
        }
    }

    // --- the servers ------------------------------------------------------------

    /**
     * The sub and dub servers of one episode.
     *
     * The episode id is `"<slug>|<number>"`. The original numbers the servers
     * of each list separately, starting at zero, and leaves a list out when it
     * is empty.
     */
    suspend fun getAnimeEpisodeServers(episodeId: String): HiAnimeResponse<HiAnimeServersData> =
        withContext(Dispatchers.IO) {
            try {
                val (slug, number) = parseEpisodeId(episodeId)
                val payload = fetchWatch(slug, number)
                    ?: return@withContext HiAnimeResponse(
                        HiAnimeServersData(episodeId, null, null, null),
                    )
                val servers = payload.getAsJsonArray("servers")
                    ?: return@withContext HiAnimeResponse(
                        HiAnimeServersData(episodeId, null, null, null),
                    )

                val sub = mutableListOf<HiAnimeServer>()
                val dub = mutableListOf<HiAnimeServer>()
                for (element in servers) {
                    if (!element.isJsonObject) continue
                    val row = element.asJsonObject
                    val name = row.get("name")?.takeIf { !it.isJsonNull }?.asString ?: continue
                    when (row.get("type")?.takeIf { !it.isJsonNull }?.asString) {
                        "sub" -> sub.add(HiAnimeServer(name, sub.size))
                        "dub" -> dub.add(HiAnimeServer(name, dub.size))
                    }
                }

                HiAnimeResponse(
                    HiAnimeServersData(
                        episodeId = episodeId,
                        episodeNo = number.toIntOrNull(),
                        sub = sub.ifEmpty { null },
                        dub = dub.ifEmpty { null },
                    ),
                )
            } catch (e: Exception) {
                Logger.e(TAG, "getAnimeEpisodeServers failed: ${e.message}")
                HiAnimeResponse(HiAnimeServersData(episodeId, null, null, null))
            }
        }

    // --- the sources ------------------------------------------------------------

    /**
     * The playable url, the subtitle tracks and the headers of one server.
     *
     * [server] is the name of the server and [category] is `"sub"` or `"dub"`.
     * The original looks the entry up by both, falls back to any entry of the
     * same category and finally to any entry at all.
     */
    suspend fun getAnimeEpisodeSources(
        episodeId: String,
        server: String,
        category: String,
    ): HiAnimeResponse<HiAnimeSourcesData> = withContext(Dispatchers.IO) {
        try {
            val (slug, number) = parseEpisodeId(episodeId)
            val payload = fetchWatch(slug, number)
                ?: return@withContext emptySources()
            val sources = payload.getAsJsonArray("sources")
                ?: return@withContext emptySources()

            val entries = sources.mapNotNull { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject
            }
            val picked = entries.firstOrNull { row ->
                row.string("server") == server && row.string("type") == category
            } ?: entries.firstOrNull { row ->
                (row.string("proxyUrl").isNullOrBlank() == false ||
                    row.string("m3u8").isNullOrBlank() == false) &&
                    row.string("type") == category
            } ?: entries.firstOrNull { row ->
                row.string("proxyUrl").isNullOrBlank() == false ||
                    row.string("m3u8").isNullOrBlank() == false
            } ?: return@withContext emptySources()

            val raw = picked.string("proxyUrl")?.takeIf { it.isNotBlank() }
                ?: picked.string("m3u8")?.takeIf { it.isNotBlank() }
                ?: return@withContext emptySources()
            val url = unwrapProxyUrl(raw)

            // A proxied url carries the referer itself, so the header is only
            // added for a direct link.
            val headers = if (url.contains("/api/proxy") || url.contains("workers.dev")) {
                null
            } else {
                picked.string("referer")?.takeIf { it.isNotBlank() }
                    ?.let { mapOf("Referer" to it) }
            }

            val tracks = mutableListOf<HiAnimeTrack>()
            val trackArray = picked.getAsJsonArray("tracks")
            if (trackArray != null) {
                for (element in trackArray) {
                    if (!element.isJsonObject) continue
                    val row = element.asJsonObject
                    val file = row.string("proxyUrl")?.takeIf { it.isNotBlank() }
                        ?: row.string("file")?.takeIf { it.isNotBlank() }
                        ?: continue
                    tracks.add(
                        HiAnimeTrack(
                            file = unwrapProxyUrl(file),
                            label = row.string("label"),
                            kind = row.string("kind") ?: "captions",
                            isDefault = row.get("default")?.takeIf { !it.isJsonNull }?.asBoolean,
                        ),
                    )
                }
            }

            HiAnimeResponse(
                HiAnimeSourcesData(
                    tracks = tracks,
                    sources = listOf(HiAnimeSource(url)),
                    headers = headers,
                ),
            )
        } catch (e: Exception) {
            Logger.e(TAG, "getAnimeEpisodeSources failed: ${e.message}")
            emptySources()
        }
    }

    // --- the wire ---------------------------------------------------------------

    /**
     * Splits an episode id into its slug and its episode number.
     *
     * The original takes the text before the last `|`; an id without a `|` is
     * the first episode.
     */
    private fun parseEpisodeId(episodeId: String): Pair<String, String> {
        val index = episodeId.lastIndexOf('|')
        return if (index >= 0) {
            episodeId.substring(0, index) to episodeId.substring(index + 1)
        } else {
            episodeId to "1"
        }
    }

    /**
     * The watch payload of one episode.
     *
     * The endpoint answers with a server-sent-event stream; the original also
     * accepts a plain `{ ok, data }` envelope. The result is cached, because
     * the same episode is asked for twice: once for the servers and once for
     * the sources.
     */
    private fun fetchWatch(slug: String, number: String): JsonObject? {
        val key = "$slug|$number"
        synchronized(watchCache) {
            watchCache[key]?.let { return it }
        }

        val request = Request.Builder()
            .url("$API_BASE/watch/$slug?ep=$number")
            .header("User-Agent", USER_AGENT)
            .build()

        val body = try {
            watchClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "fetchWatch failed: ${e.message}")
            return null
        } ?: return null

        val trimmed = body.trim()
        val payload = if (trimmed.startsWith("data:") || trimmed.contains("\"type\":\"episode\"")) {
            parseWatchSse(trimmed)
        } else {
            runCatching {
                val envelope = gson.fromJson(trimmed, Envelope::class.java)
                envelope?.data?.takeIf { it.isJsonObject }?.asJsonObject
            }.getOrNull()
        } ?: return null

        synchronized(watchCache) { watchCache[key] = payload }
        return payload
    }

    /**
     * Reads the server-sent-event stream of the watch endpoint.
     *
     * The stream carries a `servers` event and one `source` event per server.
     */
    private fun parseWatchSse(body: String): JsonObject? {
        return try {
            val sources = JsonArray()
            var servers: JsonArray? = null

            for (rawLine in body.lineSequence()) {
                val line = rawLine.trim()
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                val event = runCatching { gson.fromJson(payload, JsonObject::class.java) }.getOrNull()
                    ?: continue
                when (event.string("type")) {
                    "servers" -> servers = event.getAsJsonArray("servers")
                    "source" -> event.getAsJsonObject("source")?.let { sources.add(it) }
                }
            }

            if (servers == null && sources.size() == 0) return null
            JsonObject().apply {
                servers?.let { add("servers", it) }
                add("sources", sources)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "parseWatchSse failed: ${e.message}")
            null
        }
    }

    /**
     * Unwraps a proxied url.
     *
     * The API sometimes answers with a link to its own proxy, which carries
     * the real url in the `url` query parameter.
     */
    private fun unwrapProxyUrl(url: String): String {
        if ((url.contains("/api/proxy") || url.contains("workers.dev")) && url.contains("url=")) {
            val unwrapped = runCatching { Uri.parse(url).getQueryParameter("url") }.getOrNull()
            if (!unwrapped.isNullOrBlank()) return unwrapped
        }
        return url
    }

    /** A `GET` with the browser user agent; the body, or null on a failure. */
    private fun fetch(url: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    } catch (e: Exception) {
        Logger.e(TAG, "fetch failed: ${e.message}")
        null
    }

    /** Parses the cards of a browse or search result. */
    private fun JsonArray?.mapCards(): List<HiAnimeItem> {
        if (this == null) return emptyList()
        val cards = mutableListOf<HiAnimeItem>()
        for (element in this) {
            if (!element.isJsonObject) continue
            parseCard(element.asJsonObject)?.let { cards.add(it) }
        }
        return cards
    }

    /**
     * Parses one card of a browse or search result.
     *
     * The API is inconsistent about which field carries the title, so the
     * card keeps all of them.
     */
    fun parseCard(obj: JsonObject): HiAnimeItem? {
        val id = obj.string("slug") ?: obj.string("id") ?: return null
        val episodes = obj.getAsJsonObject("episodes")
        return HiAnimeItem(
            id = id,
            name = obj.string("title"),
            title = obj.string("title"),
            poster = obj.string("image"),
            jname = obj.string("titleJp"),
            type = obj.string("type"),
            duration = obj.string("date"),
            rating = obj.string("score"),
            episodes = com.movies.premium.data.remote.model.HiAnimeEpCount(
                sub = episodes?.get("sub")?.takeIf { !it.isJsonNull }?.asInt,
                dub = episodes?.get("dub")?.takeIf { !it.isJsonNull }?.asInt,
            ),
            description = obj.string("synopsis"),
        )
    }

    /** A string field, or null when it is missing or JSON null. */
    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    /** The empty home page the original answers with after a failure. */
    private fun emptyHome(): HiAnimeResponse<HiAnimeHome> =
        HiAnimeResponse(HiAnimeHome(emptyList(), emptyList(), emptyList()))

    /** The empty source payload the original answers with after a failure. */
    private fun emptySources(): HiAnimeResponse<HiAnimeSourcesData> =
        HiAnimeResponse(HiAnimeSourcesData(emptyList(), emptyList(), null))

    /** The `{ ok, data }` envelope of the API. */
    private class Envelope {
        var ok: Boolean? = null
        var data: JsonElement? = null
    }

    companion object {
        private const val TAG = "AnikotoScraper"

        /** The base url of the API. */
        const val API_BASE = "https://anikoto.ddns.me/api"

        /** The browser the original pretends to be. */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"
    }
}
