package com.movies.premium.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.viewmodel.SettingsViewModel

/**
 * The settings screen.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.h0.p`, which
 * shows the "Settings" title and the collapsible sections "Player",
 * "Downloads", "Orientation", "User Interface", "Sources (http)",
 * "Sources (Torrents)", "Sources (Anime)", "Content", "Navigation",
 * "Emulator Settings", "Storage" and "About", plus the "Custom IPTV Provider"
 * block of `h0.e`.
 *
 * The section headers and their collapsed state mirror the original's
 * `h0.q`; the rows mirror `h0.r` (the switch row), `h0.s` (the text field
 * row), `h0.o` (the file row) and `h0.j` (the icon button row).
 *
 * **The original's "Resource Sharing" section is deliberately missing.** That
 * section switches the Hopmn bandwidth-sharing SDK and the Appsalt consent
 * dialog on and off, and it uploads the device's IP to
 * `https://pubs.myrc.xyz/api/optout` when it is switched off. Those are the
 * money-making services the replica must not ship — see §1.2 and §7 of the
 * handoff document. Nothing else of the section is user-visible.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        item {
            SettingsSection(title = "Player") {
                VideoPlayerSettings()
            }
        }

        item {
            SettingsSection(title = "Downloads") {
                DownloaderSettings()
                DownloadSettings()
            }
        }

        item {
            SettingsSection(title = "Orientation") {
                OrientationSettings()
            }
        }

        item {
            SettingsSection(title = "User Interface") {
                SettingsSliderRow(
                    title = "Text size",
                    value = state.textScale.toFloat().coerceIn(0.8f, 1.6f),
                    onValueChange = viewModel::setTextScale,
                )
                SettingsSliderRow(
                    title = "Card size",
                    value = state.cardScale.toFloat().coerceIn(0.8f, 1.6f),
                    onValueChange = viewModel::setCardScale,
                )
            }
        }

        item {
            HttpSourcesSettings()
        }

        item {
            TorrentSourcesSettings()
        }

        item {
            AnimeSourcesSettings()
        }

        item {
            AdultSettings()
        }

        item {
            SettingsSection(title = "Navigation") {
                SourceOrderSettings()
            }
        }

        item {
            SettingsSection(title = "Emulator Settings") {
                AspectRatioSettings()
            }
        }

        item {
            SettingsSection(title = "Storage") {
                StorageSettings()
            }
        }

        item {
            IptvProviderSettings()
        }

        item {
            SettingsSection(title = "About") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "AIO Hub",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "Version ${state.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

/**
 * A labelled slider.
 *
 * Mirrors the original's "Text size" / "Card size" sliders of the
 * "User Interface" section.
 */
@Composable
private fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.8f..1.6f,
        )
    }
}
