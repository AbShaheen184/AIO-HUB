package com.movies.premium.data.remote.debrid

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-Debrid.
 *
 * Port of the original's `com.app.mlounge.data.remote.debrid.RealDebridManager`:
 * add the magnet, wait for the torrent to be cached, pick the file, select it and
 * unrestrict the link.
 */
@Singleton
class RealDebridManager @Inject constructor(
    private val api: RealDebridApi,
    private val preferences: AppPreferences,
) : DebridManager {

    override val service: DebridService = DebridService.REAL_DEBRID

    override suspend fun isLoggedIn(): Boolean =
        !preferences.realDebridToken.first().isNullOrBlank()

    override suspend fun logout() {
        preferences.setRealDebridToken(null)
    }

    override suspend fun getUserInfo(): Result<DebridUserInfo> = debridCatching {
        val auth = authHeader() ?: error("Not logged in")
        val body = api.getUser(auth)
        val info = Gson().fromJson(body, RealDebridUserInfo::class.java)
        DebridUserInfo(
            service = DebridService.REAL_DEBRID,
            username = info.username,
            email = info.email,
            isPremium = info.premium > 0,
            expiresAt = info.expiration,
            extra = buildMap {
                put("Points", info.points.toString())
                put("Type", info.type.orEmpty())
                put("ID", info.id.toString())
            },
        )
    }.onFailure { Logger.e(TAG, "getUserInfo failed", it) }

    override suspend fun unrestrictTorrent(
        magnetOrUrl: String,
        fileIndex: Int?,
    ): Result<DebridLink> = withContext(Dispatchers.IO) {
        debridCatching {
            val auth = authHeader() ?: error("Not logged in")
            val magnet = magnetOrUrl.toMagnetLink()

            val addResponse = api.addMagnet(auth, magnet)
            val torrentId = addResponse.jsonString("id")
                ?: error("addMagnet returned no torrent id")

            // Wait for the torrent to be cached.
            var info = api.torrentInfo(auth, torrentId)
            var status = info.jsonString("status")
            var attempts = 0
            while (status != STATUS_DOWNLOADED && attempts < POLL_MAX_ATTEMPTS) {
                delay(POLL_DELAY_MS)
                info = api.torrentInfo(auth, torrentId)
                status = info.jsonString("status")
                attempts++
            }
            if (status != STATUS_DOWNLOADED) {
                Logger.e(TAG, "Torrent $torrentId never became downloaded")
                error("Torrent $torrentId never became downloaded")
            }

            val files = info.jsonArray("files")
            if (files == null || files.isEmpty()) {
                Logger.e(TAG, "No files found in torrent $torrentId")
                error("No files found in torrent $torrentId")
            }

            // Pick the file: the requested index, else the largest one.
            val file = if (fileIndex != null && fileIndex >= 0 && fileIndex < files.size()) {
                files.get(fileIndex).asJsonObject
            } else {
                files.asSequence()
                    .map { it.asJsonObject }
                    .maxByOrNull { it.jsonLong("bytes") }
            } ?: error("No files found in torrent $torrentId")
            val fileId = file.jsonInt("id", -1)
            if (fileId < 0) error("Torrent file has no id")

            val select = api.selectFiles(auth, torrentId, fileId.toString())
            if (!select.isSuccessful) {
                Logger.e(TAG, "selectFiles failed: ${select.code()}")
                error("selectFiles failed: ${select.code()}")
            }

            // The links only appear once the file is selected.
            var links: List<String> = emptyList()
            var linkAttempts = 0
            while (links.isEmpty() && linkAttempts < POLL_MAX_ATTEMPTS) {
                val refreshed = api.torrentInfo(auth, torrentId)
                links = refreshed.jsonArray("links")
                    ?.asSequence()
                    // The host link is the one that has to be unrestricted; `download`
                    // only exists once the link was unrestricted.
                    ?.mapNotNull { it.asJsonObject.jsonString("link") ?: it.asJsonObject.jsonString("download") }
                    ?.toList()
                    .orEmpty()
                if (links.isEmpty()) {
                    delay(POLL_DELAY_MS)
                    linkAttempts++
                }
            }
            val hostLink = links.firstOrNull() ?: error("Torrent $torrentId has no links")

            val unrestricted = api.unrestrictLink(auth, hostLink)
            val download = unrestricted.jsonString("download")
            val link = download ?: unrestricted.jsonString("link")
            if (link.isNullOrBlank()) {
                Logger.e(TAG, "unrestrictLink failed")
                error("unrestrictLink failed")
            }
            DebridLink(link)
        }
    }

    /** The `Bearer …` header, or null when the service is not linked. */
    private suspend fun authHeader(): String? =
        preferences.realDebridToken.first()?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

    companion object {
        private const val TAG = "RealDebridManager"
        private const val POLL_DELAY_MS = 5000L
        private const val POLL_MAX_ATTEMPTS = 60
        private const val STATUS_DOWNLOADED = "downloaded"
    }
}
