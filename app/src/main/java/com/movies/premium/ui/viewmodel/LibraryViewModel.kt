package com.movies.premium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.download.DownloadManager
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.data.local.dao.ContinueWatchingDao
import com.movies.premium.data.local.dao.DownloadDao
import com.movies.premium.data.local.dao.FavoriteDao
import com.movies.premium.data.local.dao.HistoryDao
import com.movies.premium.data.local.dao.WatchlistDao
import com.movies.premium.data.local.entity.ContinueWatchingEntity
import com.movies.premium.data.local.entity.DownloadEntity
import com.movies.premium.data.local.entity.FavoriteEntity
import com.movies.premium.data.local.entity.HistoryEntity
import com.movies.premium.data.local.entity.WatchlistEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Watchlist, Favorites, History, Downloads and Continue Watching screens. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao,
    private val downloadDao: DownloadDao,
    private val favoriteDao: FavoriteDao,
    private val continueWatchingDao: ContinueWatchingDao,
    private val downloadManager: DownloadManager,
    private val preferences: AppPreferences,
) : SafeViewModel() {

    val watchlist: StateFlow<List<WatchlistEntity>> = watchlistDao.observeAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Whether the adult titles may be shown in the favourites.
     *
     * Mirrors the original's "Include Adult in Favourites" switch, which is
     * backed by the `include_adult_in_watchlist` preference. The switch only
     * has an effect once the adult section itself is enabled.
     */
    val showAdultFavourites: StateFlow<Boolean> =
        combine(preferences.adultEnabled, preferences.includeAdultInFavourites) { adult, include ->
            adult && include
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Whether the adult titles may be shown in the watch history.
     *
     * Mirrors the original's "Include Adult in History" switch, which is backed
     * by the `include_adult_in_history` preference.
     */
    val showAdultHistory: StateFlow<Boolean> =
        combine(preferences.adultEnabled, preferences.includeAdultInHistory) { adult, include ->
            adult && include
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val favorites: StateFlow<List<FavoriteEntity>> = favoriteDao.observeAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = historyDao.observeAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloads: StateFlow<List<DownloadEntity>> = downloadDao.observeAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val continueWatching: StateFlow<List<ContinueWatchingEntity>> = continueWatchingDao.observeAll()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeFromWatchlist(tmdbId: Int, mediaType: String) {
        scope.launch { watchlistDao.delete(tmdbId, mediaType) }
    }

    fun removeFavorite(tmdbId: Int, mediaType: String) {
        scope.launch { favoriteDao.delete(tmdbId, mediaType) }
    }

    fun clearHistory() {
        scope.launch { historyDao.clear() }
    }

    fun deleteDownload(id: Long) {
        scope.launch { downloadDao.delete(id) }
    }

    /** Pauses a running download. The original's row shows a pause action while running. */
    fun pauseDownload(id: Long) {
        scope.launch { downloadManager.pauseDownload(id) }
    }

    /** Resumes a paused download. */
    fun resumeDownload(id: Long) {
        scope.launch { downloadManager.resumeDownload(id) }
    }

    /** Restarts a download that failed or was cancelled. */
    fun retryDownload(id: Long) {
        scope.launch { downloadManager.retryDownload(id) }
    }
}
