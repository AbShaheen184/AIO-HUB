package com.movies.premium.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.ui.viewmodel.SettingsViewModel
import java.io.File
import java.util.Locale
import com.movies.premium.ui.tv.focusRing

/**
 * The download folder.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.h0.i` (the
 * volume picker and the folder browser) and `...settings.c` (the browser
 * body), together with `...settings.b` (the "New Folder" button) and
 * `...settings.f0` (the folder creation callback). The original's default
 * download folder is `Downloads/CinemaHQ`.
 */
@Composable
fun DownloadSettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val downloadDir by viewModel.downloadDir.collectAsStateWithLifecycle()
    var showBrowser by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsFileRow(
            title = "Download location",
            subtitle = downloadDir ?: DEFAULT_DOWNLOAD_DIR,
            icon = Icons.Filled.FolderOpen,
            onClick = { showBrowser = true },
        )
    }

    if (showBrowser) {
        FolderBrowserDialog(
            initialPath = downloadDir,
            onDismiss = { showBrowser = false },
            onSelected = { folder ->
                showBrowser = false
                viewModel.setDownloadDir(folder.absolutePath)
            },
        )
    }
}

/**
 * The storage section: the cache and the "all files access" permission.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.d0`, which clears
 * the cache (showing "Cache Cleared" or "There was an error clearing the
 * cache") and opens `android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`.
 * The size helpers mirror the original's `h0.t` (the recursive directory size)
 * and `h0.u` (the cache size).
 */
@Composable
fun StorageSettings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var cacheSize by remember { mutableStateOf(cacheSizeOf(context)) }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsFileRow(
            title = "Cache",
            subtitle = cacheSize,
            icon = Icons.Filled.CleaningServices,
            onClick = { cacheSize = clearCache(context, cacheSize) },
        )
        SettingsActionRow(
            label = "Clear",
            icon = Icons.Filled.Delete,
            onClick = { cacheSize = clearCache(context, cacheSize) },
        )
        SettingsActionRow(
            label = "Manage all files access",
            icon = Icons.Filled.Folder,
            onClick = { requestAllFilesAccess(context) },
        )
    }
}

/**
 * The folder browser.
 *
 * Mirrors the original's `h0.i` (the volume picker and the browser) and
 * `com.app.mlounge.ui.screens.settings.c` (the browser body). The volumes are
 * labelled "Internal" and "External Drive", the folders are listed, the free
 * space is shown and the user either walks up with "Back" or confirms the
 * current folder with "Select This Folder".
 */
@Composable
private fun FolderBrowserDialog(
    initialPath: String?,
    onDismiss: () -> Unit,
    onSelected: (File) -> Unit,
) {
    val context = LocalContext.current
    val volumes = remember { storageVolumes(context) }
    var current by remember {
        mutableStateOf(initialPath?.let { File(it) } ?: volumes.firstOrNull()?.first)
    }
    var newFolderVisible by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }

    val folders = remember(current?.absolutePath, refresh) {
        current?.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        title = {
            Text(text = current?.name ?: "Storage")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            val parent = current?.parentFile
                            if (parent != null && parent.absolutePath != current?.absolutePath) {
                                current = parent
                            }
                        },
                        enabled = current?.parentFile?.absolutePath != current?.absolutePath,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = current?.absolutePath.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { newFolderVisible = true }) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = "New Folder",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                HorizontalDivider()

                if (folders.isEmpty()) {
                    Text(
                        text = "Empty folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(folders) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { current = folder }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = freeSpaceLabel(current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { current?.let(onSelected) },
                enabled = current != null,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Select This Folder",
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { newFolderVisible = true }) { Text("New Folder") }
            }
        },
    )

    if (newFolderVisible) {
        AlertDialog(
            onDismissRequest = { newFolderVisible = false },
            modifier = Modifier.focusRing(),
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("New Folder") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parent = current
                        val name = newFolderName.trim()
                        newFolderVisible = false
                        newFolderName = ""
                        if (name.isNotEmpty()) {
                            if (parent == null || !parent.canWrite()) {
                                Toast.makeText(
                                    context,
                                    "Storage permission required to create folders",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                val created = File(parent, name)
                                if (created.mkdirs() || created.isDirectory) {
                                    current = created
                                    refresh++
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Could not create folder",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { newFolderVisible = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * The storage volumes of the device.
 *
 * Mirrors the original's `h0.i`, which labels the primary storage "Internal"
 * and every other volume with its description, falling back to
 * "External Drive".
 */
private fun storageVolumes(context: Context): List<Pair<File, String>> {
    val volumes = mutableListOf<Pair<File, String>>()
    val internal = Environment.getExternalStorageDirectory()
    if (internal.exists()) volumes += internal to "Internal"
    val manager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
    manager?.storageVolumes?.forEach { volume ->
        if (volume.isPrimary) return@forEach
        val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { volume.directory }.getOrNull()
        } else {
            runCatching {
                File(volume.javaClass.getMethod("getPath").invoke(volume) as String)
            }.getOrNull()
        }
        if (directory != null && directory.exists() && directory.canRead()) {
            val description = runCatching { volume.getDescription(context) }.getOrNull()
                ?: "External Drive"
            if (volumes.none { it.first.absolutePath == directory.absolutePath }) {
                volumes += directory to description
            }
        }
    }
    return volumes
}

/**
 * The size of a directory, in the original's format.
 *
 * Mirrors the original's `h0.t` (the recursive size) and `h0.u` (the
 * " B" / " KB" / "%.1f MB" / "%.2f GB" formatting).
 */
private fun directorySize(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    val children = file.listFiles() ?: return 0L
    var total = 0L
    children.forEach { total += directorySize(it) }
    return total
}

/** The size of the app cache, formatted the way the original formats it. */
private fun cacheSizeOf(context: Context): String {
    val bytes = directorySize(context.cacheDir)
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        bytes < 1024L * 1024L * 1024L ->
            String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else ->
            String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * The free space of the current volume, in the original's format.
 *
 * Mirrors the original's "%.1f MB free" / "%.1f GB free" / " KB free" label.
 */
private fun freeSpaceLabel(folder: File?): String {
    if (folder == null) return ""
    return try {
        val statFs = StatFs(folder.absolutePath)
        val free = statFs.blockSizeLong * statFs.availableBlocksLong
        when {
            free < 1024L * 1024L -> "${free / 1024L} KB free"
            free < 1024L * 1024L * 1024L ->
                String.format(Locale.US, "%.1f MB free", free / (1024.0 * 1024.0))
            else ->
                String.format(Locale.US, "%.1f GB free", free / (1024.0 * 1024.0 * 1024.0))
        }
    } catch (e: Exception) {
        ""
    }
}

/**
 * Clears the app cache and returns the new size.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.d0`, which shows
 * "Cache Cleared" or "There was an error clearing the cache".
 */
private fun clearCache(context: Context, currentSize: String): String = try {
    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    Toast.makeText(context, "Cache Cleared", Toast.LENGTH_SHORT).show()
    cacheSizeOf(context)
} catch (e: Exception) {
    Toast.makeText(context, "There was an error clearing the cache", Toast.LENGTH_SHORT).show()
    currentSize
}

/**
 * Opens the "all files access" settings screen.
 *
 * Mirrors the original's `com.app.mlounge.ui.screens.settings.d0`, which opens
 * `android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` on Android 11 and
 * newer and `android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION` before that.
 */
private fun requestAllFilesAccess(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    } else {
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (ignored: Exception) {
            // Nothing we can do; the original ignores this too.
        }
    }
}

/** The default download folder, as the original spells it. */
private const val DEFAULT_DOWNLOAD_DIR = "Downloads/AIO Hub"
