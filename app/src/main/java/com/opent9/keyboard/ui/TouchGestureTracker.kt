package com.opent9.keyboard.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import kotlin.math.abs


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

    var isSpaceScrubEnabled: Boolean = true
    var scrubActivationDistanceDp: Float = 22f
    var spaceScrubRequireHold: Boolean = false
    var spaceScrubHoldDelayMs: Long = 150L
    var spaceScrubAspectRatio: Float = 1.3f

    val spaceScrubTracker = SpaceScrubTracker()

    val scrubStepsDispatched: Int
        get() = spaceScrubTracker.scrubStepsDispatched

    val touchSlopPx: Float
        get() = if (keyAtlas.density > 0f) 10f * keyAtlas.density else TOUCH_SLOP_PX

    val flickDistanceMinPx: Float
        get() = if (keyAtlas.density > 0f) 12f * keyAtlas.density else FLICK_DISTANCE_MIN_PX

    val scrubStepPx: Float
        get() = if (keyAtlas.density > 0f) (32f / 3f) * keyAtlas.density else SCRUB_STEP_PX

    val spaceScrubActivationPx: Float
        get() = if (keyAtlas.density > 0f) scrubActivationDistanceDp * keyAtlas.density else scrubActivationDistanceDp * 3f

    private val actualHandler: Handler by lazy {
        handler ?: Handler(Looper.getMainLooper())
    }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private var activeKey: KeyInfo? = null
    private var longPressTriggered = false
    private var flickTriggered = false
    val isScrubbing: Boolean
        get() = spaceScrubTracker.isScrubbing

    // Sub-trackers
    val candidateStripTracker = CandidateStripGestureTracker(listener, actualHandler)
    val emojiGestureTracker = EmojiGestureTracker(listener, actualHandler)
    val symbolPagesTracker = SymbolPagesGestureTracker(listener)
    private val repeatDeleteHandler by lazy { RepeatDeleteHandler(listener, actualHandler) }

    // Strip dragging state
    val stripScrollOffset: Float
        get() = candidateStripTracker.stripScrollOffset

    var maxStripScroll: Float
        get() = candidateStripTracker.maxStripScroll
        set(value) { candidateStripTracker.maxStripScroll = value }

    val activeCandidateIndex: Int?
        get() = candidateStripTracker.activeCandidateIndex

    // Page 1 operator column state
    val page1ColumnScrollOffset: Float
        get() = symbolPagesTracker.page1ColumnScrollOffset

    val activeOperatorIndex: Int?
        get() = symbolPagesTracker.activeOperatorIndex

    // Page 3 Emoji state
    val emojiScrollOffset: Float
        get() = emojiGestureTracker.emojiScrollOffset

    val activeEmojiTabIndex: Int?
        get() = emojiGestureTracker.activeEmojiTabIndex

    val activeEmojiGridIndex: Int?
        get() = emojiGestureTracker.activeEmojiGridIndex

    val activeEmojiControlIndex: Int?
        get() = emojiGestureTracker.activeEmojiControlIndex

    fun resetEmojiScroll() {
        emojiGestureTracker.resetEmojiScroll()
    }

    fun resetScroll() {
        candidateStripTracker.resetScroll()
    }

    // Long press runnable (Reflection target for TouchGestureTrackerTest)
    private val longPressRunnable = object : Runnable {
        override fun run() {
            activeKey?.let { key ->
                if (!FlickDisambiguator.isLongPressSupported(key, keyAtlas.currentPage)) {
                    return
                }
                longPressTriggered = true
                if (key.type == KeyType.DEL) {
                    repeatDeleteHandler.onLongPressDel(repeatDeleteRunnable)
                } else {
                    listener.onKeyLongPress(key)
                }
            }
        }
    }

    // Repeat delete runnable (Reflection target for TouchGestureTrackerTest)
    private val repeatDeleteRunnable = object : Runnable {
        override fun run() {
            activeKey?.let { key ->
                if (key.type == KeyType.DEL) {
                    repeatDeleteHandler.onRepeatTick(touchDownTime, this)
                }
            }
        }
    }

    fun onTouchEvent(event: MotionEvent, candidateHitTest: (Float) -> Int?): Boolean {
        val x = event.x
        val y = event.y
        val now = System.currentTimeMillis()

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleActionDown(event, x, y, now, candidateHitTest)
            MotionEvent.ACTION_POINTER_DOWN -> handleActionPointerDown(event, candidateHitTest, now)
            MotionEvent.ACTION_POINTER_UP -> handleActionPointerUp(event)
            MotionEvent.ACTION_MOVE -> handleActionMove(event, x, y, now)
            MotionEvent.ACTION_UP -> handleActionUp(event, x, y, candidateHitTest)
            MotionEvent.ACTION_CANCEL -> {
                cancelAllGestures()
                true
            }
            else -> false
        }
    }

    private fun handleActionDown(event: MotionEvent, x: Float, y: Float, now: Long, candidateHitTest: (Float) -> Int?): Boolean {
        activePointerId = event.getPointerId(0)
        touchDownX = x
        touchDownY = y
        touchDownTime = now
        longPressTriggered = false
        flickTriggered = false
        spaceScrubTracker.reset()

        if (keyAtlas.currentPage == KeyboardPage.PAGE_4_EMOJI) {
            symbolPagesTracker.reset()
            activeKey = null
            listener.onTouchStateChanged(null)
            emojiGestureTracker.onDown(x, y, keyAtlas.emojiAtlas, longPressTimeoutMs, now)
            return true
        }

        if (symbolPagesTracker.onDown(x, y, keyAtlas)) {
            activeKey = null
            return true
        }

        if (y < keyAtlas.stripHeight && keyAtlas.currentPage == KeyboardPage.PAGE_0_TEXT) {
            activeKey = null
            candidateStripTracker.onDown(x, keyAtlas, longPressTimeoutMs, candidateHitTest)
        } else {
            val foundKey = keyAtlas.findKeyAt(x, y)
            activeKey = foundKey
            listener.onTouchStateChanged(foundKey?.id)
            repeatDeleteHandler.cancel(longPressRunnable, repeatDeleteRunnable)
            if (foundKey != null && FlickDisambiguator.isLongPressSupported(foundKey, keyAtlas.currentPage)) {
                actualHandler.postDelayed(longPressRunnable, longPressTimeoutMs)
            }
        }
        return true
    }

    private fun handleActionPointerDown(event: MotionEvent, candidateHitTest: (Float) -> Int?, now: Long): Boolean {
        if (emojiGestureTracker.isEmojiPageTouch || symbolPagesTracker.isPage1ColumnTouch) {
            return true
        }

        val actionIdx = event.actionIndex
        val px = event.getX(actionIdx)
        val py = event.getY(actionIdx)

        if (candidateStripTracker.isCandidateStripTouch) {
            if (py < keyAtlas.stripHeight && keyAtlas.currentPage == KeyboardPage.PAGE_0_TEXT) {
                return true
            }
            candidateStripTracker.cancel()
        }

        val prevKey = activeKey
        if (prevKey != null && !longPressTriggered && !flickTriggered && !isScrubbing) {
            repeatDeleteHandler.cancel(longPressRunnable, repeatDeleteRunnable)
            listener.onKeyTap(prevKey, touchDownX, touchDownY)
        }

        activePointerId = event.getPointerId(actionIdx)
        touchDownX = px
        touchDownY = py
        touchDownTime = now
        longPressTriggered = false
        flickTriggered = false
        spaceScrubTracker.reset()

        if (keyAtlas.currentPage == KeyboardPage.PAGE_4_EMOJI) {
            activeKey = null
            listener.onTouchStateChanged(null)
            emojiGestureTracker.onDown(px, py, keyAtlas.emojiAtlas, longPressTimeoutMs, now)
            return true
        }

        if (symbolPagesTracker.onDown(px, py, keyAtlas)) {
            activeKey = null
            return true
        }

        if (py < keyAtlas.stripHeight && keyAtlas.currentPage == KeyboardPage.PAGE_0_TEXT) {
            activeKey = null
            candidateStripTracker.onDown(px, keyAtlas, longPressTimeoutMs, candidateHitTest)
        } else {
            val foundKey = keyAtlas.findKeyAt(px, py)
            activeKey = foundKey
            listener.onTouchStateChanged(foundKey?.id)
            repeatDeleteHandler.cancel(longPressRunnable, repeatDeleteRunnable)
            if (foundKey != null && FlickDisambiguator.isLongPressSupported(foundKey, keyAtlas.currentPage)) {
                actualHandler.postDelayed(longPressRunnable, longPressTimeoutMs)
            }
        }
        return true
    }


    private fun handleActionPointerUp(event: MotionEvent): Boolean {
        val actionIdx = event.actionIndex
        val pointerId = event.getPointerId(actionIdx)
        if (pointerId == activePointerId) {
            val px = event.getX(actionIdx)
            val py = event.getY(actionIdx)
            val liftedKey = activeKey

            if (liftedKey != null) {
                val dx = px - touchDownX
                val dy = py - touchDownY
                handleKeypadUp(liftedKey, px, py, dx, dy, dx * dx + dy * dy)
            }
            repeatDeleteHandler.cancel(longPressRunnable, repeatDeleteRunnable)
            resetTouchState()
            listener.onTouchStateChanged(null)
        }
        return true
    }

    private fun handleActionMove(event: MotionEvent, x: Float, y: Float, now: Long): Boolean {
        val pointerIdx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
            event.findPointerIndex(activePointerId)
        } else -1
        val curX = if (pointerIdx >= 0) event.getX(pointerIdx) else x
        val curY = if (pointerIdx >= 0) event.getY(pointerIdx) else y
        val dx = curX - touchDownX
        val dy = curY - touchDownY
        val distSq = dx * dx + dy * dy
        val elapsed = maxOf(now - touchDownTime, if (event.downTime > 0) event.eventTime - event.downTime else 0L)

        if (emojiGestureTracker.isEmojiPageTouch) {
            emojiGestureTracker.onMove(
                touchDownY = touchDownY, dx = dx, dy = dy, distSq = distSq,
                touchSlopPx = touchSlopPx, flickDistanceMinPx = flickDistanceMinPx,
                emojiAtlas = keyAtlas.emojiAtlas, flickTriggered = flickTriggered,
                onFlickTriggered = { flickTriggered = it }
            )
            return true
        }

        if (symbolPagesTracker.onMovePage1(dy, touchSlopPx, keyAtlas)) {
            return true
        }

        if (candidateStripTracker.isCandidateStripTouch) {
            candidateStripTracker.onMove(dx, distSq, touchSlopPx)
        } else {
            val key = activeKey
            if (key != null && key.type == KeyType.SPACE_0) {
                val absDx = abs(dx)
                val absDy = abs(dy)
                if (distSq >= touchSlopPx * touchSlopPx) {
                    val mayNeedHoldForScrub = isSpaceScrubEnabled && spaceScrubRequireHold && absDx >= absDy * spaceScrubAspectRatio
                    if (!mayNeedHoldForScrub || absDy > absDx) {
                        repeatDeleteHandler.cancel(longPressRunnable, repeatDeleteRunnable)
                    }
                }

                if (isSpaceScrubEnabled) {
                    spaceScrubTracker.onMove(
                        dx = dx, dy = dy, elapsed = elapsed, aspectRatio = spaceScrubAspectRatio,
                        requireHold = spaceScrubRequireHold, holdDelayMs = spaceScrubHoldDelayMs,
                        activationDistancePx = spaceScrubActivationPx, stepPx = scrubStepPx,
                        onScrubStart = { actualHandler.removeCallbacks(longPressRunnable) },
                        onStep = { steps -> listener.onSpaceScrub(steps) }
                    )
                }

                if (!longPressTriggered && !flickTriggered && !isScrubbing && distSq >= flickDistanceMinPx * flickDistanceMinPx) {
                    val direction = disambiguateFlick(dx, dy)
                    if (isFlickSupported(key, direction)) {
                        repeatDeleteHandler.cancel(longPressRunnable, repeatDeleteRunnable)
                        flickTriggered = true
                        listener.onKeyFlick(key, direction)
                    }
                }
            } else if (distSq >= touchSlopPx * touchSlopPx) {
                repeatDeleteHandler.cancel(longPressRunnable, repeatDeleteRunnable)

                if (!longPressTriggered && !flickTriggered && !isScrubbing && key != null && distSq >= flickDistanceMinPx * flickDistanceMinPx) {
                    val direction = disambiguateFlick(dx, dy)
                    if (isFlickSupported(key, direction)) {
                        flickTriggered = true
                        listener.onKeyFlick(key, direction)
                    }
                }
            }
        }
        return true
    }

    private fun handleActionUp(event: MotionEvent, x: Float, y: Float, candidateHitTest: (Float) -> Int?): Boolean {
        repeatDeleteHandler.cancel(longPressRunnable, repeatDeleteRunnable)
        val pointerIdx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
            event.findPointerIndex(activePointerId)
        } else -1
        val curX = if (pointerIdx >= 0) event.getX(pointerIdx) else x
        val curY = if (pointerIdx >= 0) event.getY(pointerIdx) else y
        val dx = curX - touchDownX
        val dy = curY - touchDownY
        val distSq = dx * dx + dy * dy

        if (emojiGestureTracker.isEmojiPageTouch) {
            emojiGestureTracker.onUp(
                dx = dx, dy = dy, distSq = distSq, touchSlopPx = touchSlopPx,
                flickDistanceMinPx = flickDistanceMinPx, flickTriggered = flickTriggered,
                emojiAtlas = keyAtlas.emojiAtlas
            )
            resetTouchState()
            return true
        }

        if (symbolPagesTracker.onUpPage1(curX, curY, dy, distSq, touchSlopPx, flickDistanceMinPx, keyAtlas)) {
            resetTouchState()
            return true
        }

        if (candidateStripTracker.isCandidateStripTouch) {
            candidateStripTracker.onUp(curX, distSq, touchSlopPx, keyAtlas, candidateHitTest)
        } else {
            val key = activeKey
            listener.onTouchStateChanged(null)
            if (key != null) {
                handleKeypadUp(key, curX, curY, dx, dy, distSq)
            }
        }

        resetTouchState()
        return true
    }

    private fun handleKeypadUp(key: KeyInfo, curX: Float, curY: Float, dx: Float, dy: Float, distSq: Float) {
        if (longPressTriggered || flickTriggered || isScrubbing) return
        if (key.type == KeyType.SPACE_0 && scrubStepsDispatched > 0) return

        if (distSq >= flickDistanceMinPx * flickDistanceMinPx) {
            val direction = disambiguateFlick(dx, dy)
            if (isFlickSupported(key, direction)) {
                listener.onKeyFlick(key, direction)
                return
            }
        }
        listener.onKeyTap(key, curX, curY)
    }

    private fun resetTouchState() {
        activeKey = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        longPressTriggered = false
        flickTriggered = false
        spaceScrubTracker.reset()
    }

    fun cancelAllGestures() {
        repeatDeleteHandler.cancel(longPressRunnable, repeatDeleteRunnable)
        candidateStripTracker.cancel()
        emojiGestureTracker.cancel()
        symbolPagesTracker.reset()
        listener.onTouchStateChanged(null)
        activeKey = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        longPressTriggered = false
        flickTriggered = false
        spaceScrubTracker.reset()
    }

    fun isFlickSupported(key: KeyInfo, direction: FlickDirection): Boolean {
        return FlickDisambiguator.isFlickSupported(key, direction, keyAtlas.currentPage)
    }

    /**
     * 4-Quadrant Disambiguation as per PRD Section 4.2
     */
    fun disambiguateFlick(dx: Float, dy: Float): FlickDirection {
        return FlickDisambiguator.disambiguateFlick(dx, dy)
    }

    fun resetPage1Scroll() {
        symbolPagesTracker.resetPage1Scroll()
    }
}
