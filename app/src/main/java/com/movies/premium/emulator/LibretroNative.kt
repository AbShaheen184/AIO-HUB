package com.movies.premium.emulator

/**
 * The libretro entry points the emulator drives.
 *
 * The implementation is `com.app.mlounge.emulator.LibretroCore`: the prebuilt
 * `libretrowrapper.so` exports its JNI symbols bound to that exact class, so the
 * shim there owns the `external` declarations and forwards them to this seam.
 * Keeping the seam here lets the rest of the emulator talk to the native
 * library without knowing anything about the original's package.
 */
interface LibretroNative {

    /**
     * Loads the core at [corePath].
     *
     * @param systemDir where the core looks for its data files (BIOS, …).
     * @param saveDir where the core keeps its own saves.
     */
    fun bindCore(corePath: String, systemDir: String, saveDir: String): Boolean

    /** Unloads the core. */
    fun releaseCore()

    /** Loads the ROM at [romPath] into the bound core. */
    fun loadRom(romPath: String): Boolean

    /** Unloads the running game, the core stays loaded. */
    fun unloadRom()

    /** Advances the emulation by one frame. */
    fun step()

    /** Resets the running game. */
    fun resetCore()

    /** Copies the current video frame into [out] as packed ARGB pixels. */
    fun copyVideo(out: IntArray)

    /** Copies the current audio block into [out]; returns the number of samples. */
    fun copyAudio(out: ShortArray): Int

    /** The frame size the core produces, or `null` when it is not known yet. */
    fun gameSize(): IntArray?

    /** The size of the frame that was just produced, or `null` when unknown. */
    fun frameSize(): IntArray?

    /** `[fps, sampleRate]` of the running core. */
    fun gameTiming(): DoubleArray?

    /** The size of the serialized state, or 0 when the core has none. */
    fun saveStateSize(): Int

    /** Writes the serialized state into [buffer]. */
    fun writeSaveState(buffer: ByteArray): Boolean

    /** Restores the serialized state from [buffer]. */
    fun readSaveState(buffer: ByteArray): Boolean

    /** The core's SRAM, or `null` when it has none. */
    fun readSaveRam(): ByteArray?

    /** Hands the SRAM back to the core. */
    fun writeSaveRam(sram: ByteArray)

    /** Sets the libretro button mask for the next frame. */
    fun setButtonMask(mask: Int)

    /** Tells the core that the OpenGL context is up (the hardware render path). */
    fun glContextReady()

    /** The texture the core renders into, or 0 when it renders in software. */
    fun hwTextureId(): Int

    /** True when the core renders through OpenGL instead of a software buffer. */
    fun hwRenderActive(): Boolean

    /** True when a hardware rendered frame is waiting to be blitted. */
    fun hwFrameReady(): Boolean
}
