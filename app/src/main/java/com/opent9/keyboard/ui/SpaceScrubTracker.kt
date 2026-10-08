package com.opent9.keyboard.ui

/**
 * Manages Spacebar cursor scrubbing (Trackpad mode) state machine,
 * aspect-ratio deadband checking, distance accumulation, and DPAD step dispatching.
 */
class SpaceScrubTracker {

    var isScrubbing: Boolean = false
        private set

    var scrubStepsDispatched: Int = 0
        private set

    private var scrubAccumulator: Float = 0f

    /**
     * Processes motion deltas during spacebar touch.
     * Returns true if scrubbing is active or has just activated.
     */
    fun onMove(
        dx: Float,
        dy: Float,
        elapsed: Long,
        aspectRatio: Float,
        requireHold: Boolean,
        holdDelayMs: Long,
        activationDistancePx: Float,
        stepPx: Float,
        onScrubStart: () -> Unit,
        onStep: (Int) -> Unit
    ): Boolean {
        if (!isScrubbing) {
            val absDx = Math.abs(dx)
            val absDy = Math.abs(dy)
            val isHorizontal = absDx > absDy * aspectRatio
            val holdSatisfied = !requireHold || (elapsed >= holdDelayMs)

            if (isHorizontal && holdSatisfied && absDx >= activationDistancePx) {
                isScrubbing = true
                onScrubStart()
                scrubAccumulator = if (dx > 0) activationDistancePx else -activationDistancePx
                val initialStep = if (dx > 0) 1 else -1
                scrubStepsDispatched += 1
                onStep(initialStep)
            }
        } else {
            val scrubDelta = dx - scrubAccumulator
            if (Math.abs(scrubDelta) >= stepPx) {
                val steps = (scrubDelta / stepPx).toInt()
                scrubAccumulator += steps * stepPx
                scrubStepsDispatched += Math.abs(steps)
                onStep(steps)
            }
        }
        return isScrubbing
    }

    /**
     * Resets tracking state upon touch release, gesture cancel, or key change.
     */
    fun reset() {
        isScrubbing = false
        scrubAccumulator = 0f
        scrubStepsDispatched = 0
    }
}
