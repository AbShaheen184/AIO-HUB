package com.movies.premium.data.remote.ntv

import com.movies.premium.util.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * Unwraps the PNG-steganography payloads the DLHD live servers serve.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.ntv.a` (R8 name `a`).
 *
 * The payload is stored in the least-significant bits of a PNG's pixels: the
 * raw RGB buffer is prefixed with the magic `TIKTIKPX`, a big-endian length
 * and a gzip stream that carries the real MPEG-TS bytes.
 */
object DlhdUnwrap {

    /** The 8-byte magic that prefixes the steganographic payload. */
    val MAGIC: ByteArray = "TIKTIKPX".toByteArray(Charsets.US_ASCII)

    private const val TAG = "DlhdUnwrap"
    private const val MAX_PNG_BYTES = 16_777_216
    private const val MAX_PIXELS = 67_108_864

    /** Inflates a zlib stream. */
    fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 2)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    /** `true` when [data] starts with the PNG signature. */
    fun isPng(data: ByteArray): Boolean =
        data.size > 8 &&
            data[0] == 0x89.toByte() &&
            data[1] == 'P'.code.toByte() &&
            data[2] == 'N'.code.toByte() &&
            data[3] == 'G'.code.toByte()

    /** Reads a four-byte big-endian integer. */
    fun readIntBigEndian(data: ByteArray, offset: Int): Int =
        (data[offset + 3].toInt() and 0xFF) or
            ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8)

    /**
     * Entry point.
     *
     * Returns the MPEG-TS payload hidden in a PNG, the raw bytes of a TS
     * container (recognised by its sync bytes), or `null`.
     */
    fun unwrap(data: ByteArray): ByteArray? {
        try {
            if (isPng(data)) {
                if (data.size <= MAX_PNG_BYTES) return unwrapPng(data)
                Logger.d(TAG, "PNG too large (${data.size}B), refusing")
                return null
            }
            if (data.size >= 376) {
                var i = 0
                while (i + 376 <= data.size && i < 1024) {
                    if (data[i] == 0x47.toByte() &&
                        data[i + 188] == 0x47.toByte() &&
                        data[i + 376] == 0x47.toByte()
                    ) {
                        return data
                    }
                    i++
                }
            }
            return null
        } catch (t: Throwable) {
            Logger.d(TAG, "unwrap failed (${t.javaClass.simpleName}): ${t.message}")
            return null
        }
    }

    /**
     * Parses a PNG and extracts the steganographic payload.
     *
     * Only 8-bit RGB (colour type 2) and 8-bit RGBA (colour type 6) images
     * are supported — the same restriction the original has.
     */
    fun unwrapPng(data: ByteArray): ByteArray? {
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = 0
        val idat = ByteArrayOutputStream()

        var off = 8
        while (off + 8 <= data.size) {
            val length = readIntBigEndian(data, off)
            if (length < 0) return null
            val dataStart = off + 8
            if (dataStart + length > data.size) return null
            val type = String(data, off + 4, 4, Charsets.US_ASCII)
            when (type) {
                "IHDR" -> {
                    if (length < 13) return null
                    width = readIntBigEndian(data, dataStart)
                    height = readIntBigEndian(data, dataStart + 4)
                    bitDepth = data[dataStart + 8].toInt() and 0xFF
                    colorType = data[dataStart + 9].toInt() and 0xFF
                    if ((data[dataStart + 12].toInt() and 0xFF) != 0) return null
                }

                "IDAT" -> idat.write(data, dataStart, length)
                "IEND" -> {
                    off = -1
                    break
                }
            }
            off = dataStart + length + 4
        }

        if (width <= 0 || height <= 0 || bitDepth != 8 || (colorType != 2 && colorType != 6)) {
            Logger.d(TAG, "unsupported PNG layout ${width}x${height} d$bitDepth ct$colorType")
            return null
        }

        val pixels = width.toLong() * height.toLong() * 3L
        if (pixels > MAX_PIXELS) {
            Logger.d(TAG, "implausible pixel size $pixels")
            return null
        }

        val bytesPerPixel = if (colorType == 6) 4 else 3
        val stride = width * bytesPerPixel
        val inflated = inflate(idat.toByteArray())

        val rgb = ByteArray(width * height * 3)
        var current = ByteArray(stride)
        var previous = ByteArray(stride)
        var pos = 0
        for (y in 0 until height) {
            if (pos + 1 + stride > inflated.size) return null
            val filter = inflated[pos].toInt() and 0xFF
            pos++
            for (x in 0 until stride) {
                val raw = inflated[pos + x].toInt() and 0xFF
                val a = if (x >= bytesPerPixel) current[x - bytesPerPixel].toInt() and 0xFF else 0
                val b = previous[x].toInt() and 0xFF
                val c = if (x >= bytesPerPixel) previous[x - bytesPerPixel].toInt() and 0xFF else 0
                val value = when (filter) {
                    0 -> raw
                    1 -> raw + a
                    2 -> raw + b
                    3 -> raw + (a + b) / 2
                    4 -> raw + paeth(a, b, c)
                    else -> return null
                }
                current[x] = (value and 0xFF).toByte()
            }
            pos += stride
            var out = y * width * 3
            for (x in 0 until width) {
                val base = x * bytesPerPixel
                rgb[out] = current[base]
                rgb[out + 1] = current[base + 1]
                rgb[out + 2] = current[base + 2]
                out += 3
            }
            val swap = previous
            previous = current
            current = swap
        }

        if (rgb.size < 12) return null
        for (i in MAGIC.indices) {
            if (rgb[i] != MAGIC[i]) {
                Logger.d(TAG, "pixel magic mismatch")
                return null
            }
        }

        val payloadLen = readIntBigEndian(rgb, 8)
        if (payloadLen <= 0 || payloadLen > rgb.size - 12) return null
        if (rgb[12] != 0x1F.toByte() || rgb[13] != 0x8D.toByte()) return null

        val gzipped = rgb.copyOfRange(12, 12 + payloadLen)
        val ts = gunzip(gzipped) ?: return null
        if (ts.isEmpty() || (ts[0].toInt() and 0xFF) != 0x47) return null
        return ts
    }

    private fun gunzip(data: ByteArray): ByteArray? = try {
        GZIPInputStream(ByteArrayInputStream(data)).use { input ->
            val out = ByteArrayOutputStream(data.size * 2)
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    } catch (_: Exception) {
        null
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }
}
