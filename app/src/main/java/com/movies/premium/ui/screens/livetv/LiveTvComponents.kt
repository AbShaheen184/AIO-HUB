package com.movies.premium.ui.screens.livetv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.movies.premium.data.iptv.EpgProgram
import com.movies.premium.data.remote.model.IptvChannel
import com.movies.premium.data.remote.model.MatchSource
import com.movies.premium.data.remote.model.SportsMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Live TV building blocks.
 *
 * The original splits its Live TV UI over a handful of obfuscated files
 * (`ui.screens.livetv.a` for the screen itself, `ui.screens.livetv.j` for the
 * list items, `ui.components.e0` for the channel card and
 * `ui.components.q1` for the section header). The replica keeps the same
 * pieces, just in one file next to the screen.
 */

/** The country names the original shows for Pluto TV's country groups. */
private val COUNTRY_NAMES: Map<String, String> = mapOf(
    "US" to "United States",
    "GB" to "United Kingdom",
    "UK" to "United Kingdom",
    "CA" to "Canada",
    "DE" to "Germany",
    "FR" to "France",
    "IT" to "Italy",
    "ES" to "Spain",
    "AU" to "Australia",
    "NZ" to "New Zealand",
    "IE" to "Ireland",
    "NL" to "Netherlands",
    "BE" to "Belgium",
    "AT" to "Austria",
    "CH" to "Switzerland",
    "PT" to "Portugal",
    "SE" to "Sweden",
    "NO" to "Norway",
    "DK" to "Denmark",
    "FI" to "Finland",
    "BR" to "Brazil",
    "MX" to "Mexico",
    "AR" to "Argentina",
    "CL" to "Chile",
    "CO" to "Colombia",
)

/**
 * The display name of a country code.
 *
 * The original looks the code up in `com.app.mlounge.ui.viewmodel.g0` and falls
 * back to the raw code when it is not in the table.
 */
fun liveTvCountryName(code: String): String =
    COUNTRY_NAMES[code.trim().uppercase(Locale.ROOT)] ?: code

/**
 * A chip of the provider tab row, the group row and the server row.
 *
 * Mirrors the original's `ui.screens.livetv.a.e()`: a rounded (8 dp) surface
 * whose container is the primary colour at 30 % when it is selected.
 */
@Composable
fun LiveTvChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = if (selected) {
            colors.primary.copy(alpha = 0.30f)
        } else {
            colors.surfaceVariant
        },
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The header of a filter row ("Filter groups" / "Filter server").
 *
 * Mirrors the original's `androidx.compose.material3.q1.b()`: an icon and the
 * label, the icon being 20 dp.
 */
@Composable
fun LiveTvSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = tint,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * A channel card.
 *
 * Mirrors the original's `ui.components.e0.d()`: a rounded (8 dp) surface with
 * a thin border, the logo on the left and the name plus the group on the
 * right. A long press adds the channel to the favourites.
 */
@Composable
fun LiveTvChannelCard(
    channel: IptvChannel,
    groupLabel: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = channel.logo ?: channel.fallbackLogo,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(120.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            ) {
                Text(
                    text = channel.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!groupLabel.isNullOrBlank()) {
                    Text(
                        text = groupLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A channel row with its current and next programme.
 *
 * Mirrors the original's `androidx.compose.foundation.gestures.c2` (case 9):
 * the logo, the channel name, the running programme, the next one and a
 * progress bar with the remaining minutes.
 */
@Composable
fun LiveTvEpgRow(
    channel: IptvChannel,
    current: EpgProgram?,
    next: EpgProgram?,
    groupLabel: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = channel.logo ?: channel.fallbackLogo,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(52.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            ) {
                Text(
                    text = channel.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current != null) {
                    Text(
                        text = "▶  ${current.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val nextText = if (next != null) {
                    "Next ${formatTime(next.startTimeMs)}: ${next.title}"
                } else {
                    groupLabel.orEmpty()
                }
                if (nextText.isNotBlank()) {
                    Text(
                        text = nextText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val running = current
                if (running != null) {
                    LinearProgressIndicator(
                        progress = { running.progressFraction() },
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .height(3.dp),
                    )
                    val start = formatTime(running.startTimeMs)
                    val remaining = ((running.endTimeMs - System.currentTimeMillis()) / 60_000L)
                        .coerceAtLeast(0L)
                    Text(
                        text = "$start  ‑${remaining}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A sports match card.
 *
 * Mirrors the original's `androidx.compose.animation.core.f0` (case 23): the
 * poster on the left, then the title, the tournament, the date and the server.
 */
@Composable
fun LiveTvSportsCard(
    match: SportsMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = match.poster?.let { posterUrl(it) },
                contentDescription = match.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = match.title ?: "Unknown Match",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!match.tournament.isNullOrBlank()) {
                    Text(
                        text = match.tournament,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                match.date?.let { date ->
                    Text(
                        text = formatMatchDate(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (!match.server.isNullOrBlank()) {
                    Text(
                        text = match.server,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

/**
 * One row of the "Choose Source" sheet.
 *
 * Mirrors the original's `androidx.compose.foundation.text.contextmenu.internal.q`
 * (case 3): a rounded surface whose text is the source name, the source id or
 * "Unknown".
 */
@Composable
fun LiveTvSourceRow(
    source: MatchSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = source.source ?: source.id ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The poster of a sports match. The original prefixes it with `streamed.pk`. */
private fun posterUrl(poster: String): String =
    if (poster.startsWith("http")) poster else "https://streamed.pk$poster"

/** `HH:mm` of an epoch timestamp. */
private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

/** `MMM d, h:mm a` of an epoch timestamp — the original's match date format. */
private fun formatMatchDate(millis: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
