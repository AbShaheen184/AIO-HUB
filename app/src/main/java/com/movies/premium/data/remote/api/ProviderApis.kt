package com.movies.premium.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The SIMKL and debrid services are **not** declared here any more — the real, typed
 * layers live in `com.movies.premium.data.remote.simkl` and
 * `com.movies.premium.data.remote.debrid` (one interface per service, each with the
 * original's own base URL and endpoints) and each brings its own Hilt module.
 */

/** AniList GraphQL API. */
interface AniListApi {
    @POST("graphql")
    suspend fun query(
        @Body body: AniListRequest,
    ): retrofit2.Response<okhttp3.ResponseBody>
}

data class AniListRequest(
    @SerializedName("query") val query: String,
    @SerializedName("variables") val variables: Map<String, Any?>? = null,
)
