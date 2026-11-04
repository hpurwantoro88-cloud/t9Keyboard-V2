package com.opent9.keyboard.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Dedicated renderer for Page 2 (Primary Symbols) and Page 3 (Extended Symbols)
 * in 10-column Gboard-style layout.
 * Guarantees zero heap allocations per draw frame.
 */
class Page23SymbolsRenderer(
    private val backgroundPaint: Paint,
    private val actionKeyPaint: Paint,
    private val keyBorderPaint: Paint,
    private val keyPressedPaint: Paint,
    private val keyBackgroundPaint: Paint,
    private val textPaint: Paint,
    private val subTextPaint: Paint,
    private val iconPaint: Paint,
    private val iconFillPaint: Paint,
    private val scratchRect: RectF = RectF(),
    private val iconPath: Path = Path()
) {

    /**
     * Renders symbols page (Page 2 or Page 3) on the Canvas.
     */
    fun drawSymbolsPage(
        canvas: Canvas,
        width: Float,
        height: Float,
        keyAtlas: KeyAtlas,
        keys: List<KeyInfo>,
        activeKeyId: Int?,
        imeAction: Int,
        activeLanguage: String,
        density: Float
    ) {
        val cornerRadius = 8f * density
        val minDim = minOf(keyAtlas.rowHeight, keyAtlas.colWidth)

        // 1. Draw keyboard background matching active theme
        canvas.drawRect(0f, 0f, width, height, backgroundPaint)

        // 2. Draw Keys
        for (key in keys) {
            val isPressed = (key.id == activeKeyId)
            val bgPaint = if (isPressed) {
                keyPressedPaint
            } else if (key.isActionKey) {
                actionKeyPaint
            } else {
                keyBackgroundPaint
            }

            canvas.drawRoundRect(key.bounds, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(key.bounds, cornerRadius, cornerRadius, keyBorderPaint)

            when (key.type) {
                KeyType.DEL -> {
                    KeyboardVectorIcons.drawDelIcon(
                        canvas, key.centerX, key.centerY, density, minDim, iconPath, iconPaint
                    )
                }
                KeyType.ENTER -> {
                    KeyboardVectorIcons.drawEnterIcon(
                        canvas, key.centerX, key.centerY, density, imeAction, minDim, iconPath, iconPaint, iconFillPaint
                    )
                }
                KeyType.PAGE_SWITCH -> {
                    if (key.primaryLabel == "12 34") {
                        // Stacked 12 / 34 icon matching Gboard numpad switch
                        val oldSize = textPaint.textSize
                        val oldFakeBold = textPaint.isFakeBoldText
                        textPaint.textSize = (11.5f * density).coerceAtLeast(10f)
                        textPaint.isFakeBoldText = true

                        val ascent = textPaint.ascent()
                        val descent = textPaint.descent()
                        val halfH = (descent - ascent) * 0.9f
                        canvas.drawText("12", key.centerX, key.centerY - (halfH * 0.1f), textPaint)
                        canvas.drawText("34", key.centerX, key.centerY + halfH, textPaint)

                        textPaint.textSize = oldSize
                        textPaint.isFakeBoldText = oldFakeBold
                    } else {
                        val textY = key.centerY - ((textPaint.descent() + textPaint.ascent()) / 2f)
                        canvas.drawText(key.primaryLabel, key.centerX, textY, textPaint)
                    }
                }
                KeyType.SPACE_0 -> {
                    // Draw language label on space bar (e.g. "Indonesia" or "English")
                    val spaceLabel = when (activeLanguage.uppercase()) {
                        "ID" -> "Indonesia"
                        "EN" -> "English"
                        else -> activeLanguage
                    }
                    val textY = key.centerY - ((subTextPaint.descent() + subTextPaint.ascent()) / 2f)
                    canvas.drawText(spaceLabel, key.centerX, textY, subTextPaint)
                }
                else -> {
                    // Regular symbols
                    val textY = key.centerY - ((textPaint.descent() + textPaint.ascent()) / 2f)
                    canvas.drawText(key.primaryLabel, key.centerX, textY, textPaint)
                }
            }
        }
    }
}
