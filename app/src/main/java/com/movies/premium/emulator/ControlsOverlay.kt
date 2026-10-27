package com.movies.premium.emulator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Transparent on-screen gamepad overlay.
 *
 * The controls are drawn as vector shapes rather than stretched bitmaps: a
 * round four-way D-pad, round face buttons and pill-shaped system buttons.
 * The bitmap version squashed the glyphs into tall ovals, because the source
 * images were stretched into rectangles that were twice as tall as they were
 * wide.
 */
class ControlsOverlay(
    context: Context,
    /** Opacity of un-pressed buttons, 0..1. */
    private val opacity: Float,
    private val onButton: (button: Int, pressed: Boolean) -> Unit,
) : View(context) {

    /** A round face button, drawn as a filled circle with a letter in it. */
    private class RoundButton(
        val button: Int,
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val label: String,
        val accent: Int,
    )

    /** A pill-shaped button (SELECT / START) or a shoulder button (L / R). */
    private class PillButton(
        val button: Int,
        val rect: RectF,
        val label: String,
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val arrowPath = Path()

    private val roundButtons = ArrayList<RoundButton>()
    private val pillButtons = ArrayList<PillButton>()
    private val shoulderButtons = ArrayList<PillButton>()

    /** Centre and radius of the D-pad disc. */
    private var dpadCx = 0f
    private var dpadCy = 0f
    private var dpadRadius = 0f

    /** pointerId -> set of buttons currently held by that pointer */
    private val pressedByPointer = LinkedHashMap<Int, MutableSet<Int>>()

    /** Buttons currently held, for the pressed-state highlight. */
    val activeButtons: Set<Int>
        get() = pressedByPointer.values.flatten().toSet()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pressedByPointer.clear()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        roundButtons.clear()
        pillButtons.clear()
        shoulderButtons.clear()
        if (w == 0 || h == 0) return

        // The emulator runs in landscape, so the base unit is the height.
        val unit = min(w, h) * 0.08f

        // --- the D-pad: one disc, four directions -------------------------------
        dpadRadius = unit * 1.9f
        dpadCx = unit * 2.6f
        dpadCy = h * 0.62f

        // --- the face buttons: a diamond of round buttons ------------------------
        val faceRadius = unit * 0.95f
        val faceOffset = unit * 1.25f
        val faceCx = w - unit * 3.6f
        val faceCy = h * 0.60f

        roundButtons.add(
            RoundButton(
                LibretroBridge.BUTTON_Y, faceCx, faceCy - faceOffset, faceRadius, "Y",
                0xFFF6C445.toInt(),
            ),
        )
        roundButtons.add(
            RoundButton(
                LibretroBridge.BUTTON_A, faceCx + faceOffset, faceCy, faceRadius, "A",
                0xFF4CAF50.toInt(),
            ),
        )
        roundButtons.add(
            RoundButton(
                LibretroBridge.BUTTON_B, faceCx, faceCy + faceOffset, faceRadius, "B",
                0xFFE05252.toInt(),
            ),
        )
        roundButtons.add(
            RoundButton(
                LibretroBridge.BUTTON_X, faceCx - faceOffset, faceCy, faceRadius, "X",
                0xFF4A8FE0.toInt(),
            ),
        )

        // --- SELECT / START: pills in the bottom-left corner ---------------------
        // They sit outside the letterboxed game picture, so their labels stay
        // readable instead of fighting with the game's own text.
        val pillHalfW = unit * 1.1f
        val pillHalfH = unit * 0.45f
        val pillY = h - unit * 1.05f
        val selectCx = unit * 0.4f + pillHalfW
        val startCx = selectCx + pillHalfW * 2f + unit * 0.25f
        pillButtons.add(
            PillButton(
                LibretroBridge.BUTTON_SELECT,
                RectF(
                    selectCx - pillHalfW, pillY - pillHalfH,
                    selectCx + pillHalfW, pillY + pillHalfH,
                ),
                "SELECT",
            ),
        )
        pillButtons.add(
            PillButton(
                LibretroBridge.BUTTON_START,
                RectF(
                    startCx - pillHalfW, pillY - pillHalfH,
                    startCx + pillHalfW, pillY + pillHalfH,
                ),
                "START",
            ),
        )

        // --- the shoulder buttons ------------------------------------------------
        val shoulderW = unit * 2.4f
        val shoulderH = unit * 0.9f
        val shoulderY = h * 0.02f
        shoulderButtons.add(
            PillButton(
                LibretroBridge.BUTTON_L,
                RectF(unit * 0.4f, shoulderY, unit * 0.4f + shoulderW, shoulderY + shoulderH),
                "L",
            ),
        )
        shoulderButtons.add(
            PillButton(
                LibretroBridge.BUTTON_R,
                RectF(w - unit * 0.4f - shoulderW, shoulderY, w - unit * 0.4f, shoulderY + shoulderH),
                "R",
            ),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val active = activeButtons
        val base = (opacity * 255).toInt().coerceIn(0, 255)
        val pressed = (opacity * 2.2f * 255).toInt().coerceIn(0, 255)

        drawShoulderButtons(canvas, active, base, pressed)
        drawDpad(canvas, active, base, pressed)
        drawFaceButtons(canvas, active, base, pressed)
        drawPillButtons(canvas, active, base, pressed)
    }

    private fun drawFaceButtons(
        canvas: Canvas,
        active: Set<Int>,
        base: Int,
        pressed: Int,
    ) {
        for (button in roundButtons) {
            val isDown = active.contains(button.button)
            val alpha = if (isDown) pressed else base

            fillPaint.style = Paint.Style.FILL
            fillPaint.color = withAlpha(button.accent, (alpha * 0.28f).toInt())
            canvas.drawCircle(button.cx, button.cy, button.radius, fillPaint)

            strokePaint.color = withAlpha(button.accent, alpha)
            strokePaint.strokeWidth = button.radius * 0.10f
            canvas.drawCircle(button.cx, button.cy, button.radius * 0.92f, strokePaint)

            textPaint.color = withAlpha(button.accent, alpha)
            textPaint.textSize = button.radius * 1.05f
            textPaint.isFakeBoldText = true
            val baseline = button.cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(button.label, button.cx, baseline, textPaint)
        }
    }

    private fun drawPillButtons(
        canvas: Canvas,
        active: Set<Int>,
        base: Int,
        pressed: Int,
    ) {
        for (button in pillButtons) {
            val isDown = active.contains(button.button)
            val alpha = if (isDown) pressed else base

            fillPaint.style = Paint.Style.FILL
            fillPaint.color = withAlpha(Color.BLACK, (alpha * 0.55f).toInt())
            canvas.drawRoundRect(button.rect, button.rect.height() / 2f, button.rect.height() / 2f, fillPaint)

            strokePaint.color = withAlpha(Color.WHITE, alpha)
            strokePaint.strokeWidth = button.rect.height() * 0.12f
            canvas.drawRoundRect(button.rect, button.rect.height() / 2f, button.rect.height() / 2f, strokePaint)

            // The labels sit on the black letterbox, so they need more contrast
            // than the buttons that are drawn over the game.
            textPaint.color = withAlpha(Color.WHITE, (alpha * 1.7f).toInt())
            textPaint.textSize = button.rect.height() * 0.56f
            textPaint.isFakeBoldText = true
            val baseline = button.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(button.label, button.rect.centerX(), baseline, textPaint)
        }
    }

    private fun drawShoulderButtons(
        canvas: Canvas,
        active: Set<Int>,
        base: Int,
        pressed: Int,
    ) {
        for (button in shoulderButtons) {
            val isDown = active.contains(button.button)
            val alpha = if (isDown) pressed else base

            fillPaint.style = Paint.Style.FILL
            fillPaint.color = withAlpha(Color.WHITE, (alpha * 0.16f).toInt())
            canvas.drawRoundRect(button.rect, button.rect.height() / 2f, button.rect.height() / 2f, fillPaint)

            strokePaint.color = withAlpha(Color.WHITE, alpha)
            strokePaint.strokeWidth = button.rect.height() * 0.12f
            canvas.drawRoundRect(button.rect, button.rect.height() / 2f, button.rect.height() / 2f, strokePaint)

            textPaint.color = withAlpha(Color.WHITE, alpha)
            textPaint.textSize = button.rect.height() * 0.52f
            textPaint.isFakeBoldText = true
            val baseline = button.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(button.label, button.rect.centerX(), baseline, textPaint)
        }
    }

    /**
     * The D-pad is a single disc with four arrows, so it reads as one
     * control instead of four separate ovals. The direction is taken from the
     * angle of the touch, and a touch in a corner presses both axes — which is
     * what a player expects when walking diagonally.
     */
    private fun drawDpad(
        canvas: Canvas,
        active: Set<Int>,
        base: Int,
        pressed: Int,
    ) {
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = withAlpha(Color.BLACK, (base * 0.30f).toInt())
        canvas.drawCircle(dpadCx, dpadCy, dpadRadius, fillPaint)

        strokePaint.color = withAlpha(Color.WHITE, (base * 0.55f).toInt())
        strokePaint.strokeWidth = dpadRadius * 0.045f
        canvas.drawCircle(dpadCx, dpadCy, dpadRadius * 0.97f, strokePaint)

        drawArrow(canvas, 0f, -1f, active.contains(LibretroBridge.BUTTON_UP), base, pressed)
        drawArrow(canvas, 0f, 1f, active.contains(LibretroBridge.BUTTON_DOWN), base, pressed)
        drawArrow(canvas, -1f, 0f, active.contains(LibretroBridge.BUTTON_LEFT), base, pressed)
        drawArrow(canvas, 1f, 0f, active.contains(LibretroBridge.BUTTON_RIGHT), base, pressed)
    }

    /** Draws one arrow of the D-pad, pointing along ([dx], [dy]). */
    private fun drawArrow(
        canvas: Canvas,
        dx: Float,
        dy: Float,
        isDown: Boolean,
        base: Int,
        pressed: Int,
    ) {
        val alpha = if (isDown) pressed else base
        // The tip points outwards, the base sits far enough from the centre
        // that the four arrows do not run into each other.
        val tip = dpadRadius * 0.82f
        val baseRadius = dpadRadius * 0.36f
        val halfWidth = dpadRadius * 0.20f
        // The perpendicular of the direction vector.
        val px = -dy
        val py = dx

        arrowPath.reset()
        arrowPath.moveTo(dpadCx + dx * tip, dpadCy + dy * tip)
        arrowPath.lineTo(
            dpadCx + dx * baseRadius + px * halfWidth,
            dpadCy + dy * baseRadius + py * halfWidth,
        )
        arrowPath.lineTo(
            dpadCx + dx * baseRadius - px * halfWidth,
            dpadCy + dy * baseRadius - py * halfWidth,
        )
        arrowPath.close()

        fillPaint.style = Paint.Style.FILL
        fillPaint.color = withAlpha(Color.WHITE, alpha)
        canvas.drawPath(arrowPath, fillPaint)
    }

    /** Every button that is under the given point. */
    private fun buttonsAt(x: Float, y: Float): MutableSet<Int> {
        val hit = LinkedHashSet<Int>()

        val dx = x - dpadCx
        val dy = y - dpadCy
        if (dx * dx + dy * dy <= dpadRadius * dpadRadius) {
            val adx = abs(dx)
            val ady = abs(dy)
            if (max(adx, ady) > 0f) {
                if (adx > ady) {
                    hit.add(
                        if (dx < 0) LibretroBridge.BUTTON_LEFT else LibretroBridge.BUTTON_RIGHT,
                    )
                    // A corner touch presses both axes.
                    if (ady > adx * 0.5f) {
                        hit.add(if (dy < 0) LibretroBridge.BUTTON_UP else LibretroBridge.BUTTON_DOWN)
                    }
                } else {
                    hit.add(if (dy < 0) LibretroBridge.BUTTON_UP else LibretroBridge.BUTTON_DOWN)
                    if (adx > ady * 0.5f) {
                        hit.add(if (dx < 0) LibretroBridge.BUTTON_LEFT else LibretroBridge.BUTTON_RIGHT)
                    }
                }
            }
        }

        for (button in roundButtons) {
            val bx = x - button.cx
            val by = y - button.cy
            if (bx * bx + by * by <= button.radius * button.radius) {
                hit.add(button.button)
            }
        }

        for (button in pillButtons) {
            if (button.rect.contains(x, y)) hit.add(button.button)
        }
        for (button in shoulderButtons) {
            if (button.rect.contains(x, y)) hit.add(button.button)
        }

        return hit
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val hit = buttonsAt(event.getX(actionIndex), event.getY(actionIndex))
                hit.forEach { onButton(it, true) }
                pressedByPointer[pointerId] = hit
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                pressedByPointer.remove(pointerId)?.forEach { onButton(it, false) }
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val previous = pressedByPointer[id] ?: LinkedHashSet()
                    val current = buttonsAt(event.getX(i), event.getY(i))
                    (previous - current).forEach { onButton(it, false) }
                    (current - previous).forEach { onButton(it, true) }
                    pressedByPointer[id] = current
                }
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
