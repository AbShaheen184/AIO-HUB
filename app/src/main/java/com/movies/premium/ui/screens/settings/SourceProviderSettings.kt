package com.movies.premium.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.data.repository.SourceCatalog
import com.movies.premium.ui.viewmodel.SettingsViewModel

/**
 * The "Sources (http)" section of the settings screen.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.h0` (the section
 * header) and `com.app.mlounge.ui.screens.person.b` (mode 3 — the content of the
 * http section), with the switch callbacks `com.app.mlounge.ui.viewmodel.f2`
 * (the master switch) and `com.app.mlounge.ui.viewmodel.e2` (one source).
 *
 * The exact strings are the original's: `All HTTP sources`, `All enabled`,
 * `Some disabled — tap to enable all`, `Enabled`, `Disabled`.
 */
@Composable
fun HttpSourcesSettings(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val disabled by viewModel.disabledProviders.collectAsStateWithLifecycle()
    SourceProviderSection(
        title = "Sources (http)",
        masterTitle = "All HTTP sources",
        sources = SourceCatalog.HTTP,
        disabled = disabled,
        icon = Icons.Filled.Cloud,
        onToggle = viewModel::setProviderEnabled,
        onToggleAll = viewModel::setProvidersEnabled,
    )
}

/**
 * The "Sources (Torrents)" section.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.w` (mode 0).
 *
 * The original only renders this section when the user has connected at least
 * one torrent / debrid service — the gate is in the original's `prefs/k0` and
 * is read as `f2.J`. The replica's gate is
 * [com.movies.premium.ui.viewmodel.SettingsViewModel.hasTorrentAccount].
 */
@Composable
fun TorrentSourcesSettings(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val disabled by viewModel.disabledProviders.collectAsStateWithLifecycle()
    val hasTorrentAccount by viewModel.hasTorrentAccount.collectAsStateWithLifecycle()

    if (!hasTorrentAccount) return

    SourceProviderSection(
        title = "Sources (Torrents)",
        masterTitle = "All torrent sources",
        sources = SourceCatalog.TORRENT,
        disabled = disabled,
        icon = Icons.Filled.CloudDownload,
        onToggle = viewModel::setProviderEnabled,
        onToggleAll = viewModel::setProvidersEnabled,
    )
}

/**
 * The "Sources (Anime)" section.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.w` (mode 1).
 */
@Composable
fun AnimeSourcesSettings(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val disabled by viewModel.disabledProviders.collectAsStateWithLifecycle()
    SourceProviderSection(
        title = "Sources (Anime)",
        masterTitle = "All anime sources",
        sources = SourceCatalog.ANIME,
        disabled = disabled,
        icon = Icons.Filled.Cloud,
        onToggle = viewModel::setProviderEnabled,
        onToggleAll = viewModel::setProvidersEnabled,
    )
}

/**
 * One of the three source sections.
 *
 * The master row switches the whole group on or off; one row per source
 * switches a single source.
 */
@Composable
private fun SourceProviderSection(
    title: String,
    masterTitle: String,
    sources: List<SourceCatalog.Source>,
    disabled: Set<String>,
    icon: ImageVector,
    onToggle: (String, Boolean) -> Unit,
    onToggleAll: (List<String>, Boolean) -> Unit,
) {
    if (sources.isEmpty()) return

    val allEnabled = sources.none { it.key in disabled }

    SettingsSection(title = title) {
        SettingsSwitchRow(
            title = masterTitle,
            subtitle = if (allEnabled) {
                "All enabled"
            } else {
                "Some disabled — tap to enable all"
            },
            checked = allEnabled,
            onCheckedChange = { onToggleAll(sources.map { it.key }, it) },
            icon = Icons.Filled.DoneAll,
        )
        for (source in sources) {
            val enabled = source.key !in disabled
            SettingsSwitchRow(
                title = source.label,
                subtitle = if (enabled) "Enabled" else "Disabled",
                checked = enabled,
                onCheckedChange = { onToggle(source.key, it) },
                icon = icon,
            )
        }
    }
}
