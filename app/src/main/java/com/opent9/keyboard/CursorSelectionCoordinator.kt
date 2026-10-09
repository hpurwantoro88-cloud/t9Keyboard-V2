package com.opent9.keyboard

import kotlin.math.abs

/**
 * Coordinates cursor movement analysis during onUpdateSelection,
 * distinguishing user-initiated cursor repositioning from IME composing span adjustments.
 */
object CursorSelectionCoordinator {

    /**
     * Determines whether cursor movement while composing indicates the user moved the cursor manually.
     */
    fun isUserCursorMoveForComposing(
        candidatesStart: Int,
        candidatesEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        oldSelStart: Int,
        composingAnchorPosition: Int,
        firstCandidateLength: Int
    ): Boolean {
        return if (candidatesStart >= 0 && candidatesEnd >= 0) {
            newSelStart != candidatesEnd || newSelEnd != candidatesEnd
        } else {
            val expectedPos = if (composingAnchorPosition >= 0 && firstCandidateLength > 0) {
                composingAnchorPosition + firstCandidateLength
            } else -1
            newSelStart != newSelEnd || (expectedPos >= 0 && newSelStart != expectedPos) || (newSelStart < oldSelStart)
        }
    }

    /**
     * Determines whether cursor movement during multi-tap indicates user moved cursor manually.
     */
    fun isUserCursorMoveForMultiTap(
        candidatesStart: Int,
        candidatesEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        oldSelStart: Int
    ): Boolean {
        return if (candidatesStart >= 0 && candidatesEnd >= 0) {
            newSelStart != candidatesEnd || newSelEnd != candidatesEnd
        } else {
            newSelStart < oldSelStart || abs(newSelStart - oldSelStart) > 1 || newSelStart != newSelEnd
        }
    }

    fun coordinateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
        ignoreSelectionUpdateCount: Int,
        onDecrementIgnoreCount: () -> Unit,
        setLastSelectionStart: (Int) -> Unit,
        setLastSpaceTapTime: (Long) -> Unit,
        setComposingAnchorPosition: (Int) -> Unit,
        composingAnchorPosition: Int,
        isBackspaceAction: Boolean,
        setIsBackspaceAction: (Boolean) -> Unit,
        clearWordCorrection: () -> Unit,
        hasActiveComposing: Boolean,
        hasComposingDigits: Boolean,
        isMultiTapActive: Boolean,
        hasMultiTapBuffer: Boolean,
        isT9Mode: Boolean,
        firstCandidateLength: Int,
        isPasswordMode: Boolean,
        onFinalizeComposingOnCursorMove: () -> Unit,
        onCommitMultiTapActive: () -> Unit,
        onFlushMultiTapWord: () -> Unit,
        updateAutoCaps: (Boolean) -> Unit,
        checkForWordCorrectionSuggestions: (Int, Int) -> Unit
    ) {
        if (ignoreSelectionUpdateCount > 0) {
            onDecrementIgnoreCount()
            setLastSelectionStart(newSelStart)
            return
        }

        val cursorMoved = oldSelStart != newSelStart || oldSelEnd != newSelEnd
        if (cursorMoved) {
            setLastSpaceTapTime(0L)
        }

        if (candidatesStart >= 0) {
            setComposingAnchorPosition(candidatesStart)
        }

        if (isBackspaceAction) {
            setIsBackspaceAction(false)
            clearWordCorrection()
            if (!hasActiveComposing && !hasComposingDigits && !isMultiTapActive) {
                updateAutoCaps(cursorMoved)
            }
            setLastSelectionStart(newSelStart)
            return
        }

        var activeComposing = hasActiveComposing
        var composingDigitsPresent = hasComposingDigits
        if (activeComposing || composingDigitsPresent) {
            val isUserCursorMove = isUserCursorMoveForComposing(
                candidatesStart = candidatesStart,
                candidatesEnd = candidatesEnd,
                newSelStart = newSelStart,
                newSelEnd = newSelEnd,
                oldSelStart = oldSelStart,
                composingAnchorPosition = composingAnchorPosition,
                firstCandidateLength = firstCandidateLength
            )
            if (isUserCursorMove) {
                onFinalizeComposingOnCursorMove()
                activeComposing = false
                composingDigitsPresent = false
            }
        } else if (!isT9Mode && (isMultiTapActive || hasMultiTapBuffer)) {
            val isUserCursorMove = isUserCursorMoveForMultiTap(
                candidatesStart = candidatesStart,
                candidatesEnd = candidatesEnd,
                newSelStart = newSelStart,
                newSelEnd = newSelEnd,
                oldSelStart = oldSelStart
            )
            if (isUserCursorMove) {
                onCommitMultiTapActive()
                onFlushMultiTapWord()
            }
        }

        if (!activeComposing && !composingDigitsPresent && !isMultiTapActive) {
            updateAutoCaps(cursorMoved)

            if (!isPasswordMode) {
                checkForWordCorrectionSuggestions(newSelStart, newSelEnd)
            } else {
                clearWordCorrection()
            }
        }

        setLastSelectionStart(newSelStart)
    }
}
