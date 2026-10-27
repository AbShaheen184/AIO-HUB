package com.movies.premium.ui.navigation

import android.net.Uri

/**
 * Navigation destinations for the app.
 *
 * The route strings are **copied verbatim from the original** (`com.app.mlounge.ui.navigation`).
 * Do not "tidy" them: the original's strings are what any deep link, saved-state restore
 * and back-stack entry is keyed on.
 *
 * Source of truth: `G:\workdir\jadx-out\sources\com\app\mlounge\ui\navigation\*.java`
 * (see §3.4 of `G:\workdir\CINEMAHQ-HANDOFF.md`).
 */
object Routes {

    // ---- top-level destinations -------------------------------------------------

    const val MOVIES = "movies"
    const val TV_SHOWS = "tvshows"
    const val ANIME = "anime"
    const val MUSIC = "music"
    const val LIVE_TV = "livetv"
    const val GAMES = "games"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    const val WATCHLIST = "watchlist"
    const val HISTORY = "history"
    const val SERVICES = "services"
    const val ADULT = "adult"

    /**
     * **Replica addition.** The original has no "home" route — it opens straight onto
     * `search`. The replica keeps a home screen as the start destination because it
     * already implements the original's home layout; the route string itself is ours,
     * not the original's. Every other route below is verbatim.
     */
    const val HOME = "home"

    // ---- parameterised destinations ---------------------------------------------

    const val MOVIE_DETAIL = "movie_detail/{movieId}"
    const val TV_DETAIL = "tvshow_detail/{tvId}"
    const val ANIME_DETAIL = "anime_detail/{animeId}"
    const val ADULT_DETAIL = "adult_detail/{videoId}"
    const val MUSIC_ALBUM_DETAIL = "music_album_detail/{albumUrl}"
    const val MUSIC_ARTIST_DETAIL = "music_artist_detail/{artistUrl}"

    /**
     * The original's deep link to a game. The games screen itself opens the
     * details of a game as a sheet on top of the grid, so this destination is
     * only reached from outside the app.
     */
    const val GAME_DETAIL = "game_detail/{platform}/{gameSlug}"
    const val PERSON = "person/{personId}"

    /**
     * The sources picker. The original declares the whole query string in the route
     * pattern and reads the values back off the arguments.
     *
     * `server` and `category` carry the anime server the user picked; they are
     * empty for the other media types.
     */
    const val SOURCES_ROUTE =
        "sources?type={type}&id={id}&title={title}&season={season}&episode={episode}&mode={mode}" +
            "&server={server}&category={category}"

    /** The player. The original passes everything through the query string. */
    const val PLAYER_ROUTE =
        "player?url={url}&title={title}&headers={headers}&contentType={contentType}" +
            "&streamType={streamType}&tmdbId={tmdbId}&season={season}&episode={episode}" +
            "&contentKey={contentKey}&fallbackUrl={fallbackUrl}"

    // ---- builders ----------------------------------------------------------------

    fun movieDetail(id: Int) = "movie_detail/$id"

    fun tvDetail(id: Int) = "tvshow_detail/$id"

    fun animeDetail(id: String) = "anime_detail/$id"

    fun adultDetail(id: String) = "adult_detail/$id"

    fun musicAlbumDetail(url: String) = "music_album_detail/${Uri.encode(url)}"

    fun musicArtistDetail(url: String) = "music_artist_detail/${Uri.encode(url)}"

    fun gameDetail(platform: String, slug: String) = "game_detail/$platform/$slug"

    fun person(id: Int) = "person/$id"

    /**
     * Builds the sources route. Mirrors the original's `s.a(int, int, String, String,
     * String, String)`: `id` and `title` are URL-encoded, `type` and `mode` are not.
     */
    fun sources(
        type: String,
        id: String,
        title: String,
        season: Int = 0,
        episode: Int = 0,
        mode: String = MODE_PLAY,
        server: String = "",
        category: String = "",
    ): String = "sources?type=$type&id=${Uri.encode(id)}&title=${Uri.encode(title)}" +
        "&season=$season&episode=$episode&mode=$mode" +
        "&server=${Uri.encode(server)}&category=${Uri.encode(category)}"

    /**
     * Builds the player route. Mirrors the original's builder in `ui/i0.java`: `url`,
     * `title`, `headers`, `streamType`, `contentKey` and `fallbackUrl` are URL-encoded;
     * `contentType`, `tmdbId`, `season` and `episode` are not.
     */
    fun player(
        url: String,
        title: String = "",
        headers: String? = null,
        contentType: String = "",
        streamType: String = "",
        tmdbId: Int = 0,
        season: Int = 0,
        episode: Int = 0,
        contentKey: String = "",
        fallbackUrl: String = "",
    ): String = "player?url=${Uri.encode(url)}&title=${Uri.encode(title)}" +
        "&headers=${Uri.encode(headers.orEmpty())}&contentType=$contentType" +
        "&streamType=${Uri.encode(streamType)}&tmdbId=$tmdbId&season=$season&episode=$episode" +
        "&contentKey=${Uri.encode(contentKey)}&fallbackUrl=${Uri.encode(fallbackUrl)}"

    /** The `mode` value the original uses when the user picks a source to play. */
    const val MODE_PLAY = "play"

    /** The `mode` value the original uses when the user picks a source to download. */
    const val MODE_DOWNLOAD = "download"

    /**
     * The destinations that open *something* on top of a section: a detail
     * screen, the source picker, the player.
     *
     * The navigation saves the whole stack that belongs to a section when the
     * user leaves it, and restores that stack when the user comes back to the
     * section. A detail that was open when the user left therefore comes back
     * *on top of* the section — so tapping "Movies" in the menu opens the
     * detail of the last movie instead of the grid. These routes are
     * therefore popped before a section is entered.
     */
    val DETAIL_ROUTES = listOf(
        MOVIE_DETAIL,
        TV_DETAIL,
        ANIME_DETAIL,
        ADULT_DETAIL,
        MUSIC_ALBUM_DETAIL,
        MUSIC_ARTIST_DETAIL,
        GAME_DETAIL,
        PERSON,
        SOURCES_ROUTE,
        PLAYER_ROUTE,
    )
}

/*
 * The list of destinations shown in the side navigation rail lives in
 * `com.movies.premium.ui.screens.settings.SidebarDestinations` — it mirrors the
 * original's `com.app.mlounge.ui.navigation.w.f` list of thirteen destinations and it
 * carries the icon of each entry, which the rail needs.
 */
