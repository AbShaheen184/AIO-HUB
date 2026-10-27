package com.movies.premium.data.remote.debrid

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Offcloud's API.
 *
 * Every endpoint and parameter is the original's
 * (`com.app.mlounge.data.remote.debrid.OffCloudApi`).
 */
interface OffCloudApi {

    /** `api/account/info` — the account behind the token. */
    @GET("api/account/info")
    suspend fun accountInfo(@Header("Authorization") auth: String): OffCloudAccountInfo

    /** `api/cloud/explore/{requestId}` — the files of an instant download. */
    @GET("api/cloud/explore/{requestId}")
    suspend fun explore(
        @Header("Authorization") auth: String,
        @Path("requestId") requestId: String,
        @Query("format") format: String,
    ): OffCloudExploreDetailedResponse

    /** `api/cache/download` — the cached files of a magnet. */
    @POST("api/cache/download")
    suspend fun cacheDownload(
        @Header("Authorization") auth: String,
        @Body body: Map<String, String>,
    ): List<OffCloudCacheDownloadFile>

    /** `api/cache/info` — whether a hash is already cached. */
    @POST("api/cache/info")
    suspend fun cacheInfo(
        @Header("Authorization") auth: String,
        @Body body: Map<String, Any>,
    ): List<OffCloudCacheInfoItem>

    /** `api/cloud` — starts an instant download. */
    @POST("api/cloud")
    suspend fun createCloudDownload(
        @Header("Authorization") auth: String,
        @Body body: Map<String, String>,
    ): OffCloudCloudCreateResponse

    /** `api/cloud/status` — the progress of an instant download. */
    @POST("api/cloud/status")
    suspend fun cloudStatus(
        @Header("Authorization") auth: String,
        @Body body: Map<String, String>,
    ): OffCloudCloudStatusResponse
}
