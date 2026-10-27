package com.movies.premium.ui.tv

import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The colour of the focus ring.
 *
 * A light blue, the colour the platform uses for the focus of a television
 * interface, and bright enough to be seen on the dark background of the app.
 */
val FocusRingColor = Color(0xFF8AB4F8)

/**
 * Remembers whether the interface is being driven with a remote control.
 *
 * A telephone is driven with a finger, and a finger does not need a focus
 * ring: the thing under the finger is obvious. A remote control has no
 * pointer at all, so the interface has to show where the focus is — but the
 * ring must not appear on a phone, where the user has just tapped a poster
 * and the poster would suddenly grow a border.
 *
 * The state therefore switches on the first key that only a remote control or
 * a keyboard can produce, and it stays on for the rest of the session.
 */
@Stable
class FocusHighlightState {

    /** True once a remote control or a keyboard has been used. */
    var hardwareMode: Boolean by mutableStateOf(false)
        internal set

    /** Called for every key that reaches the app — see [hardwareFocusWatcher]. */
    fun onKey(keyCode: Int) {
        if (!hardwareMode && keyCode in NAVIGATION_KEYS) {
            hardwareMode = true
        }
    }

    private companion object {
        /**
         * The keys that only a remote control, a gamepad or a keyboard sends.
         *
         * The volume keys are deliberately not in the list: they reach the
         * application on a phone as well, and they do not move the focus.
         */
        val NAVIGATION_KEYS = setOf(
            android.view.KeyEvent.KEYCODE_DPAD_UP,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
            android.view.KeyEvent.KEYCODE_TAB,
            android.view.KeyEvent.KEYCODE_SPACE,
            android.view.KeyEvent.KEYCODE_BUTTON_A,
            android.view.KeyEvent.KEYCODE_BUTTON_B,
            android.view.KeyEvent.KEYCODE_BUTTON_X,
            android.view.KeyEvent.KEYCODE_BUTTON_Y,
            android.view.KeyEvent.KEYCODE_BUTTON_L1,
            android.view.KeyEvent.KEYCODE_BUTTON_R1,
            android.view.KeyEvent.KEYCODE_BUTTON_START,
            android.view.KeyEvent.KEYCODE_BUTTON_SELECT,
        )
    }
}

/** The state of the focus ring of the session — see [FocusHighlightState]. */
val LocalFocusHighlightState = staticCompositionLocalOf { FocusHighlightState() }

/**
 * Watches the keys that reach the app and switches on the focus ring.
 *
 * The watcher is a *preview* listener, so it sees a key before the focused
 * element does and therefore also the keys that the focused element would
 * swallow — the "ok" of a button, for instance.
 */
fun Modifier.hardwareFocusWatcher(state: FocusHighlightState): Modifier =
    onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (native.action == android.view.KeyEvent.ACTION_DOWN) {
            state.onKey(native.keyCode)
        }
        false
    }

/**
 * The rectangle of the element that has the focus, in the coordinates of the
 * view the interface is drawn in.
 *
 * The rectangle is read from the platform and it is polled, because the
 * focused element moves: a list scrolls the focused item into the middle of
 * the screen, and the ring has to follow it.
 */
@Composable
private fun rememberFocusRect(): Rect? {
    val view = LocalView.current
    var rect by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(view) {
        val out = AndroidRect()
        while (true) {
            out.setEmpty()
            view.getFocusedRect(out)
            val next = if (out.isEmpty) {
                null
            } else {
                Rect(
                    left = out.left.toFloat(),
                    top = out.top.toFloat(),
                    right = out.right.toFloat(),
                    bottom = out.bottom.toFloat(),
                )
            }
            if (next != rect) {
                rect = next
            }
            withFrameNanos { }
        }
    }
    return rect
}

/**
 * Draws the ring around the element that has the focus.
 *
 * The ring is the only way a user of a television knows where the focus is,
 * so it is drawn around *every* focused element — the posters of a grid, the
 * buttons, the switches and the fields — and not only around the ones that
 * happen to know about it.
 *
 * The overlay has to be the last thing in the composition, so that the ring
 * is drawn over the interface and not under it.
 */
@Composable
fun FocusHighlightOverlay(
    modifier: Modifier = Modifier,
    color: Color = FocusRingColor,
    strokeWidth: Dp = 3.dp,
    cornerRadius: Dp = 10.dp,
) {
    val state = LocalFocusHighlightState.current
    if (!state.hardwareMode) return

    val rect = rememberFocusRect() ?: return
    val density = LocalDensity.current
    val stroke = with(density) { strokeWidth.toPx() }
    val radius = with(density) { cornerRadius.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        // The ring is drawn on the inside of the rectangle of the element, so
        // that it never falls outside of it and gets cut off.
        val inset = stroke / 2f
        val left = rect.left + inset
        val top = rect.top + inset
        val right = rect.right - inset
        val bottom = rect.bottom - inset
        if (right <= left || bottom <= top) return@Canvas

        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = stroke),
        )
    }
}

/**
 * Draws a ring around the element that has the focus, inside this element.
 *
 * This is the local version of [FocusHighlightOverlay], for the elements
 * that live in a window of their own — a dialog, the sheet of a game, the
 * menu of a filter — where the ring of the window of the app cannot reach.
 *
 * The modifier can be put on the element that is to be ringed *or* on a
 * container of it:
 *
 * * On a button, the ring is drawn around the button while the button has the
 *   focus.
 * * On a container — the surface of a dialog — the ring is drawn around
 *   whatever inside it has the focus, so that a whole dialog shows the focus
 *   of the button that is selected, with a single modifier.
 *
 * The ring is only drawn when the focused element is inside the element the
 * modifier is on: an element that does not hold the focus draws nothing.
 */
@Composable
fun Modifier.focusRing(
    color: Color = FocusRingColor,
    strokeWidth: Dp = 3.dp,
    cornerRadius: Dp = 8.dp,
): Modifier {
    val view = LocalView.current
    val density = LocalDensity.current
    val stroke = with(density) { strokeWidth.toPx() }
    val radius = with(density) { cornerRadius.toPx() }

    // The rectangle of the focused element of this window, and the place of
    // this element inside the window.
    var focusedRect by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(view) {
        val out = AndroidRect()
        while (true) {
            out.setEmpty()
            view.getFocusedRect(out)
            val next = if (out.isEmpty) {
                null
            } else {
                Rect(
                    left = out.left.toFloat(),
                    top = out.top.toFloat(),
                    right = out.right.toFloat(),
                    bottom = out.bottom.toFloat(),
                )
            }
            if (next != focusedRect) {
                focusedRect = next
            }
            withFrameNanos { }
        }
    }

    return this
        .onGloballyPositioned { coordinates -> origin = coordinates.offsetInView() }
        .drawWithContent {
            drawContent()

            val rect = focusedRect ?: return@drawWithContent
            val left = rect.left - origin.x
            val top = rect.top - origin.y
            val right = rect.right - origin.x
            val bottom = rect.bottom - origin.y

            // The focused element has to be inside this one. Without the test
            // every element that carries the modifier would draw a ring
            // around the focus of the window, wherever it happens to be.
            if (left < 0f || top < 0f || right > size.width || bottom > size.height) {
                return@drawWithContent
            }
            if (right <= left || bottom <= top) return@drawWithContent

            val inset = stroke / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(left + inset, top + inset),
                size = Size(right - left - stroke, bottom - top - stroke),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = stroke),
            )
        }
}

/**
 * The place of this element inside the view it is drawn in.
 *
 * The rectangle of the focused element is reported by the platform in the
 * coordinates of the view, and the drawing of an element starts at its own
 * top left corner — so the two have to be brought into the same system
 * before the ring can be drawn at the right place.
 */
private fun LayoutCoordinates.offsetInView(): Offset {
    var offset = positionInParent()
    var parent = parentLayoutCoordinates
    while (parent != null) {
        offset += parent.positionInParent()
        parent = parent.parentLayoutCoordinates
    }
    return offset
}
