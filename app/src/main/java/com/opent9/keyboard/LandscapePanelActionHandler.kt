package com.opent9.keyboard

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.opent9.keyboard.ui.KeyboardPage
import com.opent9.keyboard.ui.LandscapePanelAction

/**
 * Executes landscape utility/edit panel actions cleanly against the active InputConnection
 * and IME service state.
 */
object LandscapePanelActionHandler {

    fun handleAction(
        action: LandscapePanelAction,
        ic: InputConnection?,
        hasActiveComposing: Boolean,
        commitActiveCandidate: (Boolean) -> Unit,
        commitMultiTapActive: () -> Unit,
        flushMultiTapWord: () -> Unit,
        updateAutoCaps: (Boolean) -> Unit,
        onSwapSide: () -> Unit,
        onSwitchToPage: (KeyboardPage) -> Unit,
        onHideKeyboard: () -> Unit
    ) {
        when (action) {
            LandscapePanelAction.SWAP_SIDE -> {
                onSwapSide()
                return
            }
            LandscapePanelAction.EMOJI -> {
                onSwitchToPage(KeyboardPage.PAGE_4_EMOJI)
                return
            }
            LandscapePanelAction.HIDE_IME -> {
                onHideKeyboard()
                return
            }
            else -> {}
        }

        if (ic == null) return

        // Finalize any active composing or multi-tap before cursor/edit/clipboard operations
        if (hasActiveComposing) {
            commitActiveCandidate(false)
        }
        commitMultiTapActive()
        flushMultiTapWord()

        when (action) {
            LandscapePanelAction.CURSOR_LEFT -> sendKeyTap(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            LandscapePanelAction.CURSOR_RIGHT -> sendKeyTap(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            LandscapePanelAction.CURSOR_UP -> sendKeyTap(ic, KeyEvent.KEYCODE_DPAD_UP)
            LandscapePanelAction.CURSOR_DOWN -> sendKeyTap(ic, KeyEvent.KEYCODE_DPAD_DOWN)
            LandscapePanelAction.HOME -> sendKeyTap(ic, KeyEvent.KEYCODE_MOVE_HOME)
            LandscapePanelAction.END -> sendKeyTap(ic, KeyEvent.KEYCODE_MOVE_END)
            LandscapePanelAction.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            LandscapePanelAction.COPY -> ic.performContextMenuAction(android.R.id.copy)
            LandscapePanelAction.CUT -> ic.performContextMenuAction(android.R.id.cut)
            LandscapePanelAction.PASTE -> {
                ic.performContextMenuAction(android.R.id.paste)
                updateAutoCaps(false)
            }
            LandscapePanelAction.UNDO -> {
                if (!ic.performContextMenuAction(android.R.id.undo)) {
                    sendCtrlShortcut(ic, KeyEvent.KEYCODE_Z, shift = false)
                }
            }
            LandscapePanelAction.REDO -> {
                if (!ic.performContextMenuAction(android.R.id.redo)) {
                    sendCtrlShortcut(ic, KeyEvent.KEYCODE_Z, shift = true)
                }
            }
            LandscapePanelAction.TAB -> {
                ic.commitText("\t", 1)
                updateAutoCaps(false)
            }
            LandscapePanelAction.SWAP_SIDE,
            LandscapePanelAction.EMOJI,
            LandscapePanelAction.HIDE_IME -> {}
        }
    }

    private fun sendKeyTap(ic: InputConnection, keyCode: Int) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun sendCtrlShortcut(ic: InputConnection, keyCode: Int, shift: Boolean) {
        val now = SystemClock.uptimeMillis()
        val metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or
            if (shift) (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON) else 0
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }
}
