package com.opent9.keyboard.ui

import android.text.InputType
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.opent9.keyboard.settings.SettingsObserver

/**
 * Encapsulates auto-capitalization logic, inspecting cursor context and EditorInfo inputType flags
 * to calculate and apply appropriate ShiftMode transitions.
 */
class AutoCapsController(
    private val shiftController: ShiftController,
    private val settingsObserver: SettingsObserver
) {

    /**
     * Updates auto-capitalization based on input connection cursor caps mode and editor flags.
     * Returns true if ShiftMode changed and UI should update its shift indicator.
     */
    fun updateAutoCaps(
        ic: InputConnection?,
        info: EditorInfo?,
        clearManualOverride: Boolean = false
    ): Boolean {
        if (ic == null || info == null) return false

        if (!settingsObserver.isAutoCapsEnabled()) {
            if (shiftController.currentMode != ShiftMode.LOWERCASE && !shiftController.isCapsLocked()) {
                shiftController.reset()
                return true
            }
            return false
        }

        if (clearManualOverride) {
            shiftController.clearManualOverride()
        }

        if (shiftController.isCapsLocked() || shiftController.isManualOverrideActive()) {
            return false
        }

        val inputType = info.inputType
        val clazz = inputType and InputType.TYPE_MASK_CLASS
        if (clazz != InputType.TYPE_CLASS_TEXT) {
            if (shiftController.currentMode != ShiftMode.LOWERCASE && !shiftController.isCapsLocked()) {
                shiftController.reset()
                return true
            }
            return false
        }

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isExcluded = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI ||
                variation == InputType.TYPE_TEXT_VARIATION_FILTER

        if (isExcluded) {
            if (shiftController.currentMode != ShiftMode.LOWERCASE && !shiftController.isCapsLocked()) {
                shiftController.reset()
                return true
            }
            return false
        }

        var reqModes = inputType and (
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
        if (reqModes == 0) {
            reqModes = TextUtils.CAP_MODE_SENTENCES
        }

        val caps = ic.getCursorCapsMode(reqModes)
        val targetMode = when {
            (caps and TextUtils.CAP_MODE_CHARACTERS) != 0 -> ShiftMode.UPPERCASE
            (caps and (TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES)) != 0 -> ShiftMode.TITLECASE
            else -> ShiftMode.LOWERCASE
        }

        return shiftController.setAutoCapsMode(targetMode)
    }
}
