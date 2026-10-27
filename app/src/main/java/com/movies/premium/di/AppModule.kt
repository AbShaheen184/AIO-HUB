package com.movies.premium.di

import android.content.Context
import com.movies.premium.data.local.AppDatabase
import com.movies.premium.data.local.dao.ContinueWatchingDao
import com.movies.premium.data.local.dao.DownloadDao
import com.movies.premium.data.local.dao.FavoriteDao
import com.movies.premium.data.local.dao.HistoryDao
import com.movies.premium.data.local.dao.IptvChannelDao
import com.movies.premium.data.local.dao.WatchlistDao
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.data.download.DownloadManager
import com.movies.premium.data.repository.ProviderPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.get(context)

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): AppPreferences = AppPreferences(context)

    /**
     * The narrow view of the preference store that the stream resolution needs:
     * the providers the user switched off.
     */
    @Provides
    @Singleton
    fun provideProviderPreferences(preferences: AppPreferences): ProviderPreferences = preferences

    @Provides
    fun provideWatchlistDao(db: AppDatabase): WatchlistDao = db.watchlistDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideDownloadDao(db: AppDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideContinueWatchingDao(db: AppDatabase): ContinueWatchingDao = db.continueWatchingDao()

    @Provides
    fun provideIptvChannelDao(db: AppDatabase): IptvChannelDao = db.iptvChannelDao()

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        dao: DownloadDao,
        preferences: AppPreferences,
        client: OkHttpClient,
    ): DownloadManager = DownloadManager(context, dao, preferences, client)
}
