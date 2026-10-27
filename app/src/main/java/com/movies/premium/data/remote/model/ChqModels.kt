package com.movies.premium.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Models for the CHQ backend.
 *
 * Field names and `@SerializedName` values mirror the original APK's
 * `com.app.mlounge.data.remote.model` package exactly, because the backend
 * returns snake_case keys that do not always match the Kotlin property names.
 */

/**
 * One subtitle track of an anime episode.
 *
 * The original's class is `HiAnimeTrack(file, label, kind, isDefault)`; the
 * `default` flag of the wire format is the `isDefault` field, and the `kind`
 * is the track type (`"captions"` when the API does not name one).
 */
data class HiAnimeTrack(
    val file: String? = null,
    val label: String? = null,
    val kind: String? = null,
    @SerializedName("default") val isDefault: Boolean? = null,
)

/** Subtitle object containing language and url. */
data class Subtitle(
    @SerializedName(value = "language", alternate = ["lang"]) val language: String? = null,
    @SerializedName("url") val url: String? = null,
)

/** A single playable stream returned by the CHQ backend. */
data class ChqStream(
    @SerializedName("name", alternate = ["server"]) val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("url", alternate = ["magnet"]) val url: String? = null,
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("headers") val headers: Map<String, String>? = null,
    @SerializedName("subtitles") val subtitles: List<Subtitle>? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("stream_type") val streamType: String? = null,
    @SerializedName("size") val size: String? = null,
    @SerializedName("seeders") val seeders: Int? = null,
    @SerializedName("info_hash", alternate = ["infoHash"]) val infoHash: String? = null,
    @SerializedName("file_index", alternate = ["fileIdx"]) val fileIdx: Int? = null,
    @SerializedName("is_torrent") val isTorrent: Boolean = false,
    @SerializedName("description") val description: String? = null,
    @SerializedName("animeSubtitles") val animeSubtitles: List<HiAnimeTrack>? = null,
    /**
     * The provider the source came from, e.g. `hdhub`.
     *
     * The original's model has no such field — the original filters its
     * *catalogue* by the provider name instead. The replica needs it to honour
     * the "Sources (http)" / "Sources (Torrents)" / "Sources (Anime)" switches
     * of the settings screen, which switch a provider off entirely.
     */
    @SerializedName("provider") val provider: String? = null,
)

data class ChqStreamResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("tmdbId") val tmdbId: String? = null,
    @SerializedName("streams") val streams: List<ChqStream>? = null,
    @SerializedName("count") val count: Int? = null,
    @SerializedName("error") val error: String? = null,
)

// --- The current CHQ stream API ----------------------------------------------
//
// The original's `ChqApi` calls one endpoint per provider (`api/multiembed/streams`,
// `api/vidzee/streams`, …). The live backend has since moved to a single route
// family, `api/streams/{id}` / `api/streams/{id}/{season}/{episode}` and
// `api/streams/{provider}/{id}[/{season}/{episode}]`, and the old routes answer
// `404 {"status":404,"message":"Not Found"}`.
//
// The shapes below describe the **new** routes. The legacy models above are kept
// because the repository still falls back to the old endpoints when the new ones
// are not available — see `data/repository/StreamsRepository.kt`.

/**
 * The answer of `api/streams/...`.
 *
 * The backend fans out over its providers server-side and reports the outcome of
 * each one: a provider that failed appears in [errors] instead of [streams], so one
 * broken provider never hides the others.
 */
data class ChqStreamsResponse(
    val id: String? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    /** `"movie"` or `"series"`. */
    val type: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** The providers the backend queried. */
    val providers: List<String>? = null,
    val streams: List<ChqStreamItem>? = null,
    val errors: List<ChqProviderError>? = null,
)

/** One playable source of `api/streams/...`. */
data class ChqStreamItem(
    /**
     * The display title. The backend builds it in three lines:
     *
     * ```
     * [PixelDrain] [💾 2.93 GB] Fight.Club.1999.1080p…mkv
     * Hindi
     * pixeldrain | HdHub
     * ```
     *
     * — the host and the size, the language, and the server the source comes from.
     */
    val title: String? = null,
    val url: String? = null,
    /** Display label shown in the player/source list. */
    val name: String? = null,
    /** The resolution of the stream, e.g. `1080p`. */
    val quality: String? = null,
    /** HTTP headers to send with the request. */
    @SerializedName("headers") val headers: Map<String, String>? = null,
    /** Caption subtitles for this stream. */
    @SerializedName("subtitles") val subtitles: List<Subtitle>? = null,
    /** `movie` or `series` — the media type, not the stream type. */
    val type: String? = null,
    /** The provider the source came from: notorrent, nebula, hdhub, vidrock. */
    val provider: String? = null,
    val behaviorHints: ChqBehaviorHints? = null,
)

/** The `behaviorHints` block of a stream. */
data class ChqBehaviorHints(
    @SerializedName("notWebReady") val notWebReady: Boolean? = null,
    /** The size of the file in bytes, when the backend knows it. */
    @SerializedName("videoSize") val videoSize: Long? = null,
)

/** The failure of one provider inside an `api/streams/...` answer. */
data class ChqProviderError(
    val provider: String? = null,
    val error: String? = null,
)

data class IptvChannel(
    @SerializedName("name") val name: String? = null,
    @SerializedName("logo") val logo: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("group") val group: String? = null,
    @SerializedName("tvgId") val tvgId: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("fallbackLogo") val fallbackLogo: String? = null,
)

data class IptvGroup(
    @SerializedName("name") val name: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("count") val count: Int? = null,
)

data class IptvGroupsResponse(
    @SerializedName("groups") val groups: List<IptvGroup>? = null,
)

data class IptvSearchResponse(
    @SerializedName("channels") val channels: List<IptvChannel>? = null,
)

data class IptvProviderStats(
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("channels") val channels: Int? = null,
    @SerializedName("lastUpdated") val lastUpdated: String? = null,
)

data class IptvStatsResponse(
    @SerializedName("total") val total: Int? = null,
    @SerializedName("countries") val countries: List<String>? = null,
    @SerializedName("categories") val categories: List<String>? = null,
    @SerializedName("providers") val providers: List<IptvProviderStats>? = null,
)

data class Game(
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("posterUrl") val posterUrl: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("players") val players: String? = null,
    @SerializedName("developer") val developer: String? = null,
    @SerializedName("publisher") val publisher: String? = null,
    @SerializedName("romUrl") val romUrl: String? = null,
)

data class GamesResponse(
    @SerializedName("games") val games: List<Game>? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("total") val total: Int? = null,
    /**
     * How many games the catalogue holds.
     *
     * The original's `GamesResponse` has this field (and no `total`).
     */
    @SerializedName("totalGames") val totalGames: Int? = null,
    /**
     * How many pages the catalogue has.
     *
     * The original's `GamesResponse` has this field, and the view model uses it
     * to decide whether the next page has to be loaded.
     */
    @SerializedName("totalPages") val totalPages: Int? = null,
    /** Whether a next page exists. The original's `GamesResponse` has it too. */
    @SerializedName("hasNext") val hasNext: Boolean? = null,
)

data class GameDetailResponse(
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("posterUrl") val posterUrl: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("players") val players: String? = null,
    @SerializedName("developer") val developer: String? = null,
    @SerializedName("publisher") val publisher: String? = null,
    @SerializedName("romUrl") val romUrl: String? = null,
    @SerializedName("romSizeMB") val romSizeMb: Double? = null,
    @SerializedName("romSizeBytes") val romSizeBytes: Long? = null,
)

data class GameSearchResponse(
    @SerializedName("games") val games: List<Game>? = null,
)

data class GameStatsResponse(
    @SerializedName("total") val total: Int? = null,
    @SerializedName("platforms") val platforms: List<String>? = null,
    @SerializedName("genres") val genres: Map<String, Int>? = null,
    @SerializedName("decades") val decades: Map<String, Int>? = null,
    /**
     * How many games the platform holds.
     *
     * The original's `GameStatsResponse` has this field (and no `total`).
     */
    @SerializedName("totalGames") val totalGames: Int? = null,
    /** The display name of the platform. The original's `GameStatsResponse` has it too. */
    @SerializedName("platformName") val platformName: String? = null,
)

// --- Live TV (NTV) models --------------------------------------------------

data class NtvChannel(
    @SerializedName("channel_id") val channelId: String? = null,
    @SerializedName("channel_name") val channelName: String? = null,
    @SerializedName("channel_code") val channelCode: String? = null,
    @SerializedName("channel_url") val channelUrl: String? = null,
    @SerializedName("channel_image") val channelImage: String? = null,
    @SerializedName("viewers") val viewers: Int? = null,
    @SerializedName("server") val server: String? = null,
    @SerializedName("has_icon") val hasIcon: Boolean? = null,
)

data class NtvChannelsResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("channels") val channels: List<NtvChannel>? = null,
)

data class LogoManifestItem(
    @SerializedName("channel") val channel: String? = null,
    @SerializedName("file") val file: String? = null,
    @SerializedName("source") val source: String? = null,
)

data class LogoManifestResponse(
    @SerializedName("ok") val ok: Int? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("exportedAt") val exportedAt: String? = null,
    @SerializedName("items") val items: List<LogoManifestItem>? = null,
)

data class MatchSource(
    @SerializedName("id") val id: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("url") val url: String? = null,
)

data class MatchTeam(
    @SerializedName("name") val name: String? = null,
    @SerializedName("badge") val badge: String? = null,
)

data class MatchTeams(
    @SerializedName("home") val home: MatchTeam? = null,
    @SerializedName("away") val away: MatchTeam? = null,
)

data class SportsMatch(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("tournament") val tournament: String? = null,
    @SerializedName("date") val date: Long? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("popular") val popular: Boolean? = null,
    @SerializedName("live") val live: Boolean? = null,
    @SerializedName("server") val server: String? = null,
    @SerializedName("teams") val teams: MatchTeams? = null,
    @SerializedName("sources") val sources: List<MatchSource>? = null,
)

data class SportsMatchResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("live") val live: List<SportsMatch>? = null,
    @SerializedName("all") val all: List<SportsMatch>? = null,
)

// --- Music models ----------------------------------------------------------
//
// Field names and constructor order mirror the original APK's
// `com.app.mlounge.data.remote.model` music package exactly. The three music
// providers (MusicMp3, YouTube Music, Deezer) all normalise their payloads
// into these shapes before the UI sees them, so the screens can stay
// provider-agnostic.

/** A browsable genre filter. Some genres expose a list of sub-genres. */
data class MusicGenre(
    val id: String,
    val name: String,
    val subGenres: List<MusicGenre> = emptyList(),
)

/**
 * Marker for everything the music browse grid can show.
 *
 * The original's grid renders albums, artists and tracks with the same
 * card component, so all three share one item type.
 */
sealed interface MusicItem

/** A single playable track inside an album. */
data class MusicTrack(
    /** Provider-specific identifier ("dz:12345" for Deezer). */
    val id: String,
    /** Opaque provider payload used to resolve the stream URL. */
    val rel: String?,
    val title: String,
    val artist: String,
    val album: String,
    /** Formatted "m:ss" duration, or null when the provider has no length. */
    val duration: String?,
    val image: String?,
    /** URL of the album this track belongs to, used to open the album page. */
    val albumUrl: String,
) : MusicItem

/** A grid card for the music browse screen. */
data class MusicAlbum(
    val title: String,
    val artist: String,
    /** Album URL as understood by the active source. */
    val url: String,
    val image: String?,
    val year: String?,
) : MusicItem

/** Full album metadata plus its track list. */
data class MusicAlbumDetail(
    val title: String,
    val artist: String,
    val image: String?,
    val year: String?,
    val genre: String?,
    val description: String?,
    val tracks: List<MusicTrack>,
)

/** A grid card for an artist. */
data class MusicArtist(
    val name: String,
    /** Artist URL as understood by the active source. */
    val url: String,
    val image: String?,
) : MusicItem

/** Full artist metadata plus their discography. */
data class MusicArtistDetail(
    val name: String,
    val image: String?,
    val bio: String?,
    val albums: List<MusicAlbum>,
)

/**
 * A page of browse results. [nextPageToken] is the provider's opaque cursor
 * for the following page, or null when the source has been exhausted.
 *
 * The original declares this generic, because a page can hold albums,
 * artists or tracks.
 */
data class MusicPageResponse<T>(
    val items: List<T>,
    val nextPageToken: Any? = null,
)

/** Combined album/artist/track search results. */
data class MusicSearchResponse(
    val albums: List<MusicAlbum>? = null,
    val artists: List<MusicArtist>? = null,
    val tracks: List<MusicTrack>? = null,
    val nextPageToken: Any? = null,
    val query: String? = null,
)

/** A resolved playable stream for a track. */
data class MusicStreamResult(
    val url: String,
    /** Container/codec hint, e.g. "mp3" or "m4a". */
    val type: String,
)

// --- Anime models ----------------------------------------------------------
//
// Wire models for the Anikoto JSON API. The envelope is
// `{ "ok": true, "data": { ... } }`; the payload itself is parsed by the
// scraper, so these models describe the *parsed* shape rather than the raw
// JSON.

/** Sub/dub episode counts for a title. */
data class HiAnimeEpCount(
    val sub: Int? = null,
    val dub: Int? = null,
)

/** Rating / quality / type / duration / episode-count block. */
data class HiAnimeStats(
    val rating: String? = null,
    val quality: String? = null,
    val type: String? = null,
    val duration: String? = null,
    val episodes: HiAnimeEpCount? = null,
)

/** The identity block of an anime. */
data class HiAnimeInfo(
    val id: String,
    val name: String?,
    val poster: String?,
    val description: String?,
    val stats: HiAnimeStats? = null,
)

/** The secondary ("more info") block of an anime. */
data class HiAnimeMoreInfo(
    val japanese: String? = null,
    val aired: String? = null,
    val premiered: String? = null,
    val duration: String? = null,
    val status: String? = null,
    val malscore: String? = null,
    val genres: List<String>? = null,
    val studios: String? = null,
    val producers: List<String>? = null,
)

/** `info` + `moreInfo` combined. */
data class HiAnimeAnimeData(
    val info: HiAnimeInfo,
    val moreInfo: HiAnimeMoreInfo? = null,
)

/** Payload of the anime-detail endpoint. */
data class HiAnimeDetailData(
    val anime: HiAnimeAnimeData,
    val seasons: List<HiAnimeSeason>? = null,
    val relatedAnimes: List<HiAnimeItem>? = null,
    val recommendedAnimes: List<HiAnimeItem>? = null,
)

/** A season entry of a franchise. */
data class HiAnimeSeason(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val poster: String? = null,
    val isCurrent: Boolean? = null,
)

/**
 * A card in the anime browse grid.
 *
 * The API is inconsistent about which of [name] / [title] / [jname] carries
 * the display title, so [displayName] picks the first non-blank one.
 */
data class HiAnimeItem(
    val id: String,
    val name: String? = null,
    val title: String? = null,
    val poster: String? = null,
    val jname: String? = null,
    val type: String? = null,
    val duration: String? = null,
    val rating: String? = null,
    val episodes: HiAnimeEpCount? = null,
    val description: String? = null,
) {
    fun displayName(): String = name ?: title ?: jname ?: "Unknown"
}

/** A page of anime results. */
data class HiAnimeCategoryData(
    val animes: List<HiAnimeItem>,
    val currentPage: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
)

/** One episode of a series. [episodeId] is "<animeId>|<number>". */
data class HiAnimeEpisode(
    val episodeId: String,
    val number: Int,
    val title: String? = null,
    val isFiller: Boolean? = null,
)

/** The episode list of a series. */
data class HiAnimeEpisodesData(
    val totalEpisodes: Int,
    val episodes: List<HiAnimeEpisode>,
)

/** The genre chip list. */
data class HiAnimeGenresData(
    val total: Int,
    val genres: List<String>,
)

// --- The scraper's own wire models -----------------------------------------
//
// The original's `com.app.mlounge.data.anime.AnikotoScraper` returns every
// payload wrapped in `HiAnimeResponse<T>`, and it hands the *home* page, the
// *search* results, the episode *servers* and the episode *sources* back in
// their own models. The replica parses the same payloads, but it used to do it
// with hand-written JSON walking, so these seven models were missing.
//
// Every field name, type and default below is taken from the original's
// `com.app.mlounge.data.remote.model` classes.

/**
 * The envelope the original's scraper wraps every payload in.
 *
 * The original's `HiAnimeResponse` carries the payload in `data` and a `status`
 * that always holds `200` (`org.mozilla.javascript.Context.VERSION_ES6`, which
 * is the constant R8 left in the default value).
 */
data class HiAnimeResponse<T>(
    val data: T,
    val status: Int = 200,
)

/**
 * The three shelves of the anime home page.
 *
 * The original builds the shelf with the `topWeek` list and falls back to
 * `topDay` when the first one is missing.
 */
data class HiAnimeHome(
    val trending: List<HiAnimeItem>,
    val latest: List<HiAnimeItem>,
    val spotlight: List<HiAnimeItem>,
)

/** One playable source of an episode. The original always reports `"hls"`. */
data class HiAnimeSource(
    val url: String,
    val type: String = "hls",
)

/**
 * One audio server of an episode.
 *
 * The original's class takes its two fields in the order `serverName`,
 * `serverId` — that is the order every call site uses — but its `equals`,
 * `hashCode` and `toString` compare and print them in the order `serverId`,
 * `serverName`. The secondary constructor reproduces the call order while the
 * primary one keeps the original's property order.
 */
data class HiAnimeServer(
    val serverId: Int?,
    val serverName: String,
) {
    constructor(serverName: String, serverId: Int?) : this(serverId, serverName)
}

/** The result of an anime search. */
data class HiAnimeSearchData(
    val animes: List<HiAnimeItem>,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false,
)

/**
 * The sub and dub servers of one episode.
 *
 * The original numbers the servers of each list separately, starting at zero.
 */
data class HiAnimeServersData(
    val episodeId: String,
    val episodeNo: Int?,
    val sub: List<HiAnimeServer>?,
    val dub: List<HiAnimeServer>?,
    val raw: List<HiAnimeServer>? = null,
)

/**
 * The playable sources of one episode, plus the tracks and the headers the
 * player needs.
 *
 * The original's constructor takes the tracks, the sources and the headers —
 * the two id fields are always null, so R8 dropped them from the constructor
 * and they live in the body here as well.
 */
data class HiAnimeSourcesData(
    val tracks: List<HiAnimeTrack>,
    val sources: List<HiAnimeSource>,
    val headers: Map<String, String>? = null,
) {
    /** Always null, exactly like in the original. */
    val anilistID: Int? = null

    /** Always null, exactly like in the original. */
    val malID: Int? = null
}
