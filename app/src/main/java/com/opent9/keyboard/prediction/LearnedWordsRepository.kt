package com.opent9.keyboard.prediction

import android.content.Context
import com.opent9.keyboard.jni.NativeEngineBridge
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Facade for reporting vocabulary usage and removals to the native DynamicStore,
 * with debounced off-main-thread persistence flushes.
 */
class LearnedWordsRepository(context: Context) {

    private val appContext = context.applicationContext ?: context

    init {
        clearLegacyPrefs()
    }

    /**
     * Records usage of a committed word for dynamic learning without blocking the UI thread on disk I/O.
     */
    fun recordWordUsage(word: String, isIncognito: Boolean) {
        if (isIncognito || word.isEmpty() || word.all { it.isDigit() }) return
        val canonicalWord = EnglishOrthography.toCanonical(word).lowercase()
        if (canonicalWord.isEmpty()) return

        if (!NativeEngineBridge.isWordDeleted(canonicalWord)) {
            NativeEngineBridge.recordUsage(canonicalWord, System.currentTimeMillis() / 1000L)
            scheduleDebouncedFlush()
        }
    }

    /**
     * Records a personal word-pair bigram for Next-Word Prediction and contextual disambiguation.
     */
    fun recordBigramUsage(prevWord: String?, nextWord: String, isIncognito: Boolean) {
        if (isIncognito || prevWord.isNullOrBlank() || nextWord.isBlank()) return
        if (prevWord.all { it.isDigit() } || nextWord.all { it.isDigit() }) return
        NativeEngineBridge.recordBigram(prevWord, nextWord, System.currentTimeMillis() / 1000L)
        scheduleDebouncedFlush()
    }

    /**
     * Removes a word via the native blacklist/dictionary and schedules an async disk flush.
     */
    fun removeWord(word: String): String {
        val canonical = EnglishOrthography.toCanonical(word).lowercase()
        NativeEngineBridge.removeWord(canonical)
        scheduleDebouncedFlush()
        return canonical
    }

    /**
     * Clears both the native user dictionary and any legacy SharedPreferences cache.
     */
    fun clearAll() {
        cancelScheduledFlush()
        NativeEngineBridge.resetUserDictionary()
        clearLegacyPrefs()
    }

    /**
     * Flushes dirty native DynamicStore entries to disk on a background thread.
     */
    fun flushAsync() {
        cancelScheduledFlush()
        try {
            flushExecutor.execute {
                NativeEngineBridge.flushDynamicStore()
            }
        } catch (_: Exception) {}
    }

    private fun scheduleDebouncedFlush() {
        try {
            synchronized(flushLock) {
                pendingFlush?.cancel(false)
                pendingFlush = flushExecutor.schedule(
                    { NativeEngineBridge.flushDynamicStore() },
                    FLUSH_DEBOUNCE_MS,
                    TimeUnit.MILLISECONDS
                )
            }
        } catch (_: Exception) {}
    }

    private fun cancelScheduledFlush() {
        synchronized(flushLock) {
            pendingFlush?.cancel(false)
            pendingFlush = null
        }
    }

    private fun clearLegacyPrefs() {
        try {
            val prefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.all.isNotEmpty()) {
                prefs.edit().clear().apply()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val LEGACY_PREFS_NAME = "opent9_last_picked"
        private const val FLUSH_DEBOUNCE_MS = 1500L

        private val flushLock = Any()
        private var pendingFlush: ScheduledFuture<*>? = null
        private val flushExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "OpenT9-StoreFlush").apply { isDaemon = true }
        }
    }
}
