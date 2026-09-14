package com.opent9.keyboard

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.ui.KeyType
import com.opent9.keyboard.ui.ShiftController
import com.opent9.keyboard.ui.ShiftMode
import com.opent9.keyboard.ui.T9KeyboardView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class AutoCapitalizationTest {

    private lateinit var controller: ServiceController<OpenT9InputMethodService>
    private lateinit var service: OpenT9InputMethodService
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("auto_caps", true)
            .commit()

        controller = Robolectric.buildService(OpenT9InputMethodService::class.java)
        service = controller.create().get()
    }

    private fun getShiftController(): ShiftController {
        val field = OpenT9InputMethodService::class.java.getDeclaredField("shiftController").apply {
            isAccessible = true
        }
        return field.get(service) as ShiftController
    }

    @Test
    fun testAutoCapsStartsInTitlecaseForSentenceField() {
        val editText = EditText(context)
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val editorInfo = EditorInfo()
        val ic = editText.onCreateInputConnection(editorInfo)

        val inputView = service.onCreateInputView() as T9KeyboardView
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(editorInfo, false)

        val shift = getShiftController()
        assertEquals("Sentence field must start in TITLECASE", ShiftMode.TITLECASE, shift.currentMode)
        assertEquals(1, inputView.shiftState)
    }

    @Test
    fun testAutoCapsCharactersMode() {
        val editText = EditText(context)
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        val editorInfo = EditorInfo()
        val ic = editText.onCreateInputConnection(editorInfo)

        val inputView = service.onCreateInputView() as T9KeyboardView
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(editorInfo, false)

        val shift = getShiftController()
        assertEquals("Cap characters field must start in UPPERCASE", ShiftMode.UPPERCASE, shift.currentMode)
        assertEquals(2, inputView.shiftState)
    }

    @Test
    fun testAutoCapsExcludedInPasswordAndEmailAndUri() {
        service.onCreateInputView()
        val shift = getShiftController()

        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }

        // 1. Password
        val pwEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val pwInfo = EditorInfo()
        icField.set(service, pwEditText.onCreateInputConnection(pwInfo))
        service.onStartInputView(pwInfo, false)
        assertEquals("Password must remain LOWERCASE", ShiftMode.LOWERCASE, shift.currentMode)

        // 2. Email Address
        val emailEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val emailInfo = EditorInfo()
        icField.set(service, emailEditText.onCreateInputConnection(emailInfo))
        service.onStartInputView(emailInfo, false)
        assertEquals("Email address must remain LOWERCASE", ShiftMode.LOWERCASE, shift.currentMode)

        // 3. URI
        val uriEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val uriInfo = EditorInfo()
        icField.set(service, uriEditText.onCreateInputConnection(uriInfo))
        service.onStartInputView(uriInfo, false)
        assertEquals("URI must remain LOWERCASE", ShiftMode.LOWERCASE, shift.currentMode)
    }

    @Test
    fun testAutoCapsSentenceTransitionAfterPeriodAndSpace() {
        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val editorInfo = EditorInfo()
        val ic = editText.onCreateInputConnection(editorInfo)

        val inputView = service.onCreateInputView() as T9KeyboardView
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(editorInfo, false)
        val shift = getShiftController()
        assertEquals(ShiftMode.TITLECASE, shift.currentMode)

        // Simulate typing and committing a word: "Hello "
        val commitWordMethod = OpenT9InputMethodService::class.java.getDeclaredMethod(
            "commitWord",
            String::class.java,
            Boolean::class.java
        ).apply { isAccessible = true }

        commitWordMethod.invoke(service, "Hello", true)
        assertEquals("Hello ", editText.text.toString())
        assertEquals("Middle of sentence should be LOWERCASE", ShiftMode.LOWERCASE, shift.currentMode)

        // Type period via PUNCT_1
        val punctKey = inputView.keyAtlas.keys.first { it.type == KeyType.PUNCT_1 }
        inputView.onKeyTapAction?.invoke(punctKey, punctKey.centerX, punctKey.centerY)
        assertEquals("Hello .", editText.text.toString())

        // Type space via SPACE_0 -> now at ". "
        val spaceKey = inputView.keyAtlas.keys.first { it.type == KeyType.SPACE_0 }
        inputView.onKeyTapAction?.invoke(spaceKey, spaceKey.centerX, spaceKey.centerY)
        assertEquals("Hello . ", editText.text.toString())

        // Auto-caps should trigger TITLECASE for the next sentence!
        assertEquals("After period and space, must auto-capitalize to TITLECASE", ShiftMode.TITLECASE, shift.currentMode)
        assertEquals(1, inputView.shiftState)
    }

    @Test
    fun testAutoCapsManualOverrideRespectedUntilCommit() {
        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val editorInfo = EditorInfo()
        val ic = editText.onCreateInputConnection(editorInfo)

        val inputView = service.onCreateInputView() as T9KeyboardView
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(editorInfo, false)
        val shift = getShiftController()
        assertEquals(ShiftMode.TITLECASE, shift.currentMode)

        // User taps shift to cycle: TITLECASE -> UPPERCASE -> LOWERCASE
        val shiftKey = inputView.keyAtlas.keys.first { it.type == KeyType.SHIFT }
        inputView.onKeyTapAction?.invoke(shiftKey, shiftKey.centerX, shiftKey.centerY)
        assertEquals(ShiftMode.UPPERCASE, shift.currentMode)

        Thread.sleep(400L)
        inputView.onKeyTapAction?.invoke(shiftKey, shiftKey.centerX, shiftKey.centerY)
        assertEquals(ShiftMode.LOWERCASE, shift.currentMode)
        assertTrue(shift.isManualOverrideActive())

        // Calling updateAutoCaps directly must not override the user's manual LOWERCASE
        val updateAutoCapsMethod = OpenT9InputMethodService::class.java.getDeclaredMethod(
            "updateAutoCaps",
            Boolean::class.java
        ).apply { isAccessible = true }

        updateAutoCapsMethod.invoke(service, false)
        assertEquals("Manual override must be preserved", ShiftMode.LOWERCASE, shift.currentMode)

        // But committing a word clears manual override
        val commitWordMethod = OpenT9InputMethodService::class.java.getDeclaredMethod(
            "commitWord",
            String::class.java,
            Boolean::class.java
        ).apply { isAccessible = true }
        commitWordMethod.invoke(service, "word", true)
        assertFalse(shift.isManualOverrideActive())
    }

    @Test
    fun testAutoCapsDisabledByPreference() {
        // Disable preference
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("auto_caps", false)
            .commit()

        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val editorInfo = EditorInfo()
        val ic = editText.onCreateInputConnection(editorInfo)

        val inputView = service.onCreateInputView() as T9KeyboardView
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(editorInfo, false)
        val shift = getShiftController()
        assertEquals("Auto-caps disabled by pref must stay LOWERCASE", ShiftMode.LOWERCASE, shift.currentMode)
        assertEquals(0, inputView.shiftState)
    }

    @Test
    fun testCapsLockPriorityOverAutoCaps() {
        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val editorInfo = EditorInfo()
        val ic = editText.onCreateInputConnection(editorInfo)

        val inputView = service.onCreateInputView() as T9KeyboardView
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(editorInfo, false)
        val shift = getShiftController()

        // Flick up on Shift to engage Caps Lock
        inputView.onKeyFlickAction?.invoke(
            inputView.keyAtlas.keys.first { it.type == KeyType.SHIFT },
            com.opent9.keyboard.ui.FlickDirection.UP
        )
        assertTrue(shift.isCapsLocked())
        assertEquals(ShiftMode.UPPERCASE, shift.currentMode)

        // Committing text must NEVER drop Caps Lock
        val commitWordMethod = OpenT9InputMethodService::class.java.getDeclaredMethod(
            "commitWord",
            String::class.java,
            Boolean::class.java
        ).apply { isAccessible = true }
        commitWordMethod.invoke(service, "HELLO", true)

        assertTrue("Caps lock must remain locked", shift.isCapsLocked())
        assertEquals("Caps lock must remain UPPERCASE", ShiftMode.UPPERCASE, shift.currentMode)
    }
}
