package com.movies.premium.data.download

import java.io.OutputStream

/**
 * The destination an in-flight download writes to.
 *
 * [filePath] is an absolute filesystem path for local directories, but a `content://…`
 * URI string when the download target is a SAF tree.
 */
data class OutputResult(
    val outputStream: OutputStream,
    val filePath: String,
)
