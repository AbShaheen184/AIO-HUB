package com.movies.premium.data.music

import com.movies.premium.data.remote.model.MusicStreamResult
import com.movies.premium.data.remote.model.MusicTrack
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a catalogue entry into a playable stream URL.
 *
 * The original keeps this behind the three music providers
 * (`com.app.mlounge.data.music.i`): MusicMp3, Deezer and YouTube Music. Only
 * the first two are reproduced here — the YouTube Music provider is built on
 * the NewPipe extractor, which is not published to Maven Central, so the
 * YouTube branch returns nothing and the Deezer provider falls back to
 * Deezer's own 30 second preview, exactly like the original does when the
 * YouTube lookup comes back empty.
 *
 * The provider is picked from the track id, the way the original's
 * `com.app.mlounge.data.repository.c0` does it:
 *
 * | track id          | provider       |
 * |-------------------|----------------|
 * | starts with `http`| YouTube Music  |
 * | starts with `dz:` | Deezer         |
 * | anything else     | MusicMp3       |
 */
@Singleton
class MusicStreamResolver @Inject constructor(
    baseClient: OkHttpClient,
) {

    companion object {
        private const val TAG = "MusicResolver"
        private const val TAG_MUSICMP3 = "MusicMp3Source"
        private const val TAG_DEEZER = "DeezerMusic"

        private const val MUSICMP3_HOME = "https://musicmp3.ru/"
        private const val MUSICMP3_LISTEN = "https://listen.musicmp3.ru/"

        /** The browser the original pretends to be when it scrapes musicmp3.ru. */
        private const val UA_MUSICMP3 =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"

        /** The browser the original pretends to be when it plays a resolved track. */
        const val UA_PLAYER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

        /** The headers the original sends with a music stream. */
        val PLAYER_HEADERS: Map<String, String> = mapOf(
            "User-Agent" to UA_PLAYER,
            "Referer" to "https://musicmp3.ru/",
        )
    }

    /** The cookie jar the original attaches to its MusicMp3 client. */
    private val cookieJar = MemoryCookieJar()

    /** A client that keeps the musicmp3.ru session cookie. */
    private val client: OkHttpClient = baseClient.newBuilder()
        .cookieJar(cookieJar)
        .build()

    /**
     * Resolves a track into a stream URL, or null when the provider cannot
     * produce one.
     */
    suspend fun resolve(track: MusicTrack): MusicStreamResult? = withContext(Dispatchers.IO) {
        try {
            when {
                track.id.startsWith("http") -> resolveYouTubeMusic(track)
                track.id.startsWith("dz:") -> resolveDeezer(track)
                else -> resolveMusicMp3(track)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "resolveTrack failed for ${track.id}: ${e.message}", e)
            null
        }
    }

    // --- YouTube Music ------------------------------------------------------

    /**
     * The original resolves a YouTube Music track with the NewPipe extractor.
     * That extractor is not available here, so the branch reports the gap
     * instead of silently returning a broken url.
     */
    private fun resolveYouTubeMusic(track: MusicTrack): MusicStreamResult? {
        Logger.e(TAG, "YouTube Music is not available in this build: ${track.id}")
        return null
    }

    // --- Deezer -------------------------------------------------------------

    /**
     * Deezer's resolver.
     *
     * The original splits the track's `rel` payload into `title`, `artist` and
     * the preview url, searches YouTube Music for `"title artist"` and only
     * then falls back to the preview. Without the YouTube extractor the
     * preview is used directly.
     */
    private fun resolveDeezer(track: MusicTrack): MusicStreamResult? {
        val parts = (track.rel ?: "").split("\n", limit = 6)
        val title = parts.getOrNull(0).orEmpty()
        val artist = parts.getOrNull(1).orEmpty()
        val preview = parts.getOrNull(2)

        // The original does not search when the track has no title; it goes
        // straight to the preview and logs the empty title.
        if (title.isBlank()) {
            return deezerPreview(preview, title)
        }
        val query = if (artist.isBlank() || artist == "Unknown") title else "$title $artist"
        return deezerPreview(preview, query)
    }

    /** The fallback the original logs as "Falling back to Deezer preview for …". */
    private fun deezerPreview(preview: String?, query: String): MusicStreamResult? {
        if (preview != null && preview.startsWith("http")) {
            Logger.d(TAG_DEEZER, "Falling back to Deezer preview for $query")
            return MusicStreamResult(preview, "mp3")
        }
        Logger.e(TAG_DEEZER, "resolveTrack failed for ${preview.orEmpty()}: no playable source")
        return null
    }

    // --- MusicMp3 -----------------------------------------------------------

    /**
     * The MusicMp3 resolver.
     *
     * musicmp3.ru hands out a `SessionId` cookie; the track id and the tail of
     * that cookie are folded into a hash that becomes the first path segment
     * of the play url. The hash is the original's, byte for byte.
     */
    private fun resolveMusicMp3(track: MusicTrack): MusicStreamResult? {
        val id = track.id
        val rel = track.rel.orEmpty()
        if (id.length < 5) {
            Logger.e(TAG_MUSICMP3, "resolveTrack failed for $id: malformed track id")
            return null
        }

        // The original fetches the home page so the server issues the cookie.
        val request = Request.Builder()
            .url(MUSICMP3_HOME)
            .header("User-Agent", UA_MUSICMP3)
            .header("Referer", MUSICMP3_HOME)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                response.body?.string()
            }
        } catch (e: Exception) {
            Logger.e(TAG_MUSICMP3, "Fetch failed: $MUSICMP3_HOME", e)
        }

        val sessionId = cookieJar.valueFor(MUSICMP3_HOME, "SessionId")
        if (sessionId.length < 8) {
            Logger.e(TAG_MUSICMP3, "resolveTrack failed for $id: no session cookie")
            return null
        }

        val payload = id.substring(5) + sessionId.substring(8)
        val (first, second) = musicMp3Hash(payload)
        val token = String.format(Locale.US, "%08x%08x", first, second)
        return MusicStreamResult(MUSICMP3_LISTEN + token + "/" + rel, "mp3")
    }

    /**
     * The original's MusicMp3 hash.
     *
     * Three 32 bit accumulators, one round per character: `j` mixes the
     * character into the running value, `j3` accumulates the shifted copy and
     * `j2` is a running sum of the character codes. The result is the low 31
     * bits of `j` and of `j3`, printed as two hex words.
     */
    private fun musicMp3Hash(input: String): Pair<Long, Long> {
        var j = 1234554321L
        var j2 = 7L
        var j3 = 305419896L
        for (character in input) {
            val code = character.code.toLong() and 255L
            j = (j xor ((((63L and j) + j2) * code) + (j shl 8))) and 0xFFFFFFFFL
            // The original derives the signed 32 bit value of the shifted
            // accumulator with an explicit branch; the truncation below is
            // the same number.
            val shifted = ((j3 shl 8) and 0xFFFFFFFFL).toInt()
            j3 = (j3 + (j.toInt().toLong() xor shifted.toLong())) and 0xFFFFFFFFL
            j2 += code
        }
        return (j and 0x7FFFFFFFL) to (j3 and 0x7FFFFFFFL)
    }
}

/**
 * A cookie jar that keeps everything in memory.
 *
 * The original uses `androidx.collection.internal.b`, which is the same idea:
 * the session cookie only has to survive until the next request.
 */
private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.removeAll { existing ->
            cookies.any { it.name == existing.name && it.domain == existing.domain }
        }
        this.cookies.addAll(cookies)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.toList()

    /** The value of the named cookie for a url, or an empty string. */
    @Synchronized
    fun valueFor(url: String, name: String): String {
        val httpUrl = url.toHttpUrl()
        return loadForRequest(httpUrl).firstOrNull { it.name == name }?.value.orEmpty()
    }
}
