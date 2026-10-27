package com.movies.premium.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/**
 * How much bigger the whole interface is drawn on a television.
 *
 * A television is watched from across the room, so everything on it has to be
 * bigger: the text, the posters, the buttons and the touch targets. The
 * factor is applied to the *density* of the whole composition (see
 * [androidx.compose.ui.platform.LocalDensity]), so a single number scales the
 * complete interface — not only the type — and the layout that is left is
 * still the layout of the phone build, only bigger.
 *
 * 1.3 is the factor the "ten foot" interfaces of the platform use: a body
 * text of 12 sp becomes 15.6 sp on a 960 dp wide television, which is
 * readable from a sofa, and a poster of 100 dp becomes 130 dp, which is big
 * enough to be recognised from the other end of the room.
 */
const val TV_UI_SCALE = 1.3f

/**
 * The width, in dp, that the interface of a television is designed for.
 *
 * A 1080p television reports 320 dpi, which is 960 dp across. A television
 * that reports a lower density gets a wider canvas in dp, and the interface
 * would be spread out over it; the scale is then raised so that the canvas
 * comes back to roughly this width.
 */
const val TV_DESIGN_WIDTH_DP = 960

/**
 * True when the app runs on a television.
 *
 * The check is the one the platform uses: a device that declares the
 * leanback feature is a television, and so is a device whose user interface
 * mode says "television". The second test catches the devices that are
 * updated over the air and report the mode without the feature.
 */
val LocalIsTv = staticCompositionLocalOf { false }

/** Whether the app runs on a television — see [LocalIsTv]. */
fun isTvDevice(context: Context): Boolean {
    if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
        return true
    }
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * The factor the interface is scaled by on a television.
 *
 * [screenWidthDp] is the width of the canvas in dp. The factor of [TV_UI_SCALE]
 * is the floor; a television that reports a wider canvas gets a proportionally
 * bigger factor, so that the interface keeps the width it was designed for.
 */
fun tvUiScale(screenWidthDp: Int): Float {
    if (screenWidthDp <= 0) return TV_UI_SCALE
    val wanted = screenWidthDp / TV_DESIGN_WIDTH_DP.toFloat()
    return maxOf(TV_UI_SCALE, wanted)
}

/**
 * How many posters fit on one row of a television.
 *
 * A television is 960 dp wide, and a poster of 200 dp — the size of the phone
 * build in landscape — leaves room for barely four of them. A row of seven
 * is what a viewer can take in at a glance, and it leaves the posters big
 * enough to be recognised from the other end of the room.
 */
const val TV_POSTERS_PER_ROW = 7

/**
 * The width of a poster on a television, in the dp of the scaled canvas.
 *
 * The canvas of a television is 960 dp wide *before* it is scaled, and the
 * scale of [TV_UI_SCALE] makes it narrower in dp — so the width of a poster
 * has to be taken from the scaled canvas, not from the raw width of the
 * screen.
 */
fun tvPosterWidthDp(screenWidthDp: Int, columns: Int = TV_POSTERS_PER_ROW): Int {
    val scale = tvUiScale(screenWidthDp)
    val canvasWidth = screenWidthDp / scale
    return (canvasWidth / columns).toInt()
}

/**
 * The width of a poster card, in the dp of the canvas it is drawn in.
 *
 * On a phone the card keeps the size of the phone build: 132 dp in portrait,
 * where two and a half of them fit, and 200 dp in landscape, where the row
 * has room. On a television the row has to carry more of them, so the card
 * is a seventh of the canvas.
 */
@Composable
fun posterCardWidthDp(): Int {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    if (!LocalIsTv.current) {
        return if (screenWidthDp < 560) 132 else 200
    }
    return tvPosterWidthDp(screenWidthDp)
}
