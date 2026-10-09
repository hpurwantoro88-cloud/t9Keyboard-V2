package com.opent9.keyboard.ui

import android.os.Handler

/**
 * Tracks candidate strip gestures: horizontal panning, pill tap, and candidate tap / long-press.
 */
class CandidateStripGestureTracker(
    private val listener: TouchGestureListener,
    private val handler: Handler
) {
    var stripScrollOffset = 0f
        private set
    var maxStripScroll = 0f // negative or 0
    private var stripStartScrollX = 0f
    var isCandidateStripTouch = false
        private set
    var activeCandidateIndex: Int? = null
        private set

    private var longPressFired = false

    private val candidateLongPressRunnable = object : Runnable {
        override fun run() {
            activeCandidateIndex?.let { candIdx ->
                longPressFired = true
                listener.onCandidateLongPress(candIdx)
            }
        }
    }

    fun onDown(
        x: Float,
        keyAtlas: KeyAtlas,
        longPressTimeoutMs: Long,
        candidateHitTest: (Float) -> Int?
    ) {
        isCandidateStripTouch = true
        longPressFired = false
        stripStartScrollX = stripScrollOffset
        activeCandidateIndex = null
        if (x >= keyAtlas.pillBounds.right) {
            val candIdx = candidateHitTest(x)
            if (candIdx != null) {
                activeCandidateIndex = candIdx
                handler.removeCallbacks(candidateLongPressRunnable)
                handler.postDelayed(candidateLongPressRunnable, longPressTimeoutMs)
            }
        }
    }

    fun onMove(
        dx: Float,
        distSq: Float,
        touchSlopPx: Float
    ) {
        if (!isCandidateStripTouch) return
        if (distSq >= touchSlopPx * touchSlopPx) {
            handler.removeCallbacks(candidateLongPressRunnable)
            activeCandidateIndex = null
        }
        val newOffset = (stripStartScrollX + dx).coerceIn(maxStripScroll, 0f)
        stripScrollOffset = newOffset
        listener.onStripScroll(newOffset)
    }

    fun onUp(
        curX: Float,
        distSq: Float,
        touchSlopPx: Float,
        keyAtlas: KeyAtlas,
        candidateHitTest: (Float) -> Int?
    ) {
        if (!isCandidateStripTouch) return
        handler.removeCallbacks(candidateLongPressRunnable)
        if (distSq < touchSlopPx * touchSlopPx && !longPressFired) {
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
        isCandidateStripTouch = false
    }

    fun resetScroll() {
        stripScrollOffset = 0f
    }

    fun cancel() {
        handler.removeCallbacks(candidateLongPressRunnable)
        activeCandidateIndex = null
        isCandidateStripTouch = false
        longPressFired = false
    }
}
