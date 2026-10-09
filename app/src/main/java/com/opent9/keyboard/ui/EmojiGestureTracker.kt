package com.opent9.keyboard.ui

import android.os.Handler
import kotlin.math.abs

/**
 * Tracks touch gestures on Page 3 (Emoji Page): tab selection, grid vertical scrolling,
 * control buttons (ABC, Recents, Space, Del repeat), and swipe page navigation.
 */
class EmojiGestureTracker(
    private val listener: TouchGestureListener,
    private val handler: Handler
) {
    var isEmojiPageTouch = false
        private set
    var emojiScrollOffset = 0f
        private set
    private var emojiStartScrollY = 0f
    var isEmojiScrolling = false
        private set
    var activeEmojiTabIndex: Int? = null
        private set
    var activeEmojiGridIndex: Int? = null
        private set
    var activeEmojiControlIndex: Int? = null
        private set

    private var repeatDeleteFired = false

    private val emojiRepeatDeleteRunnable = object : Runnable {
        override fun run() {
            if (isEmojiPageTouch && activeEmojiControlIndex == 3) {
                repeatDeleteFired = true
                listener.onEmojiControlTap(3)
                handler.postDelayed(this, TouchGestureTracker.REPEAT_TICK_INTERVAL_MS)
            }
        }
    }

    fun onDown(
        x: Float,
        y: Float,
        emojiAtlas: EmojiAtlas,
        longPressTimeoutMs: Long
    ) {
        isEmojiPageTouch = true
        isEmojiScrolling = false
        repeatDeleteFired = false
        emojiStartScrollY = emojiScrollOffset
        val tabIdx = emojiAtlas.findCategoryTabAt(x, y)
        val gridIdx = emojiAtlas.findEmojiIndexAt(x, y, emojiScrollOffset)
        val ctrlIdx = emojiAtlas.findControlIndexAt(x, y)
        activeEmojiTabIndex = tabIdx
        activeEmojiGridIndex = gridIdx
        activeEmojiControlIndex = ctrlIdx
        listener.onEmojiTouchStateChanged(tabIdx, gridIdx, ctrlIdx)

        if (ctrlIdx == 3) { // DEL button on emoji page
            handler.removeCallbacks(emojiRepeatDeleteRunnable)
            handler.postDelayed(emojiRepeatDeleteRunnable, longPressTimeoutMs)
        }
    }

    fun onMove(
        touchDownY: Float,
        dx: Float,
        dy: Float,
        distSq: Float,
        touchSlopPx: Float,
        flickDistanceMinPx: Float,
        emojiAtlas: EmojiAtlas,
        flickTriggered: Boolean,
        onFlickTriggered: (Boolean) -> Unit
    ) {
        if (!isEmojiPageTouch) return
        val absDx = abs(dx)
        val absDy = abs(dy)

        if (distSq >= touchSlopPx * touchSlopPx) {
            handler.removeCallbacks(emojiRepeatDeleteRunnable)
            activeEmojiTabIndex = null
            activeEmojiGridIndex = null
            activeEmojiControlIndex = null
            listener.onEmojiTouchStateChanged(null, null, null)

            val inGridArea = touchDownY >= emojiAtlas.gridTop && touchDownY < emojiAtlas.gridBottom
            if (inGridArea && (isEmojiScrolling || absDy > absDx * 0.8f)) {
                isEmojiScrolling = true
                val maxScroll = emojiAtlas.computeMaxScroll(emojiAtlas.activeCategoryIndex)
                val newOffset = (emojiStartScrollY + dy).coerceIn(maxScroll, 0f)
                emojiScrollOffset = newOffset
                listener.onStripScroll(newOffset)
            } else if (!isEmojiScrolling && !flickTriggered && absDx >= flickDistanceMinPx && absDx > absDy * 1.2f) {
                onFlickTriggered(true)
                val direction = if (dx < 0) FlickDirection.LEFT else FlickDirection.RIGHT
                listener.onEmojiPageSwipe(direction)
            }
        }
    }

    fun onUp(
        dx: Float,
        dy: Float,
        distSq: Float,
        touchSlopPx: Float,
        flickDistanceMinPx: Float,
        flickTriggered: Boolean,
        emojiAtlas: EmojiAtlas
    ) {
        if (!isEmojiPageTouch) return
        handler.removeCallbacks(emojiRepeatDeleteRunnable)
        val tabIdx = activeEmojiTabIndex
        val gridIdx = activeEmojiGridIndex
        val ctrlIdx = activeEmojiControlIndex
        val wasScrolling = isEmojiScrolling
        isEmojiPageTouch = false
        isEmojiScrolling = false
        activeEmojiTabIndex = null
        activeEmojiGridIndex = null
        activeEmojiControlIndex = null
        listener.onEmojiTouchStateChanged(null, null, null)

        if (!wasScrolling && !flickTriggered && !repeatDeleteFired) {
            if (distSq < touchSlopPx * touchSlopPx) {
                if (tabIdx != null) {
                    listener.onEmojiCategoryTap(tabIdx)
                } else if (gridIdx != null) {
                    val activeEmojis = emojiAtlas.getActiveEmojiList()
                    if (gridIdx in activeEmojis.indices) {
                        val emojiStr = activeEmojis[gridIdx]
                        emojiAtlas.recordRecentEmoji(emojiStr)
                        listener.onEmojiTap(emojiStr)
                    }
                } else if (ctrlIdx != null) {
                    listener.onEmojiControlTap(ctrlIdx)
                }
            } else if (distSq >= flickDistanceMinPx * flickDistanceMinPx && abs(dx) > abs(dy) * 1.2f) {
                val direction = if (dx < 0) FlickDirection.LEFT else FlickDirection.RIGHT
                listener.onEmojiPageSwipe(direction)
            }
        }
        repeatDeleteFired = false
    }

    fun resetEmojiScroll() {
        emojiScrollOffset = 0f
        isEmojiScrolling = false
    }

    fun cancel() {
        handler.removeCallbacks(emojiRepeatDeleteRunnable)
        isEmojiPageTouch = false
        isEmojiScrolling = false
        activeEmojiTabIndex = null
        activeEmojiGridIndex = null
        activeEmojiControlIndex = null
        repeatDeleteFired = false
    }
}
