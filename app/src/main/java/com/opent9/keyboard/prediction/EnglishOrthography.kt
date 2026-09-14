package com.opent9.keyboard.prediction

object EnglishOrthography {

    private val unambiguousContractions = mapOf(
        // Negations (-n't)
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

        // I-forms
        "im" to "I'm",
        "ive" to "I've",

        // You-forms
        "youre" to "you're",
        "youve" to "you've",
        "youll" to "you'll",
        "youd" to "you'd",

        // They-forms
        "theyre" to "they're",
        "theyve" to "they've",
        "theyll" to "they'll",
        "theyd" to "they'd",

        // We/He/She/It forms
        "weve" to "we've",
        "hes" to "he's",
        "shes" to "she's",
        "hed" to "he'd",
        "itll" to "it'll",
        "itd" to "it'd",

        // Demonstrative / Wh- forms
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

        // Modal + 've
        "couldve" to "could've",
        "shouldve" to "should've",
        "wouldve" to "would've",
        "mightve" to "might've",
        "mustve" to "must've",

        // Special English words
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
        return word.lowercase().replace("'", "")
    }

    /**
     * Transforms and expands a candidate list from the T9 engine for English.
     * - Restores unambiguous contractions (dont -> don't, im -> I'm, etc.)
     * - Restores standalone pronoun i -> I
     * - Expands ambiguous words so both the base word and contraction are available (its -> it's & its)
     * - Promotes high-frequency contractions over rare homographs (don't over foot, aren't over brent, you're over youse)
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
                    if (lower == "lets" || lower == "its" || lower == "ill" || lower == "hell" || lower == "wed") {
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

        // Promote very common contractions over rare homographs from the corpus
        promoteCandidate(result, "don't", "foot")
        promoteCandidate(result, "aren't", "brent")
        promoteCandidate(result, "you're", "youse")

        return if (result.size > 16) result.subList(0, 16) else result
    }

    private fun promoteCandidate(list: ArrayList<String>, target: String, demoted: String) {
        val targetIdx = list.indexOf(target)
        if (targetIdx > 0 && list[0] == demoted) {
            list.removeAt(targetIdx)
            list.add(0, target)
        }
    }
}
