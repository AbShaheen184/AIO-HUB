package com.aiohub.data.repository

import com.aiohub.data.remote.api.ChqApi
import com.aiohub.data.remote.model.ChqStream
import com.aiohub.data.remote.model.ChqStreamItem
import com.aiohub.data.remote.model.ChqStreamsResponse
import com.aiohub.data.remote.model.ChqStreamResponse
import com.aiohub.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves playable sources for a title.
 *
 * The original queries every stream provider in parallel and merges the results,
 * so a title stays playable even when individual providers are down. This
 * mirrors that behaviour: each provider is an independent coroutine and a
 * failing provider never aborts the others.
 *
 * **The routes.** The original's `ChqApi` has one endpoint per provider
 * (`api/multiembed/streams`, `api/vidzee/streams`, …). The live backend has
 * since replaced them with the `api/streams/...` family and the old routes now
 * answer `404 {"status":404,"message":"Not Found"}`. The replica asks the new
 * routes first and falls back to the original's routes when they are not
 * available, so it works against both generations of the backend.
 *
 * **Nothing here throws.** A stream lookup runs in a `viewModelScope`, where an
 * uncaught exception takes the whole process down, so every failure is caught
 * and logged and the caller gets an empty list instead.
 */
@Singleton
class StreamsRepository @Inject constructor(
    private val api: ChqApi,
    private val providerPreferences: ProviderPreferences,
) {

    data class ProviderResult(
        val provider: String,
        val streams: List<ChqStream>,
        val error: String? = null,
    )

    /**
     * Fetches streams from all providers and returns the merged,
     * de-duplicated list. [imdbId] is only used by the legacy torrent provider.
     *
     * Each provider is called independently in parallel. A slow provider never
     * blocks fast ones. This mirrors the original app's behaviour of calling
     * each provider directly rather than through a slow aggregate endpoint.
     */
    suspend fun fetchAll(
        tmdbId: Int,
        mediaType: String = "movie",
        season: Int? = null,
        episode: Int? = null,
        imdbId: String? = null,
        token: String? = null,
    ): List<ChqStream> {
        val merged = LinkedHashMap<String, ChqStream>()
        fetchAllProgressive(tmdbId, mediaType, season, episode, imdbId, token).collect { result ->
            for (stream in result.streams) {
                val url = stream.url ?: continue
                val label = stream.name?.takeIf { it.isNotBlank() } ?: result.provider
                val key = "$label|$url"
                if (!merged.containsKey(key)) {
                    merged[key] = if (stream.name.isNullOrBlank()) {
                        stream.copy(name = result.provider)
                    } else {
                        stream
                    }
                }
            }
        }
        Logger.i(TAG, "Resolved ${merged.size} streams for tmdb=$tmdbId")
        return merged.values.toList()
    }

    /**
     * Same fan-out as [fetchAll], but emits one [ProviderResult] per provider as
     * soon as that provider answers.
     *
     * The sources screen collects this flow, so the first provider's mirrors are
     * on screen while the remaining providers are still being asked. The flow
     * completes once every provider has answered (or failed), and only then
     * falls back to the legacy routes when nothing was found at all.
     */
    fun fetchAllProgressive(
        tmdbId: Int,
        mediaType: String = "movie",
        season: Int? = null,
        episode: Int? = null,
        imdbId: String? = null,
        token: String? = null,
    ): Flow<ProviderResult> = channelFlow {
        val id = tmdbId.toString()
        val hasEpisode = (season ?: 0) > 0 && (episode ?: 0) > 0

        // Map the app's mediaType ("movie"/"tv") to the server's expected type
        // ("movie"/"series"). The server's per-provider routes use this in the URL path.
        val serverType = if (mediaType == "tv") "series" else "movie"

        // A series is only resolvable per episode: the backend looks a bare id up
        // in its *movie* table and answers with a completely different title, so
        // asking without a season and an episode would return the wrong film.
        val effectiveSeason = if (mediaType == "tv" && !hasEpisode) 1 else season
        val effectiveEpisode = if (mediaType == "tv" && !hasEpisode) 1 else episode
        val effectiveHasEpisode = (effectiveSeason ?: 0) > 0 && (effectiveEpisode ?: 0) > 0

        if (mediaType == "tv" && !effectiveHasEpisode) {
            Logger.d(TAG, "No episode chosen for tv id=$id — nothing to resolve")
            return@channelFlow
        }

        // The providers the user switched off in the settings are not asked.
        val disabled = disabledProviders()
        val providers = CURRENT_PROVIDERS.filter { it !in disabled }
        val found = AtomicInteger(0)

        // Every provider runs in its own coroutine and every failure is caught, so
        // a provider that is down only removes its own entries. Each result is
        // sent the moment that provider finishes.
        supervisorScope {
            providers.forEach { provider ->
                launch(Dispatchers.IO) {
                    val result = try {
                        val streams = if (effectiveHasEpisode) {
                            api.getStreamsFromProviderForEpisode(
                                provider, serverType, id,
                                effectiveSeason ?: 0, effectiveEpisode ?: 0, token
                            )
                        } else {
                            api.getStreamsFromProvider(
                                provider, serverType, id,
                                effectiveSeason, effectiveEpisode, token
                            )
                        }
                        ProviderResult(provider, streams.toStreams())
                    } catch (e: Exception) {
                        Logger.d(TAG, "Provider '$provider' failed: ${e.message}")
                        ProviderResult(provider, emptyList(), e.message)
                    }
                    found.addAndGet(result.streams.size)
                    send(result)
                }
            }
        }

        if (found.get() == 0 && hasLegacyFallback(mediaType, season, episode)) {
            Logger.d(TAG, "New stream route returned nothing for id=$id — trying the legacy routes")
            val legacy = fetchLegacy(tmdbId, mediaType, season, episode, imdbId, disabled)
            if (legacy.isNotEmpty()) send(ProviderResult("legacy", legacy))
        }
    }

    /** Resolves a single provider, used by the provider-picker sheet. */
    suspend fun fetchFrom(
        provider: String,
        tmdbId: Int,
        mediaType: String = "movie",
        season: Int? = null,
        episode: Int? = null,
        token: String? = null,
    ): List<ChqStream> {
        val id = tmdbId.toString()
        val hasEpisode = (season ?: 0) > 0 && (episode ?: 0) > 0
        if (mediaType == "tv" && !hasEpisode) return emptyList()

        // Map mediaType to server's expected type
        val serverType = if (mediaType == "tv") "series" else "movie"

        // A provider the user switched off is not asked for its sources.
        if (provider in disabledProviders()) {
            Logger.d(TAG, "fetchFrom($provider) — the provider is switched off")
            return emptyList()
        }

        return try {
            if (provider in CURRENT_PROVIDERS) {
                val response = if (hasEpisode) {
                    api.getStreamsFromProviderForEpisode(
                        provider, serverType, id,
                        season ?: 0, episode ?: 0, token
                    )
                } else {
                    api.getStreamsFromProvider(
                        provider, serverType, id,
                        season, episode, token
                    )
                }
                response.toStreams()
            } else {
                api.getLegacyStreams(provider, id, mediaType, season, episode).streams.orEmpty()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "fetchFrom($provider) failed", e)
            emptyList()
        }
    }

    /**
     * The providers the user switched off.
     *
     * Mirrors the original's `disabled_providers` preference, which the
     * "Sources (http)", "Sources (Torrents)" and "Sources (Anime)" sections of
     * the settings screen write.
     */
    private suspend fun disabledProviders(): Set<String> =
        providerPreferences.disabledProviders.first()

    /**
     * The original's own fan-out: one request per provider, merged afterwards.
     *
     * Every provider runs in its own coroutine and every failure is caught, so a
     * provider that is down only removes its own entries.
     */
    private suspend fun fetchLegacy(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        imdbId: String?,
        disabled: Set<String>,
    ): List<ChqStream> = supervisorScope {
        val results = buildList {
            // A provider the user switched off is never asked.
            for (provider in LEGACY_PROVIDERS) {
                if (provider in disabled) continue
                add(async { legacy(provider, tmdbId, mediaType, season, episode) })
            }
            if (!imdbId.isNullOrBlank() && TORRENT_KEYS.none { it in disabled }) {
                add(async { legacyTorrent(imdbId, mediaType, season, episode) })
            }
        }.awaitAll()

        val merged = LinkedHashMap<String, ChqStream>()
        for (result in results) {
            for (stream in result.streams) {
                val url = stream.url ?: continue
                // The provider label lives in the stream's `name` (wire key
                // "server"); fall back to the endpoint that produced it.
                val label = stream.name?.takeIf { it.isNotBlank() } ?: result.provider
                val key = "$label|$url"
                if (!merged.containsKey(key)) {
                    merged[key] = if (stream.name.isNullOrBlank()) {
                        stream.copy(name = result.provider)
                    } else {
                        stream
                    }
                }
            }
        }
        Logger.i(TAG, "Resolved ${merged.size} streams from the legacy routes for tmdb=$tmdbId")
        merged.values.toList()
    }

    /** One legacy provider call, with its failure caught. */
    private suspend fun legacy(
        provider: String,
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): ProviderResult {
        val id = tmdbId.toString()
        return try {
            val streams = api.getLegacyStreams(provider, id, mediaType, season, episode)
                .streams.orEmpty()
            ProviderResult(provider, streams)
        } catch (e: Exception) {
            Logger.d(TAG, "Legacy provider '$provider' failed: ${e.message}")
            ProviderResult(provider, emptyList(), e.message)
        }
    }

    /** The legacy torrent provider, which is keyed by the IMDb id. */
    private suspend fun legacyTorrent(
        imdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): ProviderResult = try {
        ProviderResult(
            "torrentio",
            api.getTorrentioStreams(imdbId, mediaType, season, episode).streams.orEmpty(),
        )
    } catch (e: Exception) {
        Logger.d(TAG, "Legacy torrent provider failed: ${e.message}")
        ProviderResult("torrentio", emptyList(), e.message)
    }

    /**
     * Maps the answer of the current routes into the original's stream model.
     *
     * The backend sends one `title` per source, built in three lines — the host
     * and the size, the language, and the server. The last line is the server
     * label the original shows as the stream's name, the resolution is pulled out
     * of the first line, and the size comes from the `behaviorHints`.
     */
    private fun ChqStreamsResponse.toStreams(): List<ChqStream> {
        val failures = errors.orEmpty()
        if (failures.isNotEmpty()) {
            Logger.d(
                TAG,
                "Stream providers failed: " +
                    failures.joinToString { "${it.provider}=${it.error}" },
            )
        }
        return streams.orEmpty().mapNotNull { it.toChqStream() }
    }

    /** Maps one source of the current routes into the original's stream model. */
    private fun ChqStreamItem.toChqStream(): ChqStream? {
        val link = url?.takeIf { it.isNotBlank() } ?: return null
        val lines = title?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val server = lines.lastOrNull()?.takeIf { it.isNotBlank() } ?: provider
        val resolution = QUALITY.find(title.orEmpty())?.value
        val bytes = behaviorHints?.videoSize?.takeIf { it > 0 }
        val isHls = link.contains(".m3u8", ignoreCase = true)
        return ChqStream(
            name = server,
            url = link,
            quality = resolution,
            type = if (isHls) "hls" else "direct",
            streamType = if (isHls) "hls" else "direct",
            size = bytes?.let { humanSize(it) },
            description = title,
            provider = provider,
            headers = headers,
            subtitles = subtitles,
        )
    }

    private fun ChqStream.normalize(): ChqStream {
        val displayName = name?.takeIf { it.isNotBlank() } ?: title ?: "Stream"
        val isHls = url?.contains(".m3u8", ignoreCase = true) == true
        return copy(
            name = displayName,
            type = type ?: if (isHls) "hls" else "direct",
            streamType = streamType ?: if (isHls) "hls" else "direct",
        )
    }

    /** True when the legacy routes could still answer this request. */
    private fun hasLegacyFallback(mediaType: String, season: Int?, episode: Int?): Boolean =
        mediaType != "tv" || ((season ?: 0) > 0 && (episode ?: 0) > 0)

    companion object {
        private const val TAG = "StreamsRepo"

        /**
         * The providers the current backend fans out over via per-provider routes.
         *
         * These are CHQ provider names that exist on the backend (queried from
         * /api/providers). We call these providers independently in parallel as a
         * fallback when the aggregate route is too slow through Cloudflare Tunnel.
         */
        val CURRENT_PROVIDERS = listOf(
            "showbox",
            "castletv",
            "mapple",
            "meowtv",
            "movix",
            "noxpulse",
            "playimdb",
            "streamflix",
            "vaplayer",
            "vidbolt",
            "vidbox",
            "vidlink",
            "vixsrc",
            "a111477",
            "bower",
            "byse",
            "barbarian",
            "4khdhub",
        )

        /** The keys of the original's per-provider routes, in the original's order. */
        private val LEGACY_PROVIDERS = listOf(
            "multiembed",
            "vidzee",
            "vidrock",
            "fmovies",
            "vixsrc",
            "primesrc",
            "twoembed",
            "vidsrc",
            "4khd",
            "kmmovies",
            "moviesmod",
            "uhdmovies",
        )

        /** The keys of the torrent providers. */
        private val TORRENT_KEYS = listOf("torrentio")

        /**
         * The resolution of a release, e.g. `1080p`.
         *
         * The letters around the number must not be word characters, so `4KHDHub`
         * is not read as a `4K` quality.
         */
        private val QUALITY =
            Regex("(?<![A-Za-z0-9])(2160p|1440p|1080p|720p|480p|4K|UHD)(?![A-Za-z0-9])")

        /** Formats a byte count the way the original does. */
        private fun humanSize(bytes: Long): String = when {
            bytes >= 1_073_741_824 ->
                String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

            bytes >= 1_048_576 ->
                String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)

            bytes >= 1_024 ->
                String.format(Locale.US, "%.0f KB", bytes / 1_024.0)

            else -> "$bytes B"
        }
    }
}

/**
 * Legacy per-provider routes for the original's CHQ API.
 * Maps provider names to the corresponding API method.
 */
private suspend fun ChqApi.getLegacyStreams(
    provider: String,
    tmdbId: String,
    mediaType: String,
    season: Int?,
    episode: Int?,
): ChqStreamResponse {
    val type = if (mediaType == "tv") "series" else "movie"
    return when (provider) {
        "multiembed" -> getMultiEmbedStreams(tmdbId, type, season, episode)
        "vidzee" -> getVidzeeStreams(tmdbId, type, season, episode)
        "vidrock" -> getVidrockStreams(tmdbId, type, season, episode)
        "fmovies" -> getFmoviesStreams(tmdbId, type, season, episode)
        "vixsrc" -> getVixsrcStreams(tmdbId, type, season, episode)
        "primesrc" -> getPrimesrcStreams(tmdbId, type, season, episode)
        "twoembed" -> getTwoembedStreams(tmdbId, type, season, episode)
        "vidsrc" -> getVidsrcStreams(tmdbId, type, season, episode)
        "4khd" -> get4khdStreams(tmdbId, type, season, episode)
        "kmmovies" -> getKmmoviesStreams(tmdbId, type, season, episode)
        "moviesmod" -> getMoviesmodStreams(tmdbId, type, season, episode)
        "uhdmovies" -> getUhdmoviesStreams(tmdbId, type, season, episode)
        else -> throw IllegalArgumentException("Unknown legacy provider '$provider'")
    }
}
