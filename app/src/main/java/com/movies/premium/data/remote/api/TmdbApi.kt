package com.movies.premium.data.remote.api

import com.movies.premium.data.remote.model.TmdbCombinedCredits
import com.movies.premium.data.remote.model.TmdbGenreList
import com.movies.premium.data.remote.model.TmdbMovie
import com.movies.premium.data.remote.model.TmdbMovieDetailResponse
import com.movies.premium.data.remote.model.TmdbPagedResponse
import com.movies.premium.data.remote.model.TmdbPersonDetail
import com.movies.premium.data.remote.model.TmdbTvDetailResponse
import com.movies.premium.data.remote.model.TmdbTvShow
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String? = null,
        @Query("with_genres") genres: String? = null,
        @Query("year") year: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("release_date.gte") releaseDateGte: String? = null,
        @Query("release_date.lte") releaseDateLte: String? = null,
    ): TmdbPagedResponse<TmdbMovie>

    @GET("discover/tv")
    suspend fun discoverTvShows(
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String? = null,
        @Query("with_genres") genres: String? = null,
        @Query("year") year: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPagedResponse<TmdbTvShow>

    @GET("trending/movie/week")
    suspend fun trendingMovies(
        @Query("page") page: Int = 1,
    ): TmdbPagedResponse<TmdbMovie>

    @GET("trending/tv/week")
    suspend fun trendingTvShows(
        @Query("page") page: Int = 1,
    ): TmdbPagedResponse<TmdbTvShow>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPagedResponse<TmdbMovie>

    @GET("search/tv")
    suspend fun searchTvShows(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPagedResponse<TmdbTvShow>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPagedResponse<TmdbMovie>

    @GET("movie/{id}")
    suspend fun movieDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,videos,images,similar,recommendations",
    ): TmdbMovieDetailResponse

    @GET("tv/{id}")
    suspend fun tvDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,videos,images,similar,recommendations",
    ): TmdbTvDetailResponse

    @GET("tv/{id}/season/{season}")
    suspend fun tvSeason(
        @Path("id") id: Int,
        @Path("season") season: Int,
    ): retrofit2.Response<okhttp3.ResponseBody>

    @GET("person/{id}")
    suspend fun personDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "combined_credits",
    ): TmdbPersonDetail

    @GET("genre/movie/list")
    suspend fun movieGenres(): TmdbGenreList

    @GET("genre/tv/list")
    suspend fun tvGenres(): TmdbGenreList
}
