package com.movies.premium.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.viewmodel.SettingsViewModel
import com.movies.premium.ui.tv.focusRing

/**
 * The aspect ratios the player can use.
 *
 * Mirrors the original's list in
 * `com.app.mlounge.ui.screens.settings.h0.d`: `fit` / "Fit to Screen",
 * `4:3` / "4:3 (Original)", `16:9` / "16:9 (Widescreen)" and
 * `stretch` / "Stretch to Fill".
 */
val AspectRatioOptions: List<Pair<String, String>> = listOf(
    "fit" to "Fit to Screen",
    "4:3" to "4:3 (Original)",
    "16:9" to "16:9 (Widescreen)",
    "stretch" to "Stretch to Fill",
)

/**
 * The interface modes the app can run in.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.e`, which turns
 * the "TV" and "Mobile" labels into the `tv`, `mobile` and `auto` values of
 * the `ui_mode` preference.
 */
val UiModeOptions: List<Pair<String, String>> = listOf(
    "auto" to "Auto",
    "tv" to "TV",
    "mobile" to "Mobile",
)

/**
 * The display settings: the aspect ratio of the player and the interface mode.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.h0.d` (the
 * aspect ratio dialog) and `com.app.mlounge.ui.screens.settings.e` (the
 * "TV" / "Mobile" / "auto" interface mode), which live in the original's
 * "Orientation" and "Emulator Settings" sections.
 */
@Composable
fun DisplaySettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AspectRatioSettings(viewModel = viewModel)
        OrientationSettings(viewModel = viewModel)
    }
}

/**
 * The aspect ratio of the player.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.h0.d`, which
 * shows the four aspect ratios in a radio dialog.
 */
@Composable
fun AspectRatioSettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val aspectRatio by viewModel.emuAspectRatio.collectAsStateWithLifecycle()
    var showAspectRatio by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsFileRow(
            title = "Aspect Ratio",
            subtitle = AspectRatioOptions.firstOrNull { it.first == aspectRatio }?.second
                ?: "Fit to Screen",
            icon = Icons.Filled.AspectRatio,
            onClick = { showAspectRatio = true },
        )
    }

    if (showAspectRatio) {
        AspectRatioDialog(
            selected = aspectRatio,
            onDismiss = { showAspectRatio = false },
            onSelect = {
                viewModel.setEmuAspectRatio(it)
                showAspectRatio = false
            },
        )
    }
}

/**
 * The interface mode: "TV", "Mobile" or the automatic one.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.e`, which turns
 * the "TV" and "Mobile" labels into the `tv`, `mobile` and `auto` values of
 * the `ui_mode` preference.
 */
@Composable
fun OrientationSettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiMode by viewModel.uiMode.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsChoiceRow(
            title = "Orientation",
            subtitle = UiModeOptions.firstOrNull { it.first == uiMode }?.second ?: "Auto",
            icon = Icons.Filled.ScreenRotation,
            options = UiModeOptions,
            selected = uiMode,
            onSelect = viewModel::setUiMode,
        )
    }
}

/**
 * The aspect ratio dialog.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.h0.d`, which
 * shows the four aspect ratios as a radio list.
 */
@Composable
private fun AspectRatioDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        title = { Text("Aspect Ratio") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AspectRatioOptions.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
