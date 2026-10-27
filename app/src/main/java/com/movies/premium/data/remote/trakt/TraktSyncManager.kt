package com.movies.premium.data.remote.trakt

import com.movies.premium.data.local.dao.HistoryDao
import com.movies.premium.data.local.dao.WatchlistDao
import com.movies.premium.data.local.entity.HistoryEntity
import com.movies.premium.data.local.entity.WatchlistEntity
import com.movies.premium.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the local watchlist and history in step with Trakt.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.trakt.TraktSyncManager`: it pulls
 * the Trakt watchlist and history into the local Room tables, pushes local additions and
 * removals back to Trakt, and can run the whole thing on a timer.
 *
 * The original stores Trakt's own ids in the `externalId` / `providerData` columns of
 * its watchlist row. The replica's `watchlist` table has no such columns, so the
 * mapping is done through the **TMDB id**, which Trakt's `ids` object also carries.
 * See the note on `TraktManager` about the OAuth credentials.
 */
@Singleton
class TraktSyncManager @Inject constructor(
    private val trakt: TraktManager,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao,
) {

    companion object {
        const val TAG = "TraktSync"

        /** How often the periodic sync runs. */
        const val SYNC_INTERVAL_MS = 15 * 60 * 1000L

        /** The two timestamp shapes Trakt emits, tried in this order. */
        private val TIMESTAMP_PATTERNS = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
    }

    private var syncJob: Job? = null

    // ---- pulling -----------------------------------------------------------------

    /**
     * Pulls the Trakt watchlist into the local table.
     *
     * The original maps each `TraktWatchlistItem` by its `type` word — `"movie"` takes
     * `item.movie`, `"show"` takes `item.show` — and drops anything else. The local row's
     * primary key is `"<type>_<tmdbId>"` in the original; the replica's `watchlist` table
     * is keyed on `(tmdbId, mediaType)` instead, so the key is not stored.
     *
     * @return the number of entries that were written.
     */
    suspend fun syncWatchlistFromTrakt(): Result<Int> = runCatching {
        var written = 0
        for (type in listOf(TraktManager.TYPE_MOVIES, TraktManager.TYPE_SHOWS)) {
            val items = trakt.getWatchlist(type).getOrThrow()
            for (item in items) {
                val isShow = item.type == "show"
                val isMovie = item.type == "movie"
                if (!isShow && !isMovie) continue
                val ids = if (isShow) item.show?.ids else item.movie?.ids
                val tmdbId = ids?.tmdb ?: continue
                val title = if (isShow) item.show?.title else item.movie?.title
                val year = if (isShow) item.show?.year else item.movie?.year
                watchlistDao.upsert(
                    WatchlistEntity(
                        tmdbId = tmdbId,
                        mediaType = if (isShow) "tv" else "movie",
                        title = title.orEmpty(),
                        posterPath = null,
                        backdropPath = null,
                        overview = null,
                        rating = 0.0,
                        releaseDate = year?.toString(),
                        addedAt = parseTimestamp(item.listedAt) ?: System.currentTimeMillis(),
                    ),
                )
                written++
            }
        }
        Logger.d(TAG, "Synced $written watchlist entries from Trakt")
        written
    }.onFailure { Logger.e(TAG, "syncWatchlistFromTrakt failed", it) }

    /**
     * Pulls the Trakt history into the local table.
     *
     * @return the number of entries that were written.
     */
    suspend fun syncHistoryFromTrakt(): Result<Int> = runCatching {
        var written = 0
        for (type in listOf(TraktManager.TYPE_MOVIES, TraktManager.TYPE_SHOWS)) {
            val items = trakt.getHistory(type).getOrThrow()
            for (item in items) {
                val isShow = item.type == "show"
                val isMovie = item.type == "movie"
                if (!isShow && !isMovie) continue
                val ids = if (isShow) item.show?.ids else item.movie?.ids
                val tmdbId = ids?.tmdb ?: continue
                val title = if (isShow) item.show?.title else item.movie?.title
                val key = if (isShow) {
                    "tv_${tmdbId}_${item.episode?.season ?: 0}_${item.episode?.number ?: 0}"
                } else {
                    "movie_$tmdbId"
                }
                historyDao.upsert(
                    HistoryEntity(
                        key = key,
                        tmdbId = tmdbId,
                        mediaType = if (isShow) "tv" else "movie",
                        title = title.orEmpty(),
                        posterPath = null,
                        episodeNumber = item.episode?.number,
                        seasonNumber = item.episode?.season,
                        watchedAt = parseTimestamp(item.watchedAt) ?: System.currentTimeMillis(),
                    ),
                )
                written++
            }
        }
        Logger.d(TAG, "Synced $written history entries from Trakt")
        written
    }.onFailure { Logger.e(TAG, "syncHistoryFromTrakt failed", it) }

    // ---- pushing -----------------------------------------------------------------

    /** Adds a local watchlist entry to the Trakt watchlist. */
    suspend fun pushWatchlistAdd(item: WatchlistEntity): Result<TraktSyncResponse> =
        runCatching {
            val body = item.toSyncRequest() ?: throw IllegalStateException("Unsupported media type")
            trakt.addToWatchlist(body).getOrThrow()
        }.onFailure { Logger.e(TAG, "pushWatchlistAdd failed", it) }

    /** Removes a local watchlist entry from the Trakt watchlist. */
    suspend fun pushWatchlistRemove(item: WatchlistEntity): Result<TraktSyncResponse> =
        runCatching {
            val body = item.toSyncRequest() ?: throw IllegalStateException("Unsupported media type")
            trakt.removeFromWatchlist(body).getOrThrow()
        }.onFailure { Logger.e(TAG, "pushWatchlistRemove failed", it) }

    /** Adds a local history entry to the Trakt history. */
    suspend fun pushHistoryAdd(item: HistoryEntity): Result<TraktSyncResponse> =
        runCatching {
            val body = item.toSyncRequest() ?: throw IllegalStateException("Unsupported media type")
            trakt.addToHistory(body).getOrThrow()
        }.onFailure { Logger.e(TAG, "pushHistoryAdd failed", it) }

    /** Removes a local history entry from the Trakt history. */
    suspend fun pushHistoryRemove(item: HistoryEntity): Result<TraktSyncResponse> =
        runCatching {
            val body = item.toSyncRequest() ?: throw IllegalStateException("Unsupported media type")
            trakt.removeFromHistory(body).getOrThrow()
        }.onFailure { Logger.e(TAG, "pushHistoryRemove failed", it) }

    // ---- periodic ----------------------------------------------------------------

    /** Starts the periodic sync. Calling it twice replaces the running job. */
    fun startPeriodicSync(scope: CoroutineScope) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                if (trakt.isLoggedIn()) {
                    syncWatchlistFromTrakt()
                    syncHistoryFromTrakt()
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    /** Stops the periodic sync. */
    fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
    }

    // ---- mapping -----------------------------------------------------------------

    /**
     * Parses one of Trakt's ISO-8601 timestamps into epoch milliseconds.
     *
     * The original's `TraktSyncManager` tries exactly two patterns, in this order, both
     * in UTC: `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` and `yyyy-MM-dd'T'HH:mm:ss'Z'`. A value
     * that matches neither yields `null`.
     */
    private fun parseTimestamp(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        for (pattern in TIMESTAMP_PATTERNS) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                val date = format.parse(raw) ?: continue
                return date.time
            } catch (_: Exception) {
                // Try the next pattern.
            }
        }
        return null
    }

    /**
     * Maps a local media type onto Trakt's own type word.
     *
     * The original's `TraktSyncManager` does exactly this:
     * `"movie"` → `"movie"`, `"anime_movie"` → `"movie"`, `"tv"` → `"show"`,
     * `"anime_tv"` → `"show"`, anything else → `null` (the item is skipped).
     */
    private fun traktType(mediaType: String): String? = when (mediaType) {
        "movie", "anime_movie" -> "movie"
        "tv", "anime_tv" -> "show"
        else -> null
    }

    /** Builds the `sync/…` request body for one local watchlist row. */
    private fun WatchlistEntity.toSyncRequest(): TraktSyncRequest? {
        val ids = TraktIds(tmdb = tmdbId)
        return when (traktType(mediaType)) {
            "show" -> TraktSyncRequest(shows = listOf(TraktSyncShow(ids = ids)))
            "movie" -> TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = ids)))
            else -> null
        }
    }

    /** Builds the `sync/…` request body for one local history row. */
    private fun HistoryEntity.toSyncRequest(): TraktSyncRequest? {
        val ids = TraktIds(tmdb = tmdbId)
        return when (traktType(mediaType)) {
            "movie" -> TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = ids)))
            "show" -> {
                val season = seasonNumber
                val episode = episodeNumber
                if (season != null && episode != null) {
                    TraktSyncRequest(
                        shows = listOf(TraktSyncShow(ids = ids)),
                        episodes = listOf(TraktSyncEpisode(ids = ids, season = season, episode = episode)),
                    )
                } else {
                    TraktSyncRequest(shows = listOf(TraktSyncShow(ids = ids)))
                }
            }
            else -> null
        }
    }
}
