package com.movies.premium.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.movies.premium.data.anime.AnikotoScraper
import com.movies.premium.data.remote.api.AnikotoApi
import com.movies.premium.data.remote.model.HiAnimeCategoryData
import com.movies.premium.data.remote.model.HiAnimeDetailData
import com.movies.premium.data.remote.model.HiAnimeEpCount
import com.movies.premium.data.remote.model.HiAnimeEpisodesData
import com.movies.premium.data.remote.model.HiAnimeEpisode
import com.movies.premium.data.remote.model.HiAnimeGenresData
import com.movies.premium.data.remote.model.HiAnimeHome
import com.movies.premium.data.remote.model.HiAnimeInfo
import com.movies.premium.data.remote.model.HiAnimeItem
import com.movies.premium.data.remote.model.HiAnimeMoreInfo
import com.movies.premium.data.remote.model.HiAnimeResponse
import com.movies.premium.data.remote.model.HiAnimeSearchData
import com.movies.premium.data.remote.model.HiAnimeServersData
import com.movies.premium.data.remote.model.HiAnimeSourcesData
import com.movies.premium.data.remote.model.HiAnimeStats
import com.movies.premium.util.Logger
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anikoto anime catalogue.
 *
 * The original scrapes the Anikoto JSON API (`https://anikoto.ddns.me/api`)
 * and hands the `{ ok, data }` envelope to a hand-written parser. This
 * implementation keeps the exact same URLs, chip semantics and pagination
 * rules so the browse grid behaves identically.
 */
@Singleton
class AnimeRepository @Inject constructor(
    private val api: AnikotoApi,
    private val scraper: AnikotoScraper,
) {

    companion object {
        private const val TAG = "AnimeRepo"

        /** Genre chips, in the order the original shows them. */
        val GENRES: List<String> = listOf(
            "Action", "Adventure", "Cars", "Comedy", "Dementia", "Demons", "Drama",
            "Ecchi", "Fantasy", "Game", "Harem", "Historical", "Horror", "Isekai",
            "Josei", "Kids", "Magic", "Mahou Shoujo", "Martial Arts", "Mecha",
            "Military", "Music", "Mystery", "Parody", "Police", "Psychological",
            "Romance", "Samurai", "School", "Sci-Fi", "Seinen", "Shoujo",
            "Shoujo Ai", "Shounen", "Shounen Ai", "Slice of Life", "Space",
            "Sports", "Super Power", "Supernatural", "Thriller", "unknown",
            "Vampire", "Movie", "ONA", "OVA", "Special", "TV", "Updated",
            "Added", "Most Viewed", "Upcoming", "Ongoing",
        )

        /** Numeric genre ids used by the `genre[]` filter parameter. */
        private val GENRE_ID_MAP: Map<String, Int> = mapOf(
            "Action" to 1, "Adventure" to 2, "Cars" to 538, "Comedy" to 8,
            "Dementia" to 453, "Demons" to 110, "Drama" to 62, "Ecchi" to 214,
            "Fantasy" to 3, "Game" to 180, "Harem" to 215, "Historical" to 70,
            "Horror" to 222, "Isekai" to 74, "Josei" to 404, "Kids" to 46,
            "Magic" to 203, "Martial Arts" to 114, "Mecha" to 114,
            "Military" to 116, "Music" to 242, "Mystery" to 57, "Parody" to 152,
            "Police" to 127, "Psychological" to 73, "Romance" to 28,
            "Samurai" to 153, "School" to 14, "Sci-Fi" to 12, "Seinen" to 50,
            "Shoujo" to 252, "Shoujo Ai" to 235, "Shounen" to 15,
            "Shounen Ai" to 233, "Slice of Life" to 35, "Space" to 115,
            "Sports" to 29, "Super Power" to 16, "Supernatural" to 9,
            "Thriller" to 54, "Vampire" to 58,
        )

        /** Chips that filter by media type rather than genre. */
        private val TYPE_CHIPS = setOf("Movie", "Music", "ONA", "OVA", "Special", "TV")

        /** Chips that only pick a sort order. */
        private val CATEGORY_SORT_MAP = mapOf(
            "Updated" to "latest-updated",
            "Added" to "latest-added",
            "Most Viewed" to "most-viewed",
        )

        /** Chips that pick an airing status. */
        private val CATEGORY_STATUS_MAP = mapOf(
            "Ongoing" to "currently-airing",
            "Upcoming" to "not-yet-aired",
        )

        /** Maps the screen's type chip onto the API's `term_type[]` value. */
        private val TYPE_NORMALIZE_MAP = mapOf(
            "tv" to "TV",
            "movie" to "Movie",
            "ova" to "OVA",
            "ona" to "ONA",
            "special" to "Special",
        )
    }

    /**
     * The chip list. The original serves it from a local constant, so no
     * network call is involved.
     */
    suspend fun getGenres(): HiAnimeGenresData = HiAnimeGenresData(GENRES.size, GENRES)

    /**
     * Builds the filter URL. The parameter set depends on which chip is
     * active: sort chips, status chips, type chips and plain genres all use a
     * different combination of `sort`, `status[]`, `term_type[]` and `genre[]`.
     */
    private fun buildFilterUrl(genreName: String, page: Int, type: String?): String {
        val sb = StringBuilder("https://anikoto.ddns.me/api/filter?page=$page")
        CATEGORY_SORT_MAP[genreName]?.let { sort ->
            sb.append("&sort=$sort")
            TYPE_NORMALIZE_MAP[type?.lowercase(Locale.ROOT)]?.let { sb.append("&term_type[]=$it") }
            return sb.toString()
        }
        CATEGORY_STATUS_MAP[genreName]?.let { status ->
            sb.append("&status[]=$status&sort=latest-updated")
            TYPE_NORMALIZE_MAP[type?.lowercase(Locale.ROOT)]?.let { sb.append("&term_type[]=$it") }
            return sb.toString()
        }
        if (TYPE_CHIPS.contains(genreName)) {
            sb.append("&term_type[]=$genreName&sort=latest-updated")
            return sb.toString()
        }
        GENRE_ID_MAP[genreName]?.let { sb.append("&genre[]=$it") }
        sb.append("&sort=latest-updated")
        TYPE_NORMALIZE_MAP[type?.lowercase(Locale.ROOT)]?.let { sb.append("&term_type[]=$it") }
        return sb.toString()
    }

    /**
     * A page of anime for the given chip. Errors are swallowed and turned
     * into an empty page, exactly like the original.
     */
    suspend fun getAnimeByGenre(
        genreName: String,
        page: Int,
        type: String?,
    ): HiAnimeCategoryData {
        return try {
            val envelope = api.filter(buildFilterUrl(genreName, page, type))
            if (!envelope.bool("ok")) {
                HiAnimeCategoryData(emptyList(), page, 1, false)
            } else {
                parseFilterResponse(envelope.get("data"), page)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "getAnimeByGenre failed: ${e.message}")
            HiAnimeCategoryData(emptyList(), page, 1, false)
        }
    }

    /** Parses the `data` object of a filter response. */
    private fun parseFilterResponse(data: JsonElement?, page: Int): HiAnimeCategoryData {
        val obj = data?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return HiAnimeCategoryData(emptyList(), page, 1, false)

        val results = obj.getAsJsonArray("results")
        val animes = mutableListOf<HiAnimeItem>()
        if (results != null) {
            for (element in results) {
                if (element.isJsonObject) {
                    parseCard(element.asJsonObject)?.let { animes.add(it) }
                }
            }
        }

        var hasNext = true
        val nextFlag = obj.get("hasNextPage")
            ?: obj.get("has_next_page")
            ?: obj.get("nextPage")
        if (nextFlag != null && !nextFlag.isJsonNull) {
            hasNext = nextFlag.asBoolean
        } else {
            val totalPages = obj.get("totalPages")
                ?: obj.get("total_pages")
                ?: obj.get("lastPage")
            val total = obj.get("total")
                ?: obj.get("totalResults")
                ?: obj.get("totalCount")
            hasNext = when {
                totalPages != null && !totalPages.isJsonNull -> page < totalPages.asInt
                total != null && !total.isJsonNull && animes.isNotEmpty() ->
                    animes.size * page < total.asInt
                else -> animes.size >= 20
            }
        }

        val currentPage = obj.get("currentPage") ?: obj.get("current_page") ?: obj.get("page")
        val resolvedPage = if (currentPage != null && !currentPage.isJsonNull) currentPage.asInt else page

        return HiAnimeCategoryData(
            animes = animes,
            currentPage = resolvedPage,
            totalPages = if (hasNext) resolvedPage + 1 else resolvedPage,
            hasNextPage = hasNext,
        )
    }

    /** Parses one card of the browse grid. */
    private fun parseCard(obj: JsonObject): HiAnimeItem? = scraper.parseCard(obj)

    /** Detail payload of a single series. */
    suspend fun getAnimeDetail(animeId: String): HiAnimeDetailData? {
        return try {
            val id = animeId.substringBefore("|")
            val envelope = api.getAnime(id)
            if (!envelope.bool("ok")) return null
            val obj = envelope.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null

            val stats = HiAnimeStats(
                rating = obj.optString("rating").takeIf { it.isNotEmpty() },
                quality = obj.optString("quality").takeIf { it.isNotEmpty() },
                type = obj.optString("type").takeIf { it.isNotEmpty() },
                duration = obj.optString("duration").takeIf { it.isNotEmpty() },
                episodes = HiAnimeEpCount(
                    sub = obj.int("episodeCount")?.takeIf { it > 0 },
                    dub = if (obj.bool("hasDub")) 1 else null,
                ),
            )

            val info = HiAnimeInfo(
                id = obj.optString("slug").takeIf { it.isNotEmpty() } ?: id,
                name = obj.optString("title").takeIf { it.isNotEmpty() },
                poster = obj.optString("image").takeIf { it.isNotEmpty() },
                description = obj.optString("synopsis").takeIf { it.isNotEmpty() },
                stats = stats,
            )

            val moreInfo = HiAnimeMoreInfo(
                japanese = obj.optString("titleJp").takeIf { it.isNotEmpty() },
                aired = obj.optString("aired").takeIf { it.isNotEmpty() },
                premiered = obj.optString("premiered").takeIf { it.isNotEmpty() },
                duration = obj.optString("duration").takeIf { it.isNotEmpty() },
                status = obj.optString("status").takeIf { it.isNotEmpty() },
                malscore = obj.optString("malScore").takeIf { it.isNotEmpty() },
                genres = obj.getAsJsonArray("genres")?.toStringList(),
                studios = obj.optString("studios").takeIf { it.isNotEmpty() },
                producers = obj.getAsJsonArray("producers")?.toStringList(),
            )

            HiAnimeDetailData(
                anime = com.movies.premium.data.remote.model.HiAnimeAnimeData(info, moreInfo),
                seasons = null,
                relatedAnimes = null,
                recommendedAnimes = null,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "getAnimeDetail failed: ${e.message}")
            null
        }
    }

    /** Episode list of a series. Episode ids are "<animeId>|<number>". */
    suspend fun getAnimeEpisodes(animeId: String): HiAnimeEpisodesData {
        return try {
            val id = animeId.substringBefore("|")
            val envelope = api.getEpisodes(id)
            if (!envelope.bool("ok")) {
                return HiAnimeEpisodesData(0, emptyList())
            }
            val obj = envelope.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val array: JsonArray? = obj?.getAsJsonArray("episodes")
            if (array == null) return HiAnimeEpisodesData(0, emptyList())

            val episodes = mutableListOf<HiAnimeEpisode>()
            for (element in array) {
                if (!element.isJsonObject) continue
                val row = element.asJsonObject
                val number = row.get("number")?.takeIf { !it.isJsonNull }?.asString?.toIntOrNull() ?: continue
                episodes.add(
                    HiAnimeEpisode(
                        episodeId = "$id|$number",
                        number = number,
                        title = row.get("title")?.takeIf { !it.isJsonNull }?.asString,
                    ),
                )
            }
            HiAnimeEpisodesData(episodes.size, episodes)
        } catch (e: Exception) {
            Logger.e(TAG, "getAnimeEpisodes failed: ${e.message}")
            HiAnimeEpisodesData(0, emptyList())
        }
    }

    /**
     * The three shelves of the browse screen.
     *
     * The original's scraper answers with the trending, the latest and the
     * spotlight list; a [type] narrows every shelf down to that type.
     */
    suspend fun getHomePage(type: String? = null): HiAnimeResponse<HiAnimeHome> =
        scraper.getHomePage(type)

    /**
     * The result of a keyword search.
     *
     * The original's scraper accepts a page and a genre list but only uses the
     * keyword and the type.
     */
    suspend fun searchAnime(
        query: String,
        page: Int = 1,
        type: String? = null,
        genres: String? = null,
    ): HiAnimeResponse<HiAnimeSearchData> = scraper.searchAnime(query, page, type, genres)

    /**
     * The sub and dub servers of one episode.
     *
     * The episode id is `"<slug>|<number>"`.
     */
    suspend fun getAnimeEpisodeServers(episodeId: String): HiAnimeResponse<HiAnimeServersData> =
        scraper.getAnimeEpisodeServers(episodeId)

    /**
     * The playable url, the subtitle tracks and the headers of one server.
     */
    suspend fun getAnimeEpisodeSources(
        episodeId: String,
        server: String,
        category: String,
    ): HiAnimeResponse<HiAnimeSourcesData> =
        scraper.getAnimeEpisodeSources(episodeId, server, category)

    /** Reads a JSON array of strings, ignoring non-primitive entries. */
    private fun JsonArray.toStringList(): List<String> = mapNotNull { element ->
        if (element.isJsonPrimitive) element.asString else null
    }
    /** `optString` that treats JSON null as absent. */
    private fun JsonObject.optString(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.asString ?: ""

    /** `optBoolean` that treats JSON null as absent. */
    private fun JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: false

    /** `optInt` that treats JSON null as absent. */
    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { !it.isJsonNull }?.asInt
}
