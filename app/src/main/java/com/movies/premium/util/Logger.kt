package com.movies.premium.util

import android.content.Context
import android.util.Log
import com.movies.premium.BuildConfig

/** Application-wide logger. Mirrors the original's `com.app.mlounge.util.a` helper. */
object Logger {
    private const val DEFAULT_TAG = "AIO-Hub"

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        Log.w(tag, msg, t)
    }
}

/** Holds the application context, mirroring the original's global `util.a.a` field. */
object AppContext {
    @Volatile
    var app: Context? = null
        internal set

    fun require(): Context = app ?: error("Application context not initialised")
}
