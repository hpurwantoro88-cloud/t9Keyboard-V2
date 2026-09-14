package com.opent9.keyboard

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.settings.SettingsObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsSyncTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSettingsObserverDefaultsAndSync() {
        var syncedSigma = 0f
        var syncedLongPress = 0
        var syncedMultiTap = 0
        var syncedAutoSpace = false
        var syncedSlangBoost = false
        var syncedDecayDays = 0

        val observer = SettingsObserver(context) { sigma, lp, mt, aspace, sboost, decay ->
            syncedSigma = sigma
            syncedLongPress = lp
            syncedMultiTap = mt
            syncedAutoSpace = aspace
            syncedSlangBoost = sboost
            syncedDecayDays = decay
        }
        observer.start()

        // Check helper getters
        assertTrue(observer.isHapticEnabled())
        assertEquals(25L, observer.getVibrationIntensity())
        assertTrue(observer.isAudioEnabled())
        assertEquals(0.6f, observer.getAudioVolume(), 0.01f)
        assertEquals(0, observer.getAudioStyle())
        assertEquals("ID", observer.getStartupLanguage())
        assertTrue(observer.isDefaultT9())
        assertTrue(observer.isAutoSpaceEnabled())
        assertTrue(observer.isDoubleSpacePeriodEnabled())
        assertEquals(600L, observer.getMultiTapTimeout())
        assertTrue(observer.isSpaceScrubbingEnabled())
        assertEquals("right", observer.get4thColumnPosition())
        assertEquals("bottom", observer.get4thRowPosition())
        assertEquals("default", observer.get4thRowOrder())
        assertEquals("default", observer.get4thColumnOrder())

        // Verify initial sync parameters
        assertEquals(42.0f, syncedSigma, 0.01f)
        assertEquals(350, syncedLongPress)
        assertEquals(600, syncedMultiTap)
        assertTrue(syncedAutoSpace)
        assertTrue(syncedSlangBoost)
        assertEquals(30, syncedDecayDays)

        var layoutConfigChangedTriggered = false
        var inputModeChangedValue: Boolean? = null
        var languageChangedValue: String? = null
        var autoCapsChangedValue: Boolean? = null

        observer.onLayoutConfigChanged = {
            layoutConfigChangedTriggered = true
        }
        observer.onInputModeConfigChanged = {
            inputModeChangedValue = it
        }
        observer.onLanguageConfigChanged = {
            languageChangedValue = it
        }
        observer.onAutoCapsConfigChanged = {
            autoCapsChangedValue = it
        }

        // Test updating preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putInt("long_press_delay", 450)
            .putInt("multi_tap_timeout", 850)
            .putBoolean("auto_space", false)
            .putBoolean("double_space_period", false)
            .putBoolean("auto_caps", false)
            .putString("default_input_mode", "1")
            .putString("startup_language", "EN")
            .putBoolean("haptic_enabled", false)
            .putInt("vibration_intensity", 50)
            .putBoolean("audio_enabled", false)
            .putInt("audio_volume", 80)
            .putString("audio_style", "2")
            .putString("col_4th_position", "left")
            .putString("row_4th_position", "top")
            .putString("row_4th_order", "space_center")
            .putString("col_4th_order", "shift_top")
            .apply()

        // Verify updated values synchronized
        assertEquals(450, syncedLongPress)
        assertEquals(850, syncedMultiTap)
        assertEquals(false, syncedAutoSpace)
        assertEquals(false, observer.isAutoSpaceEnabled())
        assertEquals(false, observer.isDoubleSpacePeriodEnabled())
        assertEquals(false, observer.isAutoCapsEnabled())
        assertEquals(850L, observer.getMultiTapTimeout())
        assertEquals(false, observer.isDefaultT9())
        assertEquals("EN", observer.getStartupLanguage())
        assertEquals(false, observer.isHapticEnabled())
        assertEquals(50L, observer.getVibrationIntensity())
        assertEquals(false, observer.isAudioEnabled())
        assertEquals(0.8f, observer.getAudioVolume(), 0.01f)
        assertEquals(2, observer.getAudioStyle())
        assertEquals("left", observer.get4thColumnPosition())
        assertEquals("top", observer.get4thRowPosition())
        assertEquals("space_center", observer.get4thRowOrder())
        assertEquals("shift_top", observer.get4thColumnOrder())
        assertTrue(layoutConfigChangedTriggered)
        assertEquals(false, inputModeChangedValue)
        assertEquals("EN", languageChangedValue)
        assertEquals(false, autoCapsChangedValue)

        // Test that long_press_delay alone triggers layoutConfigChanged
        layoutConfigChangedTriggered = false
        prefs.edit().putInt("long_press_delay", 250).apply()
        assertTrue(layoutConfigChangedTriggered)

        observer.stop()
    }
}
