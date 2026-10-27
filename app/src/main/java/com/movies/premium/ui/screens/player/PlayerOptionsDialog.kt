package com.movies.premium.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.movies.premium.ui.tv.focusRing
import com.movies.premium.ui.viewmodel.PLAYER_BACKGROUND_COLORS
import com.movies.premium.ui.viewmodel.PLAYER_TEXT_COLORS
import com.movies.premium.ui.viewmodel.PlayerDisplayMode
import com.movies.premium.ui.viewmodel.PlayerEpisode
import java.util.Locale

/**
 * The options dialog of the player.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.player.w`, which is the
 * content of a Material3 dialog (16 dp corners, a scrim of 85 % black, a column
 * with 20 dp of padding around a lazy column) that holds:
 *
 * - "Subtitle Size" — a stepper with a `<n>sp` label; the original steps by two
 *   and clamps the size to 10..36 sp,
 * - "Sync Offset" — a stepper with a `+0.3s` label and a reset button; the
 *   original steps by 250 ms,
 * - "Text Color" — four unlabelled swatches (white, yellow, green, cyan),
 * - "Background Color" — four labelled swatches ("Semi", "Black", "None",
 *   "Gray").
 *
 * The replica adds the two entries the brief asks for and the original does not
 * have in its player: the display mode ("Fit to Screen" / "Stretch to Fill") and
 * the episode list.
 */
@Composable
fun PlayerOptionsDialog(
    subtitleSizeSp: Float,
    subtitleOffsetMs: Long,
    subtitleColor: Int,
    subtitleBgColor: Int,
    displayMode: PlayerDisplayMode,
    episodes: List<PlayerEpisode>,
    currentEpisode: Int,
    onSubtitleSizeChange: (Float) -> Unit,
    onSubtitleOffsetChange: (Long) -> Unit,
    onSubtitleOffsetReset: () -> Unit,
    onSubtitleColorChange: (Int) -> Unit,
    onSubtitleBackgroundChange: (Int) -> Unit,
    onDisplayModeChange: (PlayerDisplayMode) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            // The dialog has a window of its own, so the ring of the window
            // of the app cannot reach it: the dialog draws the ring around
            // whatever inside it has the focus.
            modifier = Modifier
                .fillMaxWidth()
                .focusRing(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(20.dp),
            ) {
                item {
                    Text(
                        text = "Subtitle Size",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(onClick = { onSubtitleSizeChange(subtitleSizeSp - 2f) }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Smaller")
                        }
                        Text(
                            text = "${subtitleSizeSp.toInt()}sp",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { onSubtitleSizeChange(subtitleSizeSp + 2f) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Bigger")
                        }
                    }
                }

                item {
                    Text(
                        text = "Sync Offset",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(onClick = { onSubtitleOffsetChange(-OFFSET_STEP_MS) }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Earlier")
                        }
                        Text(
                            text = String.format(
                                Locale.ROOT,
                                "%+.1fs",
                                subtitleOffsetMs / 1000.0,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { onSubtitleOffsetChange(OFFSET_STEP_MS) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Later")
                        }
                        if (subtitleOffsetMs != 0L) {
                            TextButton(onClick = onSubtitleOffsetReset) { Text("Reset") }
                        }
                    }
                }

                item {
                    Text(
                        text = "Text Color",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PLAYER_TEXT_COLORS.forEach { (argb, _) ->
                            ColorSwatch(
                                color = Color(argb),
                                selected = argb == subtitleColor,
                                onClick = { onSubtitleColorChange(argb) },
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Background Color",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PLAYER_BACKGROUND_COLORS.forEach { (argb, name) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ColorSwatch(
                                    color = Color(argb),
                                    selected = argb == subtitleBgColor,
                                    onClick = { onSubtitleBackgroundChange(argb) },
                                )
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Display Mode",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    PlayerDisplayMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDisplayModeChange(mode) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (mode == displayMode) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                if (episodes.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Episode",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(episodes) { episode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEpisodeSelected(episode.number) }
                                .padding(vertical = 10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Episode ${episode.number}" +
                                        if (episode.name.isBlank()) "" else " - ${episode.name}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (episode.overview.isNotBlank()) {
                                    Text(
                                        text = episode.overview,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                    )
                                }
                            }
                            if (episode.number == currentEpisode) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One colour of the options dialog.
 *
 * Mirrors the original's `h0.c`: a 32 dp circle with a one dp border, which
 * grows to three dp when the colour is the one that is in use.
 */
@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(32.dp)
            .background(color, CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape,
            )
            .clickable { onClick() },
    )
}

/**
 * The step of the sync offset.
 *
 * The original steps by `Context.VERSION_ECMASCRIPT`, which is 250.
 */
private const val OFFSET_STEP_MS = 250L
