package com.opent9.keyboard.prediction

object IndonesianOrthography {

    data class SuffixSpec(
        val suffix: String,
        val digits: List<Int>
    )

    private val productiveSuffixes = listOf(
        SuffixSpec("nya", listOf(6, 9, 2)),
        SuffixSpec("kan", listOf(5, 2, 6)),
        SuffixSpec("lah", listOf(5, 2, 4)),
        SuffixSpec("kah", listOf(5, 2, 4)),
        SuffixSpec("pun", listOf(7, 8, 6)),
        SuffixSpec("ku", listOf(5, 8)),
        SuffixSpec("mu", listOf(6, 8))
    )

    fun toCanonical(word: String): String {
        return word.lowercase()
    }

    /**
     * Post-processes candidates for Bahasa Indonesia by synthesizing productive suffix
     * compounding (-nya, -kan, -ku, -mu, etc.) when typing beyond a known root word.
     * Corpus homograph ranking is handled directly by the native OPENT9D2 lexicon.
     */
    fun processCandidates(rawCandidates: List<String>, digits: List<Int> = emptyList()): List<String> {
        if (rawCandidates.isEmpty()) {
            return rawCandidates
        }

        val result = ArrayList<String>(rawCandidates.size + 2)
        result.addAll(rawCandidates)

        if (digits.size >= 4) {
            synthesizeSuffixCandidate(result, digits)
        }

        return if (result.size > 16) result.subList(0, 16) else result
    }

    private fun synthesizeSuffixCandidate(result: ArrayList<String>, digits: List<Int>) {
        val hasExactMatch = result.isNotEmpty() && WordCorrectionHelper.wordToDigits(result[0]) == digits
        var insertIndex = if (hasExactMatch) 1 else 0
        var inserted = 0

        for (spec in productiveSuffixes) {
            val sLen = spec.digits.size
            if (digits.size > sLen) {
                val trailing = digits.subList(digits.size - sLen, digits.size)
                if (trailing == spec.digits) {
                    val rootLen = digits.size - sLen
                    val rootDigits = digits.subList(0, rootLen)
                    val matchingRoot = result.firstOrNull {
                        WordCorrectionHelper.wordToDigits(it) == rootDigits
                    }
                    if (matchingRoot != null) {
                        val compounded = matchingRoot + spec.suffix
                        if (!result.contains(compounded)) {
                            result.add(insertIndex.coerceAtMost(result.size), compounded)
                            insertIndex++
                            inserted++
                            if (inserted >= 2) break
                        }
                    }
                }
            }
        }
    }
}
