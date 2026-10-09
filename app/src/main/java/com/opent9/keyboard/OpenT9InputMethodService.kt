package com.opent9.keyboard

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.lexicon.LexiconLoader
import com.opent9.keyboard.prediction.*
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
    private lateinit var composingCoordinator: T9ComposingCoordinator

    private val isMultiTapActive: Boolean
        get() = if (::multiTapController.isInitialized) multiTapController.isMultiTapActive else false

    private val multiTapWordBuffer: StringBuilder
        get() = if (::multiTapController.isInitialized) multiTapController.multiTapWordBuffer else StringBuilder()

    private val activeWordCorrectionContext: WordCorrectionContext?
        get() = if (::wordCorrectionController.isInitialized) wordCorrectionController.activeContext else null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentEditorInfo: EditorInfo? = null

    // Composing state tracking (Reflection targets for unit test suites)
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
            onAutoCapsUpdate = { updateAutoCaps() },
            getInputConnection = { currentInputConnection }
        )
        wordCorrectionController = WordCorrectionController(
            suggestionGuard = suggestionGuard,
            learnedWordsRepository = learnedWordsRepository,
            getProcessedCandidates = { getProcessedCandidates() }
        )

        composingCoordinator = T9ComposingCoordinator(
            currentComposingDigits = currentComposingDigits,
            currentComposingStrokes = currentComposingStrokes,
            suggestionGuard = suggestionGuard,
            getHasActiveComposing = { hasActiveComposing },
            setHasActiveComposing = { hasActiveComposing = it },
            getActiveCandidates = { activeCandidates },
            setActiveCandidates = { activeCandidates = it },
            getComposingAnchorPosition = { composingAnchorPosition },
            setComposingAnchorPosition = { composingAnchorPosition = it },
            getLastSelectionStart = { lastSelectionStart },
            setLastSpaceTapTime = { lastSpaceTapTime = it },
            setIsBackspaceAction = { isBackspaceAction = it },
            incrementIgnoreSelectionUpdateCount = { ignoreSelectionUpdateCount += it },
            getInputConnection = { currentInputConnection },
            getKeyboardView = { if (::keyboardView.isInitialized) keyboardView else null },
            getShiftController = { shiftController },
            getDeleteController = { deleteController },
            getLearnedWordsRepository = { learnedWordsRepository },
            getMultiTapController = { multiTapController },
            getWordCorrectionController = { wordCorrectionController },
            getSettingsObserver = { settingsObserver },
            isIncognito = { isIncognito },
            updateAutoCaps = { clearManual -> updateAutoCaps(clearManual) },
            applyShiftFormatting = { applyShiftFormatting(it) },
            updateCandidatesFromNative = { ic, prefWord -> updateCandidatesFromNative(ic, prefWord) },
            clearWordCorrection = { clearWordCorrection() }
        )

        val dbFile = File(filesDir, "opent9_vocab.dat")
        dbFile.parentFile?.mkdirs()
        NativeEngineBridge.initEngine(dbFile.absolutePath)

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

        ImeLifecycleConfigurator.setupObserverCallbacks(
            settingsObserver = settingsObserver,
            getKeyboardView = { if (::keyboardView.isInitialized) keyboardView else null },
            isPasswordMode = { isPasswordMode },
            getCurrentEditorInfo = { currentEditorInfo },
            onAutoCapsChanged = { updateAutoCaps(clearManualOverride = true) }
        )

        setupKeyboardViewListeners()
        return keyboardView
    }

    private fun setupKeyboardViewListeners() {
        keyboardView.onKeyTapAction = { key, touchX, touchY -> handleKeyTap(key, touchX, touchY) }
        keyboardView.onKeyFlickAction = { key, direction -> handleKeyFlick(key, direction) }
        keyboardView.onKeyLongPressAction = { key -> handleKeyLongPress(key) }
        keyboardView.onKeyDeleteRepeatAction = { isWordDelete -> handleDeleteRepeat(isWordDelete) }

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

        keyboardView.onPillTapAction = { toggleT9Mode() }
        keyboardView.onCandidateTapAction = { index ->
            if (activeWordCorrectionContext != null) {
                commitWordCorrectionCandidate(index)
            } else {
                commitCandidateIndex(index)
            }
        }
        keyboardView.onCandidateLongPressAction = { _, word -> promptRemoveCandidate(word) }
        keyboardView.onOpenSettingsAction = { launchSettings() }

        keyboardView.onEmojiSelectedAction = { emoji ->
            EmojiActionDispatcher.handleEmojiSelected(
                emoji = emoji,
                ic = currentInputConnection,
                hasActiveComposing = hasActiveComposing,
                onCommitActiveCandidate = { addSpace -> commitActiveCandidate(addSpace) }
            )
        }

        keyboardView.onEmojiControlAction = { controlIndex ->
            EmojiActionDispatcher.handleEmojiControl(
                controlIndex = controlIndex,
                ic = currentInputConnection,
                keyboardView = keyboardView,
                hasActiveComposing = hasActiveComposing,
                onSwitchToPage = { targetPage -> switchToPage(targetPage) },
                onCommitActiveCandidate = { addSpace -> commitActiveCandidate(addSpace) },
                onDeleteTap = { ic ->
                    deleteController.handleDeleteTap(
                        ic = ic,
                        hasActiveComposing = hasActiveComposing,
                        onPopComposingStroke = { popComposingStroke(ic) },
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
            )
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
            keyboardView.setT9Mode(false)
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
        composingCoordinator.finalizeComposingInternal(ic, isCursorMove)
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

        CursorSelectionCoordinator.coordinateSelection(
            oldSelStart = oldSelStart,
            oldSelEnd = oldSelEnd,
            newSelStart = newSelStart,
            newSelEnd = newSelEnd,
            candidatesStart = candidatesStart,
            candidatesEnd = candidatesEnd,
            ignoreSelectionUpdateCount = ignoreSelectionUpdateCount,
            onDecrementIgnoreCount = { ignoreSelectionUpdateCount-- },
            setLastSelectionStart = { lastSelectionStart = it },
            setLastSpaceTapTime = { lastSpaceTapTime = it },
            setComposingAnchorPosition = { composingAnchorPosition = it },
            composingAnchorPosition = composingAnchorPosition,
            isBackspaceAction = isBackspaceAction,
            setIsBackspaceAction = { isBackspaceAction = it },
            clearWordCorrection = { clearWordCorrection() },
            hasActiveComposing = hasActiveComposing,
            hasComposingDigits = currentComposingDigits.isNotEmpty(),
            isMultiTapActive = isMultiTapActive,
            hasMultiTapBuffer = multiTapWordBuffer.isNotEmpty(),
            isT9Mode = if (::keyboardView.isInitialized) keyboardView.isT9Mode else true,
            firstCandidateLength = if (activeCandidates.isNotEmpty()) activeCandidates[0].length else 0,
            isPasswordMode = isPasswordMode,
            onFinalizeComposingOnCursorMove = { finalizeComposingOnCursorMove(currentInputConnection) },
            onCommitMultiTapActive = { commitMultiTapActive() },
            onFlushMultiTapWord = { flushMultiTapWord() },
            updateAutoCaps = { cursorMoved -> updateAutoCaps(clearManualOverride = cursorMoved) },
            checkForWordCorrectionSuggestions = { sStart, sEnd -> checkForWordCorrectionSuggestions(sStart, sEnd) }
        )
    }

    private val activePageController: PageController
        get() {
            if (!::pageController.isInitialized) {
                val atlas = if (::keyboardView.isInitialized) keyboardView.keyAtlas else KeyAtlas()
                pageController = PageController(atlas)
            }
            return pageController
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
                keyboardView.updateCandidates(emptyList())
            }
            KeyboardPage.PAGE_0_TEXT -> {
                keyboardView.setT9Mode(settingsObserver.isDefaultT9())
            }
        }
        keyboardView.keyAtlas.updatePageLayout(targetPage)
        keyboardView.invalidate()
    }

    private fun createActionContext(): KeyboardActionContext {
        return KeyboardActionContext(
            ic = currentInputConnection,
            isT9Mode = if (::keyboardView.isInitialized) keyboardView.isT9Mode else true,
            isPasswordMode = isPasswordMode,
            hasActiveComposing = hasActiveComposing,
            currentComposingDigits = currentComposingDigits,
            currentComposingStrokes = currentComposingStrokes,
            composingAnchorPosition = composingAnchorPosition,
            lastSelectionStart = lastSelectionStart,
            hasActiveWordCorrection = activeWordCorrectionContext != null,
            shiftController = shiftController,
            enterKeyHandler = enterKeyHandler,
            deleteController = deleteController,
            multiTapController = multiTapController,
            settingsObserver = settingsObserver,
            currentEditorInfo = currentEditorInfo,
            lastSpaceTapTime = lastSpaceTapTime,
            setBackspaceAction = { isBackspaceAction = it },
            setLastSpaceTapTime = { lastSpaceTapTime = it },
            setComposingAnchorPosition = { composingAnchorPosition = it },
            setHasActiveComposing = { hasActiveComposing = it },
            clearWordCorrection = { clearWordCorrection() },
            commitMultiTapActive = { commitMultiTapActive() },
            flushMultiTapWord = { flushMultiTapWord() },
            handleMultiTap = { digit, ic -> handleMultiTap(digit, ic) },
            commitActiveCandidate = { addSpace -> commitActiveCandidate(addSpace) },
            updateCandidatesFromNative = { ic -> updateCandidatesFromNative(ic) },
            updateAutoCaps = { clearOverride -> updateAutoCaps(clearOverride) },
            popComposingStroke = { ic -> popComposingStroke(ic) },
            resetComposingState = { resetComposingState() },
            rehydrateComposingDigits = { digits, word, ic -> rehydrateComposingDigits(digits, word, ic) },
            setShiftState = { state -> if (::keyboardView.isInitialized) keyboardView.setShiftState(state) },
            switchToPage = { page -> switchToPage(page) },
            cycleLanguage = { ic -> cycleLanguage(ic) },
            emptyCandidates = { if (::keyboardView.isInitialized) keyboardView.updateCandidates(emptyList()) },
            toggleT9Mode = { toggleT9Mode() },
            launchSettings = { launchSettings() },
            pageController = activePageController,
            onInvalidateKeyboard = { if (::keyboardView.isInitialized) keyboardView.invalidate() },
            onAbortComposing = { ic -> abortComposing(ic) }
        )
    }

    private fun handleKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {
        KeyboardActionDispatcher.dispatchKeyTap(key, touchX, touchY, createActionContext())
    }

    private fun cycleLanguage(ic: InputConnection?) {
        LanguageCycleHelper.cycleLanguage(
            hasActiveComposing = hasActiveComposing,
            ic = ic,
            currentComposingStrokes = currentComposingStrokes,
            suggestionGuard = suggestionGuard,
            currentComposingDigits = currentComposingDigits,
            onLanguageChanged = { newLang ->
                if (::keyboardView.isInitialized) {
                    keyboardView.setLanguage(newLang)
                }
            },
            updateCandidatesFromNative = { inputConn ->
                updateCandidatesFromNative(inputConn)
            }
        )
    }

    private fun handleKeyFlick(key: KeyInfo, direction: FlickDirection) {
        KeyboardActionDispatcher.dispatchKeyFlick(key, direction, createActionContext())
    }

    private fun handleKeyLongPress(key: KeyInfo) {
        KeyboardActionDispatcher.dispatchKeyLongPress(key, createActionContext())
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
        composingCoordinator.commitActiveCandidate(addSpace)
    }

    private fun commitCandidateIndex(index: Int) {
        composingCoordinator.commitCandidateIndex(index)
    }

    private fun commitWord(word: String, addSpace: Boolean) {
        composingCoordinator.commitWord(word, addSpace)
    }

    private fun abortComposing(ic: InputConnection?) {
        composingCoordinator.abortComposing(ic)
    }

    private fun handleDeleteRepeat(isWordDelete: Boolean) {
        composingCoordinator.handleDeleteRepeat(isWordDelete, activeWordCorrectionContext != null)
    }

    private fun popComposingStroke(ic: InputConnection) {
        composingCoordinator.popComposingStroke(ic)
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
        composingCoordinator.rehydrateComposingDigits(digits, restoredWord, ic)
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
        if (::composingCoordinator.isInitialized) {
            composingCoordinator.resetComposingState()
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
        activeCandidates = CandidateRemovalHelper.removeCandidateSuggestion(
            context = this,
            word = word,
            learnedWordsRepository = learnedWordsRepository,
            activeCandidates = activeCandidates,
            hasActiveComposing = hasActiveComposing,
            currentComposingDigits = currentComposingDigits,
            currentComposingStrokes = currentComposingStrokes,
            suggestionGuard = suggestionGuard,
            ic = currentInputConnection,
            updateCandidatesFromNative = { ic -> updateCandidatesFromNative(ic) },
            onCandidatesUpdated = { updated ->
                if (::keyboardView.isInitialized) {
                    keyboardView.updateCandidates(updated)
                }
            },
            onClearWordCorrection = { clearWordCorrection() }
        )
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
        ImeLifecycleConfigurator.launchSettings(this)
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
