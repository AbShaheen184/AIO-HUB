package com.movies.premium.data.remote.ntv

/**
 * A resolved live-stream URL together with the HTTP headers the player has to
 * send.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.x` (R8 name `x`).
 */
data class NtvStreamResult(
    val url: String,
    val headers: Map<String, String>,
)
