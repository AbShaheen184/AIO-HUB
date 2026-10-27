package com.movies.premium.data.iptv

import com.google.gson.annotations.SerializedName
import com.movies.premium.data.remote.model.IptvChannel

/**
 * Response of the CHQ EarthCam proxy endpoint.
 *
 * Mirrors the original's `com.app.mlounge.data.iptv.EarthCamApiResponse`
 * — Gson keys `status` and `data`, no `@SerializedName` needed.
 */
data class EarthCamApiResponse(
    val status: Int = 0,
    val data: List<IptvChannel>? = null,
)

/**
 * Response of an EarthCam `network_search.php` query.
 *
 * Mirrors the original's `com.app.mlounge.data.iptv.EarthCamSourceResponse`
 * — the `status` field is a **String** here, not an Int.
 */
data class EarthCamSourceResponse(
    val status: String? = null,
    val data: EarthCamSourceData? = null,
)

/** The `data` block of an EarthCam `network_search.php` response. */
data class EarthCamSourceData(
    @SerializedName("cam_count") val camCount: Int? = null,
    @SerializedName("cam_items") val camItems: List<EarthCamCamItem>? = null,
)

/**
 * A single webcam entry.
 *
 * Mirrors the original's `com.app.mlounge.data.iptv.EarthCamCamItem`; the
 * constructor order is `id, title, city, country, state, stateFull, category,
 * thumbnail, thumbnailLarge, url`.
 */
data class EarthCamCamItem(
    val id: String? = null,
    val title: String? = null,
    val city: String? = null,
    val country: String? = null,
    val state: String? = null,
    @SerializedName("state_full") val stateFull: String? = null,
    val category: String? = null,
    val thumbnail: String? = null,
    @SerializedName("thumbnail_large") val thumbnailLarge: String? = null,
    val url: String? = null,
)

/**
 * A resolved EarthCam stream.
 *
 * Mirrors the original's nested
 * `com.app.mlounge.data.iptv.EarthCamProvider.EarthCamStreamResult`.
 */
data class EarthCamStreamResult(
    val url: String,
    val headers: Map<String, String>,
)
