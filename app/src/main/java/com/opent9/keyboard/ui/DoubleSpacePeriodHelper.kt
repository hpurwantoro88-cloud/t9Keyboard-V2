package com.opent9.keyboard.ui

import android.view.inputmethod.InputConnection

/**
 * Handles double-space to period (". ") insertion logic when enabled in settings.
 */
object DoubleSpacePeriodHelper {

    /**
     * Processes a space key press. Returns the updated [lastSpaceTapTime] timestamp.
     */
    fun handleSpaceTap(
        ic: InputConnection,
        now: Long,
        lastSpaceTapTime: Long,
        isDoubleSpaceEnabled: Boolean,
        isPasswordMode: Boolean,
        onUpdateAutoCaps: () -> Unit
    ): Long {
        val textBefore = if (isDoubleSpaceEnabled && !isPasswordMode) {
            ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        } else ""

        val isDoubleSpace = isDoubleSpaceEnabled &&
                !isPasswordMode &&
                lastSpaceTapTime > 0L &&
                (now - lastSpaceTapTime < 800L) &&
                textBefore.length >= 2 &&
                textBefore.endsWith(" ") &&
                !textBefore[textBefore.length - 2].isWhitespace() &&
                textBefore[textBefore.length - 2] != '.' &&
                textBefore[textBefore.length - 2] != '?' &&
                textBefore[textBefore.length - 2] != '!'

        return if (isDoubleSpace) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            onUpdateAutoCaps()
            0L
        } else {
            ic.commitText(" ", 1)
            onUpdateAutoCaps()
            now
        }
    }
}
