package com.movies.premium.data.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.movies.premium.data.local.entity.DownloadEntity
import com.movies.premium.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The body of a single download. Mirrors the original's
 * `com.app.mlounge.data.download.g`.
 *
 * Responsibilities:
 *  * respect the concurrent-download cap,
 *  * resume an interrupted download over HTTP `Range`,
 *  * copy the body to disk in 8 KiB chunks while persisting progress,
 *  * map failures onto the original's user-facing reason strings.
 */
internal suspend fun DownloadManager.runDownload(id: Long) {
    val ctx: Context = appContext()
    var written = 0L

    try {
        // ---- Phase A: capacity gate & job registration -------------------------
        val max = preferences.simultaneousDownloads.first()
        if (activeJobCount() >= max) {
            Logger.d(DownloadManager.TAG, "Queue: $id waiting for a slot (max: $max)")
            // The row stays "pending"; resumePending() picks it up later.
            return
        }
        registerJob(id)
        val entity = downloadDao.find(id) ?: run {
            unregisterJob(id)
            return
        }

        // ---- Phase B: already-completed short-circuit --------------------------
        if (entity.status == DownloadEntity.STATUS_COMPLETED) {
            Logger.d(DownloadManager.TAG, "Download already completed: $id")
            unregisterJob(id)
            return
        }

        // ---- Phase C: work out the resume offset -------------------------------
        val existingPath = entity.filePath
        val isSaf = existingPath != null && existingPath.startsWith(CONTENT_PREFIX)
        val offset = when {
            existingPath == null -> 0L
            isSaf -> DocumentFile.fromTreeUri(ctx, Uri.parse(existingPath))?.length() ?: 0L
            else -> File(existingPath).length()
        }

        // ---- Phase D: build the request ----------------------------------------
        val requestBuilder = okhttp3.Request.Builder()
            .url(entity.url)
            .header("User-Agent", USER_AGENT)

        if (offset > 0) {
            requestBuilder.addHeader("Range", "$offset-")
            Logger.d(DownloadManager.TAG, "Resuming $id from $offset bytes")
        }

        // Stored headers are re-applied after the User-Agent, so a stored User-Agent
        // ends up as a second UA header — matching the original.
        entity.headers?.split("\n")?.forEach { line ->
            val kv = line.split("=", limit = 2)
            if (kv.size == 2) {
                requestBuilder.addHeader(kv[0], kv[1])
            }
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()

        // ---- Phase E: 416 -> the range is unsatisfiable, start over -------------
        if (response.code == 416) {
            response.close()
            Logger.w(DownloadManager.TAG, "Range not satisfiable for $offset, starting over")
            existingPath?.let { deleteFile(it) }
            unregisterJob(id)
            startDownload(id)
            return
        }

        if (!response.isSuccessful) {
            response.close()
            val code = response.code
            Logger.e(DownloadManager.TAG, "Download failed with HTTP $code")
            downloadDao.markFailed(id, "HTTP error $code")
            unregisterJob(id)
            return
        }

        val body = response.body
        if (body == null) {
            response.close()
            Logger.e(DownloadManager.TAG, "Empty response body")
            downloadDao.markFailed(id, "Empty response body")
            unregisterJob(id)
            return
        }

        // ---- Phase F: work out the total size ----------------------------------
        val contentType = response.header("Content-Type").orEmpty()
        val finalUrl = response.request.url.toString()
        val contentRange = response.header("Content-Range")
        val totalFromRange = contentRange?.substringAfterLast("/")?.toLongOrNull()
        val contentLength = body.contentLength()
        val total = if (contentLength > 0) contentLength + offset else (totalFromRange ?: 0L)

        val isSlate = finalUrl.contains(SLATE_HOST)

        // An HTML body means we were handed an error page, not media.
        if (contentType == "text/html") {
            response.close()
            throw IOException("Link expired or protected by Cloudflare")
        }
        if (contentType.contains("x-mpegurl") ||
            contentType.contains("scpls") ||
            contentType.contains("playback")
        ) {
            response.close()
            throw IOException("Source is an HLS stream, not a direct file")
        }

        // ---- Phase G: open the output stream -----------------------------------
        // On 206 we append to what we already have; on 200 the server ignored the
        // range, so we truncate and rewrite from scratch.
        val isPartial = response.code == 206
        var targetPath = existingPath
        val output: OutputStream

        if (isSaf) {
            val uri = Uri.parse(existingPath!!)
            output = if (isPartial) {
                ctx.contentResolver.openOutputStream(uri, "wa")
                    ?: throw IOException("Failed to open output stream for resume")
            } else {
                written = offset
                Logger.w(DownloadManager.TAG, "Server returned 200 instead of 206, overwriting file")
                ctx.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Failed to reopen stream for overwrite")
            }
        } else {
            val path = existingPath ?: createOutput(entity.fileName)?.filePath
                ?: throw IOException("Failed to open output stream for resume")
            targetPath = path
            val file = File(path)
            output = if (isPartial) {
                // The original opens local files in append mode when resuming.
                FileOutputStream(file, true)
            } else {
                written = offset
                Logger.w(DownloadManager.TAG, "Server returned 200 instead of 206, overwriting file")
                FileOutputStream(file, false)
            }
        }

        // ---- Phase H: the copy loop --------------------------------------------
        val input: InputStream = body.byteStream()
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            while (currentCoroutineContext().isActive) {
                val n = input.read(buffer)
                if (n == -1) break
                output.write(buffer, 0, n)
                written += n

                val progress = if (total > 0) ((written * 100) / total).toInt() else 0
                downloadDao.updateProgress(
                    id,
                    DownloadEntity.STATUS_DOWNLOADING,
                    progress.coerceIn(0, 99),
                    written,
                )
            }
        } finally {
            runCatching { input.close() }
            runCatching { output.close() }
            unregisterJob(id)
        }

        // ---- Phase I: final size check -----------------------------------------
        if (written < MIN_ACCEPTABLE_SIZE) {
            Logger.w(DownloadManager.TAG, "Download finished but file is too small: ${written}B")
            throw IOException("Invalid or empty video file")
        }

        // ---- Phase J: completion ------------------------------------------------
        downloadDao.markCompleted(
            id = id,
            completedAt = System.currentTimeMillis(),
            filePath = targetPath.orEmpty(),
            totalBytes = if (total > 0) total else written,
            downloadedBytes = written,
        )
        Logger.d(DownloadManager.TAG, "Download completed: ${entity.title}")
    } catch (e: CancellationException) {
        unregisterJob(id)
        Logger.d(DownloadManager.TAG, "Download job stopped: $id")
        throw e
    } catch (e: Throwable) {
        unregisterJob(id)
        val reason = describeFailure(e)
        Logger.e(DownloadManager.TAG, "Download error: ${e.message}", e)
        runCatching { downloadDao.markFailed(id, reason) }
    }
}

/**
 * Maps an exception onto one of the original's failure reasons.
 * Mirrors the original's reason ladder.
 */
internal fun describeFailure(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        e is IOException && (msg.contains("No space") || msg.contains("ENOSPC")) ->
            "Not enough storage space"

        msg.contains("EPERM") ->
            "Operation not permitted"

        msg.contains("No internet connection") -> "No internet connection"
        msg.contains("Connection timed out") -> "Connection timed out"
        msg.contains("Could not connect to server") -> "Could not connect to server"
        msg.contains("SSL") || msg.contains("TLS") -> "SSL/TLS error"
        e is IOException -> "Network error: $msg"
        else -> "Unknown error"
    }
}

/**
 * The user-facing message for a permission failure, matching the original's wording.
 */
internal const val PERMISSION_DENIED_MESSAGE =
    "Permission denied. Please use 'System Picker' in settings for this drive."

/** The original's hard-coded desktop Chrome user agent. */
internal const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

/** The debrid/Slate host the original special-cases. */
internal const val SLATE_HOST = "slate.elfhosted.com"

/** Copy buffer size. */
internal const val BUFFER_SIZE = 8192

/** A download smaller than this is rejected as an error page. */
internal const val MIN_ACCEPTABLE_SIZE = 102400L

internal const val CONTENT_PREFIX = "content://"
