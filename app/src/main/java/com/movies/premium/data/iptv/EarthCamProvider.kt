package com.movies.premium.data.iptv

import com.google.gson.Gson
import com.movies.premium.data.remote.model.IptvChannel
import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * EarthCam live-webcam provider.
 *
 * Mirrors the original's `com.app.mlounge.data.iptv.EarthCamProvider`: it
 * scrapes the public `network_search.php` endpoint for the 78 country/state
 * combinations and can resolve a camera's page into a playable HLS URL.
 */
class EarthCamProvider(private val client: OkHttpClient) {

    private val gson = Gson()
    private val domainRegex = Regex("\"html5_streamingdomain\"\\s*:\\s*\"([^\"]+)\"")
    private val pathRegex = Regex("\"html5_streampath\"\\s*:\\s*\"([^\"]+)\"")

    /**
     * Fetches one `network_search.php` source and maps its cameras to channels.
     *
     * Synchronous, like the original.
     */
    fun fetchSource(url: String): List<IptvChannel> {
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("Referer", "https://www.earthcam.com/")
                .build(),
        ).execute()
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        val parsed = gson.fromJson(body, EarthCamSourceResponse::class.java)
        if (parsed.status != "200") return emptyList()
        val items = parsed.data?.camItems ?: return emptyList()
        return items.mapNotNull { item ->
            val title = item.title?.trim() ?: return@mapNotNull null
            val camUrl = item.url?.trim() ?: return@mapNotNull null
            val sb = StringBuilder()
            item.city?.let { sb.append(it) }
            val region = item.stateFull ?: item.state ?: item.country
            if (region != null && region != "undefined") {
                if (sb.isNotEmpty()) sb.append(", ")
                sb.append(region)
            }
            var group = sb.toString()
            if (group.isEmpty()) group = "Unknown"
            val logo = item.thumbnailLarge ?: item.thumbnail
            IptvChannel(
                name = title,
                url = camUrl,
                logo = logo,
                group = group,
                provider = "earthcam",
                tvgId = item.id,
                fallbackLogo = null,
            )
        }
    }

    /**
     * Fetches every camera from the CHQ EarthCam API.
     *
     * Preferred over [fetchAllCams]; the latter is kept for parity with the
     * original and is deprecated.
     */
    suspend fun fetchFromApi(): List<IptvChannel> = withContext(Dispatchers.IO) {
        Logger.d(TAG, "Fetching from API: $EARTHCAM_API_URL")
        try {
            val response = client.newCall(
                Request.Builder()
                    .url(EARTHCAM_API_URL)
                    .header("User-Agent", UA)
                    .header("Accept", "application/json")
                    .build(),
            ).execute()
            if (!response.isSuccessful) {
                Logger.w(TAG, "API HTTP ${response.code}")
                return@withContext emptyList()
            }
            val body = response.body?.string() ?: return@withContext emptyList()
            val parsed = gson.fromJson(body, EarthCamApiResponse::class.java)
            val data = parsed.data ?: emptyList()
            if (parsed.status != 200 || data.isEmpty()) {
                Logger.w(TAG, "API returned status ${parsed.status}, ${data.size} cams")
                return@withContext emptyList()
            }
            Logger.d(TAG, "Fetched ${data.size} cams from API")
            data
        } catch (e: Exception) {
            Logger.e(TAG, "Error fetching from API: ${e.message}")
            emptyList()
        }
    }

    /**
     * Scrapes all [SOURCES] and de-duplicates by `tvgId` (falling back to the
     * channel name).
     */
    @Deprecated(
        message = "Use fetchFromApi() instead",
        replaceWith = ReplaceWith("fetchFromApi()"),
    )
    suspend fun fetchAllCams(): List<IptvChannel> = supervisorScope {
        Logger.d(TAG, "Fetching from ${SOURCES.size} sources...")
        val allCams = ArrayList<IptvChannel>()
        val seen = LinkedHashSet<String>()
        for (i in SOURCES.indices) {
            try {
                val cams = withContext(Dispatchers.IO) { fetchSource(SOURCES[i]) }
                for (channel in cams) {
                    val key = channel.tvgId ?: channel.name ?: ""
                    if (seen.add(key)) allCams.add(channel)
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Source $i failed: ${e.message}")
            }
            if (i < SOURCES.size - 1) delay(400L)
        }
        Logger.d(TAG, "Total cams fetched: ${allCams.size}")
        allCams
    }

    /**
     * Resolves an EarthCam camera page into the underlying HLS stream plus the
     * headers the player must send.
     */
    suspend fun resolveStream(pageUrl: String): EarthCamStreamResult? =
        withContext(Dispatchers.IO) {
            Logger.d(TAG, "Resolving stream: $pageUrl")
            try {
                val response = client.newCall(
                    Request.Builder()
                        .url(pageUrl)
                        .header("User-Agent", UA)
                        .header("Accept", "text/html")
                        .header("Referer", "https://www.earthcam.com/")
                        .build(),
                ).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: return@withContext null
                    val d = domainRegex.find(html)
                    val p = pathRegex.find(html)
                    if (d != null && p != null) {
                        val url = d.groupValues[1].replace("\\/", "/") +
                            p.groupValues[1].replace("\\/", "/")
                        Logger.d(TAG, "Resolved: $url")
                        return@withContext EarthCamStreamResult(
                            url,
                            mapOf(
                                "Origin" to "https://www.earthcam.com",
                                "Referer" to "https://www.earthcam.com/",
                            ),
                        )
                    }
                    Logger.w(TAG, "Could not find stream fields in page")
                } else {
                    Logger.w(TAG, "Resolve HTTP ${response.code}")
                }
                null
            } catch (e: Exception) {
                Logger.e(TAG, "Error resolving stream: ${e.message}")
                null
            }
        }

    companion object {
        const val EARTHCAM_API_URL = "https://chq-api.ddns.me/api/earthcam"
        const val TAG = "EarthCam"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /**
         * The 78 country/state queries the original scrapes.
         *
         * Order is the original's runtime order: the 44 US states first
         * (AK -> WY), then the 34 country entries.
         */
        val SOURCES: List<String> = listOf(
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=AK",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=AZ",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=CA",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=CO",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=CT",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=DC",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=FL",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=GA",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=HI",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=ID",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=IL",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=IN",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=IA",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=KY",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=LA",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=ME",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=MD",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=MA",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=MI",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=MN",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=MS",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=MO",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=MT",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=NE",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=NV",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=NH",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=NJ",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=NM",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=NY",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=NC",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=ND",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=OH",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=OK",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=OR",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=PA",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=SC",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=TN",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=TX",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=UT",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=VT",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=VA",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=WA",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=WI",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=US&state=WY",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Anguilla&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Aruba&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Bahamas&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Bermuda&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Botswana&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Canada&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Cayman%20Islands&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Costa%20Rica&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=England&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Georgia&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Germany&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Honduras&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Hong%20Kong&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Hungary&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Indonesia&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Ireland&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Israel&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Italy&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Jamaica&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Netherlands&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=New%20Zealand&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Northern%20Mariana%20Islands&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Peru&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Puerto%20Rico&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Russian%20Federation&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Sint%20Maarten&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=South%20Africa&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=South%20Korea&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Spain&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Taiwan&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Tanzania&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Virgin%20Islands%2C%20British&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Virgin%20Islands%2C%20U.S.&state=undefined",
        "https://www.earthcam.com/api/dotcom/network_search.php?r=ecn&a=fetch&country=Wales&state=undefined",
        )
    }
}
