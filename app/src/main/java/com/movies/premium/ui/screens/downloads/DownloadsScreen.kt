package com.movies.premium.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import com.movies.premium.data.local.entity.DownloadEntity
import com.movies.premium.ui.viewmodel.LibraryViewModel
import java.io.File
import java.util.Locale

/**
 * The downloads list.
 *
 * The original's screen is `com.app.mlounge.ui.screens.downloads.a` (the screen) and
 * `downloads.b` (the row). The original shows:
 *
 * * the headline "Downloads",
 * * an empty state with a `Filled.DownloadDone` glyph and the text "No downloads yet",
 * * a list whose rows carry a status glyph, the title, the quality and the size, and
 *   the status word itself.
 *
 * The status glyph is chosen from the status string:
 *
 * | status | glyph |
 * |---|---|
 * | `completed` | `Filled.DownloadDone` |
 * | `failed` | `Filled.Error` |
 * | `downloading` | `Filled.Downloading` |
 * | `paused` | `Filled.PauseCircle` |
 * | anything else (`pending`, `cancelled`) | `Filled.HourglassEmpty` |
 *
 * The original's byte formatter is reproduced verbatim in [formatBytes].
 */
@Composable
fun DownloadsScreen(
    onPlay: (url: String, title: String, contentType: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.downloads.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
        )

        if (state.isEmpty()) {
            EmptyDownloads()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state, key = { it.id }) { item ->
                DownloadRow(
                    item = item,
                    onPlay = {
                        // The player takes a URI; a completed download is a local file.
                        val path = item.filePath.orEmpty()
                        val uri = if (path.isBlank()) {
                            ""
                        } else {
                            Uri.fromFile(File(path)).toString()
                        }
                        onPlay(uri, item.title, item.contentType)
                    },
                    onPause = { viewModel.pauseDownload(item.id) },
                    onResume = { viewModel.resumeDownload(item.id) },
                    onRetry = { viewModel.retryDownload(item.id) },
                    onDelete = { viewModel.deleteDownload(item.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyDownloads() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Box(Modifier.size(8.dp))
            Text(
                text = "No downloads yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadEntity,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = statusIcon(item.status),
                contentDescription = item.status,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(item.quality, item.size)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = item.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.failureReason?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            when (item.status) {
                DownloadEntity.STATUS_DOWNLOADING -> {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Filled.PauseCircle, contentDescription = "Pause")
                    }
                }

                DownloadEntity.STATUS_PAUSED -> {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Filled.Downloading, contentDescription = "Resume")
                    }
                }

                DownloadEntity.STATUS_FAILED, DownloadEntity.STATUS_CANCELLED -> {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry")
                    }
                }

                DownloadEntity.STATUS_COMPLETED -> {
                    if (!item.filePath.isNullOrBlank()) {
                        IconButton(onClick = onPlay) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                        }
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }

        if (item.status == DownloadEntity.STATUS_DOWNLOADING) {
            LinearProgressIndicator(
                progress = { (item.progress.coerceIn(0, 100)) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The glyph the original shows for a download in the given status. */
private fun statusIcon(status: String): ImageVector = when (status) {
    DownloadEntity.STATUS_COMPLETED -> Icons.Filled.DownloadDone
    DownloadEntity.STATUS_FAILED -> Icons.Filled.Error
    DownloadEntity.STATUS_DOWNLOADING -> Icons.Filled.Downloading
    DownloadEntity.STATUS_PAUSED -> Icons.Filled.PauseCircle
    else -> Icons.Filled.HourglassEmpty
}

/**
 * The original's byte formatter (`downloads.a.c`).
 *
 * `"< 1024" → "N B"`, `"< 1 MiB" → "N KB"`, `"< 1 GiB" → "%.1f MB"`, otherwise `"%.2f GB"`,
 * always in `Locale.US`.
 */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 ->
        String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

    else -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}
