package com.movies.premium.data.remote.debrid

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * TorBox's v1 API.
 *
 * Every endpoint and parameter is the original's
 * (`com.app.mlounge.data.remote.debrid.TorBoxApi`).
 */
interface TorBoxApi {

    /** `v1/api/user/me` — the account behind the token. */
    @GET("v1/api/user/me")
    suspend fun getUserInfo(@Header("Authorization") auth: String): TorBoxUserInfo

    /** `v1/api/torrents/mylist` — every torrent on the account. */
    @GET("v1/api/torrents/mylist")
    suspend fun myList(
        @Header("Authorization") auth: String,
        @Query("bypass_cache") bypassCache: Boolean,
    ): TorBoxTorrentListResponse

    /** `v1/api/torrents/createtorrent` — adds a magnet to the account. */
    @POST("v1/api/torrents/createtorrent")
    suspend fun createTorrent(
        @Header("Authorization") auth: String,
        @Body body: RequestBody,
    ): TorBoxCreateTorrentResponse

    /** `v1/api/torrents/requestdl` — asks for a download link of one file. */
    @GET("v1/api/torrents/requestdl")
    suspend fun requestDownload(
        @Query("token") token: String,
        @Query("torrent_id") torrentId: Int,
        @Query("file_id") fileId: Int,
        @Query("zip_link") zipLink: Boolean,
    ): TorBoxRequestDlResponse

    /** `v1/api/torrents/mylist` — one torrent, with its files. */
    @GET("v1/api/torrents/mylist")
    suspend fun torrentInfo(
        @Header("Authorization") auth: String,
        @Query("id") id: Int,
        @Query("bypass_cache") bypassCache: Boolean,
    ): TorBoxTorrentInfoResponse
}
