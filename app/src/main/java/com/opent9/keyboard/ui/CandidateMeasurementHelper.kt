package com.opent9.keyboard.ui

import android.graphics.Paint

/**
 * Handles candidate text measurement, horizontal bounds calculation,
 * word formatting, and display labels.
 */
object CandidateMeasurementHelper {

    /**
     * Measures candidate words, calculates item bounds into pre-allocated arrays,
     * and returns the maximum scroll offset. Zero allocations during hot path execution.
     */
    fun recomputeCandidateLayout(
        candidates: List<String>,
        candidateTextPaint: Paint,
        candidateViewportLeft: Float,
        candidateViewportWidth: Float,
        density: Float,
        candidateItemLeft: FloatArray,
        candidateItemWidth: FloatArray,
        candidateItemRight: FloatArray,
        formatWord: (String) -> String
    ): Float {
        val minWidth = 64f * density
        val padding = 24f * density
        var curX = candidateViewportLeft

        val count = candidates.size.coerceAtMost(16)
        for (i in 0 until count) {
            val word = formatWord(candidates[i])
            val textW = candidateTextPaint.measureText(word)
            val itemW = (textW + padding).coerceAtLeast(minWidth)
            candidateItemLeft[i] = curX
            candidateItemWidth[i] = itemW
            candidateItemRight[i] = curX + itemW
            curX += itemW
        }

        val totalCandWidth = curX - candidateViewportLeft
        return if (totalCandWidth > candidateViewportWidth) {
            -(totalCandWidth - candidateViewportWidth)
        } else {
            0f
        }
    }

    fun formatCandidateWord(
        raw: String,
        isWordCorrectionActive: Boolean,
        shiftState: Int
    ): String {
        if (isWordCorrectionActive) {
            return raw
        }
        return when (shiftState) {
            1 -> raw.replaceFirstChar { it.uppercase() }
            2 -> raw.uppercase()
            else -> if (raw == "I" || raw.startsWith("I'")) raw else raw.lowercase()
        }
    }

    fun getDisplayLabel(key: KeyInfo, shiftState: Int, isCandidatesEmpty: Boolean): String {
        val isUppercase = when (shiftState) {
            2 -> true
            1 -> isCandidatesEmpty
            else -> false
        }
        return if (key.type == KeyType.DIGIT_T9) {
            if (isUppercase) key.upperPrimaryLabel else key.lowerPrimaryLabel
        } else {
            key.primaryLabel
        }
    }
}
