package com.movies.premium.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.local.dao.FavoriteDao
import com.movies.premium.data.local.dao.WatchlistDao
import com.movies.premium.data.local.entity.FavoriteEntity
import com.movies.premium.data.local.entity.WatchlistEntity
import com.movies.premium.data.remote.model.ChqStream
import com.movies.premium.data.remote.model.TmdbCredits
import com.movies.premium.data.remote.model.TmdbMovie
import com.movies.premium.data.remote.model.TmdbMovieDetailResponse
import com.movies.premium.data.remote.model.TmdbSeasonEpisode
import com.movies.premium.data.remote.model.TmdbTvDetailResponse
import com.movies.premium.data.remote.model.TmdbTvShow
import com.movies.premium.data.remote.model.TmdbVideos
import com.movies.premium.data.repository.StreamsRepository
import com.movies.premium.data.repository.TmdbRepository
import com.movies.premium.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    /**
     * What the screen is showing: `"movie"` or `"tv"`.
     *
     * It is taken from the **route**, not from the loaded data: the original's
     * detail screen is reached through `movie_detail/{movieId}` and
     * `tvshow_detail/{tvId}`, and the screen has to know which one it is
     * before the response arrives — otherwise a series looks like a movie
     * while it is loading, and a failed load looks like a movie forever.
     */
    val mediaType: String = "movie",
    /** The TMDB id the screen was opened with. */
    val mediaId: Int = 0,
    val movie: TmdbMovieDetailResponse? = null,
    val tvShow: TmdbTvDetailResponse? = null,
    val similarMovies: List<TmdbMovie> = emptyList(),
    val similarTvShows: List<TmdbTvShow> = emptyList(),
    val credits: TmdbCredits? = null,
    val streams: List<ChqStream> = emptyList(),
    val inWatchlist: Boolean = false,
    val isFavorite: Boolean = false,
    val loading: Boolean = true,
    val loadingStreams: Boolean = false,
    /**
     * True when the source resolution has finished — with sources or without.
     *
     * The screen needs to tell "the lookup is still running" from "the lookup
     * found nothing": without this flag a movie would flash "No sources found"
     * for the moment between the detail arriving and the lookup starting.
     */
    val streamsResolved: Boolean = false,
    val error: String? = null,
    /** The seasons of a series, `1..numberOfSeasons`. Empty for a movie. */
    val seasons: List<Int> = emptyList(),
    /** The season whose episodes are on screen. */
    val selectedSeason: Int = 1,
    /** The episodes of [selectedSeason]. */
    val episodes: List<TmdbSeasonEpisode> = emptyList(),
    val loadingEpisodes: Boolean = false,
    /**
     * The YouTube key of the trailer, when the title has one.
     *
     * The original's detail screen has a "Watch Trailer" button
     * (`decoded/smali/com/app/mlounge/ui/screens/movies/a.smali:5602`) that opens
     * `https://www.youtube.com/watch?v=<key>`.
     */
    val trailerKey: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    private val streamsRepository: StreamsRepository,
    private val watchlistDao: WatchlistDao,
    private val favoriteDao: FavoriteDao,
    savedStateHandle: SavedStateHandle,
) : SafeViewModel() {

    // The original registers the detail routes as `movie_detail/{movieId}` and
    // `tvshow_detail/{tvId}`, both with an Int argument. The replica shares one screen
    // for both, so the id is read from whichever argument is present.
    private val movieId: Int? = savedStateHandle.get<Int>("movieId")
        ?: savedStateHandle.get<String>("movieId")?.toIntOrNull()
    private val tvId: Int? = savedStateHandle.get<Int>("tvId")
        ?: savedStateHandle.get<String>("tvId")?.toIntOrNull()

    private val mediaId: Int = movieId ?: tvId ?: 0
    private val mediaType: String = if (movieId != null) "movie" else "tv"

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        // The type and the id are known before anything is loaded, so the screen
        // can draw the right thing from the first frame.
        _uiState.value = DetailUiState(mediaType = mediaType, mediaId = mediaId)
        load()
    }

    fun load() {
        scope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            if (mediaId <= 0) {
                // The route carried no usable id. Asking TMDB for id 0 would
                // only produce a 404, so the screen is told the truth instead.
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "Couldn't load details",
                )
                return@launch
            }
            if (mediaType == "tv") {
                tmdbRepository.tvDetail(mediaId).onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        tvShow = detail,
                        similarTvShows = detail.similar.results,
                        credits = detail.credits,
                        trailerKey = detail.videos.trailerKey(),
                        loading = false,
                    )
                    // A series is resolved per episode, not per series: the
                    // backend looks a bare id up in its *movie* table, so a
                    // lookup without a season and an episode would answer with
                    // a completely different title. The lookup therefore
                    // happens on the sources screen, once an episode is picked.
                    //
                    // A series is played per episode, so the screen needs the
                    // season list and the episodes of the season that is open.
                    val seasons = (1..detail.numberOfSeasons.coerceAtLeast(0)).toList()
                    _uiState.value = _uiState.value.copy(seasons = seasons)
                    if (seasons.isNotEmpty()) selectSeason(seasons.first())
                }.onFailure {
                    Logger.e(TAG, "Couldn't load the series $mediaId", it)
                    _uiState.value = _uiState.value.copy(loading = false, error = "Couldn't load details")
                }
            } else {
                tmdbRepository.movieDetail(mediaId).onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        movie = detail,
                        similarMovies = detail.similar.results,
                        credits = detail.credits,
                        trailerKey = detail.videos.trailerKey(),
                        loading = false,
                    )
                    loadStreams(detail.imdbId)
                }.onFailure {
                    Logger.e(TAG, "Couldn't load the movie $mediaId", it)
                    _uiState.value = _uiState.value.copy(loading = false, error = "Couldn't load details")
                }
            }
            refreshWatchlistState()
        }
    }

    /**
     * The YouTube key of the trailer of a title, when it has one.
     *
     * The original's "Watch Trailer" button opens
     * `https://www.youtube.com/watch?v=<key>`, and the key comes from TMDB's
     * `videos` block. A trailer is preferred over a teaser, and a teaser over
     * a clip, but any YouTube video will do when the title has nothing else.
     */
    private fun TmdbVideos?.trailerKey(): String? {
        val videos = this?.results.orEmpty().filter {
            it.key?.isNotBlank() == true && it.site?.equals("YouTube", ignoreCase = true) == true
        }
        if (videos.isEmpty()) return null
        val trailer = videos.firstOrNull { it.type?.equals("Trailer", ignoreCase = true) == true }
            ?: videos.firstOrNull { it.type?.equals("Teaser", ignoreCase = true) == true }
            ?: videos.first()
        return trailer.key
    }

    /**
     * Opens a season of a series and loads its episodes.
     *
     * The sources of a series are resolved per episode, so the screen has to
     * know which episode the user picked before it can ask for a source.
     */
    fun selectSeason(season: Int) {
        _uiState.value = _uiState.value.copy(selectedSeason = season, loadingEpisodes = true)
        scope.launch {
            tmdbRepository.tvSeasonEpisodes(mediaId, season).onSuccess { episodes ->
                _uiState.value = _uiState.value.copy(
                    episodes = episodes,
                    loadingEpisodes = false,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(episodes = emptyList(), loadingEpisodes = false)
            }
        }
    }

    private fun loadStreams(imdbId: String?) {
        scope.launch {
            _uiState.value = _uiState.value.copy(loadingStreams = true)
            try {
                val streams = streamsRepository.fetchAll(
                    tmdbId = mediaId,
                    mediaType = mediaType,
                    imdbId = imdbId,
                )
                _uiState.value = _uiState.value.copy(
                    streams = streams,
                    loadingStreams = false,
                    streamsResolved = true,
                )
            } catch (e: Exception) {
                // A failing stream lookup must not take the screen down with it.
                Logger.e(TAG, "Failed to load the streams of $mediaId", e)
                _uiState.value = _uiState.value.copy(
                    streams = emptyList(),
                    loadingStreams = false,
                    streamsResolved = true,
                )
            }
        }
    }

    private suspend fun refreshWatchlistState() {
        val inWatchlist = watchlistDao.contains(mediaId, mediaType)
        val isFavorite = favoriteDao.contains(mediaId, mediaType)
        _uiState.value = _uiState.value.copy(inWatchlist = inWatchlist, isFavorite = isFavorite)
    }

    fun toggleWatchlist() {
        scope.launch {
            val state = _uiState.value
            val title = state.movie?.title ?: state.tvShow?.name ?: return@launch
            val poster = state.movie?.posterPath ?: state.tvShow?.posterPath
            val backdrop = state.movie?.backdropPath ?: state.tvShow?.backdropPath
            val overview = state.movie?.overview ?: state.tvShow?.overview
            val rating = state.movie?.voteAverage ?: state.tvShow?.voteAverage ?: 0.0
            val release = state.movie?.releaseDate ?: state.tvShow?.firstAirDate
            // The original's "Include Adult in Favourites" switch needs to know
            // whether the title is an adult one, so the flag travels with the row.
            val isAdult = state.movie?.adult ?: state.tvShow?.adult ?: false

            if (state.inWatchlist) {
                watchlistDao.delete(mediaId, mediaType)
            } else {
                watchlistDao.upsert(
                    WatchlistEntity(
                        tmdbId = mediaId,
                        mediaType = mediaType,
                        title = title,
                        posterPath = poster,
                        backdropPath = backdrop,
                        overview = overview,
                        rating = rating,
                        releaseDate = release,
                        isAdult = isAdult,
                    ),
                )
            }
            refreshWatchlistState()
        }
    }

    fun toggleFavorite() {
        scope.launch {
            val state = _uiState.value
            val title = state.movie?.title ?: state.tvShow?.name ?: return@launch
            if (state.isFavorite) {
                favoriteDao.delete(mediaId, mediaType)
            } else {
                favoriteDao.upsert(
                    FavoriteEntity(
                        tmdbId = mediaId,
                        mediaType = mediaType,
                        title = title,
                        posterPath = state.movie?.posterPath ?: state.tvShow?.posterPath,
                        overview = state.movie?.overview ?: state.tvShow?.overview,
                        rating = state.movie?.voteAverage ?: state.tvShow?.voteAverage ?: 0.0,
                        releaseDate = state.movie?.releaseDate ?: state.tvShow?.firstAirDate,
                    ),
                )
            }
            refreshWatchlistState()
        }
    }

    companion object {
        private const val TAG = "DetailViewModel"
    }
}
