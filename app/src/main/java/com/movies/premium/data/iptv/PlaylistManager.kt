package com.movies.premium.data.iptv

import android.content.Context
import com.google.gson.Gson
import com.movies.premium.data.local.dao.IptvChannelDao
import com.movies.premium.data.local.entity.IptvChannelEntity
import com.movies.premium.data.providers.PlutoTvProvider
import com.movies.premium.data.remote.model.IptvChannel
import com.movies.premium.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the built-in IPTV playlists and stores them in the database.
 *
 * Mirrors the original's `com.app.mlounge.data.iptv.d0` (R8 name `d0`).
 *
 * Eight plain-M3U providers are scraped from the IPTV-Scraper-Zilla
 * repository, plus the two API-backed providers `plutotv` and `earthcam`.
 * Rows are written in batches of 1000 and the existing rows of a provider are
 * deleted before its playlist is parsed.
 */
@Singleton
class PlaylistManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val channelDao: IptvChannelDao,
) {
    private val gson = Gson()
    private val playlistDir: File = File(context.filesDir, PLAYLISTS_DIR).apply { mkdirs() }

    /** EarthCam provider, shared with the UI. */
    val earthCamProvider: EarthCamProvider = EarthCamProvider(client)

    /** Pluto TV provider, shared with the UI. */
    val plutoTvProvider: PlutoTvProvider = PlutoTvProvider(client)

    /** The channel DAO, exposed so the UI can query without a second injection. */
    val channels: IptvChannelDao get() = channelDao

    /**
     * Downloads and stores the playlist of [slug].
     *
     * Returns the number of channels written, or `0` when the slug is unknown
     * or the download failed.
     */
    suspend fun syncProvider(slug: String): Int {
        val url = PROVIDER_SOURCES[slug] ?: return 0
        return try {
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", PLAYLIST_USER_AGENT)
                    .build(),
            ).execute()
            if (!response.isSuccessful) {
                Logger.e(TAG, "Failed to download M3U for $slug: HTTP ${response.code}")
                return 0
            }
            val stream = response.body?.byteStream()
                ?: throw Exception("Empty response body")
            channelDao.deleteByProvider(slug)
            val batch = ArrayList<IptvChannelEntity>()
            var total = 0
            M3uParser.parse(stream, slug) { ch ->
                batch.add(toEntity(ch))
                total++
                if (batch.size >= BATCH_SIZE) {
                    channelDao.insertAll(ArrayList(batch))
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                channelDao.insertAll(ArrayList(batch))
            }
            total
        } catch (e: Exception) {
            Logger.e(TAG, "Error syncing M3U for $slug: ${e.message}")
            0
        }
    }

    /** Deletes playlist files that no longer belong to a known provider. */
    fun cleanupStalePlaylists() {
        val base = PROVIDER_SOURCES.keys + "plutotv" + "earthcam" + setOf(META_FILE)
        val keep = base.map { "$it.json" }.toSet() +
            setOf("meta.json", "custom_channels.json", "custom_epg.json")
        playlistDir.listFiles()?.forEach { f ->
            if (f.name !in keep) {
                f.delete()
                Logger.d(TAG, "Removed stale playlist: ${f.name}")
            }
        }
    }

    /**
     * Writes `meta.json` with the per-provider channel counts.
     *
     * Shape: `{"lastUpdated": <epochMillis>, "providers": {"<slug>": <count>}}`.
     */
    fun writeMeta(providers: Map<String, Int>) {
        val json = gson.toJson(
            mapOf(
                "lastUpdated" to System.currentTimeMillis(),
                "providers" to providers,
            ),
        )
        File(playlistDir, META_FILE).writeText(json)
    }

    /** Maps a wire channel to a database row. */
    fun toEntity(ch: IptvChannel): IptvChannelEntity = IptvChannelEntity(
        name = ch.name ?: "",
        url = ch.url ?: "",
        logo = ch.logo,
        groupName = ch.group,
        provider = ch.provider ?: "unknown",
        tvgId = ch.tvgId,
    )

    /**
     * Refreshes every provider in parallel and writes the result to
     * `meta.json`.
     */
    suspend fun regenerateAll() {
        Logger.d(TAG, "Regenerating all playlists...")
        supervisorScope {
            val scope = this
            val results = buildList {
                PROVIDER_SOURCES.keys.forEach { slug ->
                    add(scope.async(Dispatchers.IO) { syncOne(slug) })
                }
                add(scope.async(Dispatchers.IO) { regeneratePlutoTv() })
                add(scope.async(Dispatchers.IO) { regenerateEarthCam() })
            }.awaitAll()
            writeMeta(results.toMap())
            cleanupStalePlaylists()
            Logger.d(TAG, "Regeneration complete: ${results.sumOf { it.second }} total channels")
        }
    }

    private suspend fun syncOne(slug: String): Pair<String, Int> = try {
        val n = syncProvider(slug)
        Logger.d(TAG, "$slug: $n channels")
        slug to n
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to regenerate $slug: ${e.message}")
        slug to 0
    }

    private suspend fun regeneratePlutoTv(): Pair<String, Int> = try {
        val channels = plutoTvProvider.fetchChannels()
        if (channels.isNotEmpty()) {
            channelDao.deleteByProvider("plutotv")
            channels.chunked(BATCH_SIZE).forEach { chunk ->
                channelDao.insertAll(chunk.map { toEntity(it) })
            }
        }
        Logger.d(TAG, "plutotv: ${channels.size} channels")
        "plutotv" to channels.size
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to regenerate plutotv: ${e.message}")
        "plutotv" to 0
    }

    private suspend fun regenerateEarthCam(): Pair<String, Int> = try {
        val channels = earthCamProvider.fetchFromApi()
        if (channels.isNotEmpty()) {
            channelDao.deleteByProvider("earthcam")
            channels.chunked(BATCH_SIZE).forEach { chunk ->
                channelDao.insertAll(chunk.map { toEntity(it) })
            }
        }
        Logger.d(TAG, "earthcam: ${channels.size} channels")
        "earthcam" to channels.size
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to regenerate earthcam: ${e.message}")
        "earthcam" to 0
    }

    companion object {
        const val META_FILE = "meta.json"
        const val PLAYLISTS_DIR = "playlists"
        const val SCRAPER_BASE =
            "https://raw.githubusercontent.com/abusaeeidx/IPTV-Scraper-Zilla/refs/heads/main"
        const val TAG = "PlaylistManager"

        private const val BATCH_SIZE = 1000
        private const val PLAYLIST_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"

        /** The eight plain-M3U providers, in the original's declaration order. */
        val PROVIDER_SOURCES: Map<String, String> = mapOf(
            "xumo" to "$SCRAPER_BASE/xumo_playlist.m3u",
            "tubi" to "$SCRAPER_BASE/tubi_playlist.m3u",
            "yupptv" to "$SCRAPER_BASE/Yupptv.m3u",
            "uslocal" to "$SCRAPER_BASE/US_LOCAL.m3u",
            "samsungtv" to "$SCRAPER_BASE/SamsungTVPlus-All.m3u",
            "roku" to "$SCRAPER_BASE/Roku-All.m3u",
            "lgtv" to "$SCRAPER_BASE/LGTV.m3u",
            "iptvorg" to "https://iptv-org.github.io/iptv/index.m3u",
        )
    }
}
