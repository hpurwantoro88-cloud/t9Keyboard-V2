package com.opent9.keyboard.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.opent9.keyboard.jni.NativeEngineBridge

class SettingsObserver(
    private val context: Context,
    private val onConfigSync: (Float, Int, Int, Boolean, Boolean, Int) -> Unit = { sigma, longPress, multiTap, autoSpace, slang, decay ->
        NativeEngineBridge.syncConfig(sigma, longPress, multiTap, autoSpace, slang, decay)
    }
) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun start() {
        prefs.registerOnSharedPreferenceChangeListener(this)
        syncAll()
    }

    fun stop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    fun syncAll() {
        val longPressMs = prefs.getInt("long_press_delay", 350)
        val multiTapMs = prefs.getInt("multi_tap_timeout", 600)
        val autoSpace = prefs.getBoolean("auto_space", true)
        val slangBoost = prefs.getBoolean("slang_boost", true)
        val decayDays = (prefs.getString("decay_half_life", "30") ?: "30").toIntOrNull() ?: 30

        onConfigSync(42.0f, longPressMs, multiTapMs, autoSpace, slangBoost, decayDays)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        syncAll()
    }

    fun isHapticEnabled(): Boolean = prefs.getBoolean("haptic_enabled", true)
    fun getVibrationIntensity(): Long = prefs.getInt("vibration_intensity", 25).toLong()
    fun isAudioEnabled(): Boolean = prefs.getBoolean("audio_enabled", true)
    fun getAudioVolume(): Float = prefs.getInt("audio_volume", 60) / 100f
    fun getAudioStyle(): Int = (prefs.getString("audio_style", "0") ?: "0").toIntOrNull() ?: 0
    fun getStartupLanguage(): String = prefs.getString("startup_language", "ID") ?: "ID"
    fun isDefaultT9(): Boolean = (prefs.getString("default_input_mode", "0") ?: "0") == "0"
    fun isSpaceScrubbingEnabled(): Boolean = prefs.getBoolean("space_scrubbing", true)
}
