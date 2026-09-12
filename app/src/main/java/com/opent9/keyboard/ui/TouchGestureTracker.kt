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
    fun onSpaceScrub(steps: Int) // +1 for DPAD_RIGHT, -1 for DPAD_LEFT
    fun onPillTap()
    fun onCandidateTap(index: Int)
    fun onStripScroll(newScrollOffset: Float)
    fun onTouchStateChanged(activeKeyId: Int?)
}

class TouchGestureTracker(
    private val keyAtlas: KeyAtlas,
    private val listener: TouchGestureListener,
    handler: Handler? = null
) {
    companion object {
        const val TOUCH_SLOP_PX = 36f
        const val FLICK_DISTANCE_MIN_PX = 54f
        const val FLICK_TIME_WINDOW_MS = 180L
        const val LONG_PRESS_TIMEOUT_MS = 350L
        const val REPEAT_TICK_INTERVAL_MS = 50L
        const val SCRUB_STEP_PX = 32f
    }

    private val actualHandler: Handler by lazy {
        handler ?: Handler(Looper.getMainLooper())
    }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var isCandidateStripTouch = false

    private var activeKey: KeyInfo? = null
    private var longPressTriggered = false
    private var isScrubbing = false
    private var scrubAccumulator = 0f

    // Strip dragging state
    var stripScrollOffset = 0f
        private set
    var maxStripScroll = 0f // negative or 0
    private var stripStartScrollX = 0f

    // Long press runnable
    private val longPressRunnable = object : Runnable {
        override fun run() {
            activeKey?.let { key ->
                longPressTriggered = true
                listener.onKeyLongPress(key)
                if (key.type == KeyType.DEL) {
                    actualHandler.postDelayed(repeatDeleteRunnable, REPEAT_TICK_INTERVAL_MS)
                }
            }
        }
    }

    private val repeatDeleteRunnable = object : Runnable {
        override fun run() {
            activeKey?.let { key ->
                if (key.type == KeyType.DEL) {
                    listener.onKeyLongPress(key)
                    actualHandler.postDelayed(this, REPEAT_TICK_INTERVAL_MS)
                }
            }
        }
    }

    fun onTouchEvent(event: MotionEvent, candidateHitTest: (Float) -> Int?): Boolean {
        val x = event.x
        val y = event.y
        val now = System.currentTimeMillis()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                touchDownTime = now
                longPressTriggered = false
                isScrubbing = false
                scrubAccumulator = 0f

                if (y < keyAtlas.stripHeight) {
                    // Candidate Strip Touch
                    isCandidateStripTouch = true
                    activeKey = null
                    stripStartScrollX = stripScrollOffset
                } else {
                    // Keypad Touch
                    isCandidateStripTouch = false
                    activeKey = keyAtlas.findKeyAt(x, y)
                    listener.onTouchStateChanged(activeKey?.id)
                    actualHandler.removeCallbacks(longPressRunnable)
                    actualHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - touchDownX
                val dy = y - touchDownY
                val distSq = dx * dx + dy * dy

                if (isCandidateStripTouch) {
                    // Drag candidate strip
                    val newOffset = (stripStartScrollX + dx).coerceIn(maxStripScroll, 0f)
                    stripScrollOffset = newOffset
                    listener.onStripScroll(newOffset)
                } else {
                    // Keypad Area
                    val key = activeKey
                    if (key != null && key.type == KeyType.SPACE_0) {
                        // Spacebar Cursor Scrubbing (Trackpad Mode)
                        if (Math.abs(dx) >= TOUCH_SLOP_PX) {
                            if (!isScrubbing) {
                                isScrubbing = true
                                actualHandler.removeCallbacks(longPressRunnable)
                            }
                            val scrubDelta = dx - scrubAccumulator
                            if (Math.abs(scrubDelta) >= SCRUB_STEP_PX) {
                                val steps = (scrubDelta / SCRUB_STEP_PX).toInt()
                                scrubAccumulator += steps * SCRUB_STEP_PX
                                listener.onSpaceScrub(steps)
                            }
                        }
                    } else if (distSq >= TOUCH_SLOP_PX * TOUCH_SLOP_PX) {
                        // Moved beyond touch slop: cancel long press
                        actualHandler.removeCallbacks(longPressRunnable)
                        actualHandler.removeCallbacks(repeatDeleteRunnable)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                actualHandler.removeCallbacks(longPressRunnable)
                actualHandler.removeCallbacks(repeatDeleteRunnable)
                val elapsed = now - touchDownTime
                val dx = x - touchDownX
                val dy = y - touchDownY
                val distSq = dx * dx + dy * dy

                if (isCandidateStripTouch) {
                    if (distSq < TOUCH_SLOP_PX * TOUCH_SLOP_PX && elapsed < LONG_PRESS_TIMEOUT_MS) {
                        // Tap in strip
                        if (x < keyAtlas.pillBounds.right) {
                            listener.onPillTap()
                        } else {
                            val candIdx = candidateHitTest(x)
                            if (candIdx != null) {
                                listener.onCandidateTap(candIdx)
                            }
                        }
                    }
                } else {
                    val key = activeKey
                    listener.onTouchStateChanged(null)

                    if (key != null && !longPressTriggered && !isScrubbing) {
                        if (distSq >= FLICK_DISTANCE_MIN_PX * FLICK_DISTANCE_MIN_PX && elapsed <= FLICK_TIME_WINDOW_MS) {
                            // High-velocity flick
                            val direction = disambiguateFlick(dx, dy)
                            listener.onKeyFlick(key, direction)
                        } else if (distSq < TOUCH_SLOP_PX * TOUCH_SLOP_PX) {
                            // Primary Tap
                            listener.onKeyTap(key, x, y)
                        }
                    }
                }

                activeKey = null
                longPressTriggered = false
                isScrubbing = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                actualHandler.removeCallbacks(longPressRunnable)
                actualHandler.removeCallbacks(repeatDeleteRunnable)
                listener.onTouchStateChanged(null)
                activeKey = null
                longPressTriggered = false
                isScrubbing = false
                return true
            }
        }
        return false
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
}
