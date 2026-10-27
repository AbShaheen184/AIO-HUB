package com.movies.premium.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.movies.premium.data.remote.api.DeezerApi
import com.movies.premium.data.remote.model.MusicAlbum
import com.movies.premium.data.remote.model.MusicAlbumDetail
import com.movies.premium.data.remote.model.MusicArtist
import com.movies.premium.data.remote.model.MusicArtistDetail
import com.movies.premium.data.remote.model.MusicGenre
import com.movies.premium.data.remote.model.MusicItem
import com.movies.premium.data.remote.model.MusicPageResponse
import com.movies.premium.data.remote.model.MusicTrack
import com.movies.premium.util.Logger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Music catalogue.
 *
 * The original offers three providers — MusicMp3 (musicmp3.ru, scraped with
 * Jsoup), Deezer (the public JSON API) and YouTube Music (the NewPipe
 * extractor) — behind a common interface. The NewPipe extractor is not
 * published to Maven Central, so the YouTube Music source is not reproduced
 * here; the other two are.
 *
 * The genre list, the source names and the provider dispatch live here rather
 * than in the ViewModel, mirroring the original's `data.repository` package.
 */
@Singleton
class MusicRepository @Inject constructor(
    private val deezerApi: DeezerApi,
) {

    companion object {
        private const val TAG = "MusicRepo"
        private const val MUSICMP3 = "https://musicmp3.ru"
        private const val UA_MUSICMP3 =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"

        /** Deezer returns 25 entries per page, MusicMp3 40 (80 for artists). */
        private const val DEEZER_PAGE_SIZE = 25
        private const val MUSICMP3_ALBUM_PAGE_SIZE = 40
        private const val MUSICMP3_ARTIST_PAGE_SIZE = 80

        /** The genre chips, including the ones that carry sub-genres. */
        val GENRES: List<MusicGenre> = listOf(
            MusicGenre("all", "All Genres"),
            MusicGenre(
                "0", "World",
                listOf(
                    MusicGenre("3", "Celtic"),
                    MusicGenre("14", "Jewish"),
                    MusicGenre("79", "Arabic"),
                    MusicGenre("169", "Chinese"),
                    MusicGenre("179", "Japanese"),
                    MusicGenre("194", "Korean"),
                ),
            ),
            MusicGenre("313", "Classical"),
            MusicGenre("355", "Metal"),
            MusicGenre("408", "Alternative"),
            MusicGenre("473", "Rock"),
            MusicGenre("517", "R&B"),
            MusicGenre("539", "Dance"),
            MusicGenre("558", "Pop"),
            MusicGenre("596", "Jazz"),
            MusicGenre("623", "Hip Hop"),
            MusicGenre("654", "Electronic"),
            MusicGenre("750", "Country"),
            MusicGenre("774", "Blues"),
            MusicGenre(
                "0", "Soundtracks",
                listOf(
                    MusicGenre("789", "Movie Soundtracks"),
                    MusicGenre("792", "TV Soundtracks"),
                    MusicGenre("794", "Game Soundtracks"),
                ),
            ),
        )

        /** The available provider names, in the order the original lists them. */
        val SOURCES: List<String> = listOf("MusicMp3", "Deezer")
    }

    // --- provider selection -------------------------------------------------

    /** The provider the browse screen is currently pointed at. */
    @Volatile
    var currentSource: String = SOURCES.first()
        private set

    /** Switches the active provider, like the original repository's setter. */
    fun selectSource(name: String) {
        if (SOURCES.contains(name)) currentSource = name
    }

    /**
     * Picks the provider that understands the given url, mirroring the
     * original's url-based dispatch: absolute http urls belong to MusicMp3,
     * bare provider ids to Deezer.
     */
    private fun sourceForUrl(url: String): String = when {
        url.contains("youtube.com") || url.contains("youtu.be") -> "YouTube Music"
        url.startsWith("http") -> SOURCES.first()
        else -> "Deezer"
    }

    // --- browse -------------------------------------------------------------

    /**
     * A page of the requested section.
     *
     * [section] is one of "Artists", "New Releases" or "Top Albums"; the
     * original's repository dispatches on exactly those three names.
     *
     * [pageToken] is the provider's own cursor: a Deezer `index` (0, 25, 50 …)
     * or a MusicMp3 page number (1, 2, 3 …).
     */
    suspend fun loadSection(
        section: String,
        genreId: String,
        pageToken: Any?,
    ): MusicPageResponse<MusicItem> {
        if (currentSource == "Deezer") {
            return when (section) {
                "Artists" -> deezerArtists(pageToken)
                "New Releases" -> deezerAlbums("/editorial/0/selection", pageToken)
                "Top Albums" -> deezerAlbums("/chart/0/albums", pageToken)
                else -> MusicPageResponse(emptyList(), null)
            }
        }
        return when (section) {
            "Artists" -> musicMp3Artists(genreId, pageToken)
            "New Releases" -> musicMp3Albums("new", genreId, pageToken)
            "Top Albums" -> musicMp3Albums("top", genreId, pageToken)
            else -> MusicPageResponse(emptyList(), null)
        }
    }

    // --- Deezer -------------------------------------------------------------

    /** Deezer's "Top artists" chart. */
    private suspend fun deezerArtists(pageToken: Any?): MusicPageResponse<MusicItem> {
        val index = (pageToken as? Int) ?: 0
        return try {
            val envelope = deezerApi.getChartArtists(index = index)
            val data = envelope.getAsJsonArray("data")
                ?: return MusicPageResponse(emptyList(), null)
            val artists = data.mapNotNull { element ->
                if (element.isJsonObject) parseArtist(element.asJsonObject) else null
            }
            MusicPageResponse<MusicItem>(
                artists,
                if (artists.size >= DEEZER_PAGE_SIZE) index + DEEZER_PAGE_SIZE else null,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Deezer artists failed: ${e.message}")
            MusicPageResponse(emptyList(), null)
        }
    }

    /** Deezer's album charts / editorial selection. */
    private suspend fun deezerAlbums(
        path: String,
        pageToken: Any?,
    ): MusicPageResponse<MusicItem> {
        val index = (pageToken as? Int) ?: 0
        return try {
            val envelope = when (path) {
                "/editorial/0/selection" -> deezerApi.getEditorialSelection(index = index)
                else -> deezerApi.getChartAlbums(index = index)
            }
            val data = envelope.getAsJsonArray("data")
                ?: return MusicPageResponse(emptyList(), null)
            val albums = data.mapNotNull { element ->
                if (element.isJsonObject) parseAlbum(element.asJsonObject) else null
            }
            MusicPageResponse<MusicItem>(
                albums,
                if (albums.size >= DEEZER_PAGE_SIZE) index + DEEZER_PAGE_SIZE else null,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Deezer albums failed: ${e.message}")
            MusicPageResponse(emptyList(), null)
        }
    }

    // --- MusicMp3 -----------------------------------------------------------

    /** MusicMp3's artist listing. */
    private suspend fun musicMp3Artists(
        genreId: String,
        pageToken: Any?,
    ): MusicPageResponse<MusicItem> {
        val page = (pageToken as? Int) ?: 1
        return try {
            val url = "https://musicmp3.ru/main_artists.html?type=artist&page=$page" +
                musicMp3GenreParam(genreId)
            val document = fetchMusicMp3(url) ?: return MusicPageResponse(emptyList(), null)
            val artists = document.select("a[href^=/artist_]").map { element ->
                MusicArtist(
                    name = element.text().trim().ifEmpty { "Unknown" },
                    url = element.absUrl("href"),
                    image = null,
                )
            }
            MusicPageResponse<MusicItem>(
                artists,
                if (artists.size >= MUSICMP3_ARTIST_PAGE_SIZE) page + 1 else null,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "MusicMp3 artists failed: ${e.message}")
            MusicPageResponse(emptyList(), null)
        }
    }

    /** MusicMp3's album listing, either "new" or "top". */
    private suspend fun musicMp3Albums(
        sort: String,
        genreId: String,
        pageToken: Any?,
    ): MusicPageResponse<MusicItem> {
        val page = (pageToken as? Int) ?: 1
        return try {
            val url = "https://musicmp3.ru/main_albums.html?sort=$sort&type=album&page=$page" +
                musicMp3GenreParam(genreId)
            val document = fetchMusicMp3(url) ?: return MusicPageResponse(emptyList(), null)
            val albums = parseMusicMp3Albums(document)
            MusicPageResponse<MusicItem>(
                albums,
                if (albums.size >= MUSICMP3_ALBUM_PAGE_SIZE) page + 1 else null,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "MusicMp3 albums failed: ${e.message}")
            MusicPageResponse(emptyList(), null)
        }
    }

    /** MusicMp3 ignores the "all" genre and only sends a filter for a real id. */
    private fun musicMp3GenreParam(genreId: String): String {
        val id = if (genreId == "all") "0" else genreId
        return if (id != "0") "&gnr_id=$id" else ""
    }

    // --- details ------------------------------------------------------------

    /** Full album metadata plus its track list. */
    suspend fun getAlbumDetail(url: String): MusicAlbumDetail? {
        return when (sourceForUrl(url)) {
            "Deezer" -> deezerAlbumDetail(url)
            else -> musicMp3AlbumDetail(url)
        }
    }

    /** Full artist metadata plus their discography. */
    suspend fun getArtistDetail(url: String): MusicArtistDetail? {
        return when (sourceForUrl(url)) {
            "Deezer" -> deezerArtistDetail(url)
            else -> musicMp3ArtistDetail(url)
        }
    }

    private suspend fun deezerAlbumDetail(url: String): MusicAlbumDetail? {
        return try {
            val id = deezerId(url)
            if (id.isBlank()) return null
            val obj = deezerApi.getAlbum(id)
            if (!obj.has("id")) return null
            val album = parseAlbum(obj) ?: return null
            val tracksObj = obj.getAsJsonObject("tracks")
            val tracksArray: JsonArray? = tracksObj?.getAsJsonArray("data")
            val tracks = tracksArray?.mapNotNull { element ->
                if (element.isJsonObject) {
                    parseTrack(element.asJsonObject, album.title, album.url)
                } else {
                    null
                }
            } ?: emptyList()
            MusicAlbumDetail(album.title, album.artist, album.image, album.year, null, null, tracks)
        } catch (e: Exception) {
            Logger.e(TAG, "getAlbumDetail failed for $url: ${e.message}")
            null
        }
    }

    private suspend fun deezerArtistDetail(url: String): MusicArtistDetail? {
        val id = deezerId(url)
        if (id.isBlank()) return null
        return try {
            val obj = deezerApi.getArtist(id)
            if (!obj.has("id")) return null
            val artist = parseArtist(obj) ?: return null
            val albumsEnvelope = deezerApi.getArtistAlbums(id, index = 0)
            val albums = albumsEnvelope.getAsJsonArray("data")?.mapNotNull { element ->
                if (element.isJsonObject) parseAlbum(element.asJsonObject) else null
            } ?: emptyList()
            MusicArtistDetail(artist.name, artist.image, null, albums)
        } catch (e: Exception) {
            Logger.e(TAG, "getArtistDetail failed for $url: ${e.message}")
            null
        }
    }

    /** Extracts the bare Deezer id from a "dz:<id>" (or URL) form. */
    private fun deezerId(url: String): String =
        url.removePrefix("dz:").substringAfterLast("/").substringBefore("?")

    private suspend fun musicMp3AlbumDetail(url: String): MusicAlbumDetail? {
        return try {
            val document = fetchMusicMp3(url) ?: return null
            val name = document.selectFirst("h1")?.text()?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "Unknown Album"
            val artists = document.select("[itemprop=byArtist], a.album_report__artist, .album_info__artist")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val artist = artists.joinToString(", ").ifBlank { "Unknown Artist" }
            val image = document.selectFirst("img.art_wrap__img")?.absUrl("src")
                ?: document.selectFirst("img.album_report__image")?.absUrl("src")
            val date = document.selectFirst("[itemprop=datePublished]")?.attr("content")
                ?: document.selectFirst(".album_info__date")?.text()
            val year = date?.trim()?.take(4)
            val genre = document.select(".album_info__genre, [itemprop=genre]")
                .joinToString(", ") { it.text().trim() }
                .takeUnless { it.isBlank() }
            val description = document.selectFirst(".album_info__description, .album_description, [itemprop=description]")
                ?.text()?.trim()
            val tracks = document.select("tr.song").mapNotNull { row ->
                parseMusicMp3Track(row, description.orEmpty(), name, url)
            }
            MusicAlbumDetail(name, artist, image, year, genre, description, tracks)
        } catch (e: Exception) {
            Logger.e(TAG, "getAlbumDetail failed for $url: ${e.message}")
            null
        }
    }

    private suspend fun musicMp3ArtistDetail(url: String): MusicArtistDetail? {
        return try {
            val document = fetchMusicMp3(url) ?: return null
            val name = document.selectFirst("h1")?.text()?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "Unknown Artist"
            val image = document.selectFirst("img.artist_image, .artist_info__image img")?.absUrl("src")
            val bio = document.selectFirst("div.artist_bio, .artist_info__description")?.text()?.trim()
            MusicArtistDetail(name, image, bio, parseMusicMp3Albums(document))
        } catch (e: Exception) {
            Logger.e(TAG, "getArtistDetail failed for $url: ${e.message}")
            null
        }
    }

    // --- parsers ------------------------------------------------------------

    /** Deezer album payload. */
    private fun parseAlbum(obj: JsonObject): MusicAlbum? {
        val id = obj.optString("id")
        if (id.isEmpty()) return null
        val artist = obj.getAsJsonObject("artist")?.optString("name")?.takeIf { it.isNotEmpty() }
            ?: "Unknown"
        val title = obj.optString("title").takeIf { it.isNotEmpty() } ?: "Unknown"
        val cover = obj.optString("cover_big").takeIf { it.isNotEmpty() }
            ?: obj.optString("cover_medium").takeIf { it.isNotEmpty() }
        val year = obj.optString("release_date").takeIf { it.isNotEmpty() }?.take(4)
        return MusicAlbum(title, artist, id, cover, year)
    }

    /** Deezer artist payload. */
    private fun parseArtist(obj: JsonObject): MusicArtist? {
        val id = obj.optString("id")
        if (id.isEmpty()) return null
        val name = obj.optString("name").takeIf { it.isNotEmpty() } ?: "Unknown"
        val picture = obj.optString("picture_big").takeIf { it.isNotEmpty() }
            ?: obj.optString("picture_medium").takeIf { it.isNotEmpty() }
        return MusicArtist(name, id, picture)
    }

    /** Deezer track payload. */
    private fun parseTrack(obj: JsonObject, albumTitle: String, albumUrl: String): MusicTrack? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = obj.optString("title").takeIf { it.isNotEmpty() } ?: "Unknown"
        val artist = obj.optString("artist").takeIf { it.isNotEmpty() } ?: "Unknown"
        val album = obj.getAsJsonObject("album")?.optString("title")?.takeIf { it.isNotEmpty() }
            ?: albumTitle
        val duration = obj.get("duration")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val preview = obj.optString("preview")
        return MusicTrack(
            id = "dz:$id",
            rel = "$title\n$artist\n$preview",
            title = title,
            artist = artist,
            album = album,
            duration = if (duration > 0) {
                "${duration / 60}:${String.format("%02d", duration % 60)}"
            } else {
                null
            },
            image = obj.getAsJsonObject("album")?.optString("cover_medium"),
            albumUrl = obj.getAsJsonObject("album")?.optString("id")?.takeIf { it.isNotEmpty() }
                ?: albumUrl,
        )
    }

    /** MusicMp3 album rows. */
    private fun parseMusicMp3Albums(document: Document): List<MusicAlbum> {
        val albums = mutableListOf<MusicAlbum>()
        for (element in document.select("div.album_report")) {
            val link = element.selectFirst("a.album_report__link") ?: continue
            val name = link.selectFirst("span.album_report__name")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "Unknown"
            val image = element.selectFirst("img.album_report__image")?.absUrl("src")
            val artist = findSibling(element, "album_report__artist")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "Unknown"
            val date = findSibling(element, "album_report__date")?.text()?.trim()
            albums.add(MusicAlbum(name, artist, link.absUrl("href"), image, date))
        }
        return albums
    }

    /** MusicMp3 track row. */
    private fun parseMusicMp3Track(
        row: Element,
        description: String,
        albumTitle: String,
        albumUrl: String,
    ): MusicTrack? {
        val relLink = row.selectFirst("a[rel]") ?: return null
        val rel = relLink.attr("rel")
        val title = row.selectFirst("[itemprop=name]")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: row.selectFirst("td.song__name--search")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Unknown"
        val artist = row.selectFirst("[itemprop=byArtist]")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: row.selectFirst("td.song__artist--search")?.text()?.trim().orEmpty()
        val albumElement = row.selectFirst("[itemprop=inAlbum]") ?: row.selectFirst("td.song__album--search")
        val album = albumElement?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: albumTitle
        val resolvedAlbumUrl = albumElement?.selectFirst("a")?.absUrl("href") ?: albumUrl
        var duration = row.selectFirst("[itemprop=duration]")?.attr("content")
        if (duration != null && duration.startsWith("PT")) {
            duration = try {
                val minutes = Regex("(\\d+)M").find(duration)?.groupValues?.get(1)?.toInt() ?: 0
                val seconds = Regex("(\\d+)S").find(duration)?.groupValues?.get(1)?.toInt() ?: 0
                if (minutes > 0) {
                    "$minutes:${String.format("%02d", seconds)}"
                } else {
                    "0:${String.format("%02d", seconds)}"
                }
            } catch (e: Exception) {
                duration
            }
        }
        return MusicTrack(
            id = row.attr("id"),
            rel = rel,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            image = null,
            albumUrl = resolvedAlbumUrl,
        )
    }

    /**
     * MusicMp3 puts the artist and the date in a sibling of the album link,
     * so look one and two levels up.
     */
    private fun findSibling(element: Element, className: String): Element? {
        val parent = element.parent() ?: return null
        parent.selectFirst(".$className")?.let { return it }
        val grandParent = parent.parent() ?: return null
        return grandParent.selectFirst(".$className")
    }

    // --- transport ----------------------------------------------------------

    /** Fetches and parses a musicmp3.ru page. */
    private fun fetchMusicMp3(url: String): Document? {
        return try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", UA_MUSICMP3)
                .header("Referer", "$MUSICMP3/")
                .build()
            okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                Jsoup.parse(body, url)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Fetch failed: $url")
            null
        }
    }

    /** `optString` that treats JSON null as absent. */
    private fun JsonObject.optString(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.asString ?: ""
}
