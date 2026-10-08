package com.opent9.keyboard.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Dedicated renderer for Page 1 (Numeric & Math/Symbol Keypad + Left Scrollable Operator Column).
 * Guarantees zero heap allocations per draw frame.
 */
class Page1NumSymRenderer(
    private val backgroundPaint: Paint,
    private val page1KeyActionPaint: Paint,
    private val keyBorderPaint: Paint,
    private val keyPressedPaint: Paint,
    private val keyBackgroundPaint: Paint,
    private val page1OpTextPaint: Paint,
    private val page1DigitTextPaint: Paint,
    private val page1SmallTextPaint: Paint,
    private val page1SymTextPaint: Paint,
    private val iconPaint: Paint,
    private val iconFillPaint: Paint,
    private val page1ClipPath: Path = Path(),
    private val scratchRect: RectF = RectF(),
    private val iconPath: Path = Path()
) {

    /**
     * Renders Page 1 Num/Sym layout.
     */
    fun drawPage1(
        canvas: Canvas,
        width: Float,
        height: Float,
        keyAtlas: KeyAtlas,
        columnScrollOffset: Float,
        activeOperatorIndex: Int?,
        activeKeyId: Int?,
        imeAction: Int,
        density: Float
    ) {
        val cornerRadius = 10f * density

        // 1. Draw keyboard background matching active theme
        canvas.drawRect(0f, 0f, width, height, backgroundPaint)

        // 2. Draw Left Scrollable Operator Column
        val opContainer = keyAtlas.page1ScrollContainerBounds
        canvas.drawRoundRect(opContainer, cornerRadius, cornerRadius, page1KeyActionPaint)
        canvas.drawRoundRect(opContainer, cornerRadius, cornerRadius, keyBorderPaint)

        canvas.save()
        page1ClipPath.reset()
        page1ClipPath.addRoundRect(opContainer, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(page1ClipPath)

        val slotHeight = opContainer.height() / 4f
        val opItems = keyAtlas.page1OperatorItems
        for (i in opItems.indices) {
            val itemTop = opContainer.top + (i * slotHeight) + columnScrollOffset
            val itemBottom = itemTop + slotHeight

            if (itemBottom < opContainer.top || itemTop > opContainer.bottom) continue

            // Highlight pressed operator
            if (activeOperatorIndex == i) {
                scratchRect.set(
                    opContainer.left + (2f * density),
                    itemTop + (2f * density),
                    opContainer.right - (2f * density),
                    itemBottom - (2f * density)
                )
                canvas.drawRoundRect(scratchRect, 8f * density, 8f * density, keyPressedPaint)
            }

            val itemCenterY = itemTop + (slotHeight / 2f)
            val textY = itemCenterY - ((page1OpTextPaint.descent() + page1OpTextPaint.ascent()) / 2f)
            canvas.drawText(opItems[i], opContainer.centerX(), textY, page1OpTextPaint)
        }
        canvas.restore()

        // 3. Draw Page 1 Keys
        val minDim = minOf(keyAtlas.rowHeight, keyAtlas.colWidth)
        for (key in keyAtlas.page1Keys) {
            val isPressed = (key.id == activeKeyId)
            val bgPaint = if (key.isActionKey) {
                if (isPressed) keyPressedPaint else page1KeyActionPaint
            } else {
                if (isPressed) keyPressedPaint else keyBackgroundPaint
            }

            canvas.drawRoundRect(key.bounds, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(key.bounds, cornerRadius, cornerRadius, keyBorderPaint)

            when (key.type) {
                KeyType.DEL -> {
                    KeyboardVectorIcons.drawDelIcon(canvas, key.centerX, key.centerY, density, minDim, iconPath, iconPaint)
                }
                KeyType.ENTER -> {
                    KeyboardVectorIcons.drawEnterIcon(canvas, key.centerX, key.centerY, density, imeAction, minDim, iconPath, iconPaint, iconFillPaint)
                }
                KeyType.SPACE_0 -> {
                    KeyboardVectorIcons.drawPage1SpaceIcon(canvas, key.centerX, key.centerY, density, minDim, iconPath, iconPaint)
                }
                else -> {
                    val paint = when (key.primaryLabel) {
                        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" -> page1DigitTextPaint
                        "ABC", "!?#" -> page1SmallTextPaint
                        else -> page1SymTextPaint
                    }
                    val textY = key.centerY - ((paint.descent() + paint.ascent()) / 2f)
                    canvas.drawText(key.primaryLabel, key.centerX, textY, paint)
                }
            }
        }
    }
}
