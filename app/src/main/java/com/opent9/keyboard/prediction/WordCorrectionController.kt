package com.opent9.keyboard.prediction

import android.view.inputmethod.InputConnection
import com.opent9.keyboard.jni.NativeEngineBridge

/**
 * Manages retrospective word correction: scanning text around cursor or selection,
 * querying DAWG suggestions for existing words, and performing batch edits to replace text.
 */
class WordCorrectionController(
    private val suggestionGuard: SuggestionGuard,
    private val learnedWordsRepository: LearnedWordsRepository,
    private val getProcessedCandidates: () -> List<String>
) {
    var activeContext: WordCorrectionContext? = null
        private set

    var activeCandidates: List<String> = emptyList()
        private set

    val isCorrectionActive: Boolean
        get() = activeContext != null

    /**
     * Inspects surrounding text or active selection at the cursor to generate retrospective suggestions.
     * Returns candidate list if suggestions were found, or null if cleared.
     */
    fun checkSuggestions(ic: InputConnection?, selStart: Int, selEnd: Int): List<String>? {
        if (ic == null) {
            clear()
            return null
        }

        if (selStart != selEnd) {
            val selected = ic.getSelectedText(0)?.toString() ?: ""
            val trimmed = selected.trim()
            if (trimmed.isNotEmpty() && trimmed.length <= 32 && trimmed.none { it.isWhitespace() } && trimmed.any { it.isLetter() }) {
                val suggestions = querySuggestionsForWord(trimmed)
                if (suggestions.isNotEmpty()) {
                    activeContext = WordCorrectionContext(
                        originalWord = trimmed,
                        isSelection = true
                    )
                    activeCandidates = suggestions
                    return suggestions
                }
            }
            clear()
            return null
        }

        val textBefore = ic.getTextBeforeCursor(48, 0)?.toString() ?: ""
        val textAfter = ic.getTextAfterCursor(48, 0)?.toString() ?: ""

        val beforePart = WordCorrectionHelper.extractWordPartBefore(textBefore)
        val afterPart = WordCorrectionHelper.extractWordPartAfter(textAfter)
        val fullWord = beforePart + afterPart

        if (fullWord.isNotEmpty() && fullWord.length <= 32 && fullWord.any { it.isLetter() }) {
            val suggestions = querySuggestionsForWord(fullWord)
            if (suggestions.isNotEmpty()) {
                activeContext = WordCorrectionContext(
                    originalWord = fullWord,
                    isSelection = false,
                    beforeLength = beforePart.length,
                    afterLength = afterPart.length
                )
                activeCandidates = suggestions
                return suggestions
            }
        }

        clear()
        return null
    }

    /**
     * Queries T9 suggestions matching the digit sequence of an existing word.
     */
    fun querySuggestionsForWord(word: String): List<String> {
        val digits = WordCorrectionHelper.wordToDigits(word)
        if (digits.isEmpty()) return emptyList()

        NativeEngineBridge.resetT9()
        for (d in digits) {
            NativeEngineBridge.pushStroke(d, 0f, 0f)
        }
        val rawCandidates = getProcessedCandidates().filterNot { candidate ->
            NativeEngineBridge.isWordDeleted(EnglishOrthography.toCanonical(candidate))
        }
        NativeEngineBridge.resetT9()

        val candidates = if (rawCandidates.isNotEmpty()) {
            rawCandidates
        } else {
            val lang = NativeEngineBridge.getActiveLanguage()
            suggestionGuard.getGuardedCandidates(digits, lang)
        }

        if (candidates.isEmpty()) return emptyList()

        val lang = NativeEngineBridge.getActiveLanguage()
        val digitsKey = digits.joinToString("")
        val preferred = learnedWordsRepository.getPreferredWord(lang, digitsKey)
        val hasLearned = learnedWordsRepository.hasPreferredWord(lang, digitsKey)

        val sortedCandidates = if (!preferred.isNullOrEmpty()) {
            val matchIdx = candidates.indexOfFirst { it.equals(preferred, ignoreCase = true) }
            if (matchIdx > 0) {
                val mutable = ArrayList(candidates)
                val m = mutable.removeAt(matchIdx)
                mutable.add(0, m)
                mutable
            } else if (matchIdx < 0 && hasLearned) {
                listOf(preferred) + candidates
            } else {
                candidates
            }
        } else {
            candidates
        }

        return sortedCandidates.map { WordCorrectionHelper.matchCase(word, it) }
    }

    /**
     * Replaces the original word under cursor/selection with the chosen candidate replacement.
     */
    fun commitCandidate(index: Int, ic: InputConnection, isIncognito: Boolean): String? {
        val context = activeContext ?: return null
        if (index !in activeCandidates.indices) return null

        val replacement = activeCandidates[index]
        activeContext = null

        ic.beginBatchEdit()
        try {
            if (context.isSelection) {
                ic.commitText(replacement, 1)
            } else {
                ic.deleteSurroundingText(context.beforeLength, context.afterLength)
                ic.commitText(replacement, 1)
            }
        } finally {
            ic.endBatchEdit()
        }

        activeCandidates = emptyList()

        val digitsKey = WordCorrectionHelper.wordToDigits(replacement).joinToString("")
        val lang = NativeEngineBridge.getActiveLanguage()
        learnedWordsRepository.recordPickedWord(lang, digitsKey, replacement, isIncognito)

        return replacement
    }

    /**
     * Clears active correction state and candidates.
     */
    fun clear() {
        activeContext = null
        activeCandidates = emptyList()
    }
}
