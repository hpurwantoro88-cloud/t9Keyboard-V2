package com.opent9.keyboard.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Helper to inspect [EditorInfo] flags for password, incognito, and numeric input modes.
 */
object EditorContextHelper {

    /**
     * Determines whether the editor represents a password or PIN field.
     */
    fun isPassword(info: EditorInfo?): Boolean {
        if (info == null) return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    /**
     * Determines whether personal learning should be disabled (incognito mode or password fields).
     */
    fun isIncognito(info: EditorInfo?): Boolean {
        if (info == null) return false
        return ((info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) || isPassword(info)
    }

    /**
     * Determines whether the field is purely numeric, telephone, or datetime.
     */
    fun isNumeric(info: EditorInfo?): Boolean {
        if (info == null) return false
        val clazz = info.inputType and InputType.TYPE_MASK_CLASS
        return clazz == InputType.TYPE_CLASS_NUMBER ||
                clazz == InputType.TYPE_CLASS_PHONE ||
                clazz == InputType.TYPE_CLASS_DATETIME
    }
}
