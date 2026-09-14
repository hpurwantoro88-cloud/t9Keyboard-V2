package com.opent9.keyboard

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.opent9.keyboard.ui.*
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SystemKeysAndMultiTapTest {

    @Test
    fun testShiftStateTransitions() {
        val shift = ShiftController()
        assertEquals(ShiftMode.LOWERCASE, shift.currentMode)
        assertFalse(shift.isCapsLocked())

        // 1st tap: Titlecase
        assertEquals(ShiftMode.TITLECASE, shift.onShiftTap(1000L))
        assertFalse(shift.isCapsLocked())

        // Auto-reset to lower on character commit
        assertEquals(ShiftMode.LOWERCASE, shift.onCharacterCommitted())

        // Tap to Titlecase again
        assertEquals(ShiftMode.TITLECASE, shift.onShiftTap(2000L))

        // 2nd tap within 300ms: Double-tap locks Caps Lock!
        assertEquals(ShiftMode.UPPERCASE, shift.onShiftTap(2200L))
        assertTrue(shift.isCapsLocked())

        // Committing character does NOT reset when locked!
        assertEquals(ShiftMode.UPPERCASE, shift.onCharacterCommitted())
        assertTrue(shift.isCapsLocked())

        // Tapping again unlocks back to lowercase
        assertEquals(ShiftMode.LOWERCASE, shift.onShiftTap(3000L))
        assertFalse(shift.isCapsLocked())
    }

    @Test
    fun testEnterKeyHandling() {
        val enterHandler = EnterKeyHandler()
        val ic = mockk<InputConnection>(relaxed = true)

        // Case 1: Active composing text with Search action -> commits candidate, finishes composing, AND dispatches search
        var candidateCommitted = false
        val composingSearchEditorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = EditorInfo.TYPE_CLASS_TEXT
        }
        enterHandler.handleEnterTap(ic, composingSearchEditorInfo, hasActiveComposing = true) {
            candidateCommitted = true
        }
        assertTrue(candidateCommitted)
        verify { ic.finishComposingText() }
        verify { ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH) }

        // Case 2: Multi-line with ACTION_SEARCH (Google Search web <textarea> pattern)
        clearMocks(ic)
        val multiLineSearchEditorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        }
        enterHandler.handleEnterTap(ic, multiLineSearchEditorInfo, hasActiveComposing = false) {}
        verify { ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH) }
        verify(exactly = 0) { ic.commitText("\n", 1) }

        // Case 3: Multi-line text with no action -> commits newline \n
        clearMocks(ic)
        val multiLineEditorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_NONE
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        }
        enterHandler.handleEnterTap(ic, multiLineEditorInfo, hasActiveComposing = false) {}
        verify { ic.commitText("\n", 1) }

        // Case 4: Field with IME_FLAG_NO_ENTER_ACTION -> suppresses action, commits newline \n
        clearMocks(ic)
        val noEnterActionEditorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        }
        enterHandler.handleEnterTap(ic, noEnterActionEditorInfo, hasActiveComposing = false) {}
        verify(exactly = 0) { ic.performEditorAction(any()) }
        verify { ic.commitText("\n", 1) }

        // Case 5: Single-line unspecified action (Web forms / Terminals) -> sends KeyEvent.KEYCODE_ENTER
        clearMocks(ic)
        val singleLineUnspecifiedEditorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_UNSPECIFIED
            inputType = EditorInfo.TYPE_CLASS_TEXT
        }
        enterHandler.handleEnterTap(ic, singleLineUnspecifiedEditorInfo, hasActiveComposing = false) {}
        verify { ic.sendKeyEvent(match { it.keyCode == KeyEvent.KEYCODE_ENTER && it.action == KeyEvent.ACTION_DOWN }) }
        verify { ic.sendKeyEvent(match { it.keyCode == KeyEvent.KEYCODE_ENTER && it.action == KeyEvent.ACTION_UP }) }

        // Case 6: Custom actionId in EditorInfo
        clearMocks(ic)
        val customActionEditorInfo = EditorInfo().apply {
            actionId = 42
            inputType = EditorInfo.TYPE_CLASS_TEXT
        }
        enterHandler.handleEnterTap(ic, customActionEditorInfo, hasActiveComposing = false) {}
        verify { ic.performEditorAction(42) }

        // Case 7: Force submit with action
        clearMocks(ic)
        enterHandler.handleForceSubmit(ic, multiLineSearchEditorInfo)
        verify { ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH) }

        // Case 8: Force submit without action -> fallback to IME_ACTION_DONE
        clearMocks(ic)
        enterHandler.handleForceSubmit(ic, multiLineEditorInfo)
        verify { ic.performEditorAction(EditorInfo.IME_ACTION_DONE) }
    }

    @Test
    fun testDeleteControllerSurrogateAndRetroactive() {
        val delController = DeleteController()
        val ic = mockk<InputConnection>(relaxed = true)

        // Case 1: Standard character delete
        every { ic.getTextBeforeCursor(2, 0) } returns "a"
        delController.handleDeleteTap(ic, hasActiveComposing = false, {}, {})
        verify { ic.deleteSurroundingText(1, 0) }

        // Case 2: UTF-16 surrogate pair (emoji 😀 = \uD83D\uDE00)
        clearMocks(ic)
        val emojiStr = "\uD83D\uDE00"
        every { ic.getTextBeforeCursor(2, 0) } returns emojiStr
        delController.handleDeleteTap(ic, hasActiveComposing = false, {}, {})
        verify { ic.deleteSurroundingText(2, 0) }

        // Case 3: Retroactive Un-commit disabled by default
        clearMocks(ic)
        delController.recordCommit("good", listOf(4, 6, 6, 3))
        every { ic.getTextBeforeCursor(5, 0) } returns "good "
        every { ic.getTextBeforeCursor(2, 0) } returns " "
        var rehydratedDigits: List<Int>? = null
        val uncommitted = delController.handleDeleteTap(ic, hasActiveComposing = false,
            onPopComposingStroke = {},
            onRehydrateDigits = { digits -> rehydratedDigits = digits }
        )
        assertFalse(uncommitted)
        assertNull(rehydratedDigits)
        verify { ic.deleteSurroundingText(1, 0) }
    }

    @Test
    fun testDeleteSelectedTextInHandleDeleteTap() {
        val delController = DeleteController()
        val ic = mockk<InputConnection>(relaxed = true)

        every { ic.getSelectedText(0) } returns "Select All Words"

        var resetCalled = false
        val uncommitted = delController.handleDeleteTap(
            ic = ic,
            hasActiveComposing = false,
            onPopComposingStroke = {},
            onRehydrateDigits = {},
            onResetComposing = { resetCalled = true }
        )

        assertFalse(uncommitted)
        assertTrue(resetCalled)
        verify { ic.finishComposingText() }
        verify { ic.commitText("", 1) }
        verify(exactly = 0) { ic.deleteSurroundingText(any(), any()) }
    }

    @Test
    fun testDeletePrecedingWordAndClearEntireFieldWithSelection() {
        val delController = DeleteController()
        val ic = mockk<InputConnection>(relaxed = true)

        // Case 1: deletePrecedingWord with active selection
        every { ic.getSelectedText(0) } returns "selected text"
        delController.deletePrecedingWord(ic)
        verify { ic.finishComposingText() }
        verify { ic.commitText("", 1) }

        // Case 2: clearEntireField with active selection
        clearMocks(ic)
        every { ic.getSelectedText(0) } returns "all content"
        every { ic.getTextBeforeCursor(10000, 0) } returns ""
        every { ic.getTextAfterCursor(10000, 0) } returns ""
        delController.clearEntireField(ic)
        verify { ic.beginBatchEdit() }
        verify { ic.finishComposingText() }
        verify { ic.commitText("", 1) }
        verify { ic.endBatchEdit() }
    }

    @Test
    fun testRealEditTextSelectAllAndPartialSelectionDelete() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context)
        val delController = DeleteController()

        // Case 1: Select all word/text and delete
        editText.setText("The quick brown fox")
        editText.selectAll()
        val ic1 = editText.onCreateInputConnection(EditorInfo())
        assertNotNull(ic1)
        delController.handleDeleteTap(ic1, hasActiveComposing = false, {}, {})
        assertEquals("", editText.text.toString())

        // Case 2: Select a specific word in the middle and delete
        editText.setText("Hello world again")
        editText.setSelection(6, 11) // selects "world"
        val ic2 = editText.onCreateInputConnection(EditorInfo())
        assertNotNull(ic2)
        delController.handleDeleteTap(ic2, hasActiveComposing = false, {}, {})
        assertEquals("Hello  again", editText.text.toString())
    }

    @Test
    fun testPage2FlickDualSymbolsAndSystemKeys() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)
        atlas.updatePageLayout(KeyboardPage.PAGE_2_EXT_SYM)
        val controller = PageController(atlas)

        val ic = mockk<InputConnection>(relaxed = true)

        // 1. Dual symbol key 2: [ { } ]
        val key2 = atlas.keys[2] // { }
        controller.handleKeyFlick(key2, FlickDirection.LEFT, ic, {}, {}, {}, {})
        verify { ic.commitText("{", 1) }

        clearMocks(ic)
        controller.handleKeyFlick(key2, FlickDirection.RIGHT, ic, {}, {}, {}, {})
        verify { ic.commitText("}", 1) }

        clearMocks(ic)
        controller.handleKeyFlick(key2, FlickDirection.DOWN, ic, {}, {}, {}, {})
        verify { ic.commitText("{}", 1) }
        verify(exactly = 1) { ic.sendKeyEvent(match { it.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && it.action == KeyEvent.ACTION_DOWN }) }

        // 2. Dual symbol key 9: [ Rp $ ]
        val key9 = atlas.keys[9]
        clearMocks(ic)
        controller.handleKeyFlick(key9, FlickDirection.LEFT, ic, {}, {}, {}, {})
        verify { ic.commitText(any(), 1) }

        clearMocks(ic)
        controller.handleKeyFlick(key9, FlickDirection.RIGHT, ic, {}, {}, {}, {})
        verify { ic.commitText("$", 1) }

        clearMocks(ic)
        controller.handleKeyFlick(key9, FlickDirection.DOWN, ic, {}, {}, {}, {})
        verify { ic.commitText("¢", 1) }

        // 3. System keys on Page 2: DEL flick deletes preceding word
        val keyDel = atlas.keys[3] // DEL
        var deletedWord = false
        controller.handleKeyFlick(keyDel, FlickDirection.LEFT, ic, {}, {}, { deletedWord = true }, {})
        assertTrue(deletedWord)
    }

    @Test
    fun testPage2MultiLayerSymbolsAndFlicks() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)
        atlas.updatePageLayout(KeyboardPage.PAGE_2_EXT_SYM)
        val controller = PageController(atlas)
        val ic = mockk<InputConnection>(relaxed = true)

        // Switch to Layer 1: [ @ #, & *, = ≠, ... ]
        assertTrue(atlas.setSymbolLayer(1))
        assertEquals(1, atlas.activeSymbolLayerIndex)

        // Key 0 on layer 1: [ @ # ]
        val key0 = atlas.keys[0]
        assertEquals("@ #", key0.primaryLabel)
        assertEquals("@", key0.leftGlyph)
        assertEquals("#", key0.rightGlyph)

        clearMocks(ic)
        controller.handleKeyFlick(key0, FlickDirection.LEFT, ic, {}, {}, {}, {})
        verify { ic.commitText("@", 1) }

        clearMocks(ic)
        controller.handleKeyFlick(key0, FlickDirection.RIGHT, ic, {}, {}, {}, {})
        verify { ic.commitText("#", 1) }

        clearMocks(ic)
        controller.handleKeyFlick(key0, FlickDirection.DOWN, ic, {}, {}, {}, {})
        verify { ic.commitText("_", 1) }

        // Key 2 on layer 1: [ = ≠ ]
        val key2 = atlas.keys[2]
        clearMocks(ic)
        controller.handleKeyFlick(key2, FlickDirection.LEFT, ic, {}, {}, {}, {})
        verify { ic.commitText("=", 1) }

        clearMocks(ic)
        controller.handleKeyFlick(key2, FlickDirection.RIGHT, ic, {}, {}, {}, {})
        verify { ic.commitText("≠", 1) }

        clearMocks(ic)
        controller.handleKeyFlick(key2, FlickDirection.DOWN, ic, {}, {}, {}, {})
        verify { ic.commitText("≈", 1) }

        // Switch to Layer 2: [ ° ℃, § ¶, ... ]
        assertTrue(atlas.setSymbolLayer(2))
        val key0_l2 = atlas.keys[0]
        assertEquals("° ℃", key0_l2.primaryLabel)
        clearMocks(ic)
        controller.handleKeyFlick(key0_l2, FlickDirection.LEFT, ic, {}, {}, {}, {})
        verify { ic.commitText("°", 1) }

        clearMocks(ic)
        controller.handleKeyFlick(key0_l2, FlickDirection.DOWN, ic, {}, {}, {}, {})
        verify { ic.commitText("℉", 1) }

        // Switch to Layer 3: [ ¢ ¥, ₩ ₽, ... « » ]
        assertTrue(atlas.setSymbolLayer(3))
        val key8_l3 = atlas.keys[8]
        assertEquals("« »", key8_l3.primaryLabel)
        clearMocks(ic)
        controller.handleKeyFlick(key8_l3, FlickDirection.DOWN, ic, {}, {}, {}, {})
        verify { ic.commitText("«»", 1) }
        verify(exactly = 1) { ic.sendKeyEvent(match { it.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && it.action == KeyEvent.ACTION_DOWN }) }
    }

    @Test
    fun testPage0Key1LabelIncludesAtAndHash() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)
        atlas.updatePageLayout(KeyboardPage.PAGE_0_TEXT)

        val key1 = atlas.keys[0]
        assertEquals(".,?!'@#", key1.primaryLabel)
        assertEquals("1", key1.subLabel)
        assertEquals(KeyType.PUNCT_1, key1.type)
        assertEquals(1, key1.digitValue)
    }

    @Test
    fun testPage0AlphabetPrimaryAndNumberSecondary() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)
        atlas.updatePageLayout(KeyboardPage.PAGE_0_TEXT)

        val expected = mapOf(
            0 to Pair(".,?!'@#", "1"),
            1 to Pair("ABC", "2"),
            2 to Pair("DEF", "3"),
            4 to Pair("GHI", "4"),
            5 to Pair("JKL", "5"),
            6 to Pair("MNO", "6"),
            8 to Pair("PQRS", "7"),
            9 to Pair("TUV", "8"),
            10 to Pair("WXYZ", "9"),
            14 to Pair("␣", "0")
        )

        for ((id, pair) in expected) {
            val key = atlas.keys[id]
            assertEquals("Key $id primaryLabel", pair.first, key.primaryLabel)
            assertEquals("Key $id subLabel", pair.second, key.subLabel)
        }
    }

    @Test
    fun testPage0Key1MultiTapCycleSequence() {
        com.opent9.keyboard.jni.NativeEngineBridge.multiTapReset()
        val expected = listOf('.', ',', '?', '!', '\'', '@', '#', '1', '.')
        var now = 1000L

        for (i in expected.indices) {
            val res = com.opent9.keyboard.jni.NativeEngineBridge.handleMultiTapPress(1, now, 0)
            assertEquals("Expected char at cycle $i", expected[i], res.activeChar)
            assertFalse(res.committedPrev)
            now += 100L
        }

        // Commit after cycle
        val committed = com.opent9.keyboard.jni.NativeEngineBridge.multiTapCommit()
        assertEquals('.', committed)
    }

    @Test
    fun testPage0Button1ServiceTapCyclingAndTimeout() {
        val controller = org.robolectric.Robolectric.buildService(OpenT9InputMethodService::class.java)
        val service = controller.create().get()

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = android.widget.EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo()
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        val inputView = service.onCreateInputView() as T9KeyboardView
        service.onStartInputView(info, false)

        val key1 = inputView.keyAtlas.keys[0]

        // 1st tap: sets composing text "."
        inputView.onKeyTapAction?.invoke(key1, key1.centerX, key1.centerY)
        assertEquals(".", editText.text.toString())

        // 2nd tap: cycles to ","
        inputView.onKeyTapAction?.invoke(key1, key1.centerX, key1.centerY)
        assertEquals(",", editText.text.toString())

        // 3rd tap: cycles to "?"
        inputView.onKeyTapAction?.invoke(key1, key1.centerX, key1.centerY)
        assertEquals("?", editText.text.toString())

        // 4th tap: "!"
        inputView.onKeyTapAction?.invoke(key1, key1.centerX, key1.centerY)
        assertEquals("!", editText.text.toString())

        // 5th tap: "'"
        inputView.onKeyTapAction?.invoke(key1, key1.centerX, key1.centerY)
        assertEquals("'", editText.text.toString())

        // 6th tap: "@"
        inputView.onKeyTapAction?.invoke(key1, key1.centerX, key1.centerY)
        assertEquals("@", editText.text.toString())

        // 7th tap: "#"
        inputView.onKeyTapAction?.invoke(key1, key1.centerX, key1.centerY)
        assertEquals("#", editText.text.toString())

        // Let 600ms timeout elapse to commit
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals("#", editText.text.toString())
    }

    @Test
    fun testKeyDisplayLabelFollowsShiftAndCandidateState() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val keyboardView = T9KeyboardView(context, null)
        keyboardView.keyAtlas.computeGeometry(1080f, 800f, 1f, 0f, 1080f)

        val key1 = keyboardView.keyAtlas.keys[1] // ABC 2
        val key2 = keyboardView.keyAtlas.keys[2] // DEF 3
        val key9 = keyboardView.keyAtlas.keys[10] // WXYZ 9
        val punctKey = keyboardView.keyAtlas.keys[0] // .,?!'@#
        val spaceKey = keyboardView.keyAtlas.keys[14] // ␣

        // 1. ShiftState = 0 (LOWERCASE) -> all digit keys lowercase
        keyboardView.setShiftState(0)
        assertEquals("abc", keyboardView.getDisplayLabel(key1))
        assertEquals("def", keyboardView.getDisplayLabel(key2))
        assertEquals("wxyz", keyboardView.getDisplayLabel(key9))
        assertEquals(".,?!'@#", keyboardView.getDisplayLabel(punctKey))
        assertEquals("␣", keyboardView.getDisplayLabel(spaceKey))

        // 2. ShiftState = 2 (UPPERCASE) -> all digit keys uppercase
        keyboardView.setShiftState(2)
        assertEquals("ABC", keyboardView.getDisplayLabel(key1))
        assertEquals("DEF", keyboardView.getDisplayLabel(key2))
        assertEquals("WXYZ", keyboardView.getDisplayLabel(key9))

        // 3. ShiftState = 1 (TITLECASE)
        // Initial / empty candidates -> Uppercase at word start
        keyboardView.setShiftState(1)
        keyboardView.updateCandidates(emptyList())
        assertEquals("ABC", keyboardView.getDisplayLabel(key1))
        assertEquals("DEF", keyboardView.getDisplayLabel(key2))
        assertEquals("WXYZ", keyboardView.getDisplayLabel(key9))

        // Composing / non-empty candidates -> Lowercase for subsequent letters
        keyboardView.updateCandidates(listOf("hello", "help"))
        assertEquals("abc", keyboardView.getDisplayLabel(key1))
        assertEquals("def", keyboardView.getDisplayLabel(key2))
        assertEquals("wxyz", keyboardView.getDisplayLabel(key9))

        // Reset/commit word -> back to empty candidates -> Uppercase
        keyboardView.updateCandidates(emptyList())
        assertEquals("ABC", keyboardView.getDisplayLabel(key1))
        assertEquals("DEF", keyboardView.getDisplayLabel(key2))
        assertEquals("WXYZ", keyboardView.getDisplayLabel(key9))
    }
}
