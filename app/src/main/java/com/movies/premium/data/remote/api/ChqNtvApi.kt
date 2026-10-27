package com.movies.premium.data.remote.api

import com.google.gson.annotations.SerializedName
import com.movies.premium.data.remote.model.LogoManifestResponse
import com.movies.premium.data.remote.model.NtvChannelsResponse
import com.movies.premium.data.remote.model.SportsMatchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Live-TV channel listing served by the NTV provider.
 *
 * The original declares these models in the `api` package (not `model`), so
 * they live here to keep the Retrofit signatures identical.
 */
data class ProviderMeta(
    @SerializedName("exists") val exists: Boolean? = null,
    @SerializedName("lastUpdated") val lastUpdated: String? = null,
    @SerializedName("channels") val channels: Int? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("sizeBytes") val sizeBytes: Long? = null,
)

data class PlutoChannel(
    @SerializedName("name") val name: String? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("logo") val logo: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("number") val number: Int? = null,
)

data class PlutoChannelsResponse(
    @SerializedName("region") val region: String? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("channels") val channels: List<PlutoChannel>? = null,
)

data class PlutoCategory(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
)

data class PlutoCategoriesResponse(
    @SerializedName("categories") val categories: List<String>? = null,
)

data class VavooChannel(
    @SerializedName("name") val name: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("group") val group: String? = null,
    @SerializedName("poster") val poster: String? = null,
)

data class VavooChannelsResponse(
    @SerializedName("total") val total: Int? = null,
    @SerializedName("channels") val channels: List<VavooChannel>? = null,
)

data class VavooResolveResponse(
    @SerializedName("url") val url: String? = null,
    @SerializedName("headers") val headers: Map<String, String>? = null,
)

/** Live-TV channel listing served by the NTV provider. */
interface ChqNtvApi {
    @GET("api/ntv/channels")
    suspend fun getChannels(
        @Query("server") server: String? = null,
    ): NtvChannelsResponse

    @GET("logos/_manifest.json")
    suspend fun getLogoManifest(): LogoManifestResponse
}

/** Secondary live-TV source (ntv.cx) with direct match lookup. */
interface NtvApi {
    @GET("api/get-channels")
    suspend fun getChannels(): NtvChannelsResponse

    @GET("api/get-matches")
    suspend fun getMatches(
        @Query("server") server: String,
        @Query("type") type: String,
    ): SportsMatchResponse
}
