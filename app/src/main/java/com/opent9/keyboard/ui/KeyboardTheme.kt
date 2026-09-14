package com.opent9.keyboard.ui

import android.content.res.Configuration
import android.graphics.Color

data class KeyboardColors(
    val isDark: Boolean,
    val background: Int,
    val stripBackground: Int,
    val keyBackground: Int,
    val keyPressed: Int,
    val keyBorder: Int,
    val page1KeyAction: Int,
    val pillBackground: Int,
    val pillText: Int,
    val primaryText: Int,
    val subText: Int,
    val dualSymText: Int,
    val candidateText: Int,
    val candidatePrefixText: Int,
    val divider: Int,
    val indicatorGlow: Int,
    val iconColor: Int,
    val page1DigitText: Int,
    val page1OpText: Int,
    val page1SymText: Int,
    val page1SmallText: Int
)

object KeyboardTheme {
    val DARK = KeyboardColors(
        isDark = true,
        background = Color.parseColor("#121214"),
        stripBackground = Color.parseColor("#18181C"),
        keyBackground = Color.parseColor("#1E1E22"),
        keyPressed = Color.parseColor("#34343C"),
        keyBorder = Color.parseColor("#28282E"),
        page1KeyAction = Color.parseColor("#25252C"),
        pillBackground = Color.parseColor("#2A2A32"),
        pillText = Color.parseColor("#00E5FF"),
        primaryText = Color.parseColor("#E0E0E6"),
        subText = Color.parseColor("#8E8E98"),
        dualSymText = Color.parseColor("#E0E0E6"),
        candidateText = Color.parseColor("#FFFFFF"),
        candidatePrefixText = Color.parseColor("#00E5FF"),
        divider = Color.parseColor("#2E2E38"),
        indicatorGlow = Color.parseColor("#00E5FF"),
        iconColor = Color.parseColor("#E0E0E6"),
        page1DigitText = Color.parseColor("#E0E0E6"),
        page1OpText = Color.parseColor("#E0E0E6"),
        page1SymText = Color.parseColor("#E0E0E6"),
        page1SmallText = Color.parseColor("#E0E0E6")
    )

    val LIGHT = KeyboardColors(
        isDark = false,
        background = Color.parseColor("#ECEFF1"),
        stripBackground = Color.parseColor("#E0E3E8"),
        keyBackground = Color.parseColor("#FFFFFF"),
        keyPressed = Color.parseColor("#CFD4DC"),
        keyBorder = Color.parseColor("#CED3DC"),
        page1KeyAction = Color.parseColor("#DCE0E6"),
        pillBackground = Color.parseColor("#D3D8E0"),
        pillText = Color.parseColor("#00838F"),
        primaryText = Color.parseColor("#1C1C1E"),
        subText = Color.parseColor("#5F6368"),
        dualSymText = Color.parseColor("#1C1C1E"),
        candidateText = Color.parseColor("#1C1C1E"),
        candidatePrefixText = Color.parseColor("#00838F"),
        divider = Color.parseColor("#CED2D9"),
        indicatorGlow = Color.parseColor("#00838F"),
        iconColor = Color.parseColor("#1C1C1E"),
        page1DigitText = Color.parseColor("#1C1C1E"),
        page1OpText = Color.parseColor("#1C1C1E"),
        page1SymText = Color.parseColor("#1C1C1E"),
        page1SmallText = Color.parseColor("#1C1C1E")
    )

    fun isSystemInDarkMode(config: Configuration): Boolean {
        return (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun resolveColors(config: Configuration, themeMode: String = "system"): KeyboardColors {
        return when (themeMode.lowercase()) {
            "dark" -> DARK
            "light" -> LIGHT
            else -> if (isSystemInDarkMode(config)) DARK else LIGHT
        }
    }
}
