package com.movies.premium

import android.app.Application
import android.content.Context
import android.os.Build
import android.security.NetworkSecurityPolicy
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import okio.Path.Companion.toOkioPath
import com.movies.premium.util.AppContext
import com.movies.premium.util.Logger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application entry point.
 *
 * The original also initialised the Appsalt reward SDK and the Hopmn "moneytiser"
 * bandwidth-sharing SDK here. Both are deliberately absent from this build: they
 * monetise the user's connection and are not part of the playback experience.
 */
@HiltAndroidApp
class CinemaHQApp : Application(), SingletonImageLoader.Factory {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppContext.app = applicationContext

        // Match the original's DNS caching behaviour.
        try {
            java.security.Security.setProperty("networkaddress.cache.ttl", "300")
            java.security.Security.setProperty("networkaddress.cache.negative.ttl", "10")
        } catch (_: Exception) {
        }

        Logger.i("AIO-Hub", "Application started (build ${BuildConfig.VERSION_NAME})")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                add(OkHttpNetworkFetcherFactory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(160L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
