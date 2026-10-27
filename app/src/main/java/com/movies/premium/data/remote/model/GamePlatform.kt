package com.movies.premium.data.remote.model

/**
 * The retro platforms the original ships a libretro core for.
 *
 * Mirrors the original's `com.app.mlounge.data.remote.model.GamePlatform` enum —
 * the same three constants (`SNES`, `SEGA_GENESIS`, `NES`) with the same four
 * fields: the API path segment, the display name, the ROM file extension and the
 * name of the prebuilt core in `jniLibs/<abi>/`.
 *
 * The core name matters: `EmulatorActivity` loads it with
 * `System.loadLibrary(coreName.removeSuffix(".so"))`, so the value has to match the
 * file names that were copied out of the original APK.
 */
enum class GamePlatform(
    val apiPath: String,
    val displayName: String,
    val romExtension: String,
    val coreName: String,
) {
    SNES("snes", "SNES", ".sfc", "libsnes9x_libretro_android.so"),
    SEGA_GENESIS("sega-genesis", "Sega Genesis", ".md", "libgenesis_plus_gx_libretro_android.so"),
    NES("nes", "NES", ".nes", "libfceumm_libretro_android.so"),
    ;

    companion object {
        /**
         * Resolves a platform from the value the catalogue API reports.
         *
         * The API's `platforms` list uses the [apiPath] spelling (`snes`, `nes`,
         * `sega-genesis`); the enum constant names are accepted as a fallback.
         */
        fun fromApiPath(path: String?): GamePlatform {
            if (path.isNullOrBlank()) return NES
            val normalized = path.trim().lowercase()
            return entries.firstOrNull { it.apiPath == normalized }
                ?: entries.firstOrNull { it.name.lowercase() == normalized }
                ?: entries.firstOrNull { it.displayName.lowercase() == normalized }
                ?: NES
        }
    }
}
