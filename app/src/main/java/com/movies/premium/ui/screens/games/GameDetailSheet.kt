package com.movies.premium.ui.screens.games

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.movies.premium.data.remote.model.GameDetailResponse
import com.movies.premium.data.remote.model.GamePlatform
import com.movies.premium.emulator.EmulatorActivity
import com.movies.premium.ui.tv.focusRing
import java.util.Locale

/**
 * The details of a game, as a sheet on top of the games grid.
 *
 * The content is the original's `com.app.mlounge.ui.screens.games.a.a(slug,
 * platform, onBack, …)`: the poster, the name, the platform, the size of the
 * ROM, the hint and the Play button. The original opens the same content as
 * its own `game_detail/{platform}/{gameSlug}` destination; here it is a sheet
 * over the grid, so the back button and the dismissal both close it.
 *
 * The strings are the original's, verbatim:
 *
 *  - "Platform:" and the display name of the platform,
 *  - "Size:" and `String.format(Locale.US, "%.1f", romSizeMB) + " MB"`,
 *  - "Hint!",
 *  - "First download the game and then press Play. Enjoy!",
 *  - "Play" — only enabled once the ROM is on disk.
 */
@Composable
fun GameDetailSheet(
    detail: GameDetailResponse?,
    platform: GamePlatform,
    loading: Boolean,
    error: String?,
    downloading: Boolean,
    downloadedBytes: Long,
    totalBytes: Long,
    romReady: Boolean,
    isFavourite: Boolean,
    romPath: () -> String?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // The sheet has a window of its own, so the ring of the window of the
        // app cannot reach it: the sheet draws the ring around whatever
        // inside it has the focus.
        modifier = Modifier.focusRing(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = detail?.posterUrl,
                    contentDescription = detail?.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
                // The original darkens the lower half of the poster with a
                // gradient; the name is written over it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            Brush.verticalGradient(
                                0.3f to Color.Transparent,
                                0.65f to Color.Black.copy(alpha = 0.75f),
                                1f to Color.Black.copy(alpha = 0.97f),
                            ),
                        ),
                )
                // The favourite switch of the original — "Add games from their
                // detail page!".
                IconButton(
                    onClick = onToggleFavourite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = if (isFavourite) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Filled.FavoriteBorder
                        },
                        contentDescription = if (isFavourite) {
                            "Remove from favourites"
                        } else {
                            "Add to favourites"
                        },
                        tint = if (isFavourite) Color(0xFFEF5350) else Color.White,
                    )
                }
            }

            if (loading && detail == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (detail == null) {
                Text(
                    text = error ?: "This game is not available.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                Text(
                    text = detail.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )

                // "Platform:" and the display name of the platform.
                DetailRow("Platform:", platform.displayName)

                // "Size:" and the size of the ROM — the original only shows it
                // when the backend reports one.
                detail.romSizeMb?.let { sizeMb ->
                    DetailRow(
                        label = "Size:",
                        value = String.format(Locale.US, "%.1f", sizeMb) + " MB",
                    )
                }

                if (!detail.genre.isNullOrBlank()) DetailRow("Genre:", detail.genre)
                if (!detail.year.isNullOrBlank()) DetailRow("Year:", detail.year)
                if (!detail.players.isNullOrBlank()) DetailRow("Players:", detail.players)
                if (!detail.region.isNullOrBlank()) DetailRow("Region:", detail.region)
                if (!detail.developer.isNullOrBlank()) {
                    DetailRow("Developer:", detail.developer)
                }
                if (!detail.publisher.isNullOrBlank()) {
                    DetailRow("Publisher:", detail.publisher)
                }

                if (!detail.description.isNullOrBlank()) {
                    Text(
                        text = detail.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // The hint of the original, word for word.
                Text(
                    text = "Hint!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "First download the game and then press Play. Enjoy!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (downloading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat())
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Downloading… ${downloadedBytes / 1024} KB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!romReady) {
                        OutlinedButton(
                            onClick = onDownload,
                            enabled = !downloading,
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Download")
                        }
                    }
                    Button(
                        onClick = {
                            val path = romPath()
                            if (path == null) {
                                Toast.makeText(
                                    context,
                                    "Download the game first",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@Button
                            }
                            val intent = Intent(context, EmulatorActivity::class.java).apply {
                                putExtra(EmulatorActivity.EXTRA_ROM_PATH, path)
                                putExtra(
                                    EmulatorActivity.EXTRA_GAME_TITLE,
                                    detail.name ?: "Game",
                                )
                                putExtra(
                                    EmulatorActivity.EXTRA_CORE_NAME,
                                    platform.coreName,
                                )
                            }
                            context.startActivity(intent)
                        },
                        // The game can only be played when its ROM is on disk.
                        enabled = romReady,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                }

                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * One of the label/value rows of the original: the label in the muted colour,
 * the value in the light one, four dp apart.
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
