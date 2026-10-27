package com.movies.premium.data.repository

import com.movies.premium.data.remote.api.ChqApi
import com.movies.premium.data.remote.model.ChqStream
import com.movies.premium.data.remote.model.ChqStreamItem
import com.movies.premium.data.remote.model.ChqStreamsResponse
import com.movies.premium.util.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import java.util.Locale
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
     */
    suspend fun fetchAll(
        tmdbId: Int,
        mediaType: String = "movie",
        season: Int? = null,
        episode: Int? = null,
        imdbId: String? = null,
    ): List<ChqStream> {
        val id = tmdbId.toString()
        val hasEpisode = (season ?: 0) > 0 && (episode ?: 0) > 0

        // A series is only resolvable per episode: the backend looks a bare id up
        // in its *movie* table and answers with a completely different title, so
        // asking without a season and an episode would return the wrong film.
        if (mediaType == "tv" && !hasEpisode) {
            Logger.d(TAG, "No episode chosen for tv id=$id — nothing to resolve")
            return emptyList()
        }

        // The providers the user switched off in the settings are not asked.
        val disabled = disabledProviders()

        val current = try {
            val response = if (hasEpisode) {
                api.getAllStreamsForEpisode(id, season ?: 0, episode ?: 0)
            } else {
                api.getAllStreams(id)
            }
            response.toStreams()
        } catch (e: Exception) {
            Logger.d(TAG, "New stream route failed for id=$id: ${e.message} — trying the legacy routes")
            return fetchLegacy(tmdbId, mediaType, season, episode, imdbId, disabled)
        }

        val enabled = current.filter { it.provider !in disabled }

        if (enabled.isEmpty() && hasLegacyFallback(mediaType, season, episode)) {
            Logger.d(TAG, "New stream route returned nothing for id=$id — trying the legacy routes")
            val legacy = fetchLegacy(tmdbId, mediaType, season, episode, imdbId, disabled)
            if (legacy.isNotEmpty()) return legacy
        }

        Logger.i(TAG, "Resolved ${enabled.size} streams for tmdb=$id type=$mediaType")
        return enabled
    }

    /** Resolves a single provider, used by the provider-picker sheet. */
    suspend fun fetchFrom(
        provider: String,
        tmdbId: Int,
        mediaType: String = "movie",
        season: Int? = null,
        episode: Int? = null,
    ): List<ChqStream> {
        val id = tmdbId.toString()
        val hasEpisode = (season ?: 0) > 0 && (episode ?: 0) > 0
        if (mediaType == "tv" && !hasEpisode) return emptyList()

        // A provider the user switched off is not asked for its sources.
        if (provider in disabledProviders()) {
            Logger.d(TAG, "fetchFrom($provider) — the provider is switched off")
            return emptyList()
        }

        return try {
            if (provider in CURRENT_PROVIDERS) {
                val response = if (hasEpisode) {
                    api.getStreamsFromProviderForEpisode(provider, id, season ?: 0, episode ?: 0)
                } else {
                    api.getStreamsFromProvider(provider, id)
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
        Logger.d(TAG, "Legacy provider 'torrentio' failed: ${e.message}")
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
        )
    }

    /** True when the legacy routes could still answer this request. */
    private fun hasLegacyFallback(mediaType: String, season: Int?, episode: Int?): Boolean =
        mediaType != "tv" || ((season ?: 0) > 0 && (episode ?: 0) > 0)

    companion object {
        private const val TAG = "StreamsRepo"

        /**
         * The providers the current backend fans out over.
         *
         * The backend rejects any other name with
         * `{"error":"Invalid provider. Use one of: notorrent, nebula, hdhub, vidrock"}`.
         */
        val CURRENT_PROVIDERS = listOf("notorrent", "nebula", "hdhub", "vidrock")

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
 * Calls one of the original's per-provider routes.
 *
 * The original's `ChqApi` declares a separate method per provider; this is the
 * same set of routes behind one name, so the repository does not have to repeat
 * the whole `when`.
 */
private suspend fun ChqApi.getLegacyStreams(
    provider: String,
    id: String,
    mediaType: String,
    season: Int?,
    episode: Int?,
): com.movies.premium.data.remote.model.ChqStreamResponse = when (provider) {
    "multiembed" -> getMultiEmbedStreams(id, mediaType, season, episode)
    "vidzee" -> getVidzeeStreams(id, mediaType, season, episode)
    "vidrock" -> getVidrockStreams(id, mediaType, season, episode)
    "fmovies" -> getFmoviesStreams(id, mediaType, season, episode)
    "vixsrc" -> getVixsrcStreams(id, mediaType, season, episode)
    "primesrc" -> getPrimesrcStreams(id, mediaType, season, episode)
    "twoembed" -> getTwoembedStreams(id, mediaType, season, episode)
    "vidsrc" -> getVidsrcStreams(id, mediaType, season, episode)
    "4khd" -> get4khdStreams(id, mediaType, season, episode)
    "kmmovies" -> getKmmoviesStreams(id, mediaType, season, episode)
    "moviesmod" -> getMoviesmodStreams(id, mediaType, season, episode)
    "uhdmovies" -> getUhdmoviesStreams(id, mediaType, season, episode)
    else -> throw IllegalArgumentException("Unknown provider '$provider'")
}
