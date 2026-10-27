package com.movies.premium.data.remote.debrid

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * The wire models of the five debrid services.
 *
 * Every field name and `@SerializedName` value is the original's — the models come from
 * the original's `com.app.mlounge.data.remote.debrid` package, where they are one Java
 * class per response.
 */

// --- AllDebrid --------------------------------------------------------------

/** The `data.user` object of `v4/user`. */
data class AllDebridUserInfo(
    @SerializedName("email") val email: String? = null,
    @SerializedName("isPremium") val isPremium: Boolean = false,
    @SerializedName("isSubscribed") val isSubscribed: Boolean = false,
    @SerializedName("isTrial") val isTrial: Boolean = false,
    @SerializedName("premiumUntil") val premiumUntil: Long = 0L,
    @SerializedName("username") val username: String? = null,
)

/** The `data` object of `v4.1/pin/get`. */
data class AllDebridPinData(
    @SerializedName("check") val check: String? = null,
    @SerializedName("expires_in") val expiresIn: Int = 600,
    @SerializedName("pin") val pin: String? = null,
    @SerializedName("user_url") val userUrl: String? = null,
)

// --- Real-Debrid ------------------------------------------------------------

/** The `rest/1.0/user` response. */
data class RealDebridUserInfo(
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("expiration") val expiration: String? = null,
    @SerializedName("id") val id: Int = 0,
    @SerializedName("locale") val locale: String? = null,
    @SerializedName("points") val points: Int = 0,
    @SerializedName("premium") val premium: Int = 0,
    @SerializedName("type") val type: String? = null,
    @SerializedName("username") val username: String? = null,
)

/** The `oauth/v2/token` response. */
data class RealDebridTokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("error") val error: String? = null,
)

/** The `oauth/v2/device/code` response. */
data class RealDebridDeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String? = null,
    @SerializedName("expires_in") val expiresIn: Int = 0,
    @SerializedName("interval") val interval: Int = 5,
    @SerializedName("user_code") val userCode: String? = null,
    @SerializedName("verification_url") val verificationUrl: String? = null,
)

/** The `oauth/v2/device/credentials` response. */
data class RealDebridCredentialsResponse(
    @SerializedName("client_id") val clientId: String? = null,
    @SerializedName("client_secret") val clientSecret: String? = null,
    @SerializedName("error") val error: String? = null,
)

// --- TorBox -----------------------------------------------------------------

/** The `v1/api/user/me` response. */
data class TorBoxUserInfo(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: TorBoxUserData? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("error") val error: String? = null,
)

data class TorBoxUserData(
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("customer") val customer: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("id") val id: Int = 0,
    @SerializedName("is_subscribed") val isSubscribed: Boolean = false,
    @SerializedName("plan") val plan: Int = 0,
    @SerializedName("premium_expires_at") val premiumExpiresAt: String? = null,
    @SerializedName("total_downloaded") val totalDownloaded: Long = 0L,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

/** The `v1/api/torrents/createtorrent` response. */
data class TorBoxCreateTorrentResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: TorBoxCreateData? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("error") val error: String? = null,
)

data class TorBoxCreateData(
    @SerializedName("hash") val hash: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("torrent_id") val torrentId: Int = 0,
)

/** The `v1/api/torrents/requestdl` response. */
data class TorBoxRequestDlResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: String? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("error") val error: String? = null,
)

/** The `v1/api/torrents/mylist` response (without an `id` filter). */
data class TorBoxTorrentListResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<TorBoxTorrentItem>? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("error") val error: String? = null,
)

/** The `v1/api/torrents/mylist` response (with an `id` filter). */
data class TorBoxTorrentInfoResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: TorBoxTorrentItem? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("error") val error: String? = null,
)

data class TorBoxTorrentItem(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("hash") val hash: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("download_state") val downloadState: String? = null,
    @SerializedName("download_finished") val downloadFinished: Boolean = false,
    @SerializedName("files") val files: List<TorBoxTorrentFile>? = null,
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("progress") val progress: Double = 0.0,
)

data class TorBoxTorrentFile(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("mimetype") val mimetype: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("s3_path") val s3Path: String? = null,
    @SerializedName("short_name") val shortName: String? = null,
    @SerializedName("size") val size: Long = 0L,
) {
    /**
     * True when the file looks like a video.
     *
     * The original's `TorBoxTorrentListResponse.TorrentFile.c()` checks the mime type
     * first and falls back to the file extension.
     */
    fun isVideo(): Boolean {
        if (mimetype?.startsWith("video/") == true) return true
        val lower = name?.lowercase() ?: return false
        return VIDEO_EXTENSIONS.any { lower.endsWith(".$it") }
    }
}

// --- Premiumize -------------------------------------------------------------

/** The `account/info` response. */
data class PremiumizeUserInfo(
    @SerializedName("customer_id") val customerId: String? = null,
    @SerializedName("limit_used") val limitUsed: Double? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("premium_until") val premiumUntil: Long? = null,
    @SerializedName("space_used") val spaceUsed: Long? = null,
    @SerializedName("status") val status: String? = null,
) {
    /** True when the premium period has not run out yet. */
    fun isPremium(): Boolean = (premiumUntil ?: 0L) > System.currentTimeMillis() / 1000L
}

/** The `transfer/directdl` response. */
data class PremiumizeDirectDlResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("content") val content: List<PremiumizeContent>? = null,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("filesize") val filesize: Long? = null,
) {
    fun isSuccess(): Boolean = status == "success"
}

data class PremiumizeContent(
    @SerializedName("link") val link: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("stream_link") val streamLink: String? = null,
    @SerializedName("transcode_status") val transcodeStatus: String? = null,
) {
    /** True when the path ends in one of the video extensions. */
    fun isVideo(): Boolean {
        val lower = path?.lowercase() ?: return false
        return VIDEO_EXTENSIONS.any { lower.endsWith(".$it") }
    }
}

/** The `transfer/create` response. */
data class PremiumizeTransferResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("status") val status: String? = null,
) {
    fun isSuccess(): Boolean = status == "success"
}

/** The `folder/list` response. */
data class PremiumizeFolderListResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("content") val content: List<PremiumizeFolderItem>? = null,
) {
    fun isSuccess(): Boolean = status == "success"
}

data class PremiumizeFolderItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("link") val link: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("stream_link") val streamLink: String? = null,
    @SerializedName("transcode_status") val transcodeStatus: String? = null,
    @SerializedName("type") val type: String? = null,
) {
    /** True when the name ends in one of the video extensions. */
    fun isVideo(): Boolean {
        val lower = name?.lowercase() ?: return false
        return VIDEO_EXTENSIONS.any { lower.endsWith(".$it") }
    }
}

/** The `transfer/list` response. */
data class PremiumizeTransferList(
    @SerializedName("status") val status: String? = null,
    @SerializedName("transfers") val transfers: List<PremiumizeTransfer>? = null,
)

data class PremiumizeTransfer(
    @SerializedName("file_id") val fileId: String? = null,
    @SerializedName("folder_id") val folderId: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("progress") val progress: Double? = null,
    @SerializedName("src") val src: String? = null,
    @SerializedName("status") val status: String? = null,
)

// --- Offcloud ---------------------------------------------------------------

/** The `api/account/info` response. */
data class OffCloudAccountInfo(
    @SerializedName("can_download") val canDownload: Boolean? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("expiration_date") val expirationDate: String? = null,
    @SerializedName("is_premium") val isPremium: Boolean? = null,
    @SerializedName("user_id") val userId: String? = null,
)

/** One entry of the `api/cache/info` response. */
data class OffCloudCacheInfoItem(
    @SerializedName("cached") val cached: Boolean? = null,
    @SerializedName("files") val files: List<OffCloudCacheFile>? = null,
)

data class OffCloudCacheFile(
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("folder") val folder: List<String>? = null,
    @SerializedName("size") val size: Long? = null,
)

/** One entry of the `api/cache/download` response. */
data class OffCloudCacheDownloadFile(
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("folder") val folder: List<String>? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("url") val url: String? = null,
)

/** The `api/cloud` response. */
data class OffCloudCloudCreateResponse(
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("originalLink") val originalLink: String? = null,
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("status") val status: String? = null,
)

/** The `api/cloud/status` response. */
data class OffCloudCloudStatusResponse(
    @SerializedName("status") val status: OffCloudCloudStatus? = null,
)

data class OffCloudCloudStatus(
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("progress") val progress: Double? = null,
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("status") val status: String? = null,
)

/** The `api/cloud/explore/{requestId}` response. */
data class OffCloudExploreDetailedResponse(
    @SerializedName("files") val files: List<OffCloudExploreFile>? = null,
)

data class OffCloudExploreFile(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("url") val url: String? = null,
)

// --- JSON helpers -----------------------------------------------------------
//
// The services' "who am I" and file-tree responses are free-form JSON, so they are
// read through Gson's `JsonObject` rather than a typed model. These helpers give it the
// null-safe accessors the original's `org.json` parsing had.

internal fun JsonObject.jsonString(name: String): String? {
    val element: JsonElement = get(name) ?: return null
    if (element.isJsonNull || !element.isJsonPrimitive) return null
    return element.asString
}

internal fun JsonObject.jsonBoolean(name: String, fallback: Boolean = false): Boolean {
    val element: JsonElement = get(name) ?: return fallback
    if (element.isJsonNull || !element.isJsonPrimitive) return fallback
    return element.asBoolean
}

internal fun JsonObject.jsonInt(name: String, fallback: Int = 0): Int {
    val element: JsonElement = get(name) ?: return fallback
    if (element.isJsonNull || !element.isJsonPrimitive) return fallback
    return element.asInt
}

internal fun JsonObject.jsonLong(name: String, fallback: Long = 0L): Long {
    val element: JsonElement = get(name) ?: return fallback
    if (element.isJsonNull || !element.isJsonPrimitive) return fallback
    return element.asLong
}

internal fun JsonObject.jsonObject(name: String): JsonObject? {
    val element: JsonElement = get(name) ?: return null
    return if (element.isJsonObject) element.asJsonObject else null
}

internal fun JsonObject.jsonArray(name: String): JsonArray? {
    val element: JsonElement = get(name) ?: return null
    return if (element.isJsonArray) element.asJsonArray else null
}
