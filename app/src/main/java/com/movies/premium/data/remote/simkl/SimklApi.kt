package com.movies.premium.data.remote.simkl

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The SIMKL API.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.simkl.b` — the Retrofit
 * annotations survived R8, so every path, verb, query parameter and body type below is
 * the original's:
 *
 * | original | endpoint            | verb |
 * |----------|---------------------|------|
 * | `b.a`    | `users/settings`    | GET  |
 * | `b.b`    | `oauth2/device`     | POST |
 * | `b.c`    | `sync/all-items`    | GET  |
 * | `b.d`    | `sync/history`      | POST |
 * | `b.e`    | `sync/history/remove` | POST |
 * | `b.f`    | `sync/add-to-list`  | POST |
 * | `b.g`    | `oauth2/token`      | POST |
 *
 * The base URL (`https://api.simkl.com/`) and the `simkl-api-key` header live in the
 * original's DI (`com.app.mlounge.di.g` and `com.app.mlounge.di.d`); see
 * [SimklApiKeyInterceptor] for the replica's replacement and the note about the key.
 */
interface SimklApi {

    /** `GET users/settings` — the account behind the access token. */
    @GET("users/settings")
    suspend fun getUserSettings(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<SimklUserSettings>

    /**
     * `POST oauth2/device` — starts the device-code login.
     *
     * The client id is sent both in the body and as a query parameter; that is what the
     * original does.
     */
    @POST("oauth2/device")
    suspend fun requestDeviceCode(
        @Body body: SimklDeviceCodeRequest,
        @Query("client_id") clientId: String,
        @Query("scope") scope: String,
    ): retrofit2.Response<SimklDeviceCodeResponse>

    /** `POST oauth2/token` — exchanges the device code for an access token. */
    @POST("oauth2/token")
    suspend fun requestAccessToken(
        @Body body: SimklDeviceTokenRequest,
    ): retrofit2.Response<SimklTokenResponse>

    /** `GET sync/all-items` — history, watchlist and ratings in one go. */
    @GET("sync/all-items")
    suspend fun getAllItems(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<Map<String, List<SimklWatchlistItem>>>

    /** `POST sync/history` — marks something as watched. */
    @POST("sync/history")
    suspend fun addToHistory(
        @Header("Authorization") auth: String,
        @Body body: SimklSyncRequest,
    ): retrofit2.Response<Unit>

    /** `POST sync/history/remove` — takes something off the history. */
    @POST("sync/history/remove")
    suspend fun removeFromHistory(
        @Header("Authorization") auth: String,
        @Body body: SimklSyncRequest,
    ): retrofit2.Response<Unit>

    /** `POST sync/add-to-list` — puts something on the plan-to-watch list. */
    @POST("sync/add-to-list")
    suspend fun addToList(
        @Header("Authorization") auth: String,
        @Body body: SimklSyncRequest,
    ): retrofit2.Response<Unit>
}
