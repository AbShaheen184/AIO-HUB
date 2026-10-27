package com.movies.premium.data.iptv

import com.movies.premium.data.remote.api.ChqNtvApi
import com.movies.premium.data.remote.model.LogoManifestItem
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a channel name to its logo URL.
 *
 * Mirrors the original's `com.app.mlounge.data.iptv.d` (R8 name `d`): it
 * downloads the CHQ logo manifest once, keeps it in memory behind a
 * [Mutex] and answers lookups from the cached map.
 *
 * The manifest entry carries both a `source` (remote URL) and a `file`
 * (bundled file name); the returned pair is `(sourceUrl, fileUrl)` where the
 * second element is `null` when it is identical to the first.
 */
@Singleton
class ChannelLogoManager @Inject constructor(
    private val chqNtvApi: ChqNtvApi,
) {
    private val mutex = Mutex()
    private val manifestMap = LinkedHashMap<String, LogoManifestItem>()

    @Volatile
    private var isLoaded = false

    /** Normalises a channel name for manifest lookups. */
    fun normalize(name: String): String =
        name.lowercase(Locale.ROOT).replace(Regex("\\s+"), "_")

    /**
     * Returns `(sourceUrl, fileUrl)` for [name], or `null` when the manifest
     * has no entry for it. Loads the manifest on first use.
     */
    suspend fun getLogo(name: String): Pair<String?, String?>? {
        return withContext(Dispatchers.IO) {
            loadManifest()
            lookup(name)
        }
    }

    /**
     * Returns `(sourceUrl, fileUrl)` for [name] from the already-loaded
     * manifest without triggering a download.
     */
    fun lookup(name: String): Pair<String?, String?>? {
        val item = manifestMap[normalize(name)] ?: return null
        var source = item.source?.takeIf { it.isNotBlank() }
        val file = item.file?.takeIf { it.isNotBlank() }
        if (source == null && file != null) source = file
        val second = if (file != null && file != source) file else null
        return Pair(source, second)
    }

    private suspend fun loadManifest() {
        if (isLoaded) return
        mutex.lock()
        try {
            val response = chqNtvApi.getLogoManifest()
            val items = response.items ?: emptyList()
            manifestMap.clear()
            for (item in items) {
                val channel = item.channel
                if (!channel.isNullOrBlank()) {
                    manifestMap[normalize(channel)] = item
                }
            }
            isLoaded = true
            Logger.d(TAG, "Loaded logo manifest with ${manifestMap.size} channel mappings")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load logo manifest: ${e.message}")
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        const val TAG = "ChannelLogoManager"
    }
}
