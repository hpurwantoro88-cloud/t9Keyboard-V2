package com.opent9.keyboard.ui

import kotlin.math.abs

/**
 * Handles gestures specific to Page 1 (numeric/operator scrollable column)
 * and Page 2 (extended symbols category tabs and 3x3 layer swipe).
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
    var isPage2TabTouch = false
        private set

    fun reset() {
        isPage1ColumnTouch = false
        activeOperatorIndex = null
        isPage2TabTouch = false
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

        if (keyAtlas.currentPage == KeyboardPage.PAGE_2_EXT_SYM && y < keyAtlas.stripHeight) {
            isPage2TabTouch = true
            listener.onTouchStateChanged(null)
            return true
        }
        isPage2TabTouch = false
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

    fun checkPage2GridSwipeOnMove(
        dx: Float,
        dy: Float,
        touchDownX: Float,
        touchDownY: Float,
        flickDistanceMinPx: Float,
        keyAtlas: KeyAtlas,
        flickTriggered: Boolean,
        onFlickTriggered: () -> Unit
    ): Boolean {
        val inPage2Grid = (keyAtlas.currentPage == KeyboardPage.PAGE_2_EXT_SYM &&
                keyAtlas.symbolGrid3x3Bounds.contains(touchDownX, touchDownY))

        if (inPage2Grid && !flickTriggered && abs(dy) >= flickDistanceMinPx && abs(dy) > abs(dx) * 1.1f) {
            onFlickTriggered()
            val changed = if (dy < 0) keyAtlas.nextSymbolLayer() else keyAtlas.prevSymbolLayer()
            if (changed) {
                listener.onTouchStateChanged(null)
                listener.onSymbolLayerChanged(keyAtlas.activeSymbolLayerIndex)
                listener.onStripScroll(0f)
            }
            return true
        }
        return false
    }

    fun onUpPage1(
        curX: Float,
        curY: Float,
        dy: Float,
        distSq: Float,
        elapsed: Long,
        touchSlopPx: Float,
        flickDistanceMinPx: Float,
        longPressTimeoutMs: Long,
        keyAtlas: KeyAtlas
    ): Boolean {
        if (!isPage1ColumnTouch) return false
        if (distSq < touchSlopPx * touchSlopPx && elapsed < longPressTimeoutMs) {
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

    fun onUpPage2Tab(
        curX: Float,
        distSq: Float,
        touchSlopPx: Float,
        flickDistanceMinPx: Float,
        keyAtlas: KeyAtlas
    ): Boolean {
        if (!isPage2TabTouch) return false
        if (distSq < touchSlopPx * touchSlopPx || distSq < flickDistanceMinPx * flickDistanceMinPx) {
            val tabW = keyAtlas.totalWidth / 4f
            val tabIdx = (curX / tabW).toInt().coerceIn(0, 3)
            val changed = keyAtlas.setSymbolLayer(tabIdx)
            if (changed) {
                listener.onSymbolLayerChanged(keyAtlas.activeSymbolLayerIndex)
                listener.onStripScroll(0f)
            }
        }
        isPage2TabTouch = false
        return true
    }

    fun checkPage2GridSwipeOnUp(
        dx: Float,
        dy: Float,
        touchDownX: Float,
        touchDownY: Float,
        touchSlopPx: Float,
        keyAtlas: KeyAtlas
    ): Boolean {
        val inPage2Grid = (keyAtlas.currentPage == KeyboardPage.PAGE_2_EXT_SYM &&
                keyAtlas.symbolGrid3x3Bounds.contains(touchDownX, touchDownY))
        if (inPage2Grid && abs(dy) >= touchSlopPx && abs(dy) > abs(dx) * 1.1f) {
            val changed = if (dy < 0) keyAtlas.nextSymbolLayer() else keyAtlas.prevSymbolLayer()
            if (changed) {
                listener.onSymbolLayerChanged(keyAtlas.activeSymbolLayerIndex)
                listener.onStripScroll(0f)
            }
            return true
        }
        return false
    }
}
