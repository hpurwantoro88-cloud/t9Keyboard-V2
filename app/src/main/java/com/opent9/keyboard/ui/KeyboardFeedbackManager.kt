package com.opent9.keyboard.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.settings.SettingsObserver

class KeyboardFeedbackManager(
    private val context: Context,
    private val settingsObserver: () -> SettingsObserver?
) {

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private val audioManager: AudioManager? by lazy {
        try {
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        } catch (_: Exception) {
            null
        }
    }

    fun playClickFeedback() {
        val observer = settingsObserver()
        val audioEnabled = observer?.isAudioEnabled() ?: true
        if (audioEnabled) {
            val volume = observer?.getAudioVolume() ?: 0.6f
            val style = observer?.getAudioStyle() ?: 0
            if (volume > 0f) {
                if (NativeEngineBridge.isNativeLoaded) {
                    NativeEngineBridge.playClick(style, volume)
                } else {
                    try {
                        audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, volume)
                    } catch (_: Exception) {}
                }
            }
        }

        val hapticEnabled = observer?.isHapticEnabled() ?: true
        if (hapticEnabled) {
            val intensity = observer?.getVibrationIntensity() ?: 25L
            if (intensity > 0L) {
                try {
                    val vib = vibrator
                    if (vib != null && vib.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vib.vibrate(
                                VibrationEffect.createOneShot(
                                    intensity.coerceIn(1L, 100L),
                                    VibrationEffect.DEFAULT_AMPLITUDE
                                )
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            vib.vibrate(intensity.coerceIn(1L, 100L))
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
