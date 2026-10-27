@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.movies.premium.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.gson.JsonParser
import com.movies.premium.data.anime.AnimeSubtitleStore
import com.movies.premium.data.local.dao.ContinueWatchingDao
import com.movies.premium.data.local.entity.ContinueWatchingEntity
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.data.remote.api.TmdbApi
import com.movies.premium.data.remote.debrid.DebridManagerRegistry
import com.movies.premium.data.remote.debrid.DebridService
import com.movies.premium.data.remote.model.ChqStream
import com.movies.premium.data.remote.ntv.LoopbackHlsProxy
import com.movies.premium.data.remote.ntv.NtvStreamExtractor
import com.movies.premium.data.remote.ntv.NtvStreamResult
import com.movies.premium.data.remote.ntv.NtvUriParts
import com.movies.premium.data.repository.StreamsRepository
import com.movies.premium.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.util.Locale
import javax.inject.Inject

/**
 * How the video is scaled inside the player.
 *
 * The original's settings screen offers four modes (`fit`, `4:3`, `16:9` and
 * `stretch` — `com.app.mlounge.ui.screens.settings.h0`), and the player reads the
 * same preference. The replica's player offers the two the brief asks for:
 * "Fit to Screen" and "Stretch to Fill".
 */
enum class PlayerDisplayMode(val label: String) {
    FIT("Fit to Screen"),
    STRETCH("Stretch to Fill"),
}

/** The white the original uses for the subtitle text. */
const val SUBTITLE_COLOR_WHITE: Int = -1

/** The yellow the original uses for the subtitle text. */
const val SUBTITLE_COLOR_YELLOW: Int = -256

/** The green the original uses for the subtitle text. */
const val SUBTITLE_COLOR_GREEN: Int = -16711936

/** The cyan the original uses for the subtitle text. */
const val SUBTITLE_COLOR_CYAN: Int = -16711681

/** The default background of the subtitle text: half transparent black. */
val SUBTITLE_BACKGROUND_SEMI: Int = 0x80000000.toInt()

/**
 * The background colours the original's options sheet offers.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.player.w`:
 * `Color.argb(128, 0, 0, 0)` is "Semi", `Color.argb(255, 0, 0, 0)` is "Black",
 * `0` is "None" and `Color.argb(160, 50, 50, 50)` is "Gray".
 *
 * The brief also mentioned "Mono"; the original has no such background — "Mono" is
 * one of the *audio* channel labels.
 */
val PLAYER_BACKGROUND_COLORS: List<Pair<Int, String>> = listOf(
    SUBTITLE_BACKGROUND_SEMI to "Semi",
    0xFF000000.toInt() to "Black",
    0 to "None",
    0xA0323232.toInt() to "Gray",
)

/**
 * The text colours the original's options sheet offers.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.player.w`, which lists the
 * four colour ints `-1`, `-256`, `-16711936` and `-16711681` — white, yellow,
 * green and cyan. The original shows them as unlabelled swatches; the names
 * below are only there to make the replica's list readable.
 */
val PLAYER_TEXT_COLORS: List<Pair<Int, String>> = listOf(
    SUBTITLE_COLOR_WHITE to "White",
    SUBTITLE_COLOR_YELLOW to "Yellow",
    SUBTITLE_COLOR_GREEN to "Green",
    SUBTITLE_COLOR_CYAN to "Cyan",
)

/**
 * An episode of the season the player is showing.
 *
 * @param number the episode number.
 * @param name the episode's title.
 * @param overview the episode's synopsis.
 */
data class PlayerEpisode(
    val number: Int,
    val name: String,
    val overview: String = "",
)

/**
 * A request to play something.
 *
 * The original's player view model owns the ExoPlayer and calls `setMediaSource`
 * itself. The replica's player screen owns the player — the screen was already
 * written that way — so the view model publishes what has to be played and the
 * screen applies it.
 *
 * The request is kept in a [StateFlow] rather than in a one-shot flow, because the
 * view model starts resolving the sources as soon as it is created, which is before
 * the screen has collected anything. A retained value makes sure the first request
 * is never lost; the `token` tells two requests apart.
 *
 * @param url the url to play.
 * @param headers the request headers the stream needs.
 * @param mimeType the mime type of the stream, when it is known.
 * @param startPositionMs where the playback has to start.
 * @param playWhenReady false while the "Resume Playback?" dialog is open.
 * @param sidecarSubtitle the subtitle the media source has to carry.
 * @param subtitleLanguage the language of the sidecar subtitle.
 * @param title the title shown on top of the player.
 * @param token a number that grows with every request.
 */
data class PlaybackRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val startPositionMs: Long = 0L,
    val playWhenReady: Boolean = true,
    val sidecarSubtitle: MediaItem.SubtitleConfiguration? = null,
    val subtitleLanguage: String? = null,
    val title: String? = null,
    val token: Long = 0L,
)

/**
 * A request to select a track.
 *
 * @param type the kind of track, `C.TRACK_TYPE_AUDIO` or `C.TRACK_TYPE_TEXT`.
 * @param groupIndex the index of the track group.
 * @param trackIndex the index of the track inside the group.
 * @param language the language of the track, when it has one.
 */
data class TrackSelectionRequest(
    val type: Int,
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String? = null,
)

/**
 * The state of the player.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.player.n0` (R8 name `n0`) field
 * for field — the original's `n0` names its fields in its `toString()`, and they are
 * reproduced here one to one so that the two players behave the same.
 *
 * The last six fields are replica additions: the title, the source strip, the
 * display mode, the episode list and the rendered subtitle text have no counterpart
 * in the original's state class.
 */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val showControls: Boolean = true,
    val showResumeDialog: Boolean = false,
    val showAudioTrackPicker: Boolean = false,
    val showSubtitlePicker: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showNextEpisode: Boolean = false,
    val currentPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val resumePositionMs: Long = 0L,
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val subtitles: List<SubtitleItem> = emptyList(),
    val activeSubtitle: SubtitleItem? = null,
    val subtitlesLoading: Boolean = false,
    val subtitleSizeSp: Float = 16.0f,
    val subtitleColor: Int = SUBTITLE_COLOR_WHITE,
    val subtitleBgColor: Int = SUBTITLE_BACKGROUND_SEMI,
    val subtitleOffsetMs: Long = 0L,
    val subtitleContentCache: String? = null,
    val playbackStarted: Boolean = false,
    val isHlsStream: Boolean = false,
    val videoMimeType: String? = null,
    val errorMessage: String? = null,
    val transientMessage: String? = null,
    val seekWarning: String? = null,
    val isSeekable: Boolean = true,
    val showSeekRecovery: Boolean = false,
    val isAutoPlayingNext: Boolean = false,
    val autoPlayStatus: String? = null,
    val activeTitle: String? = null,
    val autoPlayEnabled: Boolean = true,
    val oneClickPlayEnabled: Boolean = false,
    val isAudioOnly: Boolean = false,
    val audioSessionId: Int = 0,
    val seekIncrementMs: Long = 30_000L,
    // --- replica additions ---
    val title: String = "",
    val streams: List<ChqStream> = emptyList(),
    val selectedStream: ChqStream? = null,
    val displayMode: PlayerDisplayMode = PlayerDisplayMode.FIT,
    val episodes: List<PlayerEpisode> = emptyList(),
    val subtitleText: String? = null,
)

/**
 * Backs the player destination.
 *
 * The original's player view model is `com.app.mlounge.ui.screens.player.a1` and it
 * is the piece this class mirrors. The original's own logic is spread over a dozen
 * small classes, all of them in the same package:
 *
 * - `a1` — the state, the player and the playback entry point.
 * - `d0` — `initializePlayer`, which sets the load control and starts the resume
 *   lookup.
 * - `v0` — the resolve-and-apply coroutine, which probes the stream and reports
 *   "Source offline" or "Unsupported file type".
 * - `u0` — the reachability probe.
 * - `t0` — the `HEAD` probe that reads the content type and the length.
 * - `q0` — the collector of the source flow, which reports "Finding sources..." and
 *   "Trying Source i/n: name".
 * - `z0` — the `Player.Listener`, which reports the playback errors.
 * - `w0` — the live re-resolve, which reports "Re-resolved live stream, retrying
 *   playback" and "Live re-resolve failed".
 * - `r0` — the next-episode coroutine.
 * - `x0` — the seek watchdog.
 * - `p0` — the sidecar subtitle applier.
 *
 * The replica splits the work the same way: the view model owns the state, the
 * messages and the decisions, and the screen owns the ExoPlayer.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val streamsRepository: StreamsRepository,
    private val debridRegistry: DebridManagerRegistry,
    private val subtitles: PlayerSubtitlesViewModel,
    private val ntvExtractor: NtvStreamExtractor,
    private val client: OkHttpClient,
    private val continueWatchingDao: ContinueWatchingDao,
    private val preferences: AppPreferences,
    private val tmdbApi: TmdbApi,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : SafeViewModel() {

    // --- the route -------------------------------------------------------------

    /** The url the route carries. */
    private val url: String = savedStateHandle.get<String>("url").orEmpty()

    /** The title of the media. */
    private val title: String = savedStateHandle.get<String>("title").orEmpty()

    /** The headers the route carries, newline separated `key=value` pairs. */
    private val headers: Map<String, String> = parseHeaders(
        savedStateHandle.get<String>("headers").orEmpty(),
    )

    /** `"movie"`, `"tv"`, `"anime"`, … */
    private val contentType: String = savedStateHandle.get<String>("contentType").orEmpty()

    /** The kind of stream: `"live"`, `"torrent"`, … */
    private val streamType: String = savedStateHandle.get<String>("streamType").orEmpty()

    private val tmdbId: Int = savedStateHandle.get<Int>("tmdbId") ?: 0

    private val season: Int = savedStateHandle.get<Int>("season") ?: 0

    private val episode: Int = savedStateHandle.get<Int>("episode") ?: 0

    /** The key the resume position is stored under. */
    private val contentKey: String = savedStateHandle.get<String>("contentKey").orEmpty()

    /** The url to fall back to when the primary one does not play. */
    private val fallbackUrl: String = savedStateHandle.get<String>("fallbackUrl").orEmpty()

    // --- the state -------------------------------------------------------------

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            title = title,
            activeTitle = title.ifBlank { null },
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /**
     * The request the screen has to play.
     *
     * It is a [StateFlow] and not a one-shot flow, because the view model starts
     * resolving as soon as it is created — before the screen has collected
     * anything. The screen remembers the token it has applied so that a retained
     * request is not applied twice.
     */
    private val _playbackRequest = MutableStateFlow<PlaybackRequest?>(null)
    val playbackRequest: StateFlow<PlaybackRequest?> = _playbackRequest.asStateFlow()

    /** The track the screen has to select. */
    private val trackChannel = Channel<TrackSelectionRequest>(Channel.BUFFERED)
    val trackSelections: Flow<TrackSelectionRequest> = trackChannel.receiveAsFlow()

    /** The position the screen has to seek to. */
    private val seekChannel = Channel<Long>(Channel.BUFFERED)
    val seekRequests: Flow<Long> = seekChannel.receiveAsFlow()

    /**
     * The unrestrict flow.
     *
     * The original's player uses the same state class as its sources screen
     * (`com.app.mlounge.ui.viewmodel.m`), so the replica reuses
     * [DebridUnrestrictState] here as well and the two screens behave identically.
     */
    private val _debridState = MutableStateFlow(DebridUnrestrictState())
    val debridState: StateFlow<DebridUnrestrictState> = _debridState.asStateFlow()

    // --- the work in flight ----------------------------------------------------

    private var resolveJob: Job? = null
    private var seekWatchdogJob: Job? = null
    private var controlsHideJob: Job? = null
    private var episodesJob: Job? = null
    private var liveProxy: LoopbackHlsProxy? = null
    private var playbackToken = 0L

    /** The url that is playing right now. */
    private var currentUrl: String = ""

    /** The `ntv://` uri of the live stream, when the player is showing one. */
    private var liveUri: String = ""

    /** How often the live stream has been re-resolved. */
    private var liveAttempts: Int = 0

    /** The last request, kept so the resume dialog can replay it. */
    private var lastRequest: PlaybackRequest? = null

    /** The cues of the subtitle that is on screen. */
    private var cues: List<SubtitleCue> = emptyList()

    /** The episode the player is showing. */
    private var currentEpisode: Int = episode

    /**
     * The number of the episode the player is showing.
     */
    val episodeNumber: Int get() = currentEpisode

    /** The scope the resume position is written with; it outlives the view model. */
    private val saveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        load()
        scope.launch {
            preferences.autoPlayNext.collect { enabled ->
                _uiState.value = _uiState.value.copy(autoPlayEnabled = enabled)
            }
        }
        scope.launch {
            preferences.subtitleSizeSp.collect { value ->
                _uiState.value = _uiState.value.copy(subtitleSizeSp = value)
            }
        }
        scope.launch {
            preferences.subtitleTextColor.collect { value ->
                _uiState.value = _uiState.value.copy(subtitleColor = value)
            }
        }
        scope.launch {
            preferences.subtitleBgColor.collect { value ->
                _uiState.value = _uiState.value.copy(subtitleBgColor = value)
            }
        }
        scope.launch {
            preferences.playerSeekIncrementMs.collect { value ->
                _uiState.value = _uiState.value.copy(seekIncrementMs = value)
            }
        }
        scope.launch {
            preferences.emuAspectRatio.collect { value ->
                _uiState.value = _uiState.value.copy(
                    displayMode = if (value == "stretch") {
                        PlayerDisplayMode.STRETCH
                    } else {
                        PlayerDisplayMode.FIT
                    },
                )
            }
        }
    }

    // --- the sources -----------------------------------------------------------

    /**
     * Finds the sources and starts the playback.
     *
     * The original's player asks its repository for the sources of the title and
     * collects the flow; the collector reports "Finding sources..." and then
     * "Trying Source i/n: name" for every source it tries.
     */
    fun load() {
        resolveJob?.cancel()
        resolveJob = scope.launch {
            _uiState.value = _uiState.value.copy(
                isBuffering = true,
                errorMessage = null,
                transientMessage = "Finding sources...",
            )
            val fetched = runCatching {
                streamsRepository.fetchAll(
                    tmdbId = tmdbId,
                    mediaType = contentType.ifBlank { "movie" },
                    season = season.takeIf { it > 0 },
                    episode = episode.takeIf { it > 0 },
                )
            }.getOrDefault(emptyList())

            val candidates = buildCandidates(fetched)
            _uiState.value = _uiState.value.copy(streams = candidates)
            if (candidates.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isBuffering = false,
                    transientMessage = null,
                    errorMessage = "No playable link found.",
                )
                return@launch
            }
            resolveSources(candidates, 0)
        }
    }

    /**
     * The list of sources to try.
     *
     * The url the route carries comes first, then the url of the fallback, and
     * finally the sources the providers answered with.
     */
    private fun buildCandidates(fetched: List<ChqStream>): List<ChqStream> {
        val candidates = mutableListOf<ChqStream>()
        if (url.isNotBlank()) {
            candidates.add(
                ChqStream(
                    name = title.ifBlank { null },
                    url = url,
                    quality = streamType.ifBlank { null },
                    type = contentType.ifBlank { null },
                    streamType = streamType.ifBlank { null },
                    headers = headers.ifEmpty { null },
                ),
            )
        }
        if (fallbackUrl.isNotBlank() && fallbackUrl != url) {
            candidates.add(
                ChqStream(
                    name = title.ifBlank { null },
                    url = fallbackUrl,
                    type = contentType.ifBlank { null },
                    streamType = streamType.ifBlank { null },
                    headers = headers.ifEmpty { null },
                ),
            )
        }
        val seen = candidates.mapNotNull { it.url }.toSet()
        for (stream in fetched) {
            val link = stream.url
            if (link.isNullOrBlank() || link in seen) continue
            candidates.add(stream)
        }
        return candidates
    }

    // --- the source resolution -------------------------------------------------

    /**
     * Tries the sources one after the other.
     *
     * A source that does not play falls through to the next one; the error is only
     * reported when the last source failed as well.
     */
    private suspend fun resolveSources(candidates: List<ChqStream>, from: Int) {
        var index = from
        var lastError: String? = null
        while (index < candidates.size) {
            val candidate = candidates[index]
            val name = candidate.name?.takeIf { it.isNotBlank() }
                ?: candidate.quality?.takeIf { it.isNotBlank() }
                ?: "Source ${index + 1}"
            _uiState.value = _uiState.value.copy(
                isBuffering = true,
                errorMessage = null,
                transientMessage = "Trying Source ${index + 1}/${candidates.size}: $name",
                selectedStream = candidate,
            )
            when (val result = prepare(candidate)) {
                is Prepared.Ready -> {
                    play(result.request, result.subtitles)
                    return
                }

                is Prepared.NeedsService -> {
                    _debridState.value = DebridUnrestrictState(
                        showServicePicker = true,
                        pendingStream = candidate,
                        availableServices = result.services,
                    )
                    return
                }

                is Prepared.Failed -> {
                    Logger.d(TAG, "Source $name failed: ${result.message}")
                    lastError = result.message
                    index++
                }
            }
        }
        _uiState.value = _uiState.value.copy(
            isBuffering = false,
            transientMessage = null,
            errorMessage = lastError ?: "No playable link found.",
        )
    }

    /**
     * Turns a source into something that can be played.
     */
    private suspend fun prepare(stream: ChqStream): Prepared {
        val link = stream.url.orEmpty()
        if (link.isBlank()) return Prepared.Failed("No playable link found.")

        // A torrent is resolved by a debrid service first.
        if (stream.isTorrentSource()) {
            val linked = debridRegistry.linkedServices()
            if (linked.isEmpty()) {
                return Prepared.Failed(
                    "No debrid service is linked. Link one in Settings to play torrents.",
                )
            }
            if (linked.size > 1) return Prepared.NeedsService(linked)
            return prepareWithDebrid(linked.first(), stream)
        }

        // A live stream comes in as an `ntv://` uri and is resolved by the proxy
        // path, so it is not probed here.
        if (link.startsWith(NtvUriParts.SCHEME)) {
            liveUri = link
            return Prepared.Ready(
                request = requestFor(url = link, headers = stream.headers.orEmpty(), isHls = true),
                subtitles = externalSubtitles(stream),
            )
        }

        val probe = probeSource(link, stream.headers.orEmpty())
        when (probe) {
            is Probe.Unsupported -> return Prepared.Failed("Unsupported file type")
            is Probe.Offline -> return Prepared.Failed("Source offline")
            is Probe.Ok -> {
                Logger.d(
                    TAG,
                    "Resolved url=$link type=${probe.contentType} len=${probe.length} " +
                        "ranges=${probe.acceptRanges}",
                )
                Logger.d(
                    TAG,
                    "Playback Decision: isMkv=${probe.isMkv}, " +
                        "isVeryLarge=${probe.length > VERY_LARGE_BYTES}, " +
                        "acceptRanges=${probe.acceptRanges}, " +
                        "useLinearSeekMap=${probe.isMkv && !probe.acceptRanges}",
                )
                if (probe.isVeryLargeMkv) {
                    _uiState.value = _uiState.value.copy(
                        seekWarning = "Very large file: Seeking may be slow or restricted.",
                    )
                }
                return Prepared.Ready(
                    request = requestFor(
                        url = link,
                        headers = stream.headers.orEmpty(),
                        mimeType = probe.contentType,
                        isHls = probe.isHls,
                    ),
                    subtitles = externalSubtitles(stream),
                )
            }
        }
    }

    /**
     * Resolves a torrent through a debrid service.
     */
    private suspend fun prepareWithDebrid(
        service: DebridService,
        stream: ChqStream,
    ): Prepared {
        _debridState.value = DebridUnrestrictState(isLoading = true)
        val result = debridRegistry.unrestrict(service, stream)
        val link = result.getOrNull()
        if (link == null) {
            _debridState.value = DebridUnrestrictState()
            val error = result.exceptionOrNull() ?: IllegalStateException("Unknown error")
            val (errorTitle, message) = describeError(service, error)
            Logger.e(TAG, "Unrestricting with ${service.displayName} failed: ${error.message}")
            return Prepared.Failed(message ?: errorTitle ?: "Source offline")
        }
        _debridState.value = DebridUnrestrictState()
        val probe = probeSource(link.url, stream.headers.orEmpty())
        if (probe is Probe.Offline) return Prepared.Failed("Source offline")
        if (probe is Probe.Unsupported) return Prepared.Failed("Unsupported file type")
        val ok = probe as? Probe.Ok
        return Prepared.Ready(
            request = requestFor(
                url = link.url,
                headers = stream.headers.orEmpty() + link.headers,
                mimeType = ok?.contentType,
                isHls = ok?.isHls ?: false,
            ),
            subtitles = externalSubtitles(stream),
        )
    }

    /**
     * Builds the playback request of a source.
     */
    private fun requestFor(
        url: String,
        headers: Map<String, String>,
        mimeType: String? = null,
        isHls: Boolean = false,
    ): PlaybackRequest {
        playbackToken++
        currentUrl = url
        _uiState.value = _uiState.value.copy(
            isHlsStream = isHls,
            videoMimeType = mimeType,
        )
        return PlaybackRequest(
            url = url,
            headers = headers,
            mimeType = mimeType,
            startPositionMs = 0L,
            playWhenReady = true,
            title = title.ifBlank { null },
            token = playbackToken,
        )
    }

    /**
     * The external subtitles a source brings along.
     *
     * The original's player takes the tracks of the anime episode out of the
     * process-wide `AnimeSubtitleStore`
     * (`com.app.mlounge.ui.i0` calls `AnimeSubtitleStore.INSTANCE.consume()`);
     * the store is filled by the sources screen when it resolves the episode.
     * The replica's sources carry the tracks in `animeSubtitles` as well, and
     * that list is the fallback.
     */
    private fun externalSubtitles(stream: ChqStream): List<SubtitleItem> {
        val tracks = AnimeSubtitleStore.consume().ifEmpty { stream.animeSubtitles.orEmpty() }
        return tracks.mapNotNull { track ->
            val link = track.file?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SubtitleItem(
                label = track.label?.takeIf { it.isNotBlank() } ?: track.kind.orEmpty(),
                url = link,
                lang = track.kind.orEmpty(),
                isDefault = track.isDefault == true,
            )
        }
    }

    // --- the reachability probe ------------------------------------------------

    /**
     * The result of the probe of a source.
     */
    private sealed interface Probe {
        data class Ok(
            val contentType: String?,
            val length: Long,
            val acceptRanges: Boolean,
            val url: String = "",
        ) : Probe {
            val isHls: Boolean
                get() = contentType?.contains("mpegurl", ignoreCase = true) == true

            /**
             * True for a Matroska file.
             *
             * Mirrors the original's `v0`: a file is a Matroska file when its url
             * carries the `.mkv` extension or when the server calls it one.
             */
            val isMkv: Boolean
                get() = url.contains(".mkv", ignoreCase = true) ||
                    contentType?.contains("matroska", ignoreCase = true) == true

            /**
             * True for a Matroska file that is too big to seek in.
             *
             * The original warns from ten gibibytes on.
             */
            val isVeryLargeMkv: Boolean
                get() = isMkv && length > VERY_LARGE_BYTES
        }

        data object Unsupported : Probe
        data object Offline : Probe
    }

    /**
     * Checks that a source can be played.
     *
     * Mirrors the original's `u0` (the reachability probe) and `t0` (the `HEAD`
     * probe): a source that answers with an archive or an HTML page is
     * "unsupported", a source that does not answer is "offline", and everything
     * else is fine. The original does not probe the hosts it knows are direct.
     */
    private suspend fun probeSource(
        url: String,
        headers: Map<String, String>,
    ): Probe {
        if (!url.startsWith("http", ignoreCase = true)) {
            // A local file — a completed download — always plays.
            return Probe.Ok(null, 0L, false, url)
        }
        if (KNOWN_DIRECT_HOSTS.any { url.contains(it, ignoreCase = true) }) {
            return Probe.Ok(null, 0L, false, url)
        }
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .apply { headers.forEach { (key, value) -> header(key, value) } }
                    .build()
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    val contentType = response.header("Content-Type")
                    val disposition = response.header("Content-Disposition")
                    Logger.d(
                        TAG,
                        "resolveStreamInfo (HEAD): code=$code, url=$url, " +
                            "cl=${response.header("Content-Length")}",
                    )
                    if (code in 200..399) {
                        if (isUnsupported(contentType, disposition)) {
                            return@withContext Probe.Unsupported
                        }
                        val length = response.header("Content-Length")?.toLongOrNull() ?: 0L
                        val ranges = response.header("Accept-Ranges")
                            ?.contains("bytes", ignoreCase = true) == true
                        Probe.Ok(contentType, length, ranges, url)
                    } else if (code == 405 && url.contains("comet.elfhosted.com", ignoreCase = true)) {
                        Probe.Ok(contentType, 0L, false, url)
                    } else {
                        Probe.Offline
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "resolveStreamInfo (HEAD) error: ${e.message}")
                Probe.Offline
            }
        }
    }

    /**
     * True when the server answered with an archive or an HTML page.
     *
     * Mirrors the original's `u0`.
     */
    private fun isUnsupported(contentType: String?, disposition: String?): Boolean {
        val type = contentType?.lowercase(Locale.ROOT).orEmpty()
        if (UNSUPPORTED_TYPES.any { type.contains(it) }) return true
        val name = disposition?.lowercase(Locale.ROOT).orEmpty()
        return name.contains(".zip") || name.contains(".rar")
    }

    // --- the playback ----------------------------------------------------------

    /**
     * Starts the playback of a resolved source.
     */
    private suspend fun play(request: PlaybackRequest, externalSubtitles: List<SubtitleItem>) {
        val resume = lookupResumePosition()
        val showResume = resume > 0L

        _uiState.value = _uiState.value.copy(
            isBuffering = true,
            errorMessage = null,
            transientMessage = null,
            showResumeDialog = showResume,
            resumePositionMs = resume,
            subtitles = addExternalSubtitles(_uiState.value.subtitles, externalSubtitles),
        )

        val prepared = prepareUrl(request.url, request.headers)
        val finalRequest = request.copy(
            url = prepared.url,
            headers = prepared.headers,
            startPositionMs = 0L,
            playWhenReady = !showResume,
            token = ++playbackToken,
        )
        lastRequest = finalRequest
        _playbackRequest.value = finalRequest
    }

    /**
     * Prepares the url of a live stream.
     *
     * Mirrors the original's `a1.f(...)`: a live stream that comes in as an
     * `ntv://` uri is served through a loopback proxy, so the player sees a plain
     * HTTP playlist it can refresh.
     */
    private suspend fun prepareUrl(url: String, headers: Map<String, String>): PreparedUrl {
        if (!url.startsWith(NtvUriParts.SCHEME)) return PreparedUrl(url, headers)
        val parts = NtvUriParts.parse(url) ?: return PreparedUrl(url, headers)
        val resolved = resolveNtv(parts) ?: return PreparedUrl(url, headers)
        liveProxy?.stop()
        val proxy = LoopbackHlsProxy(client, resolved.url, resolved.headers) {
            resolveNtv(parts)
        }
        val local = proxy.localUrl
        if (local == null) {
            Logger.d(TAG, "Loopback proxy failed to start, playing direct")
            return PreparedUrl(resolved.url, resolved.headers)
        }
        liveProxy = proxy
        Logger.d("LoopbackHlsProxy", "serving ${resolved.url} via $local")
        Logger.d(TAG, "Playing via loopback proxy: $local")
        return PreparedUrl(local, resolved.headers)
    }

    /**
     * Resolves an `ntv://` uri into a playable stream.
     */
    private suspend fun resolveNtv(parts: NtvUriParts): NtvStreamResult? =
        withContext(Dispatchers.IO) {
            ntvExtractor.resolve(
                context = context,
                name = parts.channelName,
                code = parts.channelCode,
                provider = parts.server,
                streamId = parts.channelId,
                embedUrl = parts.rawUrl.takeIf { it.isNotBlank() },
            )
        }

    // --- the resume position ---------------------------------------------------

    /**
     * The position the user stopped at.
     *
     * The original's player reads it from its own database in `a1.g()` and shows the
     * "Resume Playback?" dialog when there is one.
     */
    private suspend fun lookupResumePosition(): Long {
        val key = resumeKey()
        if (key.isBlank()) return 0L
        return try {
            val rows = continueWatchingDao.observeAll().first()
            rows.firstOrNull { it.key == key }?.progressMs ?: 0L
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read the resume position", e)
            0L
        }
    }

    /**
     * The key the resume position is stored under.
     */
    private fun resumeKey(): String = when {
        contentKey.isNotBlank() -> contentKey
        tmdbId > 0 -> "tmdb:$tmdbId-$contentType-$season-$episode"
        else -> ""
    }

    /**
     * The user answered the "Resume Playback?" dialog.
     *
     * @param resume true to continue where the user stopped, false to start over.
     */
    fun onResumeAnswered(resume: Boolean) {
        val request = lastRequest ?: return
        _uiState.value = _uiState.value.copy(showResumeDialog = false)
        _playbackRequest.value = request.copy(
            startPositionMs = if (resume) _uiState.value.resumePositionMs else 0L,
            playWhenReady = true,
            token = ++playbackToken,
        )
    }

    // --- the user picks a source ------------------------------------------------

    /**
     * The user picked one of the sources of the strip.
     */
    fun selectStream(stream: ChqStream) {
        _uiState.value = _uiState.value.copy(selectedStream = stream)
        resolveJob?.cancel()
        resolveJob = scope.launch { resolveSources(listOf(stream), 0) }
    }

    /**
     * The user picked one of the debrid services.
     */
    fun onDebridServiceSelected(service: DebridService) {
        val pending = _debridState.value.pendingStream
        _debridState.value = DebridUnrestrictState()
        if (pending == null) return
        scope.launch {
            when (val result = prepareWithDebrid(service, pending)) {
                is Prepared.Ready -> play(result.request, result.subtitles)
                is Prepared.NeedsService -> {
                    _debridState.value = DebridUnrestrictState(
                        showServicePicker = true,
                        pendingStream = pending,
                        availableServices = result.services,
                    )
                }

                is Prepared.Failed -> {
                    _uiState.value = _uiState.value.copy(
                        isBuffering = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    /**
     * The user closed the debrid picker.
     */
    fun dismissDebridPicker() {
        _debridState.value = DebridUnrestrictState()
    }

    /**
     * The user closed the error of the debrid flow.
     */
    fun dismissDebridError() {
        _debridState.value = DebridUnrestrictState()
    }

    /**
     * The user closed the error of the player.
     */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // --- the events of the player ----------------------------------------------

    /**
     * The player reported an error.
     *
     * Mirrors the original's `z0.a(PlaybackException)`.
     */
    fun onPlayerError(message: String) {
        Logger.d(TAG, "onPlayerError: ${message.take(600)}")
        val state = _uiState.value
        if (state.isAutoPlayingNext) {
            Logger.w(TAG, "Auto-play source failed during playback: $message")
            _uiState.value = state.copy(
                isBuffering = false,
                errorMessage = "Playback failed for auto-play source.",
            )
            return
        }
        if (liveUri.isNotBlank() && liveAttempts < LIVE_ATTEMPTS) {
            liveAttempts++
            Logger.d(
                TAG,
                "live error recoverable (attempt $liveAttempts/$LIVE_ATTEMPTS), " +
                    "re-resolving ${liveUri.take(80)}",
            )
            reResolveLive()
            return
        }
        _uiState.value = state.copy(
            isBuffering = false,
            errorMessage = "Playback error: $message",
        )
    }

    /**
     * Resolves the live stream again and plays it.
     *
     * Mirrors the original's `w0`.
     */
    private fun reResolveLive() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isBuffering = true)
            val parts = NtvUriParts.parse(liveUri)
            val resolved = try {
                parts?.let { resolveNtv(it) }
            } catch (e: Exception) {
                Logger.e(TAG, "Live re-resolve failed", e)
                null
            }
            if (resolved == null) {
                Logger.e(TAG, "Live re-resolve failed")
                _uiState.value = _uiState.value.copy(
                    isBuffering = false,
                    errorMessage = "Playback error: stream expired.",
                )
                return@launch
            }
            Logger.d(TAG, "Re-resolved live stream, retrying playback")
            val prepared = prepareUrl(liveUri, resolved.headers)
            val request = requestFor(
                url = prepared.url,
                headers = prepared.headers,
                mimeType = null,
                isHls = true,
            ).copy(playWhenReady = true, token = ++playbackToken)
            lastRequest = request
            _playbackRequest.value = request
        }
    }

    /**
     * The player changed its state.
     */
    fun onPlaybackStateChanged(state: Int) {
        val current = _uiState.value
        when (state) {
            Player.STATE_BUFFERING -> _uiState.value = current.copy(isBuffering = true)
            Player.STATE_READY, Player.STATE_ENDED -> {
                _uiState.value = current.copy(
                    isBuffering = false,
                    playbackStarted = true,
                )
                armControlsHide()
            }
        }
    }

    /**
     * The player started or stopped playing.
     */
    fun onIsPlayingChanged(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
    }

    /**
     * The playback reached its end.
     */
    fun onPlaybackEnded() {
        val current = _uiState.value
        _uiState.value = current.copy(
            isAutoPlayingNext = false,
            isBuffering = false,
        )
        saveProgress()
        if (current.autoPlayEnabled) playNextEpisode()
    }

    /**
     * The player moved on.
     */
    fun onPositionChanged(positionMs: Long, durationMs: Long) {
        val current = _uiState.value
        _uiState.value = current.copy(
            currentPosition = positionMs,
            totalDuration = durationMs,
            subtitleText = subtitles.cueAt(cues, positionMs, current.subtitleOffsetMs),
        )
    }

    /**
     * The player found the tracks of the media.
     */
    fun onTracksChanged(
        audioTracks: List<AudioTrackInfo>,
        internalSubtitles: List<SubtitleItem>,
    ) {
        val current = _uiState.value
        _uiState.value = current.copy(
            audioTracks = audioTracks,
            subtitles = mergeSubtitles(current.subtitles, internalSubtitles),
        )
    }

    /**
     * Merges the tracks of the media into the subtitles the user picked.
     *
     * Mirrors the original's `z0.v(Tracks)`: the external subtitles are kept and the
     * tracks of the media are added, without a track that is already in the list.
     */
    private fun mergeSubtitles(
        existing: List<SubtitleItem>,
        internal: List<SubtitleItem>,
    ): List<SubtitleItem> {
        val external = existing.filter { !it.isInternal }
        val labels = external.map { it.label.lowercase(Locale.ROOT) }.toSet()
        return external + internal.filter { it.label.lowercase(Locale.ROOT) !in labels }
    }

    /**
     * Adds the subtitles that came with the source.
     */
    private fun addExternalSubtitles(
        existing: List<SubtitleItem>,
        added: List<SubtitleItem>,
    ): List<SubtitleItem> {
        val labels = existing.map { it.label.lowercase(Locale.ROOT) }.toSet()
        return existing + added.filter { it.label.lowercase(Locale.ROOT) !in labels }
    }

    // --- the seeking -----------------------------------------------------------

    /**
     * The user asked to seek.
     *
     * Mirrors the original's `x0` case 1: every seek cancels the watchdog of the
     * previous one and arms a new one. The watchdog waits fifteen seconds and
     * then asks whether the player is still buffering — when it is, the
     * "Seeking Unstable" panel is shown.
     */
    fun onSeekRequested(targetMs: Long) {
        if (!_uiState.value.isSeekable) {
            _uiState.value = _uiState.value.copy(
                seekWarning = "Seeking disabled due to slow response.",
            )
            return
        }
        seekWatchdogJob?.cancel()
        seekWatchdogJob = scope.launch {
            delay(SEEK_TIMEOUT_MS)
            if (_uiState.value.isBuffering) {
                Logger.w(TAG, "Seeking Unstable: the stream is still buffering after $targetMs")
                _uiState.value = _uiState.value.copy(showSeekRecovery = true)
            }
        }
        seekChannel.trySend(targetMs)
    }

    /**
     * The user asked to turn seeking off.
     *
     * Mirrors the original's "Disable Seeking" button: the warning is written
     * and the player is put back to the beginning. The replica also stops the
     * seek bar, which is what the button promises.
     */
    fun disableSeeking() {
        _uiState.value = _uiState.value.copy(
            isSeekable = false,
            showSeekRecovery = false,
            seekWarning = "Seeking disabled due to slow response.",
        )
        seekChannel.trySend(0L)
    }

    /**
     * The user asked to go back to the beginning.
     *
     * Mirrors the original's "Reset to 0:00" button: the panel closes and the
     * player is put back to the beginning.
     */
    fun resetToZero() {
        _uiState.value = _uiState.value.copy(showSeekRecovery = false)
        seekChannel.trySend(0L)
    }

    /**
     * The user asked to play the media again from the beginning.
     */
    fun restartPlayback() {
        val request = lastRequest ?: return
        _playbackRequest.value = request.copy(
            startPositionMs = 0L,
            playWhenReady = true,
            token = ++playbackToken,
        )
    }

    // --- the controls ----------------------------------------------------------

    /**
     * The user tapped the screen.
     *
     * Mirrors the original's `a1.h()`: the controls are shown or hidden, and
     * they hide themselves again after five seconds.
     */
    fun toggleControls() {
        val current = _uiState.value
        val show = !current.showControls
        _uiState.value = current.copy(showControls = show)
        if (show) armControlsHide() else controlsHideJob?.cancel()
    }

    /**
     * Hides the controls after five seconds.
     *
     * Mirrors the original's `x0` case 2.
     */
    private fun armControlsHide() {
        controlsHideJob?.cancel()
        controlsHideJob = scope.launch {
            delay(CONTROLS_TIMEOUT_MS)
            _uiState.value = _uiState.value.copy(showControls = false)
        }
    }

    /**
     * Opens or closes one of the dialogs.
     *
     * The original's `a1.k(String, Boolean)` knows four of them: "subtitle",
     * "recovery", "audio" and "settings".
     */
    fun toggleDialog(key: String, open: Boolean) {
        val current = _uiState.value
        _uiState.value = when (key) {
            "subtitle" -> current.copy(showSubtitlePicker = open)
            "recovery" -> current.copy(showSeekRecovery = open)
            "audio" -> current.copy(showAudioTrackPicker = open)
            "settings" -> current.copy(showSettingsDialog = open)
            else -> current
        }
    }

    // --- the subtitles ---------------------------------------------------------

    /**
     * The user picked a subtitle.
     *
     * Mirrors the original's `a1.i(SubtitleItem)`: a track that is part of the
     * media is selected through the player's track selection, every other subtitle
     * is downloaded, written into the cache and handed to the player as a sidecar.
     */
    fun selectSubtitle(item: SubtitleItem) {
        _uiState.value = _uiState.value.copy(showSubtitlePicker = false)
        if (item.isInternal) {
            // The player draws a track that is part of the media itself, so the
            // sidecar the screen draws is taken away.
            cues = emptyList()
            _uiState.value = _uiState.value.copy(
                activeSubtitle = item,
                subtitleText = null,
            )
            trackChannel.trySend(
                TrackSelectionRequest(
                    type = C.TRACK_TYPE_TEXT,
                    groupIndex = item.trackGroupIndex,
                    trackIndex = item.trackIndex,
                    language = item.lang,
                ),
            )
            return
        }
        scope.launch {
            _uiState.value = _uiState.value.copy(subtitlesLoading = true)
            val content = subtitles.download(
                item,
                _uiState.value.selectedStream?.headers.orEmpty(),
            )
            if (content == null) {
                _uiState.value = _uiState.value.copy(subtitlesLoading = false)
                return@launch
            }
            val cached = subtitles.writeToCache(item, content)
            if (cached == null) {
                _uiState.value = _uiState.value.copy(subtitlesLoading = false)
                return@launch
            }
            Logger.d(TAG, "Applying media source with sidecar subtitle")
            val position = _uiState.value.currentPosition
            cues = subtitles.parseCues(content)
            _uiState.value = _uiState.value.copy(
                subtitlesLoading = false,
                activeSubtitle = item,
                subtitleContentCache = content,
            )
            val request = lastRequest ?: return@launch
            _playbackRequest.value = request.copy(
                startPositionMs = position,
                playWhenReady = true,
                sidecarSubtitle = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(cached.file))
                    .setMimeType(cached.mimeType)
                    .setLanguage(item.lang)
                    // The original flags the sidecar only when the subtitle is the
                    // default one; the player renders it in Compose, so the track
                    // must not be selected by the player as well.
                    .setSelectionFlags(if (item.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                    .build(),
                subtitleLanguage = item.lang,
                token = ++playbackToken,
            )
        }
    }

    /**
     * The user picked a file as a custom subtitle.
     *
     * Mirrors the original's `o`: the uri is kept, the label is the last segment of
     * the path, and the subtitle is selected right away.
     */
    fun addCustomSubtitle(uri: Uri) {
        val label = uri.lastPathSegment
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // The original logs the same message under the "Settings" tag and
            // carries on with the uri it was given.
            Logger.d("Settings", "Failed to take persistable permission: ${e.message}")
        }
        val item = SubtitleItem(
            label = label ?: "Custom Subtitle",
            url = uri.toString(),
            lang = "en",
            isLocal = false,
        )
        val current = _uiState.value
        _uiState.value = current.copy(
            subtitles = addExternalSubtitles(current.subtitles, listOf(item)),
        )
        selectSubtitle(item)
    }

    /**
     * Turns the subtitles off.
     */
    fun clearSubtitle() {
        cues = emptyList()
        _uiState.value = _uiState.value.copy(activeSubtitle = null, subtitleText = null)
    }

    /**
     * The user changed the size of the subtitles.
     */
    fun setSubtitleSize(sizeSp: Float) {
        val clamped = sizeSp.coerceIn(MIN_SUBTITLE_SP, MAX_SUBTITLE_SP)
        _uiState.value = _uiState.value.copy(subtitleSizeSp = clamped)
        scope.launch { preferences.setSubtitleSizeSp(clamped) }
    }

    /**
     * The user changed the offset of the subtitles.
     */
    fun adjustSubtitleOffset(deltaMs: Long) {
        val current = _uiState.value
        _uiState.value = current.copy(
            subtitleOffsetMs = (current.subtitleOffsetMs + deltaMs).coerceIn(
                -MAX_SUBTITLE_OFFSET_MS,
                MAX_SUBTITLE_OFFSET_MS,
            ),
        )
    }

    /**
     * The user put the subtitles back in sync.
     */
    fun resetSubtitleOffset() {
        _uiState.value = _uiState.value.copy(subtitleOffsetMs = 0L)
    }

    /**
     * The user changed the colour of the subtitles.
     */
    fun setSubtitleColor(argb: Int) {
        _uiState.value = _uiState.value.copy(subtitleColor = argb)
        scope.launch { preferences.setSubtitleTextColor(argb) }
    }

    /**
     * The user changed the background of the subtitles.
     */
    fun setSubtitleBackground(argb: Int) {
        _uiState.value = _uiState.value.copy(subtitleBgColor = argb)
        scope.launch { preferences.setSubtitleBgColor(argb) }
    }

    /**
     * The user changed how far the rewind / forward buttons jump.
     *
     * The original stores this in `player_seek_increment_ms`; the values it
     * offers are 10s, 15s, 20s, 30s, 1m, 2m and 5m.
     */
    fun setSeekIncrement(intervalMs: Long) {
        _uiState.value = _uiState.value.copy(seekIncrementMs = intervalMs)
        scope.launch { preferences.setPlayerSeekIncrementMs(intervalMs) }
    }

    // --- the audio track -------------------------------------------------------

    /**
     * The user picked an audio track.
     */
    fun selectAudioTrack(track: AudioTrackInfo) {
        val current = _uiState.value
        _uiState.value = current.copy(
            showAudioTrackPicker = false,
            audioTracks = current.audioTracks.map {
                if (it.groupIndex == track.groupIndex && it.trackIndex == track.trackIndex) {
                    it.copy(isSelected = true)
                } else if (it.groupIndex == track.groupIndex) {
                    it.copy(isSelected = false)
                } else {
                    it
                }
            },
        )
        trackChannel.trySend(
            TrackSelectionRequest(
                type = C.TRACK_TYPE_AUDIO,
                groupIndex = track.groupIndex,
                trackIndex = track.trackIndex,
            ),
        )
    }

    // --- the options -----------------------------------------------------------

    /**
     * The user changed how the video is scaled.
     */
    fun setDisplayMode(mode: PlayerDisplayMode) {
        _uiState.value = _uiState.value.copy(displayMode = mode)
    }

    /**
     * Loads the episodes of the season that is playing.
     */
    fun loadEpisodes() {
        if (episodesJob?.isActive == true) return
        if (tmdbId <= 0 || season <= 0 || contentType == "movie") return
        episodesJob = scope.launch {
            val list = runCatching { fetchEpisodes(tmdbId, season) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) _uiState.value = _uiState.value.copy(episodes = list)
        }
    }

    /**
     * The user picked another episode of the season.
     */
    fun selectEpisode(number: Int) {
        if (number == currentEpisode) return
        currentEpisode = number
        _uiState.value = _uiState.value.copy(showSettingsDialog = false)
        resolveJob?.cancel()
        resolveJob = scope.launch {
            _uiState.value = _uiState.value.copy(
                isBuffering = true,
                errorMessage = null,
                transientMessage = "Finding sources...",
            )
            val fetched = runCatching {
                streamsRepository.fetchAll(
                    tmdbId = tmdbId,
                    mediaType = contentType.ifBlank { "movie" },
                    season = season.takeIf { it > 0 },
                    episode = number.takeIf { it > 0 },
                )
            }.getOrDefault(emptyList())
            if (fetched.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isBuffering = false,
                    transientMessage = null,
                    errorMessage = "Failed to load next episode: No sources available",
                )
                return@launch
            }
            val candidates = buildCandidates(fetched)
            _uiState.value = _uiState.value.copy(streams = candidates)
            resolveSources(candidates, 0)
        }
    }

    // --- the next episode ------------------------------------------------------

    /**
     * Plays the next episode.
     *
     * Mirrors the original's `r0`: the title of the next episode is built out of
     * the title of the current one, the sources are looked up again and the player
     * is cleared.
     */
    private fun playNextEpisode() {
        val next = nextEpisode() ?: return
        resolveJob?.cancel()
        resolveJob = scope.launch {
            _uiState.value = _uiState.value.copy(
                isAutoPlayingNext = true,
                showNextEpisode = true,
                autoPlayStatus = "Finding sources...",
                activeTitle = next.title,
                isBuffering = true,
                errorMessage = null,
                transientMessage = null,
            )
            try {
                val fetched = runCatching {
                    streamsRepository.fetchAll(
                        tmdbId = tmdbId,
                        mediaType = contentType.ifBlank { "tv" },
                        season = next.season.takeIf { it > 0 },
                        episode = next.episode.takeIf { it > 0 },
                    )
                }.getOrDefault(emptyList())
                if (fetched.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isBuffering = false,
                        autoPlayStatus = "Loading next episode...",
                        errorMessage = "Failed to load next episode: No sources available",
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    autoPlayStatus = "Loading next episode...",
                    streams = fetched,
                )
                resolveNextSources(fetched, 0)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load the next episode", e)
                _uiState.value = _uiState.value.copy(
                    isBuffering = false,
                    errorMessage = "Failed to load next episode: ${e.message}",
                )
            }
        }
    }

    /**
     * Resolves the sources of the next episode.
     */
    private suspend fun resolveNextSources(candidates: List<ChqStream>, from: Int) {
        var index = from
        var lastError: String? = null
        while (index < candidates.size) {
            val candidate = candidates[index]
            val name = candidate.name?.takeIf { it.isNotBlank() } ?: "Source ${index + 1}"
            _uiState.value = _uiState.value.copy(
                transientMessage = "Trying Source ${index + 1}/${candidates.size}: $name",
                selectedStream = candidate,
            )
            when (val result = prepare(candidate)) {
                is Prepared.Ready -> {
                    val request = result.request.copy(playWhenReady = true, token = ++playbackToken)
                    lastRequest = request
                    _uiState.value = _uiState.value.copy(
                        isBuffering = true,
                        transientMessage = null,
                    )
                    _playbackRequest.value = request
                    return
                }

                is Prepared.NeedsService -> {
                    _debridState.value = DebridUnrestrictState(
                        showServicePicker = true,
                        pendingStream = candidate,
                        availableServices = result.services,
                    )
                    return
                }

                is Prepared.Failed -> {
                    lastError = result.message
                    index++
                }
            }
        }
        _uiState.value = _uiState.value.copy(
            isBuffering = false,
            errorMessage = lastError ?: "Failed to load next episode: No sources available",
        )
    }

    /**
     * The episode that plays after the current one.
     *
     * Mirrors the original's `r0`: the title is stripped of its "S1E2" or
     * "- Episode 2" part and the next episode number is appended.
     */
    private fun nextEpisode(): NextEpisode? {
        if (contentType == "movie" || contentType == "adult") return null
        val base = title
            .replace(Regex("\\s+S\\d+E\\d+.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+-\\s+Episode\\s+\\d+.*", RegexOption.IGNORE_CASE), "")
            .trim()
        val current = currentEpisode
        val next = if (current > 1 || !title.contains("Episode", ignoreCase = true)) {
            current + 1
        } else {
            val match = Regex("Episode\\s+(\\d+)", RegexOption.IGNORE_CASE).find(title)
            (match?.groupValues?.get(1)?.toIntOrNull() ?: 0) + 1
        }
        if (next <= 0) return null
        val nextTitle = if (contentType == "anime") {
            "$base - Episode $next"
        } else {
            "$base S${season}E$next"
        }
        return NextEpisode(season, next, nextTitle)
    }

    // --- the clean up ----------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        saveProgress()
        liveProxy?.stop()
        liveProxy = null
        subtitles.clearCache()
    }

    /**
     * Stores the position the user stopped at.
     *
     * Mirrors the original's `a1.g()`.
     */
    fun saveProgress() {
        val key = resumeKey()
        val position = _uiState.value.currentPosition
        if (key.isBlank() || position <= 0L) return
        val duration = _uiState.value.totalDuration
        saveScope.launch {
            try {
                continueWatchingDao.upsert(
                    ContinueWatchingEntity(
                        key = key,
                        tmdbId = tmdbId,
                        mediaType = contentType.ifBlank { "movie" },
                        title = title,
                        episodeNumber = currentEpisode.takeIf { it > 0 },
                        seasonNumber = season.takeIf { it > 0 },
                        progressMs = position,
                        durationMs = duration,
                    ),
                )
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to save the position", e)
            }
        }
    }

    // --- the episodes ----------------------------------------------------------

    /**
     * The episodes of a season.
     */
    private suspend fun fetchEpisodes(id: Int, seasonNumber: Int): List<PlayerEpisode> =
        withContext(Dispatchers.IO) {
            try {
                val response = tmdbApi.tvSeason(id, seasonNumber)
                val body = response.body()?.string() ?: return@withContext emptyList()
                val root = JsonParser.parseString(body).asJsonObject
                val episodes = root.getAsJsonArray("episodes") ?: return@withContext emptyList()
                episodes.mapNotNull { element ->
                    val entry = element.asJsonObject
                    val number = entry.get("episode_number")?.asInt ?: return@mapNotNull null
                    PlayerEpisode(
                        number = number,
                        name = entry.get("name")?.asString.orEmpty(),
                        overview = entry.get("overview")?.asString.orEmpty(),
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load the episodes", e)
                emptyList()
            }
        }

    // --- the helpers ------------------------------------------------------------

    /**
     * The original passes the request headers through the route as newline separated
     * `key=value` pairs.
     */
    private fun parseHeaders(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains('=') }
            .map { line ->
                val idx = line.indexOf('=')
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()
    }

    /**
     * Turns a failure into the message the original shows.
     *
     * The original's sources view model maps the HTTP codes like this: 451 is the
     * Real-Debrid DMCA notice, 401 is an authentication failure, 403 is a denied
     * token and anything else is reported with its code.
     */
    private fun describeError(
        service: DebridService,
        error: Throwable,
    ): Pair<String?, String?> {
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

    /**
     * The result of preparing a source.
     */
    private sealed interface Prepared {
        data class Ready(
            val request: PlaybackRequest,
            val subtitles: List<SubtitleItem>,
        ) : Prepared

        data class NeedsService(val services: List<DebridService>) : Prepared
        data class Failed(val message: String) : Prepared
    }

    /**
     * A url that is ready to be played.
     */
    private data class PreparedUrl(val url: String, val headers: Map<String, String>)

    /**
     * The episode that plays next.
     */
    private data class NextEpisode(val season: Int, val episode: Int, val title: String)

    companion object {
        const val TAG = "CinemaHQ-Player"

        /** The hosts the original does not probe. */
        private val KNOWN_DIRECT_HOSTS = listOf(
            "googleusercontent.com",
            ".workers.dev",
            "comet.elfhosted.com",
            "pluto.tv",
            "freehandyflix.online",
            "fsharetv.cc",
            "r2.cloudflarestorage.com",
            ".amazonaws.com",
            "X-Amz-Signature=",
        )

        /** The content types the original refuses to play. */
        private val UNSUPPORTED_TYPES = listOf(
            "application/zip",
            "application/x-rar",
            "application/x-7z",
            "text/html",
        )

        /** The size a Matroska file has to have before seeking is warned about. */
        private const val VERY_LARGE_BYTES = 10_737_418_240L

        /** How often a live stream is re-resolved before the player gives up. */
        private const val LIVE_ATTEMPTS = 3

        /** How long a seek may take before the player calls it unstable. */
        private const val SEEK_TIMEOUT_MS = 15_000L

        /** How long the controls stay on the screen. */
        private const val CONTROLS_TIMEOUT_MS = 5_000L

        /** The smallest and the biggest subtitle the options sheet offers. */
        private const val MIN_SUBTITLE_SP = 10f
        private const val MAX_SUBTITLE_SP = 36f

        /** The biggest offset the user can put on the subtitles. */
        private const val MAX_SUBTITLE_OFFSET_MS = 60_000L
    }
}
