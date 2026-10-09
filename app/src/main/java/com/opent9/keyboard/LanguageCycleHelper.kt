package com.opent9.keyboard

import android.view.inputmethod.InputConnection
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.prediction.SuggestionGuard

/**
 * Handles language switching between languages (e.g. EN <-> ID)
 * and composing state re-evaluation upon language swap.
 */
object LanguageCycleHelper {

    fun cycleLanguage(
        hasActiveComposing: Boolean,
        ic: InputConnection?,
        currentComposingStrokes: MutableList<ComposingStroke>,
        suggestionGuard: SuggestionGuard,
        currentComposingDigits: MutableList<Int>,
        onLanguageChanged: (String) -> Unit,
        updateCandidatesFromNative: (InputConnection) -> Unit
    ): String {
        val currentLang = NativeEngineBridge.getActiveLanguage()
        val nextLang = if (currentLang == "EN") "ID" else "EN"
        NativeEngineBridge.switchLanguage(nextLang)
        onLanguageChanged(nextLang)

        // If currently composing, re-evaluate with swapped lexicon pointer
        if (hasActiveComposing && ic != null) {
            val savedStrokes = ArrayList<ComposingStroke>(currentComposingStrokes)
            suggestionGuard.reset()
            NativeEngineBridge.resetT9()
            currentComposingDigits.clear()
            currentComposingStrokes.clear()
            for (s in savedStrokes) {
                currentComposingDigits.add(s.digit)
                currentComposingStrokes.add(s)
                NativeEngineBridge.pushStroke(s.digit, s.touchX, s.touchY)
            }
            updateCandidatesFromNative(ic)
        }
        return nextLang
    }
}
