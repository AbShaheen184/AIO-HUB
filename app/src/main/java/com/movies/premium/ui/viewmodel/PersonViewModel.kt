package com.movies.premium.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.remote.model.TmdbPersonDetail
import com.movies.premium.data.repository.TmdbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonUiState(
    val person: TmdbPersonDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    savedStateHandle: SavedStateHandle,
) : SafeViewModel() {

    private val personId: Int = savedStateHandle.get<Int>("personId")
        ?: savedStateHandle.get<String>("personId")?.toIntOrNull()
        ?: 0

    private val _uiState = MutableStateFlow(PersonUiState())
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        scope.launch {
            _uiState.value = PersonUiState(loading = true)
            tmdbRepository.personDetail(personId)
                .onSuccess { _uiState.value = PersonUiState(person = it, loading = false) }
                .onFailure {
                    _uiState.value = PersonUiState(loading = false, error = "Couldn't load person")
                }
        }
    }
}
