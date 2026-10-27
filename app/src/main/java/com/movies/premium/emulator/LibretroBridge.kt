package com.movies.premium.emulator

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.io.File
import kotlin.math.max

/**
 * The libretro core the emulator runs.
 *
 * It owns what the original's `LibretroCore` owned — the system and save
 * directories, the audio track, the video buffer — and drives the native
 * wrapper through [LibretroNative]. The JNI declarations themselves stay in
 * `com.app.mlounge.emulator.LibretroCore`, because the prebuilt
 * `libretrowrapper.so` binds its symbols to that exact class.
 */
class LibretroBridge(
    context: Context,
    private val native: LibretroNative,
) {

    companion object {
        private const val TAG = "LibretroCore"

        /** The frame size the core uses until it reports its own. */
        const val SCREEN_WIDTH = 256
        const val SCREEN_HEIGHT = 224

        /** The frame size of the PSP core, which renders at a fixed size. */
        const val PSP_SCREEN_WIDTH = 480
        const val PSP_SCREEN_HEIGHT = 272

        // libretro button ids
        const val BUTTON_B = 0
        const val BUTTON_Y = 1
        const val BUTTON_SELECT = 2
        const val BUTTON_START = 3
        const val BUTTON_UP = 4
        const val BUTTON_DOWN = 5
        const val BUTTON_LEFT = 6
        const val BUTTON_RIGHT = 7
        const val BUTTON_A = 8
        const val BUTTON_X = 9
        const val BUTTON_L = 10
        const val BUTTON_R = 11

        private const val DEFAULT_VIDEO_WIDTH = SCREEN_WIDTH
        private const val DEFAULT_VIDEO_HEIGHT = SCREEN_HEIGHT

        /**
         * libretro mixes its audio to stereo, so the output track is stereo.
         * The channel configuration is a channel *mask*
         * (`AudioTrack.CHANNEL_OUT_STEREO` = 12) — passing a plain channel
         * count is what made the original's track fail to initialise, and
         * because the original built the track inside `initCore()` that took
         * the whole emulator down before the core was even loaded.
         */
        private const val AUDIO_SAMPLE_RATE = 32040
    }

    private val systemDir: String
    private val saveDir: String

    private var activeCorePath: String? = null
    private var coreLoaded = false
    private var gameLoaded = false

    private var videoBuffer: IntArray =
        IntArray(DEFAULT_VIDEO_WIDTH * DEFAULT_VIDEO_HEIGHT)
    private var videoWidth = DEFAULT_VIDEO_WIDTH
    private var videoHeight = DEFAULT_VIDEO_HEIGHT

    private var audioBuffer: ShortArray = ShortArray(2136)
    private var audioTrack: AudioTrack? = null
    private var coreFps = 60.0
    private var coreSampleRate = AUDIO_SAMPLE_RATE.toDouble()

    init {
        val filesDir = context.filesDir
        val system = File(filesDir, "system").apply { mkdirs() }
        val saves = File(filesDir, "saves").apply { mkdirs() }
        systemDir = system.absolutePath
        saveDir = saves.absolutePath
    }

    /**
     * Starts the audio output.
     *
     * A missing or refused output track must not take the emulator down — the
     * game is still playable without sound — so every failure here is logged
     * and the emulation continues.
     */
    private fun initAudio() {
        val rate = coreSampleRate.toInt().coerceIn(8000, 48000)
        val minBuffer = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val wanted = max(minBuffer, (rate / coreFps * 2).toInt() * 16)
        try {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(wanted)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also { it.play() }
        } catch (e: Exception) {
            Log.w(TAG, "No audio output, continuing without sound: ${e.message}")
            audioTrack = null
        }
    }

    fun initCore(corePath: String): Boolean {
        return try {
            initAudio()
            activeCorePath = corePath
            coreLoaded = native.bindCore(corePath, systemDir, saveDir)
            Log.i(
                TAG,
                if (coreLoaded) "Core loaded: $corePath" else "Failed to load core",
            )
            coreLoaded
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing core", e)
            false
        }
    }

    fun loadGame(romPath: String): Boolean {
        if (!coreLoaded) return false
        gameLoaded = native.loadRom(romPath)
        if (gameLoaded) {
            val isPpsspp = activeCorePath?.contains("ppsspp", ignoreCase = true) == true
            val dims = native.gameSize()
            if (dims != null) {
                if (dims.size >= 2 && dims[0] > 0 && dims[1] > 0) {
                    setVideoSize(dims[0], dims[1])
                } else if (isPpsspp) {
                    setVideoSize(480, 272)
                }
            }
            val timing = native.gameTiming()
            if (timing != null && timing.size >= 2) {
                coreFps = timing[0]
                coreSampleRate = timing[1]
                Log.i(TAG, "Core timing: $coreFps fps, $coreSampleRate Hz")
                audioTrack?.let {
                    it.stop()
                    it.release()
                }
                audioTrack = null
                initAudio()
                audioBuffer = ShortArray(((coreSampleRate / coreFps).toInt() * 8).coerceAtLeast(4096))
            }
            Log.i(TAG, "Game loaded: $romPath (${videoWidth}x$videoHeight)")
        }
        return gameLoaded
    }

    fun runFrame() {
        if (!gameLoaded) return
        native.step()
        val dims = native.frameSize()
        if (dims != null && dims.size >= 2 && dims[0] > 0 && dims[1] > 0) {
            setVideoSize(dims[0], dims[1])
        }
        native.copyVideo(videoBuffer)
        val samples = native.copyAudio(audioBuffer)
        if (samples <= 0) return
        val track = audioTrack ?: return
        val shorts = minOf(samples * 2, audioBuffer.size)
        track.write(audioBuffer, 0, shorts, 0)
    }

    /**
     * Reallocates the frame buffer when the core changes its resolution.
     *
     * The buffer is exactly one frame: the native side copies a whole frame
     * into it and checks the size, so a buffer that is larger than the frame
     * is left untouched by the copy.
     */
    private fun setVideoSize(width: Int, height: Int) {
        if (width == videoWidth && height == videoHeight) return
        videoWidth = width
        videoHeight = height
        videoBuffer = IntArray(width * height)
        Log.i(TAG, "Video size: ${width}x$height")
    }

    fun setInput(buttonMask: Int) {
        native.setButtonMask(buttonMask)
    }

    fun saveState(): ByteArray? {
        if (!gameLoaded) return null
        val size = native.saveStateSize()
        if (size <= 0) return null
        val buffer = ByteArray(size)
        return if (native.writeSaveState(buffer)) buffer else null
    }

    fun loadState(state: ByteArray): Boolean {
        if (!gameLoaded) return false
        return native.readSaveState(state)
    }

    fun getSRAM(): ByteArray? = if (gameLoaded) native.readSaveRam() else null

    fun setSRAM(sram: ByteArray) {
        if (gameLoaded) native.writeSaveRam(sram)
    }

    fun reset() {
        if (gameLoaded) native.resetCore()
    }

    fun isHwRender(): Boolean = native.hwRenderActive()

    fun isHwFrameReady(): Boolean = native.hwFrameReady()

    fun getHwTextureId(): Int = native.hwTextureId()

    fun onGlContextReady() {
        native.glContextReady()
    }

    fun getVideoBuffer(): IntArray = videoBuffer

    fun getVideoWidth(): Int = videoWidth

    fun getVideoHeight(): Int = videoHeight

    fun getGameLoaded(): Boolean = gameLoaded

    fun getCoreFps(): Double = coreFps

    fun getCoreSampleRate(): Double = coreSampleRate

    fun shutdown() {
        audioTrack?.let {
            it.stop()
            it.release()
        }
        audioTrack = null
        if (gameLoaded) {
            native.unloadRom()
            gameLoaded = false
        }
        if (coreLoaded) {
            native.releaseCore()
            coreLoaded = false
        }
    }
}
