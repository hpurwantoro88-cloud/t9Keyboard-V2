package com.opent9.keyboard

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.settings.SettingsObserver
import com.opent9.keyboard.ui.KeyInfo
import com.opent9.keyboard.ui.KeyType
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
class TypingSettingsBehaviorTest {

    private lateinit var context: Context
    private lateinit var controller: ServiceController<OpenT9InputMethodService>
    private lateinit var service: OpenT9InputMethodService
    private lateinit var inputView: T9KeyboardView
    private lateinit var editText: EditText

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        controller = Robolectric.buildService(OpenT9InputMethodService::class.java)
        service = controller.create().get()
        inputView = service.onCreateInputView() as T9KeyboardView

        editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
    }

    private fun bindInputConnection(customInfo: EditorInfo? = null) {
        val info = customInfo ?: EditorInfo().apply {
            inputType = editText.inputType
        }
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)
        service.onStartInputView(info, false)
    }

    private fun invokeHandleKeyTap(key: KeyInfo, touchX: Float = 0f, touchY: Float = 0f) {
        val method = OpenT9InputMethodService::class.java.getDeclaredMethod(
            "handleKeyTap",
            KeyInfo::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType
        ).apply { isAccessible = true }
        method.invoke(service, key, touchX, touchY)
    }

    private fun invokeCommitCandidateIndex(index: Int) {
        val method = OpenT9InputMethodService::class.java.getDeclaredMethod(
            "commitCandidateIndex",
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        method.invoke(service, index)
    }

    private fun setActiveCandidates(candidates: List<String>) {
        val field = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        field.set(service, candidates)
    }

    @Test
    fun testAutoSpaceEnabledAppendsTrailingSpaceOnCandidateCommit() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("auto_space", true)
            .commit()

        bindInputConnection()
        setActiveCandidates(listOf("testing", "tester"))

        invokeCommitCandidateIndex(0)

        assertEquals("Testing ", editText.text.toString())
    }

    @Test
    fun testAutoSpaceDisabledDoesNotAppendTrailingSpaceOnCandidateCommit() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("auto_space", false)
            .commit()

        bindInputConnection()
        setActiveCandidates(listOf("testing", "tester"))

        invokeCommitCandidateIndex(0)

        assertEquals("Testing", editText.text.toString())
    }

    @Test
    fun testDoubleSpacePeriodEnabledReplacesSpaceWithPeriodAndTriggersAutoCaps() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("double_space_period", true)
            .commit()

        bindInputConnection()
        editText.setText("hello")
        editText.setSelection(5)

        val spaceKey = inputView.keyAtlas.keys[14].copy(type = KeyType.SPACE_0)

        // 1st Space: commits " " -> "hello "
        invokeHandleKeyTap(spaceKey)
        assertEquals("hello ", editText.text.toString())

        // 2nd Space in rapid succession (<800ms): should replace trailing space with ". " -> "hello. "
        invokeHandleKeyTap(spaceKey)
        assertEquals("hello. ", editText.text.toString())

        // Shift should now be in TITLECASE for the next sentence
        val shiftField = OpenT9InputMethodService::class.java.getDeclaredField("shiftController").apply {
            isAccessible = true
        }
        val shift = shiftField.get(service) as com.opent9.keyboard.ui.ShiftController
        assertEquals(ShiftMode.TITLECASE, shift.currentMode)
    }

    @Test
    fun testDoubleSpacePeriodDisabledCommitsDoubleSpace() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("double_space_period", false)
            .commit()

        bindInputConnection()
        editText.setText("hello")
        editText.setSelection(5)

        val spaceKey = inputView.keyAtlas.keys[14].copy(type = KeyType.SPACE_0)

        // 1st Space: commits " " -> "hello "
        invokeHandleKeyTap(spaceKey)
        assertEquals("hello ", editText.text.toString())

        // 2nd Space: with setting disabled, outputs another space -> "hello  "
        invokeHandleKeyTap(spaceKey)
        assertEquals("hello  ", editText.text.toString())
    }

    @Test
    fun testDoubleSpacePeriodBypassedInPasswordFields() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("double_space_period", true)
            .commit()

        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val passwordInfo = EditorInfo().apply {
            inputType = editText.inputType
        }
        bindInputConnection(passwordInfo)
        editText.setText("secret")
        editText.setSelection(6)

        val spaceKey = inputView.keyAtlas.keys[14].copy(type = KeyType.SPACE_0)

        invokeHandleKeyTap(spaceKey)
        invokeHandleKeyTap(spaceKey)

        assertEquals("secret  ", editText.text.toString())
    }

    @Test
    fun testAutoCapsDisabledResetsShiftToLowercase() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("auto_caps", true).commit()

        bindInputConnection()

        val shiftField = OpenT9InputMethodService::class.java.getDeclaredField("shiftController").apply {
            isAccessible = true
        }
        val shift = shiftField.get(service) as com.opent9.keyboard.ui.ShiftController
        // Initially in TITLECASE because of empty field auto-caps
        assertEquals(ShiftMode.TITLECASE, shift.currentMode)

        // Turn off auto_caps in preferences
        prefs.edit().putBoolean("auto_caps", false).commit()

        // Shift mode should immediately reset to LOWERCASE
        assertEquals(ShiftMode.LOWERCASE, shift.currentMode)
    }

    @Test
    fun testMultiTapTimeoutSettingApplied() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putInt("multi_tap_timeout", 900).commit()

        val observerField = OpenT9InputMethodService::class.java.getDeclaredField("settingsObserver").apply {
            isAccessible = true
        }
        val observer = observerField.get(service) as SettingsObserver
        assertEquals(900L, observer.getMultiTapTimeout())
    }

    @Test
    fun testPasswordModeStaysAbcAfterPageSwitchAndPillTap() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("default_input_mode", "0") // Default T9
            .commit()

        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val passwordInfo = EditorInfo().apply {
            inputType = editText.inputType
        }
        bindInputConnection(passwordInfo)
        assertFalse("Password mode must start with isT9Mode = false", inputView.isT9Mode)

        // Attempt to toggle via pill tap -> must stay false
        inputView.onPillTapAction?.invoke()
        assertFalse("Pill tap must not enable T9 mode in password field", inputView.isT9Mode)

        // Switch to Page 2 (?123) and return via ABC -> must still stay false
        val page2Key = inputView.keyAtlas.keys[12]
        invokeHandleKeyTap(page2Key)
        assertEquals(com.opent9.keyboard.ui.KeyboardPage.PAGE_2_EXT_SYM, inputView.keyAtlas.currentPage)

        val abcKey = inputView.keyAtlas.page2Keys.first { it.primaryLabel == "ABC" }
        invokeHandleKeyTap(abcKey)
        assertEquals(com.opent9.keyboard.ui.KeyboardPage.PAGE_0_TEXT, inputView.keyAtlas.currentPage)
        assertFalse("Returning to Page 0 in password field must keep isT9Mode = false", inputView.isT9Mode)
    }

    @Test
    fun testMultiTapInPasswordModeDoesNotRecordToLearnedWords() {
        com.opent9.keyboard.jni.NativeEngineBridge.resetUserDictionary()
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val passwordInfo = EditorInfo().apply {
            inputType = editText.inputType
        }
        bindInputConnection(passwordInfo)

        val key2 = inputView.keyAtlas.keys[1] // abc
        val key7 = inputView.keyAtlas.keys[8] // pqrs
        val key1 = inputView.keyAtlas.keys[0] // punctuation

        // Type 'p' -> 'a' -> '.' in password field (switching from letters to punct triggers buffer flush)
        invokeHandleKeyTap(key7)
        invokeHandleKeyTap(key2)
        invokeHandleKeyTap(key1)
        invokeHandleKeyTap(key2)

        assertEquals(0, com.opent9.keyboard.jni.NativeEngineBridge.getWordUsageCount("pa"))
    }

    @Test
    fun testSpaceScrubbingDisabledAllowsSpaceTapAndSkipsScrub() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("space_scrubbing", false)
            .commit()

        bindInputConnection()
        inputView.layout(0, 0, 1080, 780)
        inputView.reloadLayoutConfiguration()

        editText.setText("hello")
        editText.setSelection(5)

        val spaceKey = inputView.keyAtlas.keys.first { it.type == KeyType.SPACE_0 }
        val t0 = 1000L
        inputView.onTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, spaceKey.centerX, spaceKey.centerY, 0))
        inputView.onTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 30, android.view.MotionEvent.ACTION_MOVE, spaceKey.centerX + 90f, spaceKey.centerY, 0))
        assertFalse("Scrubbing must remain inactive when space_scrubbing is false", inputView.gestureTracker.isScrubbing)

        inputView.onTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 60, android.view.MotionEvent.ACTION_UP, spaceKey.centerX + 90f, spaceKey.centerY, 0))
        assertEquals("hello ", editText.text.toString())
    }

    @Test
    fun testToggleT9ModeClearsIdlePredictionsWhenSwitchingToAbc() {
        bindInputConnection()
        inputView.updateCandidates(listOf("kamu", "saya"), isIdlePrediction = true)
        assertTrue(inputView.isIdlePrediction)

        inputView.onPillTapAction?.invoke()
        assertFalse(inputView.isT9Mode)
        assertFalse("Idle predictions must be cleared when switching to ABC mode", inputView.isIdlePrediction)
    }
}
