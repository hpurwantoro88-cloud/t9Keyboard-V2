package com.opent9.keyboard.ui

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class EnterKeyHandler {

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
            return
        }

        val imeOptions = editorInfo?.imeOptions ?: EditorInfo.IME_ACTION_UNSPECIFIED
        val actionId = imeOptions and EditorInfo.IME_MASK_ACTION
        val inputType = editorInfo?.inputType ?: 0
        val isMultiLine = (inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        if (actionId != EditorInfo.IME_ACTION_NONE &&
            actionId != EditorInfo.IME_ACTION_UNSPECIFIED &&
            !isMultiLine
        ) {
            ic.performEditorAction(actionId)
        } else {
            ic.commitText("\n", 1)
        }
    }

    fun handleForceSubmit(ic: InputConnection?, editorInfo: EditorInfo?) {
        if (ic == null) return
        val imeOptions = editorInfo?.imeOptions ?: EditorInfo.IME_ACTION_UNSPECIFIED
        val actionId = imeOptions and EditorInfo.IME_MASK_ACTION
        if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(actionId)
        } else {
            ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
    }
}
