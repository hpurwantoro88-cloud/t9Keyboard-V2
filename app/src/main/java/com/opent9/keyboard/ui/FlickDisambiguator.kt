package com.opent9.keyboard.ui

import kotlin.math.atan2

/**
 * Handles 4-quadrant flick direction disambiguation and key flick capability validation.
 */
object FlickDisambiguator {

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

    /**
     * Determines whether a given key supports flick gesture on the current keyboard page.
     */
    fun isFlickSupported(key: KeyInfo, direction: FlickDirection, currentPage: KeyboardPage): Boolean {
        when (key.type) {
            KeyType.DEL -> return direction != FlickDirection.RIGHT
            KeyType.ENTER -> return direction == FlickDirection.UP || direction == FlickDirection.DOWN
            KeyType.SPACE_0 -> return direction == FlickDirection.DOWN
            else -> {}
        }

        return when (currentPage) {
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
}
