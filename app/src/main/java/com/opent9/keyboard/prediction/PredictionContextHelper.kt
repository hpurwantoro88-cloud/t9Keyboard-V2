package com.opent9.keyboard.prediction

import android.view.inputmethod.InputConnection

/**
 * Extracts preceding word context from the active InputConnection for
 * Idle Next-Word Prediction and contextual T9 disambiguation.
 */
object PredictionContextHelper {

    data class ContextInfo(
        val prevWord: String?,
        val hasTrailingSpace: Boolean,
        val isSentenceStart: Boolean
    )

    fun extractContext(ic: InputConnection?): ContextInfo {
        if (ic == null) {
            return ContextInfo(prevWord = null, hasTrailingSpace = false, isSentenceStart = true)
        }
        val textBefore = try {
            ic.getTextBeforeCursor(64, 0)?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
        return extractFromText(textBefore)
    }

    fun extractFromText(textBeforeCursor: String): ContextInfo {
        if (textBeforeCursor.isEmpty()) {
            return ContextInfo(prevWord = null, hasTrailingSpace = false, isSentenceStart = true)
        }

        val lastChar = textBeforeCursor.last()
        if (!lastChar.isWhitespace()) {
            return ContextInfo(prevWord = null, hasTrailingSpace = false, isSentenceStart = false)
        }

        var end = textBeforeCursor.length - 1
        while (end >= 0 && textBeforeCursor[end].isWhitespace()) {
            end--
        }
        if (end < 0) {
            return ContextInfo(prevWord = null, hasTrailingSpace = true, isSentenceStart = true)
        }

        var punctIdx = end
        while (punctIdx >= 0 && textBeforeCursor[punctIdx] in CLOSING_DELIMITERS) {
            punctIdx--
        }
        if (punctIdx >= 0) {
            val punctChar = textBeforeCursor[punctIdx]
            if (punctChar == '.' || punctChar == '!' || punctChar == '?') {
                return ContextInfo(prevWord = null, hasTrailingSpace = true, isSentenceStart = true)
            }
        }

        var start = end
        while (start >= 0 && !textBeforeCursor[start].isWhitespace()) {
            start--
        }
        val rawToken = textBeforeCursor.substring(start + 1, end + 1)
        val cleaned = cleanWordToken(rawToken)
        return if (cleaned.isNotEmpty()) {
            ContextInfo(prevWord = cleaned, hasTrailingSpace = true, isSentenceStart = false)
        } else {
            ContextInfo(prevWord = null, hasTrailingSpace = true, isSentenceStart = false)
        }
    }

    private fun cleanWordToken(token: String): String {
        var s = 0
        var e = token.length - 1
        while (s <= e && !token[s].isLetter()) s++
        while (e >= s && !token[e].isLetter()) e--
        if (s > e) return ""

        val core = token.substring(s, e + 1)
        if (core.any { it.isDigit() }) return ""
        return core
    }

    private val CLOSING_DELIMITERS = charArrayOf('"', '\'', '”', '’', ')', ']', '}')
}
