package com.movies.premium.data.iptv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * XMLTV (`.xml`) EPG parser.
 *
 * Mirrors the original's `com.app.mlounge.data.iptv.s` (a Kotlin `object`,
 * with the two `SimpleDateFormat` helpers in `q`/`r`).
 *
 * Only programmes that overlap the window `[now - 2h, now + 12h]` are kept —
 * the original's `0x6ddd00` / `0x2932e00` offsets.
 */
object XmlTvParser {

    private const val WINDOW_PAST_MS = 7_200_000L
    private const val WINDOW_FUTURE_MS = 43_200_000L

    private val sdfWithTz: ThreadLocal<SimpleDateFormat> =
        ThreadLocal.withInitial { SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US) }

    private val sdfNoTz: ThreadLocal<SimpleDateFormat> =
        ThreadLocal.withInitial { SimpleDateFormat("yyyyMMddHHmmss", Locale.US) }

    /**
     * Parses an XMLTV stream into `channelId -> programmes`.
     *
     * Each programme list is sorted ascending by `startTimeMs`. A malformed
     * document aborts the parse but the programmes collected so far are
     * still returned (the original swallows the exception).
     */
    fun parse(inputStream: InputStream): LinkedHashMap<String, List<EpgProgram>> {
        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_PAST_MS
        val windowEnd = now + WINDOW_FUTURE_MS
        val map = LinkedHashMap<String, MutableList<EpgProgram>>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", false)
            parser.setInput(inputStream, null)
            var event = parser.eventType
            var channel: String? = null
            var start: Long? = null
            var stop: Long? = null
            var title: String? = null
            var desc: String? = null
            var category: String? = null
            var inTitle = false
            var inDesc = false
            var inCategory = false
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.END_TAG) {
                    when (parser.name?.lowercase(Locale.ROOT)) {
                        "programme" -> {
                            val ch = channel
                            val s = start
                            val e = stop
                            val ti = title
                            if (ch != null && s != null && e != null &&
                                ti != null && ti.isNotBlank() &&
                                e > windowStart && s < windowEnd
                            ) {
                                map.getOrPut(ch) { ArrayList() }.add(
                                    EpgProgram(
                                        channelId = ch,
                                        title = ti,
                                        description = desc ?: "",
                                        startTimeMs = s,
                                        endTimeMs = e,
                                        category = category ?: "",
                                    ),
                                )
                            }
                            channel = null
                            start = null
                            stop = null
                            title = null
                            desc = null
                            category = null
                            inTitle = false
                            inDesc = false
                            inCategory = false
                        }

                        "title" -> inTitle = false
                        "desc" -> inDesc = false
                        "category" -> inCategory = false
                    }
                } else if (event == XmlPullParser.START_TAG) {
                    when (parser.name?.lowercase(Locale.ROOT)) {
                        "programme" -> {
                            channel = parser.getAttributeValue(null, "channel")
                            start = parseTimestamp(parser.getAttributeValue(null, "start"))
                            stop = parseTimestamp(parser.getAttributeValue(null, "stop"))
                            title = null
                            desc = null
                            category = null
                        }

                        "title" -> if (channel != null) inTitle = true
                        "desc" -> if (channel != null) inDesc = true
                        "category" -> if (channel != null) inCategory = true
                    }
                } else if (event == XmlPullParser.TEXT) {
                    val txt = parser.text?.trim() ?: ""
                    if (inTitle && title == null) title = txt
                    if (inDesc && desc == null) desc = txt
                    if (inCategory && category == null) category = txt
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Swallowed on purpose: the original returns whatever was collected.
        }
        val out = LinkedHashMap<String, List<EpgProgram>>(map.size)
        for ((key, value) in map) {
            out[key] = value.sortedBy { it.startTimeMs }
        }
        return out
    }

    /**
     * Parses an XMLTV timestamp such as `20240101120000 +0100`.
     *
     * Falls back to the first 14 characters (no timezone) when the tz-aware
     * format fails. Returns `null` when the value cannot be understood.
     */
    fun parseTimestamp(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        try {
            sdfWithTz.get()?.parse(s)?.time?.let { return it }
        } catch (_: Exception) {
            try {
                sdfNoTz.get()?.parse(s.take(14))?.time?.let { return it }
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }
}
