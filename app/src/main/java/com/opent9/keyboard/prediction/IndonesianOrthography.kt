package com.opent9.keyboard.prediction

object IndonesianOrthography {

    // Common Indonesian homograph promotions where a colloquial / high-frequency word
    // was ranked lower than an archaic or rare dictionary word with the same T9 digits.
    private val homographPromotions = listOf(
        "capek" to "abrek",    // 22735
        "cape" to "base",      // 2273
        "makasi" to "majasi",  // 625274
        "makasi" to "oblasi",  // 625274
        "bener" to "adnes",    // 23637
        "sis" to "rip",        // 747
        "haha" to "gaib",      // 4242
        "dong" to "fond",      // 3664
        "nih" to "oii",        // 644
        "kepo" to "leps"       // 5376
    )

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
     * Post-processes candidates for Bahasa Indonesia:
     * 1. Promotes common daily words over rare/archaic homographs.
     * 2. Synthesizes productive suffix compounding (-nya, -kan, -ku, -mu, etc.)
     *    when typing beyond a known root word.
     */
    fun processCandidates(rawCandidates: List<String>, digits: List<Int> = emptyList()): List<String> {
        if (rawCandidates.isEmpty()) {
            return rawCandidates
        }

        val result = ArrayList<String>(rawCandidates.size + 4)
        result.addAll(rawCandidates)

        // 1. Promote common daily / informal words over archaic homographs
        for ((preferred, demoted) in homographPromotions) {
            promoteCandidate(result, preferred, demoted)
        }

        // 2. Suffix compounding synthesis for Indonesian morphology
        if (digits.size >= 4) {
            synthesizeSuffixCandidate(result, digits)
        }

        return if (result.size > 16) result.subList(0, 16) else result
    }

    private fun promoteCandidate(list: ArrayList<String>, target: String, demoted: String) {
        val targetIdx = list.indexOf(target)
        if (targetIdx > 0 && list.size > 0 && (list[0] == demoted || targetIdx <= 3)) {
            list.removeAt(targetIdx)
            list.add(0, target)
        }
    }

    private fun synthesizeSuffixCandidate(result: ArrayList<String>, digits: List<Int>) {
        for (spec in productiveSuffixes) {
            val sLen = spec.digits.size
            if (digits.size > sLen) {
                val trailing = digits.subList(digits.size - sLen, digits.size)
                if (trailing == spec.digits) {
                    val rootLen = digits.size - sLen
                    // Look for a matching root word in existing candidates
                    val matchingRoot = result.firstOrNull { it.length == rootLen }
                    if (matchingRoot != null) {
                        val compounded = matchingRoot + spec.suffix
                        if (!result.contains(compounded)) {
                            result.add(0, compounded)
                            break
                        }
                    }
                }
            }
        }
    }
}
