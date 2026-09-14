package com.opent9.keyboard.jni

import java.nio.ByteBuffer

object NativeEngineBridge {

    var isNativeLoaded = false
        private set

    init {
        try {
            System.loadLibrary("opent9")
            isNativeLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            isNativeLoaded = false
        }
    }

    // Pre-allocated direct memory buffers (zero JVM heap churn in hot typing loop)
    private val candidateBuffer: ByteBuffer = ByteBuffer.allocateDirect(2048)
    private val multiTapBuffer: ByteBuffer = ByteBuffer.allocateDirect(16)

    // Reusable candidate scratch buffer
    private val scratchChars = CharArray(64)

    external fun nativeInit(dbPath: String): Boolean
    external fun nativeSyncConfig(
        sigma: Float,
        longPressMs: Int,
        multiTapMs: Int,
        autoSpace: Boolean,
        slangBoost: Boolean,
        decayDays: Int
    )
    external fun nativeLoadLexiconFd(langCode: String, fd: Int, offset: Long, length: Long): Boolean
    external fun nativeSwitchLanguage(langCode: String): Boolean
    external fun nativeGetActiveLanguage(): String
    external fun nativeUpdateKeyGeometry(digit: Int, cx: Float, cy: Float)

    external fun nativePushStroke(digit: Int, touchX: Float, touchY: Float): Boolean
    external fun nativePopStroke(): Boolean
    external fun nativeResetT9()
    external fun nativeGetCandidates(buffer: ByteBuffer, maxBytes: Int): Int

    external fun nativeMultiTapKeyPress(digit: Int, timestampMs: Long, shiftState: Int, buffer: ByteBuffer): Int
    external fun nativeMultiTapTimeout(timestampMs: Long, timeoutMs: Int, buffer: ByteBuffer): Boolean
    external fun nativeMultiTapCommit(): Char
    external fun nativeMultiTapReset()

    external fun nativePlayClick(style: Int, volume: Float)
    external fun nativeSyncAudioConfig(enabled: Boolean, volume: Float, style: Int)
    external fun nativeRecordUsage(word: String, nowSec: Long)
    external fun nativeRemoveWord(word: String): Boolean
    external fun nativeResetUserDictionary()
    external fun nativeIsWordDeleted(word: String): Boolean

    private val fallbackDeletedWords = HashSet<String>()

    fun initEngine(dbPath: String): Boolean {
        if (!isNativeLoaded) return true
        return nativeInit(dbPath)
    }

    fun syncConfig(
        sigma: Float,
        longPressMs: Int,
        multiTapMs: Int,
        autoSpace: Boolean,
        slangBoost: Boolean,
        decayDays: Int
    ) {
        if (!isNativeLoaded) return
        nativeSyncConfig(sigma, longPressMs, multiTapMs, autoSpace, slangBoost, decayDays)
    }

    fun syncAudioConfig(enabled: Boolean, volume: Float, style: Int) {
        if (!isNativeLoaded) return
        nativeSyncAudioConfig(enabled, volume, style)
    }

    fun loadLexiconFd(langCode: String, fd: Int, offset: Long, length: Long): Boolean {
        if (!isNativeLoaded) return true
        return nativeLoadLexiconFd(langCode, fd, offset, length)
    }

    private var fallbackLanguage = "ID"

    fun switchLanguage(langCode: String): Boolean {
        fallbackLanguage = langCode
        if (!isNativeLoaded) return true
        return nativeSwitchLanguage(langCode)
    }

    fun getActiveLanguage(): String {
        if (!isNativeLoaded) return fallbackLanguage
        return nativeGetActiveLanguage()
    }

    fun updateKeyGeometry(digit: Int, cx: Float, cy: Float) {
        if (!isNativeLoaded) return
        nativeUpdateKeyGeometry(digit, cx, cy)
    }

    fun pushStroke(digit: Int, touchX: Float, touchY: Float): Boolean {
        if (!isNativeLoaded) return true
        return nativePushStroke(digit, touchX, touchY)
    }

    fun popStroke(): Boolean {
        if (!isNativeLoaded) return true
        return nativePopStroke()
    }

    fun resetT9() {
        if (!isNativeLoaded) return
        nativeResetT9()
    }

    private val fallbackSequences = mapOf(
        1 to ".,?!'@#1",
        2 to "abc2",
        3 to "def3",
        4 to "ghi4",
        5 to "jkl5",
        6 to "mno6",
        7 to "pqrs7",
        8 to "tuv8",
        9 to "wxyz9",
        0 to " 0\n"
    )
    private var fallbackActiveDigit = -1
    private var fallbackCycleIndex = 0
    private var fallbackLastPressTime = 0L
    private var fallbackCurrentChar = 0.toChar()

    fun multiTapReset() {
        if (!isNativeLoaded) {
            fallbackActiveDigit = -1
            fallbackCycleIndex = 0
            fallbackLastPressTime = 0L
            fallbackCurrentChar = 0.toChar()
            return
        }
        nativeMultiTapReset()
    }

    fun multiTapCommit(): Char {
        if (!isNativeLoaded) {
            if (fallbackActiveDigit == -1) return 0.toChar()
            val committed = fallbackCurrentChar
            multiTapReset()
            return committed
        }
        return nativeMultiTapCommit()
    }

    fun playClick(style: Int, volume: Float) {
        if (!isNativeLoaded) return
        nativePlayClick(style, volume)
    }

    fun recordUsage(word: String, nowSec: Long) {
        if (!isNativeLoaded) return
        nativeRecordUsage(word, nowSec)
    }

    fun removeWord(word: String): Boolean {
        fallbackDeletedWords.add(word.lowercase())
        if (!isNativeLoaded) return true
        return nativeRemoveWord(word)
    }

    fun isWordDeleted(word: String): Boolean {
        val lower = word.lowercase()
        if (fallbackDeletedWords.contains(lower)) return true
        if (!isNativeLoaded) return false
        return nativeIsWordDeleted(lower)
    }

    fun resetUserDictionary() {
        fallbackDeletedWords.clear()
        if (!isNativeLoaded) return
        nativeResetUserDictionary()
    }

    fun getCandidates(): List<String> {
        if (!isNativeLoaded) return emptyList()
        candidateBuffer.clear()
        val bytesWritten = nativeGetCandidates(candidateBuffer, candidateBuffer.capacity())
        if (bytesWritten <= 0) return emptyList()

        candidateBuffer.position(0)
        val count = candidateBuffer.get().toInt() and 0xFF
        if (count == 0) return emptyList()

        val results = ArrayList<String>(count)
        for (i in 0 until count) {
            val len = candidateBuffer.get().toInt() and 0xFF
            var charIdx = 0
            for (j in 0 until len) {
                scratchChars[charIdx++] = (candidateBuffer.get().toInt() and 0xFF).toChar()
            }
            results.add(String(scratchChars, 0, len))
        }
        return results
    }

    data class MultiTapResult(val committedPrev: Boolean, val committedChar: Char, val activeChar: Char)

    fun handleMultiTapPress(digit: Int, timestampMs: Long, shiftState: Int): MultiTapResult {
        if (!isNativeLoaded) {
            val seq = fallbackSequences[digit] ?: return MultiTapResult(false, 0.toChar(), (digit + '0'.code).toChar())
            var committedPrev = false
            var committedChar = 0.toChar()

            if (fallbackActiveDigit != -1 && fallbackActiveDigit != digit) {
                committedPrev = true
                committedChar = fallbackCurrentChar
                fallbackActiveDigit = digit
                fallbackCycleIndex = 0
            } else if (fallbackActiveDigit == digit) {
                fallbackCycleIndex = (fallbackCycleIndex + 1) % seq.length
            } else {
                fallbackActiveDigit = digit
                fallbackCycleIndex = 0
            }

            fallbackLastPressTime = timestampMs
            val baseChar = seq[fallbackCycleIndex]
            fallbackCurrentChar = if ((shiftState == 1 || shiftState == 2) && baseChar.isLowerCase()) {
                baseChar.uppercaseChar()
            } else {
                baseChar
            }
            return MultiTapResult(committedPrev, committedChar, fallbackCurrentChar)
        }
        multiTapBuffer.clear()
        nativeMultiTapKeyPress(digit, timestampMs, shiftState, multiTapBuffer)
        multiTapBuffer.position(0)
        val committedPrev = multiTapBuffer.get().toInt() == 1
        val committedChar = multiTapBuffer.get().toInt().toChar()
        val activeChar = multiTapBuffer.get().toInt().toChar()
        return MultiTapResult(committedPrev, committedChar, activeChar)
    }

    fun handleMultiTapTimeout(timestampMs: Long, timeoutMs: Int): Char? {
        if (!isNativeLoaded) {
            if (fallbackActiveDigit != -1 && (timestampMs - fallbackLastPressTime) >= timeoutMs) {
                val committed = fallbackCurrentChar
                multiTapReset()
                return committed
            }
            return null
        }
        multiTapBuffer.clear()
        val timedOut = nativeMultiTapTimeout(timestampMs, timeoutMs, multiTapBuffer)
        if (timedOut) {
            multiTapBuffer.position(0)
            return multiTapBuffer.get().toInt().toChar()
        }
        return null
    }
}
