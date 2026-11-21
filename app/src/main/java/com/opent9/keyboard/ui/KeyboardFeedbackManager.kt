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

    private fun isRingerNormal(): Boolean {
        val am = audioManager ?: return true
        return try {
            am.ringerMode == AudioManager.RINGER_MODE_NORMAL
        } catch (_: Exception) {
            true
        }
    }

    fun playAudioPreview(style: Int, volume: Float, respectRingerMode: Boolean = true) {
        if (volume <= 0f) return
        if (respectRingerMode && !isRingerNormal()) return
        if (NativeEngineBridge.isNativeLoaded) {
            NativeEngineBridge.playClick(style, volume)
        } else {
            try {
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, volume)
            } catch (_: Exception) {}
        }
    }

    fun playHapticPreview(intensityMs: Long) {
        if (intensityMs <= 0L) return
        try {
            val vib = vibrator
            if (vib != null && vib.hasVibrator()) {
                val safeMs = intensityMs.coerceIn(1L, 100L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = if (vib.hasAmplitudeControl()) {
                        (40 + ((safeMs - 1L) * 215L / 99L)).toInt().coerceIn(1, 255)
                    } else {
                        VibrationEffect.DEFAULT_AMPLITUDE
                    }
                    vib.vibrate(VibrationEffect.createOneShot(safeMs, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(safeMs)
                }
            }
        } catch (_: Exception) {}
    }

    fun playClickFeedback() {
        val observer = settingsObserver()
        val audioEnabled = observer?.isAudioEnabled() ?: true
        if (audioEnabled) {
            val volume = observer?.getAudioVolume() ?: 0.6f
            val style = observer?.getAudioStyle() ?: 0
            playAudioPreview(style, volume, respectRingerMode = true)
        }

        val hapticEnabled = observer?.isHapticEnabled() ?: true
        if (hapticEnabled) {
            val intensity = observer?.getVibrationIntensity() ?: 25L
            playHapticPreview(intensity)
        }
    }
}
