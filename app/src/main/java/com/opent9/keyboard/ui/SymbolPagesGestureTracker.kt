package com.opent9.keyboard.ui

import kotlin.math.abs

/**
 * Handles gestures specific to Page 1 (numeric/operator scrollable column).
 */
class SymbolPagesGestureTracker(
    private val listener: TouchGestureListener
) {
    var page1ColumnScrollOffset = 0f
        private set
    private var page1ColumnStartScrollY = 0f
    var isPage1ColumnTouch = false
        private set
    var activeOperatorIndex: Int? = null
        private set

    fun reset() {
        isPage1ColumnTouch = false
        activeOperatorIndex = null
    }

    fun resetPage1Scroll() {
        page1ColumnScrollOffset = 0f
        activeOperatorIndex = null
    }

    fun onDown(x: Float, y: Float, keyAtlas: KeyAtlas): Boolean {
        if (keyAtlas.currentPage == KeyboardPage.PAGE_1_NUM_SYM && keyAtlas.page1ScrollContainerBounds.contains(x, y)) {
            isPage1ColumnTouch = true
            page1ColumnStartScrollY = page1ColumnScrollOffset
            activeOperatorIndex = keyAtlas.findPage1OperatorIndex(y, page1ColumnScrollOffset)
            listener.onTouchStateChanged(null)
            return true
        }

        isPage1ColumnTouch = false
        activeOperatorIndex = null
        return false
    }

    fun onMovePage1(dy: Float, touchSlopPx: Float, keyAtlas: KeyAtlas): Boolean {
        if (!isPage1ColumnTouch) return false
        if (abs(dy) >= touchSlopPx) {
            activeOperatorIndex = null
        }
        val newOffset = (page1ColumnStartScrollY + dy).coerceIn(keyAtlas.maxPage1ColumnScroll, 0f)
        page1ColumnScrollOffset = newOffset
        listener.onStripScroll(newOffset)
        return true
    }

    fun onUpPage1(
        curX: Float,
        curY: Float,
        dy: Float,
        distSq: Float,
        touchSlopPx: Float,
        flickDistanceMinPx: Float,
        keyAtlas: KeyAtlas
    ): Boolean {
        if (!isPage1ColumnTouch) return false
        if (distSq < touchSlopPx * touchSlopPx) {
            val opIdx = keyAtlas.findPage1OperatorIndex(curY, page1ColumnScrollOffset)
            if (opIdx != null && opIdx in keyAtlas.page1OperatorKeys.indices) {
                listener.onKeyTap(keyAtlas.page1OperatorKeys[opIdx], curX, curY)
            }
        } else if (distSq >= flickDistanceMinPx * flickDistanceMinPx) {
            page1ColumnScrollOffset = if (dy < 0) keyAtlas.maxPage1ColumnScroll else 0f
            listener.onStripScroll(page1ColumnScrollOffset)
        }
        isPage1ColumnTouch = false
        activeOperatorIndex = null
        listener.onTouchStateChanged(null)
        return true
    }
}
