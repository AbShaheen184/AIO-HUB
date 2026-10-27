package com.movies.premium.data.iptv

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.movies.premium.data.local.dao.IptvChannelDao
import com.movies.premium.data.local.entity.IptvChannelEntity
import com.movies.premium.data.remote.model.IptvChannel
import com.movies.premium.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-supplied IPTV sources (custom M3U playlists, Xtream Codes servers and
 * XMLTV guides).
 *
 * Mirrors the original's `com.app.mlounge.data.iptv.p` (R8 name `p`).
 *
 * Custom channels are stored in the same `iptv_channels` table as the built-in
 * providers, under the provider slug [SLUG] (`"custom"`).
 */
@Singleton
class CustomIPTV @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val channelDao: IptvChannelDao,
) {
    private val gson = Gson()
    private val playlistDir: File = File(context.filesDir, PLAYLIST_DIR).apply { mkdirs() }

    /** Downloads [url] as text. Throws when the server answers with an error. */
    fun fetchText(url: String): String {
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build(),
        ).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return response.body?.string() ?: throw Exception("Empty response body")
    }

    /** Maps a wire channel to a database row under the `custom` provider. */
    fun toEntity(ch: IptvChannel): IptvChannelEntity = IptvChannelEntity(
        name = ch.name ?: "",
        url = ch.url ?: "",
        logo = ch.logo,
        groupName = ch.group,
        provider = SLUG,
        tvgId = ch.tvgId,
    )

    /**
     * Builds the base URL of an Xtream Codes server.
     *
     * Adds a scheme when the host has none and appends the port when given.
     */
    fun buildBaseUrl(host: String, port: String?): String {
        var s = host.trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return if (port.isNullOrBlank()) s else "$s:$port"
    }

    /** Removes every custom channel and the stored custom EPG. */
    suspend fun clearCustom() {
        File(playlistDir, CHANNELS_FILE).delete()
        File(playlistDir, EPG_FILE).delete()
        channelDao.deleteByProvider(SLUG)
    }

    /**
     * Imports a remote M3U playlist.
     *
     * Returns the number of channels parsed. Existing `custom` rows are
     * deleted *before* the playlist is parsed, exactly like the original.
     */
    suspend fun importRemoteM3u(url: String, onProgress: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            onProgress("Downloading playlist...")
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build(),
            ).execute()
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val stream = response.body?.byteStream()
                ?: throw Exception("Empty response body")
            onProgress("Parsing channels...")
            channelDao.deleteByProvider(SLUG)
            val batch = ArrayList<IptvChannelEntity>()
            var total = 0
            M3uParser.parse(stream, SLUG) { ch ->
                batch.add(toEntity(ch))
                total++
                if (batch.size >= BATCH_SIZE) {
                    channelDao.insertAll(ArrayList(batch))
                    batch.clear()
                }
                onProgress("Parsed $total channels...")
            }
            if (batch.isNotEmpty()) {
                channelDao.insertAll(ArrayList(batch))
            }
            File(playlistDir, CHANNELS_FILE).writeText("{}")
            Logger.d(TAG, "M3U: $total channels saved to DB")
            total
        }

    /**
     * Imports the live channels of an Xtream Codes server.
     *
     * Returns the channels that were written to the database.
     */
    suspend fun importXtream(
        host: String,
        port: String?,
        username: String,
        password: String,
        onProgress: (String) -> Unit,
    ): List<IptvChannel> = withContext(Dispatchers.IO) {
        val base = buildBaseUrl(host, port)
        onProgress("Connecting to server...")
        onProgress("Downloading categories...")
        val catJson = fetchText(
            "$base/player_api.php?username=$username&password=$password&action=get_live_categories",
        )
        val categories = LinkedHashMap<String, String>()
        try {
            val arr = JSONArray(catJson)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("category_id", "")
                val name = o.optString("category_name", "")
                if (id.isNotBlank()) {
                    categories[id] = if (name.isBlank()) "Uncategorized" else name
                }
            }
        } catch (_: Exception) {
            // Ignored on purpose — the original tolerates a broken category list.
        }
        Logger.d(TAG, "Xtream: ${categories.size} categories")
        onProgress("Downloading channel list...")
        val streamsJson = fetchText(
            "$base/player_api.php?username=$username&password=$password&action=get_live_streams",
        )
        val channels = ArrayList<IptvChannel>()
        try {
            val arr = JSONArray(streamsJson)
            onProgress("Parsing ${arr.length()} channels...")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name", "")
                if (name.isNotBlank()) {
                    val streamId = o.optString("stream_id", "")
                    if (streamId.isNotBlank()) {
                        val group = categories[o.optString("category_id", "")] ?: "Uncategorized"
                        val logo = o.optString("stream_icon", "").ifBlank { null }
                        val epgId = o.optString("epg_channel_id", "").ifBlank { null }
                        channels.add(
                            IptvChannel(
                                name = name,
                                url = "$base/live/$username/$password/$streamId.m3u8",
                                logo = logo,
                                group = group,
                                provider = SLUG,
                                tvgId = epgId,
                                fallbackLogo = null,
                            ),
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Ignored on purpose — the original tolerates a broken stream list.
        }
        onProgress("Saving ${channels.size} channels to database...")
        channelDao.deleteByProvider(SLUG)
        channels.chunked(BATCH_SIZE).forEach { chunk ->
            channelDao.insertAll(chunk.map { toEntity(it) })
        }
        File(playlistDir, CHANNELS_FILE).writeText("{}")
        Logger.d(TAG, "Xtream: ${channels.size} channels saved to DB")
        channels
    }

    /**
     * Downloads and parses an Xtream Codes XMLTV guide.
     *
     * Returns the parsed programmes per channel id, or an empty map on failure.
     */
    suspend fun importRemoteEpg(
        host: String,
        port: String?,
        username: String,
        password: String,
        onProgress: (String) -> Unit,
    ): Map<String, List<EpgProgram>> {
        val base = buildBaseUrl(host, port)
        val url = "$base/xmltv.php?username=$username&password=$password"
        return try {
            withContext(Dispatchers.IO) {
                onProgress("Downloading EPG data (this may take a while)...")
                val response = client.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build(),
                ).execute()
                if (!response.isSuccessful) throw Exception("EPG HTTP ${response.code}")
                val stream = response.body?.byteStream()
                    ?: throw Exception("Empty response body")
                onProgress("Parsing EPG programs...")
                val map = XmlTvParser.parse(stream)
                if (map.isNotEmpty()) {
                    onProgress("Saving EPG (${map.size} channels)...")
                    saveEpg(map)
                }
                Logger.d(
                    TAG,
                    "EPG: ${map.size} channels, ${map.values.sumOf { it.size }} programs",
                )
                map
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Xtream EPG failed: ${e.message}")
            emptyMap()
        }
    }

    /** Imports an XMLTV guide from a local file (a `content://` URI). */
    suspend fun importLocalEpg(
        uriString: String,
        onProgress: (String) -> Unit,
    ): Map<String, List<EpgProgram>> = withContext(Dispatchers.IO) {
        onProgress("Opening local EPG...")
        val stream = context.contentResolver.openInputStream(Uri.parse(uriString))
            ?: throw Exception("Could not open local EPG file")
        onProgress("Parsing EPG programs...")
        val map = XmlTvParser.parse(stream)
        if (map.isNotEmpty()) {
            onProgress("Saving EPG (${map.size} channels)...")
            saveEpg(map)
        }
        Logger.d(TAG, "Local EPG: ${map.size} channels")
        map
    }

    /** Imports an M3U playlist from a local file (a `content://` URI). */
    suspend fun importLocalM3u(uriString: String, onProgress: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            onProgress("Opening local file...")
            val stream = context.contentResolver.openInputStream(Uri.parse(uriString))
                ?: throw Exception("Could not open local file")
            onProgress("Parsing channels...")
            channelDao.deleteByProvider(SLUG)
            val batch = ArrayList<IptvChannelEntity>()
            var total = 0
            M3uParser.parse(stream, SLUG) { ch ->
                batch.add(toEntity(ch))
                total++
                if (batch.size >= BATCH_SIZE) {
                    channelDao.insertAll(ArrayList(batch))
                    batch.clear()
                }
                onProgress("Synced $total channels...")
            }
            if (batch.isNotEmpty()) {
                channelDao.insertAll(ArrayList(batch))
            }
            File(playlistDir, CHANNELS_FILE).writeText("{}")
            Logger.d(TAG, "Local M3U: $total channels saved to DB")
            total
        }

    /** `true` when a custom EPG has been imported. */
    fun hasCustomEpg(): Boolean = File(playlistDir, EPG_FILE).exists()

    /**
     * Loads the stored EPG programmes for the given channel ids.
     *
     * Channels that are not present in the file are skipped; a partially read
     * file still yields the entries found so far.
     */
    fun loadEpgFor(channelIds: Set<String>): Map<String, List<EpgProgram>> {
        val file = File(playlistDir, EPG_FILE)
        if (!file.exists() || channelIds.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, List<EpgProgram>>()
        try {
            val type = object : TypeToken<List<EpgProgram>>() {}.type
            JsonReader(
                BufferedReader(
                    InputStreamReader(FileInputStream(file), Charsets.UTF_8),
                    8192,
                ),
            ).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (channelIds.contains(name)) {
                        out[name] = gson.fromJson(reader, type) ?: emptyList()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load EPG for channels: ${e.message}")
            return out
        }
        return out
    }

    /** Persists the EPG programmes to `custom_epg.json`. */
    fun saveEpg(map: LinkedHashMap<String, List<EpgProgram>>) {
        val type = object : TypeToken<Map<String, List<EpgProgram>>>() {}.type
        BufferedWriter(
            OutputStreamWriter(
                FileOutputStream(File(playlistDir, EPG_FILE)),
                Charsets.UTF_8,
            ),
            8192,
        ).use { writer ->
            gson.toJson(map, type, writer)
        }
    }

    companion object {
        const val CHANNELS_FILE = "custom_channels.json"
        const val EPG_FILE = "custom_epg.json"
        const val PLAYLIST_DIR = "playlists"
        const val SLUG = "custom"
        const val TAG = "CustomIPTV"

        private const val USER_AGENT = "Mozilla/5.0"
        private const val BATCH_SIZE = 1000
    }
}
