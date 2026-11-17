package com.opent9.keyboard

import android.view.inputmethod.InputConnection
import com.opent9.keyboard.ui.KeyboardPage
import com.opent9.keyboard.ui.T9KeyboardView

/**
 * Handles emoji page actions: insertion, tab reset, space, and backspace.
 */
object EmojiActionDispatcher {

    fun handleEmojiSelected(
        emoji: String,
        ic: InputConnection?,
        hasActiveComposing: Boolean,
        onCommitActiveCandidate: (Boolean) -> Unit,
        onResetLastSpaceTime: () -> Unit = {},
        onUpdateAutoCaps: () -> Unit = {}
    ) {
        if (ic != null) {
            if (hasActiveComposing) {
                onCommitActiveCandidate(false)
            }
            ic.commitText(emoji, 1)
            onResetLastSpaceTime()
            onUpdateAutoCaps()
        }
    }

    fun handleEmojiControl(
        controlIndex: Int,
        ic: InputConnection?,
        keyboardView: T9KeyboardView,
        hasActiveComposing: Boolean,
        onSwitchToPage: (KeyboardPage) -> Unit,
        onCommitActiveCandidate: (Boolean) -> Unit,
        onDeleteTap: (InputConnection) -> Unit,
        onSpaceTap: (InputConnection) -> Unit = { inputConn ->
            if (hasActiveComposing) {
                onCommitActiveCandidate(true)
            } else {
                inputConn.commitText(" ", 1)
            }
        }
    ) {
        when (controlIndex) {
            0 -> {
                onSwitchToPage(KeyboardPage.PAGE_0_TEXT)
            }
            1 -> {
                keyboardView.emojiAtlas.activeCategoryIndex = 0
                keyboardView.gestureTracker.resetEmojiScroll()
                keyboardView.invalidate()
            }
            2 -> {
                if (ic != null) {
                    onSpaceTap(ic)
                }
            }
            3 -> {
                if (ic != null) {
                    onDeleteTap(ic)
                }
            }
        }
    }
}
