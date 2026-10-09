package com.opent9.keyboard.ui

import android.graphics.Color
import android.graphics.Paint

/**
 * Factory for creating pre-configured Paint instances for T9 keyboard canvas rendering.
 * Encapsulates initial paint configuration to keep T9KeyboardView lean.
 */
object KeyboardPaintFactory {

    fun createFillPaint(color: Int = Color.BLACK, antiAlias: Boolean = true): Paint {
        return Paint().apply {
            this.color = color
            this.style = Paint.Style.FILL
            this.isAntiAlias = antiAlias
        }
    }

    fun createStrokePaint(
        width: Float,
        color: Int = Color.BLACK,
        roundCap: Boolean = false,
        antiAlias: Boolean = true
    ): Paint {
        return Paint().apply {
            this.color = color
            this.style = Paint.Style.STROKE
            this.strokeWidth = width
            if (roundCap) {
                this.strokeCap = Paint.Cap.ROUND
                this.strokeJoin = Paint.Join.ROUND
            }
            this.isAntiAlias = antiAlias
        }
    }

    fun createTextPaint(
        textSize: Float,
        align: Paint.Align = Paint.Align.CENTER,
        isBold: Boolean = false,
        color: Int = Color.WHITE,
        antiAlias: Boolean = true
    ): Paint {
        return Paint().apply {
            this.color = color
            this.isAntiAlias = antiAlias
            this.textAlign = align
            this.textSize = textSize
            this.isFakeBoldText = isBold
        }
    }
}
