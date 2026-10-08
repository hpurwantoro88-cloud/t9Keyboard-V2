package com.opent9.keyboard.ui

/**
 * Utility for applying ShiftMode casing (Lowercase, Titlecase, Uppercase)
 * to candidate and committed words.
 */
object ShiftFormatter {

    /**
     * Formats a word according to the given [ShiftMode].
     * Preserves single-letter English pronoun "I" and contractions like "I'm".
     */
    fun format(word: String, mode: ShiftMode): String {
        return when (mode) {
            ShiftMode.TITLECASE -> word.replaceFirstChar { it.uppercase() }
            ShiftMode.UPPERCASE -> word.uppercase()
            ShiftMode.LOWERCASE -> {
                if (word == "I" || word.startsWith("I'")) word else word.lowercase()
            }
        }
    }
}
