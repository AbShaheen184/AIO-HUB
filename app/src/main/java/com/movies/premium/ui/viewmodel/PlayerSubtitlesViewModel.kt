package com.movies.premium.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.movies.premium.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import javax.inject.Inject

/**
 * A subtitle track.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.player.c1` (R8 name `c1`) field
 * for field: the original builds the same class in its track listener
 * (`z0.v(Tracks)`) and in the custom-subtitle callback (`o`).
 *
 * @param label the text shown in the picker.
 * @param url where the subtitle comes from. Empty for a track that is part of the
 *   media itself.
 * @param lang the ISO code of the language.
 * @param isLocal true for a subtitle that lives on the device.
 * @param isInternal true for a subtitle track that is part of the media file. The
 *   original's track listener marks those and selects them through the player's
 *   track selection instead of through the sidecar path.
 * @param isDefault true for the track the media declares as its default.
 * @param trackGroupIndex the index of the track group inside `Tracks`.
 * @param trackIndex the index of the track inside its group.
 * @param release the release the subtitle belongs to, when it came from a list.
 */
data class SubtitleItem(
    val label: String,
    val url: String,
    val lang: String,
    val isLocal: Boolean = false,
    val isInternal: Boolean = false,
    val isDefault: Boolean = false,
    val trackGroupIndex: Int = 0,
    val trackIndex: Int = 0,
    val release: String? = null,
)

/**
 * An audio track.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.player.a` (R8 name `a`), which
 * the original's track listener (`z0.v(Tracks)`) fills with
 * `AudioTrackInfo(label, groupIndex, trackIndex, isSelected)`.
 */
data class AudioTrackInfo(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean = false,
)

/**
 * The language names the player shows.
 *
 * Copied verbatim from the original's `com.app.mlounge.ui.screens.player.h0`
 * (`LANGUAGE_NAMES`, R8 name `a`). The original's map has **82** entries — the
 * brief asked for 95, but the decompiled original is the source of truth, so the
 * map below is the original's map, entry for entry.
 */
val LANGUAGE_NAMES: Map<String, String> = mapOf(
    "en" to "English",
    "fr" to "French",
    "de" to "German",
    "es" to "Spanish",
    "it" to "Italian",
    "pt" to "Portuguese",
    "ru" to "Russian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "zh" to "Chinese",
    "ar" to "Arabic",
    "hi" to "Hindi",
    "nl" to "Dutch",
    "pl" to "Polish",
    "sv" to "Swedish",
    "da" to "Danish",
    "fi" to "Finnish",
    "no" to "Norwegian",
    "cs" to "Czech",
    "hu" to "Hungarian",
    "ro" to "Romanian",
    "vi" to "Vietnamese",
    "th" to "Thai",
    "tr" to "Turkish",
    "el" to "Greek",
    "he" to "Hebrew",
    "id" to "Indonesian",
    "ms" to "Malay",
    "uk" to "Ukrainian",
    "bg" to "Bulgarian",
    "sr" to "Serbian",
    "hr" to "Croatian",
    "sk" to "Slovak",
    "sl" to "Slovenian",
    "lt" to "Lithuanian",
    "lv" to "Latvian",
    "et" to "Estonian",
    "ca" to "Catalan",
    "gl" to "Galician",
    "eu" to "Basque",
    "af" to "Afrikaans",
    "sw" to "Swahili",
    "tl" to "Filipino",
    "mn" to "Mongolian",
    "ne" to "Nepali",
    "sq" to "Albanian",
    "hy" to "Armenian",
    "ka" to "Georgian",
    "fa" to "Persian",
    "ur" to "Urdu",
    "am" to "Amharic",
    "my" to "Burmese",
    "km" to "Khmer",
    "lo" to "Lao",
    "si" to "Sinhala",
    "ta" to "Tamil",
    "te" to "Telugu",
    "kn" to "Kannada",
    "ml" to "Malayalam",
    "mr" to "Marathi",
    "gu" to "Gujarati",
    "pa" to "Punjabi",
    "bn" to "Bengali",
    "bo" to "Tibetan",
    "ps" to "Pashto",
    "tk" to "Turkmen",
    "uz" to "Uzbek",
    "kk" to "Kazakh",
    "ky" to "Kyrgyz",
    "tg" to "Tajik",
    "az" to "Azerbaijani",
    "be" to "Belarusian",
    "is" to "Icelandic",
    "ga" to "Irish",
    "cy" to "Welsh",
    "mt" to "Maltese",
    "lb" to "Luxembourgish",
    "mk" to "Macedonian",
    "bs" to "Bosnian",
    "oc" to "Occitan",
    "la" to "Latin",
    "eo" to "Esperanto",
)

/**
 * The name of a language.
 *
 * Mirrors the original's `h0.t(String)`: an unknown code is shown as it is, and a
 * blank code becomes "Unknown".
 */
fun languageName(code: String): String =
    LANGUAGE_NAMES[code.lowercase()] ?: if (code.isBlank()) "Unknown" else code

/**
 * Formats a position the way the original's player shows it.
 *
 * Mirrors the original's `h0.s(long)`: `mm:ss` below an hour, `h:mm:ss` above.
 */
fun formatPlayerTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return if (totalSeconds < 3600) {
        String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    } else {
        String.format("%d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
    }
}

/**
 * A subtitle that has been written into the app's cache.
 *
 * @param file the file the subtitle was written to.
 * @param mimeType the mime type that matches the file's content.
 */
data class CachedSubtitle(
    val file: File,
    val mimeType: String,
)

/**
 * One cue of a subtitle.
 *
 * @param startMs when the cue starts.
 * @param endMs when the cue ends.
 * @param text the text of the cue.
 */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/**
 * The subtitle half of the player.
 *
 * The original keeps this logic in the player's view model
 * (`com.app.mlounge.ui.screens.player.a1`): the method `a1.e(SubtitleItem, String,
 * long)` downloads the subtitle, converts it to WebVTT when it uses the
 * `{h}{m}text` format, writes it into the app's cache and hands the file to the
 * player as a sidecar subtitle.
 *
 * The replica keeps the same code in its own class so that the player view model
 * stays readable. It is a plain injected collaborator and **not** a Compose
 * `ViewModel`: the player's state lives in [PlayerViewModel] and the two halves
 * must not drift apart.
 */
class PlayerSubtitlesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {

    /**
     * Downloads the subtitle text of [item].
     *
     * The original's `a1` reads the subtitle with the same OkHttp client the player
     * uses, so the stream's headers are sent along.
     */
    suspend fun download(item: SubtitleItem, headers: Map<String, String>): String? {
        val uri = item.url
        if (uri.isBlank()) return null
        return try {
            if (uri.startsWith("content://") || uri.startsWith("file://")) {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    decode(stream.readBytes(), item.lang)
                }
            } else {
                val request = Request.Builder().url(uri).apply {
                    headers.forEach { (key, value) -> header(key, value) }
                }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Logger.e(TAG, "Subtitle request failed with ${response.code}")
                        null
                    } else {
                        response.body?.bytes()?.let { decode(it, item.lang) }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to download subtitle", e)
            null
        }
    }

    /**
     * Converts the subtitle and writes it into the app's cache.
     *
     * Mirrors the original's `a1.e(...)`:
     * - the byte-order mark is removed,
     * - a subtitle that uses the `{h}{m}text` format is converted to WebVTT,
     * - a WebVTT subtitle that has no header gets one,
     * - the file is written to `cacheDir/active_sub.<ext>`,
     * - the write is logged as `Wrote subtitle to <path>`.
     *
     * The original names the file after the *source* extension even when it
     * converted the content to WebVTT, which leaves the player with a WebVTT file
     * that claims to be an SSA subtitle. The replica names the file after the
     * content it actually wrote, so the sidecar can be parsed.
     */
    suspend fun writeToCache(item: SubtitleItem, content: String): CachedSubtitle? {
        return try {
            val withoutBom = content.trimStart().removePrefix("﻿")
            val isVtt = withoutBom.startsWith("WEBVTT", ignoreCase = true) ||
                item.url.contains(".vtt", ignoreCase = true)
            val converted = convert(withoutBom, isVtt)
            val extension = when {
                converted.isWebVtt -> "vtt"
                item.url.contains(".ass", ignoreCase = true) ||
                    item.url.contains(".ssa", ignoreCase = true) -> "ass"

                else -> "srt"
            }
            val file = File(context.cacheDir, "active_sub.$extension")
            withContext(Dispatchers.IO) { file.writeText(converted.text) }
            Logger.d(TAG, "Wrote subtitle to ${file.absolutePath}")
            CachedSubtitle(file, mimeTypeFor(extension))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to write subtitle file: ${e.message}", e)
            null
        }
    }

    /**
     * Removes the subtitle files the original deletes when the player closes.
     *
     * The original's `a1.d()` deletes `active_sub.srt`, `active_sub.vtt`,
     * `active_sub.ass`, `active_sub.ssa` and `active_sub.sub` from the cache.
     */
    fun clearCache() {
        for (extension in listOf("srt", "vtt", "ass", "ssa", "sub")) {
            try {
                File(context.cacheDir, "active_sub.$extension").delete()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * The text of the cue that is on screen at [positionMs].
     *
     * The offset the user set in the options sheet is applied, the same way the
     * original applies its `subtitleOffsetMs`.
     */
    fun cueAt(cues: List<SubtitleCue>, positionMs: Long, offsetMs: Long): String? {
        val position = positionMs + offsetMs
        return cues.firstOrNull { position >= it.startMs && position <= it.endMs }?.text
    }

    /**
     * Parses the cues of a WebVTT or SubRip subtitle.
     *
     * The original renders the subtitles itself out of the cached content, so the
     * replica needs the cues as well as the file.
     */
    fun parseCues(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            val arrow = line.indexOf("-->")
            if (arrow <= 0) {
                index++
                continue
            }
            val start = parseTimestamp(line.substring(0, arrow).trim()) ?: run {
                index++
                continue
            }
            val end = parseTimestamp(line.substring(arrow + 3).trim().substringBefore(" ")) ?: run {
                index++
                continue
            }
            index++
            val text = StringBuilder()
            while (index < lines.size && lines[index].isNotBlank()) {
                if (text.isNotEmpty()) text.append('\n')
                text.append(lines[index].trim())
                index++
            }
            if (text.isNotEmpty()) cues.add(SubtitleCue(start, end, text.toString()))
        }
        return cues
    }

    /**
     * The result of the conversion: the text to write and whether it is WebVTT.
     */
    private data class Converted(val text: String, val isWebVtt: Boolean)

    /**
     * Converts the subtitle into something the player can parse.
     *
     * Mirrors the original's `a1.e(...)`: the first five non-blank lines are
     * checked for the `{h}{m}text` format; a subtitle in that format is rewritten
     * as WebVTT, a WebVTT subtitle without a header gets one, and everything else
     * is written as it is.
     */
    private fun convert(content: String, isVtt: Boolean): Converted {
        val looksLikeAss = content.lineSequence()
            .filter { it.isNotBlank() }
            .take(5)
            .any { ASS_TIMING.matches(it.trim()) }

        if (looksLikeAss) {
            val builder = StringBuilder()
            var cue = 1
            for (raw in content.lines()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val match = ASS_TIMING.find(line) ?: continue
                val start = match.groupValues[1].toLongOrNull() ?: continue
                val end = match.groupValues[2].toLongOrNull() ?: continue
                val text = match.groupValues[3].trim().replace("|", "\n")
                builder.append(cue++).append('\n')
                builder.append(webVttTimestamp(start)).append(" --> ").append(webVttTimestamp(end))
                builder.append('\n')
                builder.append(text).append("\n\n")
            }
            return Converted(builder.toString(), isWebVtt = true)
        }

        if (isVtt) {
            val stripped = content.replace("﻿", "")
            return if (stripped.trimStart().startsWith("WEBVTT", ignoreCase = true)) {
                Converted(stripped, isWebVtt = true)
            } else {
                Converted("WEBVTT\n\n$stripped", isWebVtt = true)
            }
        }

        return Converted(content, isWebVtt = false)
    }

    /**
     * The mime type of a written subtitle.
     *
     * The original's `a1.e(...)` uses `text/vtt` for a WebVTT file, `text/x-ssa`
     * for an SSA file and `application/x-subrip` for everything else.
     */
    private fun mimeTypeFor(extension: String): String = when (extension) {
        "vtt" -> "text/vtt"
        "ass" -> "text/x-ssa"
        else -> "application/x-subrip"
    }

    /**
     * Decodes the bytes of a subtitle.
     *
     * Mirrors the original's `m0.c(String, byte[])`: the bytes are decoded as UTF-8
     * and, when that fails, with the code page of the language.
     */
    private fun decode(bytes: ByteArray, lang: String): String {
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            String(bytes, charsetFor(lang))
        }
    }

    /**
     * The code page of a language.
     *
     * Mirrors the original's `m0.c(String, byte[])`.
     */
    private fun charsetFor(lang: String): Charset = when (lang.take(2).lowercase()) {
        "ar" -> Charset.forName("windows-1256")
        "be", "bg", "mk", "ru", "sr", "uk" -> Charset.forName("windows-1251")
        "cs", "hr", "hu", "pl", "ro", "sk", "sl", "sq" -> Charset.forName("windows-1250")
        "el" -> Charset.forName("windows-1253")
        "he" -> Charset.forName("windows-1255")
        "th" -> Charset.forName("windows-874")
        "tr" -> Charset.forName("windows-1254")
        "vi" -> Charset.forName("windows-1258")
        else -> Charset.forName("windows-1252")
    }

    /**
     * Formats a timestamp the way the original's `m0.d(long)` does.
     *
     * The original divides the milliseconds by 23.976 first, because the
     * `{h}{m}text` format counts 24ths of a second.
     */
    private fun webVttTimestamp(ms: Long): String {
        val value = ((ms / 23.976) * 1000).toLong()
        return String.format(
            "%02d:%02d:%02d,%03d",
            value / 3_600_000,
            (value % 3_600_000) / 60_000,
            (value % 60_000) / 1000,
            value % 1000,
        )
    }

    /**
     * Parses a `hh:mm:ss,mmm` (SubRip) or `hh:mm:ss.mmm` (WebVTT) timestamp.
     */
    private fun parseTimestamp(raw: String): Long? {
        val parts = raw.replace(",", ".").split(":")
        if (parts.size !in 2..3) return null
        return try {
            val hours = if (parts.size == 3) parts[0].trim().toLong() else 0L
            val minutes = parts[parts.size - 2].trim().toLong()
            val seconds = parts[parts.size - 1].trim().toDouble()
            (hours * 3_600_000) + (minutes * 60_000) + (seconds * 1000).toLong()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val TAG = "CinemaHQ-Player"

        /** The `{h}{m}text` format the original converts to WebVTT. */
        private val ASS_TIMING = Regex("""\{(\d+)\}\{(\d+)\}(.*)""")
    }
}
