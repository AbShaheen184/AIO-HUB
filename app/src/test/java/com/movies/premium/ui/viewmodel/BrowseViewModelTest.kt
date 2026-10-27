package com.movies.premium.ui.viewmodel

import com.movies.premium.data.remote.api.TmdbApi
import com.movies.premium.data.remote.model.TmdbGenre
import com.movies.premium.data.remote.model.TmdbGenreList
import com.movies.premium.data.remote.model.TmdbMovie
import com.movies.premium.data.remote.model.TmdbMovieDetailResponse
import com.movies.premium.data.remote.model.TmdbPagedResponse
import com.movies.premium.data.remote.model.TmdbPersonDetail
import com.movies.premium.data.remote.model.TmdbTvDetailResponse
import com.movies.premium.data.remote.model.TmdbTvShow
import com.movies.premium.data.repository.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * The browse screen of the movies and of the TV shows.
 *
 * These tests pin down the behaviour that the browse screen depends on, and
 * that used to be broken:
 *
 * * the grid used to **spin forever**, because the view model never loaded
 *   the first page — `setMode` returned early when the requested mode was
 *   already the current one, and the initialiser did not load either;
 * * the **year filter** did not exist, although the original's browse screen
 *   has one;
 * * the **genre list is per mode**: the TV Shows screen must not show the
 *   genres of the movies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

    /**
     * The scheduler is shared between the test body and the main dispatcher.
     *
     * The view model runs on `viewModelScope`, which uses `Dispatchers.Main`,
     * so the main dispatcher has to be a test dispatcher. If it had a
     * scheduler of its own, `runTest` would complain about two different
     * schedulers.
     */
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakeTmdbApi) = BrowseViewModel(TmdbRepository(api))

    @Test
    fun `the first page is loaded when the screen asks for the movies`() = runTest(dispatcher) {
        val api = FakeTmdbApi()
        val model = viewModel(api)

        model.setMode(tv = false)

        val state = model.uiState.value
        assertTrue("the movies never arrived", state.movies.isNotEmpty())
        assertTrue("the grid would spin forever", !state.loading)
        assertNull(state.error)
    }

    @Test
    fun `asking for the same mode again does not throw the list away`() = runTest(dispatcher) {
        val api = FakeTmdbApi()
        val model = viewModel(api)

        model.setMode(tv = false)
        val before = model.uiState.value.movies.size

        model.setMode(tv = false)

        assertEquals(before, model.uiState.value.movies.size)
        assertEquals("the page was fetched twice", 1, api.movieCalls)
    }

    @Test
    fun `switching to the shows loads the shows and their own genres`() = runTest(dispatcher) {
        val api = FakeTmdbApi()
        val model = viewModel(api)

        model.setMode(tv = false)
        model.setMode(tv = true)

        val state = model.uiState.value
        assertTrue("the shows never arrived", state.tvShows.isNotEmpty())
        assertTrue("the movies of the other mode are still on screen", state.movies.isEmpty())
        assertEquals("the tv genres were not fetched", 1, api.tvGenreCalls)
    }

    @Test
    fun `the year filter is handed to the api`() = runTest(dispatcher) {
        val api = FakeTmdbApi()
        val model = viewModel(api)

        model.setMode(tv = false)
        model.selectYear(2021)

        assertEquals(2021, api.lastYear)
        assertEquals(2021, model.uiState.value.selectedYear)
    }

    @Test
    fun `clearing the year filter hands no year to the api`() = runTest(dispatcher) {
        val api = FakeTmdbApi()
        val model = viewModel(api)

        model.setMode(tv = false)
        model.selectYear(2021)
        // The "All" chip of the year row clears the filter.
        model.selectYear(null)

        assertNull("the year filter was not cleared", model.uiState.value.selectedYear)
        assertNull(api.lastYear)
    }

    @Test
    fun `a failed request shows the error instead of an empty screen`() = runTest(dispatcher) {
        val api = FakeTmdbApi(fail = true)
        val model = viewModel(api)

        model.setMode(tv = false)

        val state = model.uiState.value
        assertNotNull("the failure was swallowed", state.error)
        assertTrue(state.movies.isEmpty())
        assertTrue(!state.loading)
    }

    @Test
    fun `the genre filter is handed to the api`() = runTest(dispatcher) {
        val api = FakeTmdbApi()
        val model = viewModel(api)

        model.setMode(tv = false)
        model.selectGenre(TmdbGenre(id = 28, name = "Action"))

        assertEquals("28", api.lastGenres)
        assertEquals(28, model.uiState.value.selectedGenre?.id)
    }
}

/**
 * A TMDB API that answers from memory.
 *
 * It records what it was asked for, so that the test can check that the
 * filters of the browse screen really reach the network layer.
 */
private class FakeTmdbApi(
    private val fail: Boolean = false,
) : TmdbApi {

    var movieCalls = 0
    var tvGenreCalls = 0
    var lastYear: Int? = null
    var lastGenres: String? = null

    private fun check() {
        if (fail) throw java.io.IOException("no network")
    }

    override suspend fun discoverMovies(
        page: Int,
        sortBy: String?,
        genres: String?,
        year: Int?,
        includeAdult: Boolean,
        releaseDateGte: String?,
        releaseDateLte: String?,
    ): TmdbPagedResponse<TmdbMovie> {
        check()
        movieCalls++
        lastYear = year
        lastGenres = genres
        return TmdbPagedResponse(
            page = page,
            results = listOf(TmdbMovie(id = 1, title = "A movie")),
            totalPages = 1,
        )
    }

    override suspend fun discoverTvShows(
        page: Int,
        sortBy: String?,
        genres: String?,
        year: Int?,
        includeAdult: Boolean,
    ): TmdbPagedResponse<TmdbTvShow> {
        check()
        lastYear = year
        lastGenres = genres
        return TmdbPagedResponse(
            page = page,
            results = listOf(TmdbTvShow(id = 2, name = "A show")),
            totalPages = 1,
        )
    }

    override suspend fun trendingMovies(page: Int) = TmdbPagedResponse<TmdbMovie>()

    override suspend fun trendingTvShows(page: Int) = TmdbPagedResponse<TmdbTvShow>()

    override suspend fun searchMovies(query: String, page: Int, includeAdult: Boolean) =
        TmdbPagedResponse<TmdbMovie>()

    override suspend fun searchTvShows(query: String, page: Int, includeAdult: Boolean) =
        TmdbPagedResponse<TmdbTvShow>()

    override suspend fun searchMulti(query: String, page: Int, includeAdult: Boolean) =
        TmdbPagedResponse<TmdbMovie>()

    override suspend fun movieDetail(id: Int, append: String) = TmdbMovieDetailResponse()

    override suspend fun tvDetail(id: Int, append: String) = TmdbTvDetailResponse()

    override suspend fun tvSeason(id: Int, season: Int): Response<ResponseBody> =
        Response.success("".toResponseBody())

    override suspend fun personDetail(id: Int, append: String) = TmdbPersonDetail()

    override suspend fun movieGenres() = TmdbGenreList(
        genres = listOf(TmdbGenre(id = 28, name = "Action")),
    )

    override suspend fun tvGenres(): TmdbGenreList {
        tvGenreCalls++
        return TmdbGenreList(genres = listOf(TmdbGenre(id = 10765, name = "Sci-Fi")))
    }
}
