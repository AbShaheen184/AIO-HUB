package com.movies.premium.ui.screens.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.movies.premium.data.remote.model.HiAnimeEpisode
import com.movies.premium.data.remote.model.HiAnimeItem
import com.movies.premium.ui.viewmodel.AnimeServerChoice
import com.movies.premium.ui.viewmodel.AnimeViewModel
import kotlinx.coroutines.launch

/**
 * The type chips above the anime grid.
 *
 * The values are the ones the Anikoto API expects, the labels are the ones
 * the original shows.
 */
private val TYPE_CHIPS = listOf(
    "tv" to "TV",
    "movie" to "Movies",
    "ova" to "OVA",
    "ona" to "ONA",
    "special" to "Special",
)

/** The width the original gives a single grid cell. */
private val GRID_CELL_SIZE = 85.dp

/**
 * The title of a card, never blank.
 *
 * The API is inconsistent about which field carries the title and sometimes
 * sends an empty string, so a card always ends up with something readable.
 */
private fun HiAnimeItem.cardTitle(): String = displayName().takeIf { it.isNotBlank() } ?: "Unknown"

/**
 * Anime catalogue.
 *
 * Shows the type chips, the genre filter and a paged grid of titles, exactly
 * like the original's `anime` route: a spinner while the first page is on its
 * way, an error block with a retry button when the list is empty and failed,
 * and a progress bar while a page is being appended.
 *
 * Every card carries a play button next to its poster. It starts the first
 * episode of the title right away — the servers of the episode are asked for
 * and the first one is used — so the user does not have to open the detail
 * page first. The card itself still opens the detail page when it is tapped
 * anywhere else.
 *
 * [onPlay] gets the same values the detail screen hands over: the media type,
 * the id of the episode, the title, the number of the episode, the anime
 * server and the category (`"sub"` or `"dub"`) it belongs to.
 */
@Composable
fun AnimeScreen(
    onAnimeClick: (String) -> Unit,
    onPlay: (
        mediaType: String,
        mediaId: String,
        title: String,
        episode: Int,
        server: String,
        category: String,
    ) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: AnimeViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val watchProgress by viewModel.watchProgress.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    /**
     * Starts the first episode of a card.
     *
     * The episode id of the Anikoto API is `"<slug>|<number>"`, so the first
     * episode of a title is `"<slug>|1"`. The servers of the episode are
     * asked for and the first one — the sub ones come first — is handed to
     * the source resolver. When the server list is empty the route is built
     * without a server, and the source resolver falls back to any source of
     * the episode.
     */
    fun playFirstEpisode(anime: HiAnimeItem) {
        if (anime.id.isBlank()) return
        val episodeId = "${anime.id}|1"
        val title = anime.cardTitle()
        scope.launch {
            val choice = viewModel.serversFor(episodeId).firstOrNull()
            onPlay(
                "anime",
                episodeId,
                "$title - Episode 1",
                1,
                choice?.name.orEmpty(),
                choice?.category.orEmpty(),
            )
        }
    }

    // Load the next page once the grid is scrolled close to its end. The
    // original starts the request when the last visible cell is within six
    // items of the end of the list.
    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(lastVisible, items.size) {
        if (items.isNotEmpty() && lastVisible >= items.size - 6) {
            viewModel.loadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Anime",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(TYPE_CHIPS) { (value, label) ->
                FilterPill(
                    label = label,
                    selected = selectedType == value,
                    onClick = { viewModel.setType(value) },
                )
            }
        }

        Column(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 8.dp)) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                genres.forEach { genre ->
                    FilterPill(
                        label = genre,
                        selected = selectedGenre == genre,
                        onClick = { viewModel.setGenre(genre) },
                    )
                }
            }
        }

        when {
            loading && items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            error == null || items.isNotEmpty() -> {
                // The original de-duplicates the cards before drawing them.
                val cards = remember(items) {
                    val seen = HashSet<String>()
                    items.filter { seen.add(it.id) }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(GRID_CELL_SIZE),
                    state = gridState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(cards, key = { it.id }) { anime ->
                        AnimeCard(
                            anime = anime,
                            progress = watchProgress["anime:${anime.id}"] ?: 0f,
                            onClick = { onAnimeClick(anime.id) },
                            onPlay = { playFirstEpisode(anime) },
                        )
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
                // The original keeps a second, full width progress bar under
                // the grid while a page is being appended.
                if (loading && items.isNotEmpty()) {
                    LinearProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                ErrorState(
                    message = error ?: "",
                    onRetry = { viewModel.loadAnime() },
                )
            }
        }
    }
}

/**
 * Anime detail page.
 *
 * Shows the poster header, the information block, the watch / favourite row,
 * the server list, the synopsis, the studio block, the episode list and the
 * related titles.
 *
 * [onPlay] receives the media type, the id of the episode, the title, the
 * number of the episode, the anime server the source is resolved from and
 * the category (`"sub"` or `"dub"`) the server belongs to. The original hands
 * only the first four over — it picks the server in its own source screen —
 * so the last two are the replica's addition, and the source screen of the
 * replica reads them off the route.
 */
@Composable
fun AnimeDetailsScreen(
    animeId: String,
    onBack: () -> Unit,
    onRelatedClick: (String) -> Unit,
    onPlay: (mediaType: String, mediaId: String, title: String, episode: Int, server: String, category: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimeViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val detailLoading by viewModel.detailLoading.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val related by viewModel.related.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val watchProgress by viewModel.watchProgress.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val serversLoading by viewModel.serversLoading.collectAsStateWithLifecycle()
    val error by viewModel.detailError.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    // The episode whose servers are on screen. The first episode is picked as
    // soon as the episode list arrives.
    var selectedEpisode by remember { mutableStateOf<HiAnimeEpisode?>(null) }

    LaunchedEffect(animeId) { viewModel.loadDetail(animeId) }
    DisposableEffect(animeId) { onDispose { viewModel.clearDetail() } }

    // The lazy lists need unique keys, and the API is free to repeat an entry
    // — the same episode twice, or two related titles with the same id.
    val episodeRows = remember(episodes) { episodes.distinctBy { it.episodeId } }
    val relatedItems = remember(related) { related.distinctBy { it.id } }

    // Load the servers of the first episode, so the server list of the screen
    // is filled from the start.
    LaunchedEffect(episodeRows) {
        val first = episodeRows.firstOrNull()
        if (first != null && selectedEpisode == null) {
            selectedEpisode = first
            viewModel.serversFor(first.episodeId)
        }
    }

    val data = detail
    if (data == null) {
        // A failed load must not leave the screen on a spinner forever.
        if (error != null && !detailLoading) {
            ErrorState(
                message = error ?: "Couldn't load this title",
                onRetry = { viewModel.loadDetail(animeId) },
            )
        } else {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    val info = data.anime.info
    val moreInfo = data.anime.moreInfo
    val isFavourite = animeId in favourites
    val animeTitle = info.name?.takeIf { it.isNotBlank() } ?: "Unknown title"

    /** Hands the episode and the picked server to the source route. */
    fun play(episode: HiAnimeEpisode, choice: AnimeServerChoice?) {
        val episodeTitle = episode.title?.takeIf { it.isNotBlank() }
            ?: "Episode ${episode.number}"
        onPlay(
            "anime",
            episode.episodeId,
            "$animeTitle - $episodeTitle",
            episode.number,
            choice?.name.orEmpty(),
            choice?.category.orEmpty(),
        )
    }

    /** Starts an episode: the servers are asked for and the first one is used. */
    fun startEpisode(episode: HiAnimeEpisode) {
        selectedEpisode = episode
        viewModel.setCurrentEpisode(episode.number)
        scope.launch {
            val choice = viewModel.serversFor(episode.episodeId).firstOrNull()
            play(episode, choice)
        }
    }

    /** Picks an episode and shows its servers, without starting anything. */
    fun selectEpisode(episode: HiAnimeEpisode) {
        selectedEpisode = episode
        scope.launch { viewModel.serversFor(episode.episodeId) }
    }

    // The hero is shorter in portrait: the phone is only 384 dp wide there
    // and a 320 dp hero would push the "Watch" button off the screen.
    val heroHeight = if (LocalConfiguration.current.screenWidthDp < 560) 200.dp else 320.dp

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Box {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                AsyncImage(
                    model = info.poster,
                    contentDescription = animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )
                // The original's detail screen carries a back arrow in its
                // header; the replica draws it over the poster.
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                    ) {
                        AnimeBackButton(onBack)
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    AsyncImage(
                        model = info.poster,
                        contentDescription = animeTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 120.dp, height = 180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = animeTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            info.stats?.rating?.takeIf { it.isNotBlank() }?.let {
                                InfoPill(it, MaterialTheme.colorScheme.primary)
                            }
                            info.stats?.quality?.takeIf { it.isNotBlank() }?.let {
                                InfoPill(it, MaterialTheme.colorScheme.secondary)
                            }
                            moreInfo?.malscore?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = "★ $it",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val type = info.stats?.type?.takeIf { it.isNotBlank() }
                        val duration = info.stats?.duration?.takeIf { it.isNotBlank() }
                        // The number of the episodes of the list is the one
                        // that is known; the counter of the API is the
                        // fallback. The dub flag of the API is a flag, not a
                        // count, so it is only shown as a word.
                        val episodeCount = episodeRows.size.takeIf { it > 0 }
                            ?: info.stats?.episodes?.sub?.takeIf { it > 0 }
                        val episodeText = listOfNotNull(
                            episodeCount?.let { "$it episodes" },
                            info.stats?.episodes?.dub?.let { "Dub" },
                        ).joinToString(" • ")
                        Text(
                            text = listOfNotNull(
                                type,
                                duration,
                                episodeText.takeIf { it.isNotEmpty() },
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        moreInfo?.genres?.let { genres ->
                            if (genres.isNotEmpty()) {
                                Text(
                                    text = genres.joinToString(", "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // The original draws the watch button only when the
                    // episode list is not empty.
                    val watchEpisode = selectedEpisode ?: episodeRows.firstOrNull()
                    if (watchEpisode != null) {
                        Button(
                            onClick = { startEpisode(watchEpisode) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Watch")
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.toggleFavourite(animeId) },
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
                }

                // The servers of the selected episode. The original resolves
                // the server inside its source screen; the replica shows them
                // here, so the user can see which ones are on offer and pick
                // one of them.
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Servers",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                val serverEpisode = selectedEpisode
                when {
                    serverEpisode == null -> {
                        Text(
                            text = "No episode to play yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    serversLoading -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    servers.isEmpty() -> {
                        Text(
                            text = "No servers available for this episode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            servers.forEach { choice ->
                                ServerRow(choice = choice) { play(serverEpisode, choice) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Synopsis",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                val synopsis = info.description?.takeIf { it.isNotBlank() }
                Text(
                    text = synopsis ?: "No synopsis available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (moreInfo != null) {
                    Spacer(Modifier.height(12.dp))
                    moreInfo.studios?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "Studio: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    moreInfo.status?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "Status: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    moreInfo.aired?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "Aired: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (episodeRows.isNotEmpty()) {
            item {
                Text(
                    text = "Episodes (${episodeRows.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            itemsIndexed(
                items = episodeRows,
                key = { index, episode -> "${episode.episodeId}#$index" },
            ) { _, episode ->
                EpisodeRow(
                    episode = episode,
                    progress = watchProgress[episode.episodeId] ?: 0f,
                    selected = selectedEpisode?.episodeId == episode.episodeId,
                    onSelect = { selectEpisode(episode) },
                    onPlay = { startEpisode(episode) },
                )
            }
        }

        if (relatedItems.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Related Anime",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = relatedItems,
                            key = { index, anime -> "${anime.id}#$index" },
                        ) { _, anime ->
                            AnimeCard(
                                anime = anime,
                                progress = 0f,
                                onClick = { onRelatedClick(anime.id) },
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

/**
 * One row of the server list.
 *
 * The name of the server is what the source resolver looks the entry up by,
 * and the category tells the sub servers from the dub ones.
 */
@Composable
private fun ServerRow(
    choice: AnimeServerChoice,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = choice.name.takeIf { it.isNotBlank() } ?: "Unknown server",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (choice.category.isNotBlank()) {
            InfoPill(choice.category.uppercase(), MaterialTheme.colorScheme.secondary)
        }
    }
}

/**
 * The round play button.
 *
 * It is drawn on top of a poster, so it is a plain circle with an icon in it
 * instead of a material button — a material button would enforce a touch
 * target of 48dp, which is too big for a card of 85dp.
 */
@Composable
private fun PlayBadge(
    onClick: () -> Unit,
    size: Dp = 32.dp,
    contentDescription: String = "Play",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

/** One card of the anime grid. */
@Composable
private fun AnimeCard(
    anime: HiAnimeItem,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = anime.poster,
                contentDescription = anime.cardTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
            if (onPlay != null) {
                PlayBadge(
                    onClick = onPlay,
                    contentDescription = "Play ${anime.cardTitle()}",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = anime.cardTitle(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = listOfNotNull(
            anime.type?.takeIf { it.isNotBlank() },
            anime.episodes?.sub?.takeIf { it > 0 }?.let { "$it eps" },
        ).joinToString(" • ")
        if (subtitle.isNotEmpty()) {
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

/** One row of the episode list. */
@Composable
private fun EpisodeRow(
    episode: HiAnimeEpisode,
    progress: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onSelect),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = episode.number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(32.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = episode.title?.takeIf { it.isNotBlank() }
                        ?: "Episode ${episode.number}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.isFiller == true) {
                    Text(
                        text = "Filler",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            PlayBadge(
                onClick = onPlay,
                size = 36.dp,
                contentDescription = "Play episode ${episode.number}",
            )
        }
        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }
    }
}

/** A small rounded chip, used for the type and genre filters. */
@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** A small rounded pill used for the rating and the quality. */
@Composable
private fun InfoPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** The error block with a retry button. */
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/** The detail page's back arrow; kept here so the header stays consistent. */
@Composable
internal fun AnimeBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
    }
}
