package com.movies.premium.data.remote.trakt

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Trakt.tv API.
 *
 * The endpoints and their shapes are taken from the original's
 * `com.app.mlounge.data.remote.trakt.TraktApi` — the Retrofit annotations survived
 * R8, so every path, query parameter and body type here is the original's.
 */
interface TraktApi {

    // ---- device-code OAuth -------------------------------------------------------

    @POST("oauth/device/code")
    suspend fun postDeviceCode(
        @Body body: TraktDeviceCodeRequest,
    ): retrofit2.Response<TraktDeviceCodeResponse>

    @POST("oauth/device/token")
    suspend fun postDeviceToken(
        @Body body: TraktDeviceTokenRequest,
    ): retrofit2.Response<TraktTokenResponse>

    @POST("oauth/token")
    suspend fun refreshToken(
        @Body body: TraktRefreshTokenRequest,
    ): retrofit2.Response<TraktTokenResponse>

    @POST("oauth/revoke")
    suspend fun revoke(
        @Body body: TraktRevokeRequest,
    ): retrofit2.Response<Unit>

    // ---- account -----------------------------------------------------------------

    @GET("users/settings")
    suspend fun getUserSettings(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<TraktUserSettings>

    @GET("sync/last_activities")
    suspend fun getLastActivities(
        @Header("Authorization") auth: String,
    ): retrofit2.Response<TraktLastActivities>

    // ---- watchlist ---------------------------------------------------------------

    @GET("sync/watchlist/{type}")
    suspend fun getWatchlist(
        @Header("Authorization") auth: String,
        @Path("type") type: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): retrofit2.Response<List<TraktWatchlistItem>>

    @POST("sync/watchlist")
    suspend fun addToWatchlist(
        @Header("Authorization") auth: String,
        @Body body: TraktSyncRequest,
    ): retrofit2.Response<TraktSyncResponse>

    @POST("sync/watchlist/remove")
    suspend fun removeFromWatchlist(
        @Header("Authorization") auth: String,
        @Body body: TraktSyncRequest,
    ): retrofit2.Response<TraktSyncResponse>

    // ---- history -----------------------------------------------------------------

    @GET("sync/history/{type}")
    suspend fun getHistory(
        @Header("Authorization") auth: String,
        @Path("type") type: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): retrofit2.Response<List<TraktHistoryItem>>

    @POST("sync/history")
    suspend fun addToHistory(
        @Header("Authorization") auth: String,
        @Body body: TraktSyncRequest,
    ): retrofit2.Response<TraktSyncResponse>

    @POST("sync/history/remove")
    suspend fun removeFromHistory(
        @Header("Authorization") auth: String,
        @Body body: TraktSyncRequest,
    ): retrofit2.Response<TraktSyncResponse>
}
