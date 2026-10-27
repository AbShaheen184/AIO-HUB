package com.movies.premium.di

import com.movies.premium.data.remote.api.AniListApi
import com.movies.premium.data.remote.api.AnikotoApi
import com.movies.premium.data.remote.api.ChqApi
import com.movies.premium.data.remote.api.ChqNtvApi
import com.movies.premium.data.remote.api.DeezerApi
import com.movies.premium.data.remote.api.NtvApi
import com.movies.premium.data.remote.api.TmdbApi
import com.movies.premium.data.remote.trakt.TraktApi
import com.movies.premium.util.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

object Endpoints {
    const val CHQ = "http://192.168.3.143:8787/"
    const val TMDB = "https://api.themoviedb.org/3/"
    const val NTV = "https://ntv.cx/"
    const val TRAKT = "https://api.trakt.tv/"
    const val SIMKL = "https://api.simkl.com/"
    const val ANILIST = "https://graphql.anilist.co/"
    const val ALLDEBRID = "https://api.alldebrid.com/"
    const val REALDEBRID = "https://api.real-debrid.com/"
    const val TORBOX = "https://api.torbox.app/"
    const val PREMIUMIZE = "https://www.premiumize.me/api/"
    const val DEEZER = "https://api.deezer.com/"
    const val JIKAN = "https://api.jikan.moe/v4/"
    const val ANIKOTO = "https://anikoto.ddns.me/api/"

    const val TMDB_API_KEY = "f3c627493095a7e40ceca68355c94c6d"
}

/** Appends the TMDB api_key query parameter, mirroring the original interceptor. */
class TmdbApiKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val url = original.url.newBuilder()
            .addQueryParameter("api_key", Endpoints.TMDB_API_KEY)
            .build()
        return chain.proceed(original.newBuilder().url(url).build())
    }
}

/** Adds a bearer token when one is present in [tokenProvider]. */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = tokenProvider() ?: return chain.proceed(chain.request())
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}

/**
 * Adds the headers every Trakt.tv request needs.
 *
 * Mirrors the original's `com.app.mlounge.di.g` interceptor, which adds
 * `trakt-api-version: 2` and `Content-Type: application/json` to every call.
 *
 * The original also sends `trakt-api-key: <TraktManager.CLIENT_ID>`. That value is the
 * original *developer's* Trakt application id, so the replica does not ship it — a
 * request authenticated with the user's own OAuth access token does not need it.
 * See `data/remote/trakt/TraktManager.kt`.
 */
class TraktHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()
            .addHeader("trakt-api-version", "2")
            .addHeader("Content-Type", "application/json")
            .build()
        return chain.proceed(request)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor { message -> Logger.d("OkHttp", message) }
        logging.level = HttpLoggingInterceptor.Level.BASIC
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Client used for TMDB; carries the api_key query parameter. */
    @Provides
    @Singleton
    @Named("tmdb")
    fun provideTmdbClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder().addInterceptor(TmdbApiKeyInterceptor()).build()

    @Provides
    @Singleton
    @Named("tmdbAuth")
    fun provideTmdbAuthClient(base: OkHttpClient): OkHttpClient = base

    /**
     * The original registers `GsonConverterFactory` on every Retrofit instance,
     * so the wire models must stay Gson-annotated.
     */
    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideChqApi(client: OkHttpClient): ChqApi =
        retrofit(Endpoints.CHQ, client).create(ChqApi::class.java)

    @Provides
    @Singleton
    fun provideChqNtvApi(client: OkHttpClient): ChqNtvApi =
        retrofit(Endpoints.CHQ, client).create(ChqNtvApi::class.java)

    @Provides
    @Singleton
    fun provideTmdbApi(@Named("tmdb") client: OkHttpClient): TmdbApi =
        retrofit(Endpoints.TMDB, client).create(TmdbApi::class.java)

    @Provides
    @Singleton
    fun provideNtvApi(client: OkHttpClient): NtvApi =
        retrofit(Endpoints.NTV, client).create(NtvApi::class.java)

    /** Client used for Trakt; carries the `trakt-api-version` header. */
    @Provides
    @Singleton
    @Named("trakt")
    fun provideTraktClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder().addInterceptor(TraktHeadersInterceptor()).build()

    @Provides
    @Singleton
    fun provideTraktApi(@Named("trakt") client: OkHttpClient): TraktApi =
        retrofit(Endpoints.TRAKT, client).create(TraktApi::class.java)

    @Provides
    @Singleton
    fun provideAniListApi(client: OkHttpClient): AniListApi =
        retrofit(Endpoints.ANILIST, client).create(AniListApi::class.java)

    @Provides
    @Singleton
    fun provideDeezerApi(client: OkHttpClient): DeezerApi =
        retrofit(Endpoints.DEEZER, client).create(DeezerApi::class.java)

    @Provides
    @Singleton
    fun provideAnikotoApi(client: OkHttpClient): AnikotoApi =
        retrofit(Endpoints.ANIKOTO, client).create(AnikotoApi::class.java)
}
