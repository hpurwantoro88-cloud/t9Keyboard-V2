package com.opent9.keyboard.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import kotlin.math.atan2

enum class FlickDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

interface TouchGestureListener {
    fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float)
    fun onKeyFlick(key: KeyInfo, direction: FlickDirection)
    fun onKeyLongPress(key: KeyInfo)
    fun onKeyDeleteRepeat(isWordDelete: Boolean) {}
    fun onSpaceScrub(steps: Int) // +1 for DPAD_RIGHT, -1 for DPAD_LEFT
    fun onPillTap()
    fun onCandidateTap(index: Int)
    fun onCandidateLongPress(index: Int) {}
    fun onStripScroll(newScrollOffset: Float)
    fun onTouchStateChanged(activeKeyId: Int?)

    // Page 3 Emoji callbacks
    fun onEmojiCategoryTap(categoryIndex: Int) {}
    fun onEmojiTap(emoji: String) {}
    fun onEmojiControlTap(controlIndex: Int) {}
    fun onEmojiPageSwipe(direction: FlickDirection) {}
    fun onEmojiTouchStateChanged(tabIndex: Int?, gridIndex: Int?, controlIndex: Int?) {}

    // Page 2 Symbol layer callback
    fun onSymbolLayerChanged(layerIndex: Int) {}
}

class TouchGestureTracker(
    private val keyAtlas: KeyAtlas,
    private val listener: TouchGestureListener,
    handler: Handler? = null
) {
    companion object {
        const val TOUCH_SLOP_PX = 30f
        const val FLICK_DISTANCE_MIN_PX = 36f
        const val FLICK_TIME_WINDOW_MS = 180L
        const val LONG_PRESS_TIMEOUT_MS = 350L
        const val REPEAT_TICK_INTERVAL_MS = 50L
        const val ACCELERATED_DELETE_THRESHOLD_MS = 1200L
        const val SCRUB_STEP_PX = 32f
    }

    var longPressTimeoutMs: Long = LONG_PRESS_TIMEOUT_MS

    val touchSlopPx: Float
        get() = if (keyAtlas.density > 0f) 10f * keyAtlas.density else TOUCH_SLOP_PX

    val flickDistanceMinPx: Float
        get() = if (keyAtlas.density > 0f) 12f * keyAtlas.density else FLICK_DISTANCE_MIN_PX

    val scrubStepPx: Float
        get() = if (keyAtlas.density > 0f) (32f / 3f) * keyAtlas.density else SCRUB_STEP_PX

    private val actualHandler: Handler by lazy {
        handler ?: Handler(Looper.getMainLooper())
    }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var isCandidateStripTouch = false
    private var isPage2TabTouch = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private var activeKey: KeyInfo? = null
    private var longPressTriggered = false
    private var flickTriggered = false
    private var isScrubbing = false
    private var scrubAccumulator = 0f

    // Strip dragging state
    var stripScrollOffset = 0f
        private set
    var maxStripScroll = 0f // negative or 0
    private var stripStartScrollX = 0f

    // Page 1 operator column state
    var page1ColumnScrollOffset = 0f
        private set
    private var page1ColumnStartScrollY = 0f
    private var isPage1ColumnTouch = false
    var activeOperatorIndex: Int? = null
        private set

    // Page 3 Emoji state
    private var isEmojiPageTouch = false
    var activeEmojiTabIndex: Int? = null
        private set
    var activeEmojiGridIndex: Int? = null
        private set
    var activeEmojiControlIndex: Int? = null
        private set

    private val emojiRepeatDeleteRunnable = object : Runnable {
        override fun run() {
            if (isEmojiPageTouch && activeEmojiControlIndex == 3) {
                longPressTriggered = true
                listener.onEmojiControlTap(3)
                actualHandler.postDelayed(this, REPEAT_TICK_INTERVAL_MS)
            }
        }
    }

    // Long press runnable
    private val longPressRunnable = object : Runnable {
        override fun run() {
            activeKey?.let { key ->
                longPressTriggered = true
                if (key.type == KeyType.DEL) {
                    listener.onKeyDeleteRepeat(isWordDelete = false)
                    actualHandler.postDelayed(repeatDeleteRunnable, REPEAT_TICK_INTERVAL_MS)
                } else {
                    listener.onKeyLongPress(key)
                }
            }
        }
    }

    private val repeatDeleteRunnable = object : Runnable {
        override fun run() {
            activeKey?.let { key ->
                if (key.type == KeyType.DEL) {
                    val elapsed = System.currentTimeMillis() - touchDownTime
                    val isWordDelete = elapsed >= ACCELERATED_DELETE_THRESHOLD_MS
                    listener.onKeyDeleteRepeat(isWordDelete = isWordDelete)
                    actualHandler.postDelayed(this, REPEAT_TICK_INTERVAL_MS)
                }
            }
        }
    }

    var activeCandidateIndex: Int? = null
        private set

    private val candidateLongPressRunnable = object : Runnable {
        override fun run() {
            activeCandidateIndex?.let { candIdx ->
                longPressTriggered = true
                listener.onCandidateLongPress(candIdx)
            }
        }
    }

    fun onTouchEvent(event: MotionEvent, candidateHitTest: (Float) -> Int?): Boolean {
        val x = event.x
        val y = event.y
        val now = System.currentTimeMillis()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                touchDownX = x
                touchDownY = y
                touchDownTime = now
                longPressTriggered = false
                flickTriggered = false
                isScrubbing = false
                scrubAccumulator = 0f

                if (keyAtlas.currentPage == KeyboardPage.PAGE_3_EMOJI) {
                    isEmojiPageTouch = true
                    isPage1ColumnTouch = false
                    isCandidateStripTouch = false
                    activeKey = null
                    val tabIdx = keyAtlas.emojiAtlas.findCategoryTabAt(x, y)
                    val gridIdx = keyAtlas.emojiAtlas.findEmojiIndexAt(x, y)
                    val ctrlIdx = keyAtlas.emojiAtlas.findControlIndexAt(x, y)
                    activeEmojiTabIndex = tabIdx
                    activeEmojiGridIndex = gridIdx
                    activeEmojiControlIndex = ctrlIdx
                    listener.onEmojiTouchStateChanged(tabIdx, gridIdx, ctrlIdx)
                    listener.onTouchStateChanged(null)

                    if (ctrlIdx == 3) { // DEL button on emoji page
                        actualHandler.removeCallbacks(emojiRepeatDeleteRunnable)
                        actualHandler.postDelayed(emojiRepeatDeleteRunnable, longPressTimeoutMs)
                    }
                    return true
                }

                isEmojiPageTouch = false
                activeEmojiTabIndex = null
                activeEmojiGridIndex = null
                activeEmojiControlIndex = null

                if (keyAtlas.currentPage == KeyboardPage.PAGE_1_NUM_SYM && keyAtlas.page1ScrollContainerBounds.contains(x, y)) {
                    isPage1ColumnTouch = true
                    page1ColumnStartScrollY = page1ColumnScrollOffset
                    activeOperatorIndex = keyAtlas.findPage1OperatorIndex(y, page1ColumnScrollOffset)
                    activeKey = null
                    listener.onTouchStateChanged(null)
                    return true
                }

                isPage1ColumnTouch = false
                activeOperatorIndex = null

                if (keyAtlas.currentPage == KeyboardPage.PAGE_2_EXT_SYM && y < keyAtlas.stripHeight) {
                    isPage2TabTouch = true
                    isCandidateStripTouch = false
                    activeKey = null
                    listener.onTouchStateChanged(null)
                    return true
                }
                isPage2TabTouch = false

                if (y < keyAtlas.stripHeight && keyAtlas.currentPage != KeyboardPage.PAGE_1_NUM_SYM) {
                    // Candidate Strip Touch
                    isCandidateStripTouch = true
                    activeKey = null
                    stripStartScrollX = stripScrollOffset
                    activeCandidateIndex = null
                    if (x >= keyAtlas.pillBounds.right) {
                        val candIdx = candidateHitTest(x)
                        if (candIdx != null) {
                            activeCandidateIndex = candIdx
                            actualHandler.removeCallbacks(candidateLongPressRunnable)
                            actualHandler.postDelayed(candidateLongPressRunnable, longPressTimeoutMs)
                        }
                    }
                } else {
                    // Keypad Touch
                    isCandidateStripTouch = false
                    activeKey = keyAtlas.findKeyAt(x, y)
                    listener.onTouchStateChanged(activeKey?.id)
                    actualHandler.removeCallbacks(longPressRunnable)
                    actualHandler.removeCallbacks(repeatDeleteRunnable)
                    actualHandler.postDelayed(longPressRunnable, longPressTimeoutMs)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val actionIdx = event.actionIndex
                val px = event.getX(actionIdx)
                val py = event.getY(actionIdx)

                // If previous touch had an active keypad key and hasn't fired a long press or flick,
                // commit the previous key as a tap immediately so rapid thumb alternation works seamlessly.
                val prevKey = activeKey
                if (prevKey != null && !longPressTriggered && !flickTriggered && !isScrubbing &&
                    !isCandidateStripTouch && !isEmojiPageTouch && !isPage1ColumnTouch && !isPage2TabTouch
                ) {
                    actualHandler.removeCallbacks(longPressRunnable)
                    actualHandler.removeCallbacks(repeatDeleteRunnable)
                    listener.onKeyTap(prevKey, touchDownX, touchDownY)
                }

                activePointerId = event.getPointerId(actionIdx)
                touchDownX = px
                touchDownY = py
                touchDownTime = now
                longPressTriggered = false
                flickTriggered = false
                isScrubbing = false
                scrubAccumulator = 0f

                if (py < keyAtlas.stripHeight && keyAtlas.currentPage != KeyboardPage.PAGE_1_NUM_SYM) {
                    isCandidateStripTouch = true
                    activeKey = null
                    stripStartScrollX = stripScrollOffset
                    activeCandidateIndex = null
                    if (px >= keyAtlas.pillBounds.right) {
                        val candIdx = candidateHitTest(px)
                        if (candIdx != null) {
                            activeCandidateIndex = candIdx
                            actualHandler.removeCallbacks(candidateLongPressRunnable)
                            actualHandler.postDelayed(candidateLongPressRunnable, longPressTimeoutMs)
                        }
                    }
                } else {
                    isCandidateStripTouch = false
                    activeKey = keyAtlas.findKeyAt(px, py)
                    listener.onTouchStateChanged(activeKey?.id)
                    actualHandler.removeCallbacks(longPressRunnable)
                    actualHandler.postDelayed(longPressRunnable, longPressTimeoutMs)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val actionIdx = event.actionIndex
                val pointerId = event.getPointerId(actionIdx)
                if (pointerId == activePointerId) {
                    val px = event.getX(actionIdx)
                    val py = event.getY(actionIdx)
                    val liftedKey = activeKey ?: keyAtlas.findKeyAt(px, py)

                    if (liftedKey != null && !longPressTriggered && !flickTriggered && !isScrubbing) {
                        val distSq = (px - touchDownX) * (px - touchDownX) + (py - touchDownY) * (py - touchDownY)
                        if (distSq >= flickDistanceMinPx * flickDistanceMinPx) {
                            val direction = disambiguateFlick(px - touchDownX, py - touchDownY)
                            if (isFlickSupported(liftedKey, direction)) {
                                listener.onKeyFlick(liftedKey, direction)
                            } else if (!longPressTriggered) {
                                listener.onKeyTap(liftedKey, px, py)
                            }
                        } else if (!longPressTriggered) {
                            listener.onKeyTap(liftedKey, px, py)
                        }
                    }
                    actualHandler.removeCallbacks(longPressRunnable)
                    actualHandler.removeCallbacks(repeatDeleteRunnable)
                    activeKey = null
                    listener.onTouchStateChanged(null)
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIdx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    event.findPointerIndex(activePointerId)
                } else -1
                val curX = if (pointerIdx >= 0) event.getX(pointerIdx) else x
                val curY = if (pointerIdx >= 0) event.getY(pointerIdx) else y
                val dx = curX - touchDownX
                val dy = curY - touchDownY
                val distSq = dx * dx + dy * dy

                if (isEmojiPageTouch) {
                    if (distSq >= touchSlopPx * touchSlopPx) {
                        actualHandler.removeCallbacks(emojiRepeatDeleteRunnable)
                        activeEmojiTabIndex = null
                        activeEmojiGridIndex = null
                        activeEmojiControlIndex = null
                        listener.onEmojiTouchStateChanged(null, null, null)

                        if (!flickTriggered && Math.abs(dx) >= flickDistanceMinPx) {
                            flickTriggered = true
                            val direction = if (dx < 0) FlickDirection.LEFT else FlickDirection.RIGHT
                            listener.onEmojiPageSwipe(direction)
                        }
                    }
                    return true
                }

                if (isPage1ColumnTouch) {
                    if (Math.abs(dy) >= touchSlopPx) {
                        activeOperatorIndex = null
                    }
                    val newOffset = (page1ColumnStartScrollY + dy).coerceIn(keyAtlas.maxPage1ColumnScroll, 0f)
                    page1ColumnScrollOffset = newOffset
                    listener.onStripScroll(newOffset)
                    return true
                }

                if (isCandidateStripTouch) {
                    if (distSq >= touchSlopPx * touchSlopPx) {
                        actualHandler.removeCallbacks(candidateLongPressRunnable)
                        activeCandidateIndex = null
                    }
                    // Drag candidate strip
                    val newOffset = (stripStartScrollX + dx).coerceIn(maxStripScroll, 0f)
                    stripScrollOffset = newOffset
                    listener.onStripScroll(newOffset)
                } else {
                    // Keypad Area
                    val key = activeKey
                    if (key != null && key.type == KeyType.SPACE_0) {
                        // Spacebar Cursor Scrubbing (Trackpad Mode)
                        if (Math.abs(dx) >= touchSlopPx) {
                            if (!isScrubbing) {
                                isScrubbing = true
                                actualHandler.removeCallbacks(longPressRunnable)
                            }
                            val scrubDelta = dx - scrubAccumulator
                            if (Math.abs(scrubDelta) >= scrubStepPx) {
                                val steps = (scrubDelta / scrubStepPx).toInt()
                                scrubAccumulator += steps * scrubStepPx
                                listener.onSpaceScrub(steps)
                            }
                        }
                    } else if (distSq >= touchSlopPx * touchSlopPx) {
                        // Moved beyond touch slop: cancel long press
                        actualHandler.removeCallbacks(longPressRunnable)
                        actualHandler.removeCallbacks(repeatDeleteRunnable)

                        val inPage2Grid = (keyAtlas.currentPage == KeyboardPage.PAGE_2_EXT_SYM &&
                                keyAtlas.symbolGrid3x3Bounds.contains(touchDownX, touchDownY))

                        if (inPage2Grid && !flickTriggered && Math.abs(dy) >= flickDistanceMinPx && Math.abs(dy) > Math.abs(dx) * 1.1f) {
                            // Vertical swipe between 3x3 symbol layers
                            flickTriggered = true
                            longPressTriggered = false
                            val changed = if (dy < 0) keyAtlas.nextSymbolLayer() else keyAtlas.prevSymbolLayer()
                            if (changed) {
                                listener.onTouchStateChanged(null)
                                listener.onSymbolLayerChanged(keyAtlas.activeSymbolLayerIndex)
                                listener.onStripScroll(0f)
                            }
                        } else if (!flickTriggered && !isScrubbing && key != null && distSq >= flickDistanceMinPx * flickDistanceMinPx) {
                            val direction = disambiguateFlick(dx, dy)
                            if (isFlickSupported(key, direction)) {
                                flickTriggered = true
                                longPressTriggered = false
                                listener.onKeyFlick(key, direction)
                            }
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                actualHandler.removeCallbacks(longPressRunnable)
                actualHandler.removeCallbacks(repeatDeleteRunnable)
                val pointerIdx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    event.findPointerIndex(activePointerId)
                } else -1
                val curX = if (pointerIdx >= 0) event.getX(pointerIdx) else x
                val curY = if (pointerIdx >= 0) event.getY(pointerIdx) else y
                val elapsed = now - touchDownTime
                val dx = curX - touchDownX
                val dy = curY - touchDownY
                val distSq = dx * dx + dy * dy

                if (isEmojiPageTouch) {
                    actualHandler.removeCallbacks(emojiRepeatDeleteRunnable)
                    val tabIdx = activeEmojiTabIndex
                    val gridIdx = activeEmojiGridIndex
                    val ctrlIdx = activeEmojiControlIndex
                    isEmojiPageTouch = false
                    activeEmojiTabIndex = null
                    activeEmojiGridIndex = null
                    activeEmojiControlIndex = null
                    listener.onEmojiTouchStateChanged(null, null, null)

                    if (!flickTriggered && !longPressTriggered) {
                        if (distSq < touchSlopPx * touchSlopPx) {
                            if (tabIdx != null) {
                                listener.onEmojiCategoryTap(tabIdx)
                            } else if (gridIdx != null) {
                                val activeEmojis = keyAtlas.emojiAtlas.categoryEmojis[keyAtlas.emojiAtlas.activeCategoryIndex]
                                if (gridIdx in activeEmojis.indices) {
                                    val codepoint = activeEmojis[gridIdx]
                                    val emojiStr = keyAtlas.emojiAtlas.getEmojiString(codepoint)
                                    keyAtlas.emojiAtlas.recordRecentEmoji(codepoint)
                                    listener.onEmojiTap(emojiStr)
                                }
                            } else if (ctrlIdx != null) {
                                listener.onEmojiControlTap(ctrlIdx)
                            }
                        } else if (distSq >= flickDistanceMinPx * flickDistanceMinPx) {
                            val direction = if (dx < 0) FlickDirection.LEFT else FlickDirection.RIGHT
                            listener.onEmojiPageSwipe(direction)
                        }
                    }
                    flickTriggered = false
                    longPressTriggered = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    return true
                }

                if (isPage1ColumnTouch) {
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
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    return true
                }

                if (isPage2TabTouch) {
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
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    return true
                }

                if (isCandidateStripTouch) {
                    actualHandler.removeCallbacks(candidateLongPressRunnable)
                    if (distSq < touchSlopPx * touchSlopPx && !longPressTriggered) {
                        // Tap in strip
                        if (curX < keyAtlas.pillBounds.right) {
                            listener.onPillTap()
                        } else {
                            val candIdx = candidateHitTest(curX)
                            if (candIdx != null) {
                                listener.onCandidateTap(candIdx)
                            }
                        }
                    }
                    activeCandidateIndex = null
                } else {
                    val key = activeKey
                    listener.onTouchStateChanged(null)

                    val inPage2Grid = (keyAtlas.currentPage == KeyboardPage.PAGE_2_EXT_SYM &&
                            keyAtlas.symbolGrid3x3Bounds.contains(touchDownX, touchDownY))

                    if (key != null && !isScrubbing && !flickTriggered) {
                        if (inPage2Grid && Math.abs(dy) >= touchSlopPx && Math.abs(dy) > Math.abs(dx) * 1.1f) {
                            // Vertical scroll between 3x3 symbol layers on release
                            val changed = if (dy < 0) keyAtlas.nextSymbolLayer() else keyAtlas.prevSymbolLayer()
                            if (changed) {
                                listener.onSymbolLayerChanged(keyAtlas.activeSymbolLayerIndex)
                                listener.onStripScroll(0f)
                            }
                        } else if (distSq >= flickDistanceMinPx * flickDistanceMinPx) {
                            // Deliberate flick on release (e.g. fast flick or mouse drag)
                            val direction = disambiguateFlick(dx, dy)
                            if (isFlickSupported(key, direction)) {
                                listener.onKeyFlick(key, direction)
                            } else if (!longPressTriggered) {
                                listener.onKeyTap(key, curX, curY)
                            }
                        } else if (!longPressTriggered) {
                            // Primary Tap
                            listener.onKeyTap(key, curX, curY)
                        }
                    }
                }

                activeKey = null
                activePointerId = MotionEvent.INVALID_POINTER_ID
                longPressTriggered = false
                flickTriggered = false
                isScrubbing = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                actualHandler.removeCallbacks(longPressRunnable)
                actualHandler.removeCallbacks(repeatDeleteRunnable)
                actualHandler.removeCallbacks(emojiRepeatDeleteRunnable)
                actualHandler.removeCallbacks(candidateLongPressRunnable)
                activeCandidateIndex = null
                isEmojiPageTouch = false
                activeEmojiTabIndex = null
                activeEmojiGridIndex = null
                activeEmojiControlIndex = null
                listener.onEmojiTouchStateChanged(null, null, null)
                isPage1ColumnTouch = false
                activeOperatorIndex = null
                listener.onTouchStateChanged(null)
                activeKey = null
                activePointerId = MotionEvent.INVALID_POINTER_ID
                longPressTriggered = false
                flickTriggered = false
                isScrubbing = false
                return true
            }
        }
        return false
    }

    fun isFlickSupported(key: KeyInfo, direction: FlickDirection): Boolean {
        when (key.type) {
            KeyType.DEL -> return direction != FlickDirection.RIGHT
            KeyType.ENTER -> return direction == FlickDirection.UP || direction == FlickDirection.DOWN
            KeyType.SPACE_0 -> return direction == FlickDirection.DOWN
            else -> {}
        }

        return when (keyAtlas.currentPage) {
            KeyboardPage.PAGE_0_TEXT -> {
                when (key.id) {
                    0 -> direction == FlickDirection.LEFT || direction == FlickDirection.RIGHT || direction == FlickDirection.DOWN
                    1, 2, 4, 5, 6, 8, 9, 10 -> false // Digits 2..9: no flicks on Page 0 (numbers via long-press or Page 1)
                    7 -> direction == FlickDirection.UP // SHIFT
                    12 -> direction == FlickDirection.DOWN || direction == FlickDirection.UP // ?123
                    13 -> direction == FlickDirection.UP || direction == FlickDirection.DOWN // LANG (UP = Emoji, DOWN = Lang)
                    15 -> true // 😊 / .
                    else -> false
                }
            }
            KeyboardPage.PAGE_1_NUM_SYM -> {
                key.id == 7 || key.id == 15
            }
            KeyboardPage.PAGE_2_EXT_SYM -> {
                key.type == KeyType.DUAL_SYM
            }
            KeyboardPage.PAGE_3_EMOJI -> false
        }
    }

    /**
     * 4-Quadrant Disambiguation as per PRD Section 4.2
     * Angle theta = atan2(-dy, dx) in degrees:
     * Right: [-45, 45)
     * Up: [45, 135)
     * Left: [135, 225) or < -135
     * Down: [-135, -45)
     */
    fun disambiguateFlick(dx: Float, dy: Float): FlickDirection {
        val angleRad = atan2(-dy.toDouble(), dx.toDouble())
        var angleDeg = Math.toDegrees(angleRad)
        if (angleDeg < -180.0) angleDeg += 360.0

        return when {
            angleDeg in -45.0..45.0 -> FlickDirection.RIGHT
            angleDeg in 45.0..135.0 -> FlickDirection.UP
            angleDeg in -135.0..-45.0 -> FlickDirection.DOWN
            else -> FlickDirection.LEFT
        }
    }

    fun resetScroll() {
        stripScrollOffset = 0f
    }

    fun resetPage1Scroll() {
        page1ColumnScrollOffset = 0f
        activeOperatorIndex = null
        isPage1ColumnTouch = false
    }
}
