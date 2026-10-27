package com.movies.premium.data.remote.simkl

import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The SIMKL client.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.simkl.q` (the original's log tag
 * is `SimklManager` too). It owns the access token, the device-code login and the four
 * `sync/…` calls.
 *
 * The original's method map:
 *
 * | original | here                                  | endpoint               |
 * |----------|---------------------------------------|------------------------|
 * | `q.a`    | [addToHistory]                        | `POST sync/history`    |
 * | `q.b`    | [addToWatchlist]                      | `POST sync/add-to-list`|
 * | `q.c`    | [getAllItems]                         | `GET sync/all-items`   |
 * | `q.d`    | [getAuthHeader]                       | –                      |
 * | `q.e`    | [getDeviceCode]                       | `POST oauth2/device`   |
 * | `q.f`    | [getUserSettings]                     | `GET users/settings`   |
 * | `q.g`    | [isLoggedIn]                          | –                      |
 * | `q.h`    | [pollToken]                           | `POST oauth2/token`    |
 * | `q.i`    | [removeFromHistory]                   | `POST sync/history/remove` |
 * | `q.j`    | [removeFromWatchlist]                 | `POST sync/history/remove` |
 *
 * **Deviation — the original's client id is not shipped.** See
 * [SimklClientIdProvider] for the whole story; the short version is that the original
 * hard-codes its own SIMKL client id and the replica asks the user for theirs.
 */
@Singleton
class SimklManager @Inject constructor(
    private val api: SimklApi,
    private val preferences: AppPreferences,
    private val clientIdProvider: SimklClientIdProvider,
) {

    companion object {
        const val TAG = "SimklManager"

        /** The base URL of the SIMKL API. */
        const val BASE_URL = "https://api.simkl.com/"

        /** The scope the original asks for. */
        const val SCOPE = SimklDeviceCodeRequest.DEFAULT_SCOPE

        /** The `to` value SIMKL uses for the plan-to-watch list. */
        const val TO_PLAN_TO_WATCH = "plantowatch"

        /** The media types the original knows. */
        const val TYPE_MOVIE = "movie"
        const val TYPE_TV = "tv"
        const val TYPE_ANIME = "anime"
    }

    // ---- token -------------------------------------------------------------------

    /** The stored access token, or null when SIMKL is not linked. */
    suspend fun token(): String? = preferences.simklToken.first()?.takeIf { it.isNotBlank() }

    /** True when a token is stored. */
    suspend fun isLoggedIn(): Boolean = token() != null

    /** The `Authorization` header value, or null when no token is stored. */
    suspend fun getAuthHeader(): String? = token()?.let { "Bearer $it" }

    /** Stores an access token. */
    suspend fun saveToken(value: String) = preferences.setSimklToken(value)

    /** Forgets the access token. */
    suspend fun logout() = preferences.setSimklToken(null)

    // ---- client id ---------------------------------------------------------------

    /** The SIMKL client id the user registered, or null when there is none. */
    suspend fun clientId(): String? = clientIdProvider.clientId()?.takeIf { it.isNotBlank() }

    /** Stores the SIMKL client id the user registered. */
    suspend fun setClientId(value: String?) =
        clientIdProvider.setClientId(value?.takeIf { it.isNotBlank() })

    // ---- device-code login -------------------------------------------------------

    /**
     * `POST oauth2/device` — asks SIMKL for a code the user has to confirm in a browser.
     *
     * The original hard-codes its client id; the replica needs one from the user and
     * fails with a clear message when there is none.
     */
    suspend fun getDeviceCode(): Result<SimklDeviceCodeResponse> = call("getDeviceCode") {
        val clientId = clientId() ?: throw IllegalStateException("No SIMKL client id configured")
        api.requestDeviceCode(
            body = SimklDeviceCodeRequest(clientId = clientId, scope = SCOPE),
            clientId = clientId,
            scope = SCOPE,
        )
    }

    /**
     * `POST oauth2/token` — exchanges the device code for an access token.
     *
     * Mirrors the original's `q.h(userCode, deviceCode)`: the first parameter is the
     * code the user typed into the browser and the second the device code SIMKL handed
     * out; only the device code is sent to the API. The signature is kept as it is so
     * that the polling loop of the original can hand both codes straight through.
     *
     * When SIMKL answers with an access token it is stored, exactly like the original
     * does.
     */
    suspend fun pollToken(userCode: String, deviceCode: String): Result<SimklTokenResponse> =
        call("pollToken") {
            val clientId = clientId() ?: throw IllegalStateException("No SIMKL client id configured")
            api.requestAccessToken(
                SimklDeviceTokenRequest(deviceCode = deviceCode, clientId = clientId),
            )
        }.onSuccess { response ->
            val accessToken = response.accessToken
            if (!accessToken.isNullOrEmpty()) {
                saveToken(accessToken)
                Logger.d(TAG, "Simkl token obtained successfully!")
            } else {
                Logger.d(TAG, "OAuth2 token poll without a token: ${response.error ?: response.result}")
            }
        }

    // ---- account -----------------------------------------------------------------

    /** `GET users/settings` — the account behind the token. */
    suspend fun getUserSettings(): Result<SimklUserSettings> = call("getUserSettings") {
        api.getUserSettings(requireAuth())
    }

    /**
     * `GET sync/all-items` — the user's history, watchlist and ratings.
     *
     * The endpoint answers with a map of lists. The original flattens that map into a
     * single list and, for the `anime` bucket, moves a `movie` block into the `anime`
     * slot so that every entry can be read the same way.
     */
    suspend fun getAllItems(): Result<List<SimklWatchlistItem>> = callValue("getAllItems") {
        val response = api.getAllItems(requireAuth())
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code()}")
        }
        val body = response.body() ?: throw IllegalStateException("Empty response")
        val items = ArrayList<SimklWatchlistItem>()
        for ((bucket, bucketItems) in body) {
            for (item in bucketItems) {
                if (bucket == TYPE_ANIME) {
                    val anime = item.anime
                        ?: item.show
                        ?: item.movie?.let { SimklShow(title = it.title, year = it.year, ids = it.ids) }
                    items += item.withAnime(anime)
                } else {
                    items += item
                }
            }
        }
        items
    }

    // ---- sync --------------------------------------------------------------------

    /**
     * `POST sync/history` — marks something as watched.
     *
     * The body is built exactly like the original builds it: a movie goes into
     * `movies`, a show into `shows`, an anime into all three, and a show or an anime
     * with a season and an episode goes into `episodes`.
     */
    suspend fun addToHistory(
        tmdbId: String,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
    ): Result<Unit> = call("addToHistory") {
        api.addToHistory(requireAuth(), historyRequest(tmdbId, mediaType, season, episode))
    }

    /** `POST sync/history/remove` — takes something off the history. */
    suspend fun removeFromHistory(tmdbId: String, mediaType: String): Result<Unit> =
        call("removeFromHistory") {
            api.removeFromHistory(requireAuth(), historyRequest(tmdbId, mediaType, null, null))
        }

    /**
     * `POST sync/add-to-list` — puts something on the plan-to-watch list.
     *
     * The original sends `"to": "plantowatch"` with every entry.
     */
    suspend fun addToWatchlist(tmdbId: String, mediaType: String): Result<Unit> =
        call("addToWatchlist") {
            api.addToList(requireAuth(), watchlistRequest(tmdbId, mediaType))
        }

    /**
     * Removes something from the plan-to-watch list.
     *
     * **The original posts this to `sync/history/remove`** — the same endpoint it uses
     * for the history — instead of `sync/add-to-list` with `"to": "remove"`. That is
     * what the decompiled `q.j` does (`invoke-interface … b;->e(…)`, and `b.e` is
     * `POST sync/history/remove`), so the replica keeps the original's behaviour. The
     * request is expected to be rejected by SIMKL; the failure is logged, not thrown.
     */
    suspend fun removeFromWatchlist(tmdbId: String, mediaType: String): Result<Unit> =
        call("removeFromWatchlist") {
            api.removeFromHistory(requireAuth(), watchlistRequest(tmdbId, mediaType))
        }

    // ---- request bodies ----------------------------------------------------------

    /** The body of the history calls, built the way the original builds it. */
    private fun historyRequest(
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): SimklSyncRequest {
        val ids = SimklIds(tmdb = tmdbId)
        return when {
            mediaType == TYPE_MOVIE -> SimklSyncRequest(movies = listOf(SimklSyncMovie(ids = ids)))
            season != null && episode != null ->
                SimklSyncRequest(episodes = listOf(SimklSyncEpisode(season = season, episode = episode, ids = ids)))
            isAnime(mediaType) -> SimklSyncRequest(
                movies = listOf(SimklSyncMovie(ids = ids)),
                shows = listOf(SimklSyncShow(ids = ids)),
                anime = listOf(SimklSyncShow(ids = ids)),
            )
            else -> SimklSyncRequest(shows = listOf(SimklSyncShow(ids = ids)))
        }
    }

    /** The body of the watchlist calls, built the way the original builds it. */
    private fun watchlistRequest(tmdbId: String, mediaType: String): SimklSyncRequest {
        val ids = SimklIds(tmdb = tmdbId)
        return when {
            mediaType == TYPE_MOVIE -> SimklSyncRequest(
                movies = listOf(SimklSyncMovie(to = TO_PLAN_TO_WATCH, ids = ids)),
            )
            isAnime(mediaType) -> SimklSyncRequest(
                movies = listOf(SimklSyncMovie(to = TO_PLAN_TO_WATCH, ids = ids)),
                shows = listOf(SimklSyncShow(to = TO_PLAN_TO_WATCH, ids = ids)),
                anime = listOf(SimklSyncShow(to = TO_PLAN_TO_WATCH, ids = ids)),
            )
            else -> SimklSyncRequest(shows = listOf(SimklSyncShow(to = TO_PLAN_TO_WATCH, ids = ids)))
        }
    }

    /** True for the media types the original treats as anime. */
    private fun isAnime(mediaType: String): Boolean =
        mediaType.contains(TYPE_ANIME) || mediaType == TYPE_ANIME

    // ---- plumbing ----------------------------------------------------------------

    /** Throws when no token is stored — every sync call needs one. */
    private suspend fun requireAuth(): String =
        getAuthHeader() ?: throw IllegalStateException("SIMKL is not connected")

    /**
     * Runs a SIMKL call and normalises the outcome.
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
            response.body() ?: Unit as T
        }.onFailure { Logger.e(TAG, "$name failed", it) }
    }

    /**
     * The same normalisation for a call that does not answer with a Retrofit
     * `Response` — `GET sync/all-items` returns a map that has to be flattened
     * before it can be handed back.
     */
    private suspend fun <T> callValue(
        name: String,
        block: suspend () -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching { block() }.onFailure { Logger.e(TAG, "$name failed", it) }
    }
}
