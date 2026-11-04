package com.opent9.keyboard.settings

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.opent9.keyboard.R

class RearrangeLayoutActivity : AppCompatActivity() {

    private lateinit var rearrangeKeyboardView: RearrangeKeyboardView

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val themePref = prefs.getString("app_theme", "system") ?: "system"
        SettingsActivity.applyNightMode(themePref)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rearrange_layout)

        supportActionBar?.apply {
            title = getString(R.string.rearrange_title)
            setDisplayHomeAsUpEnabled(true)
        }

        rearrangeKeyboardView = findViewById(R.id.rearrange_keyboard_view)

        // Load current custom order
        val savedRaw = prefs.getString(KeyboardLayoutConfig.PREF_KEY, null)
        val currentOrder = KeyboardLayoutConfig.deserialize(savedRaw)
        rearrangeKeyboardView.setLayoutOrder(currentOrder)

        val btnReset = findViewById<Button>(R.id.btn_reset_layout)
        val btnSave = findViewById<Button>(R.id.btn_save_layout)

        btnReset.setOnClickListener {
            rearrangeKeyboardView.resetToDefault()
            Toast.makeText(this, R.string.toast_layout_reset, Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener {
            val order = rearrangeKeyboardView.getLayoutOrder()
            val serialized = KeyboardLayoutConfig.serialize(order)
            prefs.edit().putString(KeyboardLayoutConfig.PREF_KEY, serialized).apply()
            Toast.makeText(this, R.string.toast_layout_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
