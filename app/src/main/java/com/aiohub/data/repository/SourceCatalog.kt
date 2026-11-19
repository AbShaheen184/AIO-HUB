package com.aiohub.data.repository

/**
 * The providers the app can ask for sources, in the three groups the original's
 * settings screen shows.
 *
 * **What the original does.** The original's settings screen has three
 * collapsible sections — "Sources (http)", "Sources (Torrents)" and "Sources
 * (Anime)" — and each of them lists the providers of its group with a switch.
 * A provider that is switched off is written into the `disabled_providers`
 * preference and is then skipped when the catalogue is built
 * (`com.app.mlounge.data.repository.t`).
 *
 * **What the replica does differently, and why.** The original's lists
 * (`t.o`, `t.p`, `t.q` in `com.app.mlounge.data.repository.t`) are the names of
 * the *search sites* its catalogue is built from — Frame, HDHub, MovieNight,
 * Movies111, MoviesDrive, Movy, PeeStream, VidApi, VidFast, VidUp, VidZee,
 * Vuflix, VixSrc for http; Torrentio and TorrentClaw for torrents; HiAnime,
 * ReCloud, TryEmbed, VidNest and Anikoto for anime. The replica has no
 * per-site catalogue: it browses TMDB and asks the CHQ backend for the sources
 * of a title.
 *
 * The replica therefore lists the providers it can *actually* ask, and the
 * switch really switches that provider off — a disabled provider is not
 * queried any more. The names are the CHQ provider names, which is what the
 * backend itself uses:
 *
 * | Group | Providers | Route |
 * |---|---|---|
 * | http | `notorrent`, `nebula`, `hdhub`, `vidrock` | `api/streams/{provider}/{id}` |
 * | http | `multiembed`, `vidzee`, `fmovies`, `vixsrc`, `primesrc`, `twoembed`, `vidsrc`, `4khd`, `kmmovies`, `moviesmod`, `uhdmovies` | the original's per-provider routes (the fallback) |
 * | torrent | `torrentio` | the original's per-provider route, keyed by the IMDb id |
 * | anime | `anikoto` | the Anikoto scraper |
 */
object SourceCatalog {

    const val GROUP_HTTP = "http"
    const val GROUP_TORRENT = "torrent"
    const val GROUP_ANIME = "anime"

    /**
     * One provider the user can switch on or off.
     *
     * [key] is the name the backend and the `disabled_providers` preference use;
     * [label] is what the settings screen shows.
     */
    data class Source(
        val key: String,
        val label: String,
        val group: String,
    )

    /**
     * The providers of the live CHQ routes.
     *
     * Source list synced from the backend's /api/providers (MoviesTv).
     */
    val CURRENT_PROVIDERS = listOf(
        Source("4khdhub", "4KHDHub", GROUP_HTTP),
        Source("a111477", "A111477", GROUP_HTTP),
        Source("atlantic", "Atlantic", GROUP_HTTP),
        Source("barbarian", "Barbarian", GROUP_HTTP),
        Source("bingebang", "Bingebang", GROUP_HTTP),
        Source("bower", "Bower", GROUP_HTTP),
        Source("byse", "Byse", GROUP_HTTP),
        Source("castletv", "CastleTV", GROUP_HTTP),
        Source("cinejoy", "CineJoy", GROUP_HTTP),
        Source("cinejoytv", "CineJoyTV", GROUP_HTTP),
        Source("cinesrc", "CineSrc", GROUP_HTTP),
        Source("cinestream", "CineStream", GROUP_HTTP),
        Source("dahmermovies", "DahmerMovies", GROUP_HTTP),
        Source("desiflix", "DesiFlix", GROUP_HTTP),
        Source("dexter", "Dexter", GROUP_HTTP),
        Source("duro", "Duro", GROUP_HTTP),
        Source("framextv", "FrameXTV", GROUP_HTTP),
        Source("hdmovie2", "HDMovie2", GROUP_HTTP),
        Source("kisskh", "Kisskh", GROUP_HTTP),
        Source("mapple", "Mapple", GROUP_HTTP),
        Source("meowtv", "MeowTV", GROUP_HTTP),
        Source("miner", "Miner", GROUP_HTTP),
        Source("moviebox", "Moviebox", GROUP_HTTP),
        Source("movies4u", "Movies4u", GROUP_HTTP),
        Source("moviesdrive1", "MoviesDrive1", GROUP_HTTP),
        Source("movieshunt", "MoviesHunt", GROUP_HTTP),
        Source("moviesjoy", "MoviesJoy", GROUP_HTTP),
        Source("movix", "Movix", GROUP_HTTP),
        Source("netmirror", "NetMirror", GROUP_HTTP),
        Source("noxpulse", "NoxPulse", GROUP_HTTP),
        Source("onetouchtv", "OneTouchTV", GROUP_HTTP),
        Source("showbox", "ShowBox", GROUP_HTTP),
        Source("streamflix", "StreamFlix", GROUP_HTTP),
        Source("superstream", "SuperStream", GROUP_HTTP),
        Source("titan", "Titan", GROUP_HTTP),
        Source("uhdmovies", "UHDMovies", GROUP_HTTP),
        Source("valkyrie", "Valkyrie", GROUP_HTTP),
        Source("vaplayer", "VAPlayer", GROUP_HTTP),
        Source("vegamovies", "VegaMovies", GROUP_HTTP),
        Source("vidbolt", "VidBolt", GROUP_HTTP),
        Source("vidbox", "VidBox", GROUP_HTTP),
        Source("videasy", "VidEasy", GROUP_HTTP),
        Source("vidfast", "VidFast", GROUP_HTTP),
        Source("vidlink", "VidLink", GROUP_HTTP),
        Source("vidrock", "VidRock", GROUP_HTTP),
        Source("vidsrc", "VidSrc", GROUP_HTTP),
        Source("vidstuck", "VidStuck", GROUP_HTTP),
        Source("vidy", "Vidy", GROUP_HTTP),
        Source("vixsrc", "VixSrc", GROUP_HTTP),
        Source("vumeto", "Vumeto", GROUP_HTTP),
    )

    /**
     * The original's per-provider routes, kept as a fallback.
     *
     * The live backend answers `404` for all of them, so they are only reached
     * when the current routes fail.
     */
    val LEGACY_PROVIDERS = emptyList<Source>()

    /** The torrent provider. It is keyed by the IMDb id, not by the TMDB id. */
    val TORRENT_PROVIDERS = listOf(
        Source("torrentio", "Torrentio", GROUP_TORRENT),
    )

    /** The anime source. The Anikoto scraper is the replica's only anime source. */
    val ANIME_PROVIDERS = listOf(
        Source("anikoto", "Anikoto", GROUP_ANIME),
    )

    /** Every provider of the http group, current ones first. */
    val HTTP: List<Source> = CURRENT_PROVIDERS + LEGACY_PROVIDERS

    /** Every provider of the torrent group. */
    val TORRENT: List<Source> = TORRENT_PROVIDERS

    /** Every provider of the anime group. */
    val ANIME: List<Source> = ANIME_PROVIDERS

    /** The providers of one group. */
    fun of(group: String): List<Source> = when (group) {
        GROUP_HTTP -> HTTP
        GROUP_TORRENT -> TORRENT
        GROUP_ANIME -> ANIME
        else -> emptyList()
    }

    /** The label of a provider key, or the key itself when it is unknown. */
    fun labelOf(key: String): String =
        (HTTP + TORRENT + ANIME).firstOrNull { it.key == key }?.label ?: key
}
