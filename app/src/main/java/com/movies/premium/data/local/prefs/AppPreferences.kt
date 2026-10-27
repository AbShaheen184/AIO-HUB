package com.movies.premium.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.movies.premium.data.repository.ProviderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cinemahq_prefs")

/**
 * User preferences. Mirrors the original's `data.local.prefs` store, which the
 * app uses for theme scale, text scale, language, provider selection and
 * debrid/trakt account tokens.
 */
class AppPreferences(private val context: Context) : ProviderPreferences {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val TEXT_SCALE = doublePreferencesKey("text_scale")
        val CARD_SCALE = doublePreferencesKey("card_scale")
        val LANGUAGE = stringPreferencesKey("language")
        val TMDB_LANGUAGE = stringPreferencesKey("tmdb_language")
        val PREFERRED_PROVIDER = stringPreferencesKey("preferred_provider")
        val TRAKT_TOKEN = stringPreferencesKey("trakt_token")
        val ALLDEBRID_TOKEN = stringPreferencesKey("alldebrid_token")
        val REALDEBRID_TOKEN = stringPreferencesKey("realdebrid_token")
        val TORBOX_TOKEN = stringPreferencesKey("torbox_token")
        val PREMIUMIZE_TOKEN = stringPreferencesKey("premiumize_token")
        val SIMKL_TOKEN = stringPreferencesKey("simkl_token")
        /**
         * The SIMKL **client id** the user registered.
         *
         * The original ships its own client id; the replica does not (it belongs to the
         * original developer), so the user has to supply theirs.
         */
        val SIMKL_CLIENT_ID = stringPreferencesKey("simkl_client_id")
        /**
         * The EasyNews account name.
         *
         * The original signs in to EasyNews with a **user name and a password**
         * (not an API key): the two values are stored in the plaintext and the
         * "signed in" state is derived from the user name being non-empty.
         */
        val EASYNEWS_USERNAME = stringPreferencesKey("easynews_username")

        /** The EasyNews account password. The original stores it in the plaintext too. */
        val EASYNEWS_PASSWORD = stringPreferencesKey("easynews_password")
        val OFFCLOUD_TOKEN = stringPreferencesKey("offcloud_token")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val ADULT_ENABLED = booleanPreferencesKey("adult_enabled")

        /**
         * The 4-digit PIN that protects the adult content.
         *
         * Mirrors the original's `adult_pin` — the original stores the four
         * characters in the plaintext, without a hash or a salt.
         */
        val ADULT_PIN = stringPreferencesKey("adult_pin")

        /** Whether the TMDB search may answer with adult titles. */
        val INCLUDE_ADULT_CONTENT = booleanPreferencesKey("include_adult_content")

        /** Whether the favourites list may show adult titles. */
        val INCLUDE_ADULT_IN_WATCHLIST = booleanPreferencesKey("include_adult_in_watchlist")

        /** Whether the watch history may show adult titles. */
        val INCLUDE_ADULT_IN_HISTORY = booleanPreferencesKey("include_adult_in_history")
        val DOWNLOAD_DIR = stringPreferencesKey("download_location")
        val SIMULTANEOUS_DOWNLOADS = intPreferencesKey("simultaneous_downloads")
        val PLAYER_ENGINE = intPreferencesKey("player_engine")

        // ---- player appearance (the original's `subtitle_*` + `player_seek_increment_ms`)
        val SUBTITLE_SIZE_SP = floatPreferencesKey("subtitle_size_sp")
        val SUBTITLE_TEXT_COLOR = intPreferencesKey("subtitle_text_color")
        val SUBTITLE_BG_COLOR = intPreferencesKey("subtitle_bg_color")
        val PLAYER_SEEK_INCREMENT_MS = longPreferencesKey("player_seek_increment_ms")
        val EXTERNAL_PLAYER = booleanPreferencesKey("external_player")

        /**
         * Whether the player starts the next episode on its own.
         *
         * Mirrors the original's `player_auto_play_next`.
         */
        val AUTO_PLAY_NEXT = booleanPreferencesKey("player_auto_play_next")
        val LAST_TMDB_ID = intPreferencesKey("last_tmdb_id")

        // ---- sidebar ordering / visibility (the original's `nav_order` + `hidden_menus`)
        val NAV_ORDER = stringPreferencesKey("nav_order")
        val HIDDEN_MENUS = stringSetPreferencesKey("hidden_menus")

        /**
         * The providers the user switched off.
         *
         * Mirrors the original's `disabled_providers` — a **string set** of the
         * provider names that are *not* to be used. The original writes it from
         * the "Sources (http)", "Sources (Torrents)" and "Sources (Anime)"
         * settings sections (`com.app.mlounge.data.repository.j` writes the whole
         * set, `com.app.mlounge.ui.viewmodel.e2` writes a single name) and reads
         * it when the catalogue is built.
         */
        val DISABLED_PROVIDERS = stringSetPreferencesKey("disabled_providers")

        // ---- external player / downloader (the original's `external_player_*`)
        val EXTERNAL_PLAYER_PACKAGE = stringPreferencesKey("external_player_package")
        val EXTERNAL_PLAYER_NAME = stringPreferencesKey("external_player_name")
        val EXTERNAL_DOWNLOADER_PACKAGE = stringPreferencesKey("external_downloader_package")
        val EXTERNAL_DOWNLOADER_NAME = stringPreferencesKey("external_downloader_name")

        // ---- display (the original's `emu_aspect_ratio` + `allow_portrait` + `ui_mode`)
        val EMU_ASPECT_RATIO = stringPreferencesKey("emu_aspect_ratio")
        val ALLOW_PORTRAIT = booleanPreferencesKey("allow_portrait")
        val UI_MODE = stringPreferencesKey("ui_mode")

        // ---- custom IPTV provider (the original's `custom_iptv_*` family)
        val CUSTOM_IPTV_TYPE = stringPreferencesKey("custom_iptv_type")
        val CUSTOM_IPTV_M3U_URL = stringPreferencesKey("custom_iptv_m3u_url")
        val CUSTOM_IPTV_EPG_URL = stringPreferencesKey("custom_iptv_epg_url")
        val CUSTOM_IPTV_XTREAM_HOST = stringPreferencesKey("custom_iptv_xtream_host")
        val CUSTOM_IPTV_XTREAM_PORT = stringPreferencesKey("custom_iptv_xtream_port")
        val CUSTOM_IPTV_XTREAM_USERNAME = stringPreferencesKey("custom_iptv_xtream_username")
        val CUSTOM_IPTV_XTREAM_PASSWORD = stringPreferencesKey("custom_iptv_xtream_password")
        val CUSTOM_IPTV_CHANNEL_COUNT = intPreferencesKey("custom_iptv_channel_count")
        val CUSTOM_IPTV_LAST_SYNC = longPreferencesKey("custom_iptv_last_sync")
        val CUSTOM_IPTV_HAS_EPG = booleanPreferencesKey("custom_iptv_has_epg")
        val CUSTOM_IPTV_XTREAM_USE_BUILTIN_EPG =
            booleanPreferencesKey("custom_iptv_xtream_use_builtin_epg")
        val CUSTOM_IPTV_LOCAL_M3U_PATH = stringPreferencesKey("custom_iptv_local_m3u_path")
        val CUSTOM_IPTV_LOCAL_EPG_PATH = stringPreferencesKey("custom_iptv_local_epg_path")
    }

    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "dark" }
    val textScale: Flow<Double> = context.dataStore.data.map { it[Keys.TEXT_SCALE] ?: 1.0 }
    val cardScale: Flow<Double> = context.dataStore.data.map { it[Keys.CARD_SCALE] ?: 1.0 }
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "en" }
    val tmdbLanguage: Flow<String> = context.dataStore.data.map { it[Keys.TMDB_LANGUAGE] ?: "en-US" }
    val preferredProvider: Flow<String> = context.dataStore.data.map { it[Keys.PREFERRED_PROVIDER] ?: "auto" }
    val traktToken: Flow<String?> = context.dataStore.data.map { it[Keys.TRAKT_TOKEN] }
    val allDebridToken: Flow<String?> = context.dataStore.data.map { it[Keys.ALLDEBRID_TOKEN] }
    val realDebridToken: Flow<String?> = context.dataStore.data.map { it[Keys.REALDEBRID_TOKEN] }
    val torboxToken: Flow<String?> = context.dataStore.data.map { it[Keys.TORBOX_TOKEN] }
    val premiumizeToken: Flow<String?> = context.dataStore.data.map { it[Keys.PREMIUMIZE_TOKEN] }
    val simklToken: Flow<String?> = context.dataStore.data.map { it[Keys.SIMKL_TOKEN] }
    /**
     * The SIMKL client id the user registered, or null when there is none.
     *
     * The original hard-codes its own client id, which belongs to the original
     * developer; the replica asks the user for theirs instead. See
     * `data/remote/simkl/SimklClientIdProvider.kt`.
     */
    val simklClientId: Flow<String?> = context.dataStore.data.map { it[Keys.SIMKL_CLIENT_ID] }
    /**
     * The EasyNews user name, or null when the user is not signed in.
     *
     * The original stores the pair in `easynews_username` / `easynews_password`
     * and derives the "signed in" state from the user name being non-empty.
     */
    val easynewsUsername: Flow<String?> =
        context.dataStore.data.map { it[Keys.EASYNEWS_USERNAME] }

    /** The EasyNews password. */
    val easynewsPassword: Flow<String?> =
        context.dataStore.data.map { it[Keys.EASYNEWS_PASSWORD] }
    val offcloudToken: Flow<String?> = context.dataStore.data.map { it[Keys.OFFCLOUD_TOKEN] }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }
    val adultEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.ADULT_ENABLED] ?: false }

    /**
     * The 4-digit PIN that protects the adult content, or null when none is set.
     *
     * Mirrors the original's `adult_pin`. The original keeps the four characters
     * in the plaintext, so the replica does the same — the value never leaves
     * the device.
     */
    val adultPin: Flow<String?> = context.dataStore.data.map { it[Keys.ADULT_PIN] }

    /** Whether the TMDB search may answer with adult titles. */
    val includeAdultInSearch: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.INCLUDE_ADULT_CONTENT] ?: false }

    /** Whether the favourites list may show adult titles. */
    val includeAdultInFavourites: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.INCLUDE_ADULT_IN_WATCHLIST] ?: false }

    /** Whether the watch history may show adult titles. */
    val includeAdultInHistory: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.INCLUDE_ADULT_IN_HISTORY] ?: false }
    /**
     * Download target. `null` means the app-private external dir
     * (`…/files/Download/CinemaHQ`); a plain path is used as-is; a `content://…` tree URI
     * is treated as a SAF directory. Mirrors the original's `download_location` key.
     */
    val downloadDir: Flow<String?> = context.dataStore.data.map { it[Keys.DOWNLOAD_DIR] }

    /** Maximum concurrent downloads. Mirrors the original's `simultaneous_downloads` (default 3). */
    val simultaneousDownloads: Flow<Int> = context.dataStore.data.map { it[Keys.SIMULTANEOUS_DOWNLOADS] ?: 3 }
    val playerEngine: Flow<Int> = context.dataStore.data.map { it[Keys.PLAYER_ENGINE] ?: 0 }
    val externalPlayer: Flow<Boolean> = context.dataStore.data.map { it[Keys.EXTERNAL_PLAYER] ?: false }
    val autoPlayNext: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_PLAY_NEXT] ?: true }

    /**
     * The size of the subtitles, in sp.
     *
     * Mirrors the original's `subtitle_size_sp`, whose default is 16.
     */
    val subtitleSizeSp: Flow<Float> =
        context.dataStore.data.map { it[Keys.SUBTITLE_SIZE_SP] ?: 16.0f }

    /**
     * The ARGB colour of the subtitle text.
     *
     * Mirrors the original's `subtitle_text_color`, whose default is `-1`
     * (`Color.WHITE`).
     */
    val subtitleTextColor: Flow<Int> =
        context.dataStore.data.map { it[Keys.SUBTITLE_TEXT_COLOR] ?: -1 }

    /**
     * The ARGB colour behind the subtitle text.
     *
     * Mirrors the original's `subtitle_bg_color`, whose default is
     * `Color.argb(128, 0, 0, 0)`.
     */
    val subtitleBgColor: Flow<Int> =
        context.dataStore.data.map { it[Keys.SUBTITLE_BG_COLOR] ?: 0x80000000.toInt() }

    /**
     * The step of the player's rewind / forward buttons, in milliseconds.
     *
     * Mirrors the original's `player_seek_increment_ms`, whose default is 30 000.
     */
    val playerSeekIncrementMs: Flow<Long> =
        context.dataStore.data.map { it[Keys.PLAYER_SEEK_INCREMENT_MS] ?: 30_000L }

    /**
     * The order the user picked for the sidebar destinations, stored as the list
     * of routes.
     *
     * Mirrors the original's `nav_order` preference, which keeps the routes as a
     * comma separated string. An empty list means "use the default order".
     */
    val navOrder: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.NAV_ORDER]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    /**
     * The titles of the sections the user has hidden from the sidebar.
     *
     * Mirrors the original's `hidden_menus` preference, which stores the section
     * titles (not the routes) of the hidden entries.
     */
    val hiddenMenus: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.HIDDEN_MENUS] ?: emptySet()
    }

    /**
     * The names of the providers the user switched off.
     *
     * Mirrors the original's `disabled_providers` preference. A provider that is
     * in the set is not asked for its sources any more.
     */
    override val disabledProviders: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.DISABLED_PROVIDERS] ?: emptySet()
    }

    /** The package name of the external video player, if one was picked. */
    val externalPlayerPackage: Flow<String?> =
        context.dataStore.data.map { it[Keys.EXTERNAL_PLAYER_PACKAGE] }

    /** The label of the external video player, if one was picked. */
    val externalPlayerName: Flow<String?> = context.dataStore.data.map { it[Keys.EXTERNAL_PLAYER_NAME] }

    /** The package name of the external downloader, if one was picked. */
    val externalDownloaderPackage: Flow<String?> =
        context.dataStore.data.map { it[Keys.EXTERNAL_DOWNLOADER_PACKAGE] }

    /** The label of the external downloader, if one was picked. */
    val externalDownloaderName: Flow<String?> =
        context.dataStore.data.map { it[Keys.EXTERNAL_DOWNLOADER_NAME] }

    /**
     * How the video is scaled inside the player.
     *
     * Mirrors the original's `emu_aspect_ratio`: `fit`, `4:3`, `16:9` or `stretch`.
     */
    val emuAspectRatio: Flow<String> = context.dataStore.data.map { it[Keys.EMU_ASPECT_RATIO] ?: "fit" }

    /** Whether the app may be used in portrait. Mirrors the original's `allow_portrait`. */
    val allowPortrait: Flow<Boolean> = context.dataStore.data.map { it[Keys.ALLOW_PORTRAIT] ?: true }

    /**
     * The interface mode: `auto`, `tv` or `mobile`.
     *
     * Mirrors the original's `ui_mode` preference, whose default is `auto`.
     */
    val uiMode: Flow<String> = context.dataStore.data.map { it[Keys.UI_MODE] ?: "auto" }

    /** The selected custom IPTV source type: `m3u`, `xtream` or `local`. */
    val customIptvType: Flow<String> = context.dataStore.data.map { it[Keys.CUSTOM_IPTV_TYPE] ?: "m3u" }

    /** The remote M3U playlist url. */
    val customIptvM3uUrl: Flow<String> = context.dataStore.data.map { it[Keys.CUSTOM_IPTV_M3U_URL] ?: "" }

    /** The remote XMLTV guide url. */
    val customIptvEpgUrl: Flow<String> = context.dataStore.data.map { it[Keys.CUSTOM_IPTV_EPG_URL] ?: "" }

    /** The Xtream Codes server host. */
    val customIptvXtreamHost: Flow<String> =
        context.dataStore.data.map { it[Keys.CUSTOM_IPTV_XTREAM_HOST] ?: "" }

    /** The Xtream Codes server port. */
    val customIptvXtreamPort: Flow<String> =
        context.dataStore.data.map { it[Keys.CUSTOM_IPTV_XTREAM_PORT] ?: "" }

    /** The Xtream Codes user name. */
    val customIptvXtreamUsername: Flow<String> =
        context.dataStore.data.map { it[Keys.CUSTOM_IPTV_XTREAM_USERNAME] ?: "" }

    /** The Xtream Codes password. */
    val customIptvXtreamPassword: Flow<String> =
        context.dataStore.data.map { it[Keys.CUSTOM_IPTV_XTREAM_PASSWORD] ?: "" }

    /** How many channels the last sync produced. */
    val customIptvChannelCount: Flow<Int> =
        context.dataStore.data.map { it[Keys.CUSTOM_IPTV_CHANNEL_COUNT] ?: 0 }

    /** When the last sync ran, in milliseconds since the epoch. */
    val customIptvLastSync: Flow<Long> = context.dataStore.data.map { it[Keys.CUSTOM_IPTV_LAST_SYNC] ?: 0L }

    /** Whether a guide was imported together with the channels. */
    val customIptvHasEpg: Flow<Boolean> = context.dataStore.data.map { it[Keys.CUSTOM_IPTV_HAS_EPG] ?: false }

    /**
     * Whether the Xtream server's own `xmltv.php` endpoint is used for the guide.
     *
     * Mirrors the original's `custom_iptv_xtream_use_builtin_epg`, which defaults
     * to `true`.
     */
    val customIptvXtreamUseBuiltinEpg: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.CUSTOM_IPTV_XTREAM_USE_BUILTIN_EPG] ?: true }

    /** The `content://` uri of the local M3U playlist. */
    val customIptvLocalM3uPath: Flow<String> =
        context.dataStore.data.map { it[Keys.CUSTOM_IPTV_LOCAL_M3U_PATH] ?: "" }

    /** The `content://` uri of the local XMLTV guide. */
    val customIptvLocalEpgPath: Flow<String> =
        context.dataStore.data.map { it[Keys.CUSTOM_IPTV_LOCAL_EPG_PATH] ?: "" }

    suspend fun setTheme(value: String) = edit { it[Keys.THEME] = value }
    suspend fun setTextScale(value: Double) = edit { it[Keys.TEXT_SCALE] = value }
    suspend fun setCardScale(value: Double) = edit { it[Keys.CARD_SCALE] = value }
    suspend fun setLanguage(value: String) = edit { it[Keys.LANGUAGE] = value }
    suspend fun setTmdbLanguage(value: String) = edit { it[Keys.TMDB_LANGUAGE] = value }
    suspend fun setPreferredProvider(value: String) = edit { it[Keys.PREFERRED_PROVIDER] = value }
    suspend fun setTraktToken(value: String?) = edit { if (value == null) it.remove(Keys.TRAKT_TOKEN) else it[Keys.TRAKT_TOKEN] = value }
    suspend fun setAllDebridToken(value: String?) = edit { if (value == null) it.remove(Keys.ALLDEBRID_TOKEN) else it[Keys.ALLDEBRID_TOKEN] = value }
    suspend fun setRealDebridToken(value: String?) = edit { if (value == null) it.remove(Keys.REALDEBRID_TOKEN) else it[Keys.REALDEBRID_TOKEN] = value }
    suspend fun setTorboxToken(value: String?) = edit { if (value == null) it.remove(Keys.TORBOX_TOKEN) else it[Keys.TORBOX_TOKEN] = value }
    suspend fun setPremiumizeToken(value: String?) = edit { if (value == null) it.remove(Keys.PREMIUMIZE_TOKEN) else it[Keys.PREMIUMIZE_TOKEN] = value }
    suspend fun setSimklToken(value: String?) = edit { if (value == null) it.remove(Keys.SIMKL_TOKEN) else it[Keys.SIMKL_TOKEN] = value }
    suspend fun setSimklClientId(value: String?) = edit { if (value == null) it.remove(Keys.SIMKL_CLIENT_ID) else it[Keys.SIMKL_CLIENT_ID] = value }
    /**
     * Stores the EasyNews account, or forgets it when a name is missing.
     *
     * Mirrors the original's `data.local.prefs.c` (case 0), which writes the
     * user name and the password in one edit and removes both keys when the
     * user name is null — that is how the original signs the user out.
     */
    suspend fun setEasynewsCredentials(username: String?, password: String?) = edit { prefs ->
        if (username.isNullOrBlank()) {
            prefs.remove(Keys.EASYNEWS_USERNAME)
            prefs.remove(Keys.EASYNEWS_PASSWORD)
        } else {
            prefs[Keys.EASYNEWS_USERNAME] = username
            if (password != null) prefs[Keys.EASYNEWS_PASSWORD] = password
        }
    }
    suspend fun setOffcloudToken(value: String?) = edit { if (value == null) it.remove(Keys.OFFCLOUD_TOKEN) else it[Keys.OFFCLOUD_TOKEN] = value }
    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.ONBOARDED] = value }
    suspend fun setAdultEnabled(value: Boolean) = edit { it[Keys.ADULT_ENABLED] = value }

    /**
     * Stores the 4-digit PIN that protects the adult content.
     *
     * Mirrors the original's `com.app.mlounge.v` (case 1), which writes the four
     * characters into `adult_pin` in the plaintext.
     */
    suspend fun setAdultPin(value: String?) =
        edit { if (value == null) it.remove(Keys.ADULT_PIN) else it[Keys.ADULT_PIN] = value }

    /** Switches the adult titles in the TMDB search on or off. */
    suspend fun setIncludeAdultInSearch(value: Boolean) =
        edit { it[Keys.INCLUDE_ADULT_CONTENT] = value }

    /** Switches the adult titles in the favourites list on or off. */
    suspend fun setIncludeAdultInFavourites(value: Boolean) =
        edit { it[Keys.INCLUDE_ADULT_IN_WATCHLIST] = value }

    /** Switches the adult titles in the watch history on or off. */
    suspend fun setIncludeAdultInHistory(value: Boolean) =
        edit { it[Keys.INCLUDE_ADULT_IN_HISTORY] = value }
    suspend fun setDownloadDir(value: String?) = edit { if (value == null) it.remove(Keys.DOWNLOAD_DIR) else it[Keys.DOWNLOAD_DIR] = value }
    suspend fun setSimultaneousDownloads(value: Int) = edit { it[Keys.SIMULTANEOUS_DOWNLOADS] = value }
    suspend fun setPlayerEngine(value: Int) = edit { it[Keys.PLAYER_ENGINE] = value }
    suspend fun setExternalPlayer(value: Boolean) = edit { it[Keys.EXTERNAL_PLAYER] = value }
    suspend fun setAutoPlayNext(value: Boolean) = edit { it[Keys.AUTO_PLAY_NEXT] = value }

    suspend fun setSubtitleSizeSp(value: Float) = edit { it[Keys.SUBTITLE_SIZE_SP] = value }

    suspend fun setSubtitleTextColor(value: Int) = edit { it[Keys.SUBTITLE_TEXT_COLOR] = value }

    suspend fun setSubtitleBgColor(value: Int) = edit { it[Keys.SUBTITLE_BG_COLOR] = value }

    suspend fun setPlayerSeekIncrementMs(value: Long) =
        edit { it[Keys.PLAYER_SEEK_INCREMENT_MS] = value }

    suspend fun setNavOrder(value: List<String>) = edit { it[Keys.NAV_ORDER] = value.joinToString(",") }

    suspend fun setHiddenMenus(value: Set<String>) = edit { it[Keys.HIDDEN_MENUS] = value }

    suspend fun setExternalPlayer(packageName: String, name: String) = edit {
        it[Keys.EXTERNAL_PLAYER_PACKAGE] = packageName
        it[Keys.EXTERNAL_PLAYER_NAME] = name
    }

    suspend fun setExternalDownloader(packageName: String, name: String) = edit {
        it[Keys.EXTERNAL_DOWNLOADER_PACKAGE] = packageName
        it[Keys.EXTERNAL_DOWNLOADER_NAME] = name
    }

    suspend fun setEmuAspectRatio(value: String) = edit { it[Keys.EMU_ASPECT_RATIO] = value }

    suspend fun setAllowPortrait(value: Boolean) = edit { it[Keys.ALLOW_PORTRAIT] = value }

    suspend fun setUiMode(value: String) = edit { it[Keys.UI_MODE] = value }

    suspend fun setCustomIptvType(value: String) = edit { it[Keys.CUSTOM_IPTV_TYPE] = value }

    suspend fun setCustomIptvM3uUrl(value: String) = edit { it[Keys.CUSTOM_IPTV_M3U_URL] = value }

    suspend fun setCustomIptvEpgUrl(value: String) = edit { it[Keys.CUSTOM_IPTV_EPG_URL] = value }

    suspend fun setCustomIptvXtreamHost(value: String) = edit { it[Keys.CUSTOM_IPTV_XTREAM_HOST] = value }

    suspend fun setCustomIptvXtreamPort(value: String) = edit { it[Keys.CUSTOM_IPTV_XTREAM_PORT] = value }

    suspend fun setCustomIptvXtreamUsername(value: String) =
        edit { it[Keys.CUSTOM_IPTV_XTREAM_USERNAME] = value }

    suspend fun setCustomIptvXtreamPassword(value: String) =
        edit { it[Keys.CUSTOM_IPTV_XTREAM_PASSWORD] = value }

    suspend fun setCustomIptvChannelCount(value: Int) = edit { it[Keys.CUSTOM_IPTV_CHANNEL_COUNT] = value }

    suspend fun setCustomIptvLastSync(value: Long) = edit { it[Keys.CUSTOM_IPTV_LAST_SYNC] = value }

    suspend fun setCustomIptvHasEpg(value: Boolean) = edit { it[Keys.CUSTOM_IPTV_HAS_EPG] = value }

    suspend fun setCustomIptvXtreamUseBuiltinEpg(value: Boolean) =
        edit { it[Keys.CUSTOM_IPTV_XTREAM_USE_BUILTIN_EPG] = value }

    suspend fun setCustomIptvLocalM3uPath(value: String) =
        edit { it[Keys.CUSTOM_IPTV_LOCAL_M3U_PATH] = value }

    suspend fun setCustomIptvLocalEpgPath(value: String) =
        edit { it[Keys.CUSTOM_IPTV_LOCAL_EPG_PATH] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    /**
     * Switches one provider on or off.
     *
     * Mirrors the original's `com.app.mlounge.ui.viewmodel.e2` (case 1), which
     * adds or removes a single name from the `disabled_providers` set.
     */
    suspend fun setProviderEnabled(name: String, enabled: Boolean) = edit { prefs ->
        val disabled = prefs[Keys.DISABLED_PROVIDERS].orEmpty().toMutableSet()
        if (enabled) disabled.remove(name) else disabled.add(name)
        prefs[Keys.DISABLED_PROVIDERS] = disabled
    }

    /**
     * Switches a whole group of providers on or off.
     *
     * Mirrors the original's `com.app.mlounge.data.repository.j` (the master
     * switch), which removes the whole list from the set when the master switch
     * is on and adds all of it when the master switch is off.
     */
    suspend fun setProvidersEnabled(names: Collection<String>, enabled: Boolean) = edit { prefs ->
        val disabled = prefs[Keys.DISABLED_PROVIDERS].orEmpty().toMutableSet()
        if (enabled) disabled.removeAll(names.toSet()) else disabled.addAll(names.toSet())
        prefs[Keys.DISABLED_PROVIDERS] = disabled
    }
}
