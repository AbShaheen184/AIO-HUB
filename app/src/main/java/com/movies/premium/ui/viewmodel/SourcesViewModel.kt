package com.movies.premium.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.anime.AnimeSubtitleStore
import com.movies.premium.data.remote.debrid.DebridManagerRegistry
import com.movies.premium.data.remote.debrid.DebridService
import com.movies.premium.data.remote.model.ChqStream
import com.movies.premium.data.repository.AnimeRepository
import com.movies.premium.data.repository.ProviderPreferences
import com.movies.premium.data.repository.SourceCatalog
import com.movies.premium.data.repository.StreamsRepository
import com.movies.premium.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import javax.inject.Inject

/**
 * A source that has been checked for availability.
 *
 * @param stream the provider entry.
 * @param sizeBytes the size reported by the server's `Content-Length`, or null when the
 *   source was not probed.
 * @param available false when the probe failed. The original keeps unreachable sources out
 *   of the list; the replica keeps them but marks them, so a transient failure does not
 *   hide a working source.
 */
data class ResolvedSource(
    val stream: ChqStream,
    val sizeBytes: Long? = null,
    val available: Boolean = true,
)

data class SourcesUiState(
    val loading: Boolean = true,
    val sources: List<ResolvedSource> = emptyList(),
    val error: String? = null,
)

/**
 * The state of the unrestrict flow.
 *
 * The original's state class is `com.app.mlounge.ui.viewmodel.m`
 * (`DebridUnrestrictState`); its fields are reproduced here one to one, because the
 * original's sources screen reads them by name.
 */
data class DebridUnrestrictState(
    /** True while a service is turning the magnet into a direct link. */
    val isLoading: Boolean = false,
    /** The message to show under the title. */
    val error: String? = null,
    /** The title of the error. */
    val errorTitle: String? = null,
    /** True when the user has to pick one of the linked services. */
    val showServicePicker: Boolean = false,
    /** The source that is waiting to be resolved. */
    val pendingStream: ChqStream? = null,
    /** The services the user can pick from. */
    val availableServices: List<DebridService> = emptyList(),
)

/**
 * Backs the sources screen.
 *
 * The original's screen is `com.app.mlounge.ui.screens.sources.a`, backed by
 * `com.app.mlounge.ui.viewmodel.p2`. It reads six arguments off the route —
 * `type`, `id`, `title`, `season`, `episode` and `mode` — resolves the streams for the
 * title, probes each one for availability, and then either plays the picked source or
 * queues it for download depending on `mode`.
 */
@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val streamsRepository: StreamsRepository,
    private val animeRepository: AnimeRepository,
    private val debridRegistry: DebridManagerRegistry,
    private val httpClient: OkHttpClient,
    private val providerPreferences: ProviderPreferences,
    savedStateHandle: SavedStateHandle,
) : SafeViewModel() {

    /**
     * The probe the original's `p2` runs over the resolved streams.
     */
    private val probe = SourceProbe(httpClient)

    /** `"movie"`, `"tv"`, `"anime"`, … */
    val type: String = savedStateHandle.get<String>("type") ?: "movie"

    /** The provider-side id. May be `"<slug>|<tmdbId>"`, as the original's is. */
    val id: String = savedStateHandle.get<String>("id").orEmpty()

    /** The display title. The original URL-decodes it. */
    val title: String = Uri.decode(savedStateHandle.get<String>("title") ?: "Sources")

    val season: Int = savedStateHandle.get<Int>("season")
        ?: savedStateHandle.get<String>("season")?.toIntOrNull()
        ?: 0

    val episode: Int = savedStateHandle.get<Int>("episode")
        ?: savedStateHandle.get<String>("episode")?.toIntOrNull()
        ?: 0

    /** `"play"` or `"download"`. */
    val mode: String = savedStateHandle.get<String>("mode") ?: "play"

    /**
     * The anime server the user picked, e.g. `"gogoanime"`.
     *
     * The original's anime detail screen lets the user pick one of the servers of
     * the episode and hands the name and the category (`"sub"` or `"dub"`) to the
     * source resolver. Both are empty for the other media types.
     */
    val server: String = savedStateHandle.get<String>("server").orEmpty()

    /** The category of the picked server: `"sub"` or `"dub"`. */
    val category: String = savedStateHandle.get<String>("category").orEmpty()

    /**
     * The TMDB id. The original's `id` argument sometimes carries a `|`-separated
     * composite (`"something|1234"`), and the original reads the trailing integer.
     */
    val tmdbId: Int = id.substringAfterLast('|').toIntOrNull()
        ?: id.toIntOrNull()
        ?: 0

    private val _uiState = MutableStateFlow(SourcesUiState())
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    /** The state of the unrestrict flow — the original's `m`. */
    private val _debridState = MutableStateFlow(DebridUnrestrictState())
    val debridState: StateFlow<DebridUnrestrictState> = _debridState.asStateFlow()

    /**
     * The source the player (or the download queue) should use.
     *
     * A torrent source is emitted only after a debrid service turned it into a direct
     * link; every other source is emitted unchanged.
     */
    private val _playbackStream = MutableSharedFlow<ChqStream>(extraBufferCapacity = 1)
    val playbackStream: SharedFlow<ChqStream> = _playbackStream.asSharedFlow()

    init {
        load()
    }

    fun load() {
        scope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val resolved = if (type == "anime") {
                    loadAnimeSources()
                } else {
                    loadProviderSources()
                }
                if (resolved.isEmpty()) {
                    _uiState.value = SourcesUiState(loading = false, error = "No sources available")
                    return@launch
                }
                _uiState.value = SourcesUiState(
                    loading = false,
                    sources = resolved,
                )
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load sources", e)
                _uiState.value = SourcesUiState(
                    loading = false,
                    error = e.message ?: "Failed to load sources",
                )
            }
        }
    }

    /**
     * Resolves the streams of a movie or a series through the CHQ providers.
     *
     * Every stream is probed for availability and for its size; the original
     * runs the two probes behind two independent `Semaphore(4)` gates.
     */
    private suspend fun loadProviderSources(): List<ResolvedSource> {
        val streams = streamsRepository.fetchAll(
            tmdbId = tmdbId,
            mediaType = type,
            season = season.takeIf { it > 0 },
            episode = episode.takeIf { it > 0 },
        )
        return coroutineScope {
            streams.map { stream ->
                async {
                    val alive = probe.isAlive(stream)
                    val size = probe.sizeOf(stream)
                    ResolvedSource(stream, sizeBytes = size, available = alive)
                }
            }.awaitAll()
        }
    }

    /**
     * Resolves the streams of an anime episode through the Anikoto scraper.
     *
     * The original asks the scraper for the sources of the picked server
     * (`getAnimeEpisodeSources(episodeId, server, category)`) and turns every
     * source into a stream of its own:
     *
     * ```
     * ChqStream(
     *     name = "Anikoto Server <n> (<CATEGORY>)",
     *     url = source.url,
     *     quality = "HLS",
     *     type = "hls",
     *     streamType = "hls",
     *     headers = sources.headers,
     *     animeSubtitles = sources.tracks,
     * )
     * ```
     *
     * The tracks of the episode are also put into the process-wide
     * `AnimeSubtitleStore`, because the player route only carries the url and
     * the headers.
     */
    private suspend fun loadAnimeSources(): List<ResolvedSource> {
        // The "Sources (Anime)" switch of the settings screen.
        if (SourceCatalog.ANIME_PROVIDERS.any { it.key in providerPreferences.disabledProviders.first() }) {
            Logger.d(TAG, "The anime sources are switched off — nothing to resolve")
            return emptyList()
        }
        val response = animeRepository.getAnimeEpisodeSources(id, server, category)
        val data = response.data ?: return emptyList()
        AnimeSubtitleStore.set(data.tracks)
        val streams = data.sources.mapIndexed { index, source ->
            ChqStream(
                name = "Anikoto Server ${index + 1} (${category.uppercase()})",
                url = source.url,
                quality = "hls",
                type = "hls",
                streamType = "hls",
                headers = data.headers,
                animeSubtitles = data.tracks,
            )
        }
        return coroutineScope {
            streams.map { stream ->
                async {
                    ResolvedSource(stream, available = probe.isAliveAnime(stream))
                }
            }.awaitAll()
        }
    }

    companion object {
        const val TAG = "SourcesViewModel"
    }

    // --- The unrestrict flow -------------------------------------------------
    //
    // The original's `com.app.mlounge.ui.viewmodel.p2` does the same: it decides
    // whether the picked source is a torrent, hands it to the streams repository
    // (`com.app.mlounge.data.repository.t`), which picks one of the debrid managers,
    // and reports the outcome through the `m` state.

    /**
     * Handles the user picking a source.
     *
     * A torrent source is resolved through a debrid service first; everything else is
     * handed straight to the player.
     */
    fun onSourceSelected(stream: ChqStream) {
        if (!stream.isTorrentSource()) {
            emitPlayback(stream)
            return
        }
        scope.launch {
            val linked = debridRegistry.linkedServices()
            if (linked.isEmpty()) {
                // Deviation: the original's code that opens the service picker when
                // nothing is linked did not survive the decompilation, so the replica
                // reports the problem, keeps the source around and shows the magnet so
                // the user can still open it with another app.
                val magnet = stream.url.orEmpty().ifBlank { stream.infoHash.orEmpty() }
                _debridState.value = DebridUnrestrictState(
                    error = "No debrid service is linked. Link one in Settings to play torrents." +
                        if (magnet.isNotBlank()) "\n\n$magnet" else "",
                    errorTitle = "No debrid service",
                    pendingStream = stream,
                )
                return@launch
            }
            if (linked.size == 1) {
                resolve(linked.first(), stream)
                return@launch
            }
            // More than one service is linked — let the user pick one.
            _debridState.value = DebridUnrestrictState(
                showServicePicker = true,
                pendingStream = stream,
                availableServices = linked,
            )
        }
    }

    /** The user picked one of the services of the picker. */
    fun onDebridServiceSelected(service: DebridService) {
        val pending = _debridState.value.pendingStream
        _debridState.value = DebridUnrestrictState()
        if (pending == null) return
        scope.launch { resolve(service, pending) }
    }

    /** The user closed the service picker. */
    fun dismissDebridPicker() {
        _debridState.value = DebridUnrestrictState()
    }

    /** Clears the error of the last unrestrict attempt. */
    fun dismissDebridError() {
        _debridState.value = DebridUnrestrictState()
    }

    /**
     * Turns a torrent into a direct link and publishes it.
     *
     * The original logs "Unrestricting with <service>" and "Got download URL from
     * <service>" around the call, and turns an HTTP failure into a message that names
     * the service.
     */
    private suspend fun resolve(service: DebridService, stream: ChqStream) {
        _debridState.value = DebridUnrestrictState(isLoading = true)
        Logger.d(TAG, "Unrestricting with ${service.displayName}")
        val result = debridRegistry.unrestrict(service, stream)
        result.onSuccess { link ->
            Logger.d(TAG, "Got download URL from ${service.displayName}")
            _debridState.value = DebridUnrestrictState()
            emitPlayback(
                stream.copy(
                    url = link.url,
                    headers = stream.headers.orEmpty() + link.headers,
                    isTorrent = false,
                ),
            )
        }.onFailure { error ->
            Logger.e(TAG, "Unrestrict failed", error)
            val (title, message) = describeError(service, error)
            _debridState.value = DebridUnrestrictState(
                error = message,
                errorTitle = title,
                pendingStream = stream,
            )
        }
    }

    private fun emitPlayback(stream: ChqStream) {
        scope.launch { _playbackStream.emit(stream) }
    }

    /**
     * Turns a failure into the message the original shows.
     *
     * The original's `p2` maps the HTTP codes like this: 451 is the Real-Debrid DMCA
     * notice, 401 is an authentication failure, 403 is a denied token and anything else
     * is reported with its code.
     */
    private fun describeError(service: DebridService, error: Throwable): Pair<String?, String> {
        val name = service.displayName
        val code = (error as? HttpException)?.code()
        return when (code) {
            451 -> "Real Debrid DMCA" to
                "Due to Real Debrid following current DMCA laws this link is unavailable " +
                "for legal reasons. Please try a different source or use a different service."

            401 -> "$name: authentication failed" to "$name: authentication failed — please re-login"
            403 -> "$name: access denied" to "$name: access denied — token may have expired"
            null -> null to "$name: ${error.message ?: "Unknown error"}"
            else -> "$name: HTTP $code error" to "$name: HTTP $code error"
        }
    }
}

/**
 * True when the source has to be resolved by a debrid service.
 *
 * The original's `p2.e` checks the same four things: the `is_torrent` flag, the
 * `torrent` stream type, a `magnet:` url and a url that is not an http one.
 */
fun ChqStream.isTorrentSource(): Boolean {
    if (isTorrent) return true
    if (streamType.equals("torrent", ignoreCase = true)) return true
    val link = url.orEmpty()
    if (link.startsWith("magnet:", ignoreCase = true)) return true
    if (link.isNotEmpty() && !link.startsWith("http", ignoreCase = true)) return true
    return false
}

/**
 * Formats a byte count the way the original does (`com.app.mlounge.ui.viewmodel.p2.g`).
 */
fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(java.util.Locale.US, "%.0f MB", bytes / 1_048_576.0)
    bytes >= 1_024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1_024.0)
    else -> "$bytes B"
}
