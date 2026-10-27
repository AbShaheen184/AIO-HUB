package com.movies.premium.ui.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.remote.model.GameDetailResponse
import com.movies.premium.data.remote.model.GamePlatform
import com.movies.premium.data.repository.GamesRepository
import com.movies.premium.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class GameDetailUiState(
    val detail: GameDetailResponse? = null,
    val platform: GamePlatform = GamePlatform.NES,
    val loading: Boolean = true,
    val error: String? = null,
    val downloading: Boolean = false,
    /** Bytes written so far, or 0 when idle. */
    val downloadedBytes: Long = 0L,
    /** Total size of the ROM, or -1 when the server does not report it. */
    val totalBytes: Long = -1L,
    val romReady: Boolean = false,
)

/**
 * Backs the game detail screen (`game_detail/{platform}/{gameSlug}`).
 *
 * The original keeps the ROM in `filesDir/roms/<slug><extension>` and only lets the
 * user start the emulator once that file exists — otherwise it shows the toast
 * "Download the game first". The same rule is reproduced here.
 */
@HiltViewModel
class GameDetailViewModel @Inject constructor(
    private val gamesRepository: GamesRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : SafeViewModel() {

    private val platformArg: String =
        savedStateHandle.get<String>("platform").orEmpty()
    private val slug: String = savedStateHandle.get<String>("gameSlug").orEmpty()

    private val platform: GamePlatform = GamePlatform.fromApiPath(platformArg)

    private val romsDir: File
        get() = File(context.filesDir, "roms").also { it.mkdirs() }

    private val _uiState = MutableStateFlow(GameDetailUiState(platform = platform))
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        scope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            gamesRepository.getGameDetail(platform.apiPath, slug)
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        detail = detail,
                        loading = false,
                        romReady = gamesRepository.isRomDownloaded(romsDir, slug, platform),
                    )
                }
                .onFailure {
                    Logger.e("GameDetail", "getGameDetail failed", it)
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = "Couldn't load this game",
                    )
                }
        }
    }

    /** Downloads the ROM into `filesDir/roms`, reporting progress in the state. */
    fun download() {
        if (_uiState.value.downloading) return
        scope.launch {
            _uiState.value = _uiState.value.copy(
                downloading = true,
                downloadedBytes = 0L,
                totalBytes = -1L,
                error = null,
            )
            val target = gamesRepository.romFile(romsDir, slug, platform)
            val result = withContext(Dispatchers.IO) {
                gamesRepository.downloadRom(
                    platform = platform.apiPath,
                    slug = slug,
                    target = target,
                    onProgress = { written, total ->
                        _uiState.value = _uiState.value.copy(
                            downloadedBytes = written,
                            totalBytes = total,
                        )
                    },
                )
            }
            result
                .onSuccess {
                    _uiState.value = _uiState.value.copy(downloading = false, romReady = true)
                }
                .onFailure {
                    Logger.e("GameDetail", "ROM download failed", it)
                    _uiState.value = _uiState.value.copy(
                        downloading = false,
                        error = "Download failed",
                    )
                }
        }
    }

    /** The absolute path of the downloaded ROM, or null when it is not on disk yet. */
    fun romPath(): String? {
        val file = gamesRepository.romFile(romsDir, slug, platform)
        return if (file.exists()) file.absolutePath else null
    }
}
