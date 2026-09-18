package com.opent9.keyboard

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
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import android.app.AlertDialog
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.prediction.EnglishOrthography
import com.opent9.keyboard.prediction.SuggestionGuard
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
    private val multiTapWordBuffer = StringBuilder(64)
    private val suggestionGuard = SuggestionGuard()
    private var activeCandidates: List<String> = emptyList()

    data class WordCorrectionContext(
        val originalWord: String,
        val isSelection: Boolean,
        val beforeLength: Int = 0,
        val afterLength: Int = 0
    )

    private var activeWordCorrectionContext: WordCorrectionContext? = null
    private var isBackspaceAction = false
    private var lastSpaceTapTime = 0L
    private var lastSelectionStart = 0
    private var composingAnchorPosition = -1

    // Multi-tap timer runnable
    private val multiTapTimeoutRunnable = Runnable {
        commitMultiTapActive()
    }

    override fun onCreate() {
        super.onCreate()
        settingsObserver = SettingsObserver(this)
        settingsObserver.start()

        val dbFile = File(filesDir, "opent9_vocab.dat")
        NativeEngineBridge.initEngine(dbFile.absolutePath)

        // Load static binary DAWGs directly into Linux page cache via mmap
        loadLexicons()
    }

    private fun loadLexicons() {
        loadLexiconForLanguage("EN", "dictionaries/en_lexicon.dawg")
        loadLexiconForLanguage("ID", "dictionaries/id_lexicon.dawg")

        val startupLang = settingsObserver.getStartupLanguage()
        NativeEngineBridge.switchLanguage(startupLang)
    }

    private fun loadLexiconForLanguage(langCode: String, assetPath: String) {
        var loaded = false
        try {
            assets.openFd(assetPath).use { afd ->
                loaded = NativeEngineBridge.loadLexiconFd(
                    langCode,
                    afd.parcelFileDescriptor.fd,
                    afd.startOffset,
                    afd.length
                )
            }
        } catch (e: Exception) {
            Log.w("OpenT9", "Direct asset openFd failed for $assetPath: ${e.message}")
        }

        if (!loaded) {
            try {
                val outFile = File(filesDir, assetPath.substringAfterLast('/'))
                if (!outFile.exists() || outFile.length() == 0L) {
                    outFile.parentFile?.mkdirs()
                    assets.open(assetPath).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    loaded = NativeEngineBridge.loadLexiconFd(
                        langCode,
                        pfd.fd,
                        0L,
                        outFile.length()
                    )
                }
                Log.i("OpenT9", "Loaded $langCode lexicon via fallback file ($outFile): $loaded")
            } catch (e: Exception) {
                Log.e("OpenT9", "Failed to load $langCode lexicon via fallback: ${e.message}", e)
            }
        }
    }

    override fun onCreateInputView(): View {
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
                    val isNumeric = currentEditorInfo?.let { info ->
                        val clazz = info.inputType and InputType.TYPE_MASK_CLASS
                        clazz == InputType.TYPE_CLASS_NUMBER ||
                                clazz == InputType.TYPE_CLASS_PHONE ||
                                clazz == InputType.TYPE_CLASS_DATETIME
                    } ?: false
                    if (!isNumeric) {
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

        val inputType = info.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val clazz = inputType and InputType.TYPE_MASK_CLASS

        // 1. Password detection
        isPasswordMode = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        // 2. Incognito detection
        isIncognito = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0 || isPasswordMode

        // 3. Numeric fields
        val isNumeric = clazz == InputType.TYPE_CLASS_NUMBER ||
                clazz == InputType.TYPE_CLASS_PHONE ||
                clazz == InputType.TYPE_CLASS_DATETIME

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

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (hasActiveComposing) {
            commitActiveCandidate(addSpace = false)
        }
        resetComposingState()
        if (::keyboardView.isInitialized) {
            keyboardView.updateCandidates(emptyList())
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        if (hasActiveComposing) {
            commitActiveCandidate(addSpace = false)
        }
        resetComposingState()
        if (::keyboardView.isInitialized) {
            keyboardView.updateCandidates(emptyList())
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
            if (!hasActiveComposing && currentComposingDigits.isEmpty()) {
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
        } else if (::keyboardView.isInitialized && !keyboardView.isT9Mode && multiTapWordBuffer.isNotEmpty()) {
            if (newSelStart < oldSelStart || kotlin.math.abs(newSelStart - oldSelStart) > 1 || newSelStart != newSelEnd) {
                commitMultiTapActive()
                flushMultiTapWord()
            }
        }

        if (!hasActiveComposing && currentComposingDigits.isEmpty()) {
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

                    val isDoubleSpaceEnabled = settingsObserver.isDoubleSpacePeriodEnabled()
                    val textBefore = if (isDoubleSpaceEnabled && !isPasswordMode) {
                        ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
                    } else ""

                    val isDoubleSpace = isDoubleSpaceEnabled &&
                            !isPasswordMode &&
                            lastSpaceTapTime > 0L &&
                            (now - lastSpaceTapTime < 800L) &&
                            textBefore.length >= 2 &&
                            textBefore.endsWith(" ") &&
                            !textBefore[textBefore.length - 2].isWhitespace() &&
                            textBefore[textBefore.length - 2] != '.' &&
                            textBefore[textBefore.length - 2] != '?' &&
                            textBefore[textBefore.length - 2] != '!'

                    if (isDoubleSpace) {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(". ", 1)
                        lastSpaceTapTime = 0L
                        updateAutoCaps()
                    } else {
                        ic.commitText(" ", 1)
                        lastSpaceTapTime = now
                        updateAutoCaps()
                    }
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
                if (!keyboardView.isT9Mode && multiTapWordBuffer.isNotEmpty()) {
                    multiTapWordBuffer.deleteCharAt(multiTapWordBuffer.length - 1)
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
        mainHandler.removeCallbacks(multiTapTimeoutRunnable)
        val now = System.currentTimeMillis()
        val res = NativeEngineBridge.handleMultiTapPress(digit, now, shiftController.currentMode.stateValue)

        if (res.committedPrev) {
            if (res.committedChar.isLetter()) {
                multiTapWordBuffer.append(res.committedChar)
            } else {
                flushMultiTapWord()
            }
            ic.commitText(res.committedChar.toString(), 1)
            shiftController.onCharacterCommitted()
            keyboardView.setShiftState(shiftController.currentMode.stateValue)
            updateAutoCaps()
        }

        ic.setComposingText(res.activeChar.toString(), 1)
        val timeout = settingsObserver.getMultiTapTimeout()
        mainHandler.postDelayed(multiTapTimeoutRunnable, timeout)
    }

    private fun commitMultiTapActive() {
        mainHandler.removeCallbacks(multiTapTimeoutRunnable)
        val ic = currentInputConnection ?: return
        val committed = NativeEngineBridge.multiTapCommit()
        if (committed != 0.toChar()) {
            if (committed.isLetter()) {
                multiTapWordBuffer.append(committed)
            } else {
                flushMultiTapWord()
            }
            ic.finishComposingText()
            shiftController.onCharacterCommitted()
            keyboardView.setShiftState(shiftController.currentMode.stateValue)
            updateAutoCaps()
        }
    }

    private fun flushMultiTapWord() {
        if (multiTapWordBuffer.isNotEmpty()) {
            val word = multiTapWordBuffer.toString().trim()
            multiTapWordBuffer.clear()
            if (word.length >= 2 && !isIncognito) {
                val canonicalWord = EnglishOrthography.toCanonical(word)
                if (!NativeEngineBridge.isWordDeleted(canonicalWord.lowercase())) {
                    NativeEngineBridge.recordUsage(canonicalWord.lowercase(), System.currentTimeMillis() / 1000L)
                }
            }
        }
    }

    private fun getProcessedCandidates(): List<String> {
        val raw = NativeEngineBridge.getCandidates()
        val lang = NativeEngineBridge.getActiveLanguage()
        return EnglishOrthography.processCandidates(raw, lang)
    }

    private fun updateCandidatesFromNative(ic: InputConnection) {
        updateCandidatesFromNative(ic, null)
    }

    private fun updateCandidatesFromNative(ic: InputConnection, preferredWord: String?) {
        val rawCandidates = getProcessedCandidates().filterNot { candidate ->
            NativeEngineBridge.isWordDeleted(EnglishOrthography.toCanonical(candidate))
        }
        val candidatesList = if (rawCandidates.isNotEmpty()) {
            suggestionGuard.recordValidCandidates(rawCandidates, currentComposingDigits)
            rawCandidates
        } else if (currentComposingDigits.isNotEmpty()) {
            val lang = NativeEngineBridge.getActiveLanguage()
            suggestionGuard.getGuardedCandidates(currentComposingDigits, lang)
        } else {
            emptyList()
        }

        activeCandidates = if (!preferredWord.isNullOrEmpty() && candidatesList.isNotEmpty()) {
            val mutable = ArrayList(candidatesList)
            val matchIdx = mutable.indexOfFirst { it.equals(preferredWord, ignoreCase = true) }
            if (matchIdx > 0) {
                val matched = mutable.removeAt(matchIdx)
                mutable.add(0, matched)
            } else if (matchIdx < 0) {
                mutable.add(0, preferredWord)
            }
            mutable
        } else {
            candidatesList
        }

        keyboardView.updateCandidates(activeCandidates)
        if (activeCandidates.isNotEmpty()) {
            val topCandidate = applyShiftFormatting(activeCandidates[0])
            ic.setComposingText(topCandidate, 1)
        } else {
            ic.setComposingText("", 0)
        }
    }

    private fun applyShiftFormatting(word: String): String {
        return when (shiftController.currentMode) {
            ShiftMode.TITLECASE -> word.replaceFirstChar { it.uppercase() }
            ShiftMode.UPPERCASE -> word.uppercase()
            ShiftMode.LOWERCASE -> {
                if (word == "I" || word.startsWith("I'")) word else word.lowercase()
            }
        }
    }

    private fun commitActiveCandidate(addSpace: Boolean) {
        val word = if (activeCandidates.isNotEmpty()) {
            applyShiftFormatting(activeCandidates[0])
        } else if (currentComposingDigits.isNotEmpty()) {
            val lang = NativeEngineBridge.getActiveLanguage()
            val fallback = suggestionGuard.projectWordFromDigits(currentComposingDigits, alt = false, lang = lang)
            applyShiftFormatting(fallback)
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
        if (!isIncognito && word.isNotEmpty() && !word.all { it.isDigit() }) {
            val canonicalWord = EnglishOrthography.toCanonical(word)
            if (!NativeEngineBridge.isWordDeleted(canonicalWord.lowercase())) {
                NativeEngineBridge.recordUsage(canonicalWord.lowercase(), System.currentTimeMillis() / 1000L)
            }
        }

        // Auto-reset Shift if Titlecase
        shiftController.onCharacterCommitted()
        keyboardView.setShiftState(shiftController.currentMode.stateValue)

        resetComposingState()
        keyboardView.updateCandidates(emptyList())

        updateAutoCaps()
    }

    private fun finalizeComposingOnCursorMove(ic: InputConnection?) {
        val word = if (activeCandidates.isNotEmpty()) {
            applyShiftFormatting(activeCandidates[0])
        } else if (currentComposingDigits.isNotEmpty()) {
            val lang = NativeEngineBridge.getActiveLanguage()
            val fallback = suggestionGuard.projectWordFromDigits(currentComposingDigits, alt = false, lang = lang)
            applyShiftFormatting(fallback)
        } else {
            ""
        }

        // Leave composing text in place as committed text without moving cursor away from new position
        ic?.finishComposingText()
        deleteController.clearCommitHistory()

        // Record usage for dynamic dictionary learning if not incognito and not pure numeric
        if (!isIncognito && word.isNotEmpty() && !word.all { it.isDigit() }) {
            val canonicalWord = EnglishOrthography.toCanonical(word)
            if (!NativeEngineBridge.isWordDeleted(canonicalWord.lowercase())) {
                NativeEngineBridge.recordUsage(canonicalWord.lowercase(), System.currentTimeMillis() / 1000L)
            }
        }

        // Auto-reset Shift if Titlecase
        shiftController.onCharacterCommitted()
        if (::keyboardView.isInitialized) {
            keyboardView.setShiftState(shiftController.currentMode.stateValue)
        }

        resetComposingState()
        if (::keyboardView.isInitialized) {
            keyboardView.updateCandidates(emptyList())
        }

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
        val ic = currentInputConnection ?: return
        val info = currentEditorInfo ?: return

        if (!settingsObserver.isAutoCapsEnabled()) {
            if (shiftController.currentMode != ShiftMode.LOWERCASE && !shiftController.isCapsLocked()) {
                shiftController.reset()
                if (::keyboardView.isInitialized) {
                    keyboardView.setShiftState(shiftController.currentMode.stateValue)
                }
            }
            return
        }

        if (clearManualOverride) {
            shiftController.clearManualOverride()
        }

        if (shiftController.isCapsLocked() || shiftController.isManualOverrideActive()) {
            return
        }

        val inputType = info.inputType
        val clazz = inputType and InputType.TYPE_MASK_CLASS
        if (clazz != InputType.TYPE_CLASS_TEXT) {
            if (shiftController.currentMode != ShiftMode.LOWERCASE && !shiftController.isCapsLocked()) {
                shiftController.reset()
                if (::keyboardView.isInitialized) {
                    keyboardView.setShiftState(shiftController.currentMode.stateValue)
                }
            }
            return
        }

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isExcluded = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI ||
                variation == InputType.TYPE_TEXT_VARIATION_FILTER

        if (isExcluded) {
            if (shiftController.currentMode != ShiftMode.LOWERCASE && !shiftController.isCapsLocked()) {
                shiftController.reset()
                if (::keyboardView.isInitialized) {
                    keyboardView.setShiftState(shiftController.currentMode.stateValue)
                }
            }
            return
        }

        var reqModes = inputType and (
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
        if (reqModes == 0) {
            reqModes = TextUtils.CAP_MODE_SENTENCES
        }

        val caps = ic.getCursorCapsMode(reqModes)
        val targetMode = when {
            (caps and TextUtils.CAP_MODE_CHARACTERS) != 0 -> ShiftMode.UPPERCASE
            (caps and (TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES)) != 0 -> ShiftMode.TITLECASE
            else -> ShiftMode.LOWERCASE
        }

        if (shiftController.setAutoCapsMode(targetMode)) {
            if (::keyboardView.isInitialized) {
                keyboardView.setShiftState(shiftController.currentMode.stateValue)
            }
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
        mainHandler.removeCallbacks(multiTapTimeoutRunnable)
        NativeEngineBridge.resetT9()
        NativeEngineBridge.multiTapReset()
        currentComposingDigits.clear()
        currentComposingStrokes.clear()
        multiTapWordBuffer.clear()
        hasActiveComposing = false
        suggestionGuard.reset()
        activeCandidates = emptyList()
        activeWordCorrectionContext = null
        composingAnchorPosition = -1
        if (::keyboardView.isInitialized) {
            keyboardView.isWordCorrectionActive = false
        }
    }

    private fun promptRemoveCandidate(word: String) {
        try {
            val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Remove suggestion?")
                .setMessage("Do you want to remove \"$word\" from suggestions?")
                .setPositiveButton("Remove") { _, _ ->
                    removeCandidateSuggestion(word)
                }
                .setNegativeButton("Cancel", null)

            val dialog = builder.create()
            dialog.window?.let { window ->
                val token = (if (::keyboardView.isInitialized) keyboardView.windowToken else null)
                    ?: this.window?.window?.attributes?.token
                if (token != null) {
                    val lp = window.attributes
                    lp.token = token
                    lp.type = WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG
                    window.attributes = lp
                    window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                }
            }
            dialog.show()
        } catch (_: Exception) {
            // Fallback for non-attached or headless test environments
            removeCandidateSuggestion(word)
        }
    }

    fun removeCandidateSuggestion(word: String) {
        val canonical = EnglishOrthography.toCanonical(word).lowercase()
        NativeEngineBridge.removeWord(canonical)
        // Immediately filter out from activeCandidates
        activeCandidates = activeCandidates.filterNot {
            EnglishOrthography.toCanonical(it).equals(canonical, ignoreCase = true)
        }
        keyboardView.updateCandidates(activeCandidates)

        currentInputConnection?.let { ic ->
            if (hasActiveComposing && currentComposingDigits.isNotEmpty()) {
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
            } else if (activeCandidates.isNotEmpty()) {
                val topCandidate = applyShiftFormatting(activeCandidates[0])
                ic.setComposingText(topCandidate, 1)
            } else {
                ic.setComposingText("", 0)
            }
        }
        try {
            Toast.makeText(this, "Removed \"$word\"", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun checkForWordCorrectionSuggestions(selStart: Int, selEnd: Int) {
        val ic = currentInputConnection ?: return

        if (selStart != selEnd) {
            val selected = ic.getSelectedText(0)?.toString() ?: ""
            val trimmed = selected.trim()
            if (trimmed.isNotEmpty() && trimmed.length <= 32 && trimmed.none { it.isWhitespace() } && trimmed.any { it.isLetter() }) {
                val suggestions = querySuggestionsForWord(trimmed)
                if (suggestions.isNotEmpty()) {
                    activeWordCorrectionContext = WordCorrectionContext(
                        originalWord = trimmed,
                        isSelection = true
                    )
                    activeCandidates = suggestions
                    if (::keyboardView.isInitialized) {
                        keyboardView.isWordCorrectionActive = true
                        keyboardView.updateCandidates(activeCandidates)
                    }
                    return
                }
            }
            clearWordCorrection()
            return
        }

        val textBefore = ic.getTextBeforeCursor(48, 0)?.toString() ?: ""
        val textAfter = ic.getTextAfterCursor(48, 0)?.toString() ?: ""

        val beforePart = extractWordPartBefore(textBefore)
        val afterPart = extractWordPartAfter(textAfter)
        val fullWord = beforePart + afterPart

        if (fullWord.isNotEmpty() && fullWord.length <= 32 && fullWord.any { it.isLetter() }) {
            val suggestions = querySuggestionsForWord(fullWord)
            if (suggestions.isNotEmpty()) {
                activeWordCorrectionContext = WordCorrectionContext(
                    originalWord = fullWord,
                    isSelection = false,
                    beforeLength = beforePart.length,
                    afterLength = afterPart.length
                )
                activeCandidates = suggestions
                if (::keyboardView.isInitialized) {
                    keyboardView.isWordCorrectionActive = true
                    keyboardView.updateCandidates(activeCandidates)
                }
                return
            }
        }

        clearWordCorrection()
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\''

    private fun extractWordPartBefore(text: String): String {
        var idx = text.length - 1
        while (idx >= 0 && isWordChar(text[idx])) {
            idx--
        }
        return text.substring(idx + 1)
    }

    private fun extractWordPartAfter(text: String): String {
        var idx = 0
        while (idx < text.length && isWordChar(text[idx])) {
            idx++
        }
        return text.substring(0, idx)
    }

    private fun querySuggestionsForWord(word: String): List<String> {
        val digits = wordToDigits(word)
        if (digits.isEmpty()) return emptyList()

        NativeEngineBridge.resetT9()
        for (d in digits) {
            NativeEngineBridge.pushStroke(d, 0f, 0f)
        }
        val rawCandidates = getProcessedCandidates().filterNot { candidate ->
            NativeEngineBridge.isWordDeleted(EnglishOrthography.toCanonical(candidate))
        }
        NativeEngineBridge.resetT9()

        val candidates = if (rawCandidates.isNotEmpty()) {
            rawCandidates
        } else {
            val lang = NativeEngineBridge.getActiveLanguage()
            suggestionGuard.getGuardedCandidates(digits, lang)
        }

        if (candidates.isEmpty()) return emptyList()

        return candidates.map { matchCase(word, it) }
    }

    private fun wordToDigits(word: String): List<Int> {
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

    private fun matchCase(source: String, target: String): String {
        if (source.isEmpty() || target.isEmpty()) return target
        if (source.all { it.isUpperCase() }) return target.uppercase()
        if (source[0].isUpperCase()) return target.replaceFirstChar { it.uppercase() }
        return target.lowercase()
    }

    private fun commitWordCorrectionCandidate(index: Int) {
        val context = activeWordCorrectionContext ?: return
        if (index !in activeCandidates.indices) return

        val replacement = activeCandidates[index]
        activeWordCorrectionContext = null

        val ic = currentInputConnection ?: return
        ignoreSelectionUpdateCount += 2
        ic.beginBatchEdit()
        try {
            if (context.isSelection) {
                ic.commitText(replacement, 1)
            } else {
                ic.deleteSurroundingText(context.beforeLength, context.afterLength)
                ic.commitText(replacement, 1)
            }
        } finally {
            ic.endBatchEdit()
        }

        activeCandidates = emptyList()
        if (::keyboardView.isInitialized) {
            keyboardView.isWordCorrectionActive = false
            keyboardView.updateCandidates(emptyList())
        }
        updateAutoCaps()
    }

    private fun clearWordCorrection() {
        if (::keyboardView.isInitialized) {
            keyboardView.isWordCorrectionActive = false
        }
        if (activeWordCorrectionContext != null || (activeCandidates.isNotEmpty() && !hasActiveComposing)) {
            activeWordCorrectionContext = null
            if (!hasActiveComposing) {
                activeCandidates = emptyList()
                if (::keyboardView.isInitialized) {
                    keyboardView.updateCandidates(emptyList())
                }
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
        settingsObserver.stop()
    }
}
