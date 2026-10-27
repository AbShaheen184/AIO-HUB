package com.movies.premium.ui.viewmodel

import com.movies.premium.data.remote.model.ChqStream
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Checks whether a source is reachable and how big it is.
 *
 * This is the replica of the original's probe, which lives in
 * `com.app.mlounge.ui.viewmodel.p2`:
 *
 *  * `p2.e` — the *alive* check. It first decides whether the source has to be
 *    probed at all (torrents, magnets, HLS playlists and a list of known-good
 *    CDNs are taken at face value), and otherwise sends a `HEAD` — or a
 *    `GET` with `Range: bytes=0-0` for the hosts that reject `HEAD` — and
 *    decides from the status code whether the source is alive.
 *  * `p2.f` — the *size* lookup. A second `HEAD` reads the `Content-Length`.
 *
 * The original runs the two behind two independent `Semaphore(4)` gates, so at
 * most four checks and four size lookups are in flight at a time. The replica
 * does the same.
 */
class SourceProbe(private val client: OkHttpClient) {

    /** Gates the alive check. The original's `p2.A`. */
    private val checkSemaphore = Semaphore(4)

    /** Gates the size lookup. The original's `p2.B`. */
    private val sizeSemaphore = Semaphore(4)

    /**
     * True when the source is reachable.
     *
     * The original's `p2.e` returns `true` for a source it does not need to
     * probe, and for a source whose probe succeeded.
     */
    suspend fun isAlive(stream: ChqStream): Boolean {
        val url = stream.url.orEmpty()

        // A torrent, a magnet or anything that is not an http link cannot be
        // checked with a request — the original takes them at face value.
        if (stream.isTorrent || stream.streamType == "torrent" ||
            url.startsWith("magnet:") || !url.startsWith("http")
        ) {
            return true
        }

        // HLS playlists are checked by the player, not by a request.
        if (stream.type == "hls" || stream.streamType == "hls" || url.contains(".m3u8")) {
            return true
        }

        // The hosts below always answer, so the original skips the request.
        if (FAST_HOSTS.any { url.contains(it) }) {
            Logger.d(TAG, "Source check [${stream.name}] skipped (CDN fast-path) ${url.takeLast(60)}")
            return true
        }

        return checkSemaphore.withPermit {
            try {
                withContext(Dispatchers.IO) { probeAlive(stream) }
            } catch (e: Exception) {
                Logger.d(TAG, "Source check failed [${stream.name}]: ${e.message}")
                false
            }
        }
    }

    /**
     * The size of the source in bytes, or null when the server does not report
     * one.
     *
     * The original's `p2.f` skips the two hosts that answer a `HEAD` with a
     * body instead of the headers.
     */
    suspend fun sizeOf(stream: ChqStream): Long? {
        val url = stream.url.orEmpty()
        if (url.contains("googleusercontent.com") || url.contains(".workers.dev")) return null

        return sizeSemaphore.withPermit {
            try {
                withContext(Dispatchers.IO) { fetchSize(stream) }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * True when an anime source is reachable.
     *
     * The original probes the anime sources with a simpler rule than the one it
     * uses for the provider streams: it only sends a request when the url is an
     * http one that is neither a `googleusercontent.com` link, nor a `.m3u8`
     * playlist, nor a `.workers.dev` proxy. Everything else is taken at face
     * value.
     *
     * The request is a `HEAD`; a source is alive when the server answers with a
     * 2xx or a 3xx code, or with `405 Method Not Allowed`.
     */
    suspend fun isAliveAnime(stream: ChqStream): Boolean {
        val url = stream.url.orEmpty()
        if (!url.startsWith("http")) return true
        if (url.contains("googleusercontent.com") ||
            url.contains(".m3u8") ||
            url.contains(".workers.dev")
        ) {
            return true
        }

        return checkSemaphore.withPermit {
            try {
                withContext(Dispatchers.IO) {
                    val builder = Request.Builder().url(url).head()
                    stream.headers?.forEach { (key, value) -> builder.addHeader(key, value) }
                    client.newCall(builder.build()).execute().use { response ->
                        val code = response.code
                        val alive = (code in 200..399) || code == 405
                        Logger.d(TAG, "Anime check [${stream.name}] HTTP $code alive=$alive")
                        alive
                    }
                }
            } catch (e: Exception) {
                Logger.d(TAG, "Anime check failed [${stream.name}]: ${e.message}")
                false
            }
        }
    }

    /**
     * The request the original's `i2` sends.
     *
     * Cloudflare's R2, S3 and the signed urls reject a `HEAD`, so the original
     * asks for the first byte instead. A source is alive when the server
     * answers with a 2xx or a 3xx code, or with `405 Method Not Allowed` — the
     * latter means the file is there but the verb is not allowed.
     */
    private fun probeAlive(stream: ChqStream): Boolean {
        val url = stream.url.orEmpty()
        val builder = Request.Builder().url(url)
        if (url.contains("r2.cloudflarestorage.com") ||
            url.contains(".amazonaws.com") ||
            url.contains("X-Amz-Signature=")
        ) {
            builder.header("Range", "bytes=0-0")
        } else {
            builder.head()
        }
        stream.headers?.forEach { (key, value) -> builder.addHeader(key, value) }

        client.newCall(builder.build()).execute().use { response ->
            val code = response.code
            val alive = (code in 200..399) || code == 405
            Logger.d(TAG, "Source check [${stream.name}] HTTP $code ${url.takeLast(60)} alive=$alive")
            return alive
        }
    }

    /** The `Content-Length` of the source, or null. The original's `p2.f`. */
    private fun fetchSize(stream: ChqStream): Long? {
        val builder = Request.Builder().url(stream.url.orEmpty()).head()
        stream.headers?.forEach { (key, value) -> builder.addHeader(key, value) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val raw = response.header("Content-Length") ?: return null
            val bytes = raw.toLongOrNull() ?: return null
            return if (bytes > 0) bytes else null
        }
    }

    companion object {
        private const val TAG = "SourcesViewModel"

        /**
         * The hosts the original never probes.
         *
         * They answer every request, so a probe would only waste the user's
         * data.
         */
        private val FAST_HOSTS = listOf(
            "googleusercontent.com",
            ".workers.dev",
            "freehandyflix.online",
            "nexabloom.top",
            "norami.top",
            "mikora.top",
            "vyrnex.top",
            "zhaevor.top",
            "megaplay.buzz",
        )
    }
}
