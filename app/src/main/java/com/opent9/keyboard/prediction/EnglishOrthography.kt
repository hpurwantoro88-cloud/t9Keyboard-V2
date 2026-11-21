package com.opent9.keyboard.prediction

object EnglishOrthography {

    private val unambiguousContractions = mapOf(
        "dont" to "don't",
        "doesnt" to "doesn't",
        "didnt" to "didn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "havent" to "haven't",
        "hasnt" to "hasn't",
        "hadnt" to "hadn't",
        "wont" to "won't",
        "wouldnt" to "wouldn't",
        "couldnt" to "couldn't",
        "shouldnt" to "shouldn't",
        "cant" to "can't",
        "mustnt" to "mustn't",
        "neednt" to "needn't",
        "darent" to "daren't",
        "aint" to "ain't",
        "shant" to "shan't",
        "mightnt" to "mightn't",
        "oughtnt" to "oughtn't",
        "im" to "I'm",
        "ive" to "I've",
        "youre" to "you're",
        "youve" to "you've",
        "youll" to "you'll",
        "youd" to "you'd",
        "theyre" to "they're",
        "theyve" to "they've",
        "theyll" to "they'll",
        "theyd" to "they'd",
        "weve" to "we've",
        "hes" to "he's",
        "shes" to "she's",
        "hed" to "he'd",
        "itll" to "it'll",
        "itd" to "it'd",
        "thats" to "that's",
        "thatll" to "that'll",
        "thatd" to "that'd",
        "whats" to "what's",
        "whatll" to "what'll",
        "whatd" to "what'd",
        "wheres" to "where's",
        "whered" to "where'd",
        "wherell" to "where'll",
        "theres" to "there's",
        "therell" to "there'll",
        "thered" to "there'd",
        "heres" to "here's",
        "whos" to "who's",
        "wholl" to "who'll",
        "whod" to "who'd",
        "hows" to "how's",
        "howll" to "how'll",
        "howd" to "how'd",
        "whens" to "when's",
        "whys" to "why's",
        "couldve" to "could've",
        "shouldve" to "should've",
        "wouldve" to "would've",
        "mightve" to "might've",
        "mustve" to "must've",
        "oclock" to "o'clock",
        "maam" to "ma'am",
        "neer" to "ne'er",
        "oer" to "o'er"
    )

    private val ambiguousContractions = mapOf(
        "its" to "it's",
        "were" to "we're",
        "well" to "we'll",
        "ill" to "I'll",
        "lets" to "let's",
        "hell" to "he'll",
        "shell" to "she'll",
        "shed" to "she'd",
        "wed" to "we'd",
        "id" to "I'd"
    )

    /**
     * Maps an isolated canonical word to its display form.
     * Leaves ambiguous words unchanged to avoid false positives in isolated lookup.
     */
    fun displayForm(canonical: String, lang: String): String {
        if (lang != "EN") return canonical
        val lower = canonical.lowercase()
        unambiguousContractions[lower]?.let { return it }
        if (lower == "i") return "I"
        return canonical
    }

    /**
     * Converts a display word back to its canonical lowercase form for dictionary frequency tracking.
     */
    fun toCanonical(word: String): String {
        return word.lowercase().replace("'", "").replace("’", "").replace("-", "")
    }

    /**
     * Restores English contractions inside a bilingual candidate list without altering
     * standalone Indonesian tokens such as "i".
     */
    fun restoreBilingualContractions(rawCandidates: List<String>): List<String> {
        if (rawCandidates.isEmpty()) return rawCandidates
        val result = ArrayList<String>(rawCandidates.size + 2)
        val seen = HashSet<String>()
        for (raw in rawCandidates) {
            val lower = raw.lowercase()
            when {
                unambiguousContractions.containsKey(lower) -> {
                    val restored = unambiguousContractions[lower]!!
                    if (seen.add(restored)) result.add(restored)
                }
                ambiguousContractions.containsKey(lower) -> {
                    val contraction = ambiguousContractions[lower]!!
                    val alreadyHasContraction = rawCandidates.any { it.equals(contraction, ignoreCase = true) }
                    if (alreadyHasContraction) {
                        if (seen.add(raw)) result.add(raw)
                    } else {
                        if (seen.add(raw)) result.add(raw)
                        if (seen.add(contraction)) result.add(contraction)
                    }
                }
                else -> {
                    if (seen.add(raw)) result.add(raw)
                }
            }
        }
        return if (result.size > 16) result.subList(0, 16) else result
    }

    /**
     * Transforms English candidates by restoring contractions (when coming from fallback sources)
     * and expanding ambiguous base/contraction pairs when not already supplied by OPENT9D2.
     */
    fun processCandidates(rawCandidates: List<String>, lang: String): List<String> {
        if (lang != "EN" || rawCandidates.isEmpty()) {
            return rawCandidates
        }

        val result = ArrayList<String>(rawCandidates.size + 4)
        val seen = HashSet<String>()

        for (raw in rawCandidates) {
            val lower = raw.lowercase()
            when {
                lower == "i" -> {
                    if (seen.add("I")) result.add("I")
                }
                unambiguousContractions.containsKey(lower) -> {
                    val restored = unambiguousContractions[lower]!!
                    if (seen.add(restored)) result.add(restored)
                }
                ambiguousContractions.containsKey(lower) -> {
                    val contraction = ambiguousContractions[lower]!!
                    val alreadyHasContraction = rawCandidates.any { it.equals(contraction, ignoreCase = true) }
                    if (alreadyHasContraction) {
                        if (seen.add(raw)) result.add(raw)
                    } else if (lower == "lets" || lower == "its" || lower == "ill" || lower == "hell" || lower == "wed") {
                        if (seen.add(contraction)) result.add(contraction)
                        if (seen.add(raw)) result.add(raw)
                    } else {
                        if (seen.add(raw)) result.add(raw)
                        if (seen.add(contraction)) result.add(contraction)
                    }
                }
                else -> {
                    if (seen.add(raw)) result.add(raw)
                }
            }
        }

        return if (result.size > 16) result.subList(0, 16) else result
    }
}
