package com.opent9.keyboard

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.settings.SettingsObserver
import com.opent9.keyboard.ui.FlickDirection
import com.opent9.keyboard.ui.T9KeyboardView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioHapticFeedbackTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testAudioFeedbackToggleAndSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val observer = SettingsObserver(context)
        observer.start()

        // Default state
        assertTrue(observer.isAudioEnabled())
        assertEquals(0.6f, observer.getAudioVolume(), 0.01f)
        assertEquals(0, observer.getAudioStyle())

        // Toggle OFF
        prefs.edit().putBoolean("audio_enabled", false).apply()
        assertFalse(observer.isAudioEnabled())

        // Change volume and style
        prefs.edit()
            .putBoolean("audio_enabled", true)
            .putInt("audio_volume", 90)
            .putString("audio_style", "2") // Minimalist Click
            .apply()

        assertTrue(observer.isAudioEnabled())
        assertEquals(0.9f, observer.getAudioVolume(), 0.01f)
        assertEquals(2, observer.getAudioStyle())

        observer.stop()
    }

    @Test
    fun testHapticFeedbackToggleAndIntensity() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val observer = SettingsObserver(context)
        observer.start()

        // Default state
        assertTrue(observer.isHapticEnabled())
        assertEquals(25L, observer.getVibrationIntensity())

        // Toggle OFF
        prefs.edit().putBoolean("haptic_enabled", false).apply()
        assertFalse(observer.isHapticEnabled())

        // Change intensity slider (5ms to 100ms)
        prefs.edit()
            .putBoolean("haptic_enabled", true)
            .putInt("vibration_intensity", 80)
            .apply()

        assertTrue(observer.isHapticEnabled())
        assertEquals(80L, observer.getVibrationIntensity())

        observer.stop()
    }

    @Test
    fun testPlayClickFeedbackDoesNotThrowWhenDisabled() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putBoolean("audio_enabled", false)
            .putBoolean("haptic_enabled", false)
            .apply()

        val observer = SettingsObserver(context)
        observer.start()

        val view = T9KeyboardView(context)
        view.settingsObserver = observer

        // Calling playClickFeedback when both are disabled should execute cleanly without exception
        try {
            view.playClickFeedback()
        } catch (e: Exception) {
            fail("playClickFeedback should not throw when feedback is disabled: ${e.message}")
        }

        observer.stop()
    }

    @Test
    fun testPlayClickFeedbackDoesNotThrowWhenEnabled() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putBoolean("audio_enabled", true)
            .putInt("audio_volume", 75)
            .putString("audio_style", "1")
            .putBoolean("haptic_enabled", true)
            .putInt("vibration_intensity", 45)
            .apply()

        val observer = SettingsObserver(context)
        observer.start()

        val view = T9KeyboardView(context)
        view.settingsObserver = observer

        try {
            view.playClickFeedback()
        } catch (e: Exception) {
            fail("playClickFeedback should not throw when feedback is enabled: ${e.message}")
        }

        observer.stop()
    }

    @Test
    fun testFeedbackInvokedOnTouchGestureCallbacks() {
        val observer = SettingsObserver(context)
        observer.start()

        var clickFeedbackCalls = 0
        val view = object : T9KeyboardView(context) {
            override fun playClickFeedback() {
                super.playClickFeedback()
                clickFeedbackCalls++
            }
        }
        view.settingsObserver = observer
        view.layout(0, 0, 1080, 780)

        val sampleKey = view.keyAtlas.keys[0]

        // 1. onKeyTap
        clickFeedbackCalls = 0
        view.onKeyTap(sampleKey, sampleKey.centerX, sampleKey.centerY)
        assertEquals(1, clickFeedbackCalls)

        // 2. onKeyFlick
        clickFeedbackCalls = 0
        view.onKeyFlick(sampleKey, FlickDirection.UP)
        assertEquals(1, clickFeedbackCalls)

        // 3. onKeyLongPress
        clickFeedbackCalls = 0
        view.onKeyLongPress(sampleKey)
        assertEquals(1, clickFeedbackCalls)

        // 4. onSpaceScrub
        clickFeedbackCalls = 0
        view.onSpaceScrub(1)
        assertEquals(1, clickFeedbackCalls)

        // 5. onPillTap
        clickFeedbackCalls = 0
        view.onPillTap()
        assertEquals(1, clickFeedbackCalls)

        // 6. onCandidateTap
        clickFeedbackCalls = 0
        view.onCandidateTap(0)
        assertEquals(1, clickFeedbackCalls)

        observer.stop()
    }
}
