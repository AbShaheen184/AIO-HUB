package com.movies.premium.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.components.PosterGridCard
import com.movies.premium.ui.viewmodel.SearchViewModel

private const val TMDB_BASE = "https://image.tmdb.org/t/p/w342"

/** The smallest a poster may be in the search grid. */
private val GRID_CELL_SIZE = 104.dp

@Composable
fun SearchScreen(
    onMovieClick: (Int) -> Unit,
    onTvClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search movies and shows") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(GRID_CELL_SIZE),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                state.movies,
                // The index is part of the key so that a repeated id in the
                // results can never crash the grid.
                key = { index, movie -> "movie-$index-${movie.id}" },
            ) { _, movie ->
                PosterGridCard(
                    posterUrl = movie.posterPath?.let { TMDB_BASE + it },
                    title = movie.title ?: "",
                    rating = movie.voteAverage,
                    onClick = { onMovieClick(movie.id) },
                )
            }
            itemsIndexed(
                state.tvShows,
                key = { index, show -> "show-$index-${show.id}" },
            ) { _, show ->
                PosterGridCard(
                    posterUrl = show.posterPath?.let { TMDB_BASE + it },
                    title = show.name ?: "",
                    rating = show.voteAverage,
                    onClick = { onTvClick(show.id) },
                )
            }
        }
    }
}
