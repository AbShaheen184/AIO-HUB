package com.movies.premium.data.remote.simkl

import com.movies.premium.data.local.dao.HistoryDao
import com.movies.premium.data.local.dao.WatchlistDao
import com.movies.premium.data.local.entity.HistoryEntity
import com.movies.premium.data.local.entity.WatchlistEntity
import com.movies.premium.data.repository.TmdbRepository
import com.movies.premium.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the local watchlist and history in step with SIMKL.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.simkl.e0`, whose log tag is
 * `SimklSyncManager` too. The original's method map:
 *
 * | original | here                        | what it does                                    |
 * |----------|-----------------------------|-------------------------------------------------|
 * | `e0.a`  | [enrichHistory]             | fills a history row with its TMDB details       |
 * | `e0.b`  | [enrichWatchlist]           | fills a watchlist row with its TMDB details     |
 * | `e0.c`  | [parseContentId]            | splits `"<type>_<tmdbId>"` into its two parts    |
 * | `e0.d`  | [pushHistoryAdd]            | `POST sync/history`                             |
 * | `e0.e`  | [removeItemByContentId]     | takes an item off both lists                    |
 * | `e0.f`  | [removeLocalWatchlist]      | deletes the local watchlist row                 |
 * | `e0.g`  | [removeItemByContentId]     | takes an item off both lists                    |
 * | `e0.h`  | [startSync]                 | runs the sync once, if it is not already running |
 * | `e0.i`  | [syncFromSimkl]             | the whole pull                                  |
 * | `e0.j`  | [pullHistory]               | pulls the SIMKL history into the local table    |
 * | `e0.k`  | [pullWatchlist]             | pulls the SIMKL watchlist and uploads the rest  |
 * | `e0.l`  | [toWatchlistEntity]         | one SIMKL item → one local watchlist row        |
 *
 * **Deviations from the original.**
 *
 * - The original keys its local rows on the composite `"<type>_<tmdbId>"` content id;
 *   the replica's `watchlist` table is keyed on the TMDB id and the `history` table on
 *   a `"<type>_<tmdbId>"` string, so the content id is *not* stored. An anime and a
 *   show that share a TMDB id therefore share one watchlist row.
 * - The original talks to the local tables through its own repository
 *   (`com.app.mlounge.data.repository.z`); the replica uses the DAOs directly, the
 *   same way `TraktSyncManager` does.
 * - The original's local types are `movie`, `tv`, `anime_tv` and `anime_movie`; the
 *   replica's tables only know `movie` and `tv`, so `anime_tv` becomes `tv` and
 *   `anime_movie` becomes `movie`.
 * - The original's `e0.h` starts the sync exactly once, right after the user logs in.
 *   The replica keeps that ([startSync]) and adds a timer ([startPeriodicSync]) so the
 *   watchlist and history stay fresh while the app is open.
 */
@Singleton
class SimklSyncManager @Inject constructor(
    private val simkl: SimklManager,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao,
    private val tmdb: TmdbRepository,
) {

    companion object {
        const val TAG = "SimklSync"

        /** The SIMKL status of an item that is on the plan-to-watch list. */
        const val STATUS_PLAN_TO_WATCH = "plantowatch"

        /** The SIMKL status of an item that is being watched. */
        const val STATUS_WATCHING = "watching"

        /** The SIMKL status of an item that has been watched. */
        const val STATUS_COMPLETED = "completed"

        const val TYPE_MOVIE = "movie"
        const val TYPE_TV = "tv"

        /** The original's type of a TV series that is an anime. */
        const val TYPE_ANIME_TV = "anime_tv"

        /** The original's type of a film that is an anime. */
        const val TYPE_ANIME_MOVIE = "anime_movie"

        /** The title the original uses when a title is missing. */
        const val UNKNOWN_TITLE = "Unknown"

        /** How often the periodic sync runs. */
        const val SYNC_INTERVAL_MS = 15 * 60 * 1000L

        /**
         * The local media types the original uploads to SIMKL.
         *
         * The original filters its local watchlist rows by these four types before it
         * pushes them to SIMKL.
         */
        val SYNCABLE_TYPES = listOf(TYPE_MOVIE, TYPE_TV, TYPE_ANIME_MOVIE, TYPE_ANIME_TV)
    }

    /**
     * The scope the sync runs on.
     *
     * The original's `e0` owns the same scope: a `SupervisorJob` on the IO dispatcher.
     */
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var syncJob: Job? = null

    // ---- pulling -----------------------------------------------------------------

    /**
     * The whole pull, exactly as the original's `e0.i` does it.
     *
     * Nothing happens when SIMKL is not connected or when the account has no items at
     * all; otherwise the watchlist is pulled first and the history second.
     *
     * @return the number of rows that were written.
     */
    suspend fun syncFromSimkl(): Result<Int> = runCatching {
        if (!simkl.isLoggedIn()) return@runCatching 0
        val items = simkl.getAllItems().getOrThrow()
        if (items.isEmpty()) return@runCatching 0
        val written = pullWatchlist(items) + pullHistory(items)
        Logger.d(TAG, "Simkl sync completed")
        written
    }.onFailure { Logger.e(TAG, "Sync from Simkl failed", it) }

    /**
     * Pulls the SIMKL watchlist into the local table.
     *
     * @return the number of rows that were written.
     */
    suspend fun syncWatchlistFromSimkl(): Result<Int> = runCatching {
        val items = simkl.getAllItems().getOrThrow()
        pullWatchlist(items)
    }.onFailure { Logger.e(TAG, "syncWatchlistFromSimkl failed", it) }

    /**
     * Pulls the SIMKL history into the local table.
     *
     * @return the number of rows that were written.
     */
    suspend fun syncHistoryFromSimkl(): Result<Int> = runCatching {
        val items = simkl.getAllItems().getOrThrow()
        pullHistory(items)
    }.onFailure { Logger.e(TAG, "syncHistoryFromSimkl failed", it) }

    /**
     * The original's `e0.k`: the watchlist pull, followed by the upload of the local
     * favourites SIMKL does not know yet.
     */
    private suspend fun pullWatchlist(items: List<SimklWatchlistItem>): Int {
        val onSimkl = HashSet<Int>()
        var written = 0
        for (item in items) {
            val status = item.status
            if (status != STATUS_PLAN_TO_WATCH && status != STATUS_WATCHING) continue
            val row = item.toWatchlistEntity() ?: continue
            onSimkl += row.tmdbId
            watchlistDao.upsert(enrichWatchlist(row))
            written++
        }
        Logger.d(TAG, "Added $written watchlist items from Simkl")

        // The original now walks the local watchlist and pushes everything SIMKL does
        // not have yet onto its plan-to-watch list.
        val local = watchlistDao.observeAll().first()
        var uploaded = 0
        for (row in local) {
            if (row.mediaType !in SYNCABLE_TYPES) continue
            if (row.tmdbId in onSimkl) continue
            val result = simkl.addToWatchlist(row.tmdbId.toString(), row.mediaType)
            if (result.isSuccess) uploaded++
        }
        Logger.d(TAG, "Uploaded $uploaded local favourites to Simkl plan-to-watch")
        return written
    }

    /**
     * The original's `e0.j`: the history pull.
     *
     * Only items with the status `completed` are taken, and an item that is already in
     * the local history is left alone.
     */
    private suspend fun pullHistory(items: List<SimklWatchlistItem>): Int {
        val known = historyDao.observeAll().first().map { it.tmdbId }.toHashSet()
        var written = 0
        for (item in items) {
            if (item.status != STATUS_COMPLETED) continue
            val row = item.toHistoryEntity() ?: continue
            if (row.tmdbId in known) continue
            historyDao.upsert(enrichHistory(row))
            written++
        }
        Logger.d(TAG, "Added $written history items from Simkl")
        return written
    }

    // ---- pushing -----------------------------------------------------------------

    /**
     * `POST sync/history` — marks something as watched.
     *
     * The original's `e0.d` only does this when a season *and* an episode are known.
     */
    suspend fun pushHistoryAdd(
        tmdbId: String,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
    ): Result<Unit> = simkl.addToHistory(tmdbId, mediaType, season, episode)

    /** `POST sync/add-to-list` — puts something on the plan-to-watch list. */
    suspend fun pushWatchlistAdd(tmdbId: String, mediaType: String): Result<Unit> =
        simkl.addToWatchlist(tmdbId, mediaType)

    /** `POST sync/history/remove` — takes something off the history. */
    suspend fun pushHistoryRemove(tmdbId: String, mediaType: String): Result<Unit> =
        simkl.removeFromHistory(tmdbId, mediaType)

    /** Takes something off the plan-to-watch list. */
    suspend fun pushWatchlistRemove(tmdbId: String, mediaType: String): Result<Unit> =
        simkl.removeFromWatchlist(tmdbId, mediaType)

    /**
     * The original's `e0.e` and `e0.g`: takes an item off both lists.
     *
     * The content id is the original's composite `"<type>_<tmdbId>"`; it is parsed with
     * [parseContentId] and the item is then removed from the plan-to-watch list and
     * from the history, in that order.
     */
    suspend fun removeItemByContentId(contentId: String): Result<Unit> {
        val (type, id) = parseContentId(contentId) ?: return Result.failure(
            IllegalArgumentException("Not a content id: $contentId"),
        )
        val tmdbId = id.toString()
        pushWatchlistRemove(tmdbId, type)
        return pushHistoryRemove(tmdbId, type)
    }

    /**
     * The original's `e0.f`: deletes the local watchlist row.
     *
     * The original hands the row to its watchlist repository, which deletes it.
     */
    suspend fun removeLocalWatchlist(tmdbId: Int, mediaType: String) =
        watchlistDao.delete(tmdbId, mediaType)

    // ---- periodic ----------------------------------------------------------------

    /**
     * The original's `e0.h`: runs the sync once.
     *
     * The original calls this right after the user has logged in, and it does nothing
     * when a sync is still running.
     */
    fun startSync(scope: CoroutineScope = syncScope) {
        val running = syncJob
        if (running != null && running.isActive) return
        syncJob = scope.launch { syncFromSimkl() }
    }

    /**
     * Runs the sync on a timer.
     *
     * The original has no timer — it syncs once after the login — so this is an
     * addition of the replica, mirroring `TraktSyncManager`.
     */
    fun startPeriodicSync(scope: CoroutineScope = syncScope) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                if (simkl.isLoggedIn()) {
                    syncFromSimkl()
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
     * The original's `e0.c`: splits a composite content id into its type and its id.
     *
     * The original looks for the last `_` in the first seven characters of the string
     * and then reads the number behind it. A string that does not have that shape
     * yields `null`.
     */
    fun parseContentId(contentId: String): Pair<String, Int>? {
        val head = contentId.substring(0, minOf(contentId.length, 7))
        val index = head.lastIndexOf('_')
        if (index <= 0 || index == contentId.length - 1) return null
        val id = contentId.substring(index + 1).toIntOrNull() ?: return null
        return contentId.substring(0, index) to id
    }

    /**
     * The original's `e0.l`: one SIMKL item → one local watchlist row.
     *
     * The original keys the row on `"<type>_<tmdbId>"` and uses the types `anime_tv`,
     * `tv` and `movie`; the replica's table has no content id column and only knows
     * `movie` and `tv`, so the type is narrowed and the id is the primary key.
     */
    private fun SimklWatchlistItem.toWatchlistEntity(): WatchlistEntity? {
        val movie = this.movie
        val show = this.show ?: this.anime
        val tmdbId = (movie?.ids?.tmdb ?: show?.ids?.tmdb)?.toIntOrNull() ?: return null
        val title = movie?.title ?: show?.title ?: UNKNOWN_TITLE
        val mediaType = when {
            this.anime != null -> TYPE_TV // the original's "anime_tv"
            show != null -> TYPE_TV // the original's "tv"
            else -> TYPE_MOVIE
        }
        return WatchlistEntity(
            tmdbId = tmdbId,
            mediaType = mediaType,
            title = title,
            addedAt = System.currentTimeMillis(),
        )
    }

    /**
     * One SIMKL item → one local history row.
     *
     * The original uses the same `"<type>_<tmdbId>"` content id here; the replica's
     * history table is keyed on a string, so the same shape is used for the key.
     */
    private fun SimklWatchlistItem.toHistoryEntity(): HistoryEntity? {
        val movie = this.movie
        val show = this.show ?: this.anime
        val tmdbId = (movie?.ids?.tmdb ?: show?.ids?.tmdb)?.toIntOrNull() ?: return null
        val title = movie?.title ?: show?.title ?: UNKNOWN_TITLE
        val mediaType = when {
            this.anime != null -> TYPE_TV // the original's "anime_tv"
            show != null -> TYPE_TV // the original's "tv"
            else -> TYPE_MOVIE
        }
        return HistoryEntity(
            key = "${mediaType}_$tmdbId",
            tmdbId = tmdbId,
            mediaType = mediaType,
            title = title,
            watchedAt = System.currentTimeMillis(),
        )
    }

    /**
     * The original's `e0.b`: fills a watchlist row with its TMDB details.
     *
     * A film is looked up as a film, everything else as a series — the original does
     * the same for the types `tv`, `anime_tv` and `anime_movie`.
     */
    private suspend fun enrichWatchlist(row: WatchlistEntity): WatchlistEntity = try {
        if (row.mediaType == TYPE_MOVIE) {
            val detail = tmdb.movieDetail(row.tmdbId).getOrThrow()
            row.copy(
                posterPath = detail.posterPath,
                backdropPath = detail.backdropPath,
                rating = detail.voteAverage,
                releaseDate = detail.releaseDate,
                overview = detail.overview,
            )
        } else {
            val detail = tmdb.tvDetail(row.tmdbId).getOrThrow()
            row.copy(
                posterPath = detail.posterPath,
                backdropPath = detail.backdropPath,
                rating = detail.voteAverage,
                releaseDate = detail.firstAirDate,
                overview = detail.overview,
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to enrich watchlist item ${row.title}", e)
        row
    }

    /**
     * The original's `e0.a`: fills a history row with its TMDB details.
     *
     * The original's history row carries the poster, the backdrop, the rating, the
     * release date and the overview; the replica's history row only has a poster path,
     * so that is the only field that is filled here.
     */
    private suspend fun enrichHistory(row: HistoryEntity): HistoryEntity = try {
        val posterPath = if (row.mediaType == TYPE_MOVIE) {
            tmdb.movieDetail(row.tmdbId).getOrThrow().posterPath
        } else {
            tmdb.tvDetail(row.tmdbId).getOrThrow().posterPath
        }
        row.copy(posterPath = posterPath)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to enrich history item ${row.title}", e)
        row
    }
}
