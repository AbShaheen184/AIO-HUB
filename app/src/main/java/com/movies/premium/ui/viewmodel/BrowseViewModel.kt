package com.movies.premium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.remote.model.TmdbGenre
import com.movies.premium.data.remote.model.TmdbMovie
import com.movies.premium.data.remote.model.TmdbTvShow
import com.movies.premium.data.repository.TmdbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sort options exposed by the Movies and TV Shows browse screens. */
enum class BrowseSort(val label: String, val apiValue: String) {
    POPULAR("Popular", "popularity.desc"),
    TRENDING("Trending", "trending"),
    TOP_RATED("Top Rated", "vote_average.desc"),
    NEWEST("Newest", "primary_release_date.desc"),
    OLDEST("Oldest", "primary_release_date.asc"),
}

data class BrowseUiState(
    val movies: List<TmdbMovie> = emptyList(),
    val tvShows: List<TmdbTvShow> = emptyList(),
    val genres: List<TmdbGenre> = emptyList(),
    val selectedGenre: TmdbGenre? = null,
    val selectedYear: Int? = null,
    val sort: BrowseSort = BrowseSort.POPULAR,
    val page: Int = 1,
    val totalPages: Int = 1,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
) : SafeViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private var isTv = false

    /** Whether a page was already fetched for the current mode. */
    private var loaded = false

    init {
        // Nothing is loaded here: the screen tells the view model which mode
        // it wants through `setMode`, and loading the movies first would
        // throw away a whole request on the TV Shows screen.
    }

    fun setMode(tv: Boolean) {
        // The first call always loads, and a call with the mode that is
        // already shown does not load again — but it must not clear the
        // list either, so the flag says whether anything was loaded yet.
        if (isTv == tv && loaded) return
        isTv = tv
        loaded = true
        _uiState.value = _uiState.value.copy(movies = emptyList(), tvShows = emptyList(), page = 1)
        // The genre list is per mode, so it has to be fetched again.
        loadGenres()
        load()
    }

    fun loadGenres() {
        scope.launch {
            val genres = if (isTv) tmdbRepository.tvGenres() else tmdbRepository.movieGenres()
            _uiState.value = _uiState.value.copy(genres = genres.getOrDefault(emptyList()))
        }
    }

    fun load() {
        scope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null, page = 1)
            val state = _uiState.value
            if (isTv) {
                tmdbRepository.discoverTvShows(
                    page = 1,
                    sortBy = state.sort.apiValue,
                    genres = state.selectedGenre?.id?.toString(),
                    year = state.selectedYear,
                ).onSuccess { paged ->
                    _uiState.value = _uiState.value.copy(
                        movies = emptyList(),
                        tvShows = paged.results,
                        totalPages = paged.totalPages,
                        loading = false,
                    )
                }.onFailure {
                    _uiState.value = _uiState.value.copy(loading = false, error = "Couldn't load content")
                }
            } else {
                tmdbRepository.discoverMovies(
                    page = 1,
                    sortBy = state.sort.apiValue,
                    genres = state.selectedGenre?.id?.toString(),
                    year = state.selectedYear,
                ).onSuccess { paged ->
                    _uiState.value = _uiState.value.copy(
                        movies = paged.results,
                        tvShows = emptyList(),
                        totalPages = paged.totalPages,
                        loading = false,
                    )
                }.onFailure {
                    _uiState.value = _uiState.value.copy(loading = false, error = "Couldn't load content")
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loadingMore || state.loading || state.page >= state.totalPages) return
        scope.launch {
            _uiState.value = state.copy(loadingMore = true)
            val next = state.page + 1
            if (isTv) {
                tmdbRepository.discoverTvShows(
                    page = next,
                    sortBy = state.sort.apiValue,
                    genres = state.selectedGenre?.id?.toString(),
                    year = state.selectedYear,
                ).onSuccess { paged ->
                    _uiState.value = _uiState.value.copy(
                        tvShows = _uiState.value.tvShows + paged.results,
                        page = next,
                        loadingMore = false,
                    )
                }.onFailure {
                    _uiState.value = _uiState.value.copy(loadingMore = false)
                }
            } else {
                tmdbRepository.discoverMovies(
                    page = next,
                    sortBy = state.sort.apiValue,
                    genres = state.selectedGenre?.id?.toString(),
                    year = state.selectedYear,
                ).onSuccess { paged ->
                    _uiState.value = _uiState.value.copy(
                        movies = _uiState.value.movies + paged.results,
                        page = next,
                        loadingMore = false,
                    )
                }.onFailure {
                    _uiState.value = _uiState.value.copy(loadingMore = false)
                }
            }
        }
    }

    fun selectSort(sort: BrowseSort) {
        _uiState.value = _uiState.value.copy(sort = sort)
        load()
    }

    fun selectGenre(genre: TmdbGenre?) {
        _uiState.value = _uiState.value.copy(selectedGenre = genre)
        load()
    }

    /**
     * Filters by the release year.
     *
     * The original's browse screen has the same filter next to the genre and
     * the sort order.
     */
    fun selectYear(year: Int?) {
        _uiState.value = _uiState.value.copy(selectedYear = year)
        load()
    }
}
