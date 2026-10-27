package com.movies.premium.data.remote.model

import com.google.gson.annotations.SerializedName

data class TmdbPagedResponse<T>(
    @SerializedName("page") val page: Int = 1,
    @SerializedName("results") val results: List<T> = emptyList(),
    @SerializedName("total_pages") val totalPages: Int = 0,
    @SerializedName("total_results") val totalResults: Int = 0,
)

data class TmdbMovie(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    /**
     * Whether TMDB flagged the title as an adult one.
     *
     * The original's "Include Adult in …" switches decide whether such a title
     * is shown in the search, in the favourites and in the watch history.
     */
    @SerializedName("adult") val adult: Boolean = false,
)

data class TmdbTvShow(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    /** Whether TMDB flagged the title as an adult one. */
    @SerializedName("adult") val adult: Boolean = false,
)

data class TmdbMovieDetailResponse(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("runtime") val runtime: Int = 0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("genres") val genres: List<TmdbGenre> = emptyList(),
    @SerializedName("tagline") val tagline: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("budget") val budget: Long = 0,
    @SerializedName("revenue") val revenue: Long = 0,
    @SerializedName("imdb_id") val imdbId: String? = null,
    /** Whether TMDB flagged the title as an adult one. */
    @SerializedName("adult") val adult: Boolean = false,
    @SerializedName("credits") val credits: TmdbCredits? = null,
    @SerializedName("videos") val videos: TmdbVideos? = null,
    @SerializedName("images") val images: TmdbImages? = null,
    @SerializedName("similar") val similar: TmdbPagedResponse<TmdbMovie> = TmdbPagedResponse(),
    @SerializedName("recommendations") val recommendations: TmdbPagedResponse<TmdbMovie> = TmdbPagedResponse(),
)

data class TmdbTvDetailResponse(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int = 0,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("genres") val genres: List<TmdbGenre> = emptyList(),
    @SerializedName("tagline") val tagline: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("episodes_run_time") val episodesRunTime: List<Int> = emptyList(),
    @SerializedName("imdb_id") val imdbId: String? = null,
    /** Whether TMDB flagged the title as an adult one. */
    @SerializedName("adult") val adult: Boolean = false,
    @SerializedName("credits") val credits: TmdbCredits? = null,
    @SerializedName("videos") val videos: TmdbVideos? = null,
    @SerializedName("images") val images: TmdbImages? = null,
    @SerializedName("similar") val similar: TmdbPagedResponse<TmdbTvShow> = TmdbPagedResponse(),
    @SerializedName("recommendations") val recommendations: TmdbPagedResponse<TmdbTvShow> = TmdbPagedResponse(),
)

data class TmdbGenre(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
)

data class TmdbCredits(
    @SerializedName("cast") val cast: List<TmdbCastMember> = emptyList(),
    @SerializedName("crew") val crew: List<TmdbCrewMember> = emptyList(),
)

data class TmdbCastMember(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("character") val character: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
    @SerializedName("order") val order: Int = 0,
)

data class TmdbCrewMember(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("job") val job: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
)

data class TmdbVideos(
    @SerializedName("results") val results: List<TmdbVideo> = emptyList(),
)

data class TmdbVideo(
    @SerializedName("key") val key: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("site") val site: String? = null,
    @SerializedName("type") val type: String? = null,
)

data class TmdbImages(
    @SerializedName("backdrops") val backdrops: List<TmdbImage> = emptyList(),
    @SerializedName("posters") val posters: List<TmdbImage> = emptyList(),
)

data class TmdbImage(
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("iso_639_1") val iso6391: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
)

data class TmdbPersonDetail(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("biography") val biography: String? = null,
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("deathday") val deathday: String? = null,
    @SerializedName("place_of_birth") val placeOfBirth: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
    @SerializedName("popularity") val popularity: Double = 0.0,
    @SerializedName("known_for_department") val knownForDepartment: String? = null,
    @SerializedName("combined_credits") val combinedCredits: TmdbCombinedCredits? = null,
)

data class TmdbCombinedCredits(
    @SerializedName("cast") val cast: List<TmdbCastCredit> = emptyList(),
    @SerializedName("crew") val crew: List<TmdbCrewCredit> = emptyList(),
)

data class TmdbCastCredit(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("character") val character: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
)

data class TmdbCrewCredit(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("job") val job: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
)

data class TmdbGenreList(
    @SerializedName("genres") val genres: List<TmdbGenre> = emptyList(),
)

/**
 * One episode of a season.
 *
 * TMDB's season endpoint is not part of the typed models of the original — the
 * original's player parses it by hand — so the replica models it here and reads
 * it through `TmdbRepository.tvSeasonEpisodes()`.
 */
data class TmdbSeasonEpisode(
    /** The number of the episode inside its season. */
    val number: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("still_path") val stillPath: String? = null,
    @SerializedName("episode_number") val episodeNumber: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 0,
)
