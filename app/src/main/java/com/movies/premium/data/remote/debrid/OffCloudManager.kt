package com.movies.premium.data.remote.debrid

import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offcloud.
 *
 * Port of the original's `com.app.mlounge.data.remote.debrid.OffCloudManager`:
 * ask for the cached files of the magnet; when it is not cached, start an instant
 * download and read the files out of the finished download.
 */
@Singleton
class OffCloudManager @Inject constructor(
    private val api: OffCloudApi,
    private val preferences: AppPreferences,
) : DebridManager {

    override val service: DebridService = DebridService.OFFCLOUD

    override suspend fun isLoggedIn(): Boolean =
        !preferences.offcloudToken.first().isNullOrBlank()

    override suspend fun logout() {
        preferences.setOffcloudToken(null)
    }

    override suspend fun getUserInfo(): Result<DebridUserInfo> = debridCatching {
        val auth = authHeader() ?: error("Not logged in")
        val info = api.accountInfo(auth)
        DebridUserInfo(
            service = DebridService.OFFCLOUD,
            email = info.email,
            isPremium = info.isPremium == true,
            expiresAt = info.expirationDate,
            extra = buildMap {
                put("User ID", info.userId.orEmpty())
                put("Can download", if (info.canDownload == true) "Yes" else "No")
            },
        )
    }.onFailure { Logger.e(TAG, "Get user info failed", it) }

    override suspend fun unrestrictTorrent(
        magnetOrUrl: String,
        fileIndex: Int?,
    ): Result<DebridLink> = withContext(Dispatchers.IO) {
        debridCatching {
            val auth = authHeader() ?: error("Not logged in")
            val magnet = magnetOrUrl.toMagnetLink()

            // Fast path: the torrent is already in the cache.
            val cached = pickFromCacheDownload(auth, magnet, fileIndex)
            if (!cached.isNullOrBlank()) return@debridCatching DebridLink(cached)

            // Slow path: start an instant download and wait for it.
            val created = api.createCloudDownload(auth, mapOf("url" to magnet))
            val requestId = created.requestId ?: error("Offcloud returned no request id")
            val status = waitForCloudDownload(auth, requestId)
            if (status == null) {
                Logger.e(TAG, "Explore failed")
                error("Offcloud did not finish the download")
            }
            if (status.status != "completed") {
                Logger.e(TAG, "Explore failed")
                error(status.message ?: "Offcloud could not cache the torrent")
            }
            val link = pickFromExplore(auth, requestId, fileIndex)
            if (link.isNullOrBlank()) {
                Logger.e(TAG, "Explore failed")
                error("Offcloud returned no download link")
            }
            DebridLink(link)
        }
    }

    /**
     * The files of an already cached torrent.
     *
     * The original's `OffCloudManager.f` posts the magnet to `api/cache/download` and
     * picks the file out of the answer.
     */
    private suspend fun pickFromCacheDownload(
        auth: String,
        magnet: String,
        fileIdx: Int?,
    ): String? {
        val files = try {
            api.cacheDownload(auth, mapOf("url" to magnet))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Cache download failed", e)
            return null
        }
        val usable = files.filter { !it.url.isNullOrBlank() }
        if (usable.isEmpty()) return null
        val picked = pickFile(usable, fileIdx) ?: return null
        return picked.url
    }

    /**
     * The files of a finished instant download.
     *
     * The original's `OffCloudManager.g` reads `api/cloud/explore/{requestId}` with the
     * `detailed` format.
     */
    private suspend fun pickFromExplore(
        auth: String,
        requestId: String,
        fileIdx: Int?,
    ): String? {
        val files = try {
            api.explore(auth, requestId, "detailed").files.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Explore failed", e)
            return null
        }
        val usable = files
            .filter { !it.url.isNullOrBlank() }
            .map { OffCloudCacheDownloadFile(filename = it.name, size = it.size, url = it.url) }
        if (usable.isEmpty()) return null
        val picked = pickFile(usable, fileIdx) ?: return null
        return picked.url
    }

    /**
     * Picks the file to play.
     *
     * The original's `OffCloudManager.h` keeps the files with a link, prefers the video
     * ones, and takes the requested index or the largest file.
     */
    private fun pickFile(files: List<OffCloudCacheDownloadFile>, fileIdx: Int?): OffCloudCacheDownloadFile? {
        if (files.isEmpty()) return null
        if (fileIdx != null && fileIdx >= 0 && fileIdx < files.size) return files[fileIdx]
        return files.filter { isVideoFile(it.filename) }.maxByOrNull { it.size ?: 0L }
            ?: files.maxByOrNull { it.size ?: 0L }
    }

    /** Polls `api/cloud/status` until the download is done. */
    private suspend fun waitForCloudDownload(auth: String, requestId: String): OffCloudCloudStatus? {
        repeat(MAX_POLLS) {
            val status = api.cloudStatus(auth, mapOf("requestId" to requestId)).status
            if (status != null && status.status != null && status.status != "running") {
                return status
            }
            delay(POLL_INTERVAL_MS)
        }
        return null
    }

    /** The `Bearer …` header, or null when the service is not linked. */
    private suspend fun authHeader(): String? =
        preferences.offcloudToken.first()?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

    companion object {
        private const val TAG = "OffCloudManager"
        private const val MAX_POLLS = 24
        private const val POLL_INTERVAL_MS = 5000L

        /** True when the file name ends in one of the video extensions. */
        internal fun isVideoFile(name: String?): Boolean {
            val lower = name?.lowercase() ?: return false
            val extension = lower.substringAfterLast('.', "")
            return extension in VIDEO_EXTENSIONS
        }
    }
}
