package com.movies.premium.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.data.remote.model.ChqStream
import com.movies.premium.ui.viewmodel.ResolvedSource
import com.movies.premium.ui.viewmodel.SourcesViewModel
import com.movies.premium.ui.viewmodel.formatSize
import com.movies.premium.ui.tv.focusRing

/**
 * The source picker.
 *
 * The original's screen is `com.app.mlounge.ui.screens.sources.a`. It shows every
 * provider that answered for the title and, when the user picks one, either opens the
 * player (`mode == "play"`) or queues the file (`mode == "download"`).
 *
 * The two callbacks below are what the navigation graph binds to; the screen itself
 * decides which one to call from the route's `mode` argument.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    onPlay: (ChqStream) -> Unit,
    onDownload: (ChqStream) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val debridState by viewModel.debridState.collectAsStateWithLifecycle()
    val isDownload = viewModel.mode == "download"

    // The view model resolves a torrent source through a debrid service and publishes
    // the playable stream; everything else is published unchanged.
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentOnDownload by rememberUpdatedState(onDownload)
    val currentIsDownload by rememberUpdatedState(isDownload)
    LaunchedEffect(Unit) {
        viewModel.playbackStream.collect { stream ->
            if (currentIsDownload) currentOnDownload(stream) else currentOnPlay(stream)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.loading && state.sources.isEmpty() -> {
                    // The backend asks four providers in turn, so one episode
                    // takes about eleven seconds. A bare spinner looks like a
                    // frozen app, so the screen says what it is waiting for.
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Looking for sources…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Without a way out the screen is a dead end: the user
                        // has to go back and try again by hand.
                        Button(onClick = { viewModel.load() }) {
                            Text("Retry")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Text(
                                text = if (isDownload) {
                                    "Pick a source to download"
                                } else {
                                    "Pick a source to play"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }

                        items(state.sources, key = { it.stream.url ?: it.stream.hashCode().toString() }) { source ->
                            SourceRow(
                                source = source,
                                isDownload = isDownload,
                                onClick = { viewModel.onSourceSelected(source.stream) },
                            )
                        }
                    }
                }
            }

            if (debridState.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if (debridState.showServicePicker) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDebridPicker() },
            modifier = Modifier.focusRing(),
            title = { Text("Choose a service") },
            text = {
                Column {
                    debridState.availableServices.forEach { service ->
                        TextButton(
                            onClick = { viewModel.onDebridServiceSelected(service) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = service.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (debridState.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDebridError() },
            modifier = Modifier.focusRing(),
            title = { Text(debridState.errorTitle ?: "Playback failed") },
            text = { Text(debridState.error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDebridError() }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun SourceRow(
    source: ResolvedSource,
    isDownload: Boolean,
    onClick: () -> Unit,
) {
    val stream = source.stream
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stream.name?.takeIf { it.isNotBlank() } ?: "Source",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        stream.quality?.takeIf { it.isNotBlank() }?.let { append(it) }
                        stream.size?.takeIf { it.isNotBlank() }?.let {
                            if (isNotEmpty()) append(" • ")
                            append(it)
                        }
                        source.sizeBytes?.let {
                            if (isNotEmpty()) append(" • ")
                            append(formatSize(it))
                        }
                        stream.seeders?.let {
                            if (isNotEmpty()) append(" • ")
                            append("$it seeders")
                        }
                        if (!source.available) {
                            if (isNotEmpty()) append(" • ")
                            append("Unavailable")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (source.available) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        },
                        RoundedCornerShape(20.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isDownload) Icons.Default.Download else Icons.Default.PlayArrow,
                    contentDescription = if (isDownload) "Download" else "Play",
                    tint = if (source.available) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}
