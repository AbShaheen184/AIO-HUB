package com.movies.premium.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.iptv.ChannelLogoManager
import com.movies.premium.data.iptv.CustomIPTV
import com.movies.premium.data.iptv.EpgProgram
import com.movies.premium.data.iptv.PlaylistManager
import com.movies.premium.data.local.dao.FavoriteDao
import com.movies.premium.data.local.dao.HistoryDao
import com.movies.premium.data.local.dao.IptvChannelDao
import com.movies.premium.data.local.entity.FavoriteEntity
import com.movies.premium.data.local.entity.HistoryEntity
import com.movies.premium.data.local.entity.IptvChannelEntity
import com.movies.premium.data.remote.api.ChqNtvApi
import com.movies.premium.data.remote.api.NtvApi
import com.movies.premium.data.remote.model.IptvChannel
import com.movies.premium.data.remote.model.NtvChannel
import com.movies.premium.data.remote.model.SportsMatch
import com.movies.premium.data.remote.ntv.NtvStreamExtractor
import com.movies.premium.data.remote.ntv.NtvStreamResult
import com.movies.premium.data.remote.ntv.NtvUriParts
import com.movies.premium.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject

/**
 * The play callback the Live TV screen hands a resolved stream to.
 *
 * The original passes a `Function5` down into `ui.screens.livetv.a`; the five
 * arguments are
 *
 * 1. the stream URL,
 * 2. the display name,
 * 3. the request headers (may be `null`),
 * 4. the content type (`"hls"` for every live stream the original resolves,
 *    `null` for a plain M3U URL) and
 * 5. one extra string — the raw NTV page URL, used by the player to resolve
 *    the stream itself when needed.
 */
typealias LiveTvPlayCallback = (
    url: String,
    name: String,
    headers: Map<String, String>?,
    contentType: String?,
    extra: String?,
) -> Unit

/**
 * The provider slugs and the tab order of the Live TV screen.
 *
 * The tab list is the original's `com.app.mlounge.data.repository.t` field `u`
 * — twelve `(slug, label)` pairs, in this exact order.
 */
object LiveTvProviders {

    const val NTV = "ntv"
    const val CUSTOM = "custom"
    const val PLUTO_TV = "plutotv"
    const val IPTV_ORG = "iptvorg"
    const val EARTH_CAM = "earthcam"

    /**
     * The sports section.
     *
     * **Deviation.** The original has a fully wired "sports" branch (it loads
     * matches from the Kobra and Raptor servers and offers a source picker),
     * but the slug never appears in the twelve provider tabs, so the section is
     * unreachable in the original UI. The replica keeps the code path alive
     * and drives it with this slug.
     */
    const val SPORTS = "sports"

    /** The twelve provider tabs, in the original's order. */
    val TABS: List<Pair<String, String>> = listOf(
        NTV to "CHQ TV",
        CUSTOM to "My IPTV",
        PLUTO_TV to "Pluto TV",
        "roku" to "Roku TV",
        "xumo" to "Xumo TV",
        "tubi" to "Tubi TV",
        "yupptv" to "Yupp TV",
        "uslocal" to "US Local",
        "samsungtv" to "Samsung TV+",
        "lgtv" to "LG TV",
        IPTV_ORG to "IPTVOrg",
        EARTH_CAM to "EarthCam",
    )

    /** The NTV servers shown in the "Filter server" row. */
    val NTV_SERVERS: List<String> = listOf("dlhd", "cdnlive")

    /** The sports servers shown in the "Filter server" row. */
    val SPORTS_SERVERS: List<String> = listOf("Kobra", "Raptor")
}

/**
 * Everything the Live TV screen renders.
 *
 * The original keeps these as thirteen separate `StateFlow`s on its ViewModel
 * (`com.app.mlounge.ui.viewmodel.f0`); the replica groups them into one state
 * object, which behaves identically from the screen's point of view.
 */
data class LiveTvUiState(
    /** The slug of the selected provider tab. */
    val provider: String = LiveTvProviders.NTV,
    /** The channels currently visible (after the group filter). */
    val channels: List<IptvChannel> = emptyList(),
    /** Every channel of the current provider. */
    val allChannels: List<IptvChannel> = emptyList(),
    /** The group chips offered by the "Filter groups" row. */
    val groups: List<String> = emptyList(),
    /** The selected group, or `null` for "All". */
    val selectedGroup: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** The XMLTV guide, keyed by `tvgId`. */
    val epgPrograms: Map<String, List<EpgProgram>> = emptyMap(),
    /** `true` when "My IPTV" has never been set up. */
    val customNotSetUp: Boolean = false,
    /** The sports matches of the "sports" provider. */
    val sportsMatches: List<SportsMatch> = emptyList(),
    /** The selected sports server ("Kobra" / "Raptor"), or `null` for "All". */
    val sportsServer: String? = null,
    /** The selected NTV server ("dlhd" / "cdnlive"), or `null` for "All". */
    val ntvServer: String? = null,
    /** The name of the channel whose stream is currently being resolved. */
    val resolvingChannel: String? = null,
    /** An informational message shown in the bottom sheet. */
    val message: String? = null,
    /** The current search term. */
    val searchQuery: String = "",
)

/**
 * Backs the Live TV screen (`ui.screens.livetv.a` in the original).
 *
 * The screen is a provider tab bar plus one of four bodies:
 *
 * * **CHQ TV** (`ntv`) — the channels of the NTV provider, filtered by server
 *   ("Filter server": `dlhd` / `cdnlive`),
 * * **My IPTV** (`custom`) — the user's own M3U / Xtream channels, with the
 *   XMLTV guide when one has been imported,
 * * the eight built-in M3U providers plus Pluto TV and IPTVOrg, filtered by
 *   group ("Filter groups"), and
 * * **EarthCam** (`earthcam`) — webcams whose stream is resolved on tap.
 *
 * The original also has a sports section (Kobra / Raptor) which is not
 * reachable from its tab bar; see [LiveTvProviders.SPORTS].
 */
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: IptvChannelDao,
    private val historyDao: HistoryDao,
    private val favoriteDao: FavoriteDao,
    private val customIptv: CustomIPTV,
    private val playlistManager: PlaylistManager,
    private val channelLogoManager: ChannelLogoManager,
    private val chqNtvApi: ChqNtvApi,
    private val ntvApi: NtvApi,
    private val ntvExtractor: NtvStreamExtractor,
) : SafeViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    /** One-shot messages; the original shows them with `Toast`. */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Keeps the visible channel list in sync with the database. */
    private var channelsJob: Job? = null

    init {
        refresh()
    }

    // -----------------------------------------------------------------------
    // Filters
    // -----------------------------------------------------------------------

    /**
     * Selects a provider tab.
     *
     * Mirrors the original's tab handler: the group filter and the sports
     * server are cleared, the sports match list is emptied, the NTV server is
     * reset when the NTV tab is selected and the provider is reloaded.
     */
    fun selectProvider(slug: String) {
        Logger.d(TAG, "provider=$slug")
        Logger.d(TAG, "livetv $slug")

        val state = _uiState.value
        _uiState.value = state.copy(
            provider = slug,
            selectedGroup = null,
            sportsServer = null,
            sportsMatches = emptyList(),
            ntvServer = if (slug == LiveTvProviders.NTV) null else state.ntvServer,
            customNotSetUp = if (slug != LiveTvProviders.CUSTOM) false else state.customNotSetUp,
            epgPrograms = if (slug != LiveTvProviders.CUSTOM) emptyMap() else state.epgPrograms,
            error = null,
            message = null,
        )
        channelsJob?.cancel()
        refresh()
    }

    /**
     * Selects a group ("All" passes `null`).
     *
     * Mirrors the original's `k()`: Pluto TV and IPTVOrg filter the in-memory
     * list, every other provider re-queries the database.
     */
    fun selectGroup(group: String?) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
        applyGroupFilter()
    }

    /** Selects an NTV server ("All" passes `null`) and reloads the channels. */
    fun selectNtvServer(server: String?) {
        _uiState.value = _uiState.value.copy(ntvServer = server)
        refresh()
    }

    /** Selects a sports server ("All" passes `null`). */
    fun selectSportsServer(server: String?) {
        _uiState.value = _uiState.value.copy(sportsServer = server)
    }

    /** Sets the search term and reloads the current provider. */
    fun setSearchQuery(query: String) {
        if (_uiState.value.searchQuery == query) return
        _uiState.value = _uiState.value.copy(searchQuery = query)
        refresh()
    }

    /** Dismisses the message shown in the bottom sheet. */
    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    // -----------------------------------------------------------------------
    // Loading
    // -----------------------------------------------------------------------

    /** Reloads the current provider. */
    fun refresh() {
        scope.launch { load() }
    }

    private suspend fun load() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        try {
            when (val provider = _uiState.value.provider) {
                LiveTvProviders.NTV -> loadNtv()
                LiveTvProviders.SPORTS -> loadSports()
                LiveTvProviders.CUSTOM -> {
                    if (isCustomConfigured()) {
                        loadProvider(provider)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            customNotSetUp = true,
                            channels = emptyList(),
                            allChannels = emptyList(),
                            groups = emptyList(),
                            epgPrograms = emptyMap(),
                        )
                    }
                }
                else -> loadProvider(provider)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load channels", e)
            _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to load channels")
        } finally {
            _uiState.value = _uiState.value.copy(loading = false)
        }
    }

    /**
     * `true` when the user has set up a custom provider.
     *
     * **Deviation.** The original inspects the stored custom-provider settings
     * (`custom_iptv_type` plus the M3U URL / Xtream host / local file path).
     * The replica has no such preferences yet, so "set up" is approximated by
     * "the database holds at least one `custom` channel".
     */
    private suspend fun isCustomConfigured(): Boolean =
        channelDao.countForProvider(LiveTvProviders.CUSTOM) > 0

    /**
     * Loads a database-backed provider.
     *
     * Mirrors the original's `g()`: Pluto TV and IPTVOrg derive their groups
     * from the channels themselves, every other provider uses the
     * `SELECT DISTINCT groupName` query. "My IPTV" preselects the first group.
     */
    private suspend fun loadProvider(provider: String) {
        val query = _uiState.value.searchQuery.trim()

        if (provider == LiveTvProviders.PLUTO_TV || provider == LiveTvProviders.IPTV_ORG) {
            val rows = channelDao.getChannels(provider, null)
            val channels = rows.map { it.toChannel() }.filterByQuery(query)
            val groups = providerGroups(provider, channels)
            _uiState.value = _uiState.value.copy(
                allChannels = channels,
                channels = channels,
                groups = if (groups.size <= 1) emptyList() else groups,
                selectedGroup = null,
            )
            observeChannels(provider, null)
            return
        }

        val groups = channelDao.getGroups(provider)
            .filterNotNull()
            .filter { it.isNotBlank() }
        val selected = if (provider == LiveTvProviders.CUSTOM) groups.firstOrNull() else null
        val all = channelDao.getChannels(provider, null).map { it.toChannel() }.filterByQuery(query)
        val visible = if (selected == null) {
            all
        } else {
            channelDao.getChannels(provider, selected).map { it.toChannel() }.filterByQuery(query)
        }
        _uiState.value = _uiState.value.copy(
            groups = groups,
            selectedGroup = selected,
            allChannels = all,
            channels = visible,
        )
        if (provider == LiveTvProviders.CUSTOM && selected != null) {
            loadEpg(visible)
        }
        observeChannels(provider, selected)
    }

    /**
     * Re-applies the group filter.
     *
     * Mirrors the original's `k()`.
     */
    fun applyGroupFilter() {
        val state = _uiState.value
        val provider = state.provider
        val selected = state.selectedGroup

        when {
            provider == LiveTvProviders.PLUTO_TV || provider == LiveTvProviders.IPTV_ORG -> {
                val filtered = state.allChannels.filter { channel ->
                    when {
                        selected == null -> true
                        provider == LiveTvProviders.PLUTO_TV ->
                            countryCode(channel.group) == selected
                        else -> splitGroups(channel.group).contains(selected)
                    }
                }
                _uiState.value = state.copy(channels = filtered)
            }

            provider == LiveTvProviders.NTV || provider == LiveTvProviders.SPORTS -> {
                val filtered = if (selected == null) {
                    state.allChannels
                } else {
                    state.allChannels.filter { it.group == selected }
                }
                _uiState.value = state.copy(channels = filtered)
            }

            else -> {
                scope.launch {
                    val rows = channelDao.getChannels(provider, selected)
                    val channels = rows.map { it.toChannel() }.filterByQuery(state.searchQuery.trim())
                    _uiState.value = _uiState.value.copy(channels = channels)
                    observeChannels(provider, selected)
                    if (provider == LiveTvProviders.CUSTOM && selected != null) {
                        loadEpg(channels)
                    }
                }
            }
        }
    }

    /**
     * Observes the database rows of the current provider + group.
     *
     * The original collects the same Room flow, so the list keeps up with a
     * playlist that is refreshed in the background.
     */
    private fun observeChannels(provider: String, group: String?) {
        channelsJob?.cancel()
        channelsJob = scope.launch {
            channelDao.observeChannels(provider, group).collect { rows ->
                val state = _uiState.value
                val all = rows.map { it.toChannel() }.filterByQuery(state.searchQuery.trim())
                if (provider == LiveTvProviders.PLUTO_TV || provider == LiveTvProviders.IPTV_ORG) {
                    val selected = state.selectedGroup
                    val filtered = all.filter { channel ->
                        when {
                            selected == null -> true
                            provider == LiveTvProviders.PLUTO_TV ->
                                countryCode(channel.group) == selected
                            else -> splitGroups(channel.group).contains(selected)
                        }
                    }
                    val groups = providerGroups(provider, all)
                    _uiState.value = state.copy(
                        allChannels = all,
                        channels = filtered,
                        groups = if (groups.size <= 1) emptyList() else groups,
                    )
                } else {
                    _uiState.value = state.copy(channels = all)
                }
            }
        }
    }

    /**
     * Loads the XMLTV guide of the visible channels.
     *
     * Mirrors the original's `f()`: the guide is only available for the
     * custom provider, because that is the only one with a stored EPG file.
     */
    private fun loadEpg(channels: List<IptvChannel>) {
        val ids = channels.mapNotNull { it.tvgId }.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty() || !customIptv.hasCustomEpg()) return
        scope.launch {
            val programs = withContext(Dispatchers.IO) { customIptv.loadEpgFor(ids) }
            _uiState.value = _uiState.value.copy(epgPrograms = programs)
        }
    }

    /**
     * Loads the NTV channels.
     *
     * Mirrors the original's `h()`: every requested server is queried in
     * parallel, the result is filtered by the search term and mapped to the
     * `ntv://` scheme.
     */
    private suspend fun loadNtv() {
        val servers = listOfNotNull(_uiState.value.ntvServer).ifEmpty { LiveTvProviders.NTV_SERVERS }
        val query = _uiState.value.searchQuery.trim()

        val fetched = supervisorScope {
            servers.map { server ->
                async {
                    try {
                        chqNtvApi.getChannels(server).channels.orEmpty()
                    } catch (e: Exception) {
                        Logger.e(TAG, "NTV server $server failed: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        val filtered = fetched.filter { channel ->
            query.isEmpty() || channel.channelName?.contains(query, ignoreCase = true) == true
        }
        val mapped = filterAdultChannels(filtered.mapNotNull { it.toChannel() })
        val groups = mapped.mapNotNull { it.group }.distinct().sorted()
        _uiState.value = _uiState.value.copy(
            allChannels = mapped,
            channels = mapped,
            groups = if (groups.size <= 1) emptyList() else groups,
            selectedGroup = null,
        )
    }

    /**
     * Loads the sports matches.
     *
     * Mirrors the original's `i()`: the Kobra server contributes its *live*
     * matches, the Raptor server its full list, and both are tagged with the
     * server name.
     *
     * **Deviation.** The original sorts the matches with an obfuscated
     * comparator that could not be recovered; the replica sorts by start date.
     */
    private suspend fun loadSports() {
        val kobra = try {
            ntvApi.getMatches("kobra", "both")
        } catch (e: Exception) {
            Logger.e(TAG, "Sports (kobra) failed: ${e.message}")
            null
        }
        val raptor = try {
            ntvApi.getMatches("raptor", "both")
        } catch (e: Exception) {
            Logger.e(TAG, "Sports (raptor) failed: ${e.message}")
            null
        }
        val matches = buildList {
            kobra?.live.orEmpty().forEach { add(it.copy(server = "Kobra")) }
            raptor?.all.orEmpty().forEach { add(it.copy(server = "Raptor")) }
        }.sortedBy { it.date ?: 0L }

        _uiState.value = _uiState.value.copy(
            sportsMatches = matches,
            channels = emptyList(),
            allChannels = emptyList(),
            groups = emptyList(),
            selectedGroup = null,
        )
    }

    // -----------------------------------------------------------------------
    // Playback
    // -----------------------------------------------------------------------

    /**
     * Handles a tap on a channel.
     *
     * Mirrors the original's channel click handler: the channel is written to
     * the watch history, an `ntv://` URL is resolved first, an EarthCam page
     * is resolved first and every other channel is handed to the player as-is.
     */
    fun onChannelClick(channel: IptvChannel, onPlay: LiveTvPlayCallback) {
        saveHistory(channel)
        val url = channel.url.orEmpty()
        val name = channel.name.orEmpty()

        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "This channel has no stream URL")
            return
        }
        if (url.startsWith(NtvUriParts.SCHEME)) {
            resolveNtvChannel(url, onPlay)
            return
        }
        if (channel.provider == LiveTvProviders.EARTH_CAM) {
            resolveEarthCam(channel, onPlay)
            return
        }
        onPlay(url, name, null, null, null)
    }

    /**
     * Handles a long press on a channel: the channel is added to the
     * favourites, exactly like the original.
     */
    fun onChannelLongPress(channel: IptvChannel) {
        val name = channel.name.orEmpty()
        scope.launch {
            val key = historyKey(channel)
            favoriteDao.upsert(
                FavoriteEntity(
                    tmdbId = stableId(key),
                    mediaType = MEDIA_TYPE_LIVE_TV,
                    title = name,
                    posterPath = channel.logo,
                    overview = "${channel.group.orEmpty()} - ${channel.provider.orEmpty()}",
                ),
            )
            _events.emit("$name added to favourites")
        }
    }

    /**
     * Resolves an NTV channel and hands the stream to [onPlay].
     *
     * Mirrors the original's `d0` / `h`: the resolved URL, the name, the
     * headers, `"hls"` and the raw NTV page URL are passed on.
     */
    private fun resolveNtvChannel(url: String, onPlay: LiveTvPlayCallback) {
        val parts = NtvUriParts.parse(url)
        if (parts == null) {
            _uiState.value = _uiState.value.copy(message = "This channel has no stream URL")
            return
        }
        scope.launch {
            val embed = parts.rawUrl.takeIf { it.isNotBlank() }
            val result = try {
                ntvExtractor.resolve(
                    context = context,
                    name = parts.channelName,
                    code = parts.channelCode,
                    provider = parts.server,
                    streamId = parts.channelId,
                    embedUrl = embed,
                )
            } catch (e: Exception) {
                Logger.e(TAG, "NTV resolve failed for ${parts.channelName}", e)
                null
            }
            if (result == null) {
                _events.emit("Failed to resolve stream for ${parts.channelName}")
            } else {
                onPlay(result.url, parts.channelName, result.headers, "hls", embed)
            }
        }
    }

    /**
     * Resolves an EarthCam webcam and hands the stream to [onPlay].
     *
     * Mirrors the original's `c0`: the channel name is shown while the stream
     * is being resolved and a toast reports a failure.
     */
    private fun resolveEarthCam(channel: IptvChannel, onPlay: LiveTvPlayCallback) {
        val name = channel.name.orEmpty()
        scope.launch {
            _uiState.value = _uiState.value.copy(resolvingChannel = name)
            try {
                val result = playlistManager.earthCamProvider.resolveStream(channel.url.orEmpty())
                if (result == null) {
                    _events.emit("Failed to resolve stream for $name")
                } else {
                    onPlay(result.url, name, result.headers, "hls", null)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "EarthCam resolve failed for $name", e)
                _events.emit("Error resolving $name: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(resolvingChannel = null)
            }
        }
    }

    /**
     * Plays a sports match from the given source.
     *
     * Mirrors the original's `o()`: only the Kobra and Raptor servers are
     * accepted, the stream is resolved and then handed to the player.
     */
    fun playMatch(
        match: SportsMatch,
        sourceId: String,
        sourceUrl: String?,
        onPlay: LiveTvPlayCallback,
    ) {
        val server = match.server?.lowercase(Locale.ROOT).orEmpty()
        if (server != "kobra" && server != "raptor") {
            _events.tryEmit("Unsupported server: ${match.server}")
            return
        }
        val name = match.title.orEmpty()
        scope.launch {
            val url = sourceUrl?.takeIf { it.isNotBlank() }
            val result = try {
                if (url != null && url.contains(".m3u8")) {
                    // The source is already a playlist — play it directly.
                    NtvStreamResult(url, emptyMap())
                } else {
                    ntvExtractor.resolveSports(context, name, sourceId, url)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Sports resolve failed for $name", e)
                null
            }
            if (result == null) {
                _events.emit("Failed to resolve stream for $name")
            } else {
                onPlay(result.url, name, result.headers, "hls", null)
            }
        }
    }

    // -----------------------------------------------------------------------
    // History / favourites
    // -----------------------------------------------------------------------

    /**
     * Writes the channel to the watch history.
     *
     * Mirrors the original's history writer: the key is
     * `livetv_<name>_<provider>` and the media type is `live_tv`.
     */
    private fun saveHistory(channel: IptvChannel) {
        val name = channel.name.orEmpty()
        scope.launch {
            historyDao.upsert(
                HistoryEntity(
                    key = historyKey(channel),
                    tmdbId = 0,
                    mediaType = MEDIA_TYPE_LIVE_TV,
                    title = name,
                    posterPath = channel.logo,
                ),
            )
        }
    }

    private fun historyKey(channel: IptvChannel): String =
        "livetv_${channel.name.orEmpty()}_${channel.provider.orEmpty()}"

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun IptvChannelEntity.toChannel(): IptvChannel = IptvChannel(
        name = name,
        logo = logo,
        url = url,
        group = groupName,
        tvgId = tvgId,
        provider = provider,
        fallbackLogo = null,
    )

    private fun List<IptvChannel>.filterByQuery(query: String): List<IptvChannel> =
        if (query.isEmpty()) {
            this
        } else {
            filter { it.name?.contains(query, ignoreCase = true) == true }
        }

    /**
     * The groups of a provider.
     *
     * Pluto TV groups its channels by country code ("US - News"), IPTVOrg by
     * the ";"-separated list of its groups.
     */
    private fun providerGroups(provider: String, channels: List<IptvChannel>): List<String> =
        if (provider == LiveTvProviders.PLUTO_TV) {
            channels.mapNotNull { countryCode(it.group) }.distinct().sorted()
        } else {
            channels.flatMap { splitGroups(it.group) }.distinct().sorted()
        }

    /**
     * Maps an NTV channel onto the `ntv://` scheme.
     *
     * Mirrors the original's `m()`: the URL is
     * `ntv://<name>|<code>|<server>|<channelId>[|<url encoded page url>]`, the
     * logo comes from the logo manifest and falls back to the channel image,
     * and the group is the server.
     */
    private suspend fun NtvChannel.toChannel(): IptvChannel? {
        val name = channelName?.takeIf { it.isNotBlank() } ?: return null
        val code = channelCode.orEmpty()
        val serverSlug = server?.takeIf { it.isNotBlank() } ?: "cdnlive"
        val pageUrl = channelUrl.orEmpty()
        val isEmbed = pageUrl.contains("livelive24.com")

        // The original drops "hesgoales" channels that have no livelive24 embed.
        if (serverSlug == "hesgoales" && !isEmbed) return null

        val uri = buildString {
            append(NtvUriParts.SCHEME)
            append(name)
            append('|')
            append(code)
            append('|')
            append(serverSlug)
            append('|')
            append(channelId.orEmpty())
            if (pageUrl.isNotBlank()) {
                append('|')
                append(URLEncoder.encode(pageUrl, "UTF-8"))
            }
        }

        val logo = channelLogoManager.getLogo(name)
        return IptvChannel(
            name = name,
            logo = logo?.first ?: channelImage?.replace("dlhd.link", "dlhd.st"),
            url = uri,
            group = serverSlug,
            tvgId = channelId,
            provider = LiveTvProviders.NTV,
            fallbackLogo = logo?.second,
        )
    }

    /**
     * The country code of a Pluto TV group ("US - News" -> "US").
     *
     * Mirrors the original's `n()`.
     */
    private fun countryCode(group: String?): String? {
        val value = group?.substringBefore(" - ")?.trim().orEmpty()
        return value.ifEmpty { null }
    }

    /**
     * The ";"-separated groups of an IPTVOrg channel.
     *
     * Mirrors the original's `l()`.
     */
    private fun splitGroups(group: String?): List<String> =
        group.orEmpty().split(";").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Drops the adult channels.
     *
     * The original applies this keyword list to the NTV channel list. The
     * decompiled control flow of that filter is ambiguous (both branches of the
     * decompiled loop end up adding the channel), so the replica implements the
     * evident intent: a channel whose **name or group** contains one of the
     * keywords is dropped.
     */
    private fun filterAdultChannels(channels: List<IptvChannel>): List<IptvChannel> =
        channels.filterNot { channel ->
            val name = channel.name?.uppercase(Locale.ROOT).orEmpty()
            val group = channel.group?.uppercase(Locale.ROOT).orEmpty()
            ADULT_KEYWORDS.any { name.contains(it) || group.contains(it) }
        }

    /** A stable positive id for a live-TV history/favourite key. */
    private fun stableId(key: String): Int = key.hashCode() and 0x7FFFFFFF

    companion object {
        const val TAG = "LiveTv"

        /** The media type the original stores live-TV history and favourites under. */
        const val MEDIA_TYPE_LIVE_TV = "live_tv"

        /** The keywords the original uses to weed out adult channels. */
        val ADULT_KEYWORDS: List<String> =
            listOf("18+", "XXX", "ADULT", "PORN", "EROTIC", "SEX")
    }
}
