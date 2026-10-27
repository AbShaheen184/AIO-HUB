package com.movies.premium.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.movies.premium.data.local.entity.HistoryEntity
import com.movies.premium.ui.viewmodel.LibraryViewModel
import java.util.Locale

private const val TMDB_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * The watch history.
 *
 * The original's screen is `com.app.mlounge.ui.screens.history.a`. It shows:
 *
 * * the headline "Watch History" and a clear-all action,
 * * a chip row with the filters **All**, **Movies**, **TV Shows**, **Live TV**,
 *   **Anime** and — only when the adult section is enabled — **Adult**,
 * * a grid of posters, and
 * * an empty state whose wording depends on the selected filter:
 *   "No watch history yet" / "Content you watch will appear here" for *All* and
 *   "No \<filter\> in your history" / "Watch some \<filter\> and they'll appear here"
 *   for a specific filter.
 */
@Composable
fun HistoryScreen(
    onItemClick: (Int, String) -> Unit,
    onClearAll: () -> Unit = {},
    adultEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.history.collectAsStateWithLifecycle()
    var selected by remember { mutableIntStateOf(0) }

    val filters = remember(adultEnabled) {
        buildList {
            add("All")
            add("Movies")
            add("TV Shows")
            add("Live TV")
            add("Anime")
            if (adultEnabled) add("Adult")
        }
    }

    // The original's "Include Adult in History" switch decides whether the
    // adult titles of TMDB are shown in the list.
    val showAdult by viewModel.showAdultHistory.collectAsStateWithLifecycle()
    val visible = remember(state, showAdult) {
        if (showAdult) state else state.filter { !it.isAdult }
    }

    val filtered = remember(visible, selected) { filter(visible, selected) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Watch History",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClearAll) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear history")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEachIndexed { index, label ->
                FilterChip(
                    selected = selected == index,
                    onClick = { selected = index },
                    label = { Text(label) },
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyHistory(filterName = filters.getOrNull(selected))
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filtered, key = { it.key }) { item ->
                HistoryPoster(
                    item = item,
                    onClick = { onItemClick(item.tmdbId, item.mediaType) },
                )
            }
        }
    }
}

/**
 * One poster in the history grid.
 *
 * The original's grid uses a fixed column count taken from the theme and a 2:3 poster
 * card; the replica uses an adaptive grid so the same component works on a phone and on
 * a TV.
 */
@Composable
private fun HistoryPoster(
    item: HistoryEntity,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Card(
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.posterPath?.let { TMDB_BASE + it },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The empty state. The icon is 64 dp; the title uses the title style and the subtitle
 * the body style, both centred.
 */
@Composable
private fun EmptyHistory(filterName: String?) {
    val all = filterName == null || filterName == "All"
    val lower = filterName?.lowercase(Locale.ROOT)
    val title = if (all) "No watch history yet" else "No $lower in your history"
    val subtitle = if (all) {
        "Content you watch will appear here"
    } else {
        "Watch some $lower and they'll appear here"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}

/**
 * Applies the chip filter. The index order matches the original's chip row:
 * 0 = All, 1 = Movies (`mediaType == "movie"`), 2 = TV Shows (`mediaType == "tv"`),
 * 3 = Live TV, 4 = Anime (`mediaType` starts with "anime"), 5 = Adult.
 */
private fun filter(items: List<HistoryEntity>, index: Int): List<HistoryEntity> = when (index) {
    0 -> items
    1 -> items.filter { it.mediaType == "movie" }
    2 -> items.filter { it.mediaType == "tv" }
    3 -> items.filter { it.mediaType == "live_tv" }
    4 -> items.filter { it.mediaType.startsWith("anime") }
    5 -> items.filter { it.mediaType == "adult" }
    else -> items
}
