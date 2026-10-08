package com.opent9.keyboard

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import android.app.AlertDialog
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.lexicon.LexiconLoader
import com.opent9.keyboard.prediction.*
import com.opent9.keyboard.settings.SettingsActivity
import com.opent9.keyboard.settings.SettingsObserver
import com.opent9.keyboard.ui.*
import java.io.File

class OpenT9InputMethodService : InputMethodService() {

    private lateinit var keyboardView: T9KeyboardView
    private lateinit var settingsObserver: SettingsObserver

    private val shiftController = ShiftController()
    private val enterKeyHandler = EnterKeyHandler()
    private val deleteController = DeleteController()
    private lateinit var pageController: PageController
    private lateinit var learnedWordsRepository: LearnedWordsRepository
    private lateinit var autoCapsController: AutoCapsController
    private lateinit var multiTapController: MultiTapController
    private lateinit var wordCorrectionController: WordCorrectionController

    private val isMultiTapActive: Boolean
        get() = if (::multiTapController.isInitialized) multiTapController.isMultiTapActive else false

    private val multiTapWordBuffer: StringBuilder
        get() = if (::multiTapController.isInitialized) multiTapController.multiTapWordBuffer else StringBuilder()

    private val activeWordCorrectionContext: WordCorrectionContext?
        get() = if (::wordCorrectionController.isInitialized) wordCorrectionController.activeContext else null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentEditorInfo: EditorInfo? = null

    // Composing state tracking
    data class ComposingStroke(
        val digit: Int,
        val touchX: Float,
        val touchY: Float
    )
    private val currentComposingDigits = ArrayList<Int>(32)
    private val currentComposingStrokes = ArrayList<ComposingStroke>(32)
    private var hasActiveComposing = false
    private var isIncognito = false
    private var isPasswordMode = false
    private var ignoreSelectionUpdateCount = 0
    private val suggestionGuard = SuggestionGuard()
    private var activeCandidates: List<String> = emptyList()
    private var isBackspaceAction = false
    private var lastSpaceTapTime = 0L
    private var lastSelectionStart = 0
    private var composingAnchorPosition = -1

    override fun onCreate() {
        super.onCreate()
        settingsObserver = SettingsObserver(this)
        settingsObserver.start()

        learnedWordsRepository = LearnedWordsRepository(this)
        autoCapsController = AutoCapsController(shiftController, settingsObserver)
        multiTapController = MultiTapController(
            settingsObserver = settingsObserver,
            shiftController = shiftController,
            learnedWordsRepository = learnedWordsRepository,
            mainHandler = mainHandler,
            onShiftUpdate = {
                if (::keyboardView.isInitialized) {
                    keyboardView.setShiftState(shiftController.currentMode.stateValue)
                }
            },
            onAutoCapsUpdate = {
                updateAutoCaps()
            },
            getInputConnection = {
                currentInputConnection
            }
        )
        wordCorrectionController = WordCorrectionController(
            suggestionGuard = suggestionGuard,
            learnedWordsRepository = learnedWordsRepository,
            getProcessedCandidates = { getProcessedCandidates() }
        )

        val dbFile = File(filesDir, "opent9_vocab.dat")
        dbFile.parentFile?.mkdirs()
        NativeEngineBridge.initEngine(dbFile.absolutePath)

        // Load static binary DAWGs directly into Linux page cache via mmap
        val lexiconLoader = LexiconLoader(assets, filesDir)
        lexiconLoader.loadLexicons(settingsObserver.getStartupLanguage())
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        win.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    }

    override fun onCreateInputView(): View {
        window?.window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        keyboardView = T9KeyboardView(this)
        keyboardView.settingsObserver = settingsObserver
        pageController = PageController(keyboardView.keyAtlas)

        val defaultLang = settingsObserver.getStartupLanguage()
        keyboardView.setLanguage(defaultLang)
        keyboardView.setT9Mode(settingsObserver.isDefaultT9())

        settingsObserver.onLayoutConfigChanged = {
            if (::keyboardView.isInitialized) {
                keyboardView.post {
                    keyboardView.requestLayout()
                    keyboardView.reloadLayoutConfiguration()
                }
            }
        }

        settingsObserver.onThemeConfigChanged = {
            if (::keyboardView.isInitialized) {
                keyboardView.post {
                    keyboardView.updateThemeFromConfiguration(resources.configuration)
                }
            }
        }

        settingsObserver.onInputModeConfigChanged = { isT9 ->
            if (::keyboardView.isInitialized && !isPasswordMode) {
                keyboardView.post {
                    if (!EditorContextHelper.isNumeric(currentEditorInfo)) {
                        keyboardView.setT9Mode(isT9)
                    }
                }
            }
        }

        settingsObserver.onLanguageConfigChanged = { newLang ->
            NativeEngineBridge.switchLanguage(newLang)
            if (::keyboardView.isInitialized) {
                keyboardView.post {
                    keyboardView.setLanguage(newLang)
                }
            }
        }

        settingsObserver.onAutoCapsConfigChanged = {
            updateAutoCaps(clearManualOverride = true)
        }

        setupKeyboardViewListeners()
        return keyboardView
    }

    private fun setupKeyboardViewListeners() {
        keyboardView.onKeyTapAction = { key, touchX, touchY ->
            handleKeyTap(key, touchX, touchY)
        }

        keyboardView.onKeyFlickAction = { key, direction ->
            handleKeyFlick(key, direction)
        }

        keyboardView.onKeyLongPressAction = { key ->
            handleKeyLongPress(key)
        }

        keyboardView.onKeyDeleteRepeatAction = { isWordDelete ->
            handleDeleteRepeat(isWordDelete)
        }

        keyboardView.onSpaceScrubAction = { steps ->
            if (settingsObserver.isSpaceScrubbingEnabled()) {
                val ic = currentInputConnection
                val keyEventCode = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                val count = Math.abs(steps)
                for (i in 0 until count) {
                    ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode))
                    ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyEventCode))
                }
            }
        }

        keyboardView.onPillTapAction = {
            toggleT9Mode()
        }

        keyboardView.onCandidateTapAction = { index ->
            if (activeWordCorrectionContext != null) {
                commitWordCorrectionCandidate(index)
            } else {
                commitCandidateIndex(index)
            }
        }

        keyboardView.onCandidateLongPressAction = { _, word ->
            promptRemoveCandidate(word)
        }

        keyboardView.onOpenSettingsAction = {
            launchSettings()
        }

        keyboardView.onEmojiSelectedAction = { emoji ->
            val ic = currentInputConnection
            if (ic != null) {
                if (hasActiveComposing) {
                    commitActiveCandidate(addSpace = false)
                }
                ic.commitText(emoji, 1)
            }
        }

        keyboardView.onEmojiControlAction = { controlIndex ->
            val ic = currentInputConnection
            when (controlIndex) {
                0 -> {
                    switchToPage(KeyboardPage.PAGE_0_TEXT)
                }
                1 -> {
                    keyboardView.emojiAtlas.activeCategoryIndex = 0
                    keyboardView.gestureTracker.resetEmojiScroll()
                    keyboardView.invalidate()
                }
                2 -> {
                    if (ic != null) {
                        if (hasActiveComposing) {
                            commitActiveCandidate(addSpace = true)
                        } else {
                            ic.commitText(" ", 1)
                        }
                    }
                }
                3 -> {
                    if (ic != null) {
                        deleteController.handleDeleteTap(
                            ic = ic,
                            hasActiveComposing = hasActiveComposing,
                            onPopComposingStroke = {
                                popComposingStroke(ic)
                            },
                            onRehydrateDigits = {},
                            onResetComposing = {
                                resetComposingState()
                                keyboardView.updateCandidates(emptyList())
                            },
                            onRehydrateWord = { digits, wordToRestore ->
                                rehydrateComposingDigits(digits, wordToRestore, ic)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (attribute != null) {
            currentEditorInfo = attribute
            if (::keyboardView.isInitialized) {
                keyboardView.setImeAction(enterKeyHandler.resolveEffectiveAction(attribute))
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::keyboardView.isInitialized) {
            keyboardView.updateThemeFromConfiguration(resources.configuration)
            keyboardView.reloadLayoutConfiguration()
        }
        currentEditorInfo = info
        ignoreSelectionUpdateCount = 0
        lastSpaceTapTime = 0L
        lastSelectionStart = info.initialSelStart.coerceAtLeast(0)
        resetComposingState()

        isPasswordMode = EditorContextHelper.isPassword(info)
        isIncognito = EditorContextHelper.isIncognito(info)
        val isNumeric = EditorContextHelper.isNumeric(info)

        if (isNumeric) {
            keyboardView.keyAtlas.updatePageLayout(KeyboardPage.PAGE_1_NUM_SYM)
            keyboardView.setT9Mode(false)
        } else if (isPasswordMode) {
            keyboardView.keyAtlas.updatePageLayout(KeyboardPage.PAGE_0_TEXT)
            keyboardView.setT9Mode(false) // Literal Multi-tap
        } else {
            keyboardView.keyAtlas.updatePageLayout(KeyboardPage.PAGE_0_TEXT)
            keyboardView.setT9Mode(settingsObserver.isDefaultT9())
        }

        val imeAction = enterKeyHandler.resolveEffectiveAction(info)
        keyboardView.setImeAction(imeAction)
        keyboardView.updateCandidates(emptyList())

        if (!shiftController.isCapsLocked()) {
            shiftController.reset()
            updateAutoCaps(clearManualOverride = true)
        }
        keyboardView.setShiftState(shiftController.currentMode.stateValue)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (settingsObserver.isAudioEnabled()) {
            NativeEngineBridge.startAudio()
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        NativeEngineBridge.stopAudio()
        if (::keyboardView.isInitialized) {
            keyboardView.cancelAllGestures()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (hasActiveComposing) {
            finalizeComposingOnFinish(currentInputConnection)
        } else if (isMultiTapActive) {
            commitMultiTapActive()
        }
        super.onFinishInputView(finishingInput)
        NativeEngineBridge.stopAudio()
        if (::keyboardView.isInitialized) {
            keyboardView.cancelAllGestures()
            keyboardView.updateCandidates(emptyList())
        }
        resetComposingState()
    }

    override fun onFinishInput() {
        if (hasActiveComposing) {
            finalizeComposingOnFinish(currentInputConnection)
        } else if (isMultiTapActive) {
            commitMultiTapActive()
        }
        super.onFinishInput()
        NativeEngineBridge.stopAudio()
        if (::keyboardView.isInitialized) {
            keyboardView.cancelAllGestures()
            keyboardView.updateCandidates(emptyList())
        }
        resetComposingState()
    }

    private fun finalizeComposingOnFinish(ic: InputConnection?) {
        finalizeComposingInternal(ic, isCursorMove = false)
    }

    private fun finalizeComposingOnCursorMove(ic: InputConnection?) {
        finalizeComposingInternal(ic, isCursorMove = true)
    }

    private fun finalizeComposingInternal(ic: InputConnection?, isCursorMove: Boolean) {
        val word = if (activeCandidates.isNotEmpty()) {
            applyShiftFormatting(activeCandidates[0])
        } else if (currentComposingDigits.isNotEmpty()) {
            applyShiftFormatting(CandidateResolver.projectFallbackWord(currentComposingDigits, suggestionGuard))
        } else {
            ""
        }

        ic?.finishComposingText()
        deleteController.clearCommitHistory()

        learnedWordsRepository.recordWordUsage(word, isIncognito)

        shiftController.onCharacterCommitted()
        if (::keyboardView.isInitialized) {
            keyboardView.setShiftState(shiftController.currentMode.stateValue)
        }

        if (isCursorMove) {
            resetComposingState()
            if (::keyboardView.isInitialized) {
                keyboardView.updateCandidates(emptyList())
            }
            updateAutoCaps()
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)

        if (ignoreSelectionUpdateCount > 0) {
            ignoreSelectionUpdateCount--
            lastSelectionStart = newSelStart
            return
        }

        val cursorMoved = oldSelStart != newSelStart || oldSelEnd != newSelEnd
        if (cursorMoved) {
            lastSpaceTapTime = 0L
        }

        if (candidatesStart >= 0) {
            composingAnchorPosition = candidatesStart
        }

        if (isBackspaceAction) {
            isBackspaceAction = false
            clearWordCorrection()
            if (!hasActiveComposing && currentComposingDigits.isEmpty() && !isMultiTapActive) {
                updateAutoCaps(clearManualOverride = cursorMoved)
            }
            lastSelectionStart = newSelStart
            return
        }

        if (hasActiveComposing || currentComposingDigits.isNotEmpty()) {
            val isUserCursorMove = if (candidatesStart >= 0 && candidatesEnd >= 0) {
                // If editor supports and reports composing span, any cursor position other than the active
                // composing insertion point (candidatesEnd) indicates the user moved the cursor (including index 0 or intra-word taps)
                newSelStart != candidatesEnd || newSelEnd != candidatesEnd
            } else {
                // If editor does not maintain composing spans (candidatesStart == -1):
                val expectedPos = if (composingAnchorPosition >= 0 && activeCandidates.isNotEmpty()) {
                    composingAnchorPosition + activeCandidates[0].length
                } else -1
                newSelStart != newSelEnd || (expectedPos >= 0 && newSelStart != expectedPos) || (newSelStart < oldSelStart)
            }

            if (isUserCursorMove) {
                finalizeComposingOnCursorMove(currentInputConnection)
            }
        } else if (::keyboardView.isInitialized && !keyboardView.isT9Mode && (isMultiTapActive || multiTapWordBuffer.isNotEmpty())) {
            val isUserCursorMove = if (candidatesStart >= 0 && candidatesEnd >= 0) {
                newSelStart != candidatesEnd || newSelEnd != candidatesEnd
            } else {
                newSelStart < oldSelStart || kotlin.math.abs(newSelStart - oldSelStart) > 1 || newSelStart != newSelEnd
            }
            if (isUserCursorMove) {
                commitMultiTapActive()
                flushMultiTapWord()
            }
        }

        if (!hasActiveComposing && currentComposingDigits.isEmpty() && !isMultiTapActive) {
            updateAutoCaps(clearManualOverride = cursorMoved)

            if (!isPasswordMode) {
                checkForWordCorrectionSuggestions(newSelStart, newSelEnd)
            } else {
                clearWordCorrection()
            }
        }

        lastSelectionStart = newSelStart
    }

    private fun switchToPage(targetPage: KeyboardPage) {
        val ic = currentInputConnection
        if (hasActiveComposing && ic != null) {
            commitActiveCandidate(addSpace = false)
        }
        commitMultiTapActive()
        flushMultiTapWord()
        when (targetPage) {
            KeyboardPage.PAGE_1_NUM_SYM -> {
                keyboardView.setT9Mode(false)
                keyboardView.gestureTracker.resetPage1Scroll()
                keyboardView.updateCandidates(emptyList())
            }
            KeyboardPage.PAGE_2_EXT_SYM -> {
                keyboardView.setT9Mode(false)
                keyboardView.updateCandidates(emptyList())
            }
            KeyboardPage.PAGE_3_EMOJI -> {
                keyboardView.setT9Mode(false)
                keyboardView.gestureTracker.resetEmojiScroll()
                keyboardView.updateCandidates(emptyList())
            }
            KeyboardPage.PAGE_0_TEXT -> {
                keyboardView.setT9Mode(settingsObserver.isDefaultT9())
                keyboardView.updateCandidates(emptyList())
            }
        }
        keyboardView.keyAtlas.updatePageLayout(targetPage)
        keyboardView.invalidate()
    }

    private fun handleKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {
        val ic = currentInputConnection

        if (key.type != KeyType.DEL) {
            isBackspaceAction = false
            if (activeWordCorrectionContext != null) {
                clearWordCorrection()
            }
        }

        when (key.type) {
            KeyType.DIGIT_T9 -> {
                if (ic == null) return
                lastSpaceTapTime = 0L
                if (keyboardView.isT9Mode && !isPasswordMode) {
                    commitMultiTapActive()
                    flushMultiTapWord()
                    if (currentComposingDigits.isEmpty() && composingAnchorPosition < 0) {
                        composingAnchorPosition = lastSelectionStart
                    }
                    // Push stroke to C++ DAWG beam search
                    currentComposingDigits.add(key.digitValue)
                    currentComposingStrokes.add(ComposingStroke(key.digitValue, touchX, touchY))
                    NativeEngineBridge.pushStroke(key.digitValue, touchX, touchY)
                    hasActiveComposing = true
                    updateCandidatesFromNative(ic)
                } else {
                    // Multi-Tap ABC mode
                    handleMultiTap(key.digitValue, ic)
                }
            }

            KeyType.PUNCT_1 -> {
                if (ic == null) return
                if (keyboardView.isT9Mode && hasActiveComposing) {
                    commitActiveCandidate(addSpace = false)
                }
                lastSpaceTapTime = 0L
                handleMultiTap(1, ic)
            }

            KeyType.SPACE_0 -> {
                if (ic == null) return
                val now = System.currentTimeMillis()
                if (keyboardView.isT9Mode && hasActiveComposing) {
                    commitActiveCandidate(addSpace = true)
                    lastSpaceTapTime = now
                } else {
                    commitMultiTapActive()
                    flushMultiTapWord()

                    lastSpaceTapTime = DoubleSpacePeriodHelper.handleSpaceTap(
                        ic = ic,
                        now = now,
                        lastSpaceTapTime = lastSpaceTapTime,
                        isDoubleSpaceEnabled = settingsObserver.isDoubleSpacePeriodEnabled(),
                        isPasswordMode = isPasswordMode,
                        onUpdateAutoCaps = { updateAutoCaps() }
                    )
                }
            }

            KeyType.DEL -> {
                if (ic == null) return
                isBackspaceAction = true
                lastSpaceTapTime = 0L
                if (activeWordCorrectionContext != null) {
                    clearWordCorrection()
                    deleteController.deleteSingleOrSurrogate(ic)
                    updateAutoCaps(clearManualOverride = true)
                    return
                }
                if (multiTapController.handleBackspaceDuringMultiTap()) {
                    ic.setComposingText("", 0)
                    ic.finishComposingText()
                    updateAutoCaps(clearManualOverride = true)
                    return
                }
                if (!keyboardView.isT9Mode) {
                    multiTapController.deleteBufferCharAtEnd()
                }
                val wasUncommitted = deleteController.handleDeleteTap(
                    ic = ic,
                    hasActiveComposing = hasActiveComposing,
                    onPopComposingStroke = {
                        popComposingStroke(ic)
                    },
                    onRehydrateDigits = {},
                    onResetComposing = {
                        resetComposingState()
                        keyboardView.updateCandidates(emptyList())
                    },
                    onRehydrateWord = { digits, wordToRestore ->
                        rehydrateComposingDigits(digits, wordToRestore, ic)
                    }
                )
                if (!wasUncommitted && !hasActiveComposing && currentComposingDigits.isEmpty()) {
                    updateAutoCaps(clearManualOverride = true)
                }
            }

            KeyType.SHIFT -> {
                val nextMode = shiftController.onShiftTap(System.currentTimeMillis())
                keyboardView.setShiftState(nextMode.stateValue)
                if (hasActiveComposing && ic != null) {
                    updateCandidatesFromNative(ic)
                }
            }

            KeyType.ENTER -> {
                if (ic == null) return
                lastSpaceTapTime = 0L
                commitMultiTapActive()
                flushMultiTapWord()
                enterKeyHandler.handleEnterTap(
                    ic = ic,
                    editorInfo = currentEditorInfo,
                    hasActiveComposing = hasActiveComposing,
                    onCommitActiveCandidate = {
                        commitActiveCandidate(addSpace = false)
                    }
                )
                updateAutoCaps()
            }

            KeyType.PAGE_SWITCH -> {
                lastSpaceTapTime = 0L
                val targetPage = when (key.primaryLabel) {
                    "?123" -> KeyboardPage.PAGE_1_NUM_SYM
                    "=\\<" -> KeyboardPage.PAGE_2_EXT_SYM
                    "!?#" -> KeyboardPage.PAGE_2_EXT_SYM
                    "123" -> KeyboardPage.PAGE_1_NUM_SYM
                    "ABC" -> KeyboardPage.PAGE_0_TEXT
                    else -> KeyboardPage.PAGE_0_TEXT
                }
                switchToPage(targetPage)
            }

            KeyType.LANG_SWITCH -> {
                lastSpaceTapTime = 0L
                commitMultiTapActive()
                flushMultiTapWord()
                cycleLanguage(ic)
            }

            KeyType.EMOJI_DOT -> {
                lastSpaceTapTime = 0L
                switchToPage(KeyboardPage.PAGE_3_EMOJI)
            }

            KeyType.DIRECT_SYM -> {
                if (hasActiveComposing && ic != null) {
                    commitActiveCandidate(addSpace = false)
                }
                commitMultiTapActive()
                flushMultiTapWord()
                lastSpaceTapTime = 0L
                ic?.commitText(key.primaryLabel, 1)
                updateAutoCaps()
            }

            KeyType.DUAL_SYM -> {
                if (hasActiveComposing && ic != null) {
                    commitActiveCandidate(addSpace = false)
                }
                commitMultiTapActive()
                flushMultiTapWord()
                lastSpaceTapTime = 0L
                // Tap on dual symbol defaults to left glyph
                if (key.leftGlyph.isNotEmpty()) {
                    ic?.commitText(key.leftGlyph, 1)
                    updateAutoCaps()
                }
            }
        }
    }

    private fun cycleLanguage(ic: InputConnection?) {
        val currentLang = NativeEngineBridge.getActiveLanguage()
        val nextLang = if (currentLang == "EN") "ID" else "EN"
        NativeEngineBridge.switchLanguage(nextLang)
        keyboardView.setLanguage(nextLang)

        // If currently composing, re-evaluate with swapped lexicon pointer
        if (hasActiveComposing && ic != null) {
            val savedStrokes = ArrayList(currentComposingStrokes)
            suggestionGuard.reset()
            NativeEngineBridge.resetT9()
            currentComposingDigits.clear()
            currentComposingStrokes.clear()
            for (s in savedStrokes) {
                currentComposingDigits.add(s.digit)
                currentComposingStrokes.add(s)
                NativeEngineBridge.pushStroke(s.digit, s.touchX, s.touchY)
            }
            updateCandidatesFromNative(ic)
        }
    }

    private fun handleKeyFlick(key: KeyInfo, direction: FlickDirection) {
        val ic = currentInputConnection ?: return
        lastSpaceTapTime = 0L

        if (key.type == KeyType.SHIFT && direction == FlickDirection.UP) {
            val nextMode = shiftController.onShiftFlickUp()
            keyboardView.setShiftState(nextMode.stateValue)
            if (hasActiveComposing) {
                updateCandidatesFromNative(ic)
            }
            return
        }

        if (key.type != KeyType.DEL && key.type != KeyType.SHIFT) {
            if (hasActiveComposing) {
                commitActiveCandidate(addSpace = false)
            }
        }

        val previousPage = keyboardView.keyAtlas.currentPage
        pageController.handleKeyFlick(
            key = key,
            direction = direction,
            ic = ic,
            onSwitchLanguage = {
                cycleLanguage(ic)
            },
            onClearField = {
                isBackspaceAction = true
                clearWordCorrection()
                if (hasActiveComposing || currentComposingDigits.isNotEmpty()) {
                    abortComposing(ic)
                }
                deleteController.clearEntireField(ic)
            },
            onDeletePrecedingWord = {
                isBackspaceAction = true
                clearWordCorrection()
                if (hasActiveComposing || currentComposingDigits.isNotEmpty()) {
                    abortComposing(ic)
                } else {
                    deleteController.deletePrecedingWord(ic)
                }
            },
            onForceSubmit = {
                enterKeyHandler.handleForceSubmit(ic, currentEditorInfo)
            },
            onOpenSettings = {
                launchSettings()
            },
            onSwitchPage = { targetPage ->
                switchToPage(targetPage)
            }
        )

        if (keyboardView.keyAtlas.currentPage != previousPage) {
            keyboardView.invalidate()
        }
    }

    private fun handleKeyLongPress(key: KeyInfo) {
        val ic = currentInputConnection
        when (key.type) {
            KeyType.LANG_SWITCH -> {
                // Tap and hold > T9 off / on
                toggleT9Mode()
            }
            KeyType.PAGE_SWITCH -> {
                launchSettings()
            }
            KeyType.DEL -> {
                // Continuous accelerated delete is handled via onKeyDeleteRepeatAction
            }
            KeyType.SHIFT -> {
                val nextMode = shiftController.onShiftLongPress()
                keyboardView.setShiftState(nextMode.stateValue)
                if (hasActiveComposing && ic != null) {
                    updateCandidatesFromNative(ic)
                }
            }
            KeyType.DIGIT_T9, KeyType.PUNCT_1, KeyType.SPACE_0 -> {
                if (key.digitValue >= 0 && ic != null) {
                    if (hasActiveComposing) {
                        commitActiveCandidate(addSpace = false)
                    }
                    ic.commitText(key.digitValue.toString(), 1)
                    resetComposingState()
                    keyboardView.updateCandidates(emptyList())
                    updateAutoCaps()
                }
            }
            KeyType.ENTER -> {
                if (ic != null) {
                    if (hasActiveComposing) {
                        commitActiveCandidate(addSpace = false)
                    }
                    ic.commitText("\n", 1)
                    updateAutoCaps()
                }
            }
            else -> {}
        }
    }

    private fun handleMultiTap(digit: Int, ic: InputConnection) {
        multiTapController.handleMultiTap(digit, ic)
    }

    private fun commitMultiTapActive() {
        if (::multiTapController.isInitialized) {
            multiTapController.commitMultiTapActive(currentInputConnection, isIncognito)
        }
    }

    private fun flushMultiTapWord() {
        if (::multiTapController.isInitialized) {
            multiTapController.flushMultiTapWord(isIncognito)
        }
    }

    private fun getProcessedCandidates(): List<String> {
        return CandidateResolver.getProcessedCandidates(currentComposingDigits)
    }

    private fun updateCandidatesFromNative(ic: InputConnection) {
        updateCandidatesFromNative(ic, null)
    }

    private fun updateCandidatesFromNative(ic: InputConnection, preferredWord: String?) {
        activeCandidates = CandidateResolver.resolveCandidates(
            currentComposingDigits = currentComposingDigits,
            suggestionGuard = suggestionGuard,
            learnedWordsRepository = learnedWordsRepository,
            preferredWord = preferredWord
        )

        keyboardView.updateCandidates(activeCandidates)
        if (activeCandidates.isNotEmpty()) {
            val topCandidate = applyShiftFormatting(activeCandidates[0])
            ic.setComposingText(topCandidate, 1)
        } else {
            ic.setComposingText("", 0)
        }
    }

    private fun applyShiftFormatting(word: String): String {
        return ShiftFormatter.format(word, shiftController.currentMode)
    }

    private fun commitActiveCandidate(addSpace: Boolean) {
        val word = if (activeCandidates.isNotEmpty()) {
            applyShiftFormatting(activeCandidates[0])
        } else if (currentComposingDigits.isNotEmpty()) {
            applyShiftFormatting(CandidateResolver.projectFallbackWord(currentComposingDigits, suggestionGuard))
        } else {
            ""
        }
        if (word.isNotEmpty()) {
            commitWord(word, addSpace)
        } else if (addSpace) {
            currentInputConnection?.commitText(" ", 1)
        }
    }

    private fun commitCandidateIndex(index: Int) {
        if (index in activeCandidates.indices) {
            val word = applyShiftFormatting(activeCandidates[index])
            val addSpace = settingsObserver.isAutoSpaceEnabled()
            commitWord(word, addSpace = addSpace)
        }
    }

    private fun commitWord(word: String, addSpace: Boolean) {
        val ic = currentInputConnection ?: return
        val digitsSnapshot = ArrayList(currentComposingDigits)

        // commitText atomically replaces any active composing text and finishes composing
        val textToCommit = if (addSpace) "$word " else word
        ic.commitText(textToCommit, 1)

        lastSpaceTapTime = if (addSpace) System.currentTimeMillis() else 0L

        // Record commit for retroactive un-commit
        deleteController.recordCommit(word, digitsSnapshot)

        // Record usage for dynamic dictionary learning if not incognito and not pure numeric
        val digitsKey = if (digitsSnapshot.isNotEmpty()) {
            digitsSnapshot.joinToString("")
        } else {
            WordCorrectionHelper.wordToDigits(word).joinToString("")
        }
        val lang = NativeEngineBridge.getActiveLanguage()
        learnedWordsRepository.recordPickedWord(lang, digitsKey, word, isIncognito)

        // Auto-reset Shift if Titlecase
        shiftController.onCharacterCommitted()
        keyboardView.setShiftState(shiftController.currentMode.stateValue)

        resetComposingState()
        keyboardView.updateCandidates(emptyList())

        updateAutoCaps()
    }


    private fun abortComposing(ic: InputConnection?) {
        resetComposingState()
        ic?.setComposingText("", 0)
        ic?.finishComposingText()
        if (::keyboardView.isInitialized) {
            keyboardView.updateCandidates(emptyList())
        }
        updateAutoCaps(clearManualOverride = true)
    }

    private fun handleDeleteRepeat(isWordDelete: Boolean) {
        val ic = currentInputConnection ?: return
        isBackspaceAction = true
        lastSpaceTapTime = 0L
        if (activeWordCorrectionContext != null) {
            clearWordCorrection()
        }
        if (hasActiveComposing || currentComposingDigits.isNotEmpty()) {
            if (isWordDelete) {
                abortComposing(ic)
            } else {
                popComposingStroke(ic)
            }
        } else {
            if (isWordDelete) {
                deleteController.deletePrecedingWord(ic)
            } else {
                deleteController.deleteSingleOrSurrogate(ic)
            }
            updateAutoCaps(clearManualOverride = true)
        }
    }

    private fun popComposingStroke(ic: InputConnection) {
        if (currentComposingDigits.isNotEmpty()) {
            currentComposingDigits.removeAt(currentComposingDigits.size - 1)
            if (currentComposingStrokes.isNotEmpty()) {
                currentComposingStrokes.removeAt(currentComposingStrokes.size - 1)
            }
            NativeEngineBridge.popStroke()
            if (currentComposingDigits.isEmpty()) {
                abortComposing(ic)
            } else {
                updateCandidatesFromNative(ic)
            }
        } else {
            abortComposing(ic)
            deleteController.deleteSingleOrSurrogate(ic)
            updateAutoCaps(clearManualOverride = true)
        }
    }

    private fun updateAutoCaps(clearManualOverride: Boolean = false) {
        if (!::autoCapsController.isInitialized) return
        val changed = autoCapsController.updateAutoCaps(
            ic = currentInputConnection,
            info = currentEditorInfo,
            clearManualOverride = clearManualOverride
        )
        if (changed && ::keyboardView.isInitialized) {
            keyboardView.setShiftState(shiftController.currentMode.stateValue)
        }
    }

    private fun rehydrateComposingDigits(digits: List<Int>, restoredWord: String?, ic: InputConnection) {
        ignoreSelectionUpdateCount += 2
        suggestionGuard.reset()
        NativeEngineBridge.resetT9()
        currentComposingDigits.clear()
        currentComposingStrokes.clear()
        for (d in digits) {
            currentComposingDigits.add(d)
            currentComposingStrokes.add(ComposingStroke(d, 0f, 0f))
            NativeEngineBridge.pushStroke(d, 0f, 0f)
        }
        hasActiveComposing = currentComposingDigits.isNotEmpty()
        composingAnchorPosition = lastSelectionStart
        updateCandidatesFromNative(ic, preferredWord = restoredWord)
    }

    private fun toggleT9Mode() {
        val nextMode = !keyboardView.isT9Mode
        if (hasActiveComposing) {
            commitActiveCandidate(addSpace = false)
        }
        commitMultiTapActive()
        flushMultiTapWord()
        keyboardView.setT9Mode(nextMode)
    }

    private fun resetComposingState() {
        if (::multiTapController.isInitialized) {
            multiTapController.reset()
        }
        NativeEngineBridge.resetT9()
        currentComposingDigits.clear()
        currentComposingStrokes.clear()
        hasActiveComposing = false
        suggestionGuard.reset()
        activeCandidates = emptyList()
        if (::wordCorrectionController.isInitialized) {
            wordCorrectionController.clear()
        }
        composingAnchorPosition = -1
        if (::keyboardView.isInitialized) {
            keyboardView.isWordCorrectionActive = false
        }
    }

    private fun promptRemoveCandidate(word: String) {
        val token = (if (::keyboardView.isInitialized) keyboardView.windowToken else null)
            ?: this.window?.window?.attributes?.token
        CandidateDialogHelper.showRemoveCandidateDialog(this, token, word) { targetWord ->
            removeCandidateSuggestion(targetWord)
        }
    }

    fun removeCandidateSuggestion(word: String) {
        val canonical = learnedWordsRepository.removeWord(word)
        // Immediately filter out from activeCandidates
        activeCandidates = activeCandidates.filterNot {
            EnglishOrthography.toCanonical(it).equals(canonical, ignoreCase = true)
        }
        if (hasActiveComposing && currentComposingDigits.isNotEmpty()) {
            if (::keyboardView.isInitialized) {
                keyboardView.updateCandidates(activeCandidates)
            }
            currentInputConnection?.let { ic ->
                val savedStrokes = ArrayList(currentComposingStrokes)
                suggestionGuard.reset()
                NativeEngineBridge.resetT9()
                currentComposingDigits.clear()
                currentComposingStrokes.clear()
                for (s in savedStrokes) {
                    currentComposingDigits.add(s.digit)
                    currentComposingStrokes.add(s)
                    NativeEngineBridge.pushStroke(s.digit, s.touchX, s.touchY)
                }
                updateCandidatesFromNative(ic)
            }
        } else {
            if (activeCandidates.isEmpty()) {
                clearWordCorrection()
            } else if (::keyboardView.isInitialized) {
                keyboardView.updateCandidates(activeCandidates)
            }
        }
        try {
            Toast.makeText(this, "Removed \"$word\"", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun checkForWordCorrectionSuggestions(selStart: Int, selEnd: Int) {
        val suggestions = wordCorrectionController.checkSuggestions(currentInputConnection, selStart, selEnd)
        if (suggestions != null) {
            activeCandidates = suggestions
            if (::keyboardView.isInitialized) {
                keyboardView.isWordCorrectionActive = true
                keyboardView.updateCandidates(activeCandidates)
            }
        } else {
            clearWordCorrection()
        }
    }

    private fun commitWordCorrectionCandidate(index: Int) {
        val ic = currentInputConnection ?: return
        ignoreSelectionUpdateCount += 2
        val replacement = wordCorrectionController.commitCandidate(index, ic, isIncognito)
        if (replacement != null) {
            activeCandidates = emptyList()
            if (::keyboardView.isInitialized) {
                keyboardView.isWordCorrectionActive = false
                keyboardView.updateCandidates(emptyList())
            }
            updateAutoCaps()
        }
    }

    private fun clearWordCorrection() {
        if (::wordCorrectionController.isInitialized) {
            wordCorrectionController.clear()
        }
        if (::keyboardView.isInitialized) {
            keyboardView.isWordCorrectionActive = false
        }
        if (!hasActiveComposing) {
            activeCandidates = emptyList()
            if (::keyboardView.isInitialized) {
                keyboardView.updateCandidates(emptyList())
            }
        }
    }

    private fun launchSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::keyboardView.isInitialized) {
            keyboardView.updateThemeFromConfiguration(newConfig)
            keyboardView.requestLayout()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NativeEngineBridge.stopAudio()
        settingsObserver.stop()
    }
}
