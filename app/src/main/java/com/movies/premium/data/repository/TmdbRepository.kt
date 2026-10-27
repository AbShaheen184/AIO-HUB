package com.movies.premium.data.repository

import com.movies.premium.data.remote.api.TmdbApi
import com.movies.premium.data.remote.model.TmdbGenre
import com.movies.premium.data.remote.model.TmdbGenreList
import com.movies.premium.data.remote.model.TmdbMovie
import com.movies.premium.data.remote.model.TmdbMovieDetailResponse
import com.movies.premium.data.remote.model.TmdbPagedResponse
import com.movies.premium.data.remote.model.TmdbPersonDetail
import com.movies.premium.data.remote.model.TmdbSeasonEpisode
import com.movies.premium.data.remote.model.TmdbTvDetailResponse
import com.movies.premium.data.remote.model.TmdbTvShow
import com.movies.premium.util.Logger
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps TMDB calls so the UI never sees raw exceptions. */
@Singleton
class TmdbRepository @Inject constructor(
    private val api: TmdbApi,
) {
    suspend fun trendingMovies(page: Int = 1): Result<TmdbPagedResponse<TmdbMovie>> =
        runCatching { api.trendingMovies(page) }
            .onFailure { Logger.e("TmdbRepo", "trendingMovies failed", it) }

    suspend fun trendingTvShows(page: Int = 1): Result<TmdbPagedResponse<TmdbTvShow>> =
        runCatching { api.trendingTvShows(page) }
            .onFailure { Logger.e("TmdbRepo", "trendingTvShows failed", it) }

    suspend fun discoverMovies(
        page: Int = 1,
        sortBy: String? = null,
        genres: String? = null,
        year: Int? = null,
        includeAdult: Boolean = false,
    ): Result<TmdbPagedResponse<TmdbMovie>> =
        runCatching {
            api.discoverMovies(
                page = page,
                sortBy = sortBy,
                genres = genres,
                year = year,
                includeAdult = includeAdult,
            )
        }.onFailure { Logger.e("TmdbRepo", "discoverMovies failed", it) }

    suspend fun discoverTvShows(
        page: Int = 1,
        sortBy: String? = null,
        genres: String? = null,
        year: Int? = null,
        includeAdult: Boolean = false,
    ): Result<TmdbPagedResponse<TmdbTvShow>> =
        runCatching {
            api.discoverTvShows(
                page = page,
                sortBy = sortBy,
                genres = genres,
                year = year,
                includeAdult = includeAdult,
            )
        }.onFailure { Logger.e("TmdbRepo", "discoverTvShows failed", it) }

    suspend fun searchMovies(query: String, page: Int = 1, includeAdult: Boolean = false):
        Result<TmdbPagedResponse<TmdbMovie>> =
        runCatching { api.searchMovies(query, page, includeAdult) }
            .onFailure { Logger.e("TmdbRepo", "searchMovies failed", it) }

    suspend fun searchTvShows(query: String, page: Int = 1, includeAdult: Boolean = false):
        Result<TmdbPagedResponse<TmdbTvShow>> =
        runCatching { api.searchTvShows(query, page, includeAdult) }
            .onFailure { Logger.e("TmdbRepo", "searchTvShows failed", it) }

    suspend fun searchMulti(query: String, page: Int = 1, includeAdult: Boolean = false):
        Result<TmdbPagedResponse<TmdbMovie>> =
        runCatching { api.searchMulti(query, page, includeAdult) }
            .onFailure { Logger.e("TmdbRepo", "searchMulti failed", it) }

    suspend fun movieDetail(id: Int): Result<TmdbMovieDetailResponse> =
        runCatching { api.movieDetail(id) }
            .onFailure { Logger.e("TmdbRepo", "movieDetail($id) failed", it) }

    suspend fun tvDetail(id: Int): Result<TmdbTvDetailResponse> =
        runCatching { api.tvDetail(id) }
            .onFailure { Logger.e("TmdbRepo", "tvDetail($id) failed", it) }

    suspend fun personDetail(id: Int): Result<TmdbPersonDetail> =
        runCatching { api.personDetail(id) }
            .onFailure { Logger.e("TmdbRepo", "personDetail($id) failed", it) }

    suspend fun movieGenres(): Result<List<TmdbGenre>> =
        runCatching { api.movieGenres().genres }
            .onFailure { Logger.e("TmdbRepo", "movieGenres failed", it) }

    suspend fun tvGenres(): Result<List<TmdbGenre>> =
        runCatching { api.tvGenres().genres }
            .onFailure { Logger.e("TmdbRepo", "tvGenres failed", it) }

    /**
     * The episodes of one season of a series.
     *
     * The season endpoint answers with a body that has no typed counterpart in
     * the original, so the episodes are read out of the JSON by hand — the same
     * way the original's player does it.
     */
    suspend fun tvSeasonEpisodes(id: Int, season: Int): Result<List<TmdbSeasonEpisode>> =
        runCatching {
            val body = api.tvSeason(id, season).body()?.string() ?: return@runCatching emptyList()
            val root = JsonParser.parseString(body).asJsonObject
            val episodes = root.getAsJsonArray("episodes") ?: return@runCatching emptyList()
            episodes.mapNotNull { element ->
                val entry = element.asJsonObject
                TmdbSeasonEpisode(
                    number = entry.get("episode_number")?.asInt ?: return@mapNotNull null,
                    name = entry.get("name")?.asString,
                    overview = entry.get("overview")?.asString,
                    stillPath = entry.get("still_path")?.asString,
                    episodeNumber = entry.get("episode_number")?.asInt ?: 0,
                    seasonNumber = entry.get("season_number")?.asInt ?: season,
                )
            }
        }.onFailure { Logger.e("TmdbRepo", "tvSeasonEpisodes($id, $season) failed", it) }
}
