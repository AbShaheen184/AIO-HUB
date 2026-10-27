package com.movies.premium.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.movies.premium.ui.components.PersonCard
import com.movies.premium.ui.components.PosterCard
import com.movies.premium.ui.tv.LocalIsTv
import com.movies.premium.ui.viewmodel.DetailViewModel
import kotlinx.coroutines.delay

private const val TMDB_BASE = "https://image.tmdb.org/t/p/"

/**
 * What the user asked to play from the detail screen.
 *
 * A series is played per episode, so the request carries the season and the
 * episode number as well; a movie leaves them at zero.
 */
data class DetailPlayRequest(
    val id: Int,
    val mediaType: String,
    val title: String,
    val season: Int = 0,
    val episode: Int = 0,
)

/**
 * The detail screen of a movie or of a series.
 *
 * The layout is the one of the anime screen (`ui/screens/anime/AnimeScreen.kt`),
 * which is the shape the original uses as well: a backdrop with the poster and
 * the information block on top of it, a full width **Play** button next to the
 * favourite button, and then the synopsis, the cast and the similar titles.
 *
 * The original's movie screen has these labels (they are read out of the
 * smali in `decoded/smali/com/app/mlounge/ui/screens/movies/`): "Synopsis",
 * "Cast", "Director", "Director: ", "Watch Trailer", "Similar Movies",
 * "Download", "Favourite", "Favourited", "Play", "Watch". The trailer is a
 * YouTube link — the original builds `https://www.youtube.com/watch?v=` + the
 * video key.
 *
 * A television gets a different shape: the poster and the information stand
 * side by side under the artwork, because a television is watched from far
 * away and the columns of a telephone are far too narrow for it.
 */
@Composable
fun DetailScreen(
    onPlay: (DetailPlayRequest) -> Unit,
    onDownload: (DetailPlayRequest) -> Unit,
    onPersonClick: (Int) -> Unit,
    onSimilarMovieClick: (Int) -> Unit,
    onSimilarTvClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The screen has to be readable in portrait as well as in landscape. The
    // test phone is 384 dp wide in portrait and 845 dp in landscape, and the
    // sidebar takes another 56 to 80 dp of that. On a narrow screen the
    // artwork shrinks and the spacing tightens, so that the information is
    // still readable; on a wide screen the roomy shape of the original is
    // kept.
    val narrow = LocalConfiguration.current.screenWidthDp < 560
    // A television is not a phone: it has its own shape, and the same
    // column of a telephone is unreadable on it.
    val isTv = LocalIsTv.current
    val backdropHeight = if (narrow) 200.dp else 280.dp
    val posterWidth = if (narrow) 92.dp else 120.dp
    val posterHeight = if (narrow) 138.dp else 180.dp
    val gap = if (narrow) 12.dp else 16.dp
    val sidePadding = if (narrow) 12.dp else 16.dp
    // A button label has to fit into half of the row, and half of a narrow
    // row is about 148 dp — "Watch Trailer" does not fit into that.
    val buttonLabelStyle = if (narrow) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelLarge
    }
    // The hero of a television: the artwork at the back, the poster and the
    // information side by side in front of it.
    val tvHeroHeight = 260.dp
    val tvPosterWidth = 110.dp
    val tvPosterHeight = 165.dp
    val tvSidePadding = 24.dp

    // The button the focus lands on when the screen opens on a television:
    // the Play button is the one the user came for.
    val playButton = remember { FocusRequester() }

    val hasDetail = state.movie != null || state.tvShow != null

    when {
        // Nothing loaded yet.
        state.loading && !hasDetail -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // The request failed — say so and offer a retry, instead of leaving
        // the user on an empty screen.
        !hasDetail -> {
            DetailErrorState(
                message = state.error ?: "Couldn't load details",
                onRetry = { viewModel.load() },
            )
        }

        else -> {
            val isTvShow = state.mediaType == "tv"
            val title = state.movie?.title ?: state.tvShow?.name.orEmpty()
            val overview = state.movie?.overview ?: state.tvShow?.overview.orEmpty()
            val backdrop = state.movie?.backdropPath ?: state.tvShow?.backdropPath
            val poster = state.movie?.posterPath ?: state.tvShow?.posterPath
            val rating = state.movie?.voteAverage ?: state.tvShow?.voteAverage ?: 0.0
            val year = (state.movie?.releaseDate ?: state.tvShow?.firstAirDate)?.take(4)
            val genres = state.movie?.genres ?: state.tvShow?.genres.orEmpty()
            val runtime = state.movie?.runtime ?: 0
            val status = state.movie?.status ?: state.tvShow?.status
            val episodeTotal = state.tvShow?.numberOfEpisodes ?: 0
            val director = state.credits?.crew.orEmpty()
                .firstOrNull { it.job?.equals("Director", ignoreCase = true) == true }
                ?.name
            val firstEpisode = state.episodes.firstOrNull()

            // The request both the "Play" and the "Download" button send.
            val playRequest = DetailPlayRequest(
                id = state.mediaId,
                mediaType = state.mediaType,
                title = title,
                season = if (isTvShow) state.selectedSeason else 0,
                episode = if (isTvShow) (firstEpisode?.number ?: 0) else 0,
            )

            // The line under the title: the length, the state and the number
            // of the episodes.
            val meta = listOfNotNull(
                runtime.takeIf { it > 0 }?.let { "$it min" },
                status?.takeIf { it.isNotBlank() },
                if (isTvShow && episodeTotal > 0) "$episodeTotal episodes" else null,
            ).joinToString(" • ")

            // The names of the genres, without the empty ones.
            val genreNames = genres
                .mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // ---- the hero -----------------------------------------------

                item(key = "backdrop") {
                    if (isTv) {
                        // The shape of a television: the artwork fills the
                        // whole hero, the poster and the information stand
                        // next to each other in front of it.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(tvHeroHeight),
                        ) {
                            // The focus of a television has to be somewhere
                            // from the start: it is the Play button, the one
                            // the user came for. The wait gives the row of
                            // the buttons the time to be measured.
                            LaunchedEffect(Unit) {
                                delay(400)
                                runCatching { playButton.requestFocus() }
                            }
                            AsyncImage(
                                model = backdrop?.let { TMDB_BASE + "w780" + it },
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // The artwork is at the back and the text in
                            // front of it, so the scrim is dark on the left
                            // and lets the picture through on the right. It
                            // is not completely transparent on the right
                            // either: the labels of the buttons are the
                            // colour of the theme, and they have to be read
                            // against the picture.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                                            0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
                                            1f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                        ),
                                    ),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        start = tvSidePadding,
                                        end = tvSidePadding,
                                        top = 12.dp,
                                        bottom = 12.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = poster?.let { TMDB_BASE + "w342" + it },
                                    contentDescription = title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = tvPosterWidth, height = tvPosterHeight)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.width(gap))
                                Column(Modifier.weight(1f)) {
                                    DetailTitleBlock(
                                        title = title,
                                        rating = rating,
                                        year = year,
                                        meta = meta,
                                        genreNames = genreNames,
                                        titleStyle = MaterialTheme.typography.titleLarge,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    DetailActionButtons(
                                        canPlay = !isTvShow || firstEpisode != null,
                                        isFavorite = state.isFavorite,
                                        trailerKey = state.trailerKey,
                                        trailerLabel = "Watch Trailer",
                                        buttonLabelStyle = buttonLabelStyle,
                                        twoPerRow = true,
                                        stretch = false,
                                        playFocusRequester = playButton,
                                        onPlay = { onPlay(playRequest) },
                                        onDownload = { onDownload(playRequest) },
                                        onToggleFavorite = { viewModel.toggleFavorite() },
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.toggleWatchlist() },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(
                                    imageVector = if (state.inWatchlist) {
                                        Icons.Default.Bookmark
                                    } else {
                                        Icons.Default.BookmarkBorder
                                    },
                                    contentDescription = "Watchlist",
                                    tint = Color.White,
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = backdrop?.let { TMDB_BASE + "w780" + it },
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(backdropHeight),
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(backdropHeight)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                                MaterialTheme.colorScheme.background,
                                            ),
                                        ),
                                    ),
                            )
                            IconButton(
                                onClick = { viewModel.toggleWatchlist() },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(
                                    imageVector = if (state.inWatchlist) {
                                        Icons.Default.Bookmark
                                    } else {
                                        Icons.Default.BookmarkBorder
                                    },
                                    contentDescription = "Watchlist",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }

                // ---- the poster, the information and the actions -------------

                item(key = "info") {
                    Column(Modifier.padding(start = sidePadding, end = sidePadding, top = 12.dp)) {
                        if (!isTv) {
                            Row(verticalAlignment = Alignment.Top) {
                                AsyncImage(
                                    model = poster?.let { TMDB_BASE + "w342" + it },
                                    contentDescription = title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = posterWidth, height = posterHeight)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.width(gap))
                                Column(Modifier.weight(1f)) {
                                    DetailTitleBlock(
                                        title = title,
                                        rating = rating,
                                        year = year,
                                        meta = meta,
                                        genreNames = genreNames,
                                        titleStyle = if (narrow) {
                                            MaterialTheme.typography.titleMedium
                                        } else {
                                            MaterialTheme.typography.titleLarge
                                        },
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // The action buttons.
                            //
                            // Two buttons side by side need about 150 dp
                            // each: the icon, the label and the button's own
                            // padding. A portrait phone with the sidebar
                            // open is narrower than that, and the label
                            // then breaks in the middle ("Favouri / te"). So
                            // the width that is really left for the row is
                            // measured, and the buttons are stacked one
                            // below the other when two of them do not fit.
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                DetailActionButtons(
                                    canPlay = !isTvShow || firstEpisode != null,
                                    isFavorite = state.isFavorite,
                                    trailerKey = state.trailerKey,
                                    trailerLabel = if (narrow) "Trailer" else "Watch Trailer",
                                    buttonLabelStyle = buttonLabelStyle,
                                    twoPerRow = maxWidth >= 300.dp,
                                    onPlay = { onPlay(playRequest) },
                                    onDownload = { onDownload(playRequest) },
                                    onToggleFavorite = { viewModel.toggleFavorite() },
                                )
                            }
                        }

                        // ---- the state of the source resolution ---------------
                        //
                        // The original's movie detail screen shows "Resolving..."
                        // while it looks for the sources of the title, and the
                        // replica resolves them in the background as well — but
                        // it never said so, so the screen looked as if the Play
                        // button had been ignored. The backend asks four
                        // providers in turn and needs about eleven seconds.
                        if (!isTvShow && state.loadingStreams) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "Resolving...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (!isTvShow && state.streamsResolved && state.streams.isEmpty()) {
                            // A movie with no source at all: the Play button has
                            // nothing to play, and the user should know that
                            // before they tap it. A series is never resolved
                            // here — it is resolved per episode.
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "No sources found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // ---- the synopsis -----------------------------------

                        if (overview.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Synopsis",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // The original shows the director under the synopsis.
                        director?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Director: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ---- the seasons and the episodes of a series -------------------

                if (state.seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        Text(
                            text = "Seasons",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(
                                state.seasons,
                                key = { _, season -> "season-$season" },
                            ) { _, season ->
                                FilterChip(
                                    selected = season == state.selectedSeason,
                                    onClick = { viewModel.selectSeason(season) },
                                    label = { Text("Season $season") },
                                )
                            }
                        }
                    }
                }

                if (state.loadingEpisodes) {
                    item(key = "episodes-loading") {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                itemsIndexed(
                    state.episodes,
                    // The episode number is unique inside a season, but the
                    // index is part of the key as well so that a list with a
                    // repeated number can never crash the list.
                    key = { index, episode -> "episode-${state.selectedSeason}-$index-${episode.number}" },
                ) { _, episode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlay(
                                    DetailPlayRequest(
                                        id = state.mediaId,
                                        mediaType = "tv",
                                        title = title,
                                        season = state.selectedSeason,
                                        episode = episode.number,
                                    ),
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "E${episode.number}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(44.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = episode.name?.takeIf { it.isNotBlank() } ?: "Episode ${episode.number}",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!episode.overview.isNullOrBlank()) {
                                Text(
                                    text = episode.overview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    }
                }

                // ---- the cast -------------------------------------------------

                val cast = state.credits?.cast.orEmpty()
                if (cast.isNotEmpty()) {
                    item(key = "cast") {
                        Text(
                            text = "Cast",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(
                                cast,
                                // TMDB can list the same person twice; a
                                // repeated key would crash the list, so the
                                // index is part of the key.
                                key = { index, member -> "cast-$index-${member.id}" },
                            ) { _, member ->
                                PersonCard(
                                    profileUrl = member.profilePath?.let { TMDB_BASE + "w185" + it },
                                    name = member.name ?: "",
                                    character = member.character,
                                    onClick = { onPersonClick(member.id) },
                                )
                            }
                        }
                    }
                }

                // ---- the similar titles ----------------------------------------

                if (state.similarMovies.isNotEmpty()) {
                    item(key = "similar-movies") {
                        Text(
                            text = "Similar Movies",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(
                                state.similarMovies,
                                key = { index, movie -> "similar-movie-$index-${movie.id}" },
                            ) { _, movie ->
                                PosterCard(
                                    posterUrl = movie.posterPath?.let { TMDB_BASE + "w342" + it },
                                    title = movie.title ?: "",
                                    rating = movie.voteAverage,
                                    onClick = { onSimilarMovieClick(movie.id) },
                                )
                            }
                        }
                    }
                }

                if (state.similarTvShows.isNotEmpty()) {
                    item(key = "similar-shows") {
                        Text(
                            text = "Similar Shows",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(
                                state.similarTvShows,
                                key = { index, show -> "similar-show-$index-${show.id}" },
                            ) { _, show ->
                                PosterCard(
                                    posterUrl = show.posterPath?.let { TMDB_BASE + "w342" + it },
                                    title = show.name ?: "",
                                    rating = show.voteAverage,
                                    onClick = { onSimilarTvClick(show.id) },
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
 * The title of the title, with the rating, the year, the length and the
 * genres under it.
 *
 * The same block stands in two places: next to the poster of a telephone and
 * in the hero of a television.
 */
@Composable
private fun DetailTitleBlock(
    title: String,
    rating: Double,
    year: String?,
    meta: String,
    genreNames: List<String>,
    titleStyle: TextStyle,
) {
    Text(
        text = title,
        style = titleStyle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rating > 0) {
            DetailPill(
                text = "★ ${formatRating(rating)}",
                color = MaterialTheme.colorScheme.primary,
            )
        }
        year?.takeIf { it.isNotBlank() }?.let {
            DetailPill(
                text = it,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
    if (meta.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = meta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (genreNames.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = genreNames.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The action buttons of the detail screen.
 *
 * The Play button. It is the one control the screen cannot do without, so it
 * gets the full width of the row and the favourite button sits next to it —
 * the same shape as the anime screen's "Watch" / "Favourite" row. The
 * original's "Watch Trailer" button and its "Download" button are in the
 * row below: the trailer is a YouTube link, so it is opened with the browser
 * rather than with the app's own player, which only understands a direct
 * media URL, and the "Download" button opens the source picker in download
 * mode.
 *
 * The rows are in a column, and not side by side in a box: a box places all
 * of its children in the same place, so the second row would land on top of
 * the first one and the two rows would lie over each other.
 *
 * When two buttons do not fit into the width that is left — a portrait
 * telephone with the sidebar open — the buttons are stacked one below the
 * other instead.
 */
@Composable
private fun DetailActionButtons(
    canPlay: Boolean,
    isFavorite: Boolean,
    trailerKey: String?,
    trailerLabel: String,
    buttonLabelStyle: TextStyle,
    twoPerRow: Boolean,
    stretch: Boolean = true,
    playFocusRequester: FocusRequester? = null,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val context = LocalContext.current

    if (twoPerRow) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // A button that fills the half of the row is the right
                // shape on a telephone, where the row is narrow. On a
                // television the row is wide, and a button that is three
                // times as wide as it is high looks strange — there the
                // buttons take the size of their label.
                val buttonModifier = if (stretch) Modifier.weight(1f) else Modifier

                Button(
                    onClick = onPlay,
                    // A series can only be played when its episodes are
                    // known; a movie can always be played.
                    enabled = canPlay,
                    modifier = buttonModifier.then(
                        playFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Play", style = buttonLabelStyle, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onToggleFavorite,
                    modifier = buttonModifier,
                ) {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isFavorite) "Favourited" else "Favourite",
                        style = buttonLabelStyle,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val buttonModifier = if (stretch) Modifier.weight(1f) else Modifier
                trailerKey?.let { key ->
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.youtube.com/watch?v=$key"),
                            )
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = trailerLabel,
                            style = buttonLabelStyle,
                            maxLines = 1,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onDownload,
                    modifier = buttonModifier,
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Download", style = buttonLabelStyle, maxLines = 1)
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPlay,
                enabled = canPlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Play", style = buttonLabelStyle, maxLines = 1)
            }
            OutlinedButton(
                onClick = onToggleFavorite,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (isFavorite) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isFavorite) "Favourited" else "Favourite",
                    style = buttonLabelStyle,
                    maxLines = 1,
                )
            }
            trailerKey?.let { key ->
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/watch?v=$key"),
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = trailerLabel,
                        style = buttonLabelStyle,
                        maxLines = 1,
                    )
                }
            }
            OutlinedButton(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Download", style = buttonLabelStyle, maxLines = 1)
            }
        }
    }
}

/**
 * The screen the user sees when the title could not be loaded.
 *
 * The original shows a plain message; the replica adds a retry, because a
 * failed request is usually a network hiccup and not a broken title.
 */
@Composable
private fun DetailErrorState(
    message: String,
    onRetry: () -> Unit,
) {
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
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/** The small rounded label under the title — the rating, the year. */
@Composable
private fun DetailPill(
    text: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** The rating is shown with one decimal, the way the original shows it. */
private fun formatRating(rating: Double): String = String.format(java.util.Locale.US, "%.1f", rating)
