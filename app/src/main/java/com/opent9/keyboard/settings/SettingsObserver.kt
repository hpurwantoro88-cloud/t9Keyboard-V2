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

    var onLayoutConfigChanged: (() -> Unit)? = null
    var onThemeConfigChanged: (() -> Unit)? = null
    var onInputModeConfigChanged: ((Boolean) -> Unit)? = null
    var onLanguageConfigChanged: ((String) -> Unit)? = null
    var onAutoCapsConfigChanged: ((Boolean) -> Unit)? = null

    fun syncAll() {
        val longPressMs = prefs.getInt("long_press_delay", 350)
        val multiTapMs = prefs.getInt("multi_tap_timeout", 600)
        val autoSpace = prefs.getBoolean("auto_space", true)
        val slangBoost = prefs.getBoolean("slang_boost", true)
        val decayDays = (prefs.getString("decay_half_life", "30") ?: "30").toIntOrNull() ?: 30

        onConfigSync(42.0f, longPressMs, multiTapMs, autoSpace, slangBoost, decayDays)
        NativeEngineBridge.syncAudioConfig(isAudioEnabled(), getAudioVolume(), getAudioStyle())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        syncAll()
        if (key == "keyboard_height" || key == "candidate_font_size" || key == "one_handed_mode" ||
            key == "col_4th_position" || key == "row_4th_position" ||
            key == "row_4th_order" || key == "col_4th_order" ||
            key == "long_press_delay" ||
            key == "space_scrubbing" || key == "space_scrubbing_sensitivity" || key == "space_scrubbing_hold"
        ) {
            onLayoutConfigChanged?.invoke()
        }
        if (key == "app_theme") {
            onThemeConfigChanged?.invoke()
        }
        if (key == "default_input_mode") {
            onInputModeConfigChanged?.invoke(isDefaultT9())
        }
        if (key == "startup_language") {
            onLanguageConfigChanged?.invoke(getStartupLanguage())
        }
        if (key == "auto_caps") {
            onAutoCapsConfigChanged?.invoke(isAutoCapsEnabled())
        }
    }

    fun isHapticEnabled(): Boolean = prefs.getBoolean("haptic_enabled", true)
    fun getVibrationIntensity(): Long = prefs.getInt("vibration_intensity", 25).toLong()
    fun isAudioEnabled(): Boolean = prefs.getBoolean("audio_enabled", true)
    fun getAudioVolume(): Float = prefs.getInt("audio_volume", 60) / 100f
    fun getAudioStyle(): Int = (prefs.getString("audio_style", "0") ?: "0").toIntOrNull() ?: 0
    fun getStartupLanguage(): String = prefs.getString("startup_language", "ID") ?: "ID"
    fun isDefaultT9(): Boolean = (prefs.getString("default_input_mode", "0") ?: "0") == "0"
    fun isAutoSpaceEnabled(): Boolean = prefs.getBoolean("auto_space", true)
    fun isDoubleSpacePeriodEnabled(): Boolean = prefs.getBoolean("double_space_period", true)
    fun getMultiTapTimeout(): Long = prefs.getInt("multi_tap_timeout", 600).toLong()
    fun isSpaceScrubbingEnabled(): Boolean = prefs.getBoolean("space_scrubbing", true)
    fun getSpaceScrubbingSensitivity(): String = prefs.getString("space_scrubbing_sensitivity", "normal") ?: "normal"
    fun isSpaceScrubbingHoldRequired(): Boolean = prefs.getBoolean("space_scrubbing_hold", false)
    fun isAutoCapsEnabled(): Boolean = prefs.getBoolean("auto_caps", true)
    fun getKeyboardHeightDp(): Int = prefs.getInt("keyboard_height", 260)
    fun getCandidateFontSizeSp(): Int = prefs.getInt("candidate_font_size", 16)
    fun getOneHandedMode(): Int = (prefs.getString("one_handed_mode", "0") ?: "0").toIntOrNull() ?: 0
    fun getLongPressDelay(): Long = prefs.getInt("long_press_delay", 350).toLong()
    fun get4thColumnPosition(): String = prefs.getString("col_4th_position", "right") ?: "right"
    fun get4thRowPosition(): String = prefs.getString("row_4th_position", "bottom") ?: "bottom"
    fun get4thRowOrder(): String = prefs.getString("row_4th_order", "default") ?: "default"
    fun get4thColumnOrder(): String = prefs.getString("col_4th_order", "default") ?: "default"
    fun getAppTheme(): String = prefs.getString("app_theme", "system") ?: "system"
}
