package com.movies.premium.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The games the user marked as a favourite.
 *
 * The original keeps them in a Room table (`favourite_games`, with the columns
 * `slug`, `platform`, `name`, `posterUrl` and `year` — the original's
 * `FavouriteGameEntity`). The replica's Room database is at version 3 and
 * adding a table would mean a migration, so the games live in their own
 * DataStore file instead: a small JSON array, written with the Gson that is
 * already on the classpath.
 *
 * The file is separate from `AppPreferences` on purpose — that store belongs to
 * the whole app and is written by other screens as well.
 */
private val Context.gameFavouritesStore: DataStore<Preferences> by
    preferencesDataStore(name = "cinemahq_game_favourites")

/** One favourited game. */
data class FavouriteGame(
    val slug: String,
    /** The API path of the platform — `snes`, `nes` or `sega-genesis`. */
    val platform: String,
    val name: String,
    val posterUrl: String? = null,
    val year: String? = null,
)

/**
 * What is written to the store.
 *
 * Every field is nullable, because Gson writes the object without calling the
 * constructor: a field that is missing from the JSON would stay null even when
 * the Kotlin type says otherwise.
 */
private data class StoredFavourite(
    val slug: String? = null,
    val platform: String? = null,
    val name: String? = null,
    val posterUrl: String? = null,
    val year: String? = null,
)

/**
 * Reads and writes the favourite games.
 *
 * The store is not injected: the view model that owns it creates it with the
 * application context, so no Hilt binding is needed.
 */
class GameFavouritesStore(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val FAVOURITE_GAMES = stringPreferencesKey("favourite_games")
    }

    /** The favourites, newest first. */
    val favourites: Flow<List<FavouriteGame>> =
        context.gameFavouritesStore.data.map { prefs -> decode(prefs[Keys.FAVOURITE_GAMES]) }

    /**
     * Adds [favourite] when it is not in the list yet and removes it when it is.
     *
     * The original's `favourite_games` table is keyed by the slug *and* the
     * platform, so the same game on two platforms is two entries.
     */
    suspend fun toggle(favourite: FavouriteGame) {
        context.gameFavouritesStore.edit { prefs ->
            val current = decode(prefs[Keys.FAVOURITE_GAMES]).toMutableList()
            val index = current.indexOfFirst {
                it.slug == favourite.slug && it.platform == favourite.platform
            }
            if (index >= 0) {
                current.removeAt(index)
            } else {
                current.add(0, favourite)
            }
            prefs[Keys.FAVOURITE_GAMES] = gson.toJson(
                current.map {
                    StoredFavourite(
                        slug = it.slug,
                        platform = it.platform,
                        name = it.name,
                        posterUrl = it.posterUrl,
                        year = it.year,
                    )
                },
            )
        }
    }

    /** Removes one game from the list. */
    suspend fun remove(slug: String, platform: String) {
        context.gameFavouritesStore.edit { prefs ->
            val current = decode(prefs[Keys.FAVOURITE_GAMES])
                .filterNot { it.slug == slug && it.platform == platform }
            prefs[Keys.FAVOURITE_GAMES] = gson.toJson(
                current.map {
                    StoredFavourite(
                        slug = it.slug,
                        platform = it.platform,
                        name = it.name,
                        posterUrl = it.posterUrl,
                        year = it.year,
                    )
                },
            )
        }
    }

    /** Turns the stored JSON back into the favourites. */
    private fun decode(raw: String?): List<FavouriteGame> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<StoredFavourite>>() {}.type
            gson.fromJson<List<StoredFavourite>>(raw, type).orEmpty()
        }.getOrDefault(emptyList())
            .mapNotNull { stored ->
                val slug = stored.slug ?: return@mapNotNull null
                FavouriteGame(
                    slug = slug,
                    platform = stored.platform.orEmpty(),
                    name = stored.name.orEmpty(),
                    posterUrl = stored.posterUrl,
                    year = stored.year,
                )
            }
    }
}
