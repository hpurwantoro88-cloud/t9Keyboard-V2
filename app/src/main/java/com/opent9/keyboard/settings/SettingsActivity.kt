package com.opent9.keyboard.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.opent9.keyboard.R
import com.opent9.keyboard.jni.NativeEngineBridge

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>("reset_user_dictionary")?.setOnPreferenceClickListener {
            context?.let { ctx ->
                AlertDialog.Builder(ctx)
                    .setTitle("Reset User Dictionary")
                    .setMessage("Are you sure you want to clear all learned custom words and personal frequencies?")
                    .setPositiveButton("Reset") { _, _ ->
                        NativeEngineBridge.nativeResetUserDictionary()
                        Toast.makeText(ctx, "User dictionary cleared", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            true
        }
    }
}
