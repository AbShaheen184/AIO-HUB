package com.movies.premium.data.remote.trakt

import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Trakt.tv client.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.trakt.TraktManager`: the token
 * lives in the preferences store, every call carries the `Authorization: Bearer …`
 * header and the `trakt-api-version: 2` header, and the watchlist / history are read
 * and written through the `sync/…` endpoints.
 *
 * **Deviation — the original's OAuth client credentials are not shipped.** The original
 * embeds a Trakt `client_id` / `client_secret` pair and runs the device-code flow
 * against it. Those credentials belong to the original *developer*, not to the person
 * running the replica, and using them would register the replica with Trakt as the
 * original app. The replica therefore expects the user to paste their own Trakt access
 * token on the services screen (the same way the debrid services work), and the
 * device-code and refresh-token flows are left unimplemented on purpose.
 */
@Singleton
class TraktManager @Inject constructor(
    private val api: TraktApi,
    private val preferences: AppPreferences,
) {

    companion object {
        const val TAG = "TraktManager"

        /** The API version header Trakt requires on every request. */
        const val API_VERSION = "2"

        /** The media types the original asks for when it syncs. */
        const val TYPE_MOVIES = "movies"
        const val TYPE_SHOWS = "shows"

        /** The page size the original uses. */
        const val PAGE_LIMIT = 50
    }

    /** The stored access token, or null when no service is linked. */
    suspend fun token(): String? = preferences.traktToken.first()?.takeIf { it.isNotBlank() }

    /** True when a token is stored. */
    suspend fun isLoggedIn(): Boolean = token() != null

    /** The `Authorization` header value, or null when no token is stored. */
    suspend fun getAuthHeader(): String? = token()?.let { "Bearer $it" }

    /** Stores a token. */
    suspend fun saveToken(value: String) = preferences.setTraktToken(value)

    /** Forgets the token. */
    suspend fun logout() = preferences.setTraktToken(null)

    /** `GET users/settings` — the account behind the token. */
    suspend fun getUserSettings(): Result<TraktUserSettings> = call("getUserSettings") {
        api.getUserSettings(requireAuth())
    }

    /** `GET sync/last_activities` — the timestamps the sync uses to decide what changed. */
    suspend fun getLastActivities(): Result<TraktLastActivities> = call("getLastActivities") {
        api.getLastActivities(requireAuth())
    }

    /** `GET sync/watchlist/{type}`. */
    suspend fun getWatchlist(
        type: String = TYPE_MOVIES,
        page: Int = 1,
        limit: Int = PAGE_LIMIT,
    ): Result<List<TraktWatchlistItem>> = call("getWatchlist") {
        api.getWatchlist(requireAuth(), type, page, limit)
    }

    /** `POST sync/watchlist`. */
    suspend fun addToWatchlist(body: TraktSyncRequest): Result<TraktSyncResponse> =
        call("addToWatchlist") { api.addToWatchlist(requireAuth(), body) }

    /** `POST sync/watchlist/remove`. */
    suspend fun removeFromWatchlist(body: TraktSyncRequest): Result<TraktSyncResponse> =
        call("removeFromWatchlist") { api.removeFromWatchlist(requireAuth(), body) }

    /** `GET sync/history/{type}`. */
    suspend fun getHistory(
        type: String = TYPE_MOVIES,
        page: Int = 1,
        limit: Int = PAGE_LIMIT,
    ): Result<List<TraktHistoryItem>> = call("getHistory") {
        api.getHistory(requireAuth(), type, page, limit)
    }

    /** `POST sync/history`. */
    suspend fun addToHistory(body: TraktSyncRequest): Result<TraktSyncResponse> =
        call("addToHistory") { api.addToHistory(requireAuth(), body) }

    /** `POST sync/history/remove`. */
    suspend fun removeFromHistory(body: TraktSyncRequest): Result<TraktSyncResponse> =
        call("removeFromHistory") { api.removeFromHistory(requireAuth(), body) }

    /** Throws when no token is stored — every call needs one. */
    private suspend fun requireAuth(): String =
        getAuthHeader() ?: throw IllegalStateException("Trakt is not connected")

    /**
     * Runs a Trakt call and normalises the outcome.
     *
     * A non-2xx answer becomes a failure carrying the status code, exactly like the
     * original's `Response` handling.
     */
    private suspend fun <T> call(
        name: String,
        block: suspend () -> retrofit2.Response<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val response = block()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code()}")
            }
            val body = response.body()
                ?: throw IllegalStateException("Empty response")
            body
        }.onFailure { Logger.e(TAG, "$name failed", it) }
    }
}
