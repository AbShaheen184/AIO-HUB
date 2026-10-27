package com.movies.premium.data.remote.debrid

import com.movies.premium.data.remote.model.ChqStream
import com.movies.premium.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The registry of the five debrid managers.
 *
 * The original keeps the managers in its streams repository
 * (`com.app.mlounge.data.repository.t`) and picks one of them when a torrent source is
 * played; the replica does the same through this registry.
 */
@Singleton
class DebridManagerRegistry @Inject constructor(
    private val realDebridManager: RealDebridManager,
    private val allDebridManager: AllDebridManager,
    private val premiumizeManager: PremiumizeManager,
    private val torBoxManager: TorBoxManager,
    private val offCloudManager: OffCloudManager,
) {

    /** Every manager, in the enum's order. */
    val managers: List<DebridManager> = listOf(
        realDebridManager,
        allDebridManager,
        premiumizeManager,
        torBoxManager,
        offCloudManager,
    )

    /** The manager of a service. */
    fun managerFor(service: DebridService): DebridManager? = managers.firstOrNull { it.service == service }

    /**
     * The services the user has linked.
     *
     * The original's repository only offers the services whose token is present, and
     * the sources screen lists exactly those in its service picker.
     */
    suspend fun linkedServices(): List<DebridService> {
        val linked = mutableListOf<DebridService>()
        for (manager in managers) {
            if (debridCatching { manager.isLoggedIn() }.getOrDefault(false)) {
                linked.add(manager.service)
            }
        }
        return linked
    }

    /**
     * The service to use when the user did not pick one.
     *
     * The original's repository walks Real-Debrid, AllDebrid, Premiumize, TorBox and
     * Offcloud and takes the first one that is logged in.
     */
    suspend fun autoSelect(): DebridService? {
        for (service in DebridService.AUTO_ORDER) {
            val manager = managerFor(service) ?: continue
            if (debridCatching { manager.isLoggedIn() }.getOrDefault(false)) return service
        }
        return null
    }

    /**
     * Turns a torrent source into a direct link.
     *
     * @param service the service to use, or null to let the registry pick one.
     * @param stream the source the user picked.
     */
    suspend fun unrestrict(service: DebridService?, stream: ChqStream): Result<DebridLink> {
        val manager = service?.let { managerFor(it) }
            ?: autoSelect()?.let { managerFor(it) }
            ?: return Result.failure(NoLinkedDebridServiceException())

        val magnet = stream.url.orEmpty().ifBlank { stream.infoHash.orEmpty() }
        if (magnet.isBlank()) {
            return Result.failure(IllegalArgumentException("The source has no magnet link"))
        }
        Logger.d(TAG, "Unrestricting with ${manager.service.displayName}")
        return manager.unrestrictTorrent(magnet, stream.fileIdx)
    }

    companion object {
        private const val TAG = "DebridManagerRegistry"
    }
}

/** Raised when a torrent is played but no debrid service is linked. */
class NoLinkedDebridServiceException :
    IllegalStateException("No debrid service is linked. Link one in Settings to play torrents.")
