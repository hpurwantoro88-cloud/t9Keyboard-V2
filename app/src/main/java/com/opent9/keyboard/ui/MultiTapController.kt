package com.opent9.keyboard.ui

import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputConnection
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.prediction.LearnedWordsRepository
import com.opent9.keyboard.settings.SettingsObserver

/**
 * Encapsulates the Multi-Tap (ABC) state machine: letter cycling timers,
 * composing character commitment, buffer accumulation, and word learning.
 */
class MultiTapController(
    private val settingsObserver: SettingsObserver,
    private val shiftController: ShiftController,
    private val learnedWordsRepository: LearnedWordsRepository,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    var onShiftUpdate: (() -> Unit)? = null,
    var onAutoCapsUpdate: (() -> Unit)? = null,
    var getInputConnection: (() -> InputConnection?)? = null
) {
    var isMultiTapActive: Boolean = false
        private set

    val multiTapWordBuffer = StringBuilder(64)

    private val multiTapTimeoutRunnable = Runnable {
        val ic = getInputConnection?.invoke()
        if (ic != null) {
            commitMultiTapActive(ic)
        }
    }

    /**
     * Handles key tap in Multi-tap ABC mode, cycling through characters on the same key.
     */
    fun handleMultiTap(digit: Int, ic: InputConnection) {
        mainHandler.removeCallbacks(multiTapTimeoutRunnable)
        isMultiTapActive = true
        val now = System.currentTimeMillis()
        val res = NativeEngineBridge.handleMultiTapPress(digit, now, shiftController.currentMode.stateValue)

        if (res.committedPrev) {
            if (res.committedChar.isLetter()) {
                multiTapWordBuffer.append(res.committedChar)
            } else {
                flushMultiTapWord(isIncognito = false)
            }
            ic.commitText(res.committedChar.toString(), 1)
            shiftController.onCharacterCommitted()
            onShiftUpdate?.invoke()
            onAutoCapsUpdate?.invoke()
        }

        ic.setComposingText(res.activeChar.toString(), 1)
        val timeout = settingsObserver.getMultiTapTimeout()
        mainHandler.postDelayed(multiTapTimeoutRunnable, timeout)
    }

    /**
     * Commits the currently active composing character under multi-tap timeout or interruption.
     */
    fun commitMultiTapActive(ic: InputConnection?, isIncognito: Boolean = false) {
        mainHandler.removeCallbacks(multiTapTimeoutRunnable)
        isMultiTapActive = false
        if (ic == null) return
        val committed = NativeEngineBridge.multiTapCommit()
        if (committed != 0.toChar()) {
            if (committed.isLetter()) {
                multiTapWordBuffer.append(committed)
            } else {
                flushMultiTapWord(isIncognito)
            }
            ic.finishComposingText()
            shiftController.onCharacterCommitted()
            onShiftUpdate?.invoke()
            onAutoCapsUpdate?.invoke()
        }
    }

    /**
     * Flushes accumulated multi-tap letters into the learned words repository for vocabulary enhancement.
     */
    fun flushMultiTapWord(isIncognito: Boolean) {
        if (multiTapWordBuffer.isNotEmpty()) {
            val word = multiTapWordBuffer.toString().trim()
            multiTapWordBuffer.clear()
            if (word.length >= 2) {
                learnedWordsRepository.recordWordUsage(word, isIncognito)
            }
        }
    }

    /**
     * Handles backspace during an active cycling multi-tap character.
     * Returns true if an active cycling character was dismissed.
     */
    fun handleBackspaceDuringMultiTap(): Boolean {
        if (isMultiTapActive) {
            mainHandler.removeCallbacks(multiTapTimeoutRunnable)
            NativeEngineBridge.multiTapReset()
            isMultiTapActive = false
            return true
        }
        return false
    }

    /**
     * Pops the last character from multiTapWordBuffer if backspace was pressed outside active cycling.
     */
    fun deleteBufferCharAtEnd() {
        if (multiTapWordBuffer.isNotEmpty()) {
            multiTapWordBuffer.deleteCharAt(multiTapWordBuffer.length - 1)
        }
    }

    /**
     * Completely resets multi-tap timer, native state, active flag, and word buffer.
     */
    fun reset() {
        mainHandler.removeCallbacks(multiTapTimeoutRunnable)
        NativeEngineBridge.multiTapReset()
        isMultiTapActive = false
        multiTapWordBuffer.clear()
    }
}
