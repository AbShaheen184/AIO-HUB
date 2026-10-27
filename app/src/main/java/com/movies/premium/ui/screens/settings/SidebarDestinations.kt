package com.movies.premium.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.movies.premium.ui.navigation.Routes

/**
 * One entry of the app's side navigation.
 *
 * The original models these in `com.app.mlounge.ui.navigation.w` (R8 name `w`),
 * whose fields are `a` = route, `b` = title, `c` = icon and `d` = selected icon.
 * The replica has no such model, so this file provides the equivalent.
 */
data class SidebarDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
)

/**
 * The destinations the user can order and show or hide in the sidebar.
 *
 * Mirrors the original's `com.app.mlounge.ui.navigation.w.f`, which holds the
 * thirteen destinations in this exact order:
 * search, movies, tvshows, livetv, anime, music, games, adult, watchlist,
 * downloads, history, services, settings.
 *
 * The replica adds a `home` destination of its own (the original opens straight
 * onto `search`), so it is listed first. "Home" is not part of
 * [SidebarShowableTitles] and therefore cannot be hidden — like "Search",
 * "Adult" and "Settings" in the original.
 */
val SidebarDestinations: List<SidebarDestination> = listOf(
    SidebarDestination(Routes.HOME, "Home", Icons.Filled.Home),
    SidebarDestination(Routes.SEARCH, "Search", Icons.Filled.Search),
    SidebarDestination(Routes.MOVIES, "Movies", Icons.Filled.Movie),
    SidebarDestination(Routes.TV_SHOWS, "TV Shows", Icons.Filled.Tv),
    SidebarDestination(Routes.LIVE_TV, "Live TV", Icons.Filled.LiveTv),
    SidebarDestination(Routes.ANIME, "Anime", Icons.Filled.Animation),
    SidebarDestination(Routes.MUSIC, "Music", Icons.Filled.MusicNote),
    SidebarDestination(Routes.GAMES, "Games", Icons.Filled.SportsEsports),
    SidebarDestination(Routes.ADULT, "Adult", Icons.Filled.Warning),
    SidebarDestination(Routes.WATCHLIST, "Favourites", Icons.Filled.Favorite),
    SidebarDestination(Routes.DOWNLOADS, "Downloads", Icons.Filled.Download),
    SidebarDestination(Routes.HISTORY, "History", Icons.Filled.History),
    SidebarDestination(Routes.SERVICES, "Services", Icons.Filled.Info),
    SidebarDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

/**
 * The titles of the sections the user is allowed to hide.
 *
 * Mirrors the original's `setOf("Movies", "TV Shows", "Live TV", "Anime",
 * "Music", "Games", "Favourites", "Downloads", "History", "Services")` in
 * `com.app.mlounge.ui.screens.settings.w`. "Search", "Adult" and "Settings"
 * are deliberately absent — the original does not offer a switch for them.
 */
val SidebarShowableTitles: Set<String> = setOf(
    "Movies",
    "TV Shows",
    "Live TV",
    "Anime",
    "Music",
    "Games",
    "Favourites",
    "Downloads",
    "History",
    "Services",
)
