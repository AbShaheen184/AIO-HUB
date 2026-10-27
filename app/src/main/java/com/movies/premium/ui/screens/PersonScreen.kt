package com.movies.premium.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.movies.premium.ui.components.PosterCard
import com.movies.premium.ui.viewmodel.PersonViewModel

private const val TMDB_BASE = "https://image.tmdb.org/t/p/"

/** Actor/director profile with their combined filmography. */
@Composable
fun PersonScreen(
    onMovieClick: (Int) -> Unit,
    onTvClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val person = state.person
    if (person == null) {
        Text("Couldn't load person", modifier = modifier.padding(20.dp))
        return
    }

    val credits = person.combinedCredits?.cast.orEmpty()
        .distinctBy { "${it.mediaType}-${it.id}" }
        .sortedByDescending { it.voteAverage }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                AsyncImage(
                    model = person.profilePath?.let { TMDB_BASE + "w342" + it },
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(140.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                ) {
                    Text(person.name ?: "", style = MaterialTheme.typography.headlineSmall)
                    person.knownForDepartment?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (!person.birthday.isNullOrBlank()) {
                        Text(
                            text = listOfNotNull(
                                person.birthday,
                                person.deathday,
                            ).joinToString(" – "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        if (!person.biography.isNullOrBlank()) {
            item {
                Text(
                    text = person.biography,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        if (credits.isNotEmpty()) {
            item {
                Text(
                    "Known For",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(credits, key = { "${it.mediaType}-${it.id}" }) { credit ->
                        PosterCard(
                            posterUrl = credit.posterPath?.let { TMDB_BASE + "w342" + it },
                            title = credit.title ?: credit.name ?: "",
                            rating = credit.voteAverage,
                            onClick = {
                                if (credit.mediaType == "tv") onTvClick(credit.id) else onMovieClick(credit.id)
                            },
                        )
                    }
                }
            }
        }
    }
}
