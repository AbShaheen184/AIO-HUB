package com.movies.premium.data.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.movies.premium.data.remote.model.IptvChannel
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pluto TV live-channel provider.
 *
 * Mirrors the original's `com.app.mlounge.data.providers.s` (the class holding
 * `REGIONS`, `TAG` and the Safari UA) with its suspend fetch body in
 * `com.app.mlounge.data.providers.r`.
 *
 * The original exposes the fetch only as an inline `withContext` lambda; the
 * replica turns it into a regular suspend method so callers can use it.
 */
@Singleton
class PlutoTvProvider @Inject constructor(
    private val client: OkHttpClient,
) {
    private val gson = Gson()

    private val ua =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Safari/605.1.15"

    /**
     * Boots a Pluto session and collects the guide for every region.
     *
     * Returns an empty list when the boot request fails.
     */
    suspend fun fetchChannels(): List<IptvChannel> = withContext(Dispatchers.IO) {
        try {
            val clientId = UUID.randomUUID().toString()
            val bootUrl =
                "https://boot.pluto.tv/v4/start?appName=web&appVersion=9.9.1" +
                    "&deviceVersion=130.0.0&deviceModel=web&deviceMake=Firefox" +
                    "&deviceType=web&clientID=$clientId&clientModelNumber=1.0.0" +
                    "&serverSideAds=false&constraints=&drmCapabilities=&blockingMode=" +
                    "&clientTime=${clientTime()}"
            val bootResponse = client.newCall(
                Request.Builder()
                    .url(bootUrl)
                    .header("User-Agent", ua)
                    .build(),
            ).execute()
            if (!bootResponse.isSuccessful) return@withContext emptyList()

            val bootJson = gson.fromJson(
                bootResponse.body?.string() ?: return@withContext emptyList(),
                JsonObject::class.java,
            )
                ?: return@withContext emptyList()

            val sessionToken = bootJson.get("sessionToken")?.asString
                ?: return@withContext emptyList()
            val stitcher = bootJson.getAsJsonObject("servers")?.get("stitcher")?.asString
                ?: return@withContext emptyList()
            // The original falls back to OkHttp's fragment encode set when the
            // boot payload has no "stitcherParams" entry.
            val stitcherParams = bootJson.get("stitcherParams")?.asString ?: FRAGMENT_ENCODE_SET

            val channels = ArrayList<IptvChannel>()
            for ((region, regionIp) in REGIONS) {
                try {
                    val guideResponse = client.newCall(
                        Request.Builder()
                            .url(GUIDE_URL)
                            .header("User-Agent", ua)
                            .header("Authorization", "Bearer $sessionToken")
                            .header("X-Forwarded-For", regionIp)
                            .build(),
                    ).execute()
                    if (!guideResponse.isSuccessful) continue
                    val guideJson = gson.fromJson(
                        guideResponse.body?.string() ?: continue,
                        JsonObject::class.java,
                    )
                    val data = guideJson?.getAsJsonArray("data") ?: continue
                    for (element in data) {
                        val obj = element.asJsonObject
                        val name = obj.get("name")?.asString ?: continue
                        val path = obj.getAsJsonObject("stitched")?.get("path")?.asString ?: continue
                        val category = try {
                            obj.getAsJsonArray("categoryIDs")?.get(0)?.asString ?: "Uncategorized"
                        } catch (e: Exception) {
                            Logger.w(TAG, "Region $region failed: ${e.message}")
                            "Uncategorized"
                        }
                        val logo = obj.getAsJsonArray("images")?.get(0)
                            ?.asJsonObject?.get("url")?.asString
                        val id = obj.get("id")?.asString
                        val streamUrl =
                            "$stitcher/v2$path?$stitcherParams&jwt=$sessionToken" +
                                "&masterJWTPassthrough=true"
                        val group = region.uppercase(Locale.ROOT) + " - " + category
                        channels.add(
                            IptvChannel(
                                name = name,
                                url = streamUrl,
                                logo = logo,
                                group = group,
                                provider = "plutotv",
                                tvgId = id,
                                fallbackLogo = null,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Region $region failed: ${e.message}")
                }
            }
            Logger.d(TAG, "PlutoTV: ${channels.size} channels")
            channels
        } catch (e: Exception) {
            Logger.e(TAG, "Error: ${e.message}")
            emptyList()
        }
    }

    private fun clientTime(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    companion object {
        const val TAG = "PlutoTV"

        /** OkHttp's fragment encode set — the original's fallback stitcher params. */
        private const val FRAGMENT_ENCODE_SET = " \"'<>&"

        private const val GUIDE_URL =
            "https://service-channels.clusters.pluto.tv/v2/guide/channels" +
                "?channelIds=&offset=0&limit=1000&sort=number%3Aasc"

        /** The regions the original scrapes, mapped to their edge IP. */
        val REGIONS: Map<String, String> = mapOf(
            "us" to "45.50.96.71",
            "gb" to "178.238.11.6",
            "ca" to "99.224.0.1",
            "de" to "85.214.132.117",
            "fr" to "212.27.48.10",
            "it" to "5.170.0.1",
            "es" to "88.0.0.1",
        )
    }
}
