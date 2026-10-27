package com.movies.premium.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.viewmodel.SettingsViewModel

/**
 * The list of the sections the user can order and show or hide in the sidebar.
 *
 * Mirrors the sidebar case of the original's
 * `com.app.mlounge.ui.screens.settings.w` (R8 name `w`), which renders every
 * entry of `com.app.mlounge.ui.navigation.w.f` through the original's
 * `h0.l` row. The reorder callback is the original's
 * `com.app.mlounge.ui.viewmodel.q1` and the show/hide callback is the
 * original's `com.app.mlounge.ui.viewmodel.e2`.
 *
 * The original stores the order in the `nav_order` preference (a comma
 * separated list of routes) and the hidden sections in the `hidden_menus`
 * preference (a set of section titles). The replica keeps the same two
 * preferences in `AppPreferences`.
 */
@Composable
fun SourceOrderSettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val storedOrder by viewModel.navOrder.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenMenus.collectAsStateWithLifecycle()

    // The original appends the destinations that are missing from the stored
    // order to the end of the list, so a newly added section never disappears.
    val orderedRoutes = remember(storedOrder) {
        val base = SidebarDestinations.map { it.route }
        if (storedOrder.isEmpty()) base else storedOrder + base.filter { it !in storedOrder }
    }

    val showable = SidebarShowableTitles
    val allShown = remember(hidden) {
        SidebarDestinations.none { it.title in showable && it.title in hidden }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // The original shows a master switch with the same wording above the
        // torrent and anime source lists; it is reused here for the sidebar.
        SettingsSwitchRow(
            title = if (allShown) {
                "All enabled"
            } else {
                "Some disabled — tap to enable all"
            },
            subtitle = "Show every section in the sidebar",
            checked = allShown,
            onCheckedChange = { enabled ->
                showable.forEach { title ->
                    if (enabled == (title in hidden)) viewModel.toggleNavItem(title)
                }
            },
            icon = Icons.Filled.Apps,
        )

        orderedRoutes.forEachIndexed { index, route ->
            val destination = SidebarByRoute[route] ?: return@forEachIndexed
            SidebarItemRow(
                title = destination.title,
                icon = destination.icon,
                shown = destination.title !in hidden,
                showable = destination.title in showable,
                canMoveUp = index > 0,
                canMoveDown = index < orderedRoutes.lastIndex,
                onToggle = { viewModel.toggleNavItem(destination.title) },
                onMoveUp = { viewModel.moveNavItem(route, up = true) },
                onMoveDown = { viewModel.moveNavItem(route, up = false) },
            )
        }

        Text(
            text = "↑ First visible item is the default page on launch",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** The destinations by route, for the reorder list. */
private val SidebarByRoute: Map<String, SidebarDestination> =
    SidebarDestinations.associateBy { it.route }
