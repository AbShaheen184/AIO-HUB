package com.movies.premium.ui.screens.games

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.movies.premium.emulator.EmulatorActivity
import com.movies.premium.ui.viewmodel.GameDetailViewModel

/**
 * Game detail: metadata plus the ROM download and the emulator launcher.
 *
 * The original's screen is `com.app.mlounge.ui.screens.games.a`. Its "Play" button
 * only starts the emulator when the ROM is already on disk
 * (`filesDir/roms/<slug><ext>`); otherwise it shows the toast
 * "Download the game first". The same rule applies here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GameDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.detail?.name ?: "Game",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val detail = state.detail
            when {
                state.loading && detail == null -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                detail == null -> {
                    Text(
                        text = state.error ?: "This game is not available.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = detail.posterUrl,
                            contentDescription = detail.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .aspectRatio(3f / 4f)
                                .align(Alignment.CenterHorizontally)
                                .background(Color.DarkGray, RoundedCornerShape(12.dp)),
                        )

                        Text(
                            text = detail.name.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = listOfNotNull(
                                detail.year,
                                detail.genre,
                                detail.region,
                                detail.players,
                            ).joinToString("  •  "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        MetaRow("Platform", state.platform.displayName)
                        detail.developer?.let { MetaRow("Developer", it) }
                        detail.publisher?.let { MetaRow("Publisher", it) }
                        detail.romSizeMb?.let { MetaRow("ROM size", "$it MB") }

                        if (!detail.description.isNullOrBlank()) {
                            Text(
                                text = detail.description,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        if (state.downloading) {
                            LinearProgressIndicator(
                                progress = {
                                    if (state.totalBytes > 0) {
                                        (state.downloadedBytes.toFloat() / state.totalBytes.toFloat())
                                            .coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "Downloading… ${state.downloadedBytes / 1024} KB",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.romReady) {
                                Button(
                                    onClick = {
                                        val romPath = viewModel.romPath()
                                        if (romPath == null) {
                                            Toast.makeText(
                                                context,
                                                "Download the game first",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            return@Button
                                        }
                                        val intent = Intent(context, EmulatorActivity::class.java).apply {
                                            putExtra(EmulatorActivity.EXTRA_ROM_PATH, romPath)
                                            putExtra(
                                                EmulatorActivity.EXTRA_GAME_TITLE,
                                                detail.name ?: "Game",
                                            )
                                            putExtra(
                                                EmulatorActivity.EXTRA_CORE_NAME,
                                                state.platform.coreName,
                                            )
                                        }
                                        context.startActivity(intent)
                                    },
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Text("  Play")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.download() },
                                    enabled = !state.downloading,
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Text("  Download")
                                }
                            }
                        }

                        state.error?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
