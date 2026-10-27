package com.movies.premium.data.remote.debrid

import com.movies.premium.data.repository.ServiceId
import kotlinx.coroutines.CancellationException

/**
 * The five premium-host / debrid services the original links to.
 *
 * The display names are the original's, verbatim: the original's enum
 * `com.app.mlounge.ui.viewmodel.l` carries `PREMIUMIZE("Premiumize")`,
 * `TORBOX("TorBox")`, `ALL_DEBRID("AllDebrid")`, `REAL_DEBRID("Real-Debrid")` and
 * `OFFCLOUD("Offcloud")`.
 */
enum class DebridService(val displayName: String) {

    PREMIUMIZE("Premiumize"),
    TORBOX("TorBox"),
    ALL_DEBRID("AllDebrid"),
    REAL_DEBRID("Real-Debrid"),
    OFFCLOUD("Offcloud"),
    ;

    /** The service's entry in the replica's [ServiceId] list. */
    val serviceId: ServiceId
        get() = when (this) {
            PREMIUMIZE -> ServiceId.PREMIUMIZE
            TORBOX -> ServiceId.TORBOX
            ALL_DEBRID -> ServiceId.ALLDEBRID
            REAL_DEBRID -> ServiceId.REALDEBRID
            OFFCLOUD -> ServiceId.OFFCLOUD
        }

    companion object {
        /**
         * The order the original picks a service in when the user has not chosen one.
         *
         * The original's streams repository (`com.app.mlounge.data.repository.t`) walks
         * its managers in this order and takes the first one that is logged in:
         * Real-Debrid, AllDebrid, Premiumize, TorBox, Offcloud.
         */
        val AUTO_ORDER: List<DebridService> = listOf(
            REAL_DEBRID,
            ALL_DEBRID,
            PREMIUMIZE,
            TORBOX,
            OFFCLOUD,
        )
    }
}

/**
 * A playable link a debrid service handed back.
 *
 * The original returns a bare `String` (the direct URL) from its managers; the replica
 * also carries the request headers the player has to send along, because the CHQ
 * backend's per-source headers still apply to the resolved link.
 */
data class DebridLink(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * The account details a debrid service reports.
 *
 * The field names mirror the ones `ServicesRepository` reads for the same services, so
 * the two layers can be shown with the same rows.
 */
data class DebridUserInfo(
    val service: DebridService,
    val username: String? = null,
    val email: String? = null,
    val isPremium: Boolean = false,
    /** Raw expiry as the service reported it (epoch seconds or an ISO date). */
    val expiresAt: String? = null,
    /** Any extra rows the service reported (points, plan, storage, …). */
    val extra: Map<String, String> = emptyMap(),
)

/**
 * The common surface of the five debrid managers.
 *
 * The original's managers (`AllDebridManager`, `RealDebridManager`, `TorBoxManager`,
 * `PremiumizeManager`, `OffCloudManager`) each expose their own
 * `isLoggedIn` / `getUserInfo` / `logout` / `unrestrictTorrent` methods; the replica
 * gives them a shared interface so the sources screen does not have to know which
 * service it is talking to.
 */
interface DebridManager {

    /** The service this manager talks to. */
    val service: DebridService

    /** True when a token is stored for the service. */
    suspend fun isLoggedIn(): Boolean

    /** The account details, or a failure with a short human-readable reason. */
    suspend fun getUserInfo(): Result<DebridUserInfo>

    /**
     * Turns a magnet (or a bare info hash) into a direct download link.
     *
     * @param magnetOrUrl a `magnet:` link or a bare BTIH hash.
     * @param fileIndex the file inside the torrent to pick, or null to pick the largest
     *   one.
     */
    suspend fun unrestrictTorrent(magnetOrUrl: String, fileIndex: Int? = null): Result<DebridLink>

    /** Forgets the stored token. */
    suspend fun logout()
}

/**
 * Builds the magnet link for a hash, the way the original does.
 *
 * The original prefixes a bare hash with `magnet:?xt=urn:btih:` in every manager
 * (`AllDebridManager`, `TorBoxManager`, `PremiumizeManager`, …).
 */
internal fun String.toMagnetLink(): String {
    val value = trim()
    if (value.startsWith("magnet:", ignoreCase = true)) return value
    val hash = value.infoHash()
    return if (hash.isNotEmpty()) "magnet:?xt=urn:btih:$hash" else value
}

/**
 * Extracts the BTIH hash out of a magnet link or a bare hash.
 *
 * The original does the same in `com.app.mlounge.data.repository.t`:
 * `url.substringAfter("btih:").substringBefore("&")`.
 */
internal fun String.infoHash(): String {
    val value = trim()
    if (value.startsWith("magnet:", ignoreCase = true)) {
        return value.substringAfter("btih:", "").substringBefore("&").trim()
    }
    // A bare 40 character hex hash, or a 32 character base32 one.
    if (value.length == 40 || value.length == 32) return value
    return value.substringAfter("btih:", "").substringBefore("&").trim()
}

/** The file extensions the original treats as video when it picks a file. */
internal val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
    "mpg", "mpeg", "ts", "m2ts", "3gp", "ogv",
)

/**
 * [runCatching] that lets a cancellation through.
 *
 * The managers poll a service for minutes at a time; a cancelled coroutine has to
 * stay cancelled instead of turning into a failed [Result].
 */
internal inline fun <T> debridCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
