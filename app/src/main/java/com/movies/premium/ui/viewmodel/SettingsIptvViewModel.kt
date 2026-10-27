package com.movies.premium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.iptv.CustomIPTV
import com.movies.premium.data.iptv.XmlTvParser
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The source types the custom IPTV provider supports.
 *
 * Mirrors the original's `com.app.mlounge.ui.viewmodel.k`, whose source type
 * default is `"m3u"`.
 */
object IptvSourceType {
    const val M3U = "m3u"
    const val XTREAM = "xtream"
    const val LOCAL = "local"
}

/**
 * The state of the "Custom IPTV Provider" form.
 *
 * Mirrors the original's `com.app.mlounge.ui.viewmodel.k`, whose state flow
 * fields are the source type, the channel count, the last sync timestamp, the
 * "has EPG" flag, the local m3u / epg paths, the "use built-in EPG" flag, the
 * syncing flag, the status line and the error.
 */
data class IptvSettingsUiState(
    val sourceType: String = IptvSourceType.M3U,
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val xtreamHost: String = "",
    val xtreamPort: String = "",
    val xtreamUsername: String = "",
    val xtreamPassword: String = "",
    val useBuiltinEpg: Boolean = true,
    val localM3uPath: String = "",
    val localEpgPath: String = "",
    val channelCount: Int = 0,
    val lastSync: Long = 0L,
    val hasEpg: Boolean = false,
    val isSyncing: Boolean = false,
    val status: String = "",
    val error: String? = null,
)

/**
 * Drives the "Custom IPTV Provider" form.
 *
 * Mirrors the original's `com.app.mlounge.ui.viewmodel.k`, which owns the
 * `custom_iptv_*` preferences and pushes the channels into the IPTV layer
 * (the original's `com.app.mlounge.data.iptv.p`, the replica's
 * [CustomIPTV]).
 */
@HiltViewModel
class SettingsIptvViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val customIptv: CustomIPTV,
) : SafeViewModel() {

    private val _state = MutableStateFlow(IptvSettingsUiState())
    val state: StateFlow<IptvSettingsUiState> = _state.asStateFlow()

    init {
        scope.launch {
            _state.update {
                it.copy(
                    sourceType = preferences.customIptvType.first(),
                    m3uUrl = preferences.customIptvM3uUrl.first(),
                    epgUrl = preferences.customIptvEpgUrl.first(),
                    xtreamHost = preferences.customIptvXtreamHost.first(),
                    xtreamPort = preferences.customIptvXtreamPort.first(),
                    xtreamUsername = preferences.customIptvXtreamUsername.first(),
                    xtreamPassword = preferences.customIptvXtreamPassword.first(),
                    useBuiltinEpg = preferences.customIptvXtreamUseBuiltinEpg.first(),
                    localM3uPath = preferences.customIptvLocalM3uPath.first(),
                    localEpgPath = preferences.customIptvLocalEpgPath.first(),
                    channelCount = preferences.customIptvChannelCount.first(),
                    lastSync = preferences.customIptvLastSync.first(),
                    hasEpg = preferences.customIptvHasEpg.first(),
                )
            }
        }
    }

    fun setSourceType(value: String) {
        _state.update { it.copy(sourceType = value) }
        scope.launch { preferences.setCustomIptvType(value) }
    }

    fun setM3uUrl(value: String) {
        _state.update { it.copy(m3uUrl = value) }
        scope.launch { preferences.setCustomIptvM3uUrl(value) }
    }

    fun setEpgUrl(value: String) {
        _state.update { it.copy(epgUrl = value) }
        scope.launch { preferences.setCustomIptvEpgUrl(value) }
    }

    fun setXtreamHost(value: String) {
        _state.update { it.copy(xtreamHost = value) }
        scope.launch { preferences.setCustomIptvXtreamHost(value) }
    }

    fun setXtreamPort(value: String) {
        _state.update { it.copy(xtreamPort = value) }
        scope.launch { preferences.setCustomIptvXtreamPort(value) }
    }

    fun setXtreamUsername(value: String) {
        _state.update { it.copy(xtreamUsername = value) }
        scope.launch { preferences.setCustomIptvXtreamUsername(value) }
    }

    fun setXtreamPassword(value: String) {
        _state.update { it.copy(xtreamPassword = value) }
        scope.launch { preferences.setCustomIptvXtreamPassword(value) }
    }

    fun setUseBuiltinEpg(value: Boolean) {
        _state.update { it.copy(useBuiltinEpg = value) }
        scope.launch { preferences.setCustomIptvXtreamUseBuiltinEpg(value) }
    }

    fun setLocalM3uPath(value: String) {
        _state.update { it.copy(localM3uPath = value) }
        scope.launch { preferences.setCustomIptvLocalM3uPath(value) }
    }

    fun setLocalEpgPath(value: String) {
        _state.update { it.copy(localEpgPath = value) }
        scope.launch { preferences.setCustomIptvLocalEpgPath(value) }
    }

    /**
     * Downloads the playlist / connects to the server and writes the channels
     * into the IPTV layer.
     *
     * Mirrors the original's sync routine in `com.app.mlounge.ui.viewmodel.k`.
     */
    fun sync() = scope.launch {
        val current = _state.value
        if (current.isSyncing) return@launch
        _state.update { it.copy(isSyncing = true, error = null, status = "") }
        try {
            var count = 0
            var hasEpg = false
            when (current.sourceType) {
                IptvSourceType.XTREAM -> {
                    val port = current.xtreamPort.takeIf { it.isNotBlank() }
                    val channels = customIptv.importXtream(
                        host = current.xtreamHost,
                        port = port,
                        username = current.xtreamUsername,
                        password = current.xtreamPassword,
                    ) { message -> _state.update { it.copy(status = message) } }
                    count = channels.size
                    if (current.useBuiltinEpg) {
                        val epg = customIptv.importRemoteEpg(
                            host = current.xtreamHost,
                            port = port,
                            username = current.xtreamUsername,
                            password = current.xtreamPassword,
                        ) { message -> _state.update { it.copy(status = message) } }
                        hasEpg = epg.isNotEmpty()
                    }
                }

                IptvSourceType.LOCAL -> {
                    count = customIptv.importLocalM3u(current.localM3uPath) { message ->
                        _state.update { it.copy(status = message) }
                    }
                    if (current.localEpgPath.isNotBlank()) {
                        val epg = customIptv.importLocalEpg(current.localEpgPath) { message ->
                            _state.update { it.copy(status = message) }
                        }
                        hasEpg = epg.isNotEmpty()
                    }
                }

                else -> {
                    count = customIptv.importRemoteM3u(current.m3uUrl) { message ->
                        _state.update { it.copy(status = message) }
                    }
                    if (current.epgUrl.isNotBlank()) {
                        val guide = XmlTvParser.parse(
                            customIptv.fetchText(current.epgUrl).byteInputStream(),
                        )
                        if (guide.isNotEmpty()) {
                            customIptv.saveEpg(guide)
                            hasEpg = true
                        }
                    }
                }
            }
            val now = System.currentTimeMillis()
            preferences.setCustomIptvChannelCount(count)
            preferences.setCustomIptvLastSync(now)
            preferences.setCustomIptvHasEpg(hasEpg)
            _state.update {
                it.copy(
                    isSyncing = false,
                    channelCount = count,
                    lastSync = now,
                    hasEpg = hasEpg,
                    status = "",
                    error = null,
                )
            }
            Logger.d(TAG, "IPTV sync finished: $count channels, epg=$hasEpg")
        } catch (e: Exception) {
            Logger.e(TAG, "IPTV sync failed: ${e.message}", e)
            _state.update { it.copy(isSyncing = false, error = e.message ?: "Unknown error") }
        }
    }

    /**
     * Removes every custom channel and the stored guide.
     *
     * Mirrors the original's "Clear" button, which calls
     * `com.app.mlounge.data.iptv.p.clearCustom()`.
     */
    fun clear() = scope.launch {
        try {
            customIptv.clearCustom()
            preferences.setCustomIptvChannelCount(0)
            preferences.setCustomIptvLastSync(0L)
            preferences.setCustomIptvHasEpg(false)
            _state.update {
                it.copy(
                    channelCount = 0,
                    lastSync = 0L,
                    hasEpg = false,
                    status = "",
                    error = null,
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Clearing the custom IPTV provider failed: ${e.message}", e)
            _state.update { it.copy(error = e.message ?: "Unknown error") }
        }
    }

    private companion object {
        const val TAG = "SettingsIptvViewModel"
    }
}
