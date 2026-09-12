package com.opent9.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.opent9.keyboard.jni.NativeEngineBridge
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
    private val currentComposingDigits = ArrayList<Int>(32)
    private var hasActiveComposing = false
    private var isIncognito = false
    private var isPasswordMode = false

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
        try {
            assets.openFd("dictionaries/en_lexicon.dawg").use { afd ->
                NativeEngineBridge.loadLexiconFd(
                    "EN",
                    afd.parcelFileDescriptor.fd,
                    afd.startOffset,
                    afd.length
                )
            }
        } catch (_: Exception) {}

        try {
            assets.openFd("dictionaries/id_lexicon.dawg").use { afd ->
                NativeEngineBridge.loadLexiconFd(
                    "ID",
                    afd.parcelFileDescriptor.fd,
                    afd.startOffset,
                    afd.length
                )
            }
        } catch (_: Exception) {}

        val startupLang = settingsObserver.getStartupLanguage()
        NativeEngineBridge.switchLanguage(startupLang)
    }

    override fun onCreateInputView(): View {
        keyboardView = T9KeyboardView(this)
        pageController = PageController(keyboardView.keyAtlas)

        val defaultLang = settingsObserver.getStartupLanguage()
        keyboardView.setLanguage(defaultLang)
        keyboardView.setT9Mode(settingsObserver.isDefaultT9())

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
            commitCandidateIndex(index)
        }

        keyboardView.onOpenSettingsAction = {
            launchSettings()
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentEditorInfo = info
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

        val imeAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
        keyboardView.setImeAction(imeAction)
        keyboardView.updateCandidates(emptyList())
    }

    private fun handleKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {
        val ic = currentInputConnection ?: return

        when (key.type) {
            KeyType.DIGIT_T9 -> {
                if (keyboardView.isT9Mode && !isPasswordMode) {
                    // Push stroke to C++ DAWG beam search
                    currentComposingDigits.add(key.digitValue)
                    NativeEngineBridge.pushStroke(key.digitValue, touchX, touchY)
                    hasActiveComposing = true
                    updateCandidatesFromNative(ic)
                } else {
                    // Multi-Tap ABC mode
                    handleMultiTap(key.digitValue, ic)
                }
            }

            KeyType.PUNCT_1 -> {
                if (keyboardView.isT9Mode && hasActiveComposing) {
                    commitActiveCandidate(addSpace = false)
                }
                ic.commitText(".", 1)
            }

            KeyType.SPACE_0 -> {
                if (keyboardView.isT9Mode && hasActiveComposing) {
                    commitActiveCandidate(addSpace = true)
                } else if (NativeEngineBridge.multiTapCommit() != 0.toChar()) {
                    ic.finishComposingText()
                    ic.commitText(" ", 1)
                } else {
                    ic.commitText(" ", 1)
                }
            }

            KeyType.DEL -> {
                val wasUncommitted = deleteController.handleDeleteTap(
                    ic = ic,
                    hasActiveComposing = hasActiveComposing,
                    onPopComposingStroke = {
                        popComposingStroke(ic)
                    },
                    onRehydrateDigits = { digits ->
                        rehydrateComposingDigits(digits, ic)
                    }
                )
                if (wasUncommitted) {
                    updateCandidatesFromNative(ic)
                }
            }

            KeyType.SHIFT -> {
                val nextMode = shiftController.onShiftTap(System.currentTimeMillis())
                keyboardView.setShiftState(nextMode.stateValue)
            }

            KeyType.ENTER -> {
                enterKeyHandler.handleEnterTap(
                    ic = ic,
                    editorInfo = currentEditorInfo,
                    hasActiveComposing = hasActiveComposing,
                    onCommitActiveCandidate = {
                        commitActiveCandidate(addSpace = false)
                    }
                )
            }

            KeyType.PAGE_SWITCH -> {
                val targetPage = when (key.primaryLabel) {
                    "?123" -> KeyboardPage.PAGE_1_NUM_SYM
                    "=\\<" -> KeyboardPage.PAGE_2_EXT_SYM
                    "123" -> KeyboardPage.PAGE_1_NUM_SYM
                    "ABC" -> KeyboardPage.PAGE_0_TEXT
                    else -> KeyboardPage.PAGE_0_TEXT
                }
                keyboardView.keyAtlas.updatePageLayout(targetPage)
                keyboardView.invalidate()
            }

            KeyType.LANG_SWITCH -> {
                val currentLang = NativeEngineBridge.getActiveLanguage()
                val nextLang = if (currentLang == "EN") "ID" else "EN"
                NativeEngineBridge.switchLanguage(nextLang)
                keyboardView.setLanguage(nextLang)

                // If currently composing, re-evaluate with swapped lexicon pointer
                if (hasActiveComposing) {
                    val savedDigits = ArrayList(currentComposingDigits)
                    NativeEngineBridge.resetT9()
                    currentComposingDigits.clear()
                    for (d in savedDigits) {
                        currentComposingDigits.add(d)
                        NativeEngineBridge.pushStroke(d, 0f, 0f)
                    }
                    updateCandidatesFromNative(ic)
                }
            }

            KeyType.EMOJI_DOT -> {
                keyboardView.keyAtlas.updatePageLayout(KeyboardPage.PAGE_3_EMOJI)
                keyboardView.invalidate()
            }

            KeyType.DIRECT_SYM -> {
                ic.commitText(key.primaryLabel, 1)
            }

            KeyType.DUAL_SYM -> {
                // Tap on dual symbol defaults to left glyph
                if (key.leftGlyph.isNotEmpty()) {
                    ic.commitText(key.leftGlyph, 1)
                }
            }
        }
    }

    private fun handleKeyFlick(key: KeyInfo, direction: FlickDirection) {
        val ic = currentInputConnection ?: return

        if (key.type == KeyType.SHIFT && direction == FlickDirection.UP) {
            val nextMode = shiftController.onShiftFlickUp()
            keyboardView.setShiftState(nextMode.stateValue)
            return
        }

        pageController.handleKeyFlick(
            key = key,
            direction = direction,
            ic = ic,
            onSwitchLanguage = {
                // Language hot-swap
                val currentLang = NativeEngineBridge.getActiveLanguage()
                val nextLang = if (currentLang == "EN") "ID" else "EN"
                NativeEngineBridge.switchLanguage(nextLang)
                keyboardView.setLanguage(nextLang)
            },
            onToggleT9Mode = {
                toggleT9Mode()
            },
            onClearField = {
                deleteController.clearEntireField(ic)
                resetComposingState()
            },
            onDeletePrecedingWord = {
                deleteController.deletePrecedingWord(ic)
                resetComposingState()
            },
            onForceSubmit = {
                enterKeyHandler.handleForceSubmit(ic, currentEditorInfo)
            }
        )
    }

    private fun handleKeyLongPress(key: KeyInfo) {
        val ic = currentInputConnection ?: return
        when (key.type) {
            KeyType.DEL -> {
                deleteController.deletePrecedingWord(ic)
            }
            KeyType.SHIFT -> {
                val nextMode = shiftController.onShiftLongPress()
                keyboardView.setShiftState(nextMode.stateValue)
            }
            KeyType.DIGIT_T9, KeyType.PUNCT_1, KeyType.SPACE_0 -> {
                if (key.digitValue >= 0) {
                    ic.commitText(key.digitValue.toString(), 1)
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
            ic.commitText(res.committedChar.toString(), 1)
            shiftController.onCharacterCommitted()
            keyboardView.setShiftState(shiftController.currentMode.stateValue)
        }

        ic.setComposingText(res.activeChar.toString(), 1)
        mainHandler.postDelayed(multiTapTimeoutRunnable, 600L)
    }

    private fun commitMultiTapActive() {
        val ic = currentInputConnection ?: return
        val committed = NativeEngineBridge.multiTapCommit()
        if (committed != 0.toChar()) {
            ic.finishComposingText()
            shiftController.onCharacterCommitted()
            keyboardView.setShiftState(shiftController.currentMode.stateValue)
        }
    }

    private fun updateCandidatesFromNative(ic: InputConnection) {
        val candidates = NativeEngineBridge.getCandidates()
        keyboardView.updateCandidates(candidates)
        if (candidates.isNotEmpty()) {
            val topCandidate = applyShiftFormatting(candidates[0])
            ic.setComposingText(topCandidate, 1)
        } else {
            // Fallback: literal digits
            val fallback = currentComposingDigits.joinToString("")
            ic.setComposingText(fallback, 1)
        }
    }

    private fun applyShiftFormatting(word: String): String {
        return when (shiftController.currentMode) {
            ShiftMode.TITLECASE -> word.replaceFirstChar { it.uppercase() }
            ShiftMode.UPPERCASE -> word.uppercase()
            ShiftMode.LOWERCASE -> word.lowercase()
        }
    }

    private fun commitActiveCandidate(addSpace: Boolean) {
        val candidates = NativeEngineBridge.getCandidates()
        val word = if (candidates.isNotEmpty()) applyShiftFormatting(candidates[0]) else currentComposingDigits.joinToString("")
        commitWord(word, addSpace)
    }

    private fun commitCandidateIndex(index: Int) {
        val candidates = NativeEngineBridge.getCandidates()
        if (index in candidates.indices) {
            val word = applyShiftFormatting(candidates[index])
            commitWord(word, addSpace = true)
        }
    }

    private fun commitWord(word: String, addSpace: Boolean) {
        val ic = currentInputConnection ?: return
        val digitsSnapshot = ArrayList(currentComposingDigits)

        ic.finishComposingText()
        val textToCommit = if (addSpace) "$word " else word
        ic.commitText(textToCommit, 1)

        // Record commit for retroactive un-commit
        deleteController.recordCommit(word, digitsSnapshot)

        // Record usage for dynamic dictionary learning if not incognito
        if (!isIncognito && word.isNotEmpty()) {
            NativeEngineBridge.recordUsage(word.lowercase(), System.currentTimeMillis() / 1000L)
        }

        // Auto-reset Shift if Titlecase
        shiftController.onCharacterCommitted()
        keyboardView.setShiftState(shiftController.currentMode.stateValue)

        resetComposingState()
        keyboardView.updateCandidates(emptyList())
    }

    private fun popComposingStroke(ic: InputConnection) {
        if (currentComposingDigits.isNotEmpty()) {
            currentComposingDigits.removeAt(currentComposingDigits.size - 1)
            NativeEngineBridge.popStroke()
            if (currentComposingDigits.isEmpty()) {
                hasActiveComposing = false
                ic.finishComposingText()
                keyboardView.updateCandidates(emptyList())
            } else {
                updateCandidatesFromNative(ic)
            }
        }
    }

    private fun rehydrateComposingDigits(digits: List<Int>, ic: InputConnection) {
        NativeEngineBridge.resetT9()
        currentComposingDigits.clear()
        for (d in digits) {
            currentComposingDigits.add(d)
            NativeEngineBridge.pushStroke(d, 0f, 0f)
        }
        hasActiveComposing = currentComposingDigits.isNotEmpty()
        updateCandidatesFromNative(ic)
    }

    private fun toggleT9Mode() {
        val nextMode = !keyboardView.isT9Mode
        if (hasActiveComposing) {
            commitActiveCandidate(addSpace = false)
        }
        commitMultiTapActive()
        keyboardView.setT9Mode(nextMode)
    }

    private fun resetComposingState() {
        NativeEngineBridge.resetT9()
        NativeEngineBridge.multiTapReset()
        currentComposingDigits.clear()
        hasActiveComposing = false
    }

    private fun launchSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsObserver.stop()
    }
}
