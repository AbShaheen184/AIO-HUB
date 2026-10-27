package com.movies.premium.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.components.PosterGridCard
import com.movies.premium.ui.viewmodel.BrowseSort
import com.movies.premium.ui.viewmodel.BrowseViewModel

private const val TMDB_BASE = "https://image.tmdb.org/t/p/w342"

/** The smallest a poster may be in the grid. */
private val GRID_CELL_SIZE = 104.dp

/**
 * The years the year filter offers, newest first.
 *
 * The original's browse screen filters by release year as well; these are the
 * years a title of the last fifteen years can be in.
 */
private val YEAR_FILTERS: List<Int?> = buildList {
    add(null)
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    for (year in thisYear downTo thisYear - 14) add(year)
}

/**
 * Browse grid for Movies and TV Shows, with genre and sort filters.
 *
 * The grid is the shape the original uses: a row of genre chips, a row of
 * sort chips and then the titles as a poster grid. The grid is adaptive, so
 * it shows two posters per row on a phone in portrait and more on a tablet.
 */
@Composable
fun BrowseScreen(
    isTv: Boolean,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(isTv) {
        viewModel.setMode(isTv)
    }

    // The next page is loaded when the grid is scrolled close to its end, the
    // way the original's lists load their next page.
    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(lastVisible, state.movies.size, state.tvShows.size) {
        val count = if (isTv) state.tvShows.size else state.movies.size
        if (count > 0 && lastVisible >= count - 6) {
            viewModel.loadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.genres) { genre ->
                FilterChip(
                    selected = state.selectedGenre?.id == genre.id,
                    onClick = {
                        viewModel.selectGenre(
                            if (state.selectedGenre?.id == genre.id) null else genre,
                        )
                    },
                    label = { Text(genre.name ?: "") },
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(BrowseSort.entries.toList()) { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = { viewModel.selectSort(sort) },
                    label = { Text(sort.label) },
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(YEAR_FILTERS) { year ->
                FilterChip(
                    selected = state.selectedYear == year,
                    onClick = {
                        viewModel.selectYear(
                            if (state.selectedYear == year) null else year,
                        )
                    },
                    label = { Text(year?.toString() ?: "All") },
                )
            }
        }

        when {
            state.loading && state.movies.isEmpty() && state.tvShows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            // The request failed and there is nothing to show — say so and
            // offer a retry instead of leaving an empty screen.
            state.error != null && state.movies.isEmpty() && state.tvShows.isEmpty() -> {
                val message = state.error.orEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.load() }) { Text("Retry") }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(GRID_CELL_SIZE),
                    state = gridState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Movies and shows have different item types, so the two
                    // branches are rendered separately instead of sharing a
                    // type-erased list.
                    if (isTv) {
                        itemsIndexed(
                            state.tvShows,
                            // The index is part of the key so that a page that
                            // repeats an id can never crash the grid.
                            key = { index, show -> "show-$index-${show.id}" },
                        ) { _, show ->
                            PosterGridCard(
                                posterUrl = show.posterPath?.let { TMDB_BASE + it },
                                title = show.name ?: "",
                                rating = show.voteAverage,
                                onClick = { onItemClick(show.id) },
                            )
                        }
                    } else {
                        itemsIndexed(
                            state.movies,
                            key = { index, movie -> "movie-$index-${movie.id}" },
                        ) { _, movie ->
                            PosterGridCard(
                                posterUrl = movie.posterPath?.let { TMDB_BASE + it },
                                title = movie.title ?: "",
                                rating = movie.voteAverage,
                                onClick = { onItemClick(movie.id) },
                            )
                        }
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
