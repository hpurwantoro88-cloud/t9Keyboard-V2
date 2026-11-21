package com.opent9.keyboard.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * Zero-allocation renderer for the landscape side edit/utility panel.
 */
class LandscapePanelRenderer(
    private val stripBackgroundPaint: Paint,
    private val dividerPaint: Paint,
    private val actionKeyPaint: Paint,
    private val keyPressedPaint: Paint,
    private val keyBorderPaint: Paint,
    private val primaryTextPaint: Paint,
    private val smallTextPaint: Paint,
    private val iconPaint: Paint,
    private val iconPath: Path = Path()
) {

    fun drawPanel(
        canvas: Canvas,
        layout: LandscapePanelLayout,
        activePanelKeyId: Int?,
        density: Float
    ) {
        if (!layout.isActive) return

        // 1. Panel backdrop & vertical separator
        if (!layout.leftPanelBounds.isEmpty) {
            canvas.drawRect(layout.leftPanelBounds, stripBackgroundPaint)
            canvas.drawLine(
                layout.leftPanelBounds.right,
                layout.leftPanelBounds.top,
                layout.leftPanelBounds.right,
                layout.leftPanelBounds.bottom,
                dividerPaint
            )
        }
        if (!layout.rightPanelBounds.isEmpty) {
            canvas.drawRect(layout.rightPanelBounds, stripBackgroundPaint)
            canvas.drawLine(
                layout.rightPanelBounds.left,
                layout.rightPanelBounds.top,
                layout.rightPanelBounds.left,
                layout.rightPanelBounds.bottom,
                dividerPaint
            )
        }

        // 2. Panel keys
        val cornerRadius = 7f * density
        val count = layout.activeKeyCount
        for (i in 0 until count) {
            val key = layout.getKey(i)
            if (key.bounds.isEmpty) continue

            val bgPaint = if (key.id == activePanelKeyId) keyPressedPaint else actionKeyPaint
            canvas.drawRoundRect(key.bounds, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(key.bounds, cornerRadius, cornerRadius, keyBorderPaint)

            val cx = key.bounds.centerX()
            val cy = key.bounds.centerY()

            when (key.action) {
                LandscapePanelAction.HIDE_IME -> {
                    drawHideKeyboardIcon(canvas, cx, cy, density, minOf(key.bounds.width(), key.bounds.height()))
                }
                LandscapePanelAction.CURSOR_UP,
                LandscapePanelAction.CURSOR_DOWN,
                LandscapePanelAction.CURSOR_LEFT,
                LandscapePanelAction.CURSOR_RIGHT,
                LandscapePanelAction.EMOJI -> {
                    val y = cy - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                    canvas.drawText(key.label, cx, y, primaryTextPaint)
                }
                LandscapePanelAction.SWAP_SIDE -> {
                    val displayLabel = if (key.bounds.width() >= 96f * density) "⇄ Tukar" else "⇄"
                    drawAutoFitLabel(canvas, displayLabel, cx, cy, key.bounds.width(), density)
                }
                else -> {
                    drawAutoFitLabel(canvas, key.label, cx, cy, key.bounds.width(), density)
                }
            }
        }
    }

    private fun drawAutoFitLabel(
        canvas: Canvas,
        label: String,
        cx: Float,
        cy: Float,
        keyWidth: Float,
        density: Float
    ) {
        val maxTextWidth = (keyWidth - 8f * density).coerceAtLeast(12f * density)
        val originalSize = smallTextPaint.textSize
        val measured = smallTextPaint.measureText(label)
        if (measured > maxTextWidth && measured > 0f) {
            smallTextPaint.textSize = (originalSize * (maxTextWidth / measured)).coerceAtLeast(9f * density)
        }
        val y = cy - ((smallTextPaint.descent() + smallTextPaint.ascent()) / 2f)
        canvas.drawText(label, cx, y, smallTextPaint)
        smallTextPaint.textSize = originalSize
    }

    private fun drawHideKeyboardIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        density: Float,
        minDim: Float
    ) {
        val size = (minDim * 0.28f).coerceIn(10f * density, 18f * density)
        // Small keyboard top bar
        val halfW = size * 0.65f
        val rectTop = cy - size * 0.55f
        val rectBottom = cy + size * 0.05f
        iconPath.reset()
        iconPath.moveTo(cx - halfW, rectTop)
        iconPath.lineTo(cx + halfW, rectTop)
        iconPath.lineTo(cx + halfW, rectBottom)
        iconPath.lineTo(cx - halfW, rectBottom)
        iconPath.close()
        canvas.drawPath(iconPath, iconPaint)

        // Downward chevron below
        iconPath.reset()
        iconPath.moveTo(cx - size * 0.35f, cy + size * 0.28f)
        iconPath.lineTo(cx, cy + size * 0.62f)
        iconPath.lineTo(cx + size * 0.35f, cy + size * 0.28f)
        canvas.drawPath(iconPath, iconPaint)
    }
}
