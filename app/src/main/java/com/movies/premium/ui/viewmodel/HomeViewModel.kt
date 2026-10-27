package com.movies.premium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.remote.model.TmdbMovie
import com.movies.premium.data.remote.model.TmdbTvShow
import com.movies.premium.data.repository.TmdbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val trendingMovies: List<TmdbMovie> = emptyList(),
    val trendingTvShows: List<TmdbTvShow> = emptyList(),
    val popularMovies: List<TmdbMovie> = emptyList(),
    val popularTvShows: List<TmdbTvShow> = emptyList(),
    val topRatedMovies: List<TmdbMovie> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
) : SafeViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        scope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            // Rows are independent; fetch them concurrently like the original does.
            val (trendingM, trendingTv) = coroutineScope {
                val a = async { tmdbRepository.trendingMovies() }
                val b = async { tmdbRepository.trendingTvShows() }
                a.await() to b.await()
            }
            val (popularM, popularTv) = coroutineScope {
                val a = async { tmdbRepository.discoverMovies(sortBy = "popularity.desc") }
                val b = async { tmdbRepository.discoverTvShows(sortBy = "popularity.desc") }
                a.await() to b.await()
            }
            val topRated = tmdbRepository.discoverMovies(sortBy = "vote_average.desc")

            _uiState.value = HomeUiState(
                trendingMovies = trendingM.getOrNull()?.results.orEmpty(),
                trendingTvShows = trendingTv.getOrNull()?.results.orEmpty(),
                popularMovies = popularM.getOrNull()?.results.orEmpty(),
                popularTvShows = popularTv.getOrNull()?.results.orEmpty(),
                topRatedMovies = topRated.getOrNull()?.results.orEmpty(),
                loading = false,
                error = if (trendingM.isFailure && trendingTv.isFailure) "Couldn't load content" else null,
            )
        }
    }
}
