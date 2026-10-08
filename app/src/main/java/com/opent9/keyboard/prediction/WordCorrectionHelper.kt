package com.opent9.keyboard.prediction

data class WordCorrectionContext(
    val originalWord: String,
    val isSelection: Boolean,
    val beforeLength: Int = 0,
    val afterLength: Int = 0
)

object WordCorrectionHelper {

    fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\''

    fun extractWordPartBefore(text: String): String {
        var idx = text.length - 1
        while (idx >= 0 && isWordChar(text[idx])) {
            idx--
        }
        return text.substring(idx + 1)
    }

    fun extractWordPartAfter(text: String): String {
        var idx = 0
        while (idx < text.length && isWordChar(text[idx])) {
            idx++
        }
        return text.substring(0, idx)
    }

    fun wordToDigits(word: String): List<Int> {
        val digits = ArrayList<Int>(word.length)
        for (ch in word.lowercase()) {
            val d = when (ch) {
                'a', 'b', 'c' -> 2
                'd', 'e', 'f' -> 3
                'g', 'h', 'i' -> 4
                'j', 'k', 'l' -> 5
                'm', 'n', 'o' -> 6
                'p', 'q', 'r', 's' -> 7
                't', 'u', 'v' -> 8
                'w', 'x', 'y', 'z' -> 9
                else -> continue
            }
            digits.add(d)
        }
        return digits
    }

    fun matchCase(source: String, target: String): String {
        if (source.isEmpty() || target.isEmpty()) return target
        if (source.all { it.isUpperCase() }) return target.uppercase()
        if (source[0].isUpperCase()) return target.replaceFirstChar { it.uppercase() }
        return target.lowercase()
    }
}
