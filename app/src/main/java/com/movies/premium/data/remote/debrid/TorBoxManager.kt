package com.movies.premium.data.remote.debrid

import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TorBox.
 *
 * Port of the original's `com.app.mlounge.data.remote.debrid.TorBoxManager`:
 * add the magnet with a multipart body, look the torrent up, pick the video file and
 * ask for its download link.
 */
@Singleton
class TorBoxManager @Inject constructor(
    private val api: TorBoxApi,
    private val preferences: AppPreferences,
) : DebridManager {

    override val service: DebridService = DebridService.TORBOX

    override suspend fun isLoggedIn(): Boolean =
        !preferences.torboxToken.first().isNullOrBlank()

    override suspend fun logout() {
        preferences.setTorboxToken(null)
    }

    override suspend fun getUserInfo(): Result<DebridUserInfo> = debridCatching {
        val auth = authHeader() ?: error("Not logged in")
        val body = api.getUserInfo(auth)
        if (!body.success) error(body.error ?: body.detail ?: "Unknown error")
        val data = body.data ?: error("No account data")
        DebridUserInfo(
            service = DebridService.TORBOX,
            username = data.customer,
            email = data.email,
            isPremium = data.isSubscribed,
            expiresAt = data.premiumExpiresAt,
            extra = buildMap {
                put("Plan", data.plan.toString())
                put("ID", data.id.toString())
                put("Total downloaded", data.totalDownloaded.toString())
            },
        )
    }.onFailure { Logger.e(TAG, "Get user info failed", it) }

    override suspend fun unrestrictTorrent(
        magnetOrUrl: String,
        fileIndex: Int?,
    ): Result<DebridLink> = withContext(Dispatchers.IO) {
        debridCatching {
            val token = preferences.torboxToken.first()?.takeIf { it.isNotBlank() }
                ?: error("Not logged in")
            val auth = authHeader() ?: error("Not logged in")
            val hash = magnetOrUrl.infoHash()
            val magnet = magnetOrUrl.toMagnetLink()

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("magnet", magnet)
                .addFormDataPart("seed", "1")
                .addFormDataPart("allow_zip", "true")
                .build()

            val createResult = api.createTorrent(auth, body)
            // Deviation: the original's decompiled condition reads
            // `if (!createResult.success && createResult.data != null)`, which can only
            // use a torrent id the service reported as failed. The sensible equivalent
            // is used here — trust the new id when the call succeeded, and only look the
            // torrent up by hash when it did not.
            val createdId = if (createResult.success) {
                createResult.data?.torrentId
            } else {
                null
            } ?: findExistingTorrent(auth, hash)
            if (createdId == null || createdId <= 0) {
                Logger.e(TAG, "Unrestrict failed")
                error("Could not add the torrent to TorBox")
            }

            val link = getTorrentAndDownload(auth, token, fileIndex, createdId)
            if (link.isNullOrBlank()) {
                Logger.e(TAG, "Unrestrict failed")
                error("TorBox did not return a download link")
            }
            DebridLink(link)
        }
    }

    /**
     * Looks for a torrent with the same hash on the account.
     *
     * The original's `TorBoxManager.a` walks `v1/api/torrents/mylist` and compares the
     * hashes case-insensitively.
     */
    private suspend fun findExistingTorrent(auth: String, hash: String): Int? {
        if (hash.isEmpty()) return null
        val list = api.myList(auth, true)
        if (!list.success) return null
        return list.data
            ?.firstOrNull { it.hash?.equals(hash, ignoreCase = true) == true }
            ?.id
    }

    /**
     * Reads the torrent's files, picks the video file and asks TorBox for its link.
     *
     * The original's `TorBoxManager.c` picks the file at the requested index, else the
     * largest video, else the largest file.
     */
    private suspend fun getTorrentAndDownload(
        auth: String,
        apiKey: String,
        fileIdx: Int?,
        torrentId: Int,
    ): String? {
        val info = api.torrentInfo(auth, torrentId, true)
        if (!info.success) {
            Logger.e(TAG, "Unrestrict failed")
            error(info.error ?: info.detail ?: "Could not read the torrent")
        }
        val files = info.data?.files.orEmpty()
        if (files.isEmpty()) {
            Logger.e(TAG, "Unrestrict failed")
            error("TorBox returned no files")
        }
        val videoFiles = files.filter { it.isVideo() }
        val bestFile = if (fileIdx != null && fileIdx >= 0 && fileIdx < videoFiles.size) {
            videoFiles[fileIdx]
        } else {
            videoFiles.maxByOrNull { it.size } ?: files.maxByOrNull { it.size }
        } ?: return null

        val download = api.requestDownload(apiKey, torrentId, bestFile.id, false)
        if (!download.success) {
            Logger.e(TAG, "Unrestrict failed")
            error(download.error ?: download.detail ?: "TorBox refused the download")
        }
        return download.data
    }

    /** The `Bearer …` header, or null when the service is not linked. */
    private suspend fun authHeader(): String? =
        preferences.torboxToken.first()?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

    companion object {
        private const val TAG = "TorBoxManager"
    }
}
