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
        if (word.isBlank()) return false
        val canonical = EnglishOrthography.toCanonical(word).lowercase()
        return !NativeEngineBridge.isWordDeleted(canonical)
    }

    private fun isPrefix(prefix: List<Int>, full: List<Int>): Boolean {
        if (prefix.isEmpty() || prefix.size > full.size) return false
        for (i in prefix.indices) {
            if (prefix[i] != full[i]) return false
        }
        return true
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
            if (cand.isNotBlank() && isCandidateAllowed(cand) && seen.add(cand)) {
                results.add(cand)
            }
        }

        val heldWord = state.lastValidTopWord
        val heldDigits = state.lastValidDigits
        val matchesPrefix = heldDigits.isNotEmpty() && isPrefix(heldDigits, currentDigits)

        if (heldWord.isNotEmpty() && matchesPrefix && currentDigits.size > heldDigits.size) {
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

            for (c in state.lastValidCandidates) {
                if (c != heldWord) {
                    addCandidateIfAllowed(c)
                    if (results.size >= 5) break
                }
            }
        } else if (heldWord.isNotEmpty() && matchesPrefix && currentDigits.size == heldDigits.size) {
            addCandidateIfAllowed(heldWord)
            for (c in state.lastValidCandidates) {
                addCandidateIfAllowed(c)
                if (results.size >= 5) break
            }
        } else {
            val projected = projectWordFromDigits(currentDigits, alt = false, lang = lang)
            addCandidateIfAllowed(projected)

            val altProjected = projectWordFromDigits(currentDigits, alt = true, lang = lang)
            addCandidateIfAllowed(altProjected)
        }

        if (results.isEmpty() && currentDigits.isNotEmpty()) {
            val fallback = projectWordDeterministic(currentDigits)
            if (fallback.isNotEmpty()) {
                results.add(fallback)
            }
        }

        return results
    }

    /**
     * Deterministic primary-letter projection for fallback when all character-bigram candidates are filtered.
     */
    fun projectWordDeterministic(digits: List<Int>): String {
        val sb = StringBuilder(digits.size)
        for (d in digits) {
            val letters = DIGIT_LETTERS[d] ?: continue
            sb.append(letters[0])
        }
        return sb.toString()
    }

    /**
     * Extends an existing base word by predicting the highest-scoring letters for [trailingDigits].
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
     * Projects a complete word from digits using language-specific character-bigram transitions.
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

    private fun chooseBestLetterForDigit(
        digit: Int,
        prevChar: Char,
        alt: Boolean,
        lang: String
    ): Char {
        val letters = DIGIT_LETTERS[digit] ?: return ' '
        val isId = lang.equals("ID", ignoreCase = true)
        val table = if (isId) ID_BIGRAM_BONUSES else EN_BIGRAM_BONUSES
        val baseScores = if (isId) ID_UNIGRAM_PRIORS else EN_UNIGRAM_PRIORS
        val prevIsVowel = isVowel(prevChar)

        var bestChar = letters[0]
        var bestScore = Int.MIN_VALUE
        var secondChar = letters[0]
        var secondScore = Int.MIN_VALUE

        for (candidate in letters) {
            var score = baseScores[candidate] ?: 0
            // Encourage consonant-vowel alternation
            if (prevChar == ' ') {
                score += START_BONUSES[candidate] ?: 0
            } else {
                val candIsVowel = isVowel(candidate)
                if (prevIsVowel != candIsVowel) score += 25
                score += table[bigramKey(prevChar, candidate)] ?: 0
            }

            if (score > bestScore) {
                secondScore = bestScore
                secondChar = bestChar
                bestScore = score
                bestChar = candidate
            } else if (score > secondScore) {
                secondScore = score
                secondChar = candidate
            }
        }

        return if (alt && letters.size > 1) secondChar else bestChar
    }

    private fun isVowel(c: Char): Boolean = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'

    companion object {
        private fun bigramKey(prev: Char, next: Char): Int = (prev.code shl 8) or next.code

        private val DIGIT_LETTERS: Array<CharArray?> = arrayOf(
            null, null,
            charArrayOf('a', 'b', 'c'),
            charArrayOf('d', 'e', 'f'),
            charArrayOf('g', 'h', 'i'),
            charArrayOf('j', 'k', 'l'),
            charArrayOf('m', 'n', 'o'),
            charArrayOf('p', 'q', 'r', 's'),
            charArrayOf('t', 'u', 'v'),
            charArrayOf('w', 'x', 'y', 'z')
        )

        private val START_BONUSES = mapOf(
            'a' to 15, 'b' to 20, 'c' to 15, 'd' to 15, 'e' to 10,
            'g' to 15, 'h' to 20, 'i' to 10, 'k' to 20, 'l' to 15,
            'm' to 25, 'p' to 25, 's' to 35, 't' to 30, 'w' to 20
        )

        private val EN_UNIGRAM_PRIORS = mapOf(
            'a' to 32, 'b' to 10, 'c' to 18,
            'd' to 24, 'e' to 45, 'f' to 12,
            'g' to 15, 'h' to 22, 'i' to 34,
            'j' to 4,  'k' to 10, 'l' to 26,
            'm' to 16, 'n' to 36, 'o' to 30,
            'p' to 14, 'q' to 2,  'r' to 32, 's' to 38,
            't' to 35, 'u' to 18, 'v' to 8,
            'w' to 16, 'x' to 3,  'y' to 18, 'z' to 2
        )

        private val ID_UNIGRAM_PRIORS = mapOf(
            'a' to 42, 'b' to 16, 'c' to 10,
            'd' to 22, 'e' to 32, 'f' to 4,
            'g' to 18, 'h' to 14, 'i' to 36,
            'j' to 10, 'k' to 24, 'l' to 24,
            'm' to 22, 'n' to 38, 'o' to 26,
            'p' to 18, 'q' to 1,  'r' to 34, 's' to 32,
            't' to 30, 'u' to 28, 'v' to 3,
            'w' to 12, 'x' to 1,  'y' to 14, 'z' to 2
        )

        private val EN_BIGRAM_BONUSES: Map<Int, Int> = mapOf(
            bigramKey('t', 'h') to 65,
            bigramKey('c', 'h') to 55,
            bigramKey('s', 'h') to 55,
            bigramKey('w', 'h') to 50,
            bigramKey('n', 'g') to 60,
            bigramKey('c', 'k') to 55,
            bigramKey('q', 'u') to 70,
            bigramKey('e', 'd') to 55,
            bigramKey('a', 'd') to 40,
            bigramKey('i', 'd') to 40,
            bigramKey('o', 'd') to 35,
            bigramKey('e', 'r') to 45,
            bigramKey('i', 'n') to 55,
            bigramKey('a', 'n') to 45,
            bigramKey('o', 'n') to 40,
            bigramKey('l', 'y') to 60,
            bigramKey('d', 's') to 45,
            bigramKey('s', 's') to 35,
            bigramKey('l', 'l') to 30,
            bigramKey('y', 'e') to 40,
            bigramKey('e', 's') to 35,
            bigramKey('o', 'i') to 35
        )

        private val ID_BIGRAM_BONUSES: Map<Int, Int> = mapOf(
            bigramKey('n', 'g') to 70,
            bigramKey('n', 'y') to 65,
            bigramKey('k', 'h') to 45,
            bigramKey('s', 'y') to 45,
            bigramKey('a', 'n') to 55,
            bigramKey('i', 'n') to 45,
            bigramKey('k', 'a') to 40,
            bigramKey('m', 'e') to 45,
            bigramKey('b', 'e') to 40,
            bigramKey('p', 'e') to 40,
            bigramKey('t', 'e') to 40,
            bigramKey('a', 'd') to 30,
            bigramKey('i', 'd') to 30,
            bigramKey('u', 'd') to 30,
            bigramKey('a', 'r') to 35,
            bigramKey('o', 'r') to 45,
            bigramKey('r', 'o') to 45,
            bigramKey('a', 'h') to 30,
            bigramKey('u', 'a') to 30
        )
    }
}
