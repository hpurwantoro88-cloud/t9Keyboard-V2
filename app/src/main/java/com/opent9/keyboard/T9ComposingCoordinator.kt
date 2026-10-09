package com.opent9.keyboard

import android.view.inputmethod.InputConnection
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.prediction.CandidateResolver
import com.opent9.keyboard.prediction.LearnedWordsRepository
import com.opent9.keyboard.prediction.SuggestionGuard
import com.opent9.keyboard.prediction.WordCorrectionController
import com.opent9.keyboard.prediction.WordCorrectionHelper
import com.opent9.keyboard.settings.SettingsObserver
import com.opent9.keyboard.ui.DeleteController
import com.opent9.keyboard.ui.MultiTapController
import com.opent9.keyboard.ui.ShiftController
import com.opent9.keyboard.ui.T9KeyboardView

/**
 * Manages advanced composing state, candidate commitments, stroke operations,
 * and input connection synchronization for T9 predictive typing.
 */
class T9ComposingCoordinator(
    val currentComposingDigits: ArrayList<Int>,
    val currentComposingStrokes: ArrayList<ComposingStroke>,
    val suggestionGuard: SuggestionGuard,
    private val getHasActiveComposing: () -> Boolean,
    private val setHasActiveComposing: (Boolean) -> Unit,
    private val getActiveCandidates: () -> List<String>,
    private val setActiveCandidates: (List<String>) -> Unit,
    private val getComposingAnchorPosition: () -> Int,
    private val setComposingAnchorPosition: (Int) -> Unit,
    private val getLastSelectionStart: () -> Int,
    private val setLastSpaceTapTime: (Long) -> Unit,
    private val setIsBackspaceAction: (Boolean) -> Unit,
    private val incrementIgnoreSelectionUpdateCount: (Int) -> Unit,
    private val getInputConnection: () -> InputConnection?,
    private val getKeyboardView: () -> T9KeyboardView?,
    private val getShiftController: () -> ShiftController,
    private val getDeleteController: () -> DeleteController,
    private val getLearnedWordsRepository: () -> LearnedWordsRepository,
    private val getMultiTapController: () -> MultiTapController,
    private val getWordCorrectionController: () -> WordCorrectionController,
    private val getSettingsObserver: () -> SettingsObserver,
    private val isIncognito: () -> Boolean,
    private val updateAutoCaps: (Boolean) -> Unit,
    private val applyShiftFormatting: (String) -> String,
    private val updateCandidatesFromNative: (InputConnection, String?) -> Unit,
    private val clearWordCorrection: () -> Unit
) {

    fun commitActiveCandidate(addSpace: Boolean) {
        val candidates = getActiveCandidates()
        val word = if (candidates.isNotEmpty()) {
            applyShiftFormatting(candidates[0])
        } else if (currentComposingDigits.isNotEmpty()) {
            applyShiftFormatting(CandidateResolver.projectFallbackWord(currentComposingDigits, suggestionGuard))
        } else {
            ""
        }
        if (word.isNotEmpty()) {
            commitWord(word, addSpace)
        } else if (addSpace) {
            getInputConnection()?.commitText(" ", 1)
        }
    }

    fun commitCandidateIndex(index: Int) {
        val candidates = getActiveCandidates()
        if (index in candidates.indices) {
            val word = applyShiftFormatting(candidates[index])
            val addSpace = getSettingsObserver().isAutoSpaceEnabled()
            commitWord(word, addSpace = addSpace)
        }
    }

    fun commitWord(word: String, addSpace: Boolean) {
        val ic = getInputConnection() ?: return
        val digitsSnapshot = ArrayList(currentComposingDigits)

        // commitText atomically replaces any active composing text and finishes composing
        val textToCommit = if (addSpace) "$word " else word
        ic.commitText(textToCommit, 1)

        setLastSpaceTapTime(if (addSpace) System.currentTimeMillis() else 0L)

        // Record commit for retroactive un-commit
        getDeleteController().recordCommit(word, digitsSnapshot)

        // Record usage for dynamic dictionary learning if not incognito and not pure numeric
        val digitsKey = if (digitsSnapshot.isNotEmpty()) {
            digitsSnapshot.joinToString("")
        } else {
            WordCorrectionHelper.wordToDigits(word).joinToString("")
        }
        val lang = NativeEngineBridge.getActiveLanguage()
        getLearnedWordsRepository().recordPickedWord(lang, digitsKey, word, isIncognito())

        // Auto-reset Shift if Titlecase
        val shiftController = getShiftController()
        shiftController.onCharacterCommitted()
        getKeyboardView()?.setShiftState(shiftController.currentMode.stateValue)

        resetComposingState()
        getKeyboardView()?.updateCandidates(emptyList())

        updateAutoCaps(false)
    }

    fun abortComposing(ic: InputConnection?) {
        resetComposingState()
        ic?.setComposingText("", 0)
        ic?.finishComposingText()
        getKeyboardView()?.updateCandidates(emptyList())
        updateAutoCaps(true)
    }

    fun handleDeleteRepeat(isWordDelete: Boolean, hasActiveCorrection: Boolean) {
        val ic = getInputConnection() ?: return
        setIsBackspaceAction(true)
        setLastSpaceTapTime(0L)
        if (hasActiveCorrection) {
            clearWordCorrection()
        }
        if (getHasActiveComposing() || currentComposingDigits.isNotEmpty()) {
            if (isWordDelete) {
                abortComposing(ic)
            } else {
                popComposingStroke(ic)
            }
        } else {
            if (isWordDelete) {
                getDeleteController().deletePrecedingWord(ic)
            } else {
                getDeleteController().deleteSingleOrSurrogate(ic)
            }
            updateAutoCaps(true)
        }
    }

    fun popComposingStroke(ic: InputConnection) {
        if (currentComposingDigits.isNotEmpty()) {
            currentComposingDigits.removeAt(currentComposingDigits.size - 1)
            if (currentComposingStrokes.isNotEmpty()) {
                currentComposingStrokes.removeAt(currentComposingStrokes.size - 1)
            }
            NativeEngineBridge.popStroke()
            if (currentComposingDigits.isEmpty()) {
                abortComposing(ic)
            } else {
                updateCandidatesFromNative(ic, null)
            }
        } else {
            abortComposing(ic)
            getDeleteController().deleteSingleOrSurrogate(ic)
            updateAutoCaps(true)
        }
    }

    fun rehydrateComposingDigits(digits: List<Int>, restoredWord: String?, ic: InputConnection) {
        incrementIgnoreSelectionUpdateCount(2)
        suggestionGuard.reset()
        NativeEngineBridge.resetT9()
        currentComposingDigits.clear()
        currentComposingStrokes.clear()
        for (d in digits) {
            currentComposingDigits.add(d)
            currentComposingStrokes.add(ComposingStroke(d, 0f, 0f))
            NativeEngineBridge.pushStroke(d, 0f, 0f)
        }
        setHasActiveComposing(currentComposingDigits.isNotEmpty())
        setComposingAnchorPosition(getLastSelectionStart())
        updateCandidatesFromNative(ic, restoredWord)
    }

    fun resetComposingState() {
        getMultiTapController().reset()
        NativeEngineBridge.resetT9()
        currentComposingDigits.clear()
        currentComposingStrokes.clear()
        setHasActiveComposing(false)
        suggestionGuard.reset()
        setActiveCandidates(emptyList())
        getWordCorrectionController().clear()
        setComposingAnchorPosition(-1)
        getKeyboardView()?.let { it.isWordCorrectionActive = false }
    }

    fun finalizeComposingInternal(ic: InputConnection?, isCursorMove: Boolean) {
        val candidates = getActiveCandidates()
        val word = if (candidates.isNotEmpty()) {
            applyShiftFormatting(candidates[0])
        } else if (currentComposingDigits.isNotEmpty()) {
            applyShiftFormatting(CandidateResolver.projectFallbackWord(currentComposingDigits, suggestionGuard))
        } else {
            ""
        }

        ic?.finishComposingText()
        getDeleteController().clearCommitHistory()

        getLearnedWordsRepository().recordWordUsage(word, isIncognito())

        val shiftController = getShiftController()
        shiftController.onCharacterCommitted()
        getKeyboardView()?.setShiftState(shiftController.currentMode.stateValue)

        if (isCursorMove) {
            resetComposingState()
            getKeyboardView()?.updateCandidates(emptyList())
            updateAutoCaps(false)
        }
    }
}
