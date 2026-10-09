package com.opent9.keyboard

import android.content.Context
import android.view.inputmethod.InputConnection
import android.widget.Toast
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.prediction.EnglishOrthography
import com.opent9.keyboard.prediction.LearnedWordsRepository
import com.opent9.keyboard.prediction.SuggestionGuard

/**
 * Handles learned word removal from dictionary, filtering candidates,
 * replaying composing strokes if needed, and showing toast feedback.
 */
object CandidateRemovalHelper {

    fun removeCandidateSuggestion(
        context: Context,
        word: String,
        learnedWordsRepository: LearnedWordsRepository,
        activeCandidates: List<String>,
        hasActiveComposing: Boolean,
        currentComposingDigits: MutableList<Int>,
        currentComposingStrokes: MutableList<ComposingStroke>,
        suggestionGuard: SuggestionGuard,
        ic: InputConnection?,
        updateCandidatesFromNative: (InputConnection) -> Unit,
        onCandidatesUpdated: (List<String>) -> Unit,
        onClearWordCorrection: () -> Unit
    ): List<String> {
        val canonical = learnedWordsRepository.removeWord(word)
        val filtered = activeCandidates.filterNot {
            EnglishOrthography.toCanonical(it).equals(canonical, ignoreCase = true)
        }

        if (hasActiveComposing && currentComposingDigits.isNotEmpty()) {
            onCandidatesUpdated(filtered)
            if (ic != null) {
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
        } else {
            if (filtered.isEmpty()) {
                onClearWordCorrection()
            } else {
                onCandidatesUpdated(filtered)
            }
        }

        try {
            Toast.makeText(context, "Removed \"$word\"", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}

        return filtered
    }
}
