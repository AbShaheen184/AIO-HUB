package com.movies.premium.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Wire models for the Eporner API, as used by the original's adult section
 * (original: `com.app.mlounge.data.remote.model.Eporner*`).
 */

data class EpornerThumb(
    @SerializedName("src") val src: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
)

data class EpornerVideo(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("default_thumb") val defaultThumb: EpornerThumb? = null,
    @SerializedName("length_min") val lengthMin: String? = null,
)

data class EpornerVideoDetail(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("default_thumb") val defaultThumb: EpornerThumb? = null,
    @SerializedName("length_min") val lengthMin: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("keywords") val keywords: String? = null,
    @SerializedName("rate") val rate: String? = null,
    @SerializedName("views") val views: Long? = null,
)

data class EpornerSearchDetails(
    @SerializedName("totalCount") val totalCount: Int? = null,
    @SerializedName("totalPages") val totalPages: Int? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("perPage") val perPage: Int? = null,
    @SerializedName("videos") val videos: List<EpornerVideo>? = null,
)

/** One playable source of an Eporner video (the original's `EpornerSourceDetail`). */
data class EpornerSourceDetail(
    @SerializedName("labelShort") val labelShort: String? = null,
    @SerializedName("src") val src: String? = null,
)
