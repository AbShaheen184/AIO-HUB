package com.movies.premium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.remote.model.HiAnimeDetailData
import com.movies.premium.data.remote.model.HiAnimeEpisode
import com.movies.premium.data.remote.model.HiAnimeItem
import com.movies.premium.data.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * State of the anime browse screen and the anime detail screen.
 *
 * The original keeps every one of these in separate `StateFlow`s, and so does
 * this one: the screens read them individually, exactly like the original
 * does.
 */
@HiltViewModel
class AnimeViewModel @Inject constructor(
    private val repository: AnimeRepository,
) : SafeViewModel() {

    private val _items = MutableStateFlow<List<HiAnimeItem>>(emptyList())
    val items: StateFlow<List<HiAnimeItem>> = _items.asStateFlow()

    private val _genres = MutableStateFlow<List<String>>(emptyList())
    val genres: StateFlow<List<String>> = _genres.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedGenre = MutableStateFlow(DEFAULT_GENRE)
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    private val _selectedType = MutableStateFlow(DEFAULT_TYPE)
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    private val _detail = MutableStateFlow<HiAnimeDetailData?>(null)
    val detail: StateFlow<HiAnimeDetailData?> = _detail.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    /**
     * The failure of the last detail load.
     *
     * The browse list has its own error, and the two must not run into each
     * other: the detail screen shows this one.
     */
    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    private val _episodes = MutableStateFlow<List<HiAnimeEpisode>>(emptyList())
    val episodes: StateFlow<List<HiAnimeEpisode>> = _episodes.asStateFlow()

    private val _related = MutableStateFlow<List<HiAnimeItem>>(emptyList())
    val related: StateFlow<List<HiAnimeItem>> = _related.asStateFlow()

    private val _favourites = MutableStateFlow<Set<String>>(emptySet())
    val favourites: StateFlow<Set<String>> = _favourites.asStateFlow()

    private val _watchProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val watchProgress: StateFlow<Map<String, Float>> = _watchProgress.asStateFlow()

    private val _currentEpisode = MutableStateFlow(0)
    val currentEpisode: StateFlow<Int> = _currentEpisode.asStateFlow()

    private val _servers = MutableStateFlow<List<AnimeServerChoice>>(emptyList())
    val servers: StateFlow<List<AnimeServerChoice>> = _servers.asStateFlow()

    private val _serversLoading = MutableStateFlow(false)
    val serversLoading: StateFlow<Boolean> = _serversLoading.asStateFlow()

    /** The page currently loaded; the next request asks for `page + 1`. */
    private var page = 1

    /** Whether the source still has pages left. */
    private var hasMore = true

    /** The in-flight browse request; a new one cancels the old one. */
    private var loadJob: Job? = null

    init {
        loadGenres()
        loadAnime()
    }

    // --- browse -------------------------------------------------------------

    /** Loads the genre chip list. */
    fun loadGenres() {
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) { repository.getGenres() }
                _genres.value = data.genres
            } catch (e: Exception) {
                // The original swallows this failure as well.
            }
        }
    }

    /**
     * Restarts the browse list: cancels the running request, resets to the
     * first page, clears the list and the error, and loads again.
     */
    fun loadAnime() {
        loadJob?.cancel()
        page = 1
        _items.value = emptyList()
        _error.value = null
        loadJob = scope.launch {
            _loading.value = true
            try {
                val data = withContext(Dispatchers.IO) {
                    repository.getAnimeByGenre(_selectedGenre.value, page, _selectedType.value)
                }
                _items.value = data.animes
                hasMore = data.hasNextPage
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load anime"
            } finally {
                _loading.value = false
            }
        }
    }

    /** Appends the next page to the list. */
    fun loadMore() {
        if (!hasMore || _loadingMore.value) return
        scope.launch {
            _loadingMore.value = true
            try {
                val data = withContext(Dispatchers.IO) {
                    repository.getAnimeByGenre(_selectedGenre.value, page + 1, _selectedType.value)
                }
                _items.value = _items.value + data.animes
                hasMore = data.hasNextPage
                page += 1
            } catch (e: Exception) {
                // The original swallows the failure and keeps the list as is.
            } finally {
                _loadingMore.value = false
            }
        }
    }

    /** Selects a genre chip and reloads the list. */
    fun setGenre(genre: String) {
        _selectedGenre.value = genre
        loadAnime()
    }

    /** Selects a media-type chip and reloads the list. */
    fun setType(type: String) {
        _selectedType.value = type
        loadAnime()
    }

    // --- detail -------------------------------------------------------------

    /** Loads the detail block and the episode list of a series. */
    fun loadDetail(animeId: String) {
        scope.launch {
            _detailLoading.value = true
            _detailError.value = null
            try {
                val detail = withContext(Dispatchers.IO) { repository.getAnimeDetail(animeId) }
                _detail.value = detail
                val episodes = withContext(Dispatchers.IO) { repository.getAnimeEpisodes(animeId) }
                _episodes.value = episodes.episodes
                _related.value = detail?.relatedAnimes.orEmpty()
            } catch (e: Exception) {
                _detailError.value = e.message ?: "Couldn't load this title"
            } finally {
                _detailLoading.value = false
            }
        }
    }

    /** Clears the detail block; the detail screen calls this when it leaves. */
    fun clearDetail() {
        _detail.value = null
        _episodes.value = emptyList()
        _related.value = emptyList()
        // The error of a failed load must not stay behind, otherwise the
        // detail screen would show it before it started loading.
        _detailError.value = null
    }

    // --- favourites and progress -------------------------------------------

    /** Adds or removes a title from the favourites. */
    fun toggleFavourite(animeId: String) {
        val current = _favourites.value.toMutableSet()
        if (!current.add(animeId)) current.remove(animeId)
        _favourites.value = current
    }

    /** Whether a title is in the favourites. */
    fun isFavourite(animeId: String): Boolean = animeId in _favourites.value

    /** Records the playback progress of an episode (0f..1f). */
    fun setWatchProgress(episodeId: String, progress: Float) {
        _watchProgress.value = _watchProgress.value + (episodeId to progress.coerceIn(0f, 1f))
    }

    /** The playback progress of an episode, or 0f when there is none. */
    fun progressOf(episodeId: String): Float = _watchProgress.value[episodeId] ?: 0f

    /** Remembers which episode is being played. */
    fun setCurrentEpisode(episode: Int) {
        _currentEpisode.value = episode
    }

    // --- the servers of an episode ---------------------------------------------

    /**
     * Loads the servers of an episode.
     *
     * The original asks the scraper for the sub and the dub servers of the
     * episode and lets the user pick one of them before the source is
     * resolved.
     */
    fun loadServers(episodeId: String) {
        scope.launch { serversFor(episodeId) }
    }

    /**
     * The servers of an episode, fetched and returned.
     *
     * The state is updated as well, so a screen that shows the list of the
     * servers sees them. The sub servers come first, the dub ones after them,
     * and the server the original hides is dropped.
     *
     * Unlike [loadServers] this one waits for the answer, so a screen can
     * start the playback with the server it picked right away.
     */
    suspend fun serversFor(episodeId: String): List<AnimeServerChoice> {
        _serversLoading.value = true
        _servers.value = emptyList()
        return try {
            val data = withContext(Dispatchers.IO) {
                repository.getAnimeEpisodeServers(episodeId)
            }
            val servers = data.data
            buildList {
                servers?.sub?.forEach { add(AnimeServerChoice(it.serverName, "sub")) }
                servers?.dub?.forEach { add(AnimeServerChoice(it.serverName, "dub")) }
            }.filterNot { it.name == EXCLUDED_SERVER }
        } catch (e: Exception) {
            emptyList()
        } finally {
            _serversLoading.value = false
        }
    }

    companion object {
        /** The genre the browse screen starts on. */
        const val DEFAULT_GENRE = "Action"

        /** The media type the browse screen starts on. */
        const val DEFAULT_TYPE = "tv"

        /**
         * The server the original hides.
         *
         * The anime screen skips it when it builds the server list.
         */
        const val EXCLUDED_SERVER = "t-cloud"
    }
}

/**
 * One server of an anime episode.
 *
 * The original pairs the name of a server with the category it belongs to
 * (`"sub"` or `"dub"`) and hands that pair to the source resolver.
 */
data class AnimeServerChoice(
    val name: String,
    val category: String,
)
