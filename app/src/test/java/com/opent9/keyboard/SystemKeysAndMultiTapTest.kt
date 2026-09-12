package com.opent9.keyboard

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

        // Case 1: Active composing text -> commit candidate and finish composing
        var candidateCommitted = false
        enterHandler.handleEnterTap(ic, null, hasActiveComposing = true) {
            candidateCommitted = true
        }
        assertTrue(candidateCommitted)
        verify { ic.finishComposingText() }

        // Case 2: EditorInfo with ACTION_SEARCH
        val searchEditorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = EditorInfo.TYPE_CLASS_TEXT
        }
        enterHandler.handleEnterTap(ic, searchEditorInfo, hasActiveComposing = false) {}
        verify { ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH) }

        // Case 3: Multi-line text -> commits newline \n
        val multiLineEditorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_NONE
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        }
        enterHandler.handleEnterTap(ic, multiLineEditorInfo, hasActiveComposing = false) {}
        verify { ic.commitText("\n", 1) }
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

        // Case 3: Retroactive Un-commit
        clearMocks(ic)
        delController.recordCommit("good", listOf(4, 6, 6, 3))
        every { ic.getTextBeforeCursor(5, 0) } returns "good "
        var rehydratedDigits: List<Int>? = null
        val uncommitted = delController.handleDeleteTap(ic, hasActiveComposing = false,
            onPopComposingStroke = {},
            onRehydrateDigits = { digits -> rehydratedDigits = digits }
        )
        assertTrue(uncommitted)
        assertEquals(listOf(4, 6, 6, 3), rehydratedDigits)
        verify { ic.deleteSurroundingText(5, 0) }
    }
}
