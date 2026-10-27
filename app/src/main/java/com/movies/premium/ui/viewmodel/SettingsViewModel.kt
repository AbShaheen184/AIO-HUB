package com.movies.premium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.BuildConfig
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.ui.screens.settings.SidebarDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val textScale: Double = 1.0,
    val cardScale: Double = 1.0,
    val adultEnabled: Boolean = false,
    val autoPlayNext: Boolean = true,
    val externalPlayer: Boolean = false,
    val preferredProvider: String = "auto",
    val versionName: String = BuildConfig.VERSION_NAME,
)

/**
 * An external app the user picked in the settings.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.i0`
 * (`SupportedPlayer(packageName=…, displayName=…)`), which the original uses
 * for both the video players and the downloaders.
 */
data class ExternalApp(
    val packageName: String,
    val displayName: String,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
) : SafeViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.textScale,
        preferences.cardScale,
        preferences.adultEnabled,
        preferences.autoPlayNext,
        preferences.externalPlayer,
        preferences.preferredProvider,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val scale = values[0] as Double
        val card = values[1] as Double
        val adult = values[2] as Boolean
        val autoNext = values[3] as Boolean
        val external = values[4] as Boolean
        val provider = values[5] as String
        SettingsUiState(
            textScale = scale,
            cardScale = card,
            adultEnabled = adult,
            autoPlayNext = autoNext,
            externalPlayer = external,
            preferredProvider = provider,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTextScale(value: Float) = scope.launch { preferences.setTextScale(value.toDouble()) }
    fun setCardScale(value: Float) = scope.launch { preferences.setCardScale(value.toDouble()) }
    fun setAdultEnabled(value: Boolean) = scope.launch { preferences.setAdultEnabled(value) }
    fun setAutoPlayNext(value: Boolean) = scope.launch { preferences.setAutoPlayNext(value) }
    fun setExternalPlayer(value: Boolean) = scope.launch { preferences.setExternalPlayer(value) }

    // ---------------------------------------------------------------------------
    // The adult section
    //
    // Mirrors the original's `com.app.mlounge.ui.screens.settings.m` (the four
    // rows of the "Content" section) and `com.app.mlounge.ui.components.x` (the
    // PIN dialog). Switching the adult section on is gated by the PIN: the
    // original asks for the PIN — or asks the user to create one — before it
    // writes `adult_enabled`.
    // ---------------------------------------------------------------------------

    /**
     * The 4-digit PIN that protects the adult content, or null when none is set.
     *
     * The original keeps the four characters in the plaintext in the
     * `adult_pin` preference.
     */
    val adultPin: StateFlow<String?> = preferences.adultPin
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether the TMDB search may answer with adult titles. */
    val includeAdultInSearch: StateFlow<Boolean> = preferences.includeAdultInSearch
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the favourites list may show adult titles. */
    val includeAdultInFavourites: StateFlow<Boolean> = preferences.includeAdultInFavourites
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the watch history may show adult titles. */
    val includeAdultInHistory: StateFlow<Boolean> = preferences.includeAdultInHistory
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /** Stores the 4-digit PIN that protects the adult content. */
    fun setAdultPin(value: String) = scope.launch { preferences.setAdultPin(value) }

    /** Switches the adult titles in the TMDB search on or off. */
    fun setIncludeAdultInSearch(value: Boolean) =
        scope.launch { preferences.setIncludeAdultInSearch(value) }

    /** Switches the adult titles in the favourites list on or off. */
    fun setIncludeAdultInFavourites(value: Boolean) =
        scope.launch { preferences.setIncludeAdultInFavourites(value) }

    /** Switches the adult titles in the watch history on or off. */
    fun setIncludeAdultInHistory(value: Boolean) =
        scope.launch { preferences.setIncludeAdultInHistory(value) }

    // ---------------------------------------------------------------------------
    // Sidebar ordering and visibility
    //
    // Mirrors the original's `com.app.mlounge.ui.viewmodel.f2` (SettingsViewModel),
    // `com.app.mlounge.ui.viewmodel.q1` (reorder) and `com.app.mlounge.ui.viewmodel.e2`
    // (show / hide). The original keeps the order in the `nav_order` preference and
    // the hidden sections in the `hidden_menus` preference.
    // ---------------------------------------------------------------------------

    /**
     * The routes of the sidebar destinations, in the order the user picked.
     *
     * An empty list means the default order is used. The original's
     * `nav_order` preference stores the routes comma separated; the replica's
     * `AppPreferences` splits them back into a list.
     */
    val navOrder: StateFlow<List<String>> = preferences.navOrder
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The titles of the sidebar sections the user has hidden.
     *
     * Mirrors the original's `hidden_menus` preference, which stores the section
     * titles (not the routes).
     */
    val hiddenMenus: StateFlow<Set<String>> = preferences.hiddenMenus
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Moves a sidebar destination one step up or down.
     *
     * Mirrors the original's `com.app.mlounge.ui.viewmodel.q1`, which reads the
     * stored order, swaps the entry with its neighbour and writes it back.
     */
    fun moveNavItem(route: String, up: Boolean) = scope.launch {
        val base = SidebarDestinations.map { it.route }
        val stored = preferences.navOrder.first()
        // The original appends the destinations that are missing from the stored
        // order to the end of the list, so newly added sections never disappear.
        val order = if (stored.isEmpty()) base else stored + base.filter { it !in stored }
        val index = order.indexOf(route)
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in order.indices) return@launch
        val next = order.toMutableList()
        next[index] = order[target]
        next[target] = route
        preferences.setNavOrder(next)
    }

    /**
     * Shows or hides a sidebar section.
     *
     * Mirrors the original's `com.app.mlounge.ui.viewmodel.e2`, which adds or
     * removes the section title from the `hidden_menus` set.
     */
    fun toggleNavItem(title: String) = scope.launch {
        val hidden = preferences.hiddenMenus.first().toMutableSet()
        if (!hidden.add(title)) hidden.remove(title)
        preferences.setHiddenMenus(hidden)
    }

    // ---------------------------------------------------------------------------
    // External player / downloader, display
    // ---------------------------------------------------------------------------

    /** The external video player the user picked, if any. */
    val externalPlayerApp: StateFlow<ExternalApp?> = combine(
        preferences.externalPlayerPackage,
        preferences.externalPlayerName,
    ) { pkg, name -> pkg?.takeIf { it.isNotBlank() }?.let { ExternalApp(it, name ?: it) } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** The external downloader the user picked, if any. */
    val externalDownloaderApp: StateFlow<ExternalApp?> = combine(
        preferences.externalDownloaderPackage,
        preferences.externalDownloaderName,
    ) { pkg, name -> pkg?.takeIf { it.isNotBlank() }?.let { ExternalApp(it, name ?: it) } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * How the video is scaled inside the player.
     *
     * Mirrors the original's `emu_aspect_ratio` preference: `fit`, `4:3`,
     * `16:9` or `stretch`.
     */
    val emuAspectRatio: StateFlow<String> = preferences.emuAspectRatio
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), "fit")

    /** Whether the app may be used in portrait. Mirrors the original's `allow_portrait`. */
    val allowPortrait: StateFlow<Boolean> = preferences.allowPortrait
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * The interface mode: `auto`, `tv` or `mobile`.
     *
     * Mirrors the original's `ui_mode` preference.
     */
    val uiMode: StateFlow<String> = preferences.uiMode
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), "auto")

    /**
     * The folder the downloads are written to.
     *
     * Mirrors the original's `download_location` preference, whose default is
     * `Downloads/CinemaHQ`.
     */
    val downloadDir: StateFlow<String?> = preferences.downloadDir
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDownloadDir(value: String?) = scope.launch { preferences.setDownloadDir(value) }

    fun setExternalPlayerApp(packageName: String, name: String) =
        scope.launch { preferences.setExternalPlayer(packageName, name) }

    fun setExternalDownloaderApp(packageName: String, name: String) =
        scope.launch { preferences.setExternalDownloader(packageName, name) }

    fun setEmuAspectRatio(value: String) = scope.launch { preferences.setEmuAspectRatio(value) }

    fun setAllowPortrait(value: Boolean) = scope.launch { preferences.setAllowPortrait(value) }
    fun setUiMode(value: String) = scope.launch { preferences.setUiMode(value) }

    // ---------------------------------------------------------------------------
    // The "Sources (http)" / "Sources (Torrents)" / "Sources (Anime)" sections
    //
    // Mirrors the original's `com.app.mlounge.ui.screens.settings.h0` (the three
    // collapsible sections), `com.app.mlounge.ui.viewmodel.f2` (the lists) and
    // `com.app.mlounge.ui.viewmodel.e2` (the per-source switch). A provider that
    // is switched off goes into the `disabled_providers` preference and is not
    // asked for its sources any more.
    // ---------------------------------------------------------------------------

    /** The providers the user switched off. */
    val disabledProviders: StateFlow<Set<String>> = preferences.disabledProviders
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Whether at least one torrent / debrid service is connected.
     *
     * The original only shows the "Sources (Torrents)" section when one of the
     * five tokens (`premiumize_token`, `torbox_token`, `alldebrid_token`,
     * `real_debrid_token`, `offcloud_token`) is set — the gate lives in the
     * original's `prefs/k0` and is read as `f2.J`.
     */
    val hasTorrentAccount: StateFlow<Boolean> = combine(
        preferences.premiumizeToken,
        preferences.torboxToken,
        preferences.allDebridToken,
        preferences.realDebridToken,
        preferences.offcloudToken,
    ) { tokens ->
        tokens.any { !it.isNullOrBlank() }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /** Switches one provider on or off. */
    fun setProviderEnabled(name: String, enabled: Boolean) =
        scope.launch { preferences.setProviderEnabled(name, enabled) }

    /** Switches a whole group of providers on or off. */
    fun setProvidersEnabled(names: List<String>, enabled: Boolean) =
        scope.launch { preferences.setProvidersEnabled(names, enabled) }
}
