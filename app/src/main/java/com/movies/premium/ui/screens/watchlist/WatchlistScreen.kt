package com.movies.premium.ui.screens.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.components.EmptyState
import com.movies.premium.ui.components.PosterGridCard
import com.movies.premium.ui.viewmodel.LibraryViewModel

private const val TMDB_BASE = "https://image.tmdb.org/t/p/w342"

/** The smallest a poster may be in the grid. */
private val GRID_CELL_SIZE = 104.dp

/**
 * The watchlist.
 *
 * The original's screen lives in `com.app.mlounge.ui.screens.watchlist` and shows a
 * "Sync Favourites" action next to the title (the `Filled.Sync` glyph, content
 * description "Sync Favourites"). The poster base URL is the original's
 * `https://image.tmdb.org/t/p/w342`.
 */
@Composable
fun WatchlistScreen(
    onItemClick: (Int, String) -> Unit,
    onSyncFavourites: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.watchlist.collectAsStateWithLifecycle()
    val showAdult by viewModel.showAdultFavourites.collectAsStateWithLifecycle()

    // The original's "Include Adult in Favourites" switch decides whether the
    // adult titles are shown in the list.
    val visible = remember(state, showAdult) {
        if (showAdult) state else state.filter { !it.isAdult }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Watchlist",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSyncFavourites) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = "Sync Favourites",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (visible.isEmpty()) {
            EmptyState("Your watchlist is empty", Modifier.fillMaxSize())
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(GRID_CELL_SIZE),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                visible,
                // The index is part of the key: the same title can be in the
                // watchlist twice (once as a movie and once as a series), and
                // a repeated key would crash the grid.
                key = { index, item -> "watchlist-$index-${item.mediaType}-${item.tmdbId}" },
            ) { _, item ->
                PosterGridCard(
                    posterUrl = item.posterPath?.let { TMDB_BASE + it },
                    title = item.title,
                    rating = item.rating,
                    onClick = { onItemClick(item.tmdbId, item.mediaType) },
                )
            }
        }
    }
}
