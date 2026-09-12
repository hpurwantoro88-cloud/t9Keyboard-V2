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
        assertEquals("ID", observer.getStartupLanguage())
        assertTrue(observer.isDefaultT9())
        assertTrue(observer.isSpaceScrubbingEnabled())

        // Verify initial sync parameters
        assertEquals(42.0f, syncedSigma, 0.01f)
        assertEquals(350, syncedLongPress)
        assertEquals(600, syncedMultiTap)
        assertTrue(syncedAutoSpace)
        assertTrue(syncedSlangBoost)
        assertEquals(30, syncedDecayDays)

        // Test updating a preference
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putInt("long_press_delay", 450)
            .putInt("multi_tap_timeout", 850)
            .apply()

        // Verify updated values synchronized
        assertEquals(450, syncedLongPress)
        assertEquals(850, syncedMultiTap)

        observer.stop()
    }
}
