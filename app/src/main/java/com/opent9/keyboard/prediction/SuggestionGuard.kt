package com.opent9.keyboard.prediction

import com.opent9.keyboard.jni.NativeEngineBridge

class SuggestionGuard {

    data class State(
        val lastValidCandidates: List<String> = emptyList(),
        val lastValidDigits: List<Int> = emptyList(),
        val lastValidTopWord: String = ""
    )

    private var state = State()

    fun recordValidCandidates(candidates: List<String>, digits: List<Int>) {
        if (candidates.isNotEmpty()) {
            state = State(
                lastValidCandidates = ArrayList(candidates),
                lastValidDigits = ArrayList(digits),
                lastValidTopWord = candidates[0]
            )
        }
    }

    fun reset() {
        state = State()
    }

    fun isGuarded(): Boolean = state.lastValidCandidates.isNotEmpty()

    val lastValidTopWord: String
        get() = state.lastValidTopWord

    private fun isCandidateAllowed(word: String): Boolean {
        val canonical = EnglishOrthography.toCanonical(word).lowercase()
        return !NativeEngineBridge.isWordDeleted(canonical)
    }

    /**
     * Generates a guarded candidate list when the native engine finds 0 matches for [currentDigits].
     * Never returns an empty list if [currentDigits] is not empty.
     */
    fun getGuardedCandidates(currentDigits: List<Int>, lang: String = "EN"): List<String> {
        if (currentDigits.isEmpty()) return emptyList()

        val results = ArrayList<String>(8)
        val seen = HashSet<String>()

        fun addCandidateIfAllowed(cand: String) {
            if (isCandidateAllowed(cand) && seen.add(cand)) {
                results.add(cand)
            }
        }

        val heldWord = state.lastValidTopWord
        val heldDigits = state.lastValidDigits

        if (heldWord.isNotEmpty() && currentDigits.size > heldDigits.size) {
            val trailingDigits = currentDigits.subList(heldDigits.size, currentDigits.size)
            val extPrimary = extendWordPhonotactically(heldWord, trailingDigits, alt = false, lang = lang)
            val extAlt = extendWordPhonotactically(heldWord, trailingDigits, alt = true, lang = lang)

            // If 1 extra digit was typed, prioritize the held complete word to absorb accidental typos.
            // If 2 or more extra digits were typed, prioritize the extended word as the user is deliberately typing a new word.
            if (trailingDigits.size == 1) {
                addCandidateIfAllowed(heldWord)
                addCandidateIfAllowed(extPrimary)
                addCandidateIfAllowed(extAlt)
            } else {
                if (isCandidateAllowed(extPrimary)) {
                    addCandidateIfAllowed(extPrimary)
                    addCandidateIfAllowed(heldWord)
                    addCandidateIfAllowed(extAlt)
                } else {
                    addCandidateIfAllowed(extAlt)
                    addCandidateIfAllowed(heldWord)
                }
            }

            // Also preserve alternate candidates from the last valid state
            for (c in state.lastValidCandidates) {
                if (c != heldWord) {
                    addCandidateIfAllowed(c)
                    if (results.size >= 5) break
                }
            }
        } else if (heldWord.isNotEmpty() && currentDigits.size == heldDigits.size) {
            // Same length: hold the valid candidates
            addCandidateIfAllowed(heldWord)
            for (c in state.lastValidCandidates) {
                addCandidateIfAllowed(c)
                if (results.size >= 5) break
            }
        } else {
            // No valid prefix was ever held (e.g. unknown word from the very start):
            val projected = projectWordFromDigits(currentDigits, alt = false, lang = lang)
            addCandidateIfAllowed(projected)

            val altProjected = projectWordFromDigits(currentDigits, alt = true, lang = lang)
            addCandidateIfAllowed(altProjected)
        }

        // Never append numeric digits to suggestions. If all phonotactic candidates were filtered,
        // provide a deterministic alphabetic fallback using standard primary key letters.
        if (results.isEmpty() && currentDigits.isNotEmpty()) {
            val fallback = projectWordDeterministic(currentDigits)
            if (fallback.isNotEmpty()) {
                results.add(fallback)
            }
        }

        return results
    }

    /**
     * Deterministic primary-letter projection for fallback when all phonotactic candidates are filtered.
     */
    fun projectWordDeterministic(digits: List<Int>): String {
        val sb = StringBuilder(digits.size)
        for (d in digits) {
            val c = when (d) {
                2 -> 'a'
                3 -> 'd'
                4 -> 'g'
                5 -> 'j'
                6 -> 'm'
                7 -> 'p'
                8 -> 't'
                9 -> 'w'
                else -> ' '
            }
            if (c != ' ') sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Extends an existing base word by predicting the most natural letters for [trailingDigits].
     */
    fun extendWordPhonotactically(
        baseWord: String,
        trailingDigits: List<Int>,
        alt: Boolean = false,
        lang: String = "EN"
    ): String {
        val sb = StringBuilder(baseWord)
        for (i in trailingDigits.indices) {
            val d = trailingDigits[i]
            val prevChar = if (sb.isNotEmpty()) sb.last().lowercaseChar() else ' '
            val letter = chooseBestLetterForDigit(d, prevChar, alt && i == 0, lang)
            if (letter != ' ') {
                sb.append(letter)
            }
        }
        return sb.toString()
    }

    /**
     * Projects a complete word from digits using phonotactic transition heuristics.
     */
    fun projectWordFromDigits(
        digits: List<Int>,
        alt: Boolean = false,
        lang: String = "EN"
    ): String {
        val sb = StringBuilder(digits.size)
        for (i in digits.indices) {
            val d = digits[i]
            val prevChar = if (sb.isNotEmpty()) sb.last().lowercaseChar() else ' '
            val letter = chooseBestLetterForDigit(d, prevChar, alt && i == digits.lastIndex, lang)
            if (letter != ' ') {
                sb.append(letter)
            }
        }
        return sb.toString()
    }

    private fun isVowel(c: Char): Boolean = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y'

    private fun chooseBestLetterForDigit(
        digit: Int,
        prevChar: Char,
        alt: Boolean = false,
        lang: String = "EN"
    ): Char {
        val isId = lang.equals("ID", ignoreCase = true)
        return when (digit) {
            2 -> { // a, b, c
                if (alt) 'b'
                else if (!isVowel(prevChar) || prevChar == ' ') 'a'
                else 'c'
            }
            3 -> { // d, e, f
                if (alt) 'f'
                else if (isId) {
                    if (prevChar == 'a' || prevChar == 'i' || prevChar == 'u') 'd'
                    else 'e'
                } else {
                    if (prevChar == 'e') 'd' // -ed past tense
                    else if (prevChar == 'a' || prevChar == 'i' || prevChar == 'o') 'd' // -ad, -id, -od
                    else 'e' // default to 'e'
                }
            }
            4 -> { // g, h, i
                if (alt) 'g'
                else if (prevChar == 't' || prevChar == 'c' || prevChar == 's' || prevChar == 'w') 'h' // th, ch, sh, wh
                else if (prevChar == 'n') 'g' // -ng
                else 'i'
            }
            5 -> { // j, k, l
                if (alt) 'k'
                else if (prevChar == 'c') 'k' // ck
                else 'l'
            }
            6 -> { // m, n, o
                if (alt) 'm'
                else if (prevChar == 'i' || (isId && prevChar == 'a')) 'n' // in, -an
                else if (!isVowel(prevChar) || prevChar == ' ') 'o'
                else 'n'
            }
            7 -> { // p, q, r, s
                if (isId) {
                    if (alt) 's'
                    else if (isVowel(prevChar)) 'r' // Indonesian: -oro, motor, kantor, nomor, lapor, etc.
                    else 's'
                } else {
                    if (alt) 'r'
                    else if (prevChar == 'e') 'r' // -er
                    else 's' // -s plural / 3rd person
                }
            }
            8 -> { // t, u, v
                if (alt) 'v'
                else if (prevChar == 'q') 'u' // qu
                else if (!isVowel(prevChar) || prevChar == ' ') 'u'
                else 't'
            }
            9 -> { // w, x, y, z
                if (alt) 'w'
                else if (prevChar == 'l') 'y' // -ly
                else if (isVowel(prevChar)) 'w'
                else 'y'
            }
            else -> ' '
        }
    }
}
