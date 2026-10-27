package com.movies.premium.ui.screens.livetv

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.data.iptv.EpgProgram
import com.movies.premium.data.remote.model.IptvChannel
import com.movies.premium.data.remote.model.SportsMatch
import com.movies.premium.ui.viewmodel.LiveTvPlayCallback
import com.movies.premium.ui.viewmodel.LiveTvProviders
import com.movies.premium.ui.viewmodel.LiveTvViewModel
import com.movies.premium.ui.tv.focusRing

/**
 * The Live TV screen.
 *
 * The original's screen is `com.app.mlounge.ui.screens.livetv.a`; its entry
 * composable collects thirteen flows from `com.app.mlounge.ui.viewmodel.f0` and
 * renders
 *
 * * a top bar with the title **"Live TV"**,
 * * a row of twelve provider chips (**CHQ TV**, **My IPTV**, **Pluto TV**,
 *   **Roku TV**, **Xumo TV**, **Tubi TV**, **Yupp TV**, **US Local**,
 *   **Samsung TV+**, **LG TV**, **IPTVOrg**, **EarthCam**),
 * * a **"Filter groups"** chip row (hidden for CHQ TV),
 * * a **"Filter server"** chip row (CHQ TV: `DLHD` / `CDNLIVE`; sports:
 *   `Kobra` / `Raptor`),
 * * one of four bodies — a spinner, an error with a retry button, the
 *   "My IPTV not set up" state, a two column channel grid, the EPG list or the
 *   sports list — and
 * * two bottom sheets: the **"Choose Source"** picker of a sports match and the
 *   informational sheet.
 *
 * @param onPlay receives `(url, name, headers, contentType, extra)`; the
 *   original passes a `Function5` of the same shape to the player.
 * @param onBack the replica addition: the original is a TV app whose top bar
 *   has no navigation icon.
 */
@Composable
fun LiveTvScreen(
    onPlay: LiveTvPlayCallback,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The original shows the ViewModel's one-shot messages with a Toast.
    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // The match whose source list is open. The original keeps this in the
    // screen, not in the ViewModel.
    var selectedMatch by remember { mutableStateOf<SportsMatch?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Live TV") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LiveTvProviderTabs(
                selected = state.provider,
                onSelect = viewModel::selectProvider,
            )

            if (state.groups.isNotEmpty() && state.provider != LiveTvProviders.NTV) {
                LiveTvGroupRow(
                    groups = state.groups,
                    selected = state.selectedGroup,
                    provider = state.provider,
                    onSelect = viewModel::selectGroup,
                )
            }

            if (state.provider == LiveTvProviders.NTV) {
                LiveTvServerRow(
                    title = "Filter server",
                    servers = LiveTvProviders.NTV_SERVERS,
                    selected = state.ntvServer,
                    upperCase = true,
                    onSelect = viewModel::selectNtvServer,
                )
            }

            if (state.provider == LiveTvProviders.SPORTS && state.sportsMatches.isNotEmpty()) {
                LiveTvServerRow(
                    title = "Filter server",
                    servers = LiveTvProviders.SPORTS_SERVERS,
                    selected = state.sportsServer,
                    upperCase = false,
                    onSelect = viewModel::selectSportsServer,
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> LiveTvLoading()

                    state.error != null && state.channels.isEmpty() ->
                        LiveTvError(message = state.error!!, onRetry = viewModel::refresh)

                    state.provider == LiveTvProviders.CUSTOM && state.customNotSetUp ->
                        LiveTvNotSetUp()

                    state.provider == LiveTvProviders.SPORTS && state.sportsMatches.isNotEmpty() ->
                        LiveTvSportsList(
                            matches = state.sportsMatches,
                            server = state.sportsServer,
                            onMatchClick = { match ->
                                val sources = match.sources.orEmpty()
                                if (sources.size == 1) {
                                    val source = sources.first()
                                    viewModel.playMatch(match, source.id.orEmpty(), source.url, onPlay)
                                } else {
                                    selectedMatch = match
                                }
                            },
                        )

                    state.provider != LiveTvProviders.CUSTOM ||
                        state.epgPrograms.isEmpty() ||
                        state.channels.isEmpty() ->
                        LiveTvChannelGrid(
                            channels = state.channels,
                            provider = state.provider,
                            onClick = { viewModel.onChannelClick(it, onPlay) },
                            onLongClick = viewModel::onChannelLongPress,
                        )

                    else ->
                        LiveTvEpgList(
                            channels = state.channels,
                            programs = state.epgPrograms,
                            provider = state.provider,
                            onClick = { viewModel.onChannelClick(it, onPlay) },
                            onLongClick = viewModel::onChannelLongPress,
                        )
                }
            }
        }
    }

    // Bottom sheet 1 — the "Choose Source" picker of a sports match.
    val match = selectedMatch
    if (match != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedMatch = null },
            modifier = Modifier.focusRing(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Choose Source",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                val sources = match.sources.orEmpty()
                if (sources.isEmpty()) {
                    Text(
                        text = "No sources available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sources.forEach { source ->
                    LiveTvSourceRow(
                        source = source,
                        onClick = {
                            selectedMatch = null
                            viewModel.playMatch(match, source.id.orEmpty(), source.url, onPlay)
                        },
                    )
                }
            }
        }
    }

    // Bottom sheet 2 — the informational sheet. The original shows it while a
    // channel is being resolved and for a (not recoverable) message.
    val infoText = state.resolvingChannel?.let { "Resolving $it…" } ?: state.message
    if (infoText != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissMessage() },
            modifier = Modifier.focusRing(),
        ) {
            Text(
                text = infoText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/**
 * The row of provider chips.
 *
 * Mirrors the original's scrollable row of twelve provider tabs.
 */
@Composable
private fun LiveTvProviderTabs(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveTvProviders.TABS.forEach { (slug, label) ->
            LiveTvChip(
                label = label,
                selected = slug == selected,
                onClick = { onSelect(slug) },
            )
        }
    }
}

/**
 * The "Filter groups" row.
 *
 * Mirrors the original: the header is the first item of the scrollable row,
 * "All" clears the filter and Pluto TV shows the country name while IPTVOrg
 * shows the raw group.
 */
@Composable
private fun LiveTvGroupRow(
    groups: List<String>,
    selected: String?,
    provider: String,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveTvSectionHeader(text = "Filter groups")
        LiveTvChip(label = "All", selected = selected == null, onClick = { onSelect(null) })
        groups.forEach { group ->
            val label = if (provider == LiveTvProviders.PLUTO_TV) {
                liveTvCountryName(group)
            } else {
                group
            }
            LiveTvChip(
                label = label,
                selected = selected == group,
                onClick = { onSelect(group) },
            )
        }
    }
}

/**
 * The "Filter server" row.
 *
 * Mirrors the original: the header is tinted with the accent colour, "All"
 * clears the filter and the NTV servers are shown in upper case.
 */
@Composable
private fun LiveTvServerRow(
    title: String,
    servers: List<String>,
    selected: String?,
    upperCase: Boolean,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveTvSectionHeader(
            text = title,
            tint = MaterialTheme.colorScheme.primary,
        )
        LiveTvChip(label = "All", selected = selected == null, onClick = { onSelect(null) })
        servers.forEach { server ->
            LiveTvChip(
                label = if (upperCase) server.uppercase() else server,
                selected = selected.equals(server, ignoreCase = true),
                onClick = { onSelect(server) },
            )
        }
    }
}

/**
 * The two column channel grid.
 *
 * The original's grid has a fixed column count of two and a cell width taken
 * from the theme.
 */
@Composable
private fun LiveTvChannelGrid(
    channels: List<IptvChannel>,
    provider: String,
    onClick: (IptvChannel) -> Unit,
    onLongClick: (IptvChannel) -> Unit,
) {
    if (channels.isEmpty()) {
        LiveTvEmpty()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = channels.size) { index ->
            val channel = channels[index]
            LiveTvChannelCard(
                channel = channel,
                groupLabel = liveTvGroupLabel(channel, provider),
                onClick = { onClick(channel) },
                onLongClick = { onLongClick(channel) },
            )
        }
    }
}

/**
 * The EPG list.
 *
 * Mirrors the original's `ui.screens.livetv.f`: the guide of a channel is
 * looked up by its `tvgId`, the running programme is the one that contains the
 * current time and the next one is the first that starts later.
 */
@Composable
private fun LiveTvEpgList(
    channels: List<IptvChannel>,
    programs: Map<String, List<EpgProgram>>,
    provider: String,
    onClick: (IptvChannel) -> Unit,
    onLongClick: (IptvChannel) -> Unit,
) {
    if (channels.isEmpty()) {
        LiveTvEmpty()
        return
    }
    val now = System.currentTimeMillis()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = channels.size) { index ->
            val channel = channels[index]
            val guide = channel.tvgId?.let { programs[it] }.orEmpty()
            val current = guide.firstOrNull { it.startTimeMs <= now && it.endTimeMs > now }
            val next = guide.firstOrNull { it.startTimeMs > now }
            LiveTvEpgRow(
                channel = channel,
                current = current,
                next = next,
                groupLabel = liveTvGroupLabel(channel, provider),
                onClick = { onClick(channel) },
                onLongClick = { onLongClick(channel) },
            )
        }
    }
}

/**
 * The sports list.
 *
 * Mirrors the original: the matches of the selected server, and a match with
 * a single source is played straight away while a match with several sources
 * opens the "Choose Source" sheet.
 */
@Composable
private fun LiveTvSportsList(
    matches: List<SportsMatch>,
    server: String?,
    onMatchClick: (SportsMatch) -> Unit,
) {
    val visible = if (server == null) {
        matches
    } else {
        matches.filter { it.server.equals(server, ignoreCase = true) }
    }
    if (visible.isEmpty()) {
        LiveTvEmpty()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = visible.size) { index ->
            LiveTvSportsCard(
                match = visible[index],
                onClick = { onMatchClick(visible[index]) },
            )
        }
    }
}

/**
 * The label of a channel's group.
 *
 * Mirrors the original: Pluto TV shows the country name, IPTVOrg shows its
 * ";"-separated groups and every other provider shows the raw group.
 */
private fun liveTvGroupLabel(channel: IptvChannel, provider: String): String? = when (provider) {
    LiveTvProviders.PLUTO_TV ->
        channel.group?.substringBefore(" - ")?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { liveTvCountryName(it) }

    LiveTvProviders.IPTV_ORG ->
        channel.group?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.joinToString(", ")

    else -> channel.group
}

/** The spinner the original shows while the channels are loading. */
@Composable
private fun LiveTvLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** The error state: the message and a retry button. */
@Composable
private fun LiveTvError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Retry")
            }
        }
    }
}

/**
 * The "My IPTV not set up" state.
 *
 * Mirrors the original's wording: "My IPTV not set up" and "Please setup your
 * provider in Settings > Custom IPTV provider".
 */
@Composable
private fun LiveTvNotSetUp() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "My IPTV not set up",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Please setup your provider in Settings > Custom IPTV provider",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** The empty state of a provider that has no channels. */
@Composable
private fun LiveTvEmpty() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No channels found",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
