package com.movies.premium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.music.MusicStreamResolver
import com.movies.premium.data.remote.model.MusicAlbumDetail
import com.movies.premium.data.remote.model.MusicArtistDetail
import com.movies.premium.data.remote.model.MusicGenre
import com.movies.premium.data.remote.model.MusicItem
import com.movies.premium.data.remote.model.MusicStreamResult
import com.movies.premium.data.remote.model.MusicTrack
import com.movies.premium.data.repository.MusicRepository
import com.movies.premium.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A track that has been resolved into a playable stream.
 *
 * The original hands the resolved url, the title, the request headers, the
 * content type, the container type and the track id to the player as one
 * bundle, so this carries the same information.
 */
data class MusicPlayback(
    val track: MusicTrack,
    val result: MusicStreamResult,
)

/**
 * State of the music browse screen and the album / artist detail screens.
 *
 * The original splits the state over many `StateFlow`s and keeps the paging
 * cursor in a plain field, so this does the same.
 */
@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val streamResolver: MusicStreamResolver,
) : SafeViewModel() {

    companion object {
        private const val TAG = "MusicVM"

        /** The sections the browse screen offers. */
        val SECTIONS: List<String> = listOf("Top Albums", "New Releases")
    }

    private val _items = MutableStateFlow<List<MusicItem>>(emptyList())
    val items: StateFlow<List<MusicItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedGenre = MutableStateFlow(MusicRepository.GENRES.first())
    val selectedGenre: StateFlow<MusicGenre> = _selectedGenre.asStateFlow()

    private val _selectedSection = MutableStateFlow(SECTIONS.first())
    val selectedSection: StateFlow<String> = _selectedSection.asStateFlow()

    private val _selectedSource = MutableStateFlow(MusicRepository.SOURCES.first())
    val selectedSource: StateFlow<String> = _selectedSource.asStateFlow()

    private val _albumDetail = MutableStateFlow<MusicAlbumDetail?>(null)
    val albumDetail: StateFlow<MusicAlbumDetail?> = _albumDetail.asStateFlow()

    private val _artistDetail = MutableStateFlow<MusicArtistDetail?>(null)
    val artistDetail: StateFlow<MusicArtistDetail?> = _artistDetail.asStateFlow()

    private val _favourites = MutableStateFlow<Set<String>>(emptySet())
    val favourites: StateFlow<Set<String>> = _favourites.asStateFlow()

    /** The track that is currently being resolved, if any. */
    private val _resolvingTrackId = MutableStateFlow<String?>(null)
    val resolvingTrackId: StateFlow<String?> = _resolvingTrackId.asStateFlow()

    /** The last resolved track, waiting for the navigation to pick it up. */
    private val _playback = MutableStateFlow<MusicPlayback?>(null)
    val playback: StateFlow<MusicPlayback?> = _playback.asStateFlow()

    /** The message shown when a track cannot be resolved. */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    /** The genres offered by the filter dropdown. */
    val genres: List<MusicGenre> = MusicRepository.GENRES

    /** The sources offered by the filter dropdown. */
    val sources: List<String> = MusicRepository.SOURCES

    /** The sections offered by the filter dropdown. */
    val sections: List<String> = SECTIONS

    /** The provider cursor for the next page. */
    private var nextPageToken: Any? = null

    /** Whether the source still has pages left. */
    private var hasMore = true

    init {
        loadHome()
    }

    // --- browse -------------------------------------------------------------

    /**
     * Loads the first page of the current section. Does nothing while a load
     * is already running, and resets the paging state first.
     */
    fun loadHome() {
        if (_loading.value) return
        reset()
        scope.launch {
            _loading.value = true
            try {
                val page = withContext(Dispatchers.IO) {
                    repository.loadSection(
                        section = _selectedSection.value,
                        genreId = _selectedGenre.value.id,
                        pageToken = nextPageToken,
                    )
                }
                _items.value = page.items
                nextPageToken = page.nextPageToken
                hasMore = page.nextPageToken != null
            } catch (e: Exception) {
                Logger.e(TAG, "loadHome failed: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    /** Clears the list and the paging cursor. */
    fun reset() {
        nextPageToken = null
        hasMore = true
        _items.value = emptyList()
    }

    /** Appends the next page to the list. */
    fun loadMore() {
        if (!hasMore || _loadingMore.value) return
        scope.launch {
            _loadingMore.value = true
            try {
                val page = withContext(Dispatchers.IO) {
                    repository.loadSection(
                        section = _selectedSection.value,
                        genreId = _selectedGenre.value.id,
                        pageToken = nextPageToken,
                    )
                }
                if (page.items.isNotEmpty()) {
                    _items.value = _items.value + page.items
                }
                nextPageToken = page.nextPageToken
                hasMore = page.nextPageToken != null
            } catch (e: Exception) {
                Logger.e(TAG, "loadMore failed: ${e.message}")
                hasMore = false
            } finally {
                _loadingMore.value = false
            }
        }
    }

    /** Selects a genre and reloads the list. */
    fun setGenre(genre: MusicGenre) {
        _selectedGenre.value = genre
        reset()
        loadHome()
    }

    /** Selects a section and reloads the list. */
    fun setSection(section: String) {
        _selectedSection.value = section
        reset()
        loadHome()
    }

    /** Selects a source, switches the repository over and reloads the list. */
    fun setSource(source: String) {
        _selectedSource.value = source
        repository.selectSource(source)
        reset()
        loadHome()
    }

    // --- details ------------------------------------------------------------

    /** Loads the album page behind a card. */
    fun loadAlbumDetail(url: String) {
        scope.launch {
            _loading.value = true
            try {
                val detail = withContext(Dispatchers.IO) { repository.getAlbumDetail(url) }
                _albumDetail.value = detail
            } catch (e: Exception) {
                Logger.e(TAG, "loadAlbumDetail failed for $url: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    /** Loads the artist page behind a card. */
    fun loadArtistDetail(url: String) {
        scope.launch {
            _loading.value = true
            try {
                val detail = withContext(Dispatchers.IO) { repository.getArtistDetail(url) }
                _artistDetail.value = detail
            } catch (e: Exception) {
                Logger.e(TAG, "loadArtistDetail failed for $url: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    /** Clears the loaded detail pages; called when the screens leave. */
    fun clearDetails() {
        _albumDetail.value = null
        _artistDetail.value = null
    }

    // --- playback -----------------------------------------------------------

    /**
     * Resolves a track into a stream and publishes the result.
     *
     * The original resolves the track in the background and hands the result
     * to the player through a callback; the same happens here, except that
     * the result is published as state so the navigation graph can pick it up.
     */
    fun playTrack(track: MusicTrack) {
        if (_resolvingTrackId.value != null) return
        _playbackError.value = null
        _resolvingTrackId.value = track.id
        scope.launch {
            try {
                val result = streamResolver.resolve(track)
                if (result == null) {
                    _playbackError.value = "Could not play ${track.title}"
                } else {
                    _playback.value = MusicPlayback(track, result)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "playTrack failed for ${track.id}: ${e.message}")
                _playbackError.value = "Could not play ${track.title}"
            } finally {
                _resolvingTrackId.value = null
            }
        }
    }

    /** Clears the resolved track once the navigation has taken it. */
    fun consumePlayback() {
        _playback.value = null
    }

    // --- favourites ---------------------------------------------------------

    /** Adds or removes a track from the favourites. */
    fun toggleFavourite(trackId: String) {
        val current = _favourites.value.toMutableSet()
        if (!current.add(trackId)) current.remove(trackId)
        _favourites.value = current
    }

    /** Whether a track is in the favourites. */
    fun isFavourite(trackId: String): Boolean = trackId in _favourites.value
}
