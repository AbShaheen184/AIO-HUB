package com.movies.premium.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.movies.premium.data.remote.model.MusicAlbum
import com.movies.premium.data.remote.model.MusicAlbumDetail
import com.movies.premium.data.remote.model.MusicArtist
import com.movies.premium.data.remote.model.MusicArtistDetail
import com.movies.premium.data.remote.model.MusicGenre
import com.movies.premium.data.remote.model.MusicItem
import com.movies.premium.data.remote.model.MusicTrack
import com.movies.premium.ui.viewmodel.MusicViewModel
import com.movies.premium.ui.tv.focusRing

/** The width the original gives a single grid cell. */
private val GRID_CELL_SIZE = 85.dp

/**
 * The music catalogue (`music` route).
 *
 * The original shows three filter dropdowns — the provider, the genre and the
 * section — above a grid of album and artist cards, and pages the grid in as
 * the user scrolls. The "YouTube Music" provider is not reproduced here: the
 * NewPipe extractor it relies on is not published to Maven Central, so the
 * source list is MusicMp3 and Deezer.
 */
@Composable
fun MusicScreen(
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()

    // The original starts the next page when the last visible cell is within
    // six items of the end of the list and the source still has a cursor.
    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(lastVisible, items.size) {
        if (items.isNotEmpty() && lastVisible >= items.size - 6) {
            viewModel.loadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Music",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        MusicFilters(
            source = selectedSource,
            sources = viewModel.sources,
            onSourceSelected = { viewModel.setSource(it) },
            genre = selectedGenre,
            genres = viewModel.genres,
            onGenreSelected = { viewModel.setGenre(it) },
            section = selectedSection,
            sections = viewModel.sections,
            onSectionSelected = { viewModel.setSection(it) },
        )

        if (loading || items.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(GRID_CELL_SIZE),
                state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { itemKey(it) }) { item ->
                    when (item) {
                        is MusicAlbum -> MusicCard(
                            title = item.title,
                            image = item.image,
                            subtitle = item.artist,
                            onClick = { onAlbumClick(item.url) },
                        )

                        is MusicArtist -> MusicCard(
                            title = item.name,
                            image = item.image,
                            subtitle = "Artist",
                            onClick = { onArtistClick(item.url) },
                        )

                        is MusicTrack -> MusicCard(
                            title = item.title,
                            image = item.image,
                            subtitle = item.artist,
                            onClick = { onAlbumClick(item.albumUrl) },
                        )
                    }
                }
                if (loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * The album page (`music_album_detail/{albumUrl}`).
 *
 * Shows the cover, the title block, the favourite / download row and the
 * track list. [onTrackPlay] is called with the track the user pressed.
 */
@Composable
fun MusicAlbumScreen(
    albumUrl: String,
    onBack: () -> Unit,
    onTrackPlay: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
    onDownloadAlbum: (MusicAlbumDetail) -> Unit = {},
    resolvingTrackId: String? = null,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val album by viewModel.albumDetail.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()

    LaunchedEffect(albumUrl) { viewModel.loadAlbumDetail(albumUrl) }
    DisposableEffect(albumUrl) { onDispose { viewModel.clearDetails() } }

    if (loading && album == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val detail = album
    if (detail == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Album unavailable",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val isFavourite = albumUrl in favourites

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            AsyncImage(
                model = detail.image,
                contentDescription = detail.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        Spacer(Modifier.height(16.dp))

        Column {
            Text(
                text = detail.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                detail.year?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                detail.genre?.let {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "${detail.tracks.size} tracks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        detail.description?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { viewModel.toggleFavourite(albumUrl) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (isFavourite) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isFavourite) "Favourited" else "Favourite")
            }
            Button(
                onClick = { onDownloadAlbum(detail) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Download Album")
            }
        }

        Spacer(Modifier.height(8.dp))

        detail.tracks.forEach { track ->
            TrackRow(
                track = track,
                isFavourite = track.id in favourites,
                onFavourite = { viewModel.toggleFavourite(track.id) },
                onPlay = { onTrackPlay(track) },
                isResolving = track.id == resolvingTrackId,
            )
        }
    }
}

/**
 * The artist page (`music_artist_detail/{artistUrl}`).
 *
 * The original draws the artist header, the biography and the discography
 * inside a single grid, so the header and the section title span the whole
 * row and the albums are the grid cells.
 */
@Composable
fun MusicArtistScreen(
    artistUrl: String,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val artist by viewModel.artistDetail.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()

    LaunchedEffect(artistUrl) { viewModel.loadArtistDetail(artistUrl) }
    DisposableEffect(artistUrl) { onDispose { viewModel.clearDetails() } }

    if (loading && artist == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val detail = artist
    if (detail == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Artist unavailable",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val isFavourite = artistUrl in favourites

    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_CELL_SIZE),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ArtistHeader(
                detail = detail,
                isFavourite = isFavourite,
                onBack = onBack,
                onFavourite = { viewModel.toggleFavourite(artistUrl) },
            )
        }
        detail.bio?.let { bio ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (detail.albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Discography",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(detail.albums, key = { it.url }) { album ->
                MusicCard(
                    title = album.title,
                    image = album.image,
                    subtitle = album.artist,
                    onClick = { onAlbumClick(album.url) },
                )
            }
        }
    }
}

// --- pieces -----------------------------------------------------------------

/** The provider / genre / section dropdowns of the browse screen. */
@Composable
private fun MusicFilters(
    source: String,
    sources: List<String>,
    onSourceSelected: (String) -> Unit,
    genre: MusicGenre,
    genres: List<MusicGenre>,
    onGenreSelected: (MusicGenre) -> Unit,
    section: String,
    sections: List<String>,
    onSectionSelected: (String) -> Unit,
) {
    Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Text(
            text = "Filters",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdownChip(
                label = source,
                options = sources.map { it to it },
                selected = source,
                onSelected = onSourceSelected,
            )
            FilterDropdownChip(
                label = genre.name,
                options = genres.map { it.id to it.name },
                selected = genre.id,
                onSelected = { id ->
                    genres.firstOrNull { it.id == id }?.let(onGenreSelected)
                },
            )
            FilterDropdownChip(
                label = section,
                options = sections.map { it to it },
                selected = section,
                onSelected = onSectionSelected,
            )
        }
    }
}

/** A chip that opens a dropdown menu, like the original's filter chips. */
@Composable
private fun FilterDropdownChip(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (label == selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.clickable { expanded = true },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.focusRing(),
        ) {
        options.forEach { (value, text) ->
            DropdownMenuItem(
                text = { Text(text) },
                onClick = {
                    expanded = false
                    onSelected(value)
                },
            )
        }
    }
}

/** One card of the music grid. */
@Composable
private fun MusicCard(
    title: String,
    image: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = image,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The header of the artist page. */
@Composable
private fun ArtistHeader(
    detail: MusicArtistDetail,
    isFavourite: Boolean,
    onBack: () -> Unit,
    onFavourite: () -> Unit,
) {
    Column(Modifier.width(300.dp)) {
        Box {
            AsyncImage(
                model = detail.image,
                contentDescription = detail.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = detail.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .width(140.dp)
                            .clickable(onClick = onFavourite),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = if (isFavourite) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isFavourite) "Favourited" else "Favourite",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One row of the album's track list. */
@Composable
private fun TrackRow(
    track: MusicTrack,
    isFavourite: Boolean,
    onFavourite: () -> Unit,
    onPlay: () -> Unit,
    isResolving: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        track.duration?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        IconButton(onClick = onFavourite) {
            Icon(
                imageVector = if (isFavourite) {
                    Icons.Default.Favorite
                } else {
                    Icons.Default.FavoriteBorder
                },
                contentDescription = "Favourite",
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onPlay, enabled = !isResolving) {
            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** A stable key for a browse item. */
private fun itemKey(item: MusicItem): String = when (item) {
    is MusicAlbum -> "album:${item.url}"
    is MusicArtist -> "artist:${item.url}"
    is MusicTrack -> "track:${item.id}"
}
