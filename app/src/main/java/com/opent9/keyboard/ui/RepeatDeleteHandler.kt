package com.opent9.keyboard.ui

import android.os.Handler

/**
 * Handles repeat delete acceleration logic for the Backspace (DEL) key.
 * Initiates single character deletion on long-press, then accelerates to word deletion after 1200ms.
 */
class RepeatDeleteHandler(
    private val listener: TouchGestureListener,
    private val handler: Handler
) {
    companion object {
        const val REPEAT_TICK_INTERVAL_MS = 50L
        const val ACCELERATED_DELETE_THRESHOLD_MS = 1200L
    }

    fun onLongPressDel(repeatRunnable: Runnable) {
        listener.onKeyDeleteRepeat(isWordDelete = false)
        handler.postDelayed(repeatRunnable, REPEAT_TICK_INTERVAL_MS)
    }

    fun onRepeatTick(touchDownTime: Long, repeatRunnable: Runnable) {
        val elapsed = System.currentTimeMillis() - touchDownTime
        val isWordDelete = elapsed >= ACCELERATED_DELETE_THRESHOLD_MS
        listener.onKeyDeleteRepeat(isWordDelete = isWordDelete)
        handler.postDelayed(repeatRunnable, REPEAT_TICK_INTERVAL_MS)
    }

    fun cancel(longPressRunnable: Runnable, repeatRunnable: Runnable) {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
    }
}
