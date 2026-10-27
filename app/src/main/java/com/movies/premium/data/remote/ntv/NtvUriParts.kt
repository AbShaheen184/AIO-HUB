package com.movies.premium.data.remote.ntv

import java.net.URLDecoder

/**
 * The parts of an `ntv://` stream URI.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.g` (R8 name `g`).
 *
 * The URI format is `ntv://<channelName>|<channelCode>|<server>|<channelId>|<rawUrl>`
 * where the last two parts are optional.
 */
data class NtvUriParts(
    val channelName: String,
    val channelCode: String,
    val server: String,
    val channelId: String,
    val rawUrl: String,
) {
    companion object {
        const val SCHEME = "ntv://"

        /**
         * Parses an `ntv://` URI.
         *
         * Returns `null` when the URI does not use the scheme or carries fewer
         * than three parts. The `rawUrl` part is URL-decoded.
         */
        fun parse(raw: String): NtvUriParts? {
            if (!raw.startsWith(SCHEME)) return null
            val parts = raw.removePrefix(SCHEME).split("|", limit = 6)
            if (parts.size < 3) return null
            val channelName = parts[0]
            val channelCode = parts[1]
            val server = if (parts[2].isBlank()) "cdnlive" else parts[2]
            val channelId = parts.getOrNull(3) ?: ""
            val rawUrl = parts.getOrNull(4)?.let {
                runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
            } ?: ""
            return NtvUriParts(channelName, channelCode, server, channelId, rawUrl)
        }
    }
}
