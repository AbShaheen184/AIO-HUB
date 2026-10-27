package com.movies.premium.data.iptv

import com.google.gson.annotations.SerializedName

/**
 * A single EPG entry.
 *
 * Mirrors the original's `com.app.mlounge.data.iptv.t` (R8 name `t`) — a
 * Kotlin `data class` with six Gson-annotated fields and a
 * `progressFraction()` helper used by the player's now-playing bar.
 */
data class EpgProgram(
    @SerializedName("channelId") val channelId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("startTimeMs") val startTimeMs: Long,
    @SerializedName("endTimeMs") val endTimeMs: Long,
    @SerializedName("category") val category: String,
) {
    /**
     * How far the programme has progressed, in the range `0f..1f`.
     *
     * Returns `0f` before the programme starts, `1f` once it has ended.
     */
    fun progressFraction(): Float {
        val now = System.currentTimeMillis()
        if (now < startTimeMs) return 0f
        if (now >= endTimeMs) return 1f
        val total = (endTimeMs - startTimeMs).toFloat()
        if (total <= 0f) return 1f
        return ((now - startTimeMs).toFloat() / total).coerceIn(0f, 1f)
    }

    /** `true` when the programme is live right now. */
    fun isLive(): Boolean {
        val now = System.currentTimeMillis()
        return now >= startTimeMs && now < endTimeMs
    }
}
