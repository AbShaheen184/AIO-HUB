package com.movies.premium.data.remote.api

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The Deezer public API, used by the music section for the chart and
 * editorial browsing plus album/artist detail lookups.
 *
 * The original hits `https://api.deezer.com` with a browser user agent and
 * `Accept: application/json`; the base URL is registered in
 * `di.NetworkModule.Endpoints.DEEZER`.
 */
interface DeezerApi {
    /** Top albums chart. Paged with `limit`/`index`. */
    @GET("chart/0/albums")
    suspend fun getChartAlbums(
        @Query("limit") limit: Int = 25,
        @Query("index") index: Int = 0,
    ): JsonObject

    /** Top artists chart. Paged with `limit`/`index`. */
    @GET("chart/0/artists")
    suspend fun getChartArtists(
        @Query("limit") limit: Int = 25,
        @Query("index") index: Int = 0,
    ): JsonObject

    /** Editorial selection ("New Releases"). Paged with `limit`/`index`. */
    @GET("editorial/0/selection")
    suspend fun getEditorialSelection(
        @Query("limit") limit: Int = 25,
        @Query("index") index: Int = 0,
    ): JsonObject

    /** Full album payload, including `tracks.data`. */
    @GET("album/{id}")
    suspend fun getAlbum(@Path("id") id: String): JsonObject

    /** Full artist payload. */
    @GET("artist/{id}")
    suspend fun getArtist(@Path("id") id: String): JsonObject

    /** An artist's albums, paged with `limit`/`index`. */
    @GET("artist/{id}/albums")
    suspend fun getArtistAlbums(
        @Path("id") id: String,
        @Query("limit") limit: Int = 25,
        @Query("index") index: Int = 0,
    ): JsonObject
}

/**
 * The Anikoto anime API. The original talks to it with a plain OkHttp
 * client and parses the `{ ok, data }` envelope by hand, so the Retrofit
 * interface here returns the raw envelope and the scraper does the rest.
 *
 * The base URL is registered in `di.NetworkModule.Endpoints.ANIKOTO`.
 */
interface AnikotoApi {
    /**
     * Filtered/paged anime list.
     *
     * The original builds this URL by hand (`buildFilterUrl`) because the
     * parameter order and the `genre[]` / `term_type[]` / `status[]` array
     * syntax differ per chip, so the query string is passed in whole.
     */
    @GET
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
    suspend fun filter(@Url url: String): JsonObject

    /** Anime detail payload. */
    @GET("anime/{id}")
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
    suspend fun getAnime(@Path("id") id: String): JsonObject

    /** Episode list for a series. */
    @GET("anime/{id}/episodes")
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
    suspend fun getEpisodes(@Path("id") id: String): JsonObject
}
