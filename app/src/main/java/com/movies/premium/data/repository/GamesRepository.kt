package com.movies.premium.data.repository

import com.movies.premium.data.remote.api.ChqApi
import com.movies.premium.data.remote.model.Game
import com.movies.premium.data.remote.model.GameDetailResponse
import com.movies.premium.data.remote.model.GamePlatform
import com.movies.premium.data.remote.model.GameStatsResponse
import com.movies.premium.data.remote.model.GamesResponse
import com.movies.premium.util.Logger
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Access to the CHQ retro-games catalogue and ROM downloads. */
@Singleton
class GamesRepository @Inject constructor(
    private val api: ChqApi,
) {

    /** How many games the catalogue answers with per page. */
    private companion object {
        const val PAGE_SIZE = 30
    }
    /**
     * The statistics of one platform: the genres and the decades its catalogue
     * holds, and how many games there are in each of them.
     *
     * The original loads them from `api/games/{platform}/stats` when the
     * platform changes and puts the two lists into the genre and the decade
     * dropdown of the games screen.
     */
    suspend fun getStats(platform: String): Result<GameStatsResponse> =
        runCatching { api.getGameStats(platform) }
            .onFailure { Logger.e("GamesRepo", "getStats failed", it) }

    /**
     * One page of the catalogue.
     *
     * The original asks the backend for the page with the genre and the decade
     * the user picked, so the filtering happens on the server.
     */
    suspend fun getGames(
        platform: String,
        page: Int = 1,
        genre: String? = null,
        decade: String? = null,
    ): Result<GamesResponse> =
        runCatching { api.getGames(platform, page, PAGE_SIZE, genre, decade) }
            .onFailure { Logger.e("GamesRepo", "getGames failed", it) }

    suspend fun getGameDetail(platform: String, slug: String): Result<GameDetailResponse> =
        runCatching { api.getGameDetail(platform, slug) }
            .onFailure { Logger.e("GamesRepo", "getGameDetail failed", it) }

    suspend fun searchGames(platform: String, query: String): Result<List<Game>> =
        runCatching { api.searchGames(platform, query).games.orEmpty() }
            .onFailure { Logger.e("GamesRepo", "searchGames failed", it) }

    /**
     * Downloads the ROM for [slug] into [target].
     *
     * The original fetches `api/games/{platform}/rom/{slug}` and writes the bytes to
     * `filesDir/roms/<slug><extension>` — the same path `EmulatorActivity` is later
     * pointed at with the `rom_path` extra.
     *
     * @param onProgress receives (bytes written, total bytes or -1 when unknown).
     * @return the number of bytes written.
     */
    suspend fun downloadRom(
        platform: String,
        slug: String,
        target: File,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<Long> =
        runCatching {
            val response = api.downloadRom(platform, slug)
            if (!response.isSuccessful) throw IOException("HTTP ${response.code()}")
            val body = response.body() ?: throw IOException("Empty ROM body")

            val total = body.contentLength()
            var written = 0L
            target.parentFile?.mkdirs()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                }
            }
            Logger.i("GamesRepo", "Downloaded ROM $slug (${written} bytes)")
            written
        }.onFailure { Logger.e("GamesRepo", "downloadRom failed", it) }

    /** The on-disk location of a downloaded ROM, as the original lays it out. */
    fun romFile(romsDir: File, slug: String, platform: GamePlatform): File =
        File(romsDir, slug + platform.romExtension)

    /** True when the ROM for [slug] is already on disk — the original's `isDownloaded`. */
    fun isRomDownloaded(romsDir: File, slug: String, platform: GamePlatform): Boolean =
        romFile(romsDir, slug, platform).exists()
}
