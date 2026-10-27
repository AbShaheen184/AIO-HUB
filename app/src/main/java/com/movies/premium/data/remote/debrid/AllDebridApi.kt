package com.movies.premium.data.remote.debrid

import com.google.gson.JsonObject
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * AllDebrid's v4 API.
 *
 * Every endpoint and parameter is the original's
 * (`com.app.mlounge.data.remote.debrid.AllDebridApi`).
 */
interface AllDebridApi {

    /** `v4/user` — the account behind the token. */
    @GET("v4/user")
    suspend fun getUser(@Header("Authorization") auth: String): JsonObject

    /** `v4.1/pin/get` — starts the pin-based authorisation. */
    @GET("v4.1/pin/get")
    suspend fun getPin(): JsonObject

    /** `v4/link/unlock` — turns a cached file link into a direct one. */
    @POST("v4/link/unlock")
    @FormUrlEncoded
    suspend fun unlockLink(
        @Header("Authorization") auth: String,
        @Field("link") link: String,
    ): JsonObject

    /** `v4/magnet/files` — the file tree of an uploaded magnet. */
    @POST("v4/magnet/files")
    @FormUrlEncoded
    suspend fun getMagnetFiles(
        @Header("Authorization") auth: String,
        @Field("id[]") id: Int,
    ): JsonObject

    /** `v4/pin/check` — polls the pin authorisation. */
    @POST("v4/pin/check")
    @FormUrlEncoded
    suspend fun checkPin(
        @Field("check") check: String,
        @Field("pin") pin: String,
    ): JsonObject

    /** `v4.1/magnet/status` — the cache status of an uploaded magnet. */
    @POST("v4.1/magnet/status")
    @FormUrlEncoded
    suspend fun getMagnetStatus(
        @Header("Authorization") auth: String,
        @Field("id") id: Int,
    ): JsonObject

    /** `v4/magnet/upload` — uploads a magnet to the cache. */
    @POST("v4/magnet/upload")
    @FormUrlEncoded
    suspend fun uploadMagnet(
        @Header("Authorization") auth: String,
        @Field("magnets[]") magnet: String,
    ): JsonObject
}
