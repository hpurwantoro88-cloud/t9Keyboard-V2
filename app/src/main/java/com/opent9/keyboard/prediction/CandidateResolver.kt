package com.opent9.keyboard.prediction

import com.opent9.keyboard.jni.NativeEngineBridge

/**
 * Resolves prediction candidates from the C++ DAWG engine, applying language orthography,
 * user dictionary deletion filters, fallback projections via SuggestionGuard, and learned word prioritization.
 */
object CandidateResolver {

    /**
     * Queries candidates from the native engine and applies language-specific orthography rules.
     */
    fun getProcessedCandidates(currentComposingDigits: List<Int>): List<String> {
        val raw = NativeEngineBridge.getCandidates()
        val lang = NativeEngineBridge.getActiveLanguage()
        return when (lang) {
            "ID" -> IndonesianOrthography.processCandidates(raw, currentComposingDigits)
            else -> EnglishOrthography.processCandidates(raw, lang)
        }
    }

    /**
     * Resolves the full candidate list for active composing digits, accounting for:
     * 1. Word deletions in user dictionary
     * 2. Suggestion guard history and fallback projection
     * 3. Learned words preference promotion
     */
    fun resolveCandidates(
        currentComposingDigits: List<Int>,
        suggestionGuard: SuggestionGuard,
        learnedWordsRepository: LearnedWordsRepository,
        preferredWord: String? = null
    ): List<String> {
        val rawCandidates = getProcessedCandidates(currentComposingDigits).filterNot { candidate ->
            NativeEngineBridge.isWordDeleted(EnglishOrthography.toCanonical(candidate))
        }
        val candidatesList = if (rawCandidates.isNotEmpty()) {
            suggestionGuard.recordValidCandidates(rawCandidates, currentComposingDigits)
            rawCandidates
        } else if (currentComposingDigits.isNotEmpty()) {
            val lang = NativeEngineBridge.getActiveLanguage()
            suggestionGuard.getGuardedCandidates(currentComposingDigits, lang)
        } else {
            emptyList()
        }

        val lang = NativeEngineBridge.getActiveLanguage()
        val digitsKey = currentComposingDigits.joinToString("")
        val targetWord = preferredWord ?: learnedWordsRepository.getPreferredWord(lang, digitsKey)
        val hasLearned = preferredWord != null || learnedWordsRepository.hasPreferredWord(lang, digitsKey)

        return if (!targetWord.isNullOrEmpty()) {
            if (candidatesList.isNotEmpty()) {
                val mutable = ArrayList(candidatesList)
                val matchIdx = mutable.indexOfFirst { it.equals(targetWord, ignoreCase = true) }
                if (matchIdx > 0) {
                    val matched = mutable.removeAt(matchIdx)
                    mutable.add(0, matched)
                } else if (matchIdx < 0 && hasLearned) {
                    mutable.add(0, targetWord)
                }
                mutable
            } else if (hasLearned) {
                listOf(targetWord)
            } else {
                emptyList()
            }
        } else {
            candidatesList
        }
    }

    /**
     * Projects a fallback word when no valid candidate is found in beam search.
     */
    fun projectFallbackWord(
        currentComposingDigits: List<Int>,
        suggestionGuard: SuggestionGuard
    ): String {
        val lang = NativeEngineBridge.getActiveLanguage()
        return suggestionGuard.projectWordFromDigits(currentComposingDigits, alt = false, lang = lang)
    }
}
