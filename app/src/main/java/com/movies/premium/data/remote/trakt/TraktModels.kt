package com.movies.premium.data.remote.trakt

import com.google.gson.annotations.SerializedName

/**
 * The wire models of the Trakt.tv API.
 *
 * The original keeps them in `com.app.mlounge.data.remote.trakt` as one class per file;
 * they are collapsed into this one file here. The property names come from the
 * decompiled classes' Kotlin metadata, and the `@SerializedName` values are the API's
 * own snake_case field names.
 */

/** The external ids of a movie, show or episode. */
data class TraktIds(
    @SerializedName("trakt") val trakt: Int? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("imdb") val imdb: String? = null,
    @SerializedName("tmdb") val tmdb: Int? = null,
    @SerializedName("tvdb") val tvdb: Int? = null,
)

data class TraktUserIds(
    @SerializedName("slug") val slug: String? = null,
)

data class TraktMovie(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("ids") val ids: TraktIds? = null,
)

data class TraktShow(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("ids") val ids: TraktIds? = null,
)

data class TraktEpisode(
    @SerializedName("season") val season: Int? = null,
    @SerializedName("number") val number: Int? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("ids") val ids: TraktIds? = null,
)

/** One entry of `GET sync/watchlist/{type}`. */
data class TraktWatchlistItem(
    @SerializedName("rank") val rank: Int? = null,
    @SerializedName("listed_at") val listedAt: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("movie") val movie: TraktMovie? = null,
    @SerializedName("show") val show: TraktShow? = null,
)

/** One entry of `GET sync/history/{type}`. */
data class TraktHistoryItem(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("watched_at") val watchedAt: String? = null,
    @SerializedName("action") val action: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("movie") val movie: TraktMovie? = null,
    @SerializedName("show") val show: TraktShow? = null,
    @SerializedName("episode") val episode: TraktEpisode? = null,
)

data class TraktUser(
    @SerializedName("username") val username: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("private") val isPrivate: Boolean? = null,
    @SerializedName("vip") val vip: Boolean? = null,
    @SerializedName("vip_ep") val vipEp: Boolean? = null,
    @SerializedName("ids") val ids: TraktUserIds? = null,
    @SerializedName("joined_at") val joinedAt: String? = null,
)

data class TraktUserSettings(
    @SerializedName("user") val user: TraktUser? = null,
)

/**
 * The per-media-type activity timestamps.
 *
 * The original's `TraktActivityTimestamps` has a `watchedAt`, `collectedAt`, `ratedAt`,
 * `watchlistedAt`, `commentedAt`, `pausedAt`, `hiddenAt` and `updatedAt` plus the
 * per-type sub-objects.
 */
data class TraktActivityTimestamps(
    @SerializedName("watched_at") val watchedAt: String? = null,
    @SerializedName("collected_at") val collectedAt: String? = null,
    @SerializedName("rated_at") val ratedAt: String? = null,
    @SerializedName("watchlisted_at") val watchlistedAt: String? = null,
    @SerializedName("commented_at") val commentedAt: String? = null,
    @SerializedName("paused_at") val pausedAt: String? = null,
    @SerializedName("hidden_at") val hiddenAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("movies") val movies: TraktActivityTimestamps? = null,
    @SerializedName("shows") val shows: TraktActivityTimestamps? = null,
    @SerializedName("episodes") val episodes: TraktActivityTimestamps? = null,
    @SerializedName("watchlist") val watchlist: TraktActivityTimestamps? = null,
)

/** The payload of `GET sync/last_activities`. */
data class TraktLastActivities(
    @SerializedName("all") val all: String? = null,
    @SerializedName("movies") val movies: TraktActivityTimestamps? = null,
    @SerializedName("episodes") val episodes: TraktActivityTimestamps? = null,
    @SerializedName("shows") val shows: TraktActivityTimestamps? = null,
    @SerializedName("watchlist") val watchlist: TraktActivityTimestamps? = null,
)

/** How many items of each kind a sync request added, deleted or did not find. */
data class TraktSyncCount(
    @SerializedName("movies") val movies: Int? = null,
    @SerializedName("shows") val shows: Int? = null,
    @SerializedName("episodes") val episodes: Int? = null,
)

data class TraktSyncNotFound(
    @SerializedName("movies") val movies: List<TraktSyncMovie>? = null,
    @SerializedName("shows") val shows: List<TraktSyncShow>? = null,
)

/** The `movies` / `shows` / `episodes` entries of a sync request body. */
data class TraktSyncMovie(
    @SerializedName("ids") val ids: TraktIds? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("collected_at") val collectedAt: String? = null,
    @SerializedName("watched_at") val watchedAt: String? = null,
)

data class TraktSyncShow(
    @SerializedName("ids") val ids: TraktIds? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("watched_at") val watchedAt: String? = null,
)

data class TraktSyncEpisode(
    @SerializedName("ids") val ids: TraktIds? = null,
    @SerializedName("season") val season: Int? = null,
    @SerializedName("episode") val episode: Int? = null,
    @SerializedName("watched_at") val watchedAt: String? = null,
)

/** The body of `POST sync/watchlist`, `POST sync/history` and their `remove` variants. */
data class TraktSyncRequest(
    @SerializedName("movies") val movies: List<TraktSyncMovie>? = null,
    @SerializedName("shows") val shows: List<TraktSyncShow>? = null,
    @SerializedName("episodes") val episodes: List<TraktSyncEpisode>? = null,
)

data class TraktSyncResponse(
    @SerializedName("added") val added: TraktSyncCount? = null,
    @SerializedName("deleted") val deleted: TraktSyncCount? = null,
    @SerializedName("not_found") val notFound: TraktSyncNotFound? = null,
)

/** The answer of `POST oauth/token` and `POST oauth/device/token`. */
data class TraktTokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("expires_in") val expiresIn: Long? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("scope") val scope: String? = null,
    @SerializedName("created_at") val createdAt: Long? = null,
)

data class TraktDeviceCodeRequest(
    @SerializedName("client_id") val clientId: String,
)

data class TraktDeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String? = null,
    @SerializedName("user_code") val userCode: String? = null,
    @SerializedName("verification_url") val verificationUrl: String? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("interval") val interval: Int? = null,
)

data class TraktDeviceTokenRequest(
    @SerializedName("code") val code: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_secret") val clientSecret: String,
)

data class TraktRefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_secret") val clientSecret: String,
    @SerializedName("redirect_uri") val redirectUri: String = "urn:ietf:wg:oauth:2.0:oob",
    @SerializedName("grant_type") val grantType: String = "refresh_token",
)

data class TraktRevokeRequest(
    @SerializedName("token") val token: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_secret") val clientSecret: String,
)

/** The account block the original shows in the settings screen. */
data class TraktAccount(
    @SerializedName("timezone") val timezone: String? = null,
    @SerializedName("cover_image") val coverImage: String? = null,
)
