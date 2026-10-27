package com.movies.premium.emulator

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import com.app.mlounge.emulator.LibretroCore
import com.movies.premium.R
import com.movies.premium.ui.tv.isTvDevice
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

/**
 * Hosts a libretro core instance and renders it through [EmulatorRenderer].
 *
 * Expects `rom_path`, `game_title` and `core_name` extras.
 *
 * The activity is a plain [ComponentActivity] — like the original, which
 * extended `androidx.activity.ComponentActivity` — because the emulator runs
 * with the platform's `Theme.NoTitleBar.Fullscreen`, and `AppCompatActivity`
 * refuses any theme that is not an AppCompat descendant.
 */
class EmulatorActivity : ComponentActivity() {

    companion object {
        private const val TAG = "EmulatorActivity"
        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_GAME_TITLE = "game_title"
        const val EXTRA_CORE_NAME = "core_name"
        private const val DEFAULT_CORE = "libsnes9x_libretro_android.so"
        private const val PREFS = "emu_prefs"

        /** libretro button mask, built from the per-button press state. */
        private fun maskOf(states: BooleanArray): Int {
            var mask = 0
            for (i in states.indices) {
                if (states[i]) mask = mask or (1 shl i)
            }
            return mask
        }
    }

    private lateinit var core: LibretroCore
    private lateinit var renderer: EmulatorRenderer
    private lateinit var glSurfaceView: GLSurfaceView
    private var controlsOverlay: ControlsOverlay? = null
    private var menuButtonView: ImageButton? = null
    private var vibrator: Vibrator? = null

    @Volatile
    private var running = false
    private var autoSaveThread: Thread? = null

    /**
     * Raised by the auto-save timer thread, consumed by the renderer callback
     * on the GL thread — the core must not be touched from two threads at once.
     */
    @Volatile
    private var pendingAutoSave = false

    private val buttonState = BooleanArray(12)
    private val keyMapping = HashMap<Int, Int>()

    private var aspectRatio = "fit"
    private var showTouchControls = true
    private var controlsOpacity = 0.5f
    private var autoSaveEnabled = false
    private var vibrationEnabled = false
    private var autoLoadSave = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        loadSettings()

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val romPath = intent.getStringExtra(EXTRA_ROM_PATH)
        val gameTitle = intent.getStringExtra(EXTRA_GAME_TITLE) ?: "Game"
        if (romPath.isNullOrBlank() || !File(romPath).exists()) {
            Log.e(TAG, "ROM not found: $romPath")
            finish()
            return
        }
        verifyRomFile(romPath)

        var coreName = intent.getStringExtra(EXTRA_CORE_NAME) ?: DEFAULT_CORE
        if (coreName.contains("ppsspp", ignoreCase = true)) {
            bootstrapPpssppAssets()
        }

        core = LibretroCore(this)
        val corePath = File(applicationInfo.nativeLibraryDir, coreName).absolutePath
        if (!core.initCore(corePath)) {
            Log.e(TAG, "Failed to init core")
            finish()
            return
        }
        if (!core.loadGame(romPath)) {
            Log.e(TAG, "Failed to load game")
            core.shutdown()
            finish()
            return
        }

        val romName = File(romPath).nameWithoutExtension
        val srmFile = File(filesDir, "saves/$romName.srm")
        if (srmFile.exists()) core.setSRAM(srmFile.readBytes())

        if (autoLoadSave) {
            val stateFile = File(filesDir, "saves/$romName.state")
            if (stateFile.exists()) core.loadState(stateFile.readBytes())
        }

        renderer = EmulatorRenderer(
            core = core,
            inputProvider = { maskOf(buttonState) },
            onFrameComplete = {
                // The original performs the save here, on the GL thread, and
                // only the timer thread raises the flag — the core must not be
                // serialised while it is running a frame.
                if (pendingAutoSave) {
                    pendingAutoSave = false
                    saveStateToFile()
                }
            },
        )
        renderer.aspectRatioMode = aspectRatio

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            // The original uses `setRenderMode(1)`, which is
            // `RENDERMODE_CONTINUOUSLY`: the surface redraws by itself and
            // nobody ever calls `requestRender()`.
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        val root = FrameLayout(this)
        root.addView(glSurfaceView, FrameLayout.LayoutParams(-1, -1))

        if (showTouchControls) {
            val overlay = ControlsOverlay(this, controlsOpacity) { button, pressed ->
                buttonState[button] = pressed
                if (pressed && vibrationEnabled) vibrate()
            }
            controlsOverlay = overlay
            root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        }

        val size = (resources.displayMetrics.density * 36).toInt()
        val menuButton = ImageButton(this).apply {
            setImageResource(R.drawable.menu_button)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0)
            setPadding(8, 8, 8, 8)
            alpha = controlsOpacity
            setOnClickListener { showMenu() }
        }
        menuButtonView = menuButton
        val lp = FrameLayout.LayoutParams(
            size,
            size,
            android.view.Gravity.BOTTOM or android.view.Gravity.END,
        )
        lp.bottomMargin = (resources.displayMetrics.heightPixels * 0.04f).toInt()
        root.addView(menuButton, lp)

        setContentView(root)
        title = gameTitle
        running = true

        if (autoSaveEnabled) startAutoSave()
    }

    /**
     * PPSSPP needs a handful of data files under `files/system/PPSSPP`; the
     * original ships them in assets and copies them out on first run.
     */
    private fun bootstrapPpssppAssets() {
        val prefs = getSharedPreferences("ppsspp_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("ppsspp_assets_bootstrapped", false)) {
            Log.d("PPSSPPAssets", "PPSSPP assets already bootstrapped")
            return
        }
        try {
            val root = File(filesDir, "system/PPSSPP")
            root.mkdirs()
            val lang = File(root, "lang")
            lang.mkdirs()
            copyAsset("ppsspp/lang/en_US.ini", File(lang, "en_US.ini"))
            copyAsset("ppsspp/ppge_atlas.zim", File(root, "ppge_atlas.zim"))
            val vfpu = File(root, "vfpu")
            vfpu.mkdirs()
            copyAsset("ppsspp/vfpu/vfpu_sin_lut8192.dat", File(vfpu, "vfpu_sin_lut8192.dat"))
            copyAsset("ppsspp/compat.ini", File(root, "compat.ini"))
            prefs.edit().putBoolean("ppsspp_assets_bootstrapped", true).apply()
            Log.i("PPSSPPAssets", "PPSSPP assets bootstrapped successfully")
        } catch (e: Exception) {
            Log.e("PPSSPPAssets", "Failed to bootstrap PPSSPP assets: ${e.message}", e)
        }
    }

    private fun copyAsset(assetPath: String, target: File) {
        assets.open(assetPath).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun loadSettings() {
        val prefs: SharedPreferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        aspectRatio = prefs.getString("emu_aspect_ratio", "fit") ?: "fit"
        showTouchControls = prefs.getBoolean("emu_show_touch_controls", true)
        // A television has no touchscreen: the game is played with the
        // direction pad and the gamepad buttons of the remote, which the
        // activity already maps onto the buttons of the core. The on-screen
        // pad would only cover the picture, so it stays away.
        if (isTvDevice(this)) {
            showTouchControls = false
        }
        controlsOpacity = prefs.getFloat("emu_controls_opacity", 0.5f)
        autoSaveEnabled = prefs.getBoolean("emu_auto_save", false)
        vibrationEnabled = prefs.getBoolean("emu_vibration", false)
        autoLoadSave = prefs.getBoolean("emu_auto_load_save", false)
        loadKeyMapping(prefs.getString("emu_key_mapping", "") ?: "")
    }

    private fun loadKeyMapping(json: String) {
        keyMapping.clear()
        if (json.isBlank()) return
        try {
            val obj = JSONObject(json)
            for (name in obj.keys()) {
                val button = when (name) {
                    "BUTTON_B" -> LibretroBridge.BUTTON_B
                    "BUTTON_Y" -> LibretroBridge.BUTTON_Y
                    "BUTTON_SELECT" -> LibretroBridge.BUTTON_SELECT
                    "BUTTON_START" -> LibretroBridge.BUTTON_START
                    "BUTTON_UP" -> LibretroBridge.BUTTON_UP
                    "BUTTON_DOWN" -> LibretroBridge.BUTTON_DOWN
                    "BUTTON_LEFT" -> LibretroBridge.BUTTON_LEFT
                    "BUTTON_RIGHT" -> LibretroBridge.BUTTON_RIGHT
                    "BUTTON_A" -> LibretroBridge.BUTTON_A
                    "BUTTON_X" -> LibretroBridge.BUTTON_X
                    "BUTTON_L" -> LibretroBridge.BUTTON_L
                    "BUTTON_R" -> LibretroBridge.BUTTON_R
                    else -> continue
                }
                keyMapping[obj.getInt(name)] = button
            }
        } catch (_: Exception) {
        }
    }

    /** Maps an Android key code to a libretro button, with sensible defaults. */
    private fun mapKeyToButton(keyCode: Int): Int? {
        keyMapping[keyCode]?.let { return it }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> LibretroBridge.BUTTON_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> LibretroBridge.BUTTON_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> LibretroBridge.BUTTON_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> LibretroBridge.BUTTON_RIGHT
            KeyEvent.KEYCODE_BUTTON_A -> LibretroBridge.BUTTON_A
            KeyEvent.KEYCODE_BUTTON_B -> LibretroBridge.BUTTON_B
            KeyEvent.KEYCODE_BUTTON_X -> LibretroBridge.BUTTON_X
            KeyEvent.KEYCODE_BUTTON_Y -> LibretroBridge.BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_L1 -> LibretroBridge.BUTTON_L
            KeyEvent.KEYCODE_BUTTON_R1 -> LibretroBridge.BUTTON_R
            KeyEvent.KEYCODE_BUTTON_START -> LibretroBridge.BUTTON_START
            KeyEvent.KEYCODE_BUTTON_SELECT -> LibretroBridge.BUTTON_SELECT
            // The keys of a keyboard and of a remote without a gamepad.
            // The original maps them the same way
            // (`emulator/EmulatorActivity.java`, `mapKeyToButton`).
            KeyEvent.KEYCODE_A -> LibretroBridge.BUTTON_A
            KeyEvent.KEYCODE_B -> LibretroBridge.BUTTON_B
            KeyEvent.KEYCODE_X -> LibretroBridge.BUTTON_X
            KeyEvent.KEYCODE_Y -> LibretroBridge.BUTTON_Y
            KeyEvent.KEYCODE_SPACE -> LibretroBridge.BUTTON_SELECT
            KeyEvent.KEYCODE_ENTER -> LibretroBridge.BUTTON_START
            else -> null
        }
    }

    private fun vibrate() {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(30)
        }
    }

    private fun romName(): String? {
        val path = intent.getStringExtra(EXTRA_ROM_PATH) ?: return null
        return File(path).nameWithoutExtension
    }

    private fun saveStateToFile() {
        val name = romName() ?: return
        val state = core.saveState() ?: return
        val dir = File(filesDir, "saves")
        dir.mkdirs()
        File(dir, "$name.state").writeBytes(state)
    }

    private fun loadStateFromFile() {
        val name = romName() ?: return
        val file = File(filesDir, "saves/$name.state")
        if (file.exists()) core.loadState(file.readBytes())
    }

    private fun startAutoSave() {
        autoSaveThread = Thread {
            while (running) {
                try {
                    Thread.sleep(60_000)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (running) pendingAutoSave = true
            }
        }.apply {
            name = "emu-autosave"
            isDaemon = true
            start()
        }
    }

    /** Select+Start together toggles the on-screen controls. */
    private fun checkSelectStartCombo() {
        val select = buttonState[LibretroBridge.BUTTON_SELECT]
        val start = buttonState[LibretroBridge.BUTTON_START]
        if (select && start) {
            val overlay = controlsOverlay
            if (overlay != null) {
                overlay.visibility = if (overlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
    }

    private fun isFromGamepad(event: KeyEvent): Boolean =
        event.deviceId != -1 && event.isFromSource(InputDeviceCompat.SOURCE_GAMEPAD)

    /** Minimal input-source constants so we don't pull in the whole compat shim. */
    private object InputDeviceCompat {
        const val SOURCE_GAMEPAD = 0x401
    }

    @SuppressLint("MissingSuperCall")
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDeviceCompat.SOURCE_GAMEPAD == 0) return super.onGenericMotionEvent(event)

        val pressed = BooleanArray(12)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        pressed[LibretroBridge.BUTTON_LEFT] = hatX < -0.5f
        pressed[LibretroBridge.BUTTON_RIGHT] = hatX > 0.5f
        pressed[LibretroBridge.BUTTON_UP] = hatY < -0.5f
        pressed[LibretroBridge.BUTTON_DOWN] = hatY > 0.5f

        val axisX = event.getAxisValue(MotionEvent.AXIS_X)
        val axisY = event.getAxisValue(MotionEvent.AXIS_Y)
        val axisL = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        val axisR = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)

        keyMapping[10001]?.let { pressed[it] = pressed[it] || axisY < -0.5f }
        keyMapping[10002]?.let { pressed[it] = pressed[it] || axisY > 0.5f }
        keyMapping[10003]?.let { pressed[it] = pressed[it] || axisX < -0.5f }
        keyMapping[10004]?.let { pressed[it] = pressed[it] || axisX > 0.5f }
        keyMapping[10005]?.let { pressed[it] = pressed[it] || axisX < -0.5f }
        keyMapping[10006]?.let { pressed[it] = pressed[it] || axisX > 0.5f }
        keyMapping[10007]?.let { pressed[it] = pressed[it] || axisX < -0.5f }
        keyMapping[10008]?.let { pressed[it] = pressed[it] || axisX > 0.5f }

        for (i in pressed.indices) buttonState[i] = pressed[i]
        checkSelectStartCombo()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && ::core.isInitialized) {
            saveStateToFile()
        }
        val button = mapKeyToButton(keyCode) ?: return super.onKeyDown(keyCode, event)
        buttonState[button] = true
        checkSelectStartCombo()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val button = mapKeyToButton(keyCode) ?: return super.onKeyUp(keyCode, event)
        buttonState[button] = false
        return true
    }

    override fun onPause() {
        super.onPause()
        if (::glSurfaceView.isInitialized) glSurfaceView.onPause()
        if (::core.isInitialized) saveStateToFile()
    }

    override fun onResume() {
        super.onResume()
        if (::glSurfaceView.isInitialized) glSurfaceView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        autoSaveThread?.interrupt()
        autoSaveThread = null
        if (::core.isInitialized) {
            saveStateToFile()
            core.shutdown()
        }
        if (::renderer.isInitialized) renderer.onDestroy()
    }

    /** Opens the emulator options dialog. */
    @SuppressLint("InflateParams")
    fun showMenu() {
        val options = arrayOf(
            "Aspect ratio: $aspectRatio",
            "Save state now",
            "Load state",
            "Reset game",
            "Auto-save: ${if (autoSaveEnabled) "on" else "off"}",
            "Touch controls: ${if (showTouchControls) "on" else "off"}",
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.emulator_menu)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cycleAspectRatio()
                    1 -> saveStateToFile()
                    2 -> loadStateFromFile()
                    3 -> core.reset()
                    4 -> {
                        autoSaveEnabled = !autoSaveEnabled
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("emu_auto_save", autoSaveEnabled)
                            .apply()
                        if (autoSaveEnabled) startAutoSave()
                    }

                    5 -> {
                        showTouchControls = !showTouchControls
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("emu_show_touch_controls", showTouchControls)
                            .apply()
                        controlsOverlay?.visibility =
                            if (showTouchControls) View.VISIBLE else View.GONE
                    }
                }
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun cycleAspectRatio() {
        aspectRatio = when (aspectRatio) {
            "fit" -> "stretch"
            "stretch" -> "4:3"
            "4:3" -> "16:9"
            else -> "fit"
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("emu_aspect_ratio", aspectRatio)
            .apply()
        renderer.aspectRatioMode = aspectRatio
    }

    /** Logs a warning for oversized or oddly-shaped ROMs, as the original does. */
    private fun verifyRomFile(romPath: String) {
        val file = File(romPath)
        if (!file.exists()) {
            Log.e(TAG, "ROM file does not exist: $romPath")
            return
        }
        Log.i(TAG, "ROM file verified: $romPath")
        val length = file.length()
        if (!romPath.endsWith(".iso", ignoreCase = true) || length >= 734_003_200L) {
            Log.w(TAG, "ROM size ${length} bytes may not be supported")
        }
    }
}
