package com.opent9.keyboard

import android.content.Context
import android.content.Intent
import android.view.inputmethod.EditorInfo
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.ui.EditorContextHelper
import com.opent9.keyboard.settings.SettingsActivity
import com.opent9.keyboard.settings.SettingsObserver
import com.opent9.keyboard.ui.T9KeyboardView

/**
 * Encapsulates IME lifecycle settings observer configuration and settings launcher.
 */
object ImeLifecycleConfigurator {

    fun setupObserverCallbacks(
        settingsObserver: SettingsObserver,
        getKeyboardView: () -> T9KeyboardView?,
        isPasswordMode: () -> Boolean,
        getCurrentEditorInfo: () -> EditorInfo?,
        onAutoCapsChanged: () -> Unit
    ) {
        settingsObserver.onLayoutConfigChanged = {
            val view = getKeyboardView()
            if (view != null) {
                view.post {
                    view.requestLayout()
                    view.reloadLayoutConfiguration()
                }
            }
        }

        settingsObserver.onThemeConfigChanged = {
            val view = getKeyboardView()
            if (view != null) {
                view.post {
                    view.updateThemeFromConfiguration(view.resources.configuration)
                }
            }
        }

        settingsObserver.onInputModeConfigChanged = { isT9 ->
            val view = getKeyboardView()
            if (view != null && !isPasswordMode()) {
                view.post {
                    if (!EditorContextHelper.isNumeric(getCurrentEditorInfo())) {
                        view.setT9Mode(isT9)
                    }
                }
            }
        }

        settingsObserver.onLanguageConfigChanged = { newLang ->
            NativeEngineBridge.switchLanguage(newLang)
            val view = getKeyboardView()
            if (view != null) {
                view.post {
                    view.setLanguage(newLang)
                }
            }
        }

        settingsObserver.onAutoCapsConfigChanged = {
            onAutoCapsChanged()
        }
    }

    fun launchSettings(context: Context) {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
