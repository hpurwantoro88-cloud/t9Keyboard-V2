package com.opent9.keyboard.ui

import android.content.res.Configuration
import android.graphics.Paint
import android.util.DisplayMetrics

/**
 * Calculates responsive keyboard height, ergonomic layout width,
 * and dynamic typography based on screen orientation, density, display metrics,
 * and user preferences.
 */
object KeyboardDimensionCalculator {

    /**
     * Calculates the measured keyboard height in pixels, applying landscape constraint clamp (45-48%)
     * and portrait range clamp (220-320dp).
     */
    fun calculateTargetHeight(
        displayMetrics: DisplayMetrics,
        config: Configuration,
        prefHeightDp: Int
    ): Int {
        val density = displayMetrics.density
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val screenHeightDp = if (density > 0f) displayMetrics.heightPixels / density else 800f

        val targetHeightDp = if (isLandscape) {
            // Constrain landscape height to 45%-50% of screen height to avoid covering the text field
            val maxLandscapeHeightDp = if (screenHeightDp > 100f) screenHeightDp * 0.48f else 180f
            (prefHeightDp * (maxLandscapeHeightDp / 260f)).coerceIn(140f, maxLandscapeHeightDp.coerceAtLeast(160f))
        } else {
            // Portrait mode: follow PRD / user preference (220dp to 320dp, default: 260dp)
            val maxPortraitHeightDp = if (screenHeightDp > 100f) screenHeightDp * 0.45f else 320f
            prefHeightDp.toFloat().coerceAtMost(maxPortraitHeightDp.coerceAtLeast(220f))
        }

        return (targetHeightDp * density).toInt()
    }

    /**
     * Calculates content horizontal offset and width for one-handed mode or tablet ergonomic centering.
     */
    fun calculateErgonomicOffsetAndWidth(
        width: Int,
        density: Float,
        isLandscape: Boolean,
        oneHandedMode: Int
    ): Pair<Float, Float> {
        val w = width.toFloat()
        val widthDp = if (density > 0f) width / density else 360f
        val maxErgonomicWidthPx = 480f * density

        return when {
            oneHandedMode == 1 -> Pair(0f, w * 0.85f)
            oneHandedMode == 2 -> Pair(w * 0.15f, w * 0.85f)
            widthDp > 600f && !isLandscape -> {
                val cWidth = minOf(w, maxErgonomicWidthPx)
                Pair((w - cWidth) / 2f, cWidth)
            }
            else -> Pair(0f, w)
        }
    }

    /**
     * Updates typography and stroke widths on pre-allocated paint objects dynamically.
     */
    fun applyDynamicTypography(
        displayMetrics: DisplayMetrics,
        config: Configuration,
        rowHeight: Float,
        stripHeight: Float,
        p1ContainerHeight: Float,
        candFontSizeSp: Float,
        primaryTextPaint: Paint,
        subTextPaint: Paint,
        keyCornerSubTextPaint: Paint,
        dualSymTextPaint: Paint,
        pillTextPaint: Paint,
        candidateTextPaint: Paint,
        candidatePrefixPaint: Paint,
        keyBorderPaint: Paint,
        dividerPaint: Paint,
        iconPaint: Paint,
        page1DigitTextPaint: Paint,
        page1OpTextPaint: Paint,
        page1SymTextPaint: Paint,
        page1SmallTextPaint: Paint
    ) {
        val density = displayMetrics.density
        val fontScale = config.fontScale
        val scaledDensity = density * if (fontScale > 0f) fontScale else 1f

        val baseRowHeight = 55f * density
        val rowScale = if (baseRowHeight > 0f) (rowHeight / baseRowHeight).coerceIn(0.60f, 1.40f) else 1.0f

        // Dynamic typography based on row and strip sizes
        primaryTextPaint.textSize = 16.5f * density * rowScale
        subTextPaint.textSize = 10f * density * rowScale
        keyCornerSubTextPaint.textSize = 9.5f * density * rowScale
        dualSymTextPaint.textSize = 13.5f * density * rowScale
        pillTextPaint.textSize = (stripHeight * 0.28f).coerceIn(12f * density, 20f * density)

        val candSize = (candFontSizeSp * scaledDensity).coerceAtMost(stripHeight * 0.52f)
        candidateTextPaint.textSize = candSize
        candidatePrefixPaint.textSize = candSize

        // Stroke widths
        keyBorderPaint.strokeWidth = 0.5f * density
        dividerPaint.strokeWidth = (density * 0.67f).coerceIn(1f, 3f)
        iconPaint.strokeWidth = (density * 1.33f).coerceIn(2.5f, 5f)

        // Page 1 Dedicated Paints
        val p1RowScale = if (p1ContainerHeight > 0f && baseRowHeight > 0f) {
            ((p1ContainerHeight / 3f) / baseRowHeight).coerceIn(0.60f, 1.40f)
        } else rowScale
        page1DigitTextPaint.textSize = 16.5f * density * p1RowScale
        page1OpTextPaint.textSize = 12.67f * density * p1RowScale
        page1SymTextPaint.textSize = 13.5f * density * p1RowScale
        page1SmallTextPaint.textSize = 11.33f * density * p1RowScale
    }
}
