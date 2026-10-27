package com.movies.premium.data.remote.api

import com.movies.premium.data.remote.model.ChqStreamResponse
import com.movies.premium.data.remote.model.ChqStreamsResponse
import com.movies.premium.data.remote.model.GameDetailResponse
import com.movies.premium.data.remote.model.GameSearchResponse
import com.movies.premium.data.remote.model.GameStatsResponse
import com.movies.premium.data.remote.model.GamesResponse
import com.movies.premium.data.remote.model.IptvGroupsResponse
import com.movies.premium.data.remote.model.IptvSearchResponse
import com.movies.premium.data.remote.model.IptvStatsResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The CHQ backend API. Every stream source exposed by the app is a separate
 * endpoint on the same host, so the app can fan out across providers in parallel.
 */
interface ChqApi {
    // --- The current stream routes -------------------------------------------
    //
    // The backend replaced the per-provider routes below with this family. The
    // old ones answer `404` on the live host, so the repository tries these
    // first and only falls back to the legacy ones when they are unavailable.

    /** Every provider, for a movie. */
    @GET("api/streams/{id}")
    suspend fun getAllStreams(
        @Path("id") id: String,
    ): ChqStreamsResponse

    /** Every provider, for one episode of a series. */
    @GET("api/streams/{id}/{season}/{episode}")
    suspend fun getAllStreamsForEpisode(
        @Path("id") id: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int,
    ): ChqStreamsResponse

    /** One provider, for a movie. */
    @GET("api/streams/{provider}/{id}")
    suspend fun getStreamsFromProvider(
        @Path("provider") provider: String,
        @Path("id") id: String,
    ): ChqStreamsResponse

    /** One provider, for one episode of a series. */
    @GET("api/streams/{provider}/{id}/{season}/{episode}")
    suspend fun getStreamsFromProviderForEpisode(
        @Path("provider") provider: String,
        @Path("id") id: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int,
    ): ChqStreamsResponse

    // --- The legacy per-provider routes --------------------------------------
    //
    // These are the original's own routes. They are kept so the replica still
    // works against a backend that has not migrated yet.

    @GET("api/multiembed/streams")
    suspend fun getMultiEmbedStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/vidzee/streams")
    suspend fun getVidzeeStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/vidrock/streams")
    suspend fun getVidrockStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/fmovies/streams")
    suspend fun getFmoviesStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/vixsrc/streams")
    suspend fun getVixsrcStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/primesrc/streams")
    suspend fun getPrimesrcStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/twoembed/streams")
    suspend fun getTwoembedStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/vidsrc/streams")
    suspend fun getVidsrcStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/4khdhub/streams")
    suspend fun get4khdStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/kmmovies/streams")
    suspend fun getKmmoviesStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/moviesmod/streams")
    suspend fun getMoviesmodStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/uhdmovies/streams")
    suspend fun getUhdmoviesStreams(
        @Query("tmdbId") tmdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/torrentio/streams")
    suspend fun getTorrentioStreams(
        @Query("imdbId") imdbId: String,
        @Query("type") type: String = "movie",
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): ChqStreamResponse

    @GET("api/iptv/search")
    suspend fun searchChannels(
        @Query("query") query: String,
        @Query("country") country: String? = null,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 50,
    ): IptvSearchResponse

    @GET("api/iptv/groups")
    suspend fun getChannelGroups(
        @Query("country") country: String? = null,
    ): IptvGroupsResponse

    @GET("api/iptv/stats")
    suspend fun getIptvStats(): IptvStatsResponse

    @POST("api/{provider}/regenerate")
    suspend fun regenerateProviderPlaylist(
        @Path("provider") provider: String,
    ): retrofit2.Response<ResponseBody>

    @GET("api/{provider}/meta")
    suspend fun getProviderMeta(
        @Path("provider") provider: String,
    ): ProviderMeta

    @GET("api/vavoo/channels")
    suspend fun getVavooChannels(): VavooChannelsResponse

    @GET("api/vavoo/resolve")
    suspend fun resolveVavooChannel(
        @Query("url") url: String,
    ): VavooResolveResponse

    @GET("api/plutotv/channels/{region}")
    suspend fun getPlutoChannels(
        @Path("region") region: String = "us",
    ): PlutoChannelsResponse

    @GET("api/plutotv/categories/{region}")
    suspend fun getPlutoCategories(
        @Path("region") region: String = "us",
    ): PlutoCategoriesResponse

    @GET("api/games/{platform}/stats")
    suspend fun getGameStats(
        @Path("platform") platform: String,
    ): GameStatsResponse

    @GET("api/games/{platform}/games")
    suspend fun getGames(
        @Path("platform") platform: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("genre") genre: String? = null,
        @Query("decade") decade: String? = null,
    ): GamesResponse

    @GET("api/games/{platform}/game/{slug}")
    suspend fun getGameDetail(
        @Path("platform") platform: String,
        @Path("slug") slug: String,
    ): GameDetailResponse

    @GET("api/games/{platform}/search")
    suspend fun searchGames(
        @Path("platform") platform: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): GameSearchResponse

    @GET("api/games/{platform}/rom/{slug}")
    suspend fun downloadRom(
        @Path("platform") platform: String,
        @Path("slug") slug: String,
    ): retrofit2.Response<ResponseBody>
}
