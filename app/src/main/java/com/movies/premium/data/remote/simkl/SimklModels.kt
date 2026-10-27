package com.movies.premium.data.remote.simkl

import com.google.gson.annotations.SerializedName

/**
 * The wire models of the SIMKL API.
 *
 * The original keeps them in `com.app.mlounge.data.remote.simkl` as one class per file
 * (`a`, `c`, `d`, `e`, `f`, `f0`, `g0`, `h0`, `i0`, `j0`, `k0`, `l0`, `r`, `s`, `t`).
 * They are collapsed into this one file here; the class names are the original's own,
 * recovered from the `toString()` of every decompiled class:
 *
 * | original | replica            | original | replica             |
 * |----------|--------------------|----------|---------------------|
 * | `a`      | `SimklAccount`     | `i0`     | `SimklTokenResponse`|
 * | `c`      | `SimklDeviceCodeRequest` | `j0` | `SimklUser`         |
 * | `d`      | `SimklDeviceCodeResponse` | `k0` | `SimklUserSettings`|
 * | `e`      | `SimklDeviceTokenRequest` | `l0` | `SimklWatchlistItem`|
 * | `f`      | `SimklIds`         | `r`      | `SimklMovie`        |
 * | `f0`     | `SimklSyncMovie`   | `s`      | `SimklShow`         |
 * | `g0`     | `SimklSyncRequest` | `t`      | `SimklSyncEpisode`  |
 * | `h0`     | `SimklSyncShow`    |          |                     |
 *
 * The `@SerializedName` values are the API's own snake_case field names and were read
 * straight off the decompiled classes.
 */

/** The `account` block of `GET users/settings`. */
data class SimklAccount(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("joined_at") val joinedAt: String? = null,
)

/**
 * The body of `POST oauth2/device`.
 *
 * The original hard-codes its own client id here; the replica takes it from the user
 * (see [SimklClientIdProvider]) and the manager fills it in.
 */
data class SimklDeviceCodeRequest(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("scope") val scope: String = DEFAULT_SCOPE,
) {
    companion object {
        /** The scope the original asks for. */
        const val DEFAULT_SCOPE = "media:read media:write"
    }
}

/**
 * The answer of `POST oauth2/device`.
 *
 * [verificationUrl] is the address the user has to open; [userCode] is the code they
 * have to type there.
 */
data class SimklDeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String? = null,
    @SerializedName("user_code") val userCode: String? = null,
    @SerializedName("verification_url") val verificationUrl: String? = null,
    @SerializedName("user_url") val userUrl: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("interval") val interval: Int? = null,
    @SerializedName("result") val result: String? = null,
) {
    /**
     * The address to open in a browser.
     *
     * Mirrors the original's `SimklDeviceCodeResponse.b()`: the verification url wins,
     * then the user url, then the plain url, and `https://simkl.com/pin` is the last
     * resort.
     */
    fun displayUrl(): String =
        verificationUrl ?: userUrl ?: url ?: DEFAULT_VERIFICATION_URL

    companion object {
        const val DEFAULT_VERIFICATION_URL = "https://simkl.com/pin"
    }
}

/** The body of `POST oauth2/token`. */
data class SimklDeviceTokenRequest(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("grant_type") val grantType: String = GRANT_TYPE,
) {
    companion object {
        /** The OAuth grant the device flow uses. */
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
    }
}

/**
 * The ids SIMKL knows a title by.
 *
 * `tmdb` is a string on the wire in the original, so it stays a string here.
 */
data class SimklIds(
    @SerializedName("simkl") val simkl: Long? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("tmdb") val tmdb: String? = null,
    @SerializedName("imdb") val imdb: String? = null,
)

/** One movie of a `sync/…` request. */
data class SimklSyncMovie(
    @SerializedName("to") val to: String? = null,
    @SerializedName("ids") val ids: SimklIds? = null,
)

/** One show (or anime) of a `sync/…` request. */
data class SimklSyncShow(
    @SerializedName("to") val to: String? = null,
    @SerializedName("ids") val ids: SimklIds? = null,
)

/** One episode of a `sync/…` request. */
data class SimklSyncEpisode(
    @SerializedName("to") val to: String? = null,
    @SerializedName("season") val season: Int? = null,
    @SerializedName("episode") val episode: Int? = null,
    @SerializedName("ids") val ids: SimklIds? = null,
)

/**
 * The body of every `sync/…` endpoint.
 *
 * The original leaves the lists that are not used by a request out of the JSON
 * entirely, which is what the nullable lists do here.
 */
data class SimklSyncRequest(
    @SerializedName("movies") val movies: List<SimklSyncMovie>? = null,
    @SerializedName("shows") val shows: List<SimklSyncShow>? = null,
    @SerializedName("anime") val anime: List<SimklSyncShow>? = null,
    @SerializedName("episodes") val episodes: List<SimklSyncEpisode>? = null,
)

/** The answer of `POST oauth2/token`. */
data class SimklTokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("scope") val scope: String? = null,
    @SerializedName("result") val result: String? = null,
    @SerializedName("error") val error: String? = null,
)

/** The `user` block of `GET users/settings`. */
data class SimklUser(
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("is_premium") val isPremium: Boolean? = null,
)

/** The answer of `GET users/settings`. */
data class SimklUserSettings(
    @SerializedName("user") val user: SimklUser? = null,
    @SerializedName("account") val account: SimklAccount? = null,
)

/** A movie inside a `sync/all-items` entry. */
data class SimklMovie(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("ids") val ids: SimklIds? = null,
)

/** A show (or anime) inside a `sync/all-items` entry. */
data class SimklShow(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("ids") val ids: SimklIds? = null,
)

/**
 * One entry of `GET sync/all-items`.
 *
 * The endpoint answers with a map of lists (`history`, `watchlist`, `ratings`, `anime`,
 * …); the original flattens that map into a list of these and then tells the entries
 * apart by their `status` — `completed` is history, `plantowatch` and `watching` are
 * the watchlist.
 */
data class SimklWatchlistItem(
    @SerializedName("status") val status: String? = null,
    @SerializedName("last_watched_at") val lastWatchedAt: String? = null,
    @SerializedName("user_rating") val userRating: Int? = null,
    @SerializedName("movie") val movie: SimklMovie? = null,
    @SerializedName("show") val show: SimklShow? = null,
    @SerializedName("anime") val anime: SimklShow? = null,
) {
    /**
     * A copy of this entry with the anime block set.
     *
     * Mirrors the original's `SimklWatchlistItem.a(item, anime)`.
     */
    fun withAnime(anime: SimklShow?): SimklWatchlistItem = copy(anime = anime)
}
