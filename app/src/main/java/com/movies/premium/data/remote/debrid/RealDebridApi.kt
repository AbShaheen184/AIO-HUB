package com.movies.premium.data.remote.debrid

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Real-Debrid's REST API.
 *
 * Every endpoint and parameter is the original's
 * (`com.app.mlounge.data.remote.debrid.RealDebridApi`).
 */
interface RealDebridApi {

    /** `rest/1.0/user` — the account behind the token. */
    @GET("rest/1.0/user")
    suspend fun getUser(@Header("Authorization") auth: String): JsonObject

    /** `oauth/v2/token` — exchanges a device code for an access token. */
    @POST("oauth/v2/token")
    @FormUrlEncoded
    suspend fun exchangeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String,
    ): RealDebridTokenResponse

    /** `rest/1.0/torrents/selectFiles/{id}` — chooses the files to download. */
    @POST("rest/1.0/torrents/selectFiles/{id}")
    @FormUrlEncoded
    suspend fun selectFiles(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Field("files") files: String,
    ): Response<JsonObject>

    /** `rest/1.0/torrents/info/{id}` — the torrent's files and status. */
    @GET("rest/1.0/torrents/info/{id}")
    suspend fun torrentInfo(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    ): JsonObject

    /** `oauth/v2/device/code` — starts the device-code authorisation. */
    @GET("oauth/v2/device/code")
    suspend fun deviceCode(
        @Query("client_id") clientId: String,
        @Query("new_credentials") newCredentials: String,
    ): RealDebridDeviceCodeResponse

    /** `rest/1.0/unrestrict/link` — turns a host link into a direct one. */
    @POST("rest/1.0/unrestrict/link")
    @FormUrlEncoded
    suspend fun unrestrictLink(
        @Header("Authorization") auth: String,
        @Field("link") link: String,
    ): JsonObject

    /** `oauth/v2/device/credentials` — polls the device-code authorisation. */
    @GET("oauth/v2/device/credentials")
    suspend fun deviceCredentials(
        @Query("client_id") clientId: String,
        @Query("code") code: String,
    ): RealDebridCredentialsResponse

    /** `rest/1.0/torrents/addMagnet` — adds a magnet to the account. */
    @POST("rest/1.0/torrents/addMagnet")
    @FormUrlEncoded
    suspend fun addMagnet(
        @Header("Authorization") auth: String,
        @Field("magnet") magnet: String,
    ): JsonObject
}
