@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.movies.premium.ui.screens.player

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.movies.premium.ui.viewmodel.PlaybackRequest
import com.movies.premium.ui.viewmodel.PlayerDisplayMode
import com.movies.premium.ui.viewmodel.PlayerViewModel
import com.movies.premium.ui.viewmodel.formatPlayerTime
import com.movies.premium.util.Logger
import kotlinx.coroutines.delay

/** The desktop Chrome user agent the original's sources are served with. */
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

/** The tag the original's player logs under. */
private const val TAG = "CinemaHQ-Player"

/**
 * Full-screen playback.
 *
 * The original's player is `com.app.mlounge.ui.screens.player.h0`, a 3400 line
 * Compose screen backed by the view model `a1`. The replica splits the same work
 * the same way:
 *
 * - the view model owns the state, the messages and the decisions
 *   (`com.movies.premium.ui.viewmodel.PlayerViewModel`),
 * - this screen owns the ExoPlayer, draws the control surface and shows the
 *   dialogs the original shows.
 *
 * The control surface reproduces the original's strings: "Back", "Audio",
 * "Subtitles", "Settings" in the top bar, "Rewind <n>" on the rewind button, and
 * the "Auto-Playing Next Episode" overlay while the next episode is looked up.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val debridState by viewModel.debridState.collectAsStateWithLifecycle()
    val playbackRequest by viewModel.playbackRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // The listener of the player, the original's `z0`.
    DisposableEffect(exoPlayer, viewModel) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlayerError(error.message ?: error.errorCodeName)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                viewModel.onPlaybackStateChanged(playbackState)
                if (playbackState == Player.STATE_ENDED) viewModel.onPlaybackEnded()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.onIsPlayingChanged(isPlaying)
            }

            override fun onTracksChanged(tracks: Tracks) {
                viewModel.onTracksChanged(tracks.audioTracks(), tracks.subtitleTracks())
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // The position of the player, the original's `x0` watchdog reads it too.
    LaunchedEffect(exoPlayer) {
        while (true) {
            val duration = exoPlayer.duration
            viewModel.onPositionChanged(
                exoPlayer.currentPosition,
                if (duration > 0L) duration else 0L,
            )
            delay(POSITION_INTERVAL_MS)
        }
    }

    // The requests of the view model.
    var appliedToken by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(playbackRequest, exoPlayer) {
        val request = playbackRequest ?: return@LaunchedEffect
        if (request.token == appliedToken) return@LaunchedEffect
        appliedToken = request.token
        applyPlaybackRequest(exoPlayer, context, request)
    }

    // The tracks the user picked.
    LaunchedEffect(exoPlayer) {
        viewModel.trackSelections.collect { selection ->
            val group = exoPlayer.currentTracks.groups
                .getOrNull(selection.groupIndex)
                ?.mediaTrackGroup
            if (group == null) return@collect
            val builder = exoPlayer.trackSelectionParameters.buildUpon()
            when (selection.type) {
                C.TRACK_TYPE_AUDIO -> builder.setOverrideForType(
                    TrackSelectionOverride(group, selection.trackIndex),
                )

                C.TRACK_TYPE_TEXT -> builder
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(
                        TrackSelectionOverride(group, selection.trackIndex),
                    )
            }
            exoPlayer.trackSelectionParameters = builder.build()
        }
    }

    // The seeks the user asked for.
    LaunchedEffect(exoPlayer) {
        viewModel.seekRequests.collect { target -> exoPlayer.seekTo(target) }
    }

    // The custom subtitle the user picked.
    val customSubtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.addCustomSubtitle(it) } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // A television has no finger: the picture is brought back with
            // the "ok" button of the remote, and the play and pause keys of
            // the remote do what they do on any other player.
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != android.view.KeyEvent.ACTION_DOWN) {
                    false
                } else {
                    when (native.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            // While the controls are on, the "ok" belongs to
                            // the button that has the focus.
                            if (!uiState.showControls) {
                                viewModel.toggleControls()
                                true
                            } else {
                                false
                            }
                        }

                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            true
                        }

                        android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                            exoPlayer.pause()
                            true
                        }

                        else -> false
                    }
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.setResizeMode(
                    if (uiState.displayMode == PlayerDisplayMode.STRETCH) {
                        AspectRatioFrameLayout.RESIZE_MODE_FILL
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        // The layer that shows and hides the controls, the original's `a1.h()`.
        // It sits above the video and below the controls, so a tap on the
        // picture toggles the control surface.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { viewModel.toggleControls() },
        )

        // The subtitle the player renders itself, the original renders it in Compose
        // so that the size, the colour and the offset of the options sheet apply.
        uiState.subtitleText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = Color(uiState.subtitleColor),
                    fontSize = uiState.subtitleSizeSp.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(uiState.subtitleBgColor), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (uiState.showControls) {
            PlayerTopBar(
                title = uiState.activeTitle ?: uiState.title,
                showAudio = uiState.audioTracks.size > 1,
                onBack = { context.findActivity()?.onBackPressedDispatcher?.onBackPressed() },
                onAudio = { viewModel.toggleDialog("audio", true) },
                onSubtitles = { viewModel.toggleDialog("subtitle", true) },
                onSettings = {
                    viewModel.loadEpisodes()
                    viewModel.toggleDialog("settings", true)
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            PlayerBottomBar(
                positionMs = uiState.currentPosition,
                durationMs = uiState.totalDuration,
                isPlaying = uiState.isPlaying,
                seekIncrementMs = uiState.seekIncrementMs,
                onPlayPause = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onSeek = { viewModel.onSeekRequested(it) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // The source strip the replica's player had before.
            if (uiState.streams.size > 1) {
                val labels = uiState.streams.map { stream ->
                    stream.name?.takeIf { it.isNotBlank() }
                        ?: stream.quality?.takeIf { it.isNotBlank() }
                        ?: "Source"
                }
                val selected = uiState.streams.indexOfFirst { it == uiState.selectedStream }
                SourceStrip(
                    labels = labels,
                    selectedIndex = selected,
                    onSelect = { index ->
                        uiState.streams.getOrNull(index)?.let { viewModel.selectStream(it) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 150.dp),
                )
            }
        }

        // The spinner of the buffering.
        if (uiState.isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // The spinner of the debrid resolution.
        if (debridState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // The message of the source resolution, the original's "Finding sources..."
        // and "Trying Source i/n: name".
        uiState.transientMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
        }

        // The warning of the seeking.
        uiState.seekWarning?.let { warning ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 230.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text = warning, style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
        }

        // The overlay of the next episode, the original's `h0.h(...)`.
        if (uiState.showNextEpisode) {
            AutoPlayOverlay(
                title = uiState.activeTitle.orEmpty(),
                status = uiState.autoPlayStatus.orEmpty(),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    // The "Resume Playback?" dialog.
    if (uiState.showResumeDialog) {
        ResumePlaybackDialog(
            positionMs = uiState.resumePositionMs,
            onRestart = { viewModel.onResumeAnswered(false) },
            onResume = { viewModel.onResumeAnswered(true) },
            onDismiss = { viewModel.onResumeAnswered(false) },
        )
    }

    // The "Seeking Unstable" panel.
    if (uiState.showSeekRecovery) {
        SeekingUnstableDialog(
            onResetToZero = { viewModel.resetToZero() },
            onDisableSeeking = { viewModel.disableSeeking() },
            onDismiss = { viewModel.toggleDialog("recovery", false) },
        )
    }

    // The subtitle picker.
    if (uiState.showSubtitlePicker) {
        SubtitlePickerDialog(
            subtitles = uiState.subtitles,
            activeSubtitle = uiState.activeSubtitle,
            loading = uiState.subtitlesLoading,
            onSelect = { viewModel.selectSubtitle(it) },
            onClear = { viewModel.clearSubtitle() },
            onCustomSubtitle = {
                customSubtitleLauncher.launch(SUBTITLE_MIME_TYPES)
            },
            onDismiss = { viewModel.toggleDialog("subtitle", false) },
        )
    }

    // The audio picker.
    if (uiState.showAudioTrackPicker) {
        AudioPickerDialog(
            audioTracks = uiState.audioTracks,
            onSelect = { viewModel.selectAudioTrack(it) },
            onDismiss = { viewModel.toggleDialog("audio", false) },
        )
    }

    // The debrid service picker, the same shape the sources screen uses.
    if (debridState.showServicePicker) {
        DebridServicePickerDialog(
            services = debridState.availableServices,
            onSelect = { viewModel.onDebridServiceSelected(it) },
            onDismiss = { viewModel.dismissDebridPicker() },
        )
    }

    // The error of the player.
    uiState.errorMessage?.let { message ->
        PlayerErrorDialog(message = message, onDismiss = { viewModel.dismissError() })
    }

    // The options dialog.
    if (uiState.showSettingsDialog) {
        PlayerOptionsDialog(
            subtitleSizeSp = uiState.subtitleSizeSp,
            subtitleOffsetMs = uiState.subtitleOffsetMs,
            subtitleColor = uiState.subtitleColor,
            subtitleBgColor = uiState.subtitleBgColor,
            displayMode = uiState.displayMode,
            episodes = uiState.episodes,
            currentEpisode = viewModel.episodeNumber,
            onSubtitleSizeChange = { viewModel.setSubtitleSize(it) },
            onSubtitleOffsetChange = { viewModel.adjustSubtitleOffset(it) },
            onSubtitleOffsetReset = { viewModel.resetSubtitleOffset() },
            onSubtitleColorChange = { viewModel.setSubtitleColor(it) },
            onSubtitleBackgroundChange = { viewModel.setSubtitleBackground(it) },
            onDisplayModeChange = { viewModel.setDisplayMode(it) },
            onEpisodeSelected = { viewModel.selectEpisode(it) },
            onDismiss = { viewModel.toggleDialog("settings", false) },
        )
    }
}

/**
 * The top bar of the player.
 *
 * Mirrors the original's `h0.k(...)`: "Back", the title of the media, and the
 * "Audio" (only when the media has more than one audio track), "Subtitles" and
 * "Settings" entries.
 */
@Composable
private fun PlayerTopBar(
    title: String,
    showAudio: Boolean,
    onBack: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        if (showAudio) {
            TextButton(onClick = onAudio) {
                Text("Audio", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        TextButton(onClick = onSubtitles) {
            Text("Subtitles", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = onSettings) {
            Text("Settings", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * The control surface of the player.
 *
 * Mirrors the original's `h0.l(...)`: a progress bar with the times, and the
 * rewind, play and forward buttons. The rewind button carries the original's
 * "Rewind <n>" label.
 */
@Composable
private fun PlayerBottomBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    seekIncrementMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val range = (if (durationMs > 0L) durationMs else 1L).toFloat()
    val sliderValue = if (dragging) dragValue else positionMs.toFloat().coerceIn(0f, range)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Slider(
            value = sliderValue,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(dragValue.toLong())
            },
            valueRange = 0f..range,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = formatPlayerTime(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
            Text(
                text = formatPlayerTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(
                onClick = { onSeek((positionMs - seekIncrementMs).coerceAtLeast(0L)) },
            ) {
                Icon(
                    imageVector = Icons.Filled.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Rewind ${seekLabel(seekIncrementMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            TextButton(onClick = { onSeek(positionMs + seekIncrementMs) }) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Forward ${seekLabel(seekIncrementMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/**
 * The label of the seek buttons.
 *
 * The original's label is "Rewind " followed by the increment in minutes when it
 * is at least a minute, and in seconds otherwise.
 */
private fun seekLabel(incrementMs: Long): String =
    if (incrementMs >= 60_000L) "${incrementMs / 60_000L}m" else "${incrementMs / 1_000L}s"

/**
 * Hands a playback request of the view model to the player.
 *
 * The data source is wrapped in a `DefaultDataSource` so that a *local* file — a
 * completed download, which is handed over as a `file://` URI — plays through the
 * same player as a remote stream. http(s) still goes to the HTTP factory.
 */
private fun applyPlaybackRequest(
    player: ExoPlayer,
    context: Context,
    request: PlaybackRequest,
) {
    Logger.d(TAG, "Playing url=${request.url} headers=${request.headers}")

    val mediaItem = MediaItem.Builder()
        .setUri(request.url)
        .setMimeType(mimeTypeFor(request))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(request.title ?: "AIO Hub")
                .build(),
        )
        .apply {
            request.sidecarSubtitle?.let { setSubtitleConfigurations(listOf(it)) }
        }
        .build()

    val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent(USER_AGENT)
    request.headers.takeIf { it.isNotEmpty() }?.let { httpFactory.setDefaultRequestProperties(it) }

    val dataSource = DefaultDataSource.Factory(context, httpFactory)

    // Media3 1.8 removed the `Factory(ExoPlayer).createMediaSource(MediaItem)`
    // overloads, so the source is built through DefaultMediaSourceFactory,
    // which picks HLS/DASH/progressive from the item's mime type and merges the
    // sidecar subtitle into the source.
    val source: MediaSource = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSource)
        .createMediaSource(mediaItem)

    player.setMediaSource(source)
    player.prepare()

    if (request.sidecarSubtitle != null) {
        // The subtitle is rendered by the screen, so the player must not draw it
        // a second time.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        Logger.d(TAG, "Applying media source with sidecar subtitle")
    }
    if (request.startPositionMs > 0L) {
        player.seekTo(request.startPositionMs)
        if (request.sidecarSubtitle != null) {
            Logger.d(TAG, "Player updated with subtitles and resumed at ${request.startPositionMs}")
        }
    }
    player.playWhenReady = request.playWhenReady
}

/**
 * The mime type of a stream.
 */
private fun mimeTypeFor(request: PlaybackRequest): String? {
    request.mimeType?.takeIf { it.isNotBlank() }?.let { return it }
    val url = request.url
    return when {
        url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
        url.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
        else -> null
    }
}

/**
 * The activity the player is shown in.
 */
private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** How often the position of the player is read. */
private const val POSITION_INTERVAL_MS = 500L

/** The kinds of file the custom subtitle picker offers. */
private val SUBTITLE_MIME_TYPES = arrayOf(
    "application/x-subrip",
    "text/vtt",
    "text/plain",
    "text/ssa",
    "application/octet-stream",
    "*/*",
)
