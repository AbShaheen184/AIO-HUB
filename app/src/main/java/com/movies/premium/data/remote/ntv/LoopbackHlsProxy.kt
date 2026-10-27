package com.movies.premium.data.remote.ntv

import com.movies.premium.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The response of an upstream request.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.c` (R8 name `c`).
 */
data class UpstreamResponse(
    val code: Int,
    val contentType: String?,
    val contentRange: String?,
    val contentLength: Long?,
    val body: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UpstreamResponse) return false
        return code == other.code &&
            contentType == other.contentType &&
            contentRange == other.contentRange &&
            contentLength == other.contentLength &&
            (body?.contentEquals(other.body) ?: (other.body == null))
    }

    override fun hashCode(): Int {
        var result = code
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (contentRange?.hashCode() ?: 0)
        result = 31 * result + (contentLength?.hashCode() ?: 0)
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * A tiny loopback HTTP proxy that re-writes an HLS playlist so the player can
 * consume it (and refreshes the upstream URL when it expires).
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.e` (R8 name `e`).
 *
 * The proxy serves:
 * - `GET /watch.m3u8` — the (re-written) playlist.
 * - `GET /seg?u=<urlencoded upstream url>` — a single segment or nested
 *   playlist.
 */
class LoopbackHlsProxy(
    private val client: OkHttpClient,
    upstreamUrl: String,
    upstreamHeaders: Map<String, String>,
    private val refresh: suspend () -> NtvStreamResult?,
) {
    /** The upstream playlist URL; replaced when [refreshUpstream] succeeds. */
    @Volatile
    var upstreamUrl: String = upstreamUrl

    /** The headers sent with every upstream request. */
    @Volatile
    var upstreamHeaders: Map<String, String> = upstreamHeaders

    /** How many times the upstream has been refreshed. */
    val refreshCount = AtomicInteger(0)

    /** The bound server socket, `null` once [stop] was called. */
    var serverSocket: ServerSocket? = null
        private set

    /** The local playlist URL the player should open. */
    var localUrl: String? = null
        private set

    private val executor = Executors.newFixedThreadPool(3)

    init {
        start()
    }

    /** Binds the loopback socket and starts accepting connections. */
    fun start() {
        try {
            val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = socket
            localUrl = "http://127.0.0.1:${socket.localPort}/watch.m3u8"
            executor.execute {
                while (!socket.isClosed) {
                    try {
                        val connection = socket.accept()
                        executor.execute { handleSocket(connection) }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start loopback proxy: ${e.message}")
        }
    }

    /** `true` for the status codes that mean "the upstream URL expired". */
    fun isAuthError(code: Int): Boolean =
        code == 401 || code == 403 || code == 410 || code == 503

    /**
     * Re-writes every URI of an HLS playlist so that it points at this proxy.
     */
    fun rewritePlaylist(playlist: String, baseUrl: String): String {
        return try {
            val uri = URI(baseUrl)
            playlist.split("\n").joinToString("\n") { rewriteUri(uri, uri.query, it) }
        } catch (_: Exception) {
            playlist
        }
    }

    /**
     * Resolves [value] against [base] and wraps it in the proxy's segment
     * route.
     */
    fun rewriteUri(base: URI, query: String?, value: String): String {
        try {
            val s = value.trim()
            if (s.isNotEmpty() && !s.startsWith("data:")) {
                var resolved = base.resolve(s).toString()
                if (!resolved.contains("?") && query != null) resolved = resolved + "?" + query
                return "/seg?u=" + URLEncoder.encode(resolved, "UTF-8")
            }
        } catch (_: Exception) {
        }
        return value
    }

    /** Writes a raw HTTP response head. */
    fun writeResponse(out: OutputStream, code: Int, reason: String, headers: Map<String, String>) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        for ((k, v) in headers) {
            sb.append(k).append(": ").append(v).append("\r\n")
        }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
    }

    /** Writes an error response with an empty body. */
    fun writeError(out: OutputStream, code: Int, reason: String) {
        writeResponse(out, code, reason, mapOf("Connection" to "close", "Content-Length" to "0"))
    }

    /** Fetches [url] from the upstream server, optionally with a `Range` header. */
    fun fetchUpstream(url: String, range: String?): UpstreamResponse? {
        return try {
            val builder = Request.Builder().url(url)
            upstreamHeaders.forEach { (k, v) -> builder.header(k, v) }
            if (range != null) builder.header("Range", range)
            val response = client.newCall(builder.build()).execute()
            val bytes = response.body?.bytes()
            Logger.d(TAG, "upstream ${response.code} ${url.takeLast(70)}")
            val contentLength = response.header("Content-Length")?.toLongOrNull()
                ?: bytes?.size?.toLong()
            UpstreamResponse(
                response.code,
                response.header("Content-Type"),
                response.header("Content-Range"),
                contentLength,
                bytes,
            )
        } catch (e: Exception) {
            Logger.d(TAG, "upstream error ${url.takeLast(70)}: ${e.message}")
            null
        }
    }

    /** Serves a single client connection. */
    fun handleSocket(socket: Socket) {
        try {
            val reader = BufferedReader(
                InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8),
            )
            val requestLine = reader.readLine() ?: return
            val tokens = requestLine.split(" ")
            if (tokens.size < 2) {
                writeError(socket.getOutputStream(), 400, "Bad Request")
                return
            }
            val method = tokens[0].uppercase(Locale.ROOT)
            val path = tokens[1]
            var range: String? = null
            var headerLines = 0
            while (headerLines < 64) {
                val line = reader.readLine() ?: break
                headerLines++
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    range = line.substringAfter(":").trim()
                }
            }
            val out = socket.getOutputStream()
            when {
                path == "/watch.m3u8" || path.startsWith("/watch.m3u8?") -> {
                    servePlaylist(out, method)
                }

                path.startsWith("/seg?u=") -> {
                    val u = URLDecoder.decode(path.substringAfter("/seg?u="), "UTF-8")
                    if (u.isBlank() || !(u.startsWith("http://") || u.startsWith("https://"))) {
                        writeError(out, 400, "Bad Request")
                    } else {
                        serveSegment(out, method, u, range)
                    }
                }

                else -> writeError(out, 404, "Not Found")
            }
            out.flush()
        } catch (_: Exception) {
        } finally {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    /**
     * Refreshes the upstream URL through the injected [refresh] lambda.
     *
     * Gives up after three refreshes.
     */
    fun refreshUpstream(): Boolean {
        if (refreshCount.getAndIncrement() >= 3) {
            Logger.d(TAG, "refresh budget exhausted")
            return false
        }
        Logger.d(TAG, "refreshing upstream URL (attempt ${refreshCount.get()})")
        return try {
            val result = runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) { refresh() }
            }
            if (result == null || result.url.isBlank()) {
                Logger.d(TAG, "refresh produced nothing")
                return false
            }
            upstreamUrl = result.url
            if (result.headers.isNotEmpty()) upstreamHeaders = result.headers
            Logger.d(TAG, "upstream refreshed: ${result.url.takeLast(70)}")
            true
        } catch (e: Exception) {
            Logger.d(TAG, "refresh failed: ${e.message}")
            false
        }
    }

    /** Stops the proxy and shuts the worker pool down. */
    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        localUrl = null
        executor.shutdownNow()
    }

    private fun serveSegment(out: OutputStream, method: String, u: String, range: String?) {
        val first = fetchUpstream(u, range)
        if (first == null) {
            writeError(out, 502, "Bad Gateway")
            return
        }
        var up: UpstreamResponse = first
        if (isAuthError(up.code) && refreshUpstream()) {
            val refreshed = fetchUpstream(u, range)
            if (refreshed != null) {
                up = refreshed
                if (up.code == 200 || up.code == 206) refreshCount.set(0)
            }
        }
        if (up.code == 200 || up.code == 206) refreshCount.set(0)

        val body = up.body
        if (body != null && DlhdUnwrap.isPng(body)) {
            try {
                val ts = DlhdUnwrap.unwrap(body)
                if (ts != null) {
                    Logger.d(TAG, "unwrapped PNG segment ${body.size}B -> ${ts.size}B TS")
                    writeResponse(
                        out,
                        200,
                        "OK",
                        mapOf(
                            "Content-Type" to "video/mp2t",
                            "Content-Length" to ts.size.toString(),
                            "Connection" to "close",
                        ),
                    )
                    if (method != "HEAD") out.write(ts)
                    return
                }
                Logger.d(TAG, "PNG segment unwrap failed, passing through raw")
            } catch (e: Exception) {
                Logger.d(TAG, "unwrap threw (${e.javaClass.simpleName}), 502")
                writeError(out, 502, "Bad Gateway")
                return
            }
        }

        val bytes = body ?: ByteArray(0)
        val text = String(bytes, StandardCharsets.UTF_8).trimStart('\uFEFF')
        if (text.startsWith("#EXTM3U")) {
            Logger.d(TAG, "rewriting nested playlist (${bytes.size}B)")
            val rewritten = rewritePlaylist(text, u).toByteArray(StandardCharsets.UTF_8)
            writeResponse(
                out,
                200,
                "OK",
                mapOf(
                    "Content-Type" to "application/vnd.apple.mpegurl",
                    "Content-Length" to rewritten.size.toString(),
                    "Connection" to "close",
                ),
            )
            if (method != "HEAD") out.write(rewritten)
            return
        }

        val headers = LinkedHashMap<String, String>()
        headers["Content-Type"] = up.contentType ?: "application/octet-stream"
        headers["Content-Length"] = (up.contentLength ?: bytes.size.toLong()).toString()
        up.contentRange?.let { headers["Content-Range"] = it }
        headers["Connection"] = "close"
        val reason = if (up.code == 206) "Partial Content" else "OK"
        writeResponse(out, up.code, reason, headers)
        if (method != "HEAD") out.write(bytes)
    }

    private fun servePlaylist(out: OutputStream, method: String) {
        var url = upstreamUrl
        var resp: UpstreamResponse? = null
        var attempts = 0
        while (attempts < 4) {
            attempts++
            val r = fetchUpstream(url, null)
            if (r != null && !isAuthError(r.code)) {
                if (r.code == 200 || r.code == 206) refreshCount.set(0)
                resp = r
                break
            }
            resp = r
            if (!refreshUpstream()) break
            url = upstreamUrl
        }
        if (resp != null && resp.code == 200 && resp.body != null) {
            val rewritten = rewritePlaylist(
                String(resp.body, StandardCharsets.UTF_8),
                upstreamUrl,
            ).toByteArray(StandardCharsets.UTF_8)
            writeResponse(
                out,
                200,
                "OK",
                mapOf(
                    "Content-Type" to "application/vnd.apple.mpegurl",
                    "Content-Length" to rewritten.size.toString(),
                    "Connection" to "close",
                ),
            )
            if (method != "HEAD") out.write(rewritten)
        } else {
            writeError(out, resp?.code ?: 502, "Bad Gateway")
        }
    }

    companion object {
        const val TAG = "LoopbackHlsProxy"
        private const val REFRESH_TIMEOUT_MS = 20_000L
    }
}
