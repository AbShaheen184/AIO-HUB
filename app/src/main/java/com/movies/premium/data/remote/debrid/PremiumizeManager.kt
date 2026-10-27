package com.movies.premium.data.remote.debrid

import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Premiumize.
 *
 * Port of the original's `com.app.mlounge.data.remote.debrid.PremiumizeManager`:
 * ask for a direct download of the magnet; when that does not work, start a transfer
 * and read the link out of the transfer's folder.
 */
@Singleton
class PremiumizeManager @Inject constructor(
    private val api: PremiumizeApi,
    private val preferences: AppPreferences,
) : DebridManager {

    override val service: DebridService = DebridService.PREMIUMIZE

    override suspend fun isLoggedIn(): Boolean =
        !preferences.premiumizeToken.first().isNullOrBlank()

    override suspend fun logout() {
        preferences.setPremiumizeToken(null)
    }

    override suspend fun getUserInfo(): Result<DebridUserInfo> = debridCatching {
        val apiKey = apiKey() ?: error("Not logged in")
        val info = api.accountInfo(apiKey)
        if (info.status != "success") error(info.message ?: "Unknown error")
        DebridUserInfo(
            service = DebridService.PREMIUMIZE,
            username = info.customerId,
            isPremium = info.isPremium(),
            expiresAt = info.premiumUntil?.toString(),
            extra = buildMap {
                put("Limit used", info.limitUsed?.toString().orEmpty())
                put("Space used", info.spaceUsed?.toString().orEmpty())
            },
        )
    }.onFailure { Logger.e(TAG, "Get user info failed", it) }

    override suspend fun unrestrictTorrent(
        magnetOrUrl: String,
        fileIndex: Int?,
    ): Result<DebridLink> = withContext(Dispatchers.IO) {
        debridCatching {
            val apiKey = apiKey() ?: error("Not logged in")
            val magnet = magnetOrUrl.toMagnetLink()

            // The fast path: Premiumize resolves most magnets straight away.
            val direct = api.directDownload(apiKey, magnet)
            val content = direct.content
            if (direct.isSuccess() && content != null && content.isNotEmpty()) {
                val picked = pickContent(content, fileIndex)
                val link = picked?.link ?: picked?.streamLink
                if (!link.isNullOrBlank()) return@debridCatching DebridLink(link)
            }

            // The slow path: create a transfer and read the file out of its folder.
            val transfer = api.createTransfer(apiKey, magnet)
            if (!transfer.isSuccess()) {
                Logger.e(TAG, "Unrestrict failed")
                error(transfer.message ?: "Premiumize could not create a transfer")
            }
            val transferId = transfer.id ?: error("Premiumize returned no transfer id")
            val link = getTransferDownloadLink(apiKey, transferId, fileIndex)
            if (link.isNullOrBlank()) {
                Logger.e(TAG, "Unrestrict failed")
                error("Premiumize returned no download link")
            }
            DebridLink(link)
        }
    }

    /**
     * Finds the file of a transfer and returns its link.
     *
     * The original's `PremiumizeManager.a` reads `transfer/list` to find the transfer's
     * folder and file, then reads the folder's listing.
     */
    private suspend fun getTransferDownloadLink(
        apiKey: String,
        transferId: String,
        fileIdx: Int?,
    ): String? {
        val transfers = api.transferList(apiKey).transfers.orEmpty()
        val transfer = transfers.firstOrNull { it.id == transferId }
        val folderId = transfer?.folderId
        val fileId = transfer?.fileId

        val files = if (folderId != null) {
            api.folderList(apiKey, folderId).content.orEmpty()
        } else {
            emptyList()
        }
        if (files.isEmpty()) return null

        val picked = if (fileIdx != null && fileIdx >= 0 && fileIdx < files.size) {
            files[fileIdx]
        } else {
            files.filter { it.isVideo() }.maxByOrNull { it.size ?: 0L }
                ?: files.maxByOrNull { it.size ?: 0L }
        } ?: return null

        // The file id the transfer reported wins when it is on the account.
        if (fileId != null) {
            val byId = files.firstOrNull { it.id == fileId }
            if (byId != null) return byId.link ?: byId.streamLink
        }
        return picked.link ?: picked.streamLink
    }

    /**
     * Picks the file of a `transfer/directdl` response.
     *
     * The original filters the video files out of the response and falls back to the
     * largest file when there is no video at all.
     */
    private fun pickContent(content: List<PremiumizeContent>, fileIdx: Int?): PremiumizeContent? {
        if (content.isEmpty()) return null
        if (fileIdx != null && fileIdx >= 0 && fileIdx < content.size) return content[fileIdx]
        return content.filter { it.isVideo() }.maxByOrNull { it.size ?: 0L }
            ?: content.maxByOrNull { it.size ?: 0L }
    }

    /** Premiumize authenticates with the api key in the query string. */
    private suspend fun apiKey(): String? =
        preferences.premiumizeToken.first()?.takeIf { it.isNotBlank() }

    companion object {
        private const val TAG = "PremiumizeManager"
    }
}
