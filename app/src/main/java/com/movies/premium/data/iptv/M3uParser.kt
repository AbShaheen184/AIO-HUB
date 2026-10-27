package com.movies.premium.data.iptv

import com.movies.premium.data.remote.model.IptvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * Minimal M3U / `#EXTINF` parser.
 *
 * Mirrors the original's `com.app.mlounge.data.iptv.v` (the `object` holding
 * `ATTR_REGEX`) with its suspend body in `com.app.mlounge.data.iptv.u`.
 *
 * The original performs no de-duplication, no sorting and no limit — channels
 * are handed to the callback in playlist order.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("([a-zA-Z0-9_-]+)=\"([^\"]+)\"")

    /**
     * Parses [inputStream] and invokes [onChannelFound] once per channel.
     *
     * Attribute names are lower-cased with [Locale.ROOT] before lookup, the
     * title is the text after the *last* comma of the `#EXTINF:` line, and the
     * URL is the next non-blank line that does not start with `#`.
     */
    suspend fun parse(
        inputStream: InputStream,
        providerSlug: String,
        onChannelFound: suspend (IptvChannel) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = reader.readLine()
            while (line != null) {
                val t = line!!.trim()
                if (t.startsWith("#EXTINF:")) {
                    val attrs = LinkedHashMap<String, String>()
                    for (m in ATTR_REGEX.findAll(t)) {
                        attrs[m.groupValues[1].lowercase(Locale.ROOT)] = m.groupValues[2]
                    }
                    val title = t.substringAfterLast(",").trim()
                    var next = reader.readLine()
                    var url: String? = null
                    while (next != null) {
                        val c = next!!.trim()
                        if (c.isNotEmpty() && !c.startsWith("#")) {
                            url = c
                            break
                        }
                        next = reader.readLine()
                    }
                    if (title.isNotEmpty() && !url.isNullOrEmpty()) {
                        onChannelFound(
                            IptvChannel(
                                name = title,
                                url = url,
                                logo = attrs["tvg-logo"],
                                group = attrs["group-title"],
                                provider = providerSlug,
                                tvgId = attrs["tvg-id"],
                                fallbackLogo = null,
                            ),
                        )
                    }
                }
                line = reader.readLine()
            }
        }
    }
}
