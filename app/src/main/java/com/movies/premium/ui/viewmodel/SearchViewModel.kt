package com.movies.premium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.data.remote.model.TmdbMovie
import com.movies.premium.data.remote.model.TmdbTvShow
import com.movies.premium.data.repository.TmdbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val movies: List<TmdbMovie> = emptyList(),
    val tvShows: List<TmdbTvShow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    private val preferences: AppPreferences,
) : SafeViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(movies = emptyList(), tvShows = emptyList(), loading = false)
            return
        }
        // Debounce so typing does not fire a request per keystroke.
        searchJob = scope.launch {
            delay(350)
            search(query)
        }
    }

    private suspend fun search(query: String) {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        // The original's "Include Adult in Search" switch decides whether TMDB
        // may answer with adult titles. It only has an effect once the adult
        // section itself is enabled.
        val includeAdult = preferences.adultEnabled.first() &&
            preferences.includeAdultInSearch.first()
        val movies = tmdbRepository.searchMovies(query, includeAdult = includeAdult)
        val tvShows = tmdbRepository.searchTvShows(query, includeAdult = includeAdult)
        _uiState.value = _uiState.value.copy(
            movies = movies.getOrNull()?.results.orEmpty(),
            tvShows = tvShows.getOrNull()?.results.orEmpty(),
            loading = false,
            error = if (movies.isFailure && tvShows.isFailure) "No results" else null,
        )
    }
}
