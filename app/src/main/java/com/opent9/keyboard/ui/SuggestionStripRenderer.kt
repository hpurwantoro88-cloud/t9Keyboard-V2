package com.opent9.keyboard.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Dedicated renderer for suggestion strips (Page 0 T9/ABC candidates) and symbol layer tabs (Page 2).
 * Ensures zero heap allocations during onDraw rendering cycles.
 */
class SuggestionStripRenderer(
    private val stripBackgroundPaint: Paint,
    private val pillPaint: Paint,
    private val pillTextPaint: Paint,
    private val candidateTextPaint: Paint,
    private val candidatePrefixPaint: Paint,
    private val dividerPaint: Paint,
    private val keyBackgroundPaint: Paint,
    private val keyBorderPaint: Paint,
    private val subTextPaint: Paint,
    private val scratchRect: RectF = RectF()
) {

    /**
     * Draws the Page 2 Extended Symbols top category tabs strip.
     */
    fun drawPage2Strip(
        canvas: Canvas,
        keyAtlas: KeyAtlas,
        density: Float
    ) {
        canvas.drawRect(keyAtlas.stripBounds, stripBackgroundPaint)

        val tabMargin = 3f * density
        val cornerRadius = 8f * density

        for (i in 0 until 4) {
            val bounds = keyAtlas.symbolLayerTabBounds[i]
            scratchRect.set(
                bounds.left + tabMargin,
                bounds.top + tabMargin,
                bounds.right - tabMargin,
                bounds.bottom - tabMargin
            )

            val isActive = (i == keyAtlas.activeSymbolLayerIndex)
            val bgPaint = if (isActive) pillPaint else keyBackgroundPaint
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, bgPaint)
            if (isActive) {
                canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, keyBorderPaint)
            }

            val textPaint = if (isActive) pillTextPaint else subTextPaint
            val title = keyAtlas.symbolLayerTabTitles[i]
            val textY = bounds.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(title, bounds.centerX(), textY, textPaint)
        }
    }

    /**
     * Draws the candidate suggestions strip and mode toggle pill (T9 / ABC).
     */
    fun drawSuggestionStrip(
        canvas: Canvas,
        keyAtlas: KeyAtlas,
        scrollOffset: Float,
        isT9Mode: Boolean,
        candidates: List<String>,
        candidateItemLeft: FloatArray,
        candidateItemRight: FloatArray,
        density: Float,
        formatWord: (String) -> String
    ) {
        canvas.drawRect(keyAtlas.stripBounds, stripBackgroundPaint)

        // Fixed Left Pill (12% width)
        val pillMargin = 4f * density
        val pillRadius = 4f * density
        scratchRect.set(
            keyAtlas.pillBounds.left + pillMargin,
            keyAtlas.pillBounds.top + pillMargin,
            keyAtlas.pillBounds.right - pillMargin,
            keyAtlas.pillBounds.bottom - pillMargin
        )
        canvas.drawRoundRect(scratchRect, pillRadius, pillRadius, pillPaint)
        val pillLabel = if (isT9Mode) "T9" else "ABC"
        val pillTextY = keyAtlas.pillBounds.centerY() - ((pillTextPaint.descent() + pillTextPaint.ascent()) / 2f)
        canvas.drawText(pillLabel, keyAtlas.pillBounds.centerX(), pillTextY, pillTextPaint)

        // Divider between pill and viewport
        canvas.drawLine(
            keyAtlas.candidateViewportBounds.left, keyAtlas.stripBounds.top + (2f * density),
            keyAtlas.candidateViewportBounds.left, keyAtlas.stripBounds.bottom - (2f * density),
            dividerPaint
        )

        // Candidate Viewport (88% width) with clip & scroll
        canvas.save()
        canvas.clipRect(keyAtlas.candidateViewportBounds)
        canvas.translate(scrollOffset, 0f)

        val candCount = candidates.size.coerceAtMost(16)
        val textY = keyAtlas.stripBounds.centerY() - ((candidateTextPaint.descent() + candidateTextPaint.ascent()) / 2f)

        for (i in 0 until candCount) {
            val left = candidateItemLeft[i]
            val right = candidateItemRight[i]
            val word = formatWord(candidates[i])

            // Highlight 1st candidate
            val paint = if (i == 0) candidatePrefixPaint else candidateTextPaint
            val textX = left + 12f * density
            canvas.drawText(word, textX, textY, paint)

            // Vertical divider between items
            canvas.drawLine(
                right,
                keyAtlas.stripBounds.top + (2.67f * density),
                right,
                keyAtlas.stripBounds.bottom - (2.67f * density),
                dividerPaint
            )
        }
        canvas.restore()
    }
}
