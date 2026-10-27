package com.movies.premium.data.remote.simkl

import com.movies.premium.data.local.prefs.AppPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

/** The addresses of the SIMKL API. */
object SimklEndpoints {
    const val SIMKL = "https://api.simkl.com/"
}

/**
 * The DI wiring of the SIMKL service layer.
 *
 * Mirrors the original's SIMKL wiring, which is spread over
 * `com.app.mlounge.di.g` (the Retrofit instance and the base url) and
 * `com.app.mlounge.di.d` (the `simkl-api-key` interceptor). The original keeps both in
 * the application's own DI module; the replica keeps them in the package they belong
 * to, the same way `data/remote/debrid/DebridModule.kt` does, so that the shared
 * `di/NetworkModule.kt` does not have to know about SIMKL.
 *
 * Three things are provided:
 *
 * - a **SIMKL OkHttp client** (`@Named("simkl")`) that carries the `simkl-api-key`
 *   header. The header value is read from a [SimklClientIdCache], so the OkHttp
 *   thread is never blocked;
 * - the **[SimklApi]** Retrofit instance for `https://api.simkl.com/`;
 * - the **[SimklClientIdProvider]**, bound to the preferences store.
 *
 * **The API key is not shipped.** The original hard-codes its own SIMKL client id; the
 * replica expects the user to register one at `https://simkl.com/account/apiclients` and
 * enter it in the app. It is stored in the `simkl_client_id` preference — see
 * [SimklClientIdProvider].
 */
@Module
@InstallIn(SingletonComponent::class)
object SimklModule {

    /**
     * The scope the client-id cache is filled from.
     *
     * The cache has to be filled *before* the first SIMKL request goes out, and the
     * only way to do that without blocking an OkHttp thread is to keep a collector
     * running for as long as the process lives.
     */
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The client used for SIMKL; carries the `simkl-api-key` header.
     *
     * The base client is the one `di/NetworkModule.kt` provides, so the logging
     * interceptor and the timeouts are shared.
     */
    @Provides
    @Singleton
    @Named("simkl")
    fun provideSimklClient(base: OkHttpClient, cache: SimklClientIdCache): OkHttpClient =
        base.newBuilder()
            .addInterceptor(SimklApiKeyInterceptor { cache.get() })
            .build()

    /**
     * The holder of the last known client id.
     *
     * It is filled from the preferences store as soon as the app starts, so the
     * interceptor always has a value to send.
     */
    @Provides
    @Singleton
    fun provideSimklClientIdCache(preferences: AppPreferences): SimklClientIdCache {
        val cache = SimklClientIdCache()
        cache.observe(preferences.simklClientId, cacheScope)
        return cache
    }

    /** Where the user's SIMKL client id comes from. */
    @Provides
    @Singleton
    fun provideSimklClientIdProvider(
        preferences: AppPreferences,
        cache: SimklClientIdCache,
    ): SimklClientIdProvider = FlowSimklClientIdProvider(
        source = preferences.simklClientId,
        sink = { preferences.setSimklClientId(it) },
        cache = cache,
    )

    /**
     * The original registers `GsonConverterFactory` on every Retrofit instance, so the
     * wire models must stay Gson-annotated.
     */
    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /** The SIMKL API. */
    @Provides
    @Singleton
    fun provideSimklApi(@Named("simkl") client: OkHttpClient): SimklApi =
        retrofit(SimklEndpoints.SIMKL, client).create(SimklApi::class.java)
}
