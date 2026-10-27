package com.movies.premium.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.remote.model.EpornerSourceDetail
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.data.remote.model.EpornerVideo
import com.movies.premium.data.remote.model.EpornerVideoDetail
import com.movies.premium.data.repository.AdultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdultUiState(
    val query: String = "",
    val videos: List<EpornerVideo> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AdultViewModel @Inject constructor(
    private val repository: AdultRepository,
    preferences: AppPreferences,
) : SafeViewModel() {

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }

    /**
     * The adult section is only reachable once the user turns it on in Settings
     * (`adult_enabled`), which is how the original gates it too.
     */
    val enabled: StateFlow<Boolean> = preferences.adultEnabled
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val _uiState = MutableStateFlow(AdultUiState())
    val uiState: StateFlow<AdultUiState> = _uiState.asStateFlow()

    private var page = 1
    private var searchJob: Job? = null

    init {
        load()
    }

    /** Runs a new search and replaces the list. */
    fun search(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        page = 1
        // The original searches as the user types; the requests are debounced here so
        // a fast typist does not fire one request per keystroke.
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load()
        }
    }

    /** Loads the next page and appends it. */
    fun loadMore() {
        val state = _uiState.value
        if (state.loadingMore || !state.hasMore) return
        page += 1
        _uiState.value = state.copy(loadingMore = true)
        scope.launch {
            repository.search(_uiState.value.query, page)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        videos = _uiState.value.videos + result.videos.orEmpty(),
                        loading = false,
                        loadingMore = false,
                        hasMore = page < (result.totalPages ?: 1),
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(loadingMore = false)
                }
        }
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        scope.launch {
            repository.search(_uiState.value.query, page)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        videos = result.videos.orEmpty(),
                        loading = false,
                        hasMore = page < (result.totalPages ?: 1),
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = "Couldn't load videos",
                    )
                }
        }
    }
}

data class AdultDetailUiState(
    val detail: EpornerVideoDetail? = null,
    val sources: List<EpornerSourceDetail> = emptyList(),
    val loading: Boolean = true,
    val resolving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AdultDetailViewModel @Inject constructor(
    private val repository: AdultRepository,
    savedStateHandle: SavedStateHandle,
) : SafeViewModel() {

    private val videoId: String = savedStateHandle.get<String>("videoId").orEmpty()

    private val _uiState = MutableStateFlow(AdultDetailUiState())
    val uiState: StateFlow<AdultDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        scope.launch {
            repository.videoDetail(videoId)
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(detail = detail, loading = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = "Couldn't load this video",
                    )
                }
        }
    }

    /**
     * Resolves the playable sources.
     *
     * The original resolves the stream when the user presses play and then hands the
     * URL to the player; the same is done here.
     */
    fun resolve(onResolved: (EpornerSourceDetail) -> Unit) {
        if (_uiState.value.resolving) return
        _uiState.value = _uiState.value.copy(resolving = true, error = null)
        scope.launch {
            repository.resolve(videoId)
                .onSuccess { sources ->
                    _uiState.value = _uiState.value.copy(resolving = false, sources = sources)
                    val best = sources.firstOrNull { !it.src.isNullOrBlank() }
                    if (best == null) {
                        _uiState.value = _uiState.value.copy(error = "No playable source")
                    } else {
                        onResolved(best)
                    }
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        resolving = false,
                        error = "Couldn't resolve the stream",
                    )
                }
        }
    }
}
