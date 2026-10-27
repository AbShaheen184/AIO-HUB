package com.movies.premium.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movies.premium.data.remote.debrid.DebridService
import com.movies.premium.ui.viewmodel.AudioTrackInfo
import com.movies.premium.ui.viewmodel.SubtitleItem
import com.movies.premium.ui.viewmodel.formatPlayerTime
import com.movies.premium.ui.viewmodel.languageName
import com.movies.premium.ui.tv.focusRing

/**
 * The dialogs of the player.
 *
 * Every dialog mirrors one of the original's own dialogs, which live in the same
 * package:
 *
 * - `t` — the "Resume Playback?" dialog.
 * - `u` — the "Seeking Unstable" panel.
 * - `v` — the debrid service picker.
 * - `h0.r` — the subtitle picker.
 * - `h0` — the audio picker.
 */

/**
 * The "Resume Playback?" dialog.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.player.t`: the title is
 * "Resume Playback?", the body is "You stopped at " followed by the position, and
 * the two buttons are "Restart" and "Resume".
 */
@Composable
fun ResumePlaybackDialog(
    positionMs: Long,
    onRestart: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        title = { Text("Resume Playback?") },
        text = { Text("You stopped at ${formatPlayerTime(positionMs)}") },
        confirmButton = {
            TextButton(onClick = onResume) { Text("Resume") }
        },
        dismissButton = {
            TextButton(onClick = onRestart) { Text("Restart") }
        },
    )
}

/**
 * The "Seeking Unstable" panel.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.player.u`: the title is
 * "Seeking Unstable", the body explains the problem, and the two buttons are
 * "Reset to 0:00" (with a block icon) and "Disable Seeking" (with a timer icon).
 */
@Composable
fun SeekingUnstableDialog(
    onResetToZero: () -> Unit,
    onDisableSeeking: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        icon = {
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text("Seeking Unstable") },
        text = {
            Text(
                "The stream is taking too long to resume after seeking. " +
                    "Large remux files often have this issue on some servers.",
            )
        },
        confirmButton = {
            TextButton(onClick = onDisableSeeking) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("Disable Seeking", modifier = Modifier.padding(start = 8.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = onResetToZero) { Text("Reset to 0:00") }
        },
    )
}

/**
 * The subtitle picker.
 *
 * The original's picker is `h0.r`: it lists the subtitles of the media and the
 * ones the user added, and it offers a "Custom Subtitle" entry that opens the
 * system file picker.
 */
@Composable
fun SubtitlePickerDialog(
    subtitles: List<SubtitleItem>,
    activeSubtitle: SubtitleItem?,
    loading: Boolean,
    onSelect: (SubtitleItem) -> Unit,
    onClear: () -> Unit,
    onCustomSubtitle: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        title = { Text("Subtitles") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Off", modifier = Modifier.fillMaxWidth())
                }
                subtitles.forEach { item ->
                    val selected = activeSubtitle?.url == item.url &&
                        activeSubtitle?.label == item.label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = item.label.ifBlank { languageName(item.lang) },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onCustomSubtitle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Custom Subtitle", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * The audio picker.
 *
 * The original shows the tracks it built in `z0.v(Tracks)`, with the label of the
 * active one marked. Its dialog is headed "Audio Track"
 * (`com.app.mlounge.ui.screens.person.b`); the top bar entry that opens it is
 * called "Audio".
 */
@Composable
fun AudioPickerDialog(
    audioTracks: List<AudioTrackInfo>,
    onSelect: (AudioTrackInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        title = { Text("Audio Track") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                audioTracks.forEach { track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(track) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = track.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (track.isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * The debrid service picker.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.player.v`: the title is
 * "Select Debrid Service" and the subtitle is "Choose which service to use for
 * playback". The sources screen shows the same list under the title "Choose a
 * service"; the player keeps the original's wording.
 */
@Composable
fun DebridServicePickerDialog(
    services: List<DebridService>,
    onSelect: (DebridService) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        title = { Text("Select Debrid Service") },
        text = {
            Column {
                Text(
                    text = "Choose which service to use for playback",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                services.forEach { service ->
                    TextButton(
                        onClick = { onSelect(service) },
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

/**
 * The message the player shows when it cannot play.
 */
@Composable
fun PlayerErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        title = { Text("Playback failed") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

/**
 * The overlay the player shows while it is looking for the next episode.
 *
 * Mirrors the original's `h0.h(...)`: the title is "Auto-Playing Next Episode" and
 * the status is the one the view model reported, or "Loading next episode..." when
 * there is none.
 */
@Composable
fun AutoPlayOverlay(
    title: String,
    status: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Auto-Playing Next Episode",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Text(
                text = status.ifBlank { "Loading next episode..." },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

/**
 * The row of the source strip.
 *
 * The original's player lets the user switch the source while it plays; the
 * replica keeps the strip the previous screen had.
 */
@Composable
fun SourceStrip(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            TextButton(
                onClick = { onSelect(index) },
                modifier = Modifier.background(
                    if (index == selectedIndex) {
                        Color.White.copy(alpha = 0.2f)
                    } else {
                        Color.Transparent
                    },
                    RoundedCornerShape(8.dp),
                ),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}
