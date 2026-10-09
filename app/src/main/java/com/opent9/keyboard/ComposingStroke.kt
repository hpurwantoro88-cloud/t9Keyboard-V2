package com.opent9.keyboard

/**
 * Represents a single touch stroke during T9 composition.
 */
data class ComposingStroke(
    val digit: Int,
    val touchX: Float,
    val touchY: Float
)
