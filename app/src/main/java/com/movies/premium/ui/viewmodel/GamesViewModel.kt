package com.movies.premium.ui.viewmodel

import android.content.Context
import com.movies.premium.data.local.prefs.FavouriteGame
import com.movies.premium.data.local.prefs.GameFavouritesStore
import com.movies.premium.data.remote.model.Game
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

/**
 * The state of the games screen.
 *
 * The original's view model is `com.app.mlounge.ui.viewmodel.t`; the state it
 * exposes is reproduced here field by field:
 *
 *  - the platform (SNES by default), the games, the loading and the error flags,
 *  - the genre and the decade lists of the platform — they come from
 *    `api/games/{platform}/stats` — and the genre and the decade the user
 *    picked,
 *  - the favourites mode and the favourites themselves,
 *  - the detail of the game whose sheet is open, the ROM download and its
 *    progress.
 */
data class GamesUiState(
    val platform: GamePlatform = GamePlatform.SNES,
    val games: List<Game> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val genres: List<String> = emptyList(),
    val decades: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val selectedDecade: String? = null,
    val favouritesMode: Boolean = false,
    val favourites: List<FavouriteGame> = emptyList(),
    /** The game whose sheet is open, or null when no sheet is open. */
    val detailSlug: String? = null,
    /** The platform of the game whose sheet is open. */
    val detailPlatform: GamePlatform = GamePlatform.SNES,
    val detail: GameDetailResponse? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val downloading: Boolean = false,
    /** The bytes of the ROM that are on disk, or 0 when nothing is downloading. */
    val downloadedBytes: Long = 0L,
    /** The size of the ROM, or -1 when the server does not report it. */
    val totalBytes: Long = -1L,
    /** True when the ROM is already on disk — then the game can be played. */
    val romReady: Boolean = false,
)

/**
 * Backs the games screen and the game sheet that opens on top of it.
 *
 * The original's rules are reproduced one to one:
 *
 *  - changing the platform clears the genre, the decade and the favourites mode
 *    and reloads the catalogue and the statistics,
 *  - the genre and the decade are sent to the backend, which filters the
 *    catalogue,
 *  - the ROM of a game lives in `filesDir/roms/<slug><extension>` and the game
 *    can only be played once that file exists.
 */
@HiltViewModel
class GamesViewModel @Inject constructor(
    private val gamesRepository: GamesRepository,
    @ApplicationContext private val context: Context,
) : SafeViewModel() {

    private val favouritesStore = GameFavouritesStore(context)

    private val _uiState = MutableStateFlow(GamesUiState())
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    /** The page that is on screen. The original keeps it in `t.M`. */
    private var page = 1

    /** How many pages the catalogue has. The original keeps it in `t.N`. */
    private var totalPages = 1

    /** Whether the backend announced a next page. The original's `hasNext`. */
    private var hasNext: Boolean? = null

    /** Where the ROMs live — the original's `filesDir/roms`. */
    private val romsDir: File
        get() = File(context.filesDir, "roms").also { it.mkdirs() }

    init {
        // The favourites live in their own store, so the screen only has to
        // watch the flow.
        scope.launch {
            favouritesStore.favourites.collect { favourites ->
                _uiState.value = _uiState.value.copy(favourites = favourites)
            }
        }
        loadStats()
        loadGames()
    }

    /**
     * The genres and the decades of the current platform.
     *
     * The original sorts the genres by the number of games they hold (the most
     * common one first) and the decades by name, newest first.
     */
    private fun loadStats() {
        val platform = _uiState.value.platform
        scope.launch {
            gamesRepository.getStats(platform.apiPath)
                .onSuccess { stats ->
                    val genres = stats.genres.orEmpty()
                        .entries
                        .sortedByDescending { it.value }
                        .map { it.key }
                    val decades = stats.decades.orEmpty().keys.sortedDescending()
                    _uiState.value = _uiState.value.copy(genres = genres, decades = decades)
                }
                .onFailure { Logger.e("GamesViewModel", "getStats failed", it) }
        }
    }

    /** Loads the first page of the catalogue. */
    fun loadGames() {
        val state = _uiState.value
        scope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            gamesRepository.getGames(
                platform = state.platform.apiPath,
                page = 1,
                genre = state.selectedGenre,
                decade = state.selectedDecade,
            )
                .onSuccess { response ->
                    page = 1
                    totalPages = response.totalPages ?: 1
                    hasNext = response.hasNext
                    _uiState.value = _uiState.value.copy(
                        games = response.games.orEmpty(),
                        loading = false,
                        error = null,
                    )
                }
                .onFailure {
                    Logger.e("GamesViewModel", "getGames failed", it)
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = it.message ?: "Failed to load games",
                    )
                }
        }
    }

    /**
     * Loads the next page and appends it.
     *
     * The original skips the games that are already on screen, so a page that
     * repeats an entry does not show it twice.
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore) return
        if (hasNext != true && page >= totalPages) return
        scope.launch {
            _uiState.value = _uiState.value.copy(loadingMore = true)
            gamesRepository.getGames(
                platform = state.platform.apiPath,
                page = page + 1,
                genre = state.selectedGenre,
                decade = state.selectedDecade,
            )
                .onSuccess { response ->
                    page += 1
                    totalPages = response.totalPages ?: totalPages
                    hasNext = response.hasNext
                    val known = _uiState.value.games.mapNotNull { it.slug }.toSet()
                    val more = response.games.orEmpty().filter { game ->
                        val slug = game.slug ?: return@filter true
                        slug !in known
                    }
                    _uiState.value = _uiState.value.copy(
                        games = _uiState.value.games + more,
                        loadingMore = false,
                    )
                }
                .onFailure {
                    Logger.e("GamesViewModel", "loadMore failed", it)
                    _uiState.value = _uiState.value.copy(loadingMore = false)
                }
        }
    }

    /**
     * Switches to another platform.
     *
     * The original clears the genre, the decade and the favourites mode, so
     * that a filter of one platform cannot leak into the next one.
     */
    fun selectPlatform(platform: GamePlatform) {
        if (_uiState.value.platform == platform) return
        _uiState.value = _uiState.value.copy(
            platform = platform,
            selectedGenre = null,
            selectedDecade = null,
            favouritesMode = false,
            games = emptyList(),
        )
        loadStats()
        loadGames()
    }

    /** Picks a genre — or clears it when the same one is picked again. */
    fun selectGenre(genre: String?) {
        if (_uiState.value.selectedGenre == genre) return
        _uiState.value = _uiState.value.copy(selectedGenre = genre)
        loadGames()
    }

    /** Picks a decade — or clears it when the same one is picked again. */
    fun selectDecade(decade: String?) {
        if (_uiState.value.selectedDecade == decade) return
        _uiState.value = _uiState.value.copy(selectedDecade = decade)
        loadGames()
    }

    /** Shows the favourites instead of the catalogue. */
    fun toggleFavouritesMode() {
        _uiState.value = _uiState.value.copy(
            favouritesMode = !_uiState.value.favouritesMode,
        )
    }

    /**
     * Opens the sheet of a game and loads its details.
     *
     * The original asks the backend for the details of the game and checks at
     * the same time whether the ROM is already on disk — the Play button is
     * only enabled when it is.
     */
    fun openGame(slug: String, platform: GamePlatform = _uiState.value.platform) {
        _uiState.value = _uiState.value.copy(
            detailSlug = slug,
            detailPlatform = platform,
            detail = null,
            detailLoading = true,
            detailError = null,
            downloading = false,
            downloadedBytes = 0L,
            totalBytes = -1L,
            romReady = false,
        )
        scope.launch {
            gamesRepository.getGameDetail(platform.apiPath, slug)
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        detail = detail,
                        detailLoading = false,
                        romReady = gamesRepository.isRomDownloaded(romsDir, slug, platform),
                    )
                }
                .onFailure {
                    Logger.e("GamesViewModel", "getGameDetail failed", it)
                    _uiState.value = _uiState.value.copy(
                        detailLoading = false,
                        detailError = "Couldn't load this game",
                    )
                }
        }
    }

    /** Closes the sheet and forgets the game it showed. */
    fun closeGame() {
        _uiState.value = _uiState.value.copy(
            detailSlug = null,
            detail = null,
            detailLoading = false,
            detailError = null,
            downloading = false,
            downloadedBytes = 0L,
            totalBytes = -1L,
            romReady = false,
        )
    }

    /**
     * Downloads the ROM of the open game into `filesDir/roms`.
     *
     * This is the original's download: the bytes come from
     * `api/games/{platform}/rom/{slug}` and land in the file the emulator is
     * later pointed at.
     */
    fun downloadRom() {
        val state = _uiState.value
        val slug = state.detailSlug ?: return
        if (state.downloading) return
        val platform = state.detailPlatform
        scope.launch {
            _uiState.value = _uiState.value.copy(
                downloading = true,
                downloadedBytes = 0L,
                totalBytes = -1L,
                detailError = null,
            )
            val target = gamesRepository.romFile(romsDir, slug, platform)
            val result = withContext(Dispatchers.IO) {
                gamesRepository.downloadRom(
                    platform = platform.apiPath,
                    slug = slug,
                    target = target,
                ) { written, total ->
                    _uiState.value = _uiState.value.copy(
                        downloadedBytes = written,
                        totalBytes = total,
                    )
                }
            }
            result
                .onSuccess {
                    _uiState.value = _uiState.value.copy(downloading = false, romReady = true)
                }
                .onFailure {
                    Logger.e("GamesViewModel", "ROM download failed", it)
                    _uiState.value = _uiState.value.copy(
                        downloading = false,
                        detailError = "Download failed",
                    )
                }
        }
    }

    /** The absolute path of the ROM of the open game, or null when it is missing. */
    fun romPath(): String? {
        val state = _uiState.value
        val slug = state.detailSlug ?: return null
        val file = gamesRepository.romFile(romsDir, slug, state.detailPlatform)
        return if (file.exists()) file.absolutePath else null
    }

    /** True when the game is one of the favourites. */
    fun isFavourite(slug: String, platform: GamePlatform): Boolean =
        _uiState.value.favourites.any {
            it.slug == slug && it.platform == platform.apiPath
        }

    /** Adds the game to the favourites or removes it again. */
    fun toggleFavourite(
        slug: String,
        platform: GamePlatform,
        name: String,
        posterUrl: String? = null,
        year: String? = null,
    ) {
        scope.launch {
            favouritesStore.toggle(
                FavouriteGame(
                    slug = slug,
                    platform = platform.apiPath,
                    name = name,
                    posterUrl = posterUrl,
                    year = year,
                ),
            )
        }
    }
}
