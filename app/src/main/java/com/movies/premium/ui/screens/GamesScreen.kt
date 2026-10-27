package com.movies.premium.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.movies.premium.data.remote.model.Game
import com.movies.premium.data.remote.model.GamePlatform
import com.movies.premium.ui.screens.games.GameDetailSheet
import com.movies.premium.ui.tv.focusRing
import com.movies.premium.ui.viewmodel.GamesViewModel

/** The width the original gives a single cell of the games grid. */
private val GRID_CELL_SIZE = 100.dp

/**
 * The red the original paints the favourites with (`#EF5350`, the tint of the
 * original's favourites heart).
 */
private val FavouriteRed = Color(0xFFEF5350)

/**
 * One game in the grid.
 *
 * The catalogue and the favourites are two different things — the favourites
 * keep the platform they were added from — so both are flattened into this
 * before they reach the grid.
 */
private data class GameTile(
    val slug: String,
    val name: String,
    val posterUrl: String?,
    val year: String?,
    val platform: GamePlatform,
)

/**
 * The retro-games catalogue.
 *
 * The original's screen is `com.app.mlounge.ui.screens.games.a`:
 *
 *  - a heading and a line that points at the emulator settings,
 *  - a filter bar with a filter icon, one chip per platform, a genre dropdown,
 *    a decade dropdown and the favourites switch,
 *  - a grid of the games as posters with the name and the year on a gradient,
 *  - and — when the favourites switch is on — the favourites instead of the
 *    catalogue.
 *
 * Tapping a game opens the sheet with the details of the game on top of the
 * grid; the sheet holds the original's `game_detail` content.
 */
@Composable
fun GamesScreen(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // The next page is loaded when the grid is scrolled close to its end, the
    // way the original's lists load their next page.
    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(lastVisible, state.games.size, state.favouritesMode) {
        if (!state.favouritesMode && state.games.isNotEmpty() && lastVisible >= state.games.size - 6) {
            viewModel.loadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Games",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        Text(
            text = "Retro Game Library - To change emulator settings go to Settings > Emulator Settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        GamesFilterBar(
            platform = state.platform,
            genres = state.genres,
            decades = state.decades,
            selectedGenre = state.selectedGenre,
            selectedDecade = state.selectedDecade,
            favouritesMode = state.favouritesMode,
            onPlatformSelected = viewModel::selectPlatform,
            onGenreSelected = viewModel::selectGenre,
            onDecadeSelected = viewModel::selectDecade,
            onFavouritesToggled = viewModel::toggleFavouritesMode,
        )

        when {
            // The favourites are the catalogue of the user, not of the backend.
            state.favouritesMode -> {
                if (state.favourites.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No favourites yet. Add games from their detail page!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else {
                    GamesGrid(
                        gridState = gridState,
                        tiles = state.favourites.map { favourite ->
                            GameTile(
                                slug = favourite.slug,
                                name = favourite.name.ifBlank { "Unknown" },
                                posterUrl = favourite.posterUrl,
                                year = favourite.year,
                                platform = GamePlatform.fromApiPath(favourite.platform),
                            )
                        },
                        onGameClick = { slug, platform -> viewModel.openGame(slug, platform) },
                    )
                }
            }

            state.loading && state.games.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            // A failed load must not leave an empty screen behind — the original
            // says what went wrong and lets the user try again.
            state.error != null && state.games.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadGames() }) { Text("Retry") }
                }
            }

            else -> {
                GamesGrid(
                    gridState = gridState,
                    tiles = state.games.map { game -> game.toTile(state.platform) },
                    loadingMore = state.loadingMore,
                    onGameClick = { slug, platform -> viewModel.openGame(slug, platform) },
                )
            }
        }
    }

    // The detail of the game opens as a sheet on top of the grid.
    val detailSlug = state.detailSlug
    if (detailSlug != null) {
        GameDetailSheet(
            detail = state.detail,
            platform = state.detailPlatform,
            loading = state.detailLoading,
            error = state.detailError,
            downloading = state.downloading,
            downloadedBytes = state.downloadedBytes,
            totalBytes = state.totalBytes,
            romReady = state.romReady,
            isFavourite = viewModel.isFavourite(detailSlug, state.detailPlatform),
            romPath = { viewModel.romPath() },
            onDismiss = { viewModel.closeGame() },
            onDownload = { viewModel.downloadRom() },
            onToggleFavourite = {
                viewModel.toggleFavourite(
                    slug = detailSlug,
                    platform = state.detailPlatform,
                    name = state.detail?.name.orEmpty(),
                    posterUrl = state.detail?.posterUrl,
                    year = state.detail?.year,
                )
            },
        )
    }
}

/**
 * The filter bar of the original: the filter icon, one chip per platform, the
 * genre and the decade dropdowns and the favourites switch.
 *
 * The original lays the bar out in a single row that does not scroll. It does
 * not fit on a phone in portrait, so the row scrolls here — the chips keep
 * their shape and their order.
 */
@Composable
private fun GamesFilterBar(
    platform: GamePlatform,
    genres: List<String>,
    decades: List<String>,
    selectedGenre: String?,
    selectedDecade: String?,
    favouritesMode: Boolean,
    onPlatformSelected: (GamePlatform) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onDecadeSelected: (String?) -> Unit,
    onFavouritesToggled: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Filter,
            contentDescription = "Filters",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        GamePlatform.entries.forEach { entry ->
            GameChip(
                selected = entry == platform,
                onClick = { onPlatformSelected(entry) },
            ) { color ->
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                )
            }
        }

        // The genre: the label is the picked genre, the menu offers "All Genres"
        // and every genre of the platform.
        FilterDropdown(
            label = selectedGenre?.let { capitalizeFirst(it) } ?: "Genre",
            items = listOf<Pair<String?, String>>(null to "All Genres") +
                genres.map { it to capitalizeFirst(it) },
            selected = selectedGenre != null,
            onSelect = onGenreSelected,
        )

        // The decade: the menu offers "All Decades" and every decade.
        FilterDropdown(
            label = selectedDecade ?: "Decade",
            items = listOf<Pair<String?, String>>(null to "All Decades") +
                decades.map { it to it },
            selected = selectedDecade != null,
            onSelect = onDecadeSelected,
        )

        // The favourites switch — the last control of the original's bar.
        Surface(
            onClick = onFavouritesToggled,
            shape = RoundedCornerShape(8.dp),
            color = if (favouritesMode) {
                FavouriteRed.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            border = BorderStroke(2.dp, Color.Transparent),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = if (favouritesMode) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = null,
                    tint = if (favouritesMode) {
                        FavouriteRed
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Favourites",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (favouritesMode) {
                        FavouriteRed
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * One of the chips of the original's filter bar.
 *
 * The original paints the chip with the accent colour at 30 % when it is the
 * selected one and with a dark grey otherwise, and gives it a two dp border.
 */
@Composable
private fun GameChip(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            accent.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(2.dp, Color.Transparent),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            content(
                if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The dropdowns of the original's filter bar: the genre and the decade.
 *
 * The label shows what is picked ("Genre" or "Decade" when nothing is), the
 * menu offers "All Genres" / "All Decades" and every entry of the list.
 */
@Composable
private fun FilterDropdown(
    label: String,
    items: List<Pair<String?, String>>,
    selected: Boolean,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        GameChip(selected = selected, onClick = { expanded = true }) { color ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // The menu is a window of its own, so the ring of the window of
            // the app cannot reach it: the menu draws the ring around
            // whatever inside it has the focus.
            modifier = Modifier.focusRing(),
        ) {
            items.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/** The grid of the games — the original's `LazyVerticalGrid` of poster cards. */
@Composable
private fun GamesGrid(
    gridState: LazyGridState,
    tiles: List<GameTile>,
    onGameClick: (String, GamePlatform) -> Unit,
    loadingMore: Boolean = false,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_CELL_SIZE),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            tiles,
            // The index is part of the key, so that a page which repeats a slug
            // can never crash the grid.
            key = { index, tile -> "game-$index-${tile.slug}" },
        ) { _, tile ->
            GameCard(
                name = tile.name,
                posterUrl = tile.posterUrl,
                year = tile.year,
                onClick = { onGameClick(tile.slug, tile.platform) },
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * A game in the grid: the poster with the name and the year on a gradient
 * below it.
 *
 * This is the original's `ui.components.e0.f`: a surface with a two dp border,
 * the poster in the 2 : 3 ratio, a vertical gradient that darkens the lower
 * half and the centred name and year on top of it.
 */
@Composable
private fun GameCard(
    name: String,
    posterUrl: String?,
    year: String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            AsyncImage(
                model = posterUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.6667f),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            0.65f to Color.Black.copy(alpha = 0.75f),
                            1f to Color.Black.copy(alpha = 0.97f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!year.isNullOrBlank()) {
                    Text(
                        text = year,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The first letter of [value] in upper case — the original's genre labels. */
private fun capitalizeFirst(value: String): String =
    if (value.isEmpty()) value else value.replaceFirstChar { it.uppercaseChar() }

/** A game of the catalogue as a tile of the grid. */
private fun Game.toTile(platform: GamePlatform): GameTile = GameTile(
    slug = slug.orEmpty(),
    name = name?.takeIf { it.isNotBlank() } ?: "Unknown",
    posterUrl = posterUrl,
    year = year?.take(4),
    platform = platform,
)
