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
        assertEquals("normal", observer.getSpaceScrubbingSensitivity())
        assertEquals(false, observer.isSpaceScrubbingHoldRequired())

        // Verify initial sync parameters (DPI-normalized touch sigma = 16dp * density)
        val expectedSigma = SettingsObserver.DEFAULT_TOUCH_SIGMA_DP *
            context.resources.displayMetrics.density.coerceAtLeast(1.0f)
        assertEquals(expectedSigma, syncedSigma, 0.01f)
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
        assertTrue(layoutConfigChangedTriggered)
        assertEquals(false, inputModeChangedValue)
        assertEquals("EN", languageChangedValue)
        assertEquals(false, autoCapsChangedValue)

        // Test that long_press_delay alone triggers layoutConfigChanged
        layoutConfigChangedTriggered = false
        prefs.edit().putInt("long_press_delay", 250).apply()
        assertTrue(layoutConfigChangedTriggered)

        // Test space scrubbing settings trigger layoutConfigChanged
        layoutConfigChangedTriggered = false
        prefs.edit()
            .putString("space_scrubbing_sensitivity", "low")
            .putBoolean("space_scrubbing_hold", true)
            .apply()
        assertTrue(layoutConfigChangedTriggered)
        assertEquals("low", observer.getSpaceScrubbingSensitivity())
        assertEquals(true, observer.isSpaceScrubbingHoldRequired())

        observer.stop()
    }

    @Test
    fun testBilingualPredictionPreferenceSync() {
        var syncedBilingual: Boolean? = null
        val observer = SettingsObserver(
            context = context,
            onBilingualSync = { enabled -> syncedBilingual = enabled }
        )
        observer.start()
        assertEquals(false, observer.isBilingualPredictionEnabled())
        assertEquals(false, syncedBilingual)

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("bilingual_prediction", true)
            .commit()

        assertEquals(true, observer.isBilingualPredictionEnabled())
        assertEquals(true, syncedBilingual)
        observer.stop()
    }
}
