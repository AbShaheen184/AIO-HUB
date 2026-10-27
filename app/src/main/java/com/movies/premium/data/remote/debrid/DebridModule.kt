package com.movies.premium.data.remote.debrid

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * The base urls of the five debrid services.
 *
 * They are the original's (`com.app.mlounge.data.remote.debrid`); they are duplicated
 * here so this package does not depend on `di/Endpoints`, which the rest of the app
 * keeps for its other services.
 */
object DebridEndpoints {
    const val ALLDEBRID = "https://api.alldebrid.com/"
    const val REALDEBRID = "https://api.real-debrid.com/"
    const val TORBOX = "https://api.torbox.app/"
    const val PREMIUMIZE = "https://www.premiumize.me/api/"
    const val OFFCLOUD = "https://offcloud.com/"
}

/**
 * The DI graph of the debrid layer.
 *
 * The five managers and the registry are `@Inject` constructed, so only the Retrofit
 * instances have to be provided.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebridModule {

    /**
     * The original registers `GsonConverterFactory` on every Retrofit instance, so the
     * wire models stay Gson-annotated.
     */
    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideDebridAllDebridApi(client: OkHttpClient): AllDebridApi =
        retrofit(DebridEndpoints.ALLDEBRID, client).create(AllDebridApi::class.java)

    @Provides
    @Singleton
    fun provideDebridRealDebridApi(client: OkHttpClient): RealDebridApi =
        retrofit(DebridEndpoints.REALDEBRID, client).create(RealDebridApi::class.java)

    @Provides
    @Singleton
    fun provideDebridTorBoxApi(client: OkHttpClient): TorBoxApi =
        retrofit(DebridEndpoints.TORBOX, client).create(TorBoxApi::class.java)

    @Provides
    @Singleton
    fun provideDebridPremiumizeApi(client: OkHttpClient): PremiumizeApi =
        retrofit(DebridEndpoints.PREMIUMIZE, client).create(PremiumizeApi::class.java)

    @Provides
    @Singleton
    fun provideDebridOffCloudApi(client: OkHttpClient): OffCloudApi =
        retrofit(DebridEndpoints.OFFCLOUD, client).create(OffCloudApi::class.java)
}
