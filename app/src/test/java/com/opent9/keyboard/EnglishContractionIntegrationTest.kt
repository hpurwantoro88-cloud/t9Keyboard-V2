package com.opent9.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.opent9.keyboard.prediction.EnglishOrthography
import com.opent9.keyboard.ui.*
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class EnglishContractionIntegrationTest {

    private lateinit var controller: ServiceController<OpenT9InputMethodService>
    private lateinit var service: OpenT9InputMethodService

    @Before
    fun setup() {
        controller = Robolectric.buildService(OpenT9InputMethodService::class.java)
        service = controller.create().get()
    }

    @Test
    fun testShiftFormattingPreservesIInLowercase() {
        service.onCreateInputView()
        val textInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        service.onStartInputView(textInfo, false)

        val method = OpenT9InputMethodService::class.java.getDeclaredMethod("applyShiftFormatting", String::class.java).apply {
            isAccessible = true
        }
        val shiftField = OpenT9InputMethodService::class.java.getDeclaredField("shiftController").apply {
            isAccessible = true
        }
        val shiftController = shiftField.get(service) as ShiftController

        // 1. LOWERCASE mode
        shiftController.reset()
        assertEquals(ShiftMode.LOWERCASE, shiftController.currentMode)
        assertEquals("don't", method.invoke(service, "don't"))
        assertEquals("can't", method.invoke(service, "can't"))
        assertEquals("I'm", method.invoke(service, "I'm")) // 'I' must remain capitalized!
        assertEquals("I've", method.invoke(service, "I've"))
        assertEquals("I", method.invoke(service, "I"))
        assertEquals("it's", method.invoke(service, "it's"))

        // 2. TITLECASE mode
        shiftController.onShiftTap(1000L)
        assertEquals(ShiftMode.TITLECASE, shiftController.currentMode)
        assertEquals("Don't", method.invoke(service, "don't"))
        assertEquals("Can't", method.invoke(service, "can't"))
        assertEquals("I'm", method.invoke(service, "I'm"))
        assertEquals("It's", method.invoke(service, "it's"))
        assertEquals("You're", method.invoke(service, "you're"))

        // 3. UPPERCASE mode
        shiftController.onShiftTap(1500L)
        assertEquals(ShiftMode.UPPERCASE, shiftController.currentMode)
        assertEquals("DON'T", method.invoke(service, "don't"))
        assertEquals("CAN'T", method.invoke(service, "can't"))
        assertEquals("I'M", method.invoke(service, "I'm"))
        assertEquals("IT'S", method.invoke(service, "it's"))
        assertEquals("YOU'RE", method.invoke(service, "you're"))
    }

    @Test
    fun testRetroactiveUncommitWithContractionDisabledByDefault() {
        val delController = DeleteController()
        val ic = mockk<InputConnection>(relaxed = true)

        // Commit "don't " with length 6
        delController.recordCommit("don't", listOf(3, 6, 6, 8))

        every { ic.getSelectedText(0) } returns null
        every { ic.getTextBeforeCursor(6, 0) } returns "don't "
        every { ic.getTextBeforeCursor(2, 0) } returns " "

        var rehydrated: List<Int>? = null
        val uncommitted = delController.handleDeleteTap(
            ic = ic,
            hasActiveComposing = false,
            onPopComposingStroke = {},
            onRehydrateDigits = { digits -> rehydrated = digits }
        )

        assertFalse("Should not trigger retroactive uncommit by default", uncommitted)
        assertNull(rehydrated)
        verify { ic.deleteSurroundingText(1, 0) }
    }

    @Test
    fun testRetroactiveUncommitWithImContractionDisabledByDefault() {
        val delController = DeleteController()
        val ic = mockk<InputConnection>(relaxed = true)

        // Commit "I'm " with length 4
        delController.recordCommit("I'm", listOf(4, 6))

        every { ic.getSelectedText(0) } returns null
        every { ic.getTextBeforeCursor(4, 0) } returns "I'm "
        every { ic.getTextBeforeCursor(2, 0) } returns " "

        var rehydrated: List<Int>? = null
        val uncommitted = delController.handleDeleteTap(
            ic = ic,
            hasActiveComposing = false,
            onPopComposingStroke = {},
            onRehydrateDigits = { digits -> rehydrated = digits }
        )

        assertFalse("Should not trigger retroactive uncommit by default", uncommitted)
        assertNull(rehydrated)
        verify { ic.deleteSurroundingText(1, 0) }
    }

    @Test
    fun testKeyboardViewCandidateShiftFormatting() {
        val inputView = service.onCreateInputView() as T9KeyboardView
        inputView.updateCandidates(listOf("don't", "I'm", "it's"))

        // Default: Lowercase (state 0)
        inputView.setShiftState(0)
        val formatMethod = T9KeyboardView::class.java.getDeclaredMethod("formatCandidateWord", String::class.java).apply {
            isAccessible = true
        }
        assertEquals("don't", formatMethod.invoke(inputView, "don't"))
        assertEquals("I'm", formatMethod.invoke(inputView, "I'm"))

        // Titlecase (state 1)
        inputView.setShiftState(1)
        assertEquals("Don't", formatMethod.invoke(inputView, "don't"))
        assertEquals("I'm", formatMethod.invoke(inputView, "I'm"))
        assertEquals("It's", formatMethod.invoke(inputView, "it's"))

        // Uppercase (state 2)
        inputView.setShiftState(2)
        assertEquals("DON'T", formatMethod.invoke(inputView, "don't"))
        assertEquals("I'M", formatMethod.invoke(inputView, "I'm"))
        assertEquals("IT'S", formatMethod.invoke(inputView, "it's"))
    }
}
