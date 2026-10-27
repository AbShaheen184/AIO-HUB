package com.movies.premium.data.remote.debrid

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Premiumize's API.
 *
 * Every endpoint and parameter is the original's
 * (`com.app.mlounge.data.remote.debrid.PremiumizeApi`).
 */
interface PremiumizeApi {

    /** `transfer/directdl` — the direct link of a magnet or a file id. */
    @POST("transfer/directdl")
    @FormUrlEncoded
    suspend fun directDownload(
        @Query("apikey") apiKey: String,
        @Field("src") src: String,
    ): PremiumizeDirectDlResponse

    /** `account/info` — the account behind the api key. */
    @GET("account/info")
    suspend fun accountInfo(@Query("apikey") apiKey: String): PremiumizeUserInfo

    /** `transfer/create` — starts a transfer for a magnet. */
    @POST("transfer/create")
    @FormUrlEncoded
    suspend fun createTransfer(
        @Query("apikey") apiKey: String,
        @Field("src") src: String,
    ): PremiumizeTransferResponse

    /** `folder/list` — the files of a transfer's folder. */
    @GET("folder/list")
    suspend fun folderList(
        @Query("apikey") apiKey: String,
        @Query("id") id: String,
    ): PremiumizeFolderListResponse

    /** `transfer/list` — every transfer on the account. */
    @GET("transfer/list")
    suspend fun transferList(@Query("apikey") apiKey: String): PremiumizeTransferList
}
