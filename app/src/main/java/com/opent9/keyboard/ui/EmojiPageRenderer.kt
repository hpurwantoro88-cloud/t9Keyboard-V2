package com.opent9.keyboard.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Dedicated renderer for Page 3 Emoji Picker (category tabs, scrollable grid, and bottom controls).
 * Guarantees zero heap allocations per frame by pre-allocating bounds and arrays.
 */
class EmojiPageRenderer(
    private val pillPaint: Paint,
    private val keyPressedPaint: Paint,
    private val keyBackgroundPaint: Paint,
    private val subTextPaint: Paint,
    private val primaryTextPaint: Paint,
    private val scratchRect: RectF = RectF()
) {

    // Pre-allocated labels array to avoid heap allocations on draw hot paths
    private val ctrlLabels = arrayOf("ABC", "🕒 Recents", "␣ Space", "⌫ DEL")

    /**
     * Renders the complete Page 3 Emoji page.
     */
    fun drawEmojiPage(
        canvas: Canvas,
        emojiAtlas: EmojiAtlas,
        emojiScrollOffset: Float,
        activeTabIndex: Int?,
        activeGridIndex: Int?,
        activeControlIndex: Int?,
        density: Float
    ) {
        // 1. Draw category tabs (Smileys, Memoji, etc.)
        val tabCount = emojiAtlas.categories.size
        for (i in 0 until tabCount) {
            val bounds = emojiAtlas.categoryTabBounds[i]
            if (i == emojiAtlas.activeCategoryIndex) {
                canvas.drawRoundRect(bounds, 8f * density, 8f * density, pillPaint)
            } else if (i == activeTabIndex) {
                canvas.drawRoundRect(bounds, 8f * density, 8f * density, keyPressedPaint)
            }
            val textY = bounds.centerY() - ((subTextPaint.descent() + subTextPaint.ascent()) / 2f)
            canvas.drawText(emojiAtlas.categories[i], bounds.centerX(), textY, subTextPaint)
        }

        // 2. Draw active category scrollable emoji grid
        val scrollY = emojiScrollOffset
        val gridTop = emojiAtlas.gridTop
        val gridBottom = emojiAtlas.gridBottom
        val rowHeight = emojiAtlas.gridRowHeight
        val colWidth = emojiAtlas.gridColWidth
        val cols = emojiAtlas.gridCols
        val offsetX = emojiAtlas.currentOffsetX
        val activeEmojis = emojiAtlas.getActiveEmojiList()

        canvas.save()
        canvas.clipRect(offsetX, gridTop, offsetX + (cols * colWidth), gridBottom)

        val totalRows = (activeEmojis.size + cols - 1) / cols
        if (rowHeight > 0f) {
            val startRow = maxOf(0, ((-scrollY) / rowHeight).toInt())
            val endRow = minOf(totalRows - 1, ((-scrollY + emojiAtlas.visibleGridHeight) / rowHeight).toInt() + 1)
            for (r in startRow..endRow) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    if (idx !in activeEmojis.indices) continue
                    val left = offsetX + (c * colWidth)
                    val top = gridTop + (r * rowHeight) + scrollY
                    val right = left + colWidth
                    val bottom = top + rowHeight

                    if (idx == activeGridIndex) {
                        scratchRect.set(
                            left + 2f * density,
                            top + 2f * density,
                            right - 2f * density,
                            bottom - 2f * density
                        )
                        canvas.drawRoundRect(scratchRect, 8f * density, 8f * density, keyPressedPaint)
                    }
                    val emojiStr = activeEmojis[idx]
                    val textY = top + (rowHeight / 2f) - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                    canvas.drawText(emojiStr, left + (colWidth / 2f), textY, primaryTextPaint)
                }
            }
        }
        canvas.restore()

        // 3. Draw control row (ABC, Recents, Space, Del)
        val ctrlRow = emojiAtlas.controlRowBounds
        for (i in 0 until 4) {
            val bounds = ctrlRow[i]
            val bgPaint = if (i == activeControlIndex) keyPressedPaint else keyBackgroundPaint
            canvas.drawRoundRect(bounds, 8f * density, 8f * density, bgPaint)
            val textY = bounds.centerY() - ((subTextPaint.descent() + subTextPaint.ascent()) / 2f)
            canvas.drawText(ctrlLabels[i], bounds.centerX(), textY, subTextPaint)
        }
    }
}
