package com.movies.premium.data.repository

import com.google.gson.JsonObject
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The third-party accounts the original can be linked to.
 *
 * The names, descriptions and websites are the ones the original's services screen
 * shows (original: `com.app.mlounge.ui.screens.services.e` … `l`).
 */
enum class ServiceId(
    val displayName: String,
    val description: String,
    val website: String,
    /** True when the original asks for an API key rather than a sign-in token. */
    val usesApiKey: Boolean,
    /**
     * True when the original asks for a user name and a password.
     *
     * Only EasyNews works that way: the original's services screen shows a
     * "Username" and a "Password" field for it, and the "signed in" state is
     * derived from a non-empty stored user name.
     */
    val usesCredentials: Boolean = false,
) {
    SIMKL(
        "Simkl",
        "Track movies, TV shows, and anime across all devices",
        "https://simkl.com/",
        false,
    ),
    TORBOX(
        "TorBox",
        "Torrent downloading and caching service",
        "https://torbox.app/",
        true,
    ),
    REALDEBRID(
        "Real-Debrid",
        "Premium multi-host service with torrent caching",
        "https://real-debrid.com/",
        false,
    ),
    EASYNEWS(
        "EasyNews",
        "Usenet indexer with direct video links",
        "https://easynews.com/",
        false,
        usesCredentials = true,
    ),
    TRAKT(
        "Trakt",
        "Track what you watch and sync across devices",
        "https://trakt.tv/",
        false,
    ),
    ALLDEBRID(
        "AllDebrid",
        "Premium link host and torrent downloader",
        "https://alldebrid.com/",
        true,
    ),
    PREMIUMIZE(
        "Premiumize",
        "Premium multi-host service with cloud storage",
        "https://www.premiumize.me/",
        true,
    ),
    OFFCLOUD(
        "Offcloud",
        "Cloud downloads with instant cached torrents",
        "https://offcloud.com/",
        true,
    ),
}

/**
 * The account details the original displays for a linked service.
 *
 * [fields] is an ordered list of label/value pairs so the screen can render exactly
 * the rows the original shows for that service.
 */
data class ServiceAccount(
    val service: ServiceId,
    val fields: List<Pair<String, String>>,
)

/**
 * Validates account tokens against the services' own APIs.
 *
 * The original's services screen signs in with the service and then shows the
 * account's details (username, email, premium status, expiry). The same is done
 * here: the token is checked against the service's "who am I" endpoint and the
 * response is reduced to the rows the original displays.
 *
 * Two services cannot be validated without a third-party client id that the
 * original keeps in its own Firebase/remote config — Simkl and EasyNews. Their
 * tokens are stored and reported as linked without an account lookup.
 *
 * **EasyNews is not a token service.** The original signs in to it with a user
 * name and a password and never talks to easynews.com itself: the "signed in"
 * state is derived from a non-empty stored user name, and the account details
 * come from the local store. The replica does the same.
 */
@Singleton
class ServicesRepository @Inject constructor(
    private val client: OkHttpClient,
) {

    companion object {
        private const val TAG = "ServicesRepo"
    }

    /**
     * Checks [token] against [service].
     *
     * @return the account details, or a failure with a short human-readable reason.
     */
    suspend fun validate(service: ServiceId, token: String): Result<ServiceAccount> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (service) {
                    ServiceId.ALLDEBRID -> allDebrid(token)
                    ServiceId.REALDEBRID -> realDebrid(token)
                    ServiceId.TORBOX -> torBox(token)
                    ServiceId.PREMIUMIZE -> premiumize(token)
                    ServiceId.OFFCLOUD -> offcloud(token)
                    ServiceId.TRAKT -> trakt(token)
                    ServiceId.SIMKL, ServiceId.EASYNEWS -> ServiceAccount(service, emptyList())
                }
            }.onFailure { Logger.e(TAG, "validate(${service.name}) failed", it) }
        }

    private fun allDebrid(token: String): ServiceAccount {
        val json = getJson("https://api.alldebrid.com/account/info", "Authorization" to "Bearer $token")
        val premium = json.optBoolean("isPremium", false)
        return ServiceAccount(
            ServiceId.ALLDEBRID,
            listOfNotNull(
                json.optString("username").takeIf { it.isNotBlank() }?.let { "Username" to it },
                json.optString("email").takeIf { it.isNotBlank() }?.let { "Email" to it },
                "Premium" to if (premium) "Yes" else "No",
                json.optString("premiumUntil").takeIf { it.isNotBlank() }
                    ?.let { "Expires" to formatDate(it) },
            ),
        )
    }

    private fun realDebrid(token: String): ServiceAccount {
        val json = getJson("https://api.real-debrid.com/user", "Authorization" to "Bearer $token")
        val premium = json.optInt("premium", 0)
        return ServiceAccount(
            ServiceId.REALDEBRID,
            listOfNotNull(
                json.optString("username").takeIf { it.isNotBlank() }?.let { "Username" to it },
                json.optString("email").takeIf { it.isNotBlank() }?.let { "Email" to it },
                "Premium" to if (premium > 0) "Yes" else "No",
                json.optString("expiration").takeIf { it.isNotBlank() }
                    ?.let { "Expires" to formatDate(it) },
            ),
        )
    }

    private fun torBox(token: String): ServiceAccount {
        val json = getJson("https://api.torbox.app/v1/api/user/me", "Authorization" to "Bearer $token")
        val data = json.optJsonObject("data") ?: json
        return ServiceAccount(
            ServiceId.TORBOX,
            listOfNotNull(
                data.optString("email").takeIf { it.isNotBlank() }?.let { "Email" to it },
                "Premium" to if (data.optInt("plan", 0) > 0) "Yes" else "No",
                data.optString("expires_at").takeIf { it.isNotBlank() }
                    ?.let { "Expires" to formatDate(it) },
            ),
        )
    }

    private fun premiumize(token: String): ServiceAccount {
        val json = getJson(
            "https://www.premiumize.me/api/account/info?apikey=$token",
        )
        val limit = json.optInt("limit_used", 0)
        return ServiceAccount(
            ServiceId.PREMIUMIZE,
            listOfNotNull(
                json.optString("status").takeIf { it.isNotBlank() }?.let { "Status" to it },
                json.optString("customer_id").takeIf { it.isNotBlank() }?.let { "Customer ID" to it },
                "Premium" to if (json.optBoolean("premium_until", false)) "Yes" else "No",
                json.optString("premium_until").takeIf { it.isNotBlank() }
                    ?.let { "Expires" to formatDate(it) },
                "Fair Use" to "Yes",
                "Free" to if (limit > 0) "No" else "Yes",
            ),
        )
    }

    private fun offcloud(token: String): ServiceAccount {
        val json = getJson(
            "https://offcloud.com/api/account/stats",
            "Authorization" to "Bearer $token",
        )
        return ServiceAccount(
            ServiceId.OFFCLOUD,
            listOfNotNull(
                json.optString("status").takeIf { it.isNotBlank() }?.let { "Status" to it },
                json.optDouble("available", 0.0).let { "Available" to "${it.toInt()} GB" },
                json.optInt("downloads", 0).let { "Downloads" to it.toString() },
                "Premium" to if (json.optLong("premiumUntil", 0L) > 0) "Yes" else "No",
                json.optLong("premiumUntil", 0L)
                    .takeIf { it > 0 }
                    ?.let { "Expires" to formatEpoch(it) },
            ),
        )
    }

    private fun trakt(token: String): ServiceAccount {
        val json = getJson(
            "https://api.trakt.tv/users/me",
            "Authorization" to "Bearer $token",
            "trakt-api-version" to "2",
        )
        val vip = json.optBoolean("vip", false)
        return ServiceAccount(
            ServiceId.TRAKT,
            listOfNotNull(
                json.optString("username").takeIf { it.isNotBlank() }?.let { "Username" to it },
                json.optString("joined_at").takeIf { it.isNotBlank() }?.let { "Joined" to formatDate(it) },
                json.optString("name").takeIf { it.isNotBlank() }?.let { "Name" to it },
                "VIP EP" to if (vip) "Yes" else "No",
            ),
        )
    }

    /** Performs a GET and parses the body as a JSON object. */
    private fun getJson(url: String, vararg headers: Pair<String, String>): JsonObject {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val element = com.google.gson.JsonParser.parseString(body)
            return element.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw IllegalStateException("Unexpected response")
        }
    }

    /** Formats an epoch or ISO-8601 timestamp as `yyyy-MM-dd`, the original's format. */
    private fun formatDate(raw: String): String {
        val epoch = raw.toLongOrNull()
        if (epoch != null) {
            return formatEpoch(epoch)
        }
        return raw.substringBefore('T').takeIf { it.length >= 10 } ?: raw
    }

    /** Formats a unix timestamp (seconds) as `yyyy-MM-dd` in UTC. */
    private fun formatEpoch(epoch: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(java.util.Date(epoch * 1000))
    }
}

/**
 * Small helpers that give a Gson [JsonObject] the `opt*` accessors the original's
 * `org.json` parsing had.
 *
 * Gson's own API is null-hostile (`get` throws, `getAsString` on a missing member
 * throws), so every read below goes through these.
 */
private fun JsonObject.optString(name: String, fallback: String = ""): String {
    val element = get(name) ?: return fallback
    if (element.isJsonNull) return fallback
    return if (element.isJsonPrimitive) element.asString else fallback
}

private fun JsonObject.optBoolean(name: String, fallback: Boolean = false): Boolean {
    val element = get(name) ?: return fallback
    if (element.isJsonNull) return fallback
    return if (element.isJsonPrimitive) element.asBoolean else fallback
}

private fun JsonObject.optInt(name: String, fallback: Int = 0): Int {
    val element = get(name) ?: return fallback
    if (element.isJsonNull) return fallback
    return if (element.isJsonPrimitive) element.asInt else fallback
}

private fun JsonObject.optLong(name: String, fallback: Long = 0L): Long {
    val element = get(name) ?: return fallback
    if (element.isJsonNull) return fallback
    return if (element.isJsonPrimitive) element.asLong else fallback
}

private fun JsonObject.optDouble(name: String, fallback: Double = 0.0): Double {
    val element = get(name) ?: return fallback
    if (element.isJsonNull) return fallback
    return if (element.isJsonPrimitive) element.asDouble else fallback
}

private fun JsonObject.optJsonObject(name: String): JsonObject? {
    val element = get(name) ?: return null
    return if (element is JsonObject) element else null
}
