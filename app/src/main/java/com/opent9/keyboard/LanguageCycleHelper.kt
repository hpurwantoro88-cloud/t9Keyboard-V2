package com.opent9.keyboard

import android.view.inputmethod.InputConnection
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.prediction.SuggestionGuard
import java.util.Locale

/**
 * Handles language switching between languages (e.g. EN <-> ID),
 * OS subtype locale mapping, and composing state re-evaluation upon language swap.
 */
object LanguageCycleHelper {

    fun resolveLanguageFromSubtypeLocale(locale: String?): String? {
        if (locale.isNullOrBlank()) return null
        val normalized = locale.trim().lowercase(Locale.ROOT)
        return when {
            normalized == "in" || normalized == "id" ||
                normalized.startsWith("in_") || normalized.startsWith("in-") ||
                normalized.startsWith("id_") || normalized.startsWith("id-") -> "ID"
            normalized == "en" ||
                normalized.startsWith("en_") || normalized.startsWith("en-") -> "EN"
            else -> null
        }
    }

    fun nextLanguage(currentLang: String): String {
        return if (currentLang == "EN") "ID" else "EN"
    }

    fun cycleLanguage(
        hasActiveComposing: Boolean,
        ic: InputConnection?,
        currentComposingStrokes: MutableList<ComposingStroke>,
        suggestionGuard: SuggestionGuard,
        currentComposingDigits: MutableList<Int>,
        onLanguageChanged: (String) -> Unit,
        updateCandidatesFromNative: (InputConnection) -> Unit
    ): String {
        val nextLang = nextLanguage(NativeEngineBridge.getActiveLanguage())
        return switchToLanguage(
            targetLang = nextLang,
            hasActiveComposing = hasActiveComposing,
            ic = ic,
            currentComposingStrokes = currentComposingStrokes,
            suggestionGuard = suggestionGuard,
            currentComposingDigits = currentComposingDigits,
            onLanguageChanged = onLanguageChanged,
            updateCandidatesFromNative = updateCandidatesFromNative
        )
    }

    fun switchToLanguage(
        targetLang: String,
        hasActiveComposing: Boolean,
        ic: InputConnection?,
        currentComposingStrokes: MutableList<ComposingStroke>,
        suggestionGuard: SuggestionGuard,
        currentComposingDigits: MutableList<Int>,
        onLanguageChanged: (String) -> Unit,
        updateCandidatesFromNative: (InputConnection) -> Unit
    ): String {
        NativeEngineBridge.switchLanguage(targetLang)
        onLanguageChanged(targetLang)

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
        return targetLang
    }
}
