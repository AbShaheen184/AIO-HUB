package com.movies.premium.data.repository

import com.google.gson.GsonBuilder
import com.movies.premium.data.remote.api.ChqApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The stream resolution is the one place where the app used to die.
 *
 * A failing lookup ran inside a `viewModelScope`, where an uncaught exception
 * reaches the *global* uncaught-exception handler and kills the process — the
 * user saw the app force-close the moment a title was opened. The repository
 * therefore never throws: every failure ends up as an empty list.
 *
 * These tests pin that contract down. The "live" cases talk to the real
 * backend, the "broken" cases point the API at a host that cannot answer, and
 * the assertion is always the same: **no exception escapes**.
 */
class StreamsRepositoryTest {

    /**
     * The timeouts of the app's own HTTP client (`di/NetworkModule.kt`).
     *
     * The live backend is slow: `api/streams/1396/1/1` — one episode of one
     * series — takes about **11 seconds** to answer, because the server asks
     * four providers in turn. A test client with a 20 second read timeout was
     * therefore failing at random: the call was cut off, the repository fell
     * back to the legacy routes (which answer 404), and the episode resolved
     * to nothing. The app itself was never affected — it waits 45 seconds.
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private fun api(baseUrl: String): ChqApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()
        .create(ChqApi::class.java)

    private fun repository(
        baseUrl: String = LIVE,
        disabled: Set<String> = emptySet(),
    ) = StreamsRepository(api(baseUrl), preferences(disabled))

    /** A stand-in for the preference store, so the test needs no Android context. */
    private fun preferences(disabled: Set<String>) = object : ProviderPreferences {
        override val disabledProviders: Flow<Set<String>> = flowOf(disabled)
    }

    @Test
    fun `a movie resolves to a list of sources`() = runTest(timeout = 2.minutes) {
        val streams = repository().fetchAll(tmdbId = 550, mediaType = "movie")

        assertTrue("the movie should have at least one source", streams.isNotEmpty())
        assertTrue("every source needs a url", streams.all { !it.url.isNullOrBlank() })
        assertTrue("every source needs a name", streams.all { !it.name.isNullOrBlank() })
    }

    @Test
    fun `a series resolves when the season and the episode are known`() = runTest(timeout = 2.minutes) {
        val streams = repository().fetchAll(tmdbId = 1396, mediaType = "tv", season = 1, episode = 1)

        assertTrue("the episode should have at least one source", streams.isNotEmpty())
        assertTrue("every source needs a url", streams.all { !it.url.isNullOrBlank() })
    }

    @Test
    fun `a series without an episode resolves to nothing instead of the wrong film`() = runTest(timeout = 2.minutes) {
        // The backend looks a bare id up in its *movie* table, so asking without
        // a season and an episode would answer with a completely different title.
        val streams = repository().fetchAll(tmdbId = 1396, mediaType = "tv")

        assertTrue("a series without an episode must not resolve", streams.isEmpty())
    }

    @Test
    fun `a backend that answers 404 does not throw`() = runTest(timeout = 2.minutes) {
        // This is the exact failure that used to close the app: the original's
        // per-provider routes answer 404 and Retrofit turns that into an
        // `HttpException`.
        val streams = repository(BROKEN_PATH).fetchAll(tmdbId = 550, mediaType = "movie")

        assertTrue("a 404 backend must resolve to nothing, not to an exception", streams.isEmpty())
    }

    @Test
    fun `a backend that cannot be reached does not throw`() = runTest(timeout = 2.minutes) {
        val streams = repository(UNREACHABLE).fetchAll(tmdbId = 550, mediaType = "movie")

        assertTrue("an unreachable backend must resolve to nothing", streams.isEmpty())
    }

    @Test
    fun `a single provider that fails does not throw`() = runTest(timeout = 2.minutes) {
        val streams = repository(BROKEN_PATH).fetchFrom(provider = "notorrent", tmdbId = 550)

        assertTrue(streams.isEmpty())
    }

    @Test
    fun `a single provider that works returns its sources`() = runTest(timeout = 2.minutes) {
        val streams = repository().fetchFrom(provider = "notorrent", tmdbId = 550)

        assertTrue("the notorrent provider should answer for Fight Club", streams.isNotEmpty())
    }

    @Test
    fun `an unknown provider does not throw`() = runTest(timeout = 2.minutes) {
        val streams = repository().fetchFrom(provider = "not-a-provider", tmdbId = 550)

        assertTrue("an unknown provider must not answer", streams.isEmpty())
    }

    @Test
    fun `a provider the user switched off is not asked`() = runTest(timeout = 2.minutes) {
        val streams = repository(disabled = setOf("hdhub"))
            .fetchAll(tmdbId = 550, mediaType = "movie")

        assertTrue("the sources must still resolve", streams.isNotEmpty())
        assertTrue(
            "no source of a switched-off provider may be returned",
            streams.none { it.provider == "hdhub" },
        )
    }

    @Test
    fun `a switched-off provider answers nothing on its own`() = runTest(timeout = 2.minutes) {
        val streams = repository(disabled = setOf("hdhub"))
            .fetchFrom(provider = "hdhub", tmdbId = 550)

        assertTrue("a switched-off provider must not answer", streams.isEmpty())
    }

    private companion object {
        const val LIVE = "https://chq-api.ddns.me/"
        /** Every path under this prefix answers 404. */
        const val BROKEN_PATH = "https://chq-api.ddns.me/this-route-does-not-exist/"
        /** Nothing listens on port 9, so the connection is refused. */
        const val UNREACHABLE = "http://127.0.0.1:9/"
    }
}
