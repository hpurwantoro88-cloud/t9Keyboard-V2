package com.opent9.keyboard.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.opent9.keyboard.R
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.prediction.LearnedWordsRepository
import com.opent9.keyboard.ui.KeyboardFeedbackManager

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val themePref = prefs.getString("app_theme", "system") ?: "system"
        applyNightMode(themePref)

        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    companion object {
        fun applyNightMode(themeMode: String) {
            val mode = when (themeMode.lowercase()) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val ctx = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val feedbackManager = KeyboardFeedbackManager(ctx) { null }

        findPreference<Preference>("app_theme")?.setOnPreferenceChangeListener { _, newValue ->
            val modeStr = newValue as? String ?: "system"
            SettingsActivity.applyNightMode(modeStr)
            true
        }

        findPreference<Preference>("haptic_enabled")?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as? Boolean ?: true
            if (enabled) {
                val intensity = prefs.getInt("vibration_intensity", 25).toLong()
                feedbackManager.playHapticPreview(intensity)
            }
            true
        }

        findPreference<Preference>("vibration_intensity")?.setOnPreferenceChangeListener { _, newValue ->
            val intensity = (newValue as? Number)?.toLong() ?: 25L
            feedbackManager.playHapticPreview(intensity)
            true
        }

        findPreference<Preference>("audio_enabled")?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as? Boolean ?: true
            val volume = prefs.getInt("audio_volume", 60) / 100f
            val style = (prefs.getString("audio_style", "0") ?: "0").toIntOrNull() ?: 0
            NativeEngineBridge.syncAudioConfig(enabled, volume, style)
            if (enabled) {
                NativeEngineBridge.startAudio()
                feedbackManager.playAudioPreview(style, volume, respectRingerMode = false)
            } else {
                NativeEngineBridge.stopAudio()
            }
            true
        }

        findPreference<Preference>("audio_volume")?.setOnPreferenceChangeListener { _, newValue ->
            val volPct = (newValue as? Number)?.toInt() ?: 60
            val volume = (volPct / 100f).coerceIn(0f, 1f)
            val style = (prefs.getString("audio_style", "0") ?: "0").toIntOrNull() ?: 0
            NativeEngineBridge.syncAudioConfig(true, volume, style)
            NativeEngineBridge.startAudio()
            feedbackManager.playAudioPreview(style, volume, respectRingerMode = false)
            true
        }

        findPreference<Preference>("audio_style")?.setOnPreferenceChangeListener { _, newValue ->
            val style = (newValue as? String)?.toIntOrNull() ?: 0
            val volume = prefs.getInt("audio_volume", 60) / 100f
            NativeEngineBridge.syncAudioConfig(true, volume, style)
            NativeEngineBridge.startAudio()
            feedbackManager.playAudioPreview(style, volume, respectRingerMode = false)
            true
        }

        findPreference<Preference>("rearrange_keyboard_layout")?.setOnPreferenceClickListener {
            val intent = android.content.Intent(requireContext(), RearrangeLayoutActivity::class.java)
            startActivity(intent)
            true
        }

        findPreference<Preference>("reset_user_dictionary")?.setOnPreferenceClickListener {
            context?.let { ctx ->
                AlertDialog.Builder(ctx)
                    .setTitle("Reset User Dictionary")
                    .setMessage("Are you sure you want to clear all learned custom words and personal frequencies?")
                    .setPositiveButton("Reset") { _, _ ->
                        LearnedWordsRepository(ctx).clearAll()
                        Toast.makeText(ctx, "User dictionary cleared", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            true
        }
    }

    override fun onStop() {
        super.onStop()
        NativeEngineBridge.stopAudio()
    }
}
