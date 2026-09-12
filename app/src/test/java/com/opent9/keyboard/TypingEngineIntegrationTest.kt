package com.opent9.keyboard

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
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
        val ic = mockk<InputConnection>(relaxed = true)

        // Simulate Space scrub action
        inputView.onSpaceScrubAction?.invoke(2) // +2 right steps
        // Verifies that listener was registered and ready
        assertNotNull(inputView.onSpaceScrubAction)
    }
}
