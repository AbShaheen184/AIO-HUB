package com.app.mlounge.emulator

import android.content.Context
import android.util.Log
import com.movies.premium.emulator.LibretroBridge
import com.movies.premium.emulator.LibretroNative

/**
 * Thin JNI shim around the prebuilt `libretrowrapper.so`.
 *
 * NOTE: this class deliberately keeps the `com.app.mlounge.emulator` package.
 * The library was compiled against `com.app.mlounge.emulator.LibretroCore` and
 * exports its entry points literally as
 * `Java_com_app_mlounge_emulator_LibretroCore_<name>`, so the class that
 * *declares* the native methods has to keep this exact fully qualified name —
 * renaming it would break every native call unless the C sources were
 * recompiled.
 *
 * The class therefore owns nothing but the JNI declarations: the state, the
 * audio track and the buffers live in [LibretroBridge], and the public API
 * below is forwarded to it.
 */
class LibretroCore(context: Context) : LibretroNative {

    companion object {
        private const val TAG = "LibretroCore"

        @JvmStatic
        var nativeAvailable: Boolean = false
            private set

        init {
            try {
                System.loadLibrary("retrowrapper")
                nativeAvailable = true
                Log.i(TAG, "Loaded retrowrapper native library")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "retrowrapper not available: ${e.message}")
            }
        }
    }

    /** The implementation that drives the native wrapper. */
    private val bridge = LibretroBridge(context, this)

    // --- the native entry points ------------------------------------------------
    // They are declared here on purpose: the symbol lookup of the prebuilt
    // library binds on the class that declares them, not on the call site.

    private external fun nativeDeinitCore()
    private external fun nativeGetAudioBuffer(buffer: ShortArray): Int
    private external fun nativeGetCurrentDimensions(): IntArray?
    private external fun nativeGetGameDimensions(): IntArray?
    private external fun nativeGetHwTextureId(): Int
    private external fun nativeGetSRAM(): ByteArray?
    private external fun nativeGetSaveStateSize(): Int
    private external fun nativeGetTiming(): DoubleArray?
    private external fun nativeGetVideoBuffer(buffer: IntArray)
    private external fun nativeInitCore(corePath: String, systemDir: String, saveDir: String): Boolean
    private external fun nativeIsHwFrameReady(): Boolean
    private external fun nativeIsHwRender(): Boolean
    private external fun nativeLoadGame(romPath: String): Boolean
    private external fun nativeLoadState(buffer: ByteArray): Boolean
    private external fun nativeOnGlContextReady()
    private external fun nativeReset()
    private external fun nativeRunFrame()
    private external fun nativeSaveState(buffer: ByteArray): Boolean
    private external fun nativeSetInputState(mask: Int)
    private external fun nativeSetSRAM(sram: ByteArray)
    private external fun nativeUnloadGame()

    // --- the same calls under the names the bridge uses --------------------------

    override fun bindCore(corePath: String, systemDir: String, saveDir: String): Boolean =
        nativeInitCore(corePath, systemDir, saveDir)

    override fun releaseCore() = nativeDeinitCore()

    override fun loadRom(romPath: String): Boolean = nativeLoadGame(romPath)

    override fun unloadRom() = nativeUnloadGame()

    override fun step() = nativeRunFrame()

    override fun resetCore() = nativeReset()

    override fun copyVideo(out: IntArray) = nativeGetVideoBuffer(out)

    override fun copyAudio(out: ShortArray): Int = nativeGetAudioBuffer(out)

    override fun gameSize(): IntArray? = nativeGetGameDimensions()

    override fun frameSize(): IntArray? = nativeGetCurrentDimensions()

    override fun gameTiming(): DoubleArray? = nativeGetTiming()

    override fun saveStateSize(): Int = nativeGetSaveStateSize()

    override fun writeSaveState(buffer: ByteArray): Boolean = nativeSaveState(buffer)

    override fun readSaveState(buffer: ByteArray): Boolean = nativeLoadState(buffer)

    override fun readSaveRam(): ByteArray? = nativeGetSRAM()

    override fun writeSaveRam(sram: ByteArray) = nativeSetSRAM(sram)

    override fun setButtonMask(mask: Int) = nativeSetInputState(mask)

    override fun glContextReady() = nativeOnGlContextReady()

    override fun hwTextureId(): Int = nativeGetHwTextureId()

    override fun hwRenderActive(): Boolean = nativeIsHwRender()

    override fun hwFrameReady(): Boolean = nativeIsHwFrameReady()

    // --- the public API, forwarded to the implementation --------------------------

    fun initCore(corePath: String): Boolean {
        if (!nativeAvailable) return false
        return bridge.initCore(corePath)
    }

    fun loadGame(romPath: String): Boolean = bridge.loadGame(romPath)

    fun runFrame() = bridge.runFrame()

    fun setInput(buttonMask: Int) = bridge.setInput(buttonMask)

    fun saveState(): ByteArray? = bridge.saveState()

    fun loadState(state: ByteArray): Boolean = bridge.loadState(state)

    fun getSRAM(): ByteArray? = bridge.getSRAM()

    fun setSRAM(sram: ByteArray) = bridge.setSRAM(sram)

    fun reset() = bridge.reset()

    fun isHwRender(): Boolean = bridge.isHwRender()

    fun isHwFrameReady(): Boolean = bridge.isHwFrameReady()

    fun getHwTextureId(): Int = bridge.getHwTextureId()

    fun onGlContextReady() = bridge.onGlContextReady()

    fun getVideoBuffer(): IntArray = bridge.getVideoBuffer()

    fun getVideoWidth(): Int = bridge.getVideoWidth()

    fun getVideoHeight(): Int = bridge.getVideoHeight()

    fun getGameLoaded(): Boolean = bridge.getGameLoaded()

    fun getCoreFps(): Double = bridge.getCoreFps()

    fun getCoreSampleRate(): Double = bridge.getCoreSampleRate()

    fun shutdown() = bridge.shutdown()
}
