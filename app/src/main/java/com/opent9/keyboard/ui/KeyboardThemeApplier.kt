package com.opent9.keyboard.ui

import android.graphics.Paint

/**
 * Handles color scheme application to keyboard Paint instances.
 * Pure logic, avoids bloating T9KeyboardView.
 */
object KeyboardThemeApplier {

    fun applyThemeColors(
        colors: KeyboardColors,
        backgroundPaint: Paint,
        keyBackgroundPaint: Paint,
        keyPressedPaint: Paint,
        keyBorderPaint: Paint,
        primaryTextPaint: Paint,
        subTextPaint: Paint,
        keyCornerSubTextPaint: Paint,
        dualSymTextPaint: Paint,
        stripBackgroundPaint: Paint,
        pillPaint: Paint,
        pillTextPaint: Paint,
        candidateTextPaint: Paint,
        candidatePrefixPaint: Paint,
        dividerPaint: Paint,
        indicatorGlowPaint: Paint,
        iconPaint: Paint,
        iconFillPaint: Paint,
        page1KeyActionPaint: Paint,
        page1DigitTextPaint: Paint,
        page1OpTextPaint: Paint,
        page1SymTextPaint: Paint,
        page1SmallTextPaint: Paint
    ) {
        backgroundPaint.color = colors.background
        keyBackgroundPaint.color = colors.keyBackground
        keyPressedPaint.color = colors.keyPressed
        keyBorderPaint.color = colors.keyBorder
        primaryTextPaint.color = colors.primaryText
        subTextPaint.color = colors.subText
        keyCornerSubTextPaint.color = colors.subText
        dualSymTextPaint.color = colors.dualSymText
        stripBackgroundPaint.color = colors.stripBackground
        pillPaint.color = colors.pillBackground
        pillTextPaint.color = colors.pillText
        candidateTextPaint.color = colors.candidateText
        candidatePrefixPaint.color = colors.candidatePrefixText
        dividerPaint.color = colors.divider
        indicatorGlowPaint.color = colors.indicatorGlow
        iconPaint.color = colors.iconColor
        iconFillPaint.color = colors.iconColor
        page1KeyActionPaint.color = colors.page1KeyAction
        page1DigitTextPaint.color = colors.page1DigitText
        page1OpTextPaint.color = colors.page1OpText
        page1SymTextPaint.color = colors.page1SymText
        page1SmallTextPaint.color = colors.page1SmallText
    }
}
