package com.opent9.keyboard.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Dedicated renderer for Page 0 Keypad (Alphabet / Digits grid, Lang switch with T9 glow bar,
 * composite utility key, and dual symbols).
 * Guarantees zero heap allocations per draw frame.
 */
class Page0KeypadRenderer(
    private val keyBackgroundPaint: Paint,
    private val keyPressedPaint: Paint,
    private val keyBorderPaint: Paint,
    private val primaryTextPaint: Paint,
    private val keyCornerSubTextPaint: Paint,
    private val dualSymTextPaint: Paint,
    private val indicatorGlowPaint: Paint,
    private val iconPaint: Paint,
    private val iconFillPaint: Paint,
    private val scratchRect: RectF = RectF(),
    private val glowRect: RectF = RectF(),
    private val iconPath: Path = Path()
) {

    /**
     * Renders the complete Page 0 keypad grid on the Canvas.
     */
    fun drawKeypad(
        canvas: Canvas,
        keyAtlas: KeyAtlas,
        activeKeyId: Int?,
        isT9Mode: Boolean,
        activeLanguage: String,
        imeAction: Int,
        shiftState: Int,
        density: Float,
        getDisplayLabel: (KeyInfo) -> String
    ) {
        val keyMargin = 3f * density
        val cornerRadius = 10f * density
        val baseRowHeight = 55f * density
        val rowScale = if (baseRowHeight > 0f) (keyAtlas.rowHeight / baseRowHeight).coerceIn(0.60f, 1.40f) else 1f

        for (i in 0 until 16) {
            val key = keyAtlas.keys[i]
            if (keyAtlas.currentPage == KeyboardPage.PAGE_0_TEXT && key.id == 15) continue
            if (key.bounds.isEmpty) continue

            scratchRect.set(
                key.bounds.left + keyMargin,
                key.bounds.top + keyMargin,
                key.bounds.right - keyMargin,
                key.bounds.bottom - keyMargin
            )

            // Key background (pressed or normal)
            val bgPaint = if (key.id == activeKeyId) keyPressedPaint else keyBackgroundPaint
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, keyBorderPaint)

            // Specific key rendering
            when (key.type) {
                KeyType.DIGIT_T9, KeyType.PUNCT_1, KeyType.SPACE_0 -> {
                    // Alphabet / main symbol centered
                    val label = getDisplayLabel(key)
                    val labelY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                    val originalSize = primaryTextPaint.textSize
                    if (label.length > 4) {
                        primaryTextPaint.textSize = originalSize * 0.72f
                    }
                    canvas.drawText(label, key.centerX, labelY, primaryTextPaint)
                    if (label.length > 4) {
                        primaryTextPaint.textSize = originalSize
                    }

                    // Number (secondary) in upper right corner of the key
                    if (key.subLabel.isNotEmpty()) {
                        val numX = scratchRect.right - (7f * density)
                        val numY = scratchRect.top + (11f * density * rowScale)
                        canvas.drawText(key.subLabel, numX, numY, keyCornerSubTextPaint)
                    }
                }
                KeyType.LANG_SWITCH -> {
                    // Language code text (EN or ID)
                    val langY = key.centerY - (2f * density * rowScale)
                    canvas.drawText(activeLanguage, key.centerX, langY, primaryTextPaint)

                    // Emoticon indicator / subLabel in upper right corner
                    if (key.subLabel.isNotEmpty()) {
                        val subX = scratchRect.right - (7f * density)
                        val subY = scratchRect.top + (11f * density * rowScale)
                        canvas.drawText(key.subLabel, subX, subY, keyCornerSubTextPaint)
                    }

                    // Active T9 Glow Bar directly beneath the language text
                    if (isT9Mode) {
                        val barWidth = (28f * density).coerceAtMost(key.bounds.width() * 0.60f)
                        val barHeight = 3f * density
                        val barRadius = 1.5f * density
                        val barTop = key.centerY + (13f * density * rowScale)
                        glowRect.set(
                            key.centerX - (barWidth / 2f),
                            barTop,
                            key.centerX + (barWidth / 2f),
                            barTop + barHeight
                        )
                        canvas.drawRoundRect(glowRect, barRadius, barRadius, indicatorGlowPaint)
                    }
                }
                KeyType.ENTER -> {
                    val minDim = minOf(keyAtlas.rowHeight, keyAtlas.colWidth)
                    KeyboardVectorIcons.drawEnterIcon(canvas, key.centerX, key.centerY, density, imeAction, minDim, iconPath, iconPaint, iconFillPaint)
                }
                KeyType.SHIFT -> {
                    val minDim = minOf(keyAtlas.rowHeight, keyAtlas.colWidth)
                    KeyboardVectorIcons.drawShiftIcon(canvas, key.centerX, key.centerY, density, shiftState, minDim, iconPath, iconPaint, iconFillPaint)
                }
                KeyType.DEL -> {
                    val minDim = minOf(keyAtlas.rowHeight, keyAtlas.colWidth)
                    KeyboardVectorIcons.drawDelIcon(canvas, key.centerX, key.centerY, density, minDim, iconPath, iconPaint)
                }
                KeyType.DUAL_SYM -> {
                    val glyphOffset = (key.bounds.width() * 0.20f).coerceIn(10f * density, 24f * density)
                    val leftX = key.centerX - glyphOffset
                    val rightX = key.centerX + glyphOffset
                    val symY = key.centerY - ((dualSymTextPaint.descent() + dualSymTextPaint.ascent()) / 2f)
                    canvas.drawText(key.leftGlyph, leftX, symY, dualSymTextPaint)
                    canvas.drawText(key.rightGlyph, rightX, symY, dualSymTextPaint)
                }
                KeyType.PAGE_SWITCH -> {
                    if (key.id == 12 && keyAtlas.currentPage == KeyboardPage.PAGE_0_TEXT) {
                        // Combined Utility Key
                        val prevAlign = keyCornerSubTextPaint.textAlign
                        keyCornerSubTextPaint.textAlign = Paint.Align.LEFT
                        val langX = scratchRect.left + (7f * density)
                        val langY = scratchRect.top + (11f * density * rowScale)
                        canvas.drawText(activeLanguage, langX, langY, keyCornerSubTextPaint)

                        if (isT9Mode) {
                            val barWidth = 14f * density
                            val barHeight = 2.5f * density
                            val barRadius = 1.25f * density
                            val barTop = langY + (2.5f * density * rowScale)
                            glowRect.set(
                                langX,
                                barTop,
                                langX + barWidth,
                                barTop + barHeight
                            )
                            canvas.drawRoundRect(glowRect, barRadius, barRadius, indicatorGlowPaint)
                        }

                        keyCornerSubTextPaint.textAlign = Paint.Align.RIGHT
                        val emojiX = scratchRect.right - (7f * density)
                        val emojiY = scratchRect.top + (11f * density * rowScale)
                        canvas.drawText("😊", emojiX, emojiY, keyCornerSubTextPaint)
                        keyCornerSubTextPaint.textAlign = prevAlign

                        val labelY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                        canvas.drawText("?123", key.centerX, labelY, primaryTextPaint)
                    } else {
                        val labelY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                        canvas.drawText(key.primaryLabel, key.centerX, labelY, primaryTextPaint)
                    }
                }
                else -> {
                    val labelY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                    canvas.drawText(key.primaryLabel, key.centerX, labelY, primaryTextPaint)
                }
            }
        }
    }
}
