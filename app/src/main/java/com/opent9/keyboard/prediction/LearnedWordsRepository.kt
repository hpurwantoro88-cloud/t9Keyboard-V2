package com.opent9.keyboard.prediction

import android.content.Context
import android.content.SharedPreferences
import com.opent9.keyboard.jni.NativeEngineBridge

/**
 * Manages persistent storage and caching of user-selected words per digit sequence,
 * as well as reporting vocabulary usage to the native prediction engine.
 */
class LearnedWordsRepository(context: Context) {

    private val prefs: SharedPreferences? = try {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Exception) {
        null
    }

    private val lastPickedWords = HashMap<String, String>()

    init {
        loadFromPreferences()
    }

    private fun loadFromPreferences() {
        try {
            prefs?.all?.forEach { (key, value) ->
                if (key.startsWith(PREFIX) && value is String) {
                    lastPickedWords[key.removePrefix(PREFIX)] = value
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Resolves the preferred/most-recently selected word for a digit sequence in a given language.
     */
    fun getPreferredWord(lang: String, digitsKey: String): String? {
        val langKey = "${lang}_$digitsKey"
        return lastPickedWords[langKey] ?: lastPickedWords[digitsKey]
    }

    /**
     * Checks if a preferred word is explicitly mapped for the language or digit sequence.
     */
    fun hasPreferredWord(lang: String, digitsKey: String): Boolean {
        val langKey = "${lang}_$digitsKey"
        return lastPickedWords.containsKey(langKey) || lastPickedWords.containsKey(digitsKey)
    }

    /**
     * Records a picked word commit, updating memory cache, persistent storage, and native dynamic store.
     */
    fun recordPickedWord(lang: String, digitsKey: String, word: String, isIncognito: Boolean) {
        if (isIncognito || word.isEmpty() || word.all { it.isDigit() }) return

        val canonicalWord = EnglishOrthography.toCanonical(word).lowercase()
        if (!NativeEngineBridge.isWordDeleted(canonicalWord)) {
            NativeEngineBridge.recordUsage(canonicalWord, System.currentTimeMillis() / 1000L)
        }

        if (digitsKey.isNotEmpty() && canonicalWord.isNotEmpty()) {
            val langKey = "${lang}_$digitsKey"
            lastPickedWords[langKey] = canonicalWord
            lastPickedWords[digitsKey] = canonicalWord
            try {
                prefs?.edit()
                    ?.putString("$PREFIX$langKey", canonicalWord)
                    ?.putString("$PREFIX$digitsKey", canonicalWord)
                    ?.apply()
            } catch (_: Exception) {}
        }
    }

    /**
     * Records usage of a committed word for dynamic learning without digit key mapping.
     */
    fun recordWordUsage(word: String, isIncognito: Boolean) {
        if (isIncognito || word.isEmpty() || word.all { it.isDigit() }) return
        val canonicalWord = EnglishOrthography.toCanonical(word).lowercase()
        if (!NativeEngineBridge.isWordDeleted(canonicalWord)) {
            NativeEngineBridge.recordUsage(canonicalWord, System.currentTimeMillis() / 1000L)
        }
    }

    /**
     * Removes a word from native blacklist/dictionary and clears all mapped digit sequences from storage.
     */
    fun removeWord(word: String): String {
        val canonical = EnglishOrthography.toCanonical(word).lowercase()
        NativeEngineBridge.removeWord(canonical)

        val keysToRemove = lastPickedWords.filter { it.value.equals(canonical, ignoreCase = true) }.keys.toList()
        if (keysToRemove.isNotEmpty()) {
            try {
                val editor = prefs?.edit()
                for (k in keysToRemove) {
                    lastPickedWords.remove(k)
                    editor?.remove("$PREFIX$k")
                }
                editor?.apply()
            } catch (_: Exception) {}
        }
        return canonical
    }

    companion object {
        private const val PREFS_NAME = "opent9_last_picked"
        private const val PREFIX = "digits_"
    }
}
