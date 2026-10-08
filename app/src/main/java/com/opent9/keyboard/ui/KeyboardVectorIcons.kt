package com.opent9.keyboard.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.inputmethod.EditorInfo

/**
 * Utility to draw resolution-independent vector icons (Enter, Shift, Backspace, Space)
 * on Canvas using pre-allocated Path and Paint objects to ensure 0 GC pressure.
 */
object KeyboardVectorIcons {

    fun drawEnterIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        density: Float,
        imeAction: Int,
        minDimension: Float,
        path: Path,
        strokePaint: Paint,
        fillPaint: Paint
    ) {
        val size = (minDimension * 0.28f).coerceIn(12f * density, 24f * density)
        path.reset()
        when (imeAction) {
            EditorInfo.IME_ACTION_SEARCH -> {
                // Magnifying glass
                val r = size * 0.35f
                canvas.drawCircle(cx - (size * 0.1f), cy - (size * 0.1f), r, strokePaint)
                canvas.drawLine(cx + (size * 0.15f), cy + (size * 0.15f), cx + (size * 0.45f), cy + (size * 0.45f), strokePaint)
            }
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEND -> {
                // Right arrow / Paper plane
                path.moveTo(cx - (size * 0.35f), cy - (size * 0.35f))
                path.lineTo(cx + (size * 0.4f), cy)
                path.lineTo(cx - (size * 0.35f), cy + (size * 0.35f))
                path.lineTo(cx - (size * 0.15f), cy)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            EditorInfo.IME_ACTION_NEXT -> {
                // Tab right arrow
                path.moveTo(cx - (size * 0.3f), cy - (size * 0.35f))
                path.lineTo(cx + (size * 0.2f), cy)
                path.lineTo(cx - (size * 0.3f), cy + (size * 0.35f))
                canvas.drawPath(path, strokePaint)
                canvas.drawLine(cx + (size * 0.3f), cy - (size * 0.35f), cx + (size * 0.3f), cy + (size * 0.35f), strokePaint)
            }
            EditorInfo.IME_ACTION_DONE -> {
                // Checkmark
                path.moveTo(cx - (size * 0.35f), cy)
                path.lineTo(cx - (size * 0.05f), cy + (size * 0.3f))
                path.lineTo(cx + (size * 0.4f), cy - (size * 0.3f))
                canvas.drawPath(path, strokePaint)
            }
            else -> {
                // Return carriage arrow
                path.moveTo(cx + (size * 0.3f), cy - (size * 0.3f))
                path.lineTo(cx + (size * 0.3f), cy + (size * 0.1f))
                path.lineTo(cx - (size * 0.25f), cy + (size * 0.1f))
                canvas.drawPath(path, strokePaint)
                // arrow head
                path.reset()
                path.moveTo(cx - (size * 0.1f), cy - (size * 0.1f))
                path.lineTo(cx - (size * 0.35f), cy + (size * 0.1f))
                path.lineTo(cx - (size * 0.1f), cy + (size * 0.3f))
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    fun drawShiftIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        density: Float,
        shiftState: Int,
        minDimension: Float,
        path: Path,
        strokePaint: Paint,
        fillPaint: Paint
    ) {
        val s = (minDimension * 0.25f).coerceIn(10f * density, 20f * density)
        path.reset()
        path.moveTo(cx, cy - (s * 0.5f))
        path.lineTo(cx + (s * 0.45f), cy)
        path.lineTo(cx + (s * 0.2f), cy)
        path.lineTo(cx + (s * 0.2f), cy + (s * 0.45f))
        path.lineTo(cx - (s * 0.2f), cy + (s * 0.45f))
        path.lineTo(cx - (s * 0.2f), cy)
        path.lineTo(cx - (s * 0.45f), cy)
        path.close()

        when (shiftState) {
            0 -> canvas.drawPath(path, strokePaint) // Hollow outline (LOWERCASE)
            1 -> canvas.drawPath(path, fillPaint) // Solid filled (TITLECASE)
            2 -> { // Solid filled with base bar (UPPERCASE / CAPS LOCK)
                canvas.drawPath(path, fillPaint)
                canvas.drawLine(cx - (s * 0.45f), cy + (s * 0.65f), cx + (s * 0.45f), cy + (s * 0.65f), strokePaint)
            }
        }
    }

    fun drawDelIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        density: Float,
        minDimension: Float,
        path: Path,
        strokePaint: Paint
    ) {
        val s = (minDimension * 0.25f).coerceIn(10f * density, 20f * density)
        path.reset()
        path.moveTo(cx - (s * 0.5f), cy)
        path.lineTo(cx - (s * 0.15f), cy - (s * 0.35f))
        path.lineTo(cx + (s * 0.5f), cy - (s * 0.35f))
        path.lineTo(cx + (s * 0.5f), cy + (s * 0.35f))
        path.lineTo(cx - (s * 0.15f), cy + (s * 0.35f))
        path.close()
        canvas.drawPath(path, strokePaint)

        // Draw inner 'x'
        val xs = s * 0.15f
        val xcx = cx + (s * 0.15f)
        canvas.drawLine(xcx - xs, cy - xs, xcx + xs, cy + xs, strokePaint)
        canvas.drawLine(xcx - xs, cy + xs, xcx + xs, cy - xs, strokePaint)
    }

    fun drawPage1SpaceIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        density: Float,
        minDimension: Float,
        path: Path,
        strokePaint: Paint
    ) {
        val s = (minDimension * 0.22f).coerceIn(10f * density, 18f * density)
        path.reset()
        path.moveTo(cx - (s * 0.7f), cy - (s * 0.2f))
        path.lineTo(cx - (s * 0.7f), cy + (s * 0.2f))
        path.lineTo(cx + (s * 0.7f), cy + (s * 0.2f))
        path.lineTo(cx + (s * 0.7f), cy - (s * 0.2f))
        canvas.drawPath(path, strokePaint)
    }
}
