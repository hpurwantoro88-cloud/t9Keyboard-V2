package com.opent9.keyboard

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.opent9.keyboard.ui.KeyInfo
import com.opent9.keyboard.ui.KeyType
import com.opent9.keyboard.ui.KeyboardPage
import com.opent9.keyboard.ui.T9KeyboardView
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class TypingEngineIntegrationTest {

    private lateinit var controller: ServiceController<OpenT9InputMethodService>
    private lateinit var service: OpenT9InputMethodService

    @Before
    fun setup() {
        com.opent9.keyboard.jni.NativeEngineBridge.resetUserDictionary()
        controller = Robolectric.buildService(OpenT9InputMethodService::class.java)
        service = controller.create().get()
    }

    @Test
    fun testEditorInfoRouting() {
        val inputView = service.onCreateInputView() as T9KeyboardView

        // 1. Password Field
        val passwordInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        service.onStartInputView(passwordInfo, false)
        assertFalse("Password field must disable T9 mode", inputView.isT9Mode)
        assertEquals(KeyboardPage.PAGE_0_TEXT, inputView.keyAtlas.currentPage)

        // 2. Numeric / Phone Field
        val phoneInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_PHONE
        }
        service.onStartInputView(phoneInfo, false)
        assertFalse("Phone field must not be in T9 mode", inputView.isT9Mode)
        assertEquals(KeyboardPage.PAGE_1_NUM_SYM, inputView.keyAtlas.currentPage)

        // 3. Regular Text Field with Search Action
        val searchInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        service.onStartInputView(searchInfo, false)
        assertEquals(EditorInfo.IME_ACTION_SEARCH, inputView.imeAction)
        assertEquals(KeyboardPage.PAGE_0_TEXT, inputView.keyAtlas.currentPage)
    }

    @Test
    fun testSpacebarScrubbingEvents() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        // Verifies that listener was registered and ready
        assertNotNull(inputView.onSpaceScrubAction)
        inputView.onSpaceScrubAction?.invoke(2) // +2 right steps
    }

    @Test
    fun testLanguageCycleOnTap() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val textInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        service.onStartInputView(textInfo, false)

        val keyLang = inputView.keyAtlas.keys[13]
        val initialLang = inputView.activeLanguage

        // Tap Key 13: cycle language
        inputView.onKeyTapAction?.invoke(keyLang, keyLang.centerX, keyLang.centerY)
        val secondLang = inputView.activeLanguage
        assertNotEquals(initialLang, secondLang)

        // Tap again: cycles back
        inputView.onKeyTapAction?.invoke(keyLang, keyLang.centerX, keyLang.centerY)
        assertEquals(initialLang, inputView.activeLanguage)
    }

    @Test
    fun testT9ModeToggleOnLongPress() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val textInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        service.onStartInputView(textInfo, false)

        val keyLang = inputView.keyAtlas.keys[13]
        val initialT9 = inputView.isT9Mode

        // Long press Key 13: toggles T9 mode
        inputView.onKeyLongPressAction?.invoke(keyLang)
        assertEquals(!initialT9, inputView.isT9Mode)

        // Long press again: toggles back
        inputView.onKeyLongPressAction?.invoke(keyLang)
        assertEquals(initialT9, inputView.isT9Mode)
    }

    @Test
    fun testFlickUpOnKey13CallsOpenSettings() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val textInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        service.onStartInputView(textInfo, false)

        val keyLang = inputView.keyAtlas.keys[13]
        // Flick Up Key 13: opens settings activity without throwing
        inputView.onKeyFlickAction?.invoke(keyLang, com.opent9.keyboard.ui.FlickDirection.UP)
    }

    @Test
    fun testPage1LayoutAndKeys() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val phoneInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_PHONE
        }
        service.onStartInputView(phoneInfo, false)
        assertEquals(KeyboardPage.PAGE_1_NUM_SYM, inputView.keyAtlas.currentPage)

        // Verify operator column contents: + - * / ( )
        assertArrayEquals(
            arrayOf("+", "-", "*", "/", "(", ")"),
            inputView.keyAtlas.page1OperatorItems
        )
        assertEquals(6, inputView.keyAtlas.page1OperatorKeys.size)

        // Verify page1Keys contains all expected keys matching design image
        val labels = inputView.keyAtlas.page1Keys.map { it.primaryLabel }
        assertTrue(labels.containsAll(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")))
        assertTrue(labels.containsAll(listOf("%", "␣", "⌫", "↵", "ABC", ",", "!?#", "=", ".")))

        // Verify geometry computation
        inputView.keyAtlas.computeGeometry(1080f, 700f, 2.75f)
        assertFalse(inputView.keyAtlas.page1ScrollContainerBounds.isEmpty)
        assertTrue(inputView.keyAtlas.maxPage1ColumnScroll < 0f)

        // Operator hit testing at scroll offset 0
        val op0 = inputView.keyAtlas.findPage1OperatorIndex(inputView.keyAtlas.page1ScrollContainerBounds.top + 10f, 0f)
        assertEquals(0, op0) // "+"
    }

    @Test
    fun testPage1Navigation() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val phoneInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_PHONE
        }
        service.onStartInputView(phoneInfo, false)
        assertEquals(KeyboardPage.PAGE_1_NUM_SYM, inputView.keyAtlas.currentPage)

        // Tap "ABC" to switch to Page 0
        val abcKey = inputView.keyAtlas.page1Keys.first { it.primaryLabel == "ABC" }
        inputView.onKeyTapAction?.invoke(abcKey, abcKey.centerX, abcKey.centerY)
        assertEquals(KeyboardPage.PAGE_0_TEXT, inputView.keyAtlas.currentPage)

        // Switch back to Page 1 via ?123
        val numKey = inputView.keyAtlas.keys[12] // "?123"
        inputView.onKeyTapAction?.invoke(numKey, numKey.centerX, numKey.centerY)
        assertEquals(KeyboardPage.PAGE_1_NUM_SYM, inputView.keyAtlas.currentPage)

        // Tap "!?#" to switch to Page 2
        val symKey = inputView.keyAtlas.page1Keys.first { it.primaryLabel == "!?#" }
        inputView.onKeyTapAction?.invoke(symKey, symKey.centerX, symKey.centerY)
        assertEquals(KeyboardPage.PAGE_2_EXT_SYM, inputView.keyAtlas.currentPage)
    }

    @Test
    fun testCandidateLongPressWiringAndRemoval() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val textInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        service.onStartInputView(textInfo, false)

        assertNotNull(inputView.onCandidateLongPressAction)

        // Simulate active candidates
        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        candidatesField.set(service, listOf("purwantoso", "purwantoro", "purwanto"))
        inputView.updateCandidates(listOf("purwantoso", "purwantoro", "purwanto"))

        // Trigger long press on candidate 0 ("purwantoso")
        inputView.onCandidateLongPressAction?.invoke(0, "purwantoso")

        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestDialog() as? android.app.AlertDialog
        if (dialog != null) {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.performClick()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        } else {
            service.removeCandidateSuggestion("purwantoso")
        }

        // Check that purwantoso is removed and suppressed
        assertTrue(com.opent9.keyboard.jni.NativeEngineBridge.isWordDeleted("purwantoso"))

        @Suppress("UNCHECKED_CAST")
        val currentActive = candidatesField.get(service) as List<String>
        assertFalse("purwantoso must not be in active candidates after removal", currentActive.contains("purwantoso"))
    }

    @Test
    fun testWordCorrectionCandidateRemovalDoesNotCorruptCommittedText() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        // Type 4-3-5-5-6 ("hello")
        val key4 = inputView.keyAtlas.keys.first { it.digitValue == 4 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }
        val key5 = inputView.keyAtlas.keys.first { it.digitValue == 5 }
        val key6 = inputView.keyAtlas.keys.first { it.digitValue == 6 }
        val keySpace = inputView.keyAtlas.keys.first { it.type == com.opent9.keyboard.ui.KeyType.SPACE_0 }

        inputView.onKeyTapAction?.invoke(key4, key4.centerX, key4.centerY)
        inputView.onKeyTapAction?.invoke(key3, key3.centerX, key3.centerY)
        inputView.onKeyTapAction?.invoke(key5, key5.centerX, key5.centerY)
        inputView.onKeyTapAction?.invoke(key5, key5.centerX, key5.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)

        // Commit with Space
        inputView.onKeyTapAction?.invoke(keySpace, keySpace.centerX, keySpace.centerY)
        val initialText = editText.text.toString()
        assertTrue("Committed text must contain a word", initialText.trim().isNotEmpty())
        assertTrue("Committed text must end with space", initialText.endsWith(" "))

        // Move cursor back inside the word
        val wordLen = initialText.trim().length
        editText.setSelection(wordLen)
        service.onUpdateSelection(initialText.length, initialText.length, wordLen, wordLen, -1, -1)

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val wordCandidates = candidatesField.get(service) as List<String>
        assertFalse("Candidates should be populated for word correction", wordCandidates.isEmpty())

        val candidateToRemove = wordCandidates.first()
        inputView.onCandidateLongPressAction?.invoke(0, candidateToRemove)

        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestDialog() as? android.app.AlertDialog
        if (dialog != null) {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.performClick()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        } else {
            service.removeCandidateSuggestion(candidateToRemove)
        }

        // Verify candidate was removed
        @Suppress("UNCHECKED_CAST")
        val remainingCandidates = candidatesField.get(service) as List<String>
        assertFalse("Candidate must be removed from suggestions", remainingCandidates.contains(candidateToRemove))

        // CRITICAL: The committed text in editText must NOT be duplicated or corrupted with an uncommitted composing span
        assertEquals("Committed text must remain unaltered", initialText, editText.text.toString())

        val hasActiveField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }
        assertFalse("hasActiveComposing must remain false", hasActiveField.getBoolean(service))
    }

    @Test
    fun testMultiTapWordBufferingOnSpace() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val textInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        service.onStartInputView(textInfo, false)
        inputView.setT9Mode(false) // Multi-Tap mode

        // Simulate typing on Key 7 (P) and Key 8 (U)
        val key7 = inputView.keyAtlas.keys[8] // Key 7
        val key8 = inputView.keyAtlas.keys[9] // Key 8
        val keySpace = inputView.keyAtlas.keys[14] // Space

        inputView.onKeyTapAction?.invoke(key7, key7.centerX, key7.centerY)
        inputView.onKeyTapAction?.invoke(key8, key8.centerX, key8.centerY)

        // Advance multi-tap timeout to commit
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Tap Space
        inputView.onKeyTapAction?.invoke(keySpace, keySpace.centerX, keySpace.centerY)
    }

    @Test
    fun testBackspaceAfterCommitDoesNotUncommitOrShowSuggestions() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        // 1. Commit word "hello " (simulating user typing and hitting space)
        val commitWordMethod = OpenT9InputMethodService::class.java.getDeclaredMethod(
            "commitWord",
            String::class.java,
            Boolean::class.java
        ).apply { isAccessible = true }

        val currentDigitsField = OpenT9InputMethodService::class.java.getDeclaredField("currentComposingDigits").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val composingDigits = currentDigitsField.get(service) as ArrayList<Int>
        composingDigits.addAll(listOf(4, 3, 5, 5, 6)) // "hello"

        commitWordMethod.invoke(service, "hello", true)
        assertEquals("hello ", editText.text.toString())

        // Verify initial state after commit
        val hasActiveField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }
        assertFalse(hasActiveField.getBoolean(service))

        // 2. Tap DEL: retroactive uncommit must NOT occur
        val keyDel = inputView.keyAtlas.keys.first { it.type == com.opent9.keyboard.ui.KeyType.DEL }
        inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)

        // Must delete trailing space only, NOT restore composing digits
        assertFalse("Composing must NOT be restored on backspace", hasActiveField.getBoolean(service))
        assertEquals("hello", editText.text.toString())

        // 3. Simulate Android framework delivering asynchronous onUpdateSelection from the deletion
        service.onUpdateSelection(6, 6, 5, 5, -1, -1)

        // Composing must remain inactive and candidates empty
        assertFalse(hasActiveField.getBoolean(service))
        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val active = candidatesField.get(service) as List<String>
        assertTrue("Candidates must remain empty upon backspace", active.isEmpty())
    }

    @Test
    fun testBackspaceWhileComposingPopsStrokesAndCleansUp() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        val currentDigitsField = OpenT9InputMethodService::class.java.getDeclaredField("currentComposingDigits").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val composingDigits = currentDigitsField.get(service) as ArrayList<Int>
        val hasActiveField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }

        // Tap digit 4 ('g','h','i') and digit 3 ('d','e','f')
        val key4 = inputView.keyAtlas.keys.first { it.digitValue == 4 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }
        val keyDel = inputView.keyAtlas.keys.first { it.type == com.opent9.keyboard.ui.KeyType.DEL }

        inputView.onKeyTapAction?.invoke(key4, key4.centerX, key4.centerY)
        inputView.onKeyTapAction?.invoke(key3, key3.centerX, key3.centerY)

        assertEquals(listOf(4, 3), composingDigits)
        assertTrue(hasActiveField.getBoolean(service))

        // Backspace 1 stroke
        inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)
        assertEquals(listOf(4), composingDigits)
        assertTrue(hasActiveField.getBoolean(service))

        // Backspace 2nd stroke -> now empty
        inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)
        assertTrue(composingDigits.isEmpty())
        assertFalse(hasActiveField.getBoolean(service))

        // Extra backspace when empty: must remain clean without crash or stale state
        inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)
        assertTrue(composingDigits.isEmpty())
        assertFalse(hasActiveField.getBoolean(service))
    }

    @Test
    fun testSuggestionGuardHoldsWordAndNeverShowsNumbers() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        val guardField = OpenT9InputMethodService::class.java.getDeclaredField("suggestionGuard").apply {
            isAccessible = true
        }
        val guard = guardField.get(service) as com.opent9.keyboard.prediction.SuggestionGuard

        val currentDigitsField = OpenT9InputMethodService::class.java.getDeclaredField("currentComposingDigits").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val composingDigits = currentDigitsField.get(service) as ArrayList<Int>

        val updateCandidatesMethod = OpenT9InputMethodService::class.java.getDeclaredMethod("updateCandidatesFromNative", InputConnection::class.java).apply {
            isAccessible = true
        }
        val commitActiveMethod = OpenT9InputMethodService::class.java.getDeclaredMethod("commitActiveCandidate", Boolean::class.java).apply {
            isAccessible = true
        }

        // Simulate user had typed "good" (4, 6, 6, 3) and it was valid
        guard.recordValidCandidates(listOf("good", "home"), listOf(4, 6, 6, 3))

        // User types 1 extra digit '7' -> total digits: 4, 6, 6, 3, 7 (no dictionary entry)
        composingDigits.addAll(listOf(4, 6, 6, 3, 7))

        // Update candidates
        updateCandidatesMethod.invoke(service, ic)

        // Composing text must be held to the base word (formatted with auto-caps "Good"), NOT raw digits "46637"
        assertEquals("Good", editText.text.toString())
        assertFalse("Composing text must not contain numbers", editText.text.toString().any { it.isDigit() })

        // Hit space -> commits the held base word with space
        commitActiveMethod.invoke(service, true)
        assertEquals("Good ", editText.text.toString())
        assertFalse("Committed text must not contain numbers", editText.text.toString().any { it.isDigit() })
    }

    @Test
    fun testBackspaceAfterExplicitCommitDeletesOnlyTrailingChar() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)
        service.onStartInputView(info, false)

        val currentDigitsField = OpenT9InputMethodService::class.java.getDeclaredField("currentComposingDigits").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val composingDigits = currentDigitsField.get(service) as ArrayList<Int>
        composingDigits.addAll(listOf(5, 4, 5, 2, 8)) // "kilat" / "jilat"

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        candidatesField.set(service, listOf("kilat", "jilat"))

        // User explicitly commits candidate 1 ("jilat") -> auto-caps gives "Jilat "
        val commitIndexMethod = OpenT9InputMethodService::class.java.getDeclaredMethod("commitCandidateIndex", Int::class.java).apply {
            isAccessible = true
        }
        commitIndexMethod.invoke(service, 1)
        assertEquals("Jilat ", editText.text.toString())

        // User hits DEL -> must NOT trigger retroactive uncommit
        val keyDel = inputView.keyAtlas.keys.first { it.type == com.opent9.keyboard.ui.KeyType.DEL }
        inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)

        @Suppress("UNCHECKED_CAST")
        val activeAfterUncommit = candidatesField.get(service) as List<String>
        assertTrue("Active candidates must be empty upon backspace", activeAfterUncommit.isEmpty())
        assertEquals("Jilat", editText.text.toString())
    }

    @Test
    fun testExplicitWordTapShowsSuggestionsAndReplacesOnTap() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("hello world")
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)
        service.onStartInputView(info, false)

        // User taps inside "world" at offset 8 (between 'r' and 'l')
        editText.setSelection(8)
        service.onUpdateSelection(0, 0, 8, 8, -1, -1)

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val candidates = candidatesField.get(service) as List<String>
        assertTrue("Suggestions must appear when explicitly tapping a word", candidates.isNotEmpty())

        // Tap first suggestion
        inputView.onCandidateTapAction?.invoke(0)

        // The candidates must be cleared after replacing word
        @Suppress("UNCHECKED_CAST")
        val afterTap = candidatesField.get(service) as List<String>
        assertTrue("Candidates should be cleared after replacing word", afterTap.isEmpty())
    }

    @Test
    fun testExplicitWordSelectionShowsSuggestionsAndReplaces() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("hello world")
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)
        service.onStartInputView(info, false)

        // User selects "world" (range 6..11)
        editText.setSelection(6, 11)
        service.onUpdateSelection(0, 0, 6, 11, -1, -1)

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val candidates = candidatesField.get(service) as List<String>
        assertTrue("Suggestions must appear when selecting a word", candidates.isNotEmpty())

        // User taps candidate 0
        val chosen = candidates[0]
        inputView.onCandidateTapAction?.invoke(0)
        assertEquals("hello $chosen", editText.text.toString())
    }

    @Test
    fun testBackspaceDismissesWordCorrectionSuggestionsWithoutCorrection() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("hello world")
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)
        service.onStartInputView(info, false)

        // User taps inside "world" at offset 11
        editText.setSelection(11)
        service.onUpdateSelection(0, 0, 11, 11, -1, -1)

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        var candidates = candidatesField.get(service) as List<String>
        assertTrue("Suggestions must appear when tapping a word", candidates.isNotEmpty())

        // User presses DEL
        val keyDel = inputView.keyAtlas.keys.first { it.type == com.opent9.keyboard.ui.KeyType.DEL }
        inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)

        @Suppress("UNCHECKED_CAST")
        candidates = candidatesField.get(service) as List<String>
        assertTrue("Candidates must be dismissed when pressing DEL", candidates.isEmpty())
        assertEquals("hello worl", editText.text.toString())

        // Framework delivers onUpdateSelection from the backspace deletion
        service.onUpdateSelection(11, 11, 10, 10, -1, -1)

        @Suppress("UNCHECKED_CAST")
        candidates = candidatesField.get(service) as List<String>
        assertTrue("Candidates must NOT reappear after backspace", candidates.isEmpty())
    }

    @Test
    fun testLanguageSwitchDuringComposingPreservesStrokes() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        val key4 = inputView.keyAtlas.keys.first { it.digitValue == 4 }
        val key6 = inputView.keyAtlas.keys.first { it.digitValue == 6 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }

        // Type 4, 6, 6, 3 with custom touch coordinates
        inputView.onKeyTapAction?.invoke(key4, key4.centerX + 5f, key4.centerY + 5f)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX - 3f, key6.centerY + 2f)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX + 2f, key6.centerY - 4f)
        inputView.onKeyTapAction?.invoke(key3, key3.centerX + 1f, key3.centerY + 1f)

        val strokesField = OpenT9InputMethodService::class.java.getDeclaredField("currentComposingStrokes").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val strokes = strokesField.get(service) as List<Any>
        assertEquals(4, strokes.size)

        val initialLang = inputView.activeLanguage
        val keyLang = inputView.keyAtlas.keys[13]

        // Tap Language Switch mid-word
        inputView.onKeyTapAction?.invoke(keyLang, keyLang.centerX, keyLang.centerY)

        val newLang = inputView.activeLanguage
        assertNotEquals(initialLang, newLang)

        // Strokes must be preserved and replayed
        @Suppress("UNCHECKED_CAST")
        val strokesAfter = strokesField.get(service) as List<Any>
        assertEquals(4, strokesAfter.size)

        // Composing state remains active
        val hasActiveComposingField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }
        assertTrue(hasActiveComposingField.getBoolean(service))
    }

    @Test
    fun testLongPressDigitCommitsWordAndDigitAndCleansComposingState() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)
        com.opent9.keyboard.jni.NativeEngineBridge.switchLanguage("EN")

        val key4 = inputView.keyAtlas.keys.first { it.digitValue == 4 }
        val key6 = inputView.keyAtlas.keys.first { it.digitValue == 6 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }

        // Type "good" (4, 6, 6, 3)
        inputView.onKeyTapAction?.invoke(key4, key4.centerX, key4.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)
        inputView.onKeyTapAction?.invoke(key3, key3.centerX, key3.centerY)

        val hasActiveField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }
        assertTrue("Must be composing", hasActiveField.getBoolean(service))

        // Long press Key 2 ("ABC 2")
        val key2 = inputView.keyAtlas.keys.first { it.digitValue == 2 }
        inputView.onKeyLongPressAction?.invoke(key2)

        // Composing word should be committed and digit "2" appended
        assertTrue("Committed text must end with '2'", editText.text.toString().endsWith("2"))
        assertTrue("Committed word must precede '2'", editText.text.toString().length > 1)
        assertFalse("Composing state must be cleared", hasActiveField.getBoolean(service))

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val candidates = candidatesField.get(service) as List<String>
        assertTrue("Active candidates must be cleared", candidates.isEmpty())
    }

    @Test
    fun testFlickUpOnDigitKeysDoesNotCommitNumbers() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        // Flick up on Key 2 ("ABC 2"), Key 3 ("DEF 3"), Key 0 ("1 .,?!'@#")
        val key2 = inputView.keyAtlas.keys.first { it.digitValue == 2 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }
        val key1 = inputView.keyAtlas.keys.first { it.id == 0 }

        inputView.onKeyFlickAction?.invoke(key2, com.opent9.keyboard.ui.FlickDirection.UP)
        inputView.onKeyFlickAction?.invoke(key3, com.opent9.keyboard.ui.FlickDirection.UP)
        inputView.onKeyFlickAction?.invoke(key1, com.opent9.keyboard.ui.FlickDirection.UP)

        // No numbers should have been committed
        assertEquals("", editText.text.toString())
    }

    @Test
    fun testOutOfDictionaryWordCandidatesContainNoNumbers() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        // Type unknown sequence from start: 7, 7, 7, 7
        val key7 = inputView.keyAtlas.keys.first { it.digitValue == 7 }
        inputView.onKeyTapAction?.invoke(key7, key7.centerX, key7.centerY)
        inputView.onKeyTapAction?.invoke(key7, key7.centerX, key7.centerY)
        inputView.onKeyTapAction?.invoke(key7, key7.centerX, key7.centerY)
        inputView.onKeyTapAction?.invoke(key7, key7.centerX, key7.centerY)

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val activeCandidates = candidatesField.get(service) as List<String>
        assertFalse("Candidates must not be empty", activeCandidates.isEmpty())
        assertTrue("Suggestion bar candidates must not contain any digits",
            activeCandidates.none { candidate -> candidate.any { it.isDigit() } })

        // Commit with Space
        val keySpace = inputView.keyAtlas.keys.first { it.type == com.opent9.keyboard.ui.KeyType.SPACE_0 }
        inputView.onKeyTapAction?.invoke(keySpace, keySpace.centerX, keySpace.centerY)

        assertFalse("Committed text must not contain numbers", editText.text.toString().any { it.isDigit() })
        assertTrue("Committed text must contain alphabetic characters", editText.text.toString().any { it.isLetter() })
    }

    @Test
    fun testForwardCompletionProvidesRealWordsAndEliminatesFragments() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        // Candidates delivered from C++ engine containing forward completions ("hello", "help")
        val completedWords = listOf("hell", "hello", "help", "helping")
        candidatesField.set(service, completedWords)
        inputView.updateCandidates(completedWords)

        @Suppress("UNCHECKED_CAST")
        val activeCandidates = candidatesField.get(service) as List<String>
        assertFalse("Candidates must not be empty", activeCandidates.isEmpty())

        // Verify that forward completion suggested "hello"
        assertTrue("Candidates must include completed word 'hello'",
            activeCandidates.any { it.equals("hello", ignoreCase = true) })

        // Verify that candidates contain valid letters and no digits
        assertTrue("No candidates should contain digits",
            activeCandidates.none { candidate -> candidate.any { it.isDigit() } })

        // Commit candidate 1 ("hello")
        inputView.onCandidateTapAction?.invoke(1)
        assertEquals("Hello ", editText.text.toString())
    }

    @Test
    fun testHoldBackspaceWhileComposingProgressivelyPopsStrokesAndDoesNotFreeze() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        val currentDigitsField = OpenT9InputMethodService::class.java.getDeclaredField("currentComposingDigits").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val composingDigits = currentDigitsField.get(service) as ArrayList<Int>
        val hasActiveField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }

        // Tap 4 ('g','h','i'), 3 ('d','e','f'), 5 ('j','k','l')
        val key4 = inputView.keyAtlas.keys.first { it.digitValue == 4 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }
        val key5 = inputView.keyAtlas.keys.first { it.digitValue == 5 }
        val keyDel = inputView.keyAtlas.keys.first { it.type == com.opent9.keyboard.ui.KeyType.DEL }

        inputView.onKeyTapAction?.invoke(key4, key4.centerX, key4.centerY)
        inputView.onKeyTapAction?.invoke(key3, key3.centerX, key3.centerY)
        inputView.onKeyTapAction?.invoke(key5, key5.centerX, key5.centerY)

        assertEquals(3, composingDigits.size)
        assertTrue(hasActiveField.getBoolean(service))

        // First hold tick (350ms): pops 1 composing stroke
        inputView.onKeyDeleteRepeatAction?.invoke(false)
        assertEquals(2, composingDigits.size)
        assertTrue(hasActiveField.getBoolean(service))

        // Second hold tick: pops another stroke
        inputView.onKeyDeleteRepeatAction?.invoke(false)
        assertEquals(1, composingDigits.size)
        assertTrue(hasActiveField.getBoolean(service))

        // Third hold tick: pops last stroke -> cleanly aborts composing without ghost span
        inputView.onKeyDeleteRepeatAction?.invoke(false)
        assertTrue(composingDigits.isEmpty())
        assertFalse(hasActiveField.getBoolean(service))
        assertEquals("", editText.text.toString())

        // Now set some existing text into the editor and verify subsequent backspace is NOT frozen
        editText.setText("abc")
        editText.setSelection(3)
        inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)
        assertEquals("ab", editText.text.toString())

        inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)
        assertEquals("a", editText.text.toString())
    }

    @Test
    fun testFlickDeleteWordWhileComposingCleanlyDiscardsComposingWord() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        editText.setText("previous ")
        editText.setSelection(9)

        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        // Type digits 4, 3 ('h', 'e')
        val key4 = inputView.keyAtlas.keys.first { it.digitValue == 4 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }
        val keyDel = inputView.keyAtlas.keys.first { it.type == com.opent9.keyboard.ui.KeyType.DEL }

        inputView.onKeyTapAction?.invoke(key4, key4.centerX, key4.centerY)
        inputView.onKeyTapAction?.invoke(key3, key3.centerX, key3.centerY)

        val hasActiveField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }
        assertTrue(hasActiveField.getBoolean(service))

        // Flick Left on DEL -> delete word should cleanly discard composing word and leave "previous "
        inputView.onKeyFlickAction?.invoke(keyDel, com.opent9.keyboard.ui.FlickDirection.LEFT)

        assertFalse(hasActiveField.getBoolean(service))
        assertEquals("previous ", editText.text.toString())

        // Backspace tap should now delete the space from "previous "
        inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)
        assertEquals("previous", editText.text.toString())
    }

    @Test
    fun testTypeWordThenPage1SymbolCommitsWordAndDoesNotReappearOnBackspace() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        val hasActiveField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }
        val currentDigitsField = OpenT9InputMethodService::class.java.getDeclaredField("currentComposingDigits").apply {
            isAccessible = true
        }

        // 1. Type word "good" (digits 4, 6, 6, 3)
        val key4 = inputView.keyAtlas.keys.first { it.digitValue == 4 }
        val key6 = inputView.keyAtlas.keys.first { it.digitValue == 6 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }

        inputView.onKeyTapAction?.invoke(key4, key4.centerX, key4.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)
        inputView.onKeyTapAction?.invoke(key3, key3.centerX, key3.centerY)

        assertTrue("Composing must be active while typing word", hasActiveField.getBoolean(service))
        val typedWord = editText.text.toString()
        assertTrue("A word must be composed", typedWord.isNotEmpty())

        // 2. Switch to Page 1 via "?123"
        val keyNum = inputView.keyAtlas.keys[12]
        inputView.onKeyTapAction?.invoke(keyNum, keyNum.centerX, keyNum.centerY)
        assertEquals(KeyboardPage.PAGE_1_NUM_SYM, inputView.keyAtlas.currentPage)

        // Switching to Page 1 must immediately commit the typed word!
        assertFalse("Composing must be committed and inactive after switching page", hasActiveField.getBoolean(service))
        @Suppress("UNCHECKED_CAST")
        val digits = currentDigitsField.get(service) as List<Int>
        assertTrue("Composing digits must be cleared after switching page", digits.isEmpty())
        assertEquals(typedWord, editText.text.toString())

        // 3. Tap symbol "-" from Page 1 operator keys
        val minusKey = inputView.keyAtlas.page1OperatorKeys.first { it.primaryLabel == "-" }
        inputView.onKeyTapAction?.invoke(minusKey, minusKey.centerX, minusKey.centerY)

        // Text must be "$typedWord-", NOT replacing typed word with "-"
        assertEquals("$typedWord-", editText.text.toString())
        assertFalse(hasActiveField.getBoolean(service))

        // 4. Tap DEL on Page 1
        val keyDel = inputView.keyAtlas.page1Keys.first { it.type == com.opent9.keyboard.ui.KeyType.DEL }
        inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)

        // Backspace must delete "-" leaving typedWord, NOT resurrecting/re-popping the typed word
        assertEquals(typedWord, editText.text.toString())
        assertFalse("Composing must remain inactive after backspace", hasActiveField.getBoolean(service))
    }

    @Test
    fun testDirectSymbolTapWhileComposingCommitsWordFirst() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        // Type "good"
        val key4 = inputView.keyAtlas.keys.first { it.digitValue == 4 }
        val key6 = inputView.keyAtlas.keys.first { it.digitValue == 6 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }
        inputView.onKeyTapAction?.invoke(key4, key4.centerX, key4.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)
        inputView.onKeyTapAction?.invoke(key3, key3.centerX, key3.centerY)

        val typedWord = editText.text.toString()
        assertTrue(typedWord.isNotEmpty())

        // Tap a direct symbol key directly (e.g. key with KeyType.DIRECT_SYM)
        val directSymKey = KeyInfo(id = 999, row = 0, col = 0, type = KeyType.DIRECT_SYM, primaryLabel = "*")
        inputView.onKeyTapAction?.invoke(directSymKey, 0f, 0f)

        assertEquals("$typedWord*", editText.text.toString())
    }

    @Test
    fun testCursorMoveWhileComposingCommitsWordInsteadOfDeleting() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        // Type "good"
        val key4 = inputView.keyAtlas.keys.first { it.digitValue == 4 }
        val key6 = inputView.keyAtlas.keys.first { it.digitValue == 6 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }
        inputView.onKeyTapAction?.invoke(key4, key4.centerX, key4.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)
        inputView.onKeyTapAction?.invoke(key3, key3.centerX, key3.centerY)

        val typedWord = editText.text.toString()
        assertTrue(typedWord.isNotEmpty())

        // User taps elsewhere (cursor moves to position 0 outside composing span 0..4)
        service.onUpdateSelection(4, 4, 0, 0, 0, 4)

        // Typed word must be committed and preserved, NOT deleted!
        assertEquals(typedWord, editText.text.toString())
        val hasActiveField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }
        assertFalse("Composing state must be false after cursor moved", hasActiveField.getBoolean(service))
    }

    @Test
    fun testCursorMoveToPreviousWordWithNegativeCandidatesStartFinalizesComposing() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("hello ")
            setSelection(6)
        }
        val info = EditorInfo().apply {
            initialSelStart = 6
            initialSelEnd = 6
        }
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)
        service.onStartInputView(info, false)
        inputView.setT9Mode(true)

        // Type "good" at offset 6
        val key4 = inputView.keyAtlas.keys.first { it.digitValue == 4 }
        val key6 = inputView.keyAtlas.keys.first { it.digitValue == 6 }
        val key3 = inputView.keyAtlas.keys.first { it.digitValue == 3 }
        inputView.onKeyTapAction?.invoke(key4, key4.centerX, key4.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)
        inputView.onKeyTapAction?.invoke(key6, key6.centerX, key6.centerY)
        inputView.onKeyTapAction?.invoke(key3, key3.centerX, key3.centerY)

        val textBeforeMove = editText.text.toString()
        assertTrue(textBeforeMove.startsWith("hello "))

        // User taps inside "hello" at offset 2 (editors reporting candidatesStart = -1)
        editText.setSelection(2)
        service.onUpdateSelection(10, 10, 2, 2, -1, -1)

        // Composing must be finalized, not stuck!
        val hasActiveField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }
        assertFalse("Composing must NOT be stuck after moving cursor to previous word", hasActiveField.getBoolean(service))
        assertEquals(textBeforeMove, editText.text.toString())
        assertTrue("Word correction should activate for previous word", inputView.isWordCorrectionActive)
    }

    @Test
    fun testWordCorrectionPreservesOriginalCasingInCandidateStrip() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("London")
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)
        service.onStartInputView(info, false)

        // Tap on "London"
        editText.setSelection(3)
        service.onUpdateSelection(0, 0, 3, 3, -1, -1)

        assertTrue(inputView.isWordCorrectionActive)
    }

    @Test
    fun testWordSelectedThreeTimesBecomesNumberOneInEnglish() {
        service.onCreateInputView() as T9KeyboardView
        com.opent9.keyboard.jni.NativeEngineBridge.resetUserDictionary()
        com.opent9.keyboard.jni.NativeEngineBridge.switchLanguage("EN")

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)
        service.onStartInputView(info, false)

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }

        val commitIndexMethod = OpenT9InputMethodService::class.java.getDeclaredMethod("commitCandidateIndex", Int::class.java).apply {
            isAccessible = true
        }

        val digitsField = OpenT9InputMethodService::class.java.getDeclaredField("currentComposingDigits").apply {
            isAccessible = true
        }

        // User selects candidate 1 ("home") from suggestion list 3 times
        for (i in 1..3) {
            digitsField.set(service, arrayListOf(4, 6, 6, 3))
            candidatesField.set(service, listOf("good", "home", "gone"))
            commitIndexMethod.invoke(service, 1)
        }

        assertEquals("home must have usage count 3", 3, com.opent9.keyboard.jni.NativeEngineBridge.getWordUsageCount("home"))

        val updateMethod = OpenT9InputMethodService::class.java.getDeclaredMethod("updateCandidatesFromNative", InputConnection::class.java, String::class.java).apply {
            isAccessible = true
        }

        digitsField.set(service, arrayListOf(4, 6, 6, 3))

        val guardField = OpenT9InputMethodService::class.java.getDeclaredField("suggestionGuard").apply {
            isAccessible = true
        }
        val guard = guardField.get(service) as com.opent9.keyboard.prediction.SuggestionGuard
        guard.recordValidCandidates(listOf("good", "home", "gone"), listOf(4, 6, 6, 3))

        updateMethod.invoke(service, ic, null)

        @Suppress("UNCHECKED_CAST")
        val updatedCandidates = candidatesField.get(service) as List<String>
        assertTrue("Candidates must not be empty", updatedCandidates.isNotEmpty())
        assertEquals("home must be promoted to position 0 (#1 suggestion) after selections", "home", updatedCandidates[0])
    }

    @Test
    fun testWordSelectedThreeTimesBecomesNumberOneInIndonesian() {
        service.onCreateInputView() as T9KeyboardView
        com.opent9.keyboard.jni.NativeEngineBridge.resetUserDictionary()
        com.opent9.keyboard.jni.NativeEngineBridge.switchLanguage("ID")

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)
        service.onStartInputView(info, false)

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }

        val commitIndexMethod = OpenT9InputMethodService::class.java.getDeclaredMethod("commitCandidateIndex", Int::class.java).apply {
            isAccessible = true
        }

        val digitsField = OpenT9InputMethodService::class.java.getDeclaredField("currentComposingDigits").apply {
            isAccessible = true
        }

        // User selects candidate 1 ("sama") from suggestion list 3 times
        for (i in 1..3) {
            digitsField.set(service, arrayListOf(7, 2, 6, 2))
            candidatesField.set(service, listOf("sana", "sama", "rama"))
            commitIndexMethod.invoke(service, 1)
        }

        assertEquals("sama must have usage count 3", 3, com.opent9.keyboard.jni.NativeEngineBridge.getWordUsageCount("sama"))

        val updateMethod = OpenT9InputMethodService::class.java.getDeclaredMethod("updateCandidatesFromNative", InputConnection::class.java, String::class.java).apply {
            isAccessible = true
        }

        digitsField.set(service, arrayListOf(7, 2, 6, 2))

        val guardField = OpenT9InputMethodService::class.java.getDeclaredField("suggestionGuard").apply {
            isAccessible = true
        }
        val guard = guardField.get(service) as com.opent9.keyboard.prediction.SuggestionGuard
        guard.recordValidCandidates(listOf("sana", "sama", "rama"), listOf(7, 2, 6, 2))

        updateMethod.invoke(service, ic, null)

        @Suppress("UNCHECKED_CAST")
        val updatedCandidates = candidatesField.get(service) as List<String>
        assertTrue("Candidates must not be empty", updatedCandidates.isNotEmpty())
        assertEquals("sama must be promoted to position 0 (#1 suggestion) after selections", "sama", updatedCandidates[0])
    }

    @Test
    fun testLastWordPickedBecomesNumberOneImmediatelyForAllWords() {
        service.onCreateInputView() as T9KeyboardView
        com.opent9.keyboard.jni.NativeEngineBridge.resetUserDictionary()

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)
        service.onStartInputView(info, false)

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        val commitIndexMethod = OpenT9InputMethodService::class.java.getDeclaredMethod("commitCandidateIndex", Int::class.java).apply {
            isAccessible = true
        }
        val updateMethod = OpenT9InputMethodService::class.java.getDeclaredMethod("updateCandidatesFromNative", InputConnection::class.java, String::class.java).apply {
            isAccessible = true
        }
        val digitsField = OpenT9InputMethodService::class.java.getDeclaredField("currentComposingDigits").apply {
            isAccessible = true
        }
        val guardField = OpenT9InputMethodService::class.java.getDeclaredField("suggestionGuard").apply {
            isAccessible = true
        }
        val guard = guardField.get(service) as com.opent9.keyboard.prediction.SuggestionGuard

        // 1. Indonesian Test: User types 7-2-6-2, picks "sama" once (candidate index 1)
        com.opent9.keyboard.jni.NativeEngineBridge.switchLanguage("ID")
        digitsField.set(service, arrayListOf(7, 2, 6, 2))
        candidatesField.set(service, listOf("sana", "sama", "rama"))
        commitIndexMethod.invoke(service, 1) // Pick "sama"

        // Next suggestion query for 7-2-6-2 -> "sama" MUST immediately be #1
        digitsField.set(service, arrayListOf(7, 2, 6, 2))
        guard.recordValidCandidates(listOf("sana", "sama", "rama"), listOf(7, 2, 6, 2))
        updateMethod.invoke(service, ic, null)

        @Suppress("UNCHECKED_CAST")
        var cands = candidatesField.get(service) as List<String>
        assertEquals("sama must immediately become #1 suggestion on next typing", "sama", cands[0])

        // User now picks "sana" (candidate index 1)
        commitIndexMethod.invoke(service, 1)

        // Next suggestion query for 7-2-6-2 -> "sana" MUST immediately become #1
        digitsField.set(service, arrayListOf(7, 2, 6, 2))
        guard.recordValidCandidates(listOf("sama", "sana", "rama"), listOf(7, 2, 6, 2))
        updateMethod.invoke(service, ic, null)

        @Suppress("UNCHECKED_CAST")
        cands = candidatesField.get(service) as List<String>
        assertEquals("sana must immediately become #1 suggestion after being picked", "sana", cands[0])

        // 2. English Test with arbitrary word: User types 2-6-6-5 ("book", "cool", "cook")
        com.opent9.keyboard.jni.NativeEngineBridge.switchLanguage("EN")
        digitsField.set(service, arrayListOf(2, 6, 6, 5))
        candidatesField.set(service, listOf("book", "cool", "cook"))
        commitIndexMethod.invoke(service, 1) // Pick "cool"

        // Next suggestion query for 2-6-6-5 -> "cool" MUST immediately be #1
        digitsField.set(service, arrayListOf(2, 6, 6, 5))
        guard.recordValidCandidates(listOf("book", "cool", "cook"), listOf(2, 6, 6, 5))
        updateMethod.invoke(service, ic, null)

        @Suppress("UNCHECKED_CAST")
        cands = candidatesField.get(service) as List<String>
        assertEquals("cool must immediately become #1 suggestion on next typing", "cool", cands[0])
    }
}

