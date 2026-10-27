package com.movies.premium.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.viewmodel.IptvSourceType
import com.movies.premium.ui.viewmodel.SettingsIptvViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "Custom IPTV Provider" form.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.h0.e` (the section
 * itself) and `com.app.mlounge.ui.screens.settings.c0` (the form body), with
 * the source type chips of `...settings.n` and the file pickers of
 * `...settings.o` / `...settings.b0`.
 *
 * The channels are written into the IPTV layer through
 * `com.movies.premium.data.iptv.CustomIPTV`, which is the replica of the
 * original's `com.app.mlounge.data.iptv.p`.
 */
@Composable
fun IptvProviderSettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsIptvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val m3uPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.setLocalM3uPath(uri.toString())
    }
    val epgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.setLocalEpgPath(uri.toString())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Custom IPTV Provider",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Text(
            text = "Source Type",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IptvSourceTypeChip(
                label = "M3U Playlist",
                selected = state.sourceType == IptvSourceType.M3U,
                onClick = { viewModel.setSourceType(IptvSourceType.M3U) },
            )
            IptvSourceTypeChip(
                label = "Xtream Codes",
                selected = state.sourceType == IptvSourceType.XTREAM,
                onClick = { viewModel.setSourceType(IptvSourceType.XTREAM) },
            )
            IptvSourceTypeChip(
                label = "Local File",
                selected = state.sourceType == IptvSourceType.LOCAL,
                onClick = { viewModel.setSourceType(IptvSourceType.LOCAL) },
            )
        }

        when (state.sourceType) {
            IptvSourceType.XTREAM -> {
                SettingsTextField(
                    label = "Server Host",
                    value = state.xtreamHost,
                    onValueChange = viewModel::setXtreamHost,
                    icon = Icons.Filled.Dns,
                    placeholder = "http://yourserver.com",
                    keyboardType = KeyboardType.Uri,
                )
                SettingsTextField(
                    label = "Username",
                    value = state.xtreamUsername,
                    onValueChange = viewModel::setXtreamUsername,
                    icon = Icons.Filled.Tag,
                    placeholder = "username",
                )
                SettingsTextField(
                    label = "Port (opt.)",
                    value = state.xtreamPort,
                    onValueChange = viewModel::setXtreamPort,
                    icon = Icons.Filled.Tag,
                    placeholder = "8080",
                    keyboardType = KeyboardType.Number,
                )
                SettingsTextField(
                    label = "Password",
                    value = state.xtreamPassword,
                    onValueChange = viewModel::setXtreamPassword,
                    icon = Icons.Filled.Password,
                    placeholder = "password",
                    visualTransformation = PasswordVisualTransformation(),
                )
                SettingsSwitchRow(
                    title = "Use Xtream built-in EPG",
                    subtitle = if (state.useBuiltinEpg) {
                        "Fetch guide from server's xmltv endpoint"
                    } else {
                        "EPG wont be used"
                    },
                    checked = state.useBuiltinEpg,
                    onCheckedChange = viewModel::setUseBuiltinEpg,
                    icon = Icons.Filled.Info,
                )
            }

            IptvSourceType.LOCAL -> {
                SettingsFileRow(
                    title = "Choose .m3u file",
                    subtitle = state.localM3uPath.ifBlank { "Select Local M3U" },
                    icon = Icons.Filled.FolderOpen,
                    onClick = { m3uPicker.launch(arrayOf("*/*")) },
                )
                SettingsFileRow(
                    title = "Choose .xml or .gz file",
                    subtitle = state.localEpgPath.ifBlank { "Select Local EPG (optional)" },
                    icon = Icons.Filled.FolderOpen,
                    onClick = { epgPicker.launch(arrayOf("*/*")) },
                )
            }

            else -> {
                SettingsTextField(
                    label = "M3U Playlist URL",
                    value = state.m3uUrl,
                    onValueChange = viewModel::setM3uUrl,
                    icon = Icons.Filled.Link,
                    placeholder = "http://example.com/playlist.m3u",
                    keyboardType = KeyboardType.Uri,
                )
                SettingsTextField(
                    label = "EPG URL (optional)",
                    value = state.epgUrl,
                    onValueChange = viewModel::setEpgUrl,
                    icon = Icons.Filled.Link,
                    placeholder = "http://example.com/epg.xml.gz",
                    keyboardType = KeyboardType.Uri,
                )
            }
        }

        Text(
            text = "Internal Player does not support some IPTV Providers and will work best " +
                "with VLC or MX Player",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsButton(
                label = if (state.sourceType == IptvSourceType.XTREAM) {
                    "Download & Sync"
                } else {
                    "Sync"
                },
                onClick = { viewModel.sync() },
                enabled = !state.isSyncing,
            )
            SettingsButton(
                label = "Clear",
                onClick = { viewModel.clear() },
                filled = false,
            )
            if (state.isSyncing) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (state.status.isNotBlank()) {
            Text(
                text = state.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        state.error?.let { error ->
            Text(
                text = "Error: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (state.channelCount > 0) {
            Text(
                text = "${state.channelCount} channels loaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Text(
            text = "Last sync: ${formatLastSync(state.lastSync)}" +
                if (state.hasEpg) " · EPG" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/**
 * One of the three source type chips.
 *
 * Mirrors the chip row of the original's `com.app.mlounge.ui.screens.settings.n`.
 */
@Composable
private fun IptvSourceTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

/**
 * Formats the "Last sync" timestamp.
 *
 * Mirrors the original's `dd MMM yyyy HH:mm` date format; the original shows
 * "Never" when the app has never synced.
 */
private fun formatLastSync(timestamp: Long): String {
    if (timestamp <= 0L) return "Never"
    return SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}
