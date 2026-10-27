package com.movies.premium.data.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.movies.premium.data.local.dao.DownloadDao
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * The public facade of the download subsystem.
 *
 * Mirrors the original's `com.app.mlounge.data.download.i`. The original runs downloads
 * entirely in-process — there is **no WorkManager** — using a coroutine scope plus an
 * in-memory map of active jobs, with the Room `downloads` table as the durable queue.
 *
 * Five seconds after construction the manager reconciles storage (dropping rows whose file
 * vanished and deleting orphaned files) and then resumes any pending download.
 */
class DownloadManager(
    private val context: Context,
    internal val downloadDao: DownloadDao,
    internal val preferences: AppPreferences,
    internal val httpClient: OkHttpClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Active download id -> job. Mirrors the original's `activeJobs`. */
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    /** All download rows, newest first. */
    val downloads: Flow<List<com.movies.premium.data.local.entity.DownloadEntity>> =
        downloadDao.observeAll()

    init {
        scope.launch {
            delay(5_000)
            withContext(Dispatchers.IO) { reconcileStorage() }
            resumePending()
        }
    }

    /**
     * Queues a download. Fire-and-forget: the row is inserted as `pending` and the worker
     * is started once the insert completes.
     *
     * Mirrors the original's `i.i(...)`, including its default arguments.
     */
    fun enqueueDownload(
        url: String,
        fileName: String,
        title: String,
        contentType: String,
        contentId: String,
        posterPath: String? = null,
        quality: String,
        size: String,
        headers: Map<String, String>? = null,
        tmdbId: Int = 0,
        season: Int = 0,
        episode: Int = 0,
    ) {
        scope.launch {
            val entity = com.movies.premium.data.local.entity.DownloadEntity(
                id = 0L,
                url = url,
                fileName = sanitizeFileName(fileName),
                title = title,
                contentType = contentType,
                contentId = contentId,
                posterPath = posterPath,
                quality = quality,
                size = size,
                status = com.movies.premium.data.local.entity.DownloadEntity.STATUS_PENDING,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                filePath = null,
                headers = headers?.entries
                    ?.joinToString("\n") { "${it.key}=${it.value}" }
                    ?.takeIf { it.isNotEmpty() },
                addedAt = System.currentTimeMillis(),
                completedAt = null,
                tmdbId = tmdbId,
                season = season,
                episode = episode,
                failureReason = null,
            )
            val id = downloadDao.insert(entity)
            Logger.d(TAG, "Enqueued download: $title (id=$id)")
            startDownload(id)
        }
    }

    /**
     * Starts the worker for [id] unless it is already running.
     * Mirrors the original's `i.k(long)`.
     */
    fun startDownload(id: Long) {
        if (activeJobs.containsKey(id)) return
        scope.launch {
            runDownload(id)
        }
    }

    /**
     * Picks the oldest pending download and starts it, if there is a free slot.
     * Mirrors the original's `i.h(...)`.
     */
    suspend fun resumePending() {
        val max = preferences.simultaneousDownloads.first()
        if (activeJobs.size >= max) return
        val pending = downloadDao.observeAll().first()
            .filter { it.status == com.movies.premium.data.local.entity.DownloadEntity.STATUS_PENDING }
        val next = pending.minByOrNull { it.addedAt } ?: return
        startDownload(next.id)
    }

    /** Pauses a running download. The worker re-checks the status and stops. */
    suspend fun pauseDownload(id: Long) {
        downloadDao.markPaused(id)
        startDownload(id)
    }

    /** Resumes a paused download. */
    suspend fun resumeDownload(id: Long) {
        downloadDao.markPending(id)
        resumePending()
    }

    /** Cancels a download: deletes the partial file and frees the slot. */
    suspend fun cancelDownload(id: Long) {
        val entity = downloadDao.find(id)
        entity?.filePath?.let { deleteFile(it) }
        downloadDao.markCancelled(id)
        resumePending()
    }

    /** Retries a download: deletes the partial file and resets the row to `pending`. */
    suspend fun retryDownload(id: Long) {
        val entity = downloadDao.find(id) ?: return
        entity.filePath?.let { deleteFile(it) }
        downloadDao.insert(
            entity.copy(
                downloadedBytes = 0L,
                totalBytes = 0L,
                status = com.movies.premium.data.local.entity.DownloadEntity.STATUS_PENDING,
                failureReason = null,
            )
        )
        startDownload(id)
    }

    // ---- internals used by the worker -----------------------------------------

    internal fun appContext(): Context = context

    internal fun activeJobCount(): Int = activeJobs.size

    internal fun registerJob(id: Long) {
        val job = scope.coroutineContext[Job]
        if (job != null) activeJobs[id] = job
    }

    internal fun unregisterJob(id: Long) {
        activeJobs.remove(id)
    }

    /**
     * Reconciles the download table against what is actually on disk: rows whose file has
     * disappeared are deleted, and orphaned files are removed.
     *
     * Mirrors the original's `f` (`StorageReconcileCoroutine`).
     */
    internal suspend fun reconcileStorage() {
        try {
            val location = preferences.downloadDir.first()
            val rows = downloadDao.observeAll().first()
            val knownPaths = rows.mapNotNull { it.filePath }.toSet()

            // Drop rows whose file is missing.
            rows.forEach { entity ->
                val path = entity.filePath ?: return@forEach
                val exists = if (path.startsWith(CONTENT_PREFIX)) {
                    DocumentFile.fromTreeUri(context, Uri.parse(path))?.exists() == true
                } else {
                    File(path).exists()
                }
                if (!exists) {
                    Logger.d(TAG, "Reconcile: Removing DB entry for missing file: $path")
                    downloadDao.delete(entity.id)
                }
            }

            // Delete files in the target directory that no row refers to.
            if (location != null && location.startsWith(CONTENT_PREFIX)) {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(location)) ?: return
                tree.listFiles().forEach { doc ->
                    val uri = doc.uri.toString()
                    if (uri !in knownPaths) {
                        Logger.d(TAG, "Reconcile: Deleting orphaned SAF file: $uri")
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, doc.uri) }
                    }
                }
            } else {
                val dir = if (location != null) {
                    File(location)
                } else {
                    File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        DEFAULT_DIR_NAME,
                    )
                }
                dir.listFiles()?.forEach { file ->
                    if (file.absolutePath !in knownPaths) {
                        Logger.d(TAG, "Reconcile: Deleting orphaned local file: ${file.absolutePath}")
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Storage reconcile failed: ${e.message}", e)
        }
    }

    /**
     * Opens the output stream for [fileName], honouring the user's chosen download
     * location. Mirrors the original's `i.a(String)`.
     *
     * Note the SAF quirk: the created document's display name has its **extension stripped**
     * — the MIME type carries the type instead.
     */
    suspend fun createOutput(fileName: String): OutputResult? {
        val location = preferences.downloadDir.first()
        return when {
            location == null -> {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    DEFAULT_DIR_NAME,
                )
                dir.mkdirs()
                val file = uniqueFile(File(dir, fileName))
                OutputResult(FileOutputStream(file), file.absolutePath)
            }

            !location.startsWith(CONTENT_PREFIX) -> {
                val dir = File(location)
                dir.mkdirs()
                val file = uniqueFile(File(dir, fileName))
                OutputResult(FileOutputStream(file), file.absolutePath)
            }

            else -> {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(location)) ?: return null
                val mime = when {
                    fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                    fileName.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
                    else -> "application/octet-stream"
                }

                var name = fileName
                var n = 1
                while (tree.listFiles().any { it.name == name }) {
                    val base = name.substringBeforeLast(".")
                    val ext = name.substringAfterLast(".", "")
                    name = if (ext.isNotEmpty()) "$base ($n).$ext" else "$base ($n)"
                    n++
                }

                // The original strips the extension before creating the document.
                val finalName = name.substringBeforeLast(".")
                val uri = try {
                    DocumentsContract.createDocument(context.contentResolver, tree.uri, mime, finalName)
                } catch (e: Exception) {
                    null
                }
                if (uri == null) {
                    Log.e(TAG, "Failed to create file in custom directory")
                    return null
                }
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream == null) {
                    Log.e(TAG, "Failed to open output stream")
                    return null
                }
                OutputResult(stream, uri.toString())
            }
        }
    }

    /**
     * Returns a path that does not yet exist, appending ` (n)` before the extension.
     * Mirrors the original's `i.j(File)`.
     */
    fun uniqueFile(file: File): File {
        if (!file.exists()) return file
        val nameWithoutExtension = file.nameWithoutExtension
        val extension = file.extension
        var i = 1
        var candidate: File
        do {
            candidate = File(file.parentFile, "$nameWithoutExtension ($i).$extension")
            i++
        } while (candidate.exists())
        return candidate
    }

    /**
     * Deletes a downloaded or partial file. SAF URIs are deleted through the
     * DocumentsContract; everything else is deleted as a plain file. Errors are swallowed.
     * Mirrors the original's `i.b(String)`.
     */
    fun deleteFile(path: String?) {
        if (path == null) return
        try {
            if (path.startsWith(CONTENT_PREFIX)) {
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(path))
                } catch (_: Exception) {
                }
            } else {
                val f = File(path)
                if (f.exists()) f.delete()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val TAG = "DownloadManager"
        private const val DEFAULT_DIR_NAME = "AIO Hub"
        private const val CONTENT_PREFIX = "content://"

        /** Illegal characters in a filename are replaced with `_`. */
        private val ILLEGAL_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|]")

        /** Runs of whitespace collapse to a single space. */
        private val WHITESPACE_RUN = Regex("\\s+")

        /** Filenames are truncated to this many characters after sanitisation. */
        private const val MAX_FILE_NAME_LENGTH = 200

        /**
         * Sanitises a filename: illegal characters become `_`, whitespace runs collapse to
         * one space, the result is trimmed and then truncated to 200 characters.
         * Mirrors the original's sanitisation in the enqueue pipeline.
         */
        fun sanitizeFileName(fileName: String): String =
            fileName
                .replace(ILLEGAL_FILENAME_CHARS, "_")
                .replace(WHITESPACE_RUN, " ")
                .trim()
                .take(MAX_FILE_NAME_LENGTH)
    }
}
