package com.opent9.keyboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceViewHolder
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.settings.ManualInputSeekBarPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class ManualInputSeekBarPreferenceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testInitializationAndDefaults() {
        val pref = ManualInputSeekBarPreference(context).apply {
            min = 10
            max = 100
            value = 40
            title = "Test Preference"
        }

        assertEquals(10, pref.min)
        assertEquals(100, pref.max)
        assertEquals(40, pref.value)
        assertEquals("Test Preference", pref.title)
    }

    @Test
    fun testBindingAndStylingValueDisplay() {
        val pref = ManualInputSeekBarPreference(context).apply {
            min = 5
            max = 50
            value = 25
            showSeekBarValue = true
        }

        // Inflate seekbar preference layout
        val itemView = LayoutInflater.from(context).inflate(
            androidx.preference.R.layout.preference_widget_seekbar_material,
            null
        )
        val holder = PreferenceViewHolder.createInstanceForTests(itemView)
        pref.onBindViewHolder(holder)

        val valueTv = holder.findViewById(androidx.preference.R.id.seekbar_value) as? TextView
        assertNotNull("Value TextView should be found", valueTv)
        assertEquals("25", valueTv?.text.toString())
        assertTrue("Value TextView should be clickable", valueTv!!.isClickable)
        assertTrue("Value TextView should be focusable", valueTv.isFocusable)
        assertNotNull("Value TextView should have background drawable set", valueTv.background)
    }

    @Test
    fun testManualInputDialogValidInput() {
        val pref = ManualInputSeekBarPreference(context).apply {
            min = 10
            max = 100
            value = 30
            title = "Volume"
            showSeekBarValue = true
        }

        val itemView = LayoutInflater.from(context).inflate(
            androidx.preference.R.layout.preference_widget_seekbar_material,
            null
        )
        val holder = PreferenceViewHolder.createInstanceForTests(itemView)
        pref.onBindViewHolder(holder)

        val dialog = pref.showManualInputDialog()
        assertNotNull("Dialog should be created", dialog)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("Dialog should be showing", dialog!!.isShowing)

        // Find the EditText inside the dialog
        val editText = findEditTextRecursively(dialog.window?.decorView)
        assertNotNull("EditText should be present inside dialog", editText)
        assertEquals("30", editText?.text.toString())

        // User enters a valid value
        editText?.setText("75")
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertNotNull("Positive button should exist", positiveButton)
        positiveButton?.performClick()

        // Verify value is updated in preference and seekbar progress
        assertEquals(75, pref.value)
        val seekBar = holder.findViewById(androidx.preference.R.id.seekbar) as? SeekBar
        assertEquals(75 - 10, seekBar?.progress)
    }

    @Test
    fun testManualInputDialogInvalidInputClampingAndError() {
        val pref = ManualInputSeekBarPreference(context).apply {
            max = 600
            min = 200
            value = 350
            title = "Delay"
        }

        val dialog = pref.showManualInputDialog()
        assertNotNull("Dialog should be created", dialog)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val editText = findEditTextRecursively(dialog!!.window?.decorView)
        assertNotNull("EditText should be present", editText)

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        // Test 1: Empty input
        editText?.setText("")
        positiveButton?.performClick()
        assertNotNull("Error should be shown for empty input", editText?.error)
        assertEquals(350, pref.value) // Unchanged
        assertTrue("Dialog should remain open on error", dialog.isShowing)

        // Test 2: Input below min
        editText?.setText("150")
        positiveButton?.performClick()
        assertNotNull("Error should be shown when below min", editText?.error)
        assertEquals(350, pref.value) // Unchanged
        assertTrue("Dialog should remain open on error", dialog.isShowing)

        // Test 3: Input above max
        editText?.setText("900")
        positiveButton?.performClick()
        assertNotNull("Error should be shown when above max", editText?.error)
        assertEquals(350, pref.value) // Unchanged
        assertTrue("Dialog should remain open on error", dialog.isShowing)

        // Test 4: Correct valid input
        editText?.setText("420")
        positiveButton?.performClick()
        assertNull("Error should be cleared", editText?.error)
        assertEquals(420, pref.value)
    }

    @Test
    fun testPreferencesXmlInflation() {
        val prefManager = androidx.preference.PreferenceManager(context)
        val prefScreen = prefManager.inflateFromResource(context, R.xml.preferences, null)

        val vibrationPref = prefScreen.findPreference<ManualInputSeekBarPreference>("vibration_intensity")
        val audioVolumePref = prefScreen.findPreference<ManualInputSeekBarPreference>("audio_volume")
        val keyboardHeightPref = prefScreen.findPreference<ManualInputSeekBarPreference>("keyboard_height")
        val candidateFontSizePref = prefScreen.findPreference<ManualInputSeekBarPreference>("candidate_font_size")
        val longPressDelayPref = prefScreen.findPreference<ManualInputSeekBarPreference>("long_press_delay")
        val multiTapTimeoutPref = prefScreen.findPreference<ManualInputSeekBarPreference>("multi_tap_timeout")

        assertNotNull("vibration_intensity should be ManualInputSeekBarPreference", vibrationPref)
        assertNotNull("audio_volume should be ManualInputSeekBarPreference", audioVolumePref)
        assertNotNull("keyboard_height should be ManualInputSeekBarPreference", keyboardHeightPref)
        assertNotNull("candidate_font_size should be ManualInputSeekBarPreference", candidateFontSizePref)
        assertNotNull("long_press_delay should be ManualInputSeekBarPreference", longPressDelayPref)
        assertNotNull("multi_tap_timeout should be ManualInputSeekBarPreference", multiTapTimeoutPref)

        assertEquals(5, vibrationPref?.min)
        assertEquals(100, vibrationPref?.max)
        assertEquals(220, keyboardHeightPref?.min)
        assertEquals(320, keyboardHeightPref?.max)
        assertEquals(200, longPressDelayPref?.min)
        assertEquals(600, longPressDelayPref?.max)
        assertEquals(400, multiTapTimeoutPref?.min)
        assertEquals(1000, multiTapTimeoutPref?.max)
    }

    @Test
    fun testDisabledPreferenceDoesNotShowDialog() {
        val pref = ManualInputSeekBarPreference(context).apply {
            min = 5
            max = 100
            value = 25
            isEnabled = false
        }

        val dialog = pref.showManualInputDialog()
        assertNull("Dialog should not be opened when preference is disabled", dialog)
    }

    private fun findEditTextRecursively(view: View?): EditText? {
        if (view is EditText) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findEditTextRecursively(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }
}
