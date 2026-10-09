package com.opent9.keyboard

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.settings.SettingsObserver
import com.opent9.keyboard.ui.*

/**
 * Context holder passing necessary state and callbacks to KeyboardActionDispatcher.
 */
class KeyboardActionContext(
    val ic: InputConnection?,
    val isT9Mode: Boolean,
    val isPasswordMode: Boolean,
    val hasActiveComposing: Boolean,
    val currentComposingDigits: ArrayList<Int>,
    val currentComposingStrokes: ArrayList<ComposingStroke>,
    val composingAnchorPosition: Int,
    val lastSelectionStart: Int,
    val hasActiveWordCorrection: Boolean,
    val shiftController: ShiftController,
    val enterKeyHandler: EnterKeyHandler,
    val deleteController: DeleteController,
    val multiTapController: MultiTapController,
    val settingsObserver: SettingsObserver,
    val currentEditorInfo: EditorInfo?,
    val lastSpaceTapTime: Long,
    val setBackspaceAction: (Boolean) -> Unit,
    val setLastSpaceTapTime: (Long) -> Unit,
    val setComposingAnchorPosition: (Int) -> Unit,
    val setHasActiveComposing: (Boolean) -> Unit,
    val clearWordCorrection: () -> Unit,
    val commitMultiTapActive: () -> Unit,
    val flushMultiTapWord: () -> Unit,
    val handleMultiTap: (Int, InputConnection) -> Unit,
    val commitActiveCandidate: (Boolean) -> Unit,
    val updateCandidatesFromNative: (InputConnection) -> Unit,
    val updateAutoCaps: (Boolean) -> Unit,
    val popComposingStroke: (InputConnection) -> Unit,
    val resetComposingState: () -> Unit,
    val rehydrateComposingDigits: (List<Int>, String?, InputConnection) -> Unit,
    val setShiftState: (Int) -> Unit,
    val switchToPage: (KeyboardPage) -> Unit,
    val cycleLanguage: (InputConnection?) -> Unit,
    val emptyCandidates: () -> Unit,
    val toggleT9Mode: () -> Unit,
    val launchSettings: () -> Unit,
    val pageController: PageController,
    val onInvalidateKeyboard: () -> Unit,
    val onAbortComposing: (InputConnection) -> Unit
)

/**
 * Dispatches key actions: tap, flick, long-press, direct/dual symbols, and page resolution.
 */
object KeyboardActionDispatcher {

    fun resolvePageSwitch(label: String): KeyboardPage {
        return when (label) {
            "?123" -> KeyboardPage.PAGE_1_NUM_SYM
            "=\\<" -> KeyboardPage.PAGE_2_EXT_SYM
            "!?#" -> KeyboardPage.PAGE_2_EXT_SYM
            "123" -> KeyboardPage.PAGE_1_NUM_SYM
            "ABC" -> KeyboardPage.PAGE_0_TEXT
            else -> KeyboardPage.PAGE_0_TEXT
        }
    }

    fun handleDirectSymbolTap(
        key: KeyInfo,
        ic: InputConnection?,
        hasActiveComposing: Boolean,
        onCommitActiveCandidate: (Boolean) -> Unit,
        onCommitMultiTapActive: () -> Unit,
        onFlushMultiTapWord: () -> Unit,
        onUpdateAutoCaps: () -> Unit
    ) {
        if (hasActiveComposing && ic != null) {
            onCommitActiveCandidate(false)
        }
        onCommitMultiTapActive()
        onFlushMultiTapWord()
        ic?.commitText(key.primaryLabel, 1)
        onUpdateAutoCaps()
    }

    fun handleDualSymbolTap(
        key: KeyInfo,
        ic: InputConnection?,
        hasActiveComposing: Boolean,
        onCommitActiveCandidate: (Boolean) -> Unit,
        onCommitMultiTapActive: () -> Unit,
        onFlushMultiTapWord: () -> Unit,
        onUpdateAutoCaps: () -> Unit
    ) {
        if (hasActiveComposing && ic != null) {
            onCommitActiveCandidate(false)
        }
        onCommitMultiTapActive()
        onFlushMultiTapWord()
        if (key.leftGlyph.isNotEmpty()) {
            ic?.commitText(key.leftGlyph, 1)
            onUpdateAutoCaps()
        }
    }

    fun handleDigitLongPress(
        key: KeyInfo,
        ic: InputConnection?,
        hasActiveComposing: Boolean,
        onCommitActiveCandidate: (Boolean) -> Unit,
        onResetComposing: () -> Unit,
        onUpdateAutoCaps: () -> Unit
    ) {
        if (key.digitValue >= 0 && ic != null) {
            if (hasActiveComposing) {
                onCommitActiveCandidate(false)
            }
            ic.commitText(key.digitValue.toString(), 1)
            onResetComposing()
            onUpdateAutoCaps()
        }
    }

    fun dispatchKeyTap(key: KeyInfo, touchX: Float, touchY: Float, ctx: KeyboardActionContext) {
        val ic = ctx.ic

        if (key.type != KeyType.DEL) {
            ctx.setBackspaceAction(false)
            if (ctx.hasActiveWordCorrection) {
                ctx.clearWordCorrection()
            }
        }

        when (key.type) {
            KeyType.DIGIT_T9 -> {
                if (ic == null) return
                ctx.setLastSpaceTapTime(0L)
                if (ctx.isT9Mode && !ctx.isPasswordMode) {
                    ctx.commitMultiTapActive()
                    ctx.flushMultiTapWord()
                    if (ctx.currentComposingDigits.isEmpty() && ctx.composingAnchorPosition < 0) {
                        ctx.setComposingAnchorPosition(ctx.lastSelectionStart)
                    }
                    ctx.currentComposingDigits.add(key.digitValue)
                    ctx.currentComposingStrokes.add(ComposingStroke(key.digitValue, touchX, touchY))
                    NativeEngineBridge.pushStroke(key.digitValue, touchX, touchY)
                    ctx.setHasActiveComposing(true)
                    ctx.updateCandidatesFromNative(ic)
                } else {
                    ctx.handleMultiTap(key.digitValue, ic)
                }
            }

            KeyType.PUNCT_1 -> {
                if (ic == null) return
                if (ctx.isT9Mode && ctx.hasActiveComposing) {
                    ctx.commitActiveCandidate(false)
                }
                ctx.setLastSpaceTapTime(0L)
                ctx.handleMultiTap(1, ic)
            }

            KeyType.SPACE_0 -> {
                if (ic == null) return
                val now = System.currentTimeMillis()
                if (ctx.isT9Mode && ctx.hasActiveComposing) {
                    ctx.commitActiveCandidate(true)
                    ctx.setLastSpaceTapTime(now)
                } else {
                    ctx.commitMultiTapActive()
                    ctx.flushMultiTapWord()
                    val newLastSpace = DoubleSpacePeriodHelper.handleSpaceTap(
                        ic = ic,
                        now = now,
                        lastSpaceTapTime = ctx.lastSpaceTapTime,
                        isDoubleSpaceEnabled = ctx.settingsObserver.isDoubleSpacePeriodEnabled(),
                        isPasswordMode = ctx.isPasswordMode,
                        onUpdateAutoCaps = { ctx.updateAutoCaps(false) }
                    )
                    ctx.setLastSpaceTapTime(newLastSpace)
                }
            }

            KeyType.DEL -> {
                if (ic == null) return
                ctx.setBackspaceAction(true)
                ctx.setLastSpaceTapTime(0L)
                if (ctx.hasActiveWordCorrection) {
                    ctx.clearWordCorrection()
                    ctx.deleteController.deleteSingleOrSurrogate(ic)
                    ctx.updateAutoCaps(true)
                    return
                }
                if (ctx.multiTapController.handleBackspaceDuringMultiTap()) {
                    ic.setComposingText("", 0)
                    ic.finishComposingText()
                    ctx.updateAutoCaps(true)
                    return
                }
                if (!ctx.isT9Mode) {
                    ctx.multiTapController.deleteBufferCharAtEnd()
                }
                val wasUncommitted = ctx.deleteController.handleDeleteTap(
                    ic = ic,
                    hasActiveComposing = ctx.hasActiveComposing,
                    onPopComposingStroke = { ctx.popComposingStroke(ic) },
                    onRehydrateDigits = {},
                    onResetComposing = {
                        ctx.resetComposingState()
                        ctx.emptyCandidates()
                    },
                    onRehydrateWord = { digits, wordToRestore ->
                        ctx.rehydrateComposingDigits(digits, wordToRestore, ic)
                    }
                )
                if (!wasUncommitted && !ctx.hasActiveComposing && ctx.currentComposingDigits.isEmpty()) {
                    ctx.updateAutoCaps(true)
                }
            }

            KeyType.SHIFT -> {
                val nextMode = ctx.shiftController.onShiftTap(System.currentTimeMillis())
                ctx.setShiftState(nextMode.stateValue)
                if (ctx.hasActiveComposing && ic != null) {
                    ctx.updateCandidatesFromNative(ic)
                }
            }

            KeyType.ENTER -> {
                if (ic == null) return
                ctx.setLastSpaceTapTime(0L)
                ctx.commitMultiTapActive()
                ctx.flushMultiTapWord()
                ctx.enterKeyHandler.handleEnterTap(
                    ic = ic,
                    editorInfo = ctx.currentEditorInfo,
                    hasActiveComposing = ctx.hasActiveComposing,
                    onCommitActiveCandidate = { ctx.commitActiveCandidate(false) }
                )
                ctx.updateAutoCaps(false)
            }

            KeyType.PAGE_SWITCH -> {
                ctx.setLastSpaceTapTime(0L)
                val targetPage = resolvePageSwitch(key.primaryLabel)
                ctx.switchToPage(targetPage)
            }

            KeyType.LANG_SWITCH -> {
                ctx.setLastSpaceTapTime(0L)
                ctx.commitMultiTapActive()
                ctx.flushMultiTapWord()
                ctx.cycleLanguage(ic)
            }

            KeyType.EMOJI_DOT -> {
                ctx.setLastSpaceTapTime(0L)
                ctx.switchToPage(KeyboardPage.PAGE_3_EMOJI)
            }

            KeyType.DIRECT_SYM -> {
                ctx.setLastSpaceTapTime(0L)
                handleDirectSymbolTap(
                    key = key,
                    ic = ic,
                    hasActiveComposing = ctx.hasActiveComposing,
                    onCommitActiveCandidate = { ctx.commitActiveCandidate(it) },
                    onCommitMultiTapActive = { ctx.commitMultiTapActive() },
                    onFlushMultiTapWord = { ctx.flushMultiTapWord() },
                    onUpdateAutoCaps = { ctx.updateAutoCaps(false) }
                )
            }

            KeyType.DUAL_SYM -> {
                ctx.setLastSpaceTapTime(0L)
                handleDualSymbolTap(
                    key = key,
                    ic = ic,
                    hasActiveComposing = ctx.hasActiveComposing,
                    onCommitActiveCandidate = { ctx.commitActiveCandidate(it) },
                    onCommitMultiTapActive = { ctx.commitMultiTapActive() },
                    onFlushMultiTapWord = { ctx.flushMultiTapWord() },
                    onUpdateAutoCaps = { ctx.updateAutoCaps(false) }
                )
            }
        }
    }

    fun dispatchKeyFlick(key: KeyInfo, direction: FlickDirection, ctx: KeyboardActionContext) {
        val ic = ctx.ic ?: return
        ctx.setLastSpaceTapTime(0L)

        if (key.type == KeyType.SHIFT && direction == FlickDirection.UP) {
            val nextMode = ctx.shiftController.onShiftFlickUp()
            ctx.setShiftState(nextMode.stateValue)
            if (ctx.hasActiveComposing) {
                ctx.updateCandidatesFromNative(ic)
            }
            return
        }

        if (key.type != KeyType.DEL && key.type != KeyType.SHIFT) {
            if (ctx.hasActiveComposing) {
                ctx.commitActiveCandidate(false)
            }
        }

        ctx.pageController.handleKeyFlick(
            key = key,
            direction = direction,
            ic = ic,
            onSwitchLanguage = { ctx.cycleLanguage(ic) },
            onClearField = {
                ctx.setBackspaceAction(true)
                ctx.clearWordCorrection()
                if (ctx.hasActiveComposing || ctx.currentComposingDigits.isNotEmpty()) {
                    ctx.onAbortComposing(ic)
                }
                ctx.deleteController.clearEntireField(ic)
            },
            onDeletePrecedingWord = {
                ctx.setBackspaceAction(true)
                ctx.clearWordCorrection()
                if (ctx.hasActiveComposing || ctx.currentComposingDigits.isNotEmpty()) {
                    ctx.onAbortComposing(ic)
                } else {
                    ctx.deleteController.deletePrecedingWord(ic)
                }
            },
            onForceSubmit = {
                ctx.enterKeyHandler.handleForceSubmit(ic, ctx.currentEditorInfo)
            },
            onOpenSettings = { ctx.launchSettings() },
            onSwitchPage = { targetPage -> ctx.switchToPage(targetPage) }
        )

        ctx.onInvalidateKeyboard()
    }

    fun dispatchKeyLongPress(key: KeyInfo, ctx: KeyboardActionContext) {
        val ic = ctx.ic
        when (key.type) {
            KeyType.LANG_SWITCH -> {
                ctx.toggleT9Mode()
            }
            KeyType.PAGE_SWITCH -> {
                ctx.launchSettings()
            }
            KeyType.DEL -> {
                // Continuous accelerated delete handled via onKeyDeleteRepeatAction
            }
            KeyType.SHIFT -> {
                val nextMode = ctx.shiftController.onShiftLongPress()
                ctx.setShiftState(nextMode.stateValue)
                if (ctx.hasActiveComposing && ic != null) {
                    ctx.updateCandidatesFromNative(ic)
                }
            }
            KeyType.DIGIT_T9, KeyType.PUNCT_1, KeyType.SPACE_0 -> {
                handleDigitLongPress(
                    key = key,
                    ic = ic,
                    hasActiveComposing = ctx.hasActiveComposing,
                    onCommitActiveCandidate = { ctx.commitActiveCandidate(it) },
                    onResetComposing = {
                        ctx.resetComposingState()
                        ctx.emptyCandidates()
                    },
                    onUpdateAutoCaps = { ctx.updateAutoCaps(false) }
                )
            }
            KeyType.ENTER -> {
                if (ic != null) {
                    if (ctx.hasActiveComposing) {
                        ctx.commitActiveCandidate(false)
                    }
                    ic.commitText("\n", 1)
                    ctx.updateAutoCaps(false)
                }
            }
            else -> {}
        }
    }
}
