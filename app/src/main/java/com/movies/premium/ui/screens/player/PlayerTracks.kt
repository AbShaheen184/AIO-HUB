@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.movies.premium.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.Tracks
import com.movies.premium.ui.viewmodel.AudioTrackInfo
import com.movies.premium.ui.viewmodel.SubtitleItem
import java.util.Locale

/**
 * Turns the tracks of the media into the list the player shows.
 *
 * Mirrors the original's track listener, `com.app.mlounge.ui.screens.player.z0`
 * (R8 name `z0`), method `v(Tracks)`:
 *
 * - an audio track is labelled `LANG (CHANNELS)`, where `LANG` is the language of
 *   the format in upper case (`UNK` when there is none) and `CHANNELS` is `Mono`,
 *   `Stereo`, `5.1`, `7.1` or `<n> Ch`. The name of the format is kept when it
 *   already carries the language.
 * - a subtitle track is labelled with the name of the format, or `LANG Track <n>`
 *   when the format has no name. Its language is the language of the format, or
 *   the one [languageCodeFor] finds in the name.
 */
fun Tracks.audioTracks(): List<AudioTrackInfo> {
    val result = mutableListOf<AudioTrackInfo>()
    groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            val language = format.language?.uppercase(Locale.ROOT) ?: "UNK"
            // Media3 1.8 calls the name of a track its label.
            var name = format.label
            if (name.isNullOrEmpty() || !name.contains(language, ignoreCase = true)) {
                name = "$language (${channelLabel(format.channelCount)})"
            }
            result.add(
                AudioTrackInfo(
                    label = name,
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    isSelected = group.isTrackSelected(trackIndex),
                ),
            )
        }
    }
    return result
}

/**
 * The subtitle tracks that are part of the media.
 *
 * Mirrors the second half of the original's `z0.v(Tracks)`.
 */
fun Tracks.subtitleTracks(): List<SubtitleItem> {
    val result = mutableListOf<SubtitleItem>()
    groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            val language = format.language?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
                ?: languageCodeFor(format.label)
            val label = format.label?.takeIf { it.isNotBlank() }
                ?: "${language.uppercase(Locale.ROOT)} Track ${trackIndex + 1}"
            result.add(
                SubtitleItem(
                    label = label,
                    url = "",
                    lang = language,
                    isLocal = false,
                    isInternal = true,
                    isDefault = false,
                    trackGroupIndex = groupIndex,
                    trackIndex = trackIndex,
                    release = null,
                ),
            )
        }
    }
    return result
}

/**
 * The name the original gives to a number of channels.
 */
private fun channelLabel(channelCount: Int): String = when (channelCount) {
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> if (channelCount > 0) "$channelCount Ch" else "UNK"
}

/**
 * The language code of a track name.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.player.m0.f(String)`: the name
 * is looked up for the language it names, and English is the fallback.
 */
fun languageCodeFor(name: String?): String {
    val value = name?.lowercase(Locale.ROOT)?.trim().orEmpty()
    return when {
        value.contains("english") || value.contains("(en)") -> "en"
        value.contains("french") || value.contains("français") || value.contains("(fr)") -> "fr"
        value.contains("spanish") || value.contains("español") || value.contains("(es)") -> "es"
        value.contains("german") || value.contains("deutsch") || value.contains("(de)") -> "de"
        value.contains("italian") || value.contains("italiano") || value.contains("(it)") -> "it"
        value.contains("portuguese") || value.contains("português") || value.contains("(pt)") -> "pt"
        value.contains("japanese") || value.contains("日本語") || value.contains("(ja)") -> "ja"
        value.contains("chinese") || value.contains("中文") || value.contains("(zh)") -> "zh"
        value.contains("arabic") || value.contains("العربية") || value.contains("(ar)") -> "ar"
        value.contains("korean") || value.contains("한국어") || value.contains("(ko)") -> "ko"
        value.contains("dutch") || value.contains("nederlands") || value.contains("(nl)") -> "nl"
        value.contains("russian") || value.contains("русский") || value.contains("(ru)") -> "ru"
        else -> "en"
    }
}
