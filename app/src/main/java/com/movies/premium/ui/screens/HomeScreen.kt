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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.components.PosterCard
import com.movies.premium.ui.viewmodel.HomeViewModel

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w342"

private fun posterUrl(path: String?): String? =
    path?.takeIf { it.isNotBlank() }?.let { TMDB_IMAGE_BASE + it }

@Composable
fun HomeScreen(
    onMovieClick: (Int) -> Unit,
    onTvClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // A failed load must not leave a blank screen — say what went wrong and
    // offer a retry.
    if (state.error != null && state.trendingMovies.isEmpty() && state.popularMovies.isEmpty()) {
        val message = state.error.orEmpty()
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.load() }) { Text("Retry") }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            // The name of the app. The original calls itself "CinemaHQ", which
            // says nothing about the anime, music, live-TV and games sections
            // the same app also carries — hence "AIO Hub" ("all in one hub").
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "AIO Hub",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Movies · Series · Anime · Music · Live TV · Games",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.trendingMovies.isNotEmpty()) {
            item {
                MovieRow(
                    title = "Trending Movies",
                    ids = state.trendingMovies.map { it.id },
                    titles = state.trendingMovies.associate { it.id to (it.title ?: "") },
                    posters = state.trendingMovies.associate { it.id to posterUrl(it.posterPath) },
                    ratings = state.trendingMovies.associate { it.id to it.voteAverage },
                    onClick = onMovieClick,
                )
            }
        }

        if (state.trendingTvShows.isNotEmpty()) {
            item {
                MovieRow(
                    title = "Trending TV Shows",
                    ids = state.trendingTvShows.map { it.id },
                    titles = state.trendingTvShows.associate { it.id to (it.name ?: "") },
                    posters = state.trendingTvShows.associate { it.id to posterUrl(it.posterPath) },
                    ratings = state.trendingTvShows.associate { it.id to it.voteAverage },
                    onClick = onTvClick,
                )
            }
        }

        if (state.popularMovies.isNotEmpty()) {
            item {
                MovieRow(
                    title = "Popular Movies",
                    ids = state.popularMovies.map { it.id },
                    titles = state.popularMovies.associate { it.id to (it.title ?: "") },
                    posters = state.popularMovies.associate { it.id to posterUrl(it.posterPath) },
                    ratings = state.popularMovies.associate { it.id to it.voteAverage },
                    onClick = onMovieClick,
                )
            }
        }

        if (state.popularTvShows.isNotEmpty()) {
            item {
                MovieRow(
                    title = "Popular TV Shows",
                    ids = state.popularTvShows.map { it.id },
                    titles = state.popularTvShows.associate { it.id to (it.name ?: "") },
                    posters = state.popularTvShows.associate { it.id to posterUrl(it.posterPath) },
                    ratings = state.popularTvShows.associate { it.id to it.voteAverage },
                    onClick = onTvClick,
                )
            }
        }

        if (state.topRatedMovies.isNotEmpty()) {
            item {
                MovieRow(
                    title = "Top Rated",
                    ids = state.topRatedMovies.map { it.id },
                    titles = state.topRatedMovies.associate { it.id to (it.title ?: "") },
                    posters = state.topRatedMovies.associate { it.id to posterUrl(it.posterPath) },
                    ratings = state.topRatedMovies.associate { it.id to it.voteAverage },
                    onClick = onMovieClick,
                )
            }
        }
    }
}

@Composable
private fun MovieRow(
    title: String,
    ids: List<Int>,
    titles: Map<Int, String>,
    posters: Map<Int, String?>,
    ratings: Map<Int, Double>,
    onClick: (Int) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ids, key = { it }) { id ->
                PosterCard(
                    posterUrl = posters[id],
                    title = titles[id].orEmpty(),
                    rating = ratings[id],
                    onClick = { onClick(id) },
                )
            }
        }
    }
}
