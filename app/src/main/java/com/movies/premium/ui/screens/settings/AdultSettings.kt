package com.movies.premium.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.components.AdultPinDialog
import com.movies.premium.ui.components.AdultPinMode
import com.movies.premium.ui.viewmodel.SettingsViewModel

/**
 * The "Content" section of the settings screen.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.m`, which is the
 * content of the "Content" section. The original's four rows are reproduced
 * here, with its own strings:
 *
 * | Row | Subtitle |
 * |---|---|
 * | `Adult Section` | `Enable adult content section` / `Adult content section is enabled` |
 * | `Include Adult in Search` | `Show adult content in TMDB search results` |
 * | `Include Adult in Favourites` | `Show adult items in your favourites` |
 * | `Include Adult in History` | `Show adult items in your history` |
 *
 * **The PIN.** Switching the adult section on is gated by a 4-digit PIN, which
 * the original asks for in one of its two dialogs
 * (`com.app.mlounge.ui.components.e0.p`):
 *
 * - there is no PIN yet → the "Set Adult PIN" dialog, which asks for the PIN
 *   twice and stores it,
 * - a PIN is there → the "Enter PIN" dialog, which asks for it once and only
 *   enables the section when it matches.
 *
 * A wrong PIN leaves the dialog open without a message — the original has no
 * "wrong PIN" string either.
 */
@Composable
fun AdultSettings(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pin by viewModel.adultPin.collectAsStateWithLifecycle()
    val includeInSearch by viewModel.includeAdultInSearch.collectAsStateWithLifecycle()
    val includeInFavourites by viewModel.includeAdultInFavourites.collectAsStateWithLifecycle()
    val includeInHistory by viewModel.includeAdultInHistory.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf<AdultPinMode?>(null) }

    SettingsSection(title = "Content") {
        SettingsSwitchRow(
            title = "Adult Section",
            subtitle = if (state.adultEnabled) {
                "Adult content section is enabled"
            } else {
                "Enable adult content section"
            },
            checked = state.adultEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    // The original asks for the PIN before it switches the
                    // section on: the "Set Adult PIN" dialog when there is no
                    // PIN yet, the "Enter PIN" dialog when there is one.
                    dialog = if (pin.isNullOrEmpty()) AdultPinMode.Create else AdultPinMode.Verify
                } else {
                    viewModel.setAdultEnabled(false)
                }
            },
            icon = Icons.Filled.Info,
        )
        SettingsSwitchRow(
            title = "Include Adult in Search",
            subtitle = "Show adult content in TMDB search results",
            checked = includeInSearch,
            onCheckedChange = viewModel::setIncludeAdultInSearch,
            icon = Icons.Filled.Search,
        )
        SettingsSwitchRow(
            title = "Include Adult in Favourites",
            subtitle = "Show adult items in your favourites",
            checked = includeInFavourites,
            onCheckedChange = viewModel::setIncludeAdultInFavourites,
            icon = Icons.Filled.BookmarkBorder,
        )
        SettingsSwitchRow(
            title = "Include Adult in History",
            subtitle = "Show adult items in your history",
            checked = includeInHistory,
            onCheckedChange = viewModel::setIncludeAdultInHistory,
            icon = Icons.Filled.History,
        )
        // Deviation: the original opens the very same "Set Adult PIN" dialog,
        // but it does not expose a row for it — the replica does, so that an
        // already stored PIN can be changed.
        SettingsActionRow(
            label = "Set Adult PIN",
            icon = Icons.Filled.Lock,
            onClick = { dialog = AdultPinMode.Create },
        )
    }

    dialog?.let { mode ->
        AdultPinDialog(
            mode = mode,
            onDismiss = { dialog = null },
            onConfirm = { entered ->
                if (mode == AdultPinMode.Create) {
                    // The original stores the PIN and switches the adult
                    // section on in one go.
                    viewModel.setAdultPin(entered)
                    viewModel.setAdultEnabled(true)
                    dialog = null
                } else if (entered == pin) {
                    viewModel.setAdultEnabled(true)
                    dialog = null
                }
                // A wrong PIN does nothing, exactly like in the original.
            },
        )
    }
}
