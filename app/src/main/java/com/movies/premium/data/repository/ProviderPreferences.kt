package com.movies.premium.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * The providers the user switched off.
 *
 * The narrow view of the preference store that the stream resolution needs. It
 * is an interface so that the repository can be exercised in a plain JVM test
 * without an Android `Context` — see
 * `app/src/test/java/com/movies/premium/data/repository/StreamsRepositoryTest.kt`.
 *
 * The original keeps the same data in the `disabled_providers` preference
 * (`com.app.mlounge.data.local.prefs.a2`, field `g1`), which the "Sources
 * (http)", "Sources (Torrents)" and "Sources (Anime)" sections of the settings
 * screen write.
 */
interface ProviderPreferences {

    /**
     * The names of the providers the user switched off.
     *
     * A provider in this set is not asked for its sources any more.
     */
    val disabledProviders: Flow<Set<String>>
}
