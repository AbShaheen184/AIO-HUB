package com.movies.premium.data.remote.debrid

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AllDebrid.
 *
 * Port of the original's `com.app.mlounge.data.remote.debrid.AllDebridManager`:
 * upload the magnet, poll `v4.1/magnet/status` until the magnet is ready (status 4),
 * read the file tree from `v4/magnet/files`, pick the file (the requested index, or the
 * largest one) and unlock it with `v4/link/unlock`.
 */
@Singleton
class AllDebridManager @Inject constructor(
    private val api: AllDebridApi,
    private val preferences: AppPreferences,
) : DebridManager {

    override val service: DebridService = DebridService.ALL_DEBRID

    override suspend fun isLoggedIn(): Boolean =
        !preferences.allDebridToken.first().isNullOrBlank()

    override suspend fun logout() {
        preferences.setAllDebridToken(null)
    }

    override suspend fun getUserInfo(): Result<DebridUserInfo> = debridCatching {
        val auth = authHeader() ?: error("Not logged in")
        val body = api.getUser(auth)
        val user = body.jsonObject("data")?.jsonObject("user")
            ?: error("Unexpected response")
        val info = Gson().fromJson(user, AllDebridUserInfo::class.java)
        DebridUserInfo(
            service = DebridService.ALL_DEBRID,
            username = info.username,
            email = info.email,
            isPremium = info.isPremium,
            expiresAt = info.premiumUntil.takeIf { it > 0L }?.toString(),
            extra = buildMap {
                put("Subscription", if (info.isSubscribed) "Yes" else "No")
                put("Trial", if (info.isTrial) "Yes" else "No")
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

            val upload = api.uploadMagnet(auth, magnet)
            if (upload.jsonString("status") != "success") {
                Logger.e(TAG, "uploadMagnet failed: $upload")
                error("uploadMagnet failed")
            }
            val first = upload.jsonObject("data")
                ?.jsonArray("magnets")
                ?.firstOrNull()
                ?.asJsonObject
                ?: error("uploadMagnet returned no magnet")
            val magnetId = first.get("id").asInt

            if (!pollMagnetStatus(auth, magnetId)) {
                Logger.e(TAG, "Magnet $magnetId never became ready")
                error("Magnet $magnetId never became ready")
            }

            val filesResponse = api.getMagnetFiles(auth, magnetId)
            if (filesResponse.jsonString("status") != "success") {
                Logger.e(TAG, "getMagnetFiles failed: $filesResponse")
                error("getMagnetFiles failed")
            }
            val filesTree = filesResponse.jsonObject("data")
                ?.jsonArray("magnets")
                ?.firstOrNull()
                ?.asJsonObject
                ?.jsonArray("files")
            val allFiles = mutableListOf<Pair<Long, String>>()
            if (filesTree != null) flattenFiles(filesTree, allFiles)
            if (allFiles.isEmpty()) {
                Logger.e(TAG, "No files found in magnet $magnetId")
                error("No files found in magnet $magnetId")
            }

            val fileLink = if (fileIndex != null && fileIndex >= 0 && fileIndex < allFiles.size) {
                allFiles[fileIndex].second
            } else {
                allFiles.maxByOrNull { it.first }?.second
                    ?: error("No files found in magnet $magnetId")
            }

            val unlock = api.unlockLink(auth, fileLink)
            val link = unlock.jsonObject("data")?.jsonString("link")
            if (link.isNullOrBlank()) {
                Logger.e(TAG, "unlockLink failed: $unlock")
                error("unlockLink failed")
            }
            DebridLink(link)
        }
    }

    /** The `Bearer …` header, or null when the service is not linked. */
    private suspend fun authHeader(): String? =
        preferences.allDebridToken.first()?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

    /**
     * Polls `v4.1/magnet/status` until the magnet reports the "ready" status.
     *
     * The original polls every 5 seconds, at most 60 times.
     *
     * Deviation: the original's `pollMagnetStatus` did not decompile, so the response
     * shape is read defensively — AllDebrid reports the status either directly under
     * `data.status` or under `data.magnets[0].status`.
     */
    private suspend fun pollMagnetStatus(auth: String, magnetId: Int): Boolean {
        repeat(POLL_MAX_ATTEMPTS) {
            val body = api.getMagnetStatus(auth, magnetId)
            val data = body.jsonObject("data")
            val status = when {
                data == null -> -1
                data.jsonInt("status", -1) != -1 -> data.jsonInt("status", -1)
                else -> data.jsonArray("magnets")
                    ?.firstOrNull()
                    ?.asJsonObject
                    ?.jsonInt("status", -1)
                    ?: -1
            }
            if (status == STATUS_READY) return true
            delay(POLL_DELAY_MS)
        }
        return false
    }

    companion object {
        private const val TAG = "AllDebridManager"
        private const val POLL_DELAY_MS = 5000L
        private const val POLL_MAX_ATTEMPTS = 60
        private const val STATUS_READY = 4

        /**
         * Flattens AllDebrid's file tree.
         *
         * A node with a link (`l`) is a file, a node with children (`e`) is a folder.
         * The original's `AllDebridManager.a` does the same and keeps the size (`s`)
         * next to the link so the largest file can be picked.
         */
        internal fun flattenFiles(nodes: JsonArray, out: MutableList<Pair<Long, String>>) {
            for (node in nodes) {
                val entry = node.asJsonObject ?: continue
                val link = entry.jsonString("l")
                if (link != null) {
                    out.add((entry.get("s")?.asLong ?: 0L) to link)
                } else {
                    val children = entry.jsonArray("e") ?: continue
                    flattenFiles(children, out)
                }
            }
        }
    }
}
