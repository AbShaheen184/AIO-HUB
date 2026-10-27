package com.movies.premium.data.remote.ntv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import com.movies.premium.util.Logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a live-TV channel into a playable stream URL.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.w` (R8 name `w`).
 *
 * Three strategies are used, in the original's order:
 * 1. `hesgoales` / `livelive24.com` embeds — a WebView polls the DOM for the
 *    `#source` element.
 * 2. `dlhd` — the `dlive.sx` stream page is scraped and its `_econfig`
 *    payload decrypted.
 * 3. everything else — the `ntv.cx` channel page is scraped for the embed URL
 *    and a WebView intercepts the resulting `.m3u8` request.
 */
@Singleton
class NtvStreamExtractor @Inject constructor(
    private val client: OkHttpClient,
) {

    /**
     * Decrypts the DLHD `window._econfig` payload.
     *
     * The payload is a base64url blob whose characters are split into four
     * chunks, each with its fourth character removed; the chunks are then
     * re-ordered and base64url-decoded again.
     */
    fun decryptDlhdTiestep(html: String): String? {
        return try {
            val m = Regex("window\\._econfig\\s*=\\s*'([^']+)'").find(html)
            if (m == null) {
                Logger.w(TAG, "DLHD no window._econfig found in tiestep embed")
                return null
            }
            val decoded = base64UrlDecodeIso(m.groupValues[1]) ?: return null
            val chunkLen = (decoded.length + 3) / 4
            val order = intArrayOf(2, 0, 3, 1)
            val parts = arrayOfNulls<String>(4)
            for (i in 0 until 4) {
                val start = i * chunkLen
                if (start >= decoded.length) break
                val chunk = decoded.substring(start, minOf(start + chunkLen, decoded.length))
                if (chunk.length >= 4) {
                    val stripped = chunk.substring(0, 3) + chunk.substring(4)
                    base64UrlDecodeIso(stripped)?.let { parts[order[i]] = it }
                }
            }
            val json = base64UrlDecodeUtf8(parts.filterNotNull().joinToString("")) ?: return null
            val obj = JSONObject(json)
            var url = obj.optString("stream_url", "")
            val nop2p = obj.optString("stream_url_nop2p", "")
            if (!nop2p.isBlank()) url = nop2p
            val trimmed = url.trim()
            if (trimmed.startsWith("http") && (trimmed.contains(".m3u8") || trimmed.contains(".mp4"))) {
                Logger.d(TAG, "DLHD tiestep decrypted stream")
                return trimmed
            }
            Logger.w(TAG, "DLHD tiestep payload has no playable URL")
            null
        } catch (e: Exception) {
            Logger.e(TAG, "DLHD tiestep decrypt error: ${e.message}")
            null
        }
    }

    /** Extracts a `STREAM_URL` / `window.atob` stream URL from [html]. */
    fun extractStreamUrl(html: String): String? {
        Regex("STREAM_URL\\s*=\\s*[\"']([^\"']+)[\"']").find(html)?.let { m ->
            val v = m.groupValues[1].replace("\\/", "/").trim()
            if (v.contains(".m3u8")) return v
        }
        Regex("window\\.atob\\(['\"]([A-Za-z0-9+/=]+)['\"]\\)").find(html)?.let { m ->
            val decoded = String(android.util.Base64.decode(m.groupValues[1], 0), Charsets.UTF_8)
                .trim()
            if (decoded.contains(".m3u8")) return decoded
        }
        return null
    }

    /** Extracts the `const SRC = "..."` stream URL from a DLHD embed page. */
    fun extractPlayerSrc(html: String): String? {
        val m = Regex("const\\s+SRC\\s*=\\s*\"([^\"]+)\"").find(html) ?: return null
        val v = m.groupValues[1].trim()
        if (!v.startsWith("http") || !v.contains(".m3u8")) return null
        Logger.d(TAG, "DLHD player SRC stream")
        return v
    }

    /**
     * Loads [url] in an offscreen WebView and intercepts the first `.m3u8`
     * request it makes.
     *
     * Gives up after 25 seconds.
     */
    suspend fun interceptM3u8(context: Context, url: String): NtvStreamResult? =
        withTimeoutOrNull(TIMEOUT_MS) {
            m3u8Driver(context, url)
        }

    /**
     * Resolves a sports stream.
     *
     * Uses [embedUrl] directly when it is supplied, otherwise the `ntv.cx`
     * watch page is scraped for the embed URL.
     */
    suspend fun resolveSports(
        context: Context,
        name: String,
        code: String,
        embedUrl: String?,
    ): NtvStreamResult? {
        if (embedUrl != null) return interceptM3u8(context, embedUrl)
        val url = "https://ntv.cx/watch/" + name + "/" + code
        Logger.d(TAG, "Resolving sports stream via watch page: $url")
        val embed = withContext(Dispatchers.IO) { extractEmbedUrl(url) } ?: return null
        return interceptM3u8(context, embed)
    }

    /**
     * Resolves a channel to a playable stream.
     *
     * @param provider the server slug (`hesgoales`, `dlhd`, `cdnlive`, …).
     * @param streamId the provider's channel id.
     * @param embedUrl an optional direct embed URL.
     */
    suspend fun resolve(
        context: Context,
        name: String,
        code: String,
        provider: String,
        streamId: String,
        embedUrl: String?,
    ): NtvStreamResult? {
        if (provider == "hesgoales" || (embedUrl != null && embedUrl.contains("livelive24.com"))) {
            if (!embedUrl.isNullOrBlank()) {
                Logger.d(TAG, "Resolving hesgoales DOM for: $embedUrl")
                return withTimeoutOrNull(TIMEOUT_MS) { hesgoalesDriver(context, embedUrl) }
            }
            return null
        }
        if (provider == "dlhd" && streamId.isNotBlank()) {
            CookieManager.getInstance().setAcceptCookie(true)
            Logger.d(TAG, "Resolving DLHD stream for: $name (id=$streamId)")
            Logger.d(TAG, "DLHD loading: https://dlive.sx/stream/stream-$streamId.php")
            return withContext(Dispatchers.IO) { resolveDlhd(streamId) }
        }
        val url = "https://ntv.cx/channel-" + provider + "/" + name.replace(" ", "-") + "?code=" + code
        Logger.d(TAG, "Resolving stream for: $url")
        val embed = withContext(Dispatchers.IO) { extractEmbedUrl(url) }
        if (embed == null) {
            Logger.e(TAG, "Failed to extract embed URL")
            return null
        }
        Logger.d(TAG, "Embed URL: $embed")
        val result = interceptM3u8(context, embed)
        if (result == null) {
            Logger.e(TAG, "Failed to intercept m3u8 URL")
            return null
        }
        Logger.d(TAG, "Resolved m3u8: ${result.url}")
        return result
    }

    /** Scrapes the embed URL out of an `ntv.cx` watch / channel page. */
    private suspend fun extractEmbedUrl(url: String): String? {
        return try {
            val body = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", DEFAULT_HEADERS["User-Agent"]!!)
                    .build(),
            ).execute().body?.string() ?: return null
            var m = Regex(
                "<iframe[^>]*id=[\"']streamPlayer[\"'][^>]*src=[\"']([^\"']+)[\"']",
                RegexOption.IGNORE_CASE,
            ).find(body)
            if (m == null) {
                m = Regex(
                    "<iframe[^>]*src=[\"'](/embed[^\"']+)[\"']",
                    RegexOption.IGNORE_CASE,
                ).find(body)
            }
            val src = m?.groupValues?.get(1) ?: return null
            if (src.startsWith("http")) src else "https://ntv.cx" + src
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to extract embed URL from $url", e)
            null
        }
    }

    /** Scrapes a DLHD stream page and resolves its playable URL. */
    private suspend fun resolveDlhd(streamId: String): NtvStreamResult? {
        return try {
            val pageUrl = "https://dlive.sx/stream/stream-$streamId.php"
            val html = client.newCall(
                Request.Builder()
                    .url(pageUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://ntv.cx/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build(),
            ).execute().body?.string()
            if (html.isNullOrEmpty()) {
                Logger.e(TAG, "DLHD failed to load stream page for $streamId")
                return null
            }
            val src = Regex(
                "<iframe[^>]*id=[\"']thatframe[\"'][^>]*src=[\"']([^\"']+)[\"']",
                RegexOption.IGNORE_CASE,
            ).find(html)?.groupValues?.get(1)
                ?: Regex(
                    "<iframe[^>]*src=[\"']([^\"']+)[\"']",
                    RegexOption.IGNORE_CASE,
                ).find(html)?.groupValues?.get(1)
            if (src == null) {
                Logger.w(TAG, "DLHD no embed iframe found in stream page for $streamId")
                return null
            }
            Logger.d(TAG, "DLHD embed iframe: $src")
            val parsed = URL(src)
            val origin = parsed.protocol + "://" + parsed.host
            val embedHtml = client.newCall(
                Request.Builder()
                    .url(src)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", pageUrl)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build(),
            ).execute().body?.string()
            if (embedHtml.isNullOrEmpty()) {
                Logger.e(TAG, "DLHD failed to load embed iframe")
                return null
            }
            val url = extractPlayerSrc(embedHtml)
                ?: decryptDlhdTiestep(embedHtml)
                ?: extractStreamUrl(embedHtml)
            if (url == null) {
                Logger.w(TAG, "DLHD could not extract stream from embed")
                return null
            }
            Logger.d(TAG, "DLHD resolved: $url")
            NtvStreamResult(
                url,
                mapOf(
                    "Origin" to origin,
                    "Referer" to "$origin/",
                    "User-Agent" to USER_AGENT,
                ),
            )
        } catch (e: Exception) {
            Logger.e(TAG, "DLHD resolution error: ${e.message}")
            null
        }
    }

    companion object {
        const val TAG = "NtvStreamExtractor"

        /** The WebView/parse timeout used by every branch. */
        const val TIMEOUT_MS = 25_000L

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36"

        /** The headers the original sends with every live request. */
        val DEFAULT_HEADERS: Map<String, String> = mapOf(
            "Origin" to "https://cdnlivetv.tv",
            "Referer" to "https://cdnlivetv.tv/",
            "User-Agent" to USER_AGENT,
        )

        /** Base64url-decodes [s] and interprets the bytes as ISO-8859-1. */
        fun base64UrlDecodeIso(s: String): String? = decodeBase64(s, Charsets.ISO_8859_1)

        /** Base64url-decodes [s] and interprets the bytes as UTF-8. */
        fun base64UrlDecodeUtf8(s: String): String? = decodeBase64(s, Charsets.UTF_8)

        private fun decodeBase64(s: String, charset: java.nio.charset.Charset): String? = try {
            val t = s.replace('-', '+').replace('_', '/')
            val padded = t + "=".repeat((4 - (t.length % 4)) % 4)
            String(android.util.Base64.decode(padded, 0), charset)
        } catch (_: Exception) {
            null
        }

        /** Drives the offscreen WebView that intercepts the `.m3u8` request. */
        private suspend fun m3u8Driver(context: Context, url: String): NtvStreamResult? =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val captured = AtomicBoolean(false)
                    val tornDown = AtomicBoolean(false)
                    val handler = Handler(Looper.getMainLooper())
                    val webView = WebView(context)
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.settings.mediaPlaybackRequiresUserGesture = false
                    webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webView.settings.userAgentString = USER_AGENT
                    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                    webView.setWebChromeClient(SilentWebChromeClient())
                    webView.setWebViewClient(
                        M3u8InterceptingWebViewClient(
                            captured = captured,
                            predicate = { it.contains(".m3u8") },
                            continuation = continuation,
                            tornDown = tornDown,
                            handler = handler,
                            webView = webView,
                        ),
                    )
                    continuation.invokeOnCancellation {
                        handler.post {
                            tornDown.set(true)
                            teardownWebView(webView)
                        }
                    }
                    Logger.d(TAG, "Loading embed URL: $url")
                    webView.loadUrl(url)
                }
            }

        /** Drives the offscreen WebView that polls the hesgoales DOM. */
        private suspend fun hesgoalesDriver(context: Context, url: String): NtvStreamResult? =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val captured = AtomicBoolean(false)
                    val tornDown = AtomicBoolean(false)
                    val handler = Handler(Looper.getMainLooper())
                    val webView = WebView(context)
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.settings.mediaPlaybackRequiresUserGesture = false
                    webView.settings.userAgentString = USER_AGENT
                    webView.setWebChromeClient(SilentWebChromeClient())
                    webView.setWebViewClient(
                        HesgoalesWebViewClient(
                            handler = handler,
                            webView = webView,
                            captured = captured,
                            tornDown = tornDown,
                            continuation = continuation,
                        ),
                    )
                    continuation.invokeOnCancellation {
                        handler.post {
                            tornDown.set(true)
                            teardownWebView(webView)
                        }
                    }
                    Logger.d(TAG, "Loading embed URL: $url")
                    webView.loadUrl(url)
                }
            }
    }
}

/** Tears a WebView down. Mirrors the original's `l` / `s` teardown runnables. */
private fun teardownWebView(webView: WebView) {
    try {
        webView.stopLoading()
        webView.onPause()
        webView.removeAllViews()
        webView.loadUrl("about:blank")
        webView.destroy()
    } catch (_: Exception) {
    }
}

/**
 * Intercepts the first `.m3u8` request the page makes.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.j`.
 */
private class M3u8InterceptingWebViewClient(
    private val captured: AtomicBoolean,
    private val predicate: (String) -> Boolean,
    private val continuation: CancellableContinuation<NtvStreamResult>,
    private val tornDown: AtomicBoolean,
    private val handler: Handler,
    private val webView: WebView,
) : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String) {
        Logger.d(NtvStreamExtractor.TAG, "Page loaded: $url")
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url?.toString() ?: return null
        if (captured.get() && (url.startsWith("http://") || url.startsWith("https://"))) {
            return emptyResponse()
        }
        if (predicate(url) && !captured.get()) {
            captured.set(true)
            val requestHeaders = request.requestHeaders ?: emptyMap()
            val out = LinkedHashMap<String, String>()
            out["Origin"] = requestHeaders["Origin"]
                ?: NtvStreamExtractor.DEFAULT_HEADERS["Origin"]!!
            out["Referer"] = requestHeaders["Referer"]
                ?: NtvStreamExtractor.DEFAULT_HEADERS["Referer"]!!
            out["User-Agent"] = requestHeaders["User-Agent"]
                ?: NtvStreamExtractor.USER_AGENT
            try {
                val cookie = CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrBlank()) out["Cookie"] = cookie
            } catch (_: Exception) {
            }
            Logger.d(NtvStreamExtractor.TAG, "Intercepted stream: $url")
            Logger.d(
                NtvStreamExtractor.TAG,
                "Captured headers: keys=${out.keys} cookie=${out.containsKey("Cookie")}",
            )
            continuation.resumeWith(Result.success(NtvStreamResult(url, out)))
            if (!tornDown.get()) {
                tornDown.set(true)
                handler.post { teardownWebView(webView) }
            }
            return emptyResponse()
        }
        return null
    }

    private fun emptyResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0)),
    )
}

/**
 * Swallows the page's console output.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.i`.
 */
private class SilentWebChromeClient : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean = true
}

/**
 * Starts the hesgoales DOM poller once the page has finished loading.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.q`.
 */
private class HesgoalesWebViewClient(
    private val handler: Handler,
    private val webView: WebView,
    private val captured: AtomicBoolean,
    private val tornDown: AtomicBoolean,
    private val continuation: CancellableContinuation<NtvStreamResult>,
) : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String) {
        Logger.d(NtvStreamExtractor.TAG, "Hesgoales page finished: $url")
        handler.postDelayed(
            HesgoalesDomPoller(
                captured = captured,
                continuation = continuation,
                handler = handler,
                webView = webView,
                tornDown = tornDown,
            ),
            2000L,
        )
    }
}

/**
 * Polls the hesgoales page for its `#source` element.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.p`.
 */
private class HesgoalesDomPoller(
    private val captured: AtomicBoolean,
    private val continuation: CancellableContinuation<NtvStreamResult>,
    private val handler: Handler,
    private val webView: WebView,
    private val tornDown: AtomicBoolean,
) : Runnable {

    private var attempts = 0

    override fun run() {
        if (tornDown.get() || attempts > 20) return
        attempts++
        webView.evaluateJavascript(
            "(function() {   var s = document.getElementById('source');   return s ? s.src : null; })();",
            HesgoalesValueCallback(
                captured = captured,
                continuation = continuation,
                handler = handler,
                poller = this,
                tornDown = tornDown,
                webView = webView,
            ),
        )
    }
}

/**
 * Receives the value of the `#source` element and resumes the continuation.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.o`.
 */
private class HesgoalesValueCallback(
    private val captured: AtomicBoolean,
    private val continuation: CancellableContinuation<NtvStreamResult>,
    private val handler: Handler,
    private val poller: Runnable,
    private val tornDown: AtomicBoolean,
    private val webView: WebView,
) : ValueCallback<String> {

    override fun onReceiveValue(value: String?) {
        val v = value?.trim()?.removeSurrounding("\"")
        if (v == null || v.isBlank() || v == "null" || !v.startsWith("http")) {
            handler.postDelayed(poller, 1000L)
            return
        }
        if (captured.get()) return
        captured.set(true)
        Logger.d(NtvStreamExtractor.TAG, "Hesgoales source found: $v")
        continuation.resumeWith(
            Result.success(NtvStreamResult(v, NtvStreamExtractor.DEFAULT_HEADERS)),
        )
        if (!tornDown.get()) {
            tornDown.set(true)
            handler.post { teardownWebView(webView) }
        }
    }
}
