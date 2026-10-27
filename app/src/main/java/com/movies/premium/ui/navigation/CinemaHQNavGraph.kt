package com.movies.premium.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.movies.premium.data.download.DownloadManager
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.data.music.MusicStreamResolver
import com.movies.premium.ui.screens.BrowseScreen
import com.movies.premium.ui.screens.DetailScreen
import com.movies.premium.ui.screens.GamesScreen
import com.movies.premium.ui.screens.HomeScreen
import com.movies.premium.ui.screens.PersonScreen
import com.movies.premium.ui.screens.SearchScreen
import com.movies.premium.ui.screens.SettingsScreen
import com.movies.premium.ui.screens.SourcesScreen
import com.movies.premium.ui.screens.adult.AdultDetailScreen
import com.movies.premium.ui.screens.adult.AdultScreen
import com.movies.premium.ui.screens.anime.AnimeDetailsScreen
import com.movies.premium.ui.screens.anime.AnimeScreen
import com.movies.premium.ui.screens.downloads.DownloadsScreen
import com.movies.premium.ui.screens.games.GameDetailScreen
import com.movies.premium.ui.screens.livetv.LiveTvScreen
import com.movies.premium.ui.screens.history.HistoryScreen
import com.movies.premium.ui.screens.music.MusicAlbumScreen
import com.movies.premium.ui.screens.music.MusicArtistScreen
import com.movies.premium.ui.screens.music.MusicScreen
import com.movies.premium.ui.screens.player.PlayerScreen
import com.movies.premium.ui.screens.services.ServicesScreen
import com.movies.premium.ui.screens.settings.SidebarDestination
import com.movies.premium.ui.screens.settings.SidebarDestinations
import com.movies.premium.ui.screens.watchlist.WatchlistScreen
import com.movies.premium.ui.tv.LocalIsTv
import com.movies.premium.ui.viewmodel.MusicViewModel
import com.movies.premium.ui.viewmodel.SourcesViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The original passes a source's request headers through the routes as newline-separated
 * `key=value` pairs — the same shape the download manager stores them in.
 */
private fun Map<String, String>?.toHeaderString(): String =
    this?.entries?.joinToString("\n") { "${it.key}=${it.value}" }.orEmpty()

/**
 * The app's navigation graph.
 *
 * Every route string is the original's, verbatim — see [Routes] and §3.4 of
 * `G:\workdir\CINEMAHQ-HANDOFF.md`. The `navArgument` types and defaults also match the
 * original, which was recovered from `com.app.mlounge.data.remote.ntv.f` (the original's
 * `NavType` factory) and the registrations in `com.app.mlounge.ui.z`.
 *
 * The original's argument table:
 *
 * | route                       | argument      | type    | default   |
 * |-----------------------------|---------------|---------|-----------|
 * | `movie_detail/{movieId}`    | movieId       | Int     | —         |
 * | `tvshow_detail/{tvId}`      | tvId          | Int     | —         |
 * | `anime_detail/{animeId}`    | animeId       | String  | —         |
 * | `adult_detail/{videoId}`    | videoId       | String  | —         |
 * | `music_album_detail/{albumUrl}`   | albumUrl | String  | —         |
 * | `music_artist_detail/{artistUrl}` | artistUrl | String | —        |
 * | `game_detail/{platform}/{gameSlug}` | platform | String | —        |
 * |                             | gameSlug      | String  | —         |
 * | `person/{personId}`         | personId      | Int     | —         |
 * | `sources?…`                 | type          | String  | —         |
 * |                             | id            | String  | —         |
 * |                             | title         | String  | "Sources" |
 * |                             | season        | Int     | 0         |
 * |                             | episode       | Int     | 0         |
 * |                             | mode          | String  | "play"    |
 * | `player?…`                  | url           | String  | —         |
 * |                             | title         | String  | ""        |
 * |                             | headers       | String  | ""        |
 * |                             | contentType   | String  | "tv"      |
 * |                             | streamType    | String  | ""        |
 * |                             | tmdbId        | Int     | 0         |
 * |                             | season        | Int     | 0         |
 * |                             | episode       | Int     | 0         |
 * |                             | contentKey    | String  | ""        |
 * |                             | fallbackUrl   | String  | ""        |
 *
 * **The sidebar.** The destinations live in a `NavigationRail` that the user
 * can fold away: the hamburger in its header toggles it, and the collapsed
 * rail is a narrow strip that carries nothing but that button, so the screen
 * gets the room back. The state of the rail is saved, so it survives a
 * rotation. The player route hides the sidebar completely, because the
 * player is fullscreen. See [CollapsibleSidebar].
 */

/**
 * Lets the navigation graph read the preferences store.
 *
 * The original's sidebar order (`nav_order`) and the hidden sections
 * (`hidden_menus`) are user settings, so the rail has to read them — otherwise the
 * "Navigation" section of the settings screen would have no effect.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesEntryPoint {
    fun appPreferences(): AppPreferences
}

/**
 * The rail's destinations, in the order the user chose and without the sections the
 * user hid.
 *
 * Mirrors the original's `com.app.mlounge.ui.navigation.w.f` list of thirteen
 * destinations and the way the original's `q1` (move) and `e2` (show/hide) view models
 * write the order and the hidden set back to the preferences store. Destinations that
 * the stored order does not mention are appended, so a section added by a later version
 * never disappears.
 */
private fun sidebarDestinations(
    storedOrder: List<String>,
    hiddenTitles: Set<String>,
): List<SidebarDestination> {
    val all = SidebarDestinations
    val routes = all.map { it.route }
    val order = if (storedOrder.isEmpty()) {
        routes
    } else {
        storedOrder + routes.filter { it !in storedOrder }
    }
    return order
        .mapNotNull { route -> all.firstOrNull { it.route == route } }
        .filter { it.title !in hiddenTitles }
}

/** The width of the expanded rail — the width Material3 gives a navigation rail. */
private val SIDEBAR_RAIL_WIDTH = 80.dp

/**
 * The width of the collapsed strip.
 *
 * It carries nothing but the hamburger button, so it is as narrow as the
 * touch target of a button.
 */
private val SIDEBAR_COLLAPSED_WIDTH = 56.dp

/**
 * The collapsible sidebar.
 *
 * **Deviation from the original.** The original has no drawer and no
 * collapsible sidebar: its list of destinations is always on the left, and
 * the user asked for a sidebar that can be hidden so that the screens get
 * the room they need. This is the replica's own design, not the original's.
 *
 * **Why a `NavigationRail` and not a `ModalNavigationDrawer`.**
 *
 * * The screens of this app draw their own header — the title of the section
 *   is the first thing on the screen — and a modal drawer is drawn *over*
 *   the content: it would cover that header and it would need a scrim, which
 *   is not what "give the screen its room back" means.
 * * The rail is a sibling of the content in the row of [CinemaHQApp], so
 *   expanding it simply widens the rail and narrows the content — the layout
 *   of the original, with the destinations in the same place.
 * * The original's own widget for the destinations is a rail, so the
 *   `NavigationRail` keeps the look of the app, and the only thing that is
 *   new is the hamburger that collapses it.
 *
 * The hamburger sits in the `header` slot of the rail while the rail is
 * expanded and in a narrow strip of its own while it is collapsed, so the
 * button is reachable on every screen that is not the fullscreen player.
 */
@Composable
private fun CollapsibleSidebar(
    destinations: List<SidebarDestination>,
    isSelected: (SidebarDestination) -> Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDestinationClick: (SidebarDestination) -> Unit,
    modifier: Modifier = Modifier,
    /** Asked for the focus when the rail is opened — see [CinemaHQApp]. */
    firstItemRequester: FocusRequester = remember { FocusRequester() },
    /** Asked for the focus when the rail is closed, and at the start. */
    menuRequester: FocusRequester = remember { FocusRequester() },
) {
    if (expanded) {
        NavigationRail(
            modifier = modifier.width(SIDEBAR_RAIL_WIDTH),
            header = {
                NavigationRailItem(
                    selected = false,
                    onClick = onToggle,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Hide the menu",
                        )
                    },
                    label = { Text("Menu") },
                    modifier = Modifier.focusRequester(menuRequester),
                )
            },
        ) {
            // The rail can hold more entries than fit on a short screen, so it
            // scrolls — the original's sidebar does the same.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                destinations.forEachIndexed { index, destination ->
                    NavigationRailItem(
                        selected = isSelected(destination),
                        onClick = { onDestinationClick(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.title) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstItemRequester)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    } else {
        // Collapsed: a narrow strip that carries nothing but the hamburger.
        //
        // The strip stands next to the `Scaffold` and not inside it, so it
        // gets no inset from the scaffold and the button would sit *under*
        // the status bar — half of it unreachable, because the status bar
        // window swallows the touches. The expanded rail gets the inset
        // from the `NavigationRail` itself; the collapsed one has to ask
        // for it.
        Column(
            modifier = modifier
                .width(SIDEBAR_COLLAPSED_WIDTH)
                .fillMaxHeight()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = onToggle,
                // A television has no finger: the strip is reached with the
                // left button of the remote, and the same button opens the
                // rail — the strip is the leftmost thing on the screen, so
                // there is nothing to the left of it.
                modifier = Modifier
                    .focusRequester(menuRequester)
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (native.action == android.view.KeyEvent.ACTION_DOWN &&
                            native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                        ) {
                            onToggle()
                            true
                        } else {
                            false
                        }
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Show the menu",
                )
            }
        }
    }
}

@Composable
fun CinemaHQApp(
    downloadManager: DownloadManager,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The player is fullscreen, so the rail is hidden there.
    val showRail = currentRoute != Routes.PLAYER_ROUTE

    // Whether the sidebar is expanded. It starts collapsed, so the screens
    // get the room they need, and the flag is saved — the sidebar stays the
    // way the user left it when the device is turned.
    var sidebarExpanded by rememberSaveable { mutableStateOf(false) }

    // The focus of the first entry of the rail. When the rail is opened, the
    // button that opened it disappears — it is a different button in the
    // collapsed strip and in the header of the rail — and the focus would be
    // lost with it. On a television that would leave the user without a
    // cursor, so the focus is handed to the first entry of the rail.
    val firstRailItem = remember { FocusRequester() }
    var moveFocusToRail by remember { mutableStateOf(false) }

    // The focus of the hamburger. It is the one element of the interface that
    // is on the screen on every page but the player, so it is the natural
    // place for the focus to start on a television — and the place to go
    // back to when the rail is closed again.
    val menuButton = remember { FocusRequester() }
    var moveFocusToMenu by remember { mutableStateOf(false) }

    // On a television nothing is touched, so nothing has the focus when the
    // app starts. The user would have to guess where the cursor is, so the
    // focus is put on the hamburger — the ring around it shows where it is,
    // and the right button of the remote moves into the content.
    val isTv = LocalIsTv.current
    LaunchedEffect(isTv) {
        if (isTv) {
            delay(400)
            runCatching { menuButton.requestFocus() }
        }
    }

    LaunchedEffect(moveFocusToRail, sidebarExpanded) {
        if (moveFocusToRail && sidebarExpanded) {
            // Give the rail a moment to be laid out before the focus is
            // asked for.
            delay(60)
            runCatching { firstRailItem.requestFocus() }
            moveFocusToRail = false
        }
    }

    LaunchedEffect(moveFocusToMenu, sidebarExpanded) {
        if (moveFocusToMenu && !sidebarExpanded) {
            delay(60)
            runCatching { menuButton.requestFocus() }
            moveFocusToMenu = false
        }
    }

    val context = LocalContext.current
    val preferences = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, PreferencesEntryPoint::class.java)
            .appPreferences()
    }
    val storedOrder by preferences.navOrder.collectAsStateWithLifecycle(emptyList())
    val hiddenTitles by preferences.hiddenMenus.collectAsStateWithLifecycle(emptySet())
    val destinations = remember(storedOrder, hiddenTitles) {
        sidebarDestinations(storedOrder, hiddenTitles)
    }

    Row(Modifier.fillMaxSize()) {
        if (showRail) {
            CollapsibleSidebar(
                destinations = destinations,
                isSelected = { destination ->
                    backStackEntry?.destination?.hierarchy?.any {
                        it.route == destination.route
                    } == true
                },
                expanded = sidebarExpanded,
                onToggle = {
                    val willExpand = !sidebarExpanded
                    sidebarExpanded = willExpand
                    if (willExpand) moveFocusToRail = true else moveFocusToMenu = true
                },
                firstItemRequester = firstRailItem,
                menuRequester = menuButton,
                onDestinationClick = { destination ->
                    // A section is entered as itself. The detail screen that
                    // was open on it must not come back with it: the
                    // navigation saves the whole stack of a section when the
                    // user leaves it — the detail included — and restores
                    // that stack when the user comes back. So every detail
                    // is popped first, and what is saved is the section
                    // alone.
                    Routes.DETAIL_ROUTES.forEach { route ->
                        navController.popBackStack(route, inclusive = true)
                    }
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }

        Scaffold(modifier = Modifier.weight(1f)) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding),
            ) {
                // ---- top-level destinations ------------------------------------

                composable(Routes.HOME) {
                    HomeScreen(
                        onMovieClick = { navController.navigate(Routes.movieDetail(it)) },
                        onTvClick = { navController.navigate(Routes.tvDetail(it)) },
                    )
                }

                composable(Routes.MOVIES) {
                    BrowseScreen(
                        isTv = false,
                        onItemClick = { navController.navigate(Routes.movieDetail(it)) },
                    )
                }

                composable(Routes.TV_SHOWS) {
                    BrowseScreen(
                        isTv = true,
                        onItemClick = { navController.navigate(Routes.tvDetail(it)) },
                    )
                }

                composable(Routes.SEARCH) {
                    SearchScreen(
                        onMovieClick = { navController.navigate(Routes.movieDetail(it)) },
                        onTvClick = { navController.navigate(Routes.tvDetail(it)) },
                    )
                }

                composable(Routes.ANIME) {
                    AnimeScreen(
                        onAnimeClick = { animeId ->
                            navController.navigate(Routes.animeDetail(animeId))
                        },
                        // The play button of a card starts the first episode of
                        // the title.
                        onPlay = { mediaType, mediaId, title, episode, server, category ->
                            navController.navigate(
                                Routes.sources(
                                    type = mediaType,
                                    id = mediaId,
                                    title = title,
                                    episode = episode,
                                    mode = Routes.MODE_PLAY,
                                    server = server,
                                    category = category,
                                ),
                            )
                        },
                    )
                }

                composable(Routes.MUSIC) {
                    MusicScreen(
                        onAlbumClick = { albumUrl ->
                            navController.navigate(Routes.musicAlbumDetail(albumUrl))
                        },
                        onArtistClick = { artistUrl ->
                            navController.navigate(Routes.musicArtistDetail(artistUrl))
                        },
                    )
                }

                composable(Routes.GAMES) {
                    // The detail of a game is a sheet on top of the grid, not
                    // a destination of its own.
                    GamesScreen()
                }

                composable(Routes.LIVE_TV) {
                    LiveTvScreen(
                        onPlay = { url, name, headers, contentType, extra ->
                            navController.navigate(
                                Routes.player(
                                    url = url,
                                    title = name,
                                    headers = headers.toHeaderString(),
                                    contentType = contentType.orEmpty(),
                                    fallbackUrl = extra.orEmpty(),
                                ),
                            )
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.ADULT) {
                    AdultScreen(
                        onVideoClick = { videoId ->
                            navController.navigate(Routes.adultDetail(videoId))
                        },
                    )
                }

                composable(Routes.SERVICES) { ServicesScreen() }

                composable(Routes.SETTINGS) { SettingsScreen() }

                composable(Routes.WATCHLIST) {
                    WatchlistScreen(
                        onItemClick = { id, type ->
                            navController.navigate(
                                if (type == "tv") Routes.tvDetail(id) else Routes.movieDetail(id),
                            )
                        },
                    )
                }

                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onItemClick = { id, type ->
                            navController.navigate(
                                if (type == "tv") Routes.tvDetail(id) else Routes.movieDetail(id),
                            )
                        },
                    )
                }

                composable(Routes.DOWNLOADS) {
                    DownloadsScreen(
                        onPlay = { url, title, contentType ->
                            navController.navigate(
                                Routes.player(
                                    url = url,
                                    title = title,
                                    contentType = contentType,
                                ),
                            )
                        },
                    )
                }

                // ---- detail destinations ----------------------------------------

                composable(
                    route = Routes.MOVIE_DETAIL,
                    arguments = listOf(navArgument("movieId") { type = NavType.IntType }),
                ) {
                    DetailScreen(
                        onPlay = { request ->
                            navController.navigate(
                                Routes.sources(
                                    type = request.mediaType,
                                    id = request.id.toString(),
                                    title = request.title,
                                    season = request.season,
                                    episode = request.episode,
                                    mode = Routes.MODE_PLAY,
                                ),
                            )
                        },
                        onDownload = { request ->
                            navController.navigate(
                                Routes.sources(
                                    type = request.mediaType,
                                    id = request.id.toString(),
                                    title = request.title,
                                    season = request.season,
                                    episode = request.episode,
                                    mode = Routes.MODE_DOWNLOAD,
                                ),
                            )
                        },
                        onPersonClick = { navController.navigate(Routes.person(it)) },
                        onSimilarMovieClick = { navController.navigate(Routes.movieDetail(it)) },
                        onSimilarTvClick = { navController.navigate(Routes.tvDetail(it)) },
                    )
                }

                composable(
                    route = Routes.TV_DETAIL,
                    arguments = listOf(navArgument("tvId") { type = NavType.IntType }),
                ) {
                    DetailScreen(
                        onPlay = { request ->
                            navController.navigate(
                                Routes.sources(
                                    type = request.mediaType,
                                    id = request.id.toString(),
                                    title = request.title,
                                    season = request.season,
                                    episode = request.episode,
                                    mode = Routes.MODE_PLAY,
                                ),
                            )
                        },
                        onDownload = { request ->
                            navController.navigate(
                                Routes.sources(
                                    type = request.mediaType,
                                    id = request.id.toString(),
                                    title = request.title,
                                    season = request.season,
                                    episode = request.episode,
                                    mode = Routes.MODE_DOWNLOAD,
                                ),
                            )
                        },
                        onPersonClick = { navController.navigate(Routes.person(it)) },
                        onSimilarMovieClick = { navController.navigate(Routes.movieDetail(it)) },
                        onSimilarTvClick = { navController.navigate(Routes.tvDetail(it)) },
                    )
                }

                composable(
                    route = Routes.ANIME_DETAIL,
                    arguments = listOf(navArgument("animeId") { type = NavType.StringType }),
                ) { entry ->
                    AnimeDetailsScreen(
                        animeId = entry.arguments?.getString("animeId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onRelatedClick = { animeId ->
                            navController.navigate(Routes.animeDetail(animeId))
                        },
                        onPlay = { mediaType, mediaId, title, episode, server, category ->
                            navController.navigate(
                                Routes.sources(
                                    type = mediaType,
                                    id = mediaId,
                                    title = title,
                                    episode = episode,
                                    mode = Routes.MODE_PLAY,
                                    server = server,
                                    category = category,
                                ),
                            )
                        },
                    )
                }

                composable(
                    route = Routes.ADULT_DETAIL,
                    arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                ) {
                    AdultDetailScreen(
                        onPlay = { url, title ->
                            navController.navigate(
                                Routes.player(url = url, title = title, contentType = "adult"),
                            )
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Routes.MUSIC_ALBUM_DETAIL,
                    arguments = listOf(navArgument("albumUrl") { type = NavType.StringType }),
                ) { entry ->
                    val viewModel: MusicViewModel = hiltViewModel(entry)
                    val playback by viewModel.playback.collectAsStateWithLifecycle()
                    val resolving by viewModel.resolvingTrackId.collectAsStateWithLifecycle()

                    // The original resolves the track and then opens the player with
                    // the url, the title, the request headers, the content type and
                    // the container type.
                    LaunchedEffect(playback) {
                        val current = playback ?: return@LaunchedEffect
                        viewModel.consumePlayback()
                        navController.navigate(
                            Routes.player(
                                url = current.result.url,
                                title = current.track.title,
                                headers = MusicStreamResolver.PLAYER_HEADERS.toHeaderString(),
                                contentType = "music",
                                streamType = current.result.type,
                                contentKey = current.track.id,
                            ),
                        )
                    }

                    MusicAlbumScreen(
                        albumUrl = entry.arguments?.getString("albumUrl").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onTrackPlay = { viewModel.playTrack(it) },
                        resolvingTrackId = resolving,
                    )
                }

                composable(
                    route = Routes.MUSIC_ARTIST_DETAIL,
                    arguments = listOf(navArgument("artistUrl") { type = NavType.StringType }),
                ) { entry ->
                    MusicArtistScreen(
                        artistUrl = entry.arguments?.getString("artistUrl").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { albumUrl ->
                            navController.navigate(Routes.musicAlbumDetail(albumUrl))
                        },
                    )
                }

                // The game detail is a sheet on top of the games grid; this
                // destination stays for the original's deep link
                // (`game_detail/{platform}/{gameSlug}`), which the games screen
                // itself does not use any more.
                composable(
                    route = Routes.GAME_DETAIL,
                    arguments = listOf(
                        navArgument("platform") { type = NavType.StringType },
                        navArgument("gameSlug") { type = NavType.StringType },
                    ),
                ) {
                    GameDetailScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Routes.PERSON,
                    arguments = listOf(navArgument("personId") { type = NavType.IntType }),
                ) {
                    PersonScreen(
                        onMovieClick = { navController.navigate(Routes.movieDetail(it)) },
                        onTvClick = { navController.navigate(Routes.tvDetail(it)) },
                    )
                }

                // ---- the source picker -------------------------------------------

                composable(
                    route = Routes.SOURCES_ROUTE,
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("id") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType; defaultValue = "Sources" },
                        navArgument("season") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("episode") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("mode") { type = NavType.StringType; defaultValue = Routes.MODE_PLAY },
                        navArgument("server") { type = NavType.StringType; defaultValue = "" },
                        navArgument("category") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val viewModel: SourcesViewModel = hiltViewModel(entry)
                    SourcesScreen(
                        onPlay = { stream ->
                            navController.navigate(
                                Routes.player(
                                    url = stream.url.orEmpty(),
                                    title = viewModel.title,
                                    headers = stream.headers.toHeaderString(),
                                    contentType = viewModel.type,
                                    streamType = stream.streamType.orEmpty(),
                                    tmdbId = viewModel.tmdbId,
                                    season = viewModel.season,
                                    episode = viewModel.episode,
                                ),
                            )
                        },
                        onDownload = { stream ->
                            downloadManager.enqueueDownload(
                                url = stream.url.orEmpty(),
                                fileName = "${viewModel.title}.mp4",
                                title = viewModel.title,
                                contentType = viewModel.type,
                                contentId = viewModel.id,
                                quality = stream.quality.orEmpty(),
                                size = stream.size.orEmpty(),
                                headers = stream.headers,
                                tmdbId = viewModel.tmdbId,
                                season = viewModel.season,
                                episode = viewModel.episode,
                            )
                        },
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel,
                    )
                }

                // ---- the player --------------------------------------------------

                composable(
                    route = Routes.PLAYER_ROUTE,
                    arguments = listOf(
                        navArgument("url") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("headers") { type = NavType.StringType; defaultValue = "" },
                        navArgument("contentType") { type = NavType.StringType; defaultValue = "tv" },
                        navArgument("streamType") { type = NavType.StringType; defaultValue = "" },
                        navArgument("tmdbId") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("season") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("episode") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("contentKey") { type = NavType.StringType; defaultValue = "" },
                        navArgument("fallbackUrl") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    PlayerScreen(viewModel = hiltViewModel())
                }
            }
        }
    }
}
