package com.opent9.keyboard.ui

import android.view.inputmethod.InputConnection
import com.opent9.keyboard.jni.NativeEngineBridge

class DeleteController {

    data class LastCommitInfo(
        val word: String,
        val digits: List<Int>,
        val timestamp: Long
    )

    var lastCommitInfo: LastCommitInfo? = null

    fun recordCommit(word: String, digits: List<Int>) {
        lastCommitInfo = LastCommitInfo(word, digits, System.currentTimeMillis())
    }

    fun clearCommitHistory() {
        lastCommitInfo = null
    }

    /**
     * Handles deletion tap.
     * Returns true if retroactive un-commit was performed, re-hydrating the digits list.
     */
    fun handleDeleteTap(
        ic: InputConnection?,
        hasActiveComposing: Boolean,
        onPopComposingStroke: () -> Unit,
        onRehydrateDigits: (List<Int>) -> Unit
    ): Boolean {
        if (ic == null) return false

        // 1. If actively composing in T9: pop stroke
        if (hasActiveComposing) {
            onPopComposingStroke()
            return false
        }

        // 2. Check for Retroactive Un-Commit
        val lastCommit = lastCommitInfo
        if (lastCommit != null && (System.currentTimeMillis() - lastCommit.timestamp) < 3000L) {
            val textBefore = ic.getTextBeforeCursor(lastCommit.word.length + 1, 0)?.toString() ?: ""
            val expectedSuffix = lastCommit.word + " "
            if (textBefore.endsWith(expectedSuffix) || textBefore.endsWith(lastCommit.word)) {
                val deleteLen = if (textBefore.endsWith(expectedSuffix)) lastCommit.word.length + 1 else lastCommit.word.length
                ic.deleteSurroundingText(deleteLen, 0)
                val digitsToRehydrate = lastCommit.digits
                lastCommitInfo = null
                onRehydrateDigits(digitsToRehydrate)
                return true
            }
        }

        // 3. Normal deletion with UTF-16 surrogate pair detection
        deleteSingleOrSurrogate(ic)
        lastCommitInfo = null
        return false
    }

    fun deleteSingleOrSurrogate(ic: InputConnection?) {
        if (ic == null) return
        val before = ic.getTextBeforeCursor(2, 0)
        if (!before.isNullOrEmpty()) {
            if (before.length >= 2 && Character.isSurrogatePair(before[0], before[1])) {
                ic.deleteSurroundingText(2, 0)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        }
    }

    /**
     * Whole-word deletion (triggered after >1200ms of continuous holding or flick left)
     */
    fun deletePrecedingWord(ic: InputConnection?) {
        if (ic == null) return
        val text = ic.getTextBeforeCursor(64, 0)?.toString() ?: ""
        if (text.isEmpty()) return

        var idx = text.length - 1
        // Skip trailing spaces
        while (idx >= 0 && text[idx].isWhitespace()) {
            idx--
        }
        // Delete back to whitespace
        while (idx >= 0 && !text[idx].isWhitespace()) {
            idx--
        }
        val deleteCount = text.length - (idx + 1)
        if (deleteCount > 0) {
            ic.deleteSurroundingText(deleteCount, 0)
        }
    }

    fun clearEntireField(ic: InputConnection?) {
        if (ic == null) return
        val before = ic.getTextBeforeCursor(10000, 0)?.length ?: 0
        val after = ic.getTextAfterCursor(10000, 0)?.length ?: 0
        ic.deleteSurroundingText(before, after)
    }
}
