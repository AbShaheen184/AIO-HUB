package com.movies.premium.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.viewmodel.SettingsViewModel
import com.movies.premium.ui.tv.focusRing

/**
 * An app the app can hand a video or a download to.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.i0`
 * (`SupportedPlayer(packageName=…, displayName=…)`), which the original uses
 * for both the video players and the downloaders.
 */
data class SupportedPlayer(
    val packageName: String,
    val displayName: String,
)

/**
 * The video players the original knows about.
 *
 * Mirrors the original's `h0.c` list. The package name of the built-in player
 * is `internal`.
 */
val KnownVideoPlayers: List<SupportedPlayer> = listOf(
    SupportedPlayer("internal", "Built-in Player"),
    SupportedPlayer("com.mxtech.videoplayer.ad", "MX Player"),
    SupportedPlayer("com.mxtech.videoplayer.pro", "MX Player Pro"),
    SupportedPlayer("org.videolan.vlc", "VLC"),
    SupportedPlayer("com.brouken.player", "Just (Video) Player"),
    SupportedPlayer("com.hbplayer.HBvideoplayer", "HB Video Player"),
    SupportedPlayer("com.player.bear", "Bear/Fluid Video Player"),
)

/**
 * The downloaders the original knows about.
 *
 * Mirrors the original's `h0.d` list. The package name of the built-in
 * downloader is `internal`.
 */
val KnownDownloaders: List<SupportedPlayer> = listOf(
    SupportedPlayer("internal", "Built-in Downloader"),
    SupportedPlayer("com.dv.adm", "ADM"),
    SupportedPlayer("idm.internet.download.manager", "1DM"),
    SupportedPlayer("idm.internet.download.manager.plus", "1DM+"),
)

/**
 * The video player settings.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.h0.f` (the video
 * player dialog) and `h0.h` (the dialog that lists the players the original
 * knows about), together with the
 * `com.app.mlounge.ui.screens.settings.j` callback that shows the
 * "Please install … first" toast.
 */
@Composable
fun VideoPlayerSettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val externalPlayer by viewModel.externalPlayerApp.collectAsStateWithLifecycle()

    var showPlayerPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSwitchRow(
            title = "Use external player",
            subtitle = externalPlayer?.displayName ?: "Built-in Player",
            checked = state.externalPlayer,
            onCheckedChange = viewModel::setExternalPlayer,
            icon = Icons.Filled.PlayArrow,
        )
        SettingsActionRow(
            label = "Video Player",
            icon = Icons.Filled.PlayArrow,
            onClick = { showPlayerPicker = true },
        )
        // Deviation: the original keeps the preference `player_auto_play_next`
        // but does not expose a row for it anywhere in the settings. The
        // replica does, so the automatic next episode can be switched off.
        SettingsSwitchRow(
            title = "Autoplay next episode",
            subtitle = "Play the next episode automatically",
            checked = state.autoPlayNext,
            onCheckedChange = viewModel::setAutoPlayNext,
            icon = Icons.Filled.AutoAwesome,
        )
    }

    if (showPlayerPicker) {
        val candidates = remember(context) {
            (KnownVideoPlayers + installedVideoPlayers(context))
                .distinctBy { it.packageName }
                .sortedBy { it.displayName.lowercase() }
        }
        PlayerPickerDialog(
            title = "Video Player",
            players = candidates,
            selectedPackage = externalPlayer?.packageName,
            onDismiss = { showPlayerPicker = false },
            onSelect = { player ->
                showPlayerPicker = false
                if (player.packageName == BUILT_IN_PACKAGE) {
                    viewModel.setExternalPlayerApp("", "")
                } else if (!isInstalled(context, player.packageName)) {
                    toastNotInstalled(context, player.displayName)
                } else {
                    viewModel.setExternalPlayerApp(player.packageName, player.displayName)
                }
            },
        )
    }
}

/**
 * The downloader settings.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.h0.g` (the
 * downloader dialog) and the `...settings.j` callback that shows the
 * "Please install … first" toast.
 */
@Composable
fun DownloaderSettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val externalDownloader by viewModel.externalDownloaderApp.collectAsStateWithLifecycle()

    var showDownloaderPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsActionRow(
            label = "Downloader",
            icon = Icons.Filled.Download,
            onClick = { showDownloaderPicker = true },
        )
    }

    if (showDownloaderPicker) {
        PlayerPickerDialog(
            title = "Downloader",
            players = KnownDownloaders,
            selectedPackage = externalDownloader?.packageName,
            onDismiss = { showDownloaderPicker = false },
            onSelect = { player ->
                showDownloaderPicker = false
                if (player.packageName == BUILT_IN_PACKAGE) {
                    viewModel.setExternalDownloaderApp("", "")
                } else if (!isInstalled(context, player.packageName)) {
                    toastNotInstalled(context, player.displayName)
                } else {
                    viewModel.setExternalDownloaderApp(player.packageName, player.displayName)
                }
            },
        )
    }
}

/**
 * The dialog that lists the players the user can pick.
 *
 * Mirrors the original's `androidx.compose.material3.q.a(...)` dialogs in
 * `h0.f` / `h0.g` / `h0.h`.
 */
@Composable
private fun PlayerPickerDialog(
    title: String,
    players: List<SupportedPlayer>,
    selectedPackage: String?,
    onDismiss: () -> Unit,
    onSelect: (SupportedPlayer) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(players) { player ->
                    Text(
                        text = player.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (player.packageName == selectedPackage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(player) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * The video players that are installed on the device.
 *
 * Mirrors the original's `h0.f`, which asks the package manager for the apps
 * that can open a video url, drops the ones the original knows about
 * already and drops the app itself.
 */
private fun installedVideoPlayers(context: Context): List<SupportedPlayer> {
    val packageManager = context.packageManager
    val intents = listOf(
        Intent(Intent.ACTION_VIEW).setType("video/*"),
        Intent(Intent.ACTION_VIEW).setType("video/*")
            .setData(Uri.parse("https://example.com/video.m3u8")),
        Intent(Intent.ACTION_VIEW).setType("video/*")
            .setData(Uri.parse("file:///sdcard/video.mp4")),
    )
    val known = KnownVideoPlayers.map { it.packageName }.toSet()
    val found = LinkedHashMap<String, SupportedPlayer>()
    intents.forEach { intent ->
        val resolved = runCatching {
            packageManager.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())
        resolved.forEach { info ->
            val packageName = info.activityInfo.packageName
            if (packageName in known || packageName == context.packageName) return@forEach
            val label = runCatching { info.loadLabel(packageManager).toString() }
                .getOrDefault(packageName)
            if (!found.containsKey(packageName)) {
                found[packageName] = SupportedPlayer(packageName, label)
            }
        }
    }
    return found.values.toList()
}

/** `true` when the given package is installed on the device. */
private fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
    context.packageManager.getPackageInfo(packageName, 0)
    true
}.getOrDefault(false)

/**
 * Shows the original's "Please install … first" toast.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.j`.
 */
private fun toastNotInstalled(context: Context, displayName: String) {
    Toast.makeText(context, "Please install $displayName first", Toast.LENGTH_SHORT).show()
}

/** The package name the original uses for its own player and downloader. */
private const val BUILT_IN_PACKAGE = "internal"
