package com.opent9.keyboard.ui

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class EnterKeyHandler {

    /**
     * Resolves the effective IME action taking into account:
     * 1. IME_FLAG_NO_ENTER_ACTION: If set, client requests Enter to NOT perform the action.
     * 2. Custom actionId: If editorInfo.actionId is non-zero, use it.
     * 3. Standard action: Masked from editorInfo.imeOptions (GO, SEARCH, SEND, NEXT, DONE, PREVIOUS).
     * Returns EditorInfo.IME_ACTION_UNSPECIFIED if no explicit action should be performed.
     */
    fun resolveEffectiveAction(editorInfo: EditorInfo?): Int {
        if (editorInfo == null) return EditorInfo.IME_ACTION_UNSPECIFIED
        val imeOptions = editorInfo.imeOptions
        if ((imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EditorInfo.IME_ACTION_UNSPECIFIED
        }
        if (editorInfo.actionId != 0) {
            return editorInfo.actionId
        }
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        return when (action) {
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_PREVIOUS -> action
            else -> EditorInfo.IME_ACTION_UNSPECIFIED
        }
    }

    /**
     * Handles tapping the Enter key:
     * 1. If composing text is active, commits the candidate and finishes composing,
     *    then proceeds to dispatch the action.
     * 2. If an explicit action is resolved (e.g. SEARCH, SEND, GO, NEXT, DONE), executes it via performEditorAction.
     *    Note: Multi-line inputs with explicit actions (e.g. Google Search textarea, WhatsApp "Enter to send")
     *    MUST trigger the action.
     * 3. If no action (IME_ACTION_UNSPECIFIED or IME_ACTION_NONE):
     *    - If multi-line: commits newline "\n".
     *    - If single-line: sends KeyEvent.KEYCODE_ENTER down/up events so forms/webviews/terminals/OnKeyListeners execute.
     */
    fun handleEnterTap(
        ic: InputConnection?,
        editorInfo: EditorInfo?,
        hasActiveComposing: Boolean,
        onCommitActiveCandidate: () -> Unit
    ) {
        if (ic == null) return

        if (hasActiveComposing) {
            onCommitActiveCandidate()
            ic.finishComposingText()
        }

        val effectiveAction = resolveEffectiveAction(editorInfo)
        val inputType = editorInfo?.inputType ?: 0
        val isMultiLine = (inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        if (effectiveAction != EditorInfo.IME_ACTION_NONE &&
            effectiveAction != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(effectiveAction)
        } else if (isMultiLine) {
            ic.commitText("\n", 1)
        } else {
            sendEnterKeyEvent(ic)
        }
    }

    /**
     * Sends down and up KeyEvent for KEYCODE_ENTER.
     */
    fun sendEnterKeyEvent(ic: InputConnection) {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
        ic.sendKeyEvent(down)
        ic.sendKeyEvent(up)
    }

    /**
     * Flick Up / Force Submit:
     * If an action is specified, performs it; otherwise sends IME_ACTION_DONE or KEYCODE_ENTER.
     */
    fun handleForceSubmit(ic: InputConnection?, editorInfo: EditorInfo?) {
        if (ic == null) return
        val effectiveAction = resolveEffectiveAction(editorInfo)
        if (effectiveAction != EditorInfo.IME_ACTION_NONE && effectiveAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(effectiveAction)
        } else {
            val handled = ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
            if (!handled) {
                sendEnterKeyEvent(ic)
            }
        }
    }
}
