package com.opent9.keyboard.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.opent9.keyboard.R
import com.opent9.keyboard.ui.KeyAtlas
import com.opent9.keyboard.ui.KeyInfo
import com.opent9.keyboard.ui.KeyType
import com.opent9.keyboard.ui.KeyboardPaintFactory
import com.opent9.keyboard.ui.KeyboardTheme
import com.opent9.keyboard.ui.KeyboardVectorIcons

/**
 * Interactive virtual keyboard canvas for rearranging Row 4 and Column 4 keys.
 * Functions like a sliding tile puzzle: keys slide dynamically along the track
 * when dragged or can be swapped by tapping.
 */
class RearrangeKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val keyAtlas = KeyAtlas()
    var currentOrder: MutableList<RearrangeKeyId> = KeyboardLayoutConfig.DEFAULT_ORDER.toMutableList()
        private set

    // Touch & Drag state
    private var activeKey: RearrangeKeyId? = null
    private var selectedKey: RearrangeKeyId? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var isDragging = false

    private val touchSlop: Float
        get() = 8f * resources.displayMetrics.density

    // Paints
    private val backgroundPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#121214"), antiAlias = false)
    private val keyBackgroundPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#1E1E22"))
    private val fixedKeyPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#18181C"))
    private val keyPressedPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#34343C"))
    private val keyBorderPaint = KeyboardPaintFactory.createStrokePaint(1.5f, Color.parseColor("#28282E"))
    private val movableBorderPaint = KeyboardPaintFactory.createStrokePaint(2f, Color.parseColor("#00E5FF"))
    private val selectedBorderPaint = KeyboardPaintFactory.createStrokePaint(3f, Color.parseColor("#FFD600"))
    private val ghostBorderPaint = KeyboardPaintFactory.createStrokePaint(2f, Color.parseColor("#00E5FF")).apply {
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val draggedFillPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#2E3544"))
    private val draggedBorderPaint = KeyboardPaintFactory.createStrokePaint(3f, Color.parseColor("#00E5FF"))

    private val primaryTextPaint = KeyboardPaintFactory.createTextPaint(42f, Paint.Align.CENTER, color = Color.parseColor("#E0E0E6"))
    private val fixedTextPaint = KeyboardPaintFactory.createTextPaint(42f, Paint.Align.CENTER, color = Color.parseColor("#9E9EA8"))
    private val subTextPaint = KeyboardPaintFactory.createTextPaint(22f, Paint.Align.CENTER, color = Color.parseColor("#787884"))
    private val keyCornerSubTextPaint = KeyboardPaintFactory.createTextPaint(20f, Paint.Align.RIGHT, color = Color.parseColor("#8E8E98"))
    private val stripBackgroundPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#18181C"), antiAlias = false)
    private val pillPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#2A2A32"))
    private val pillTextPaint = KeyboardPaintFactory.createTextPaint(26f, Paint.Align.CENTER, isBold = true, color = Color.parseColor("#00E5FF"))
    private val stripHintPaint = KeyboardPaintFactory.createTextPaint(28f, Paint.Align.LEFT, color = Color.parseColor("#8E8E98"))
    private val indicatorGlowPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#00E5FF"))
    private val iconPaint = KeyboardPaintFactory.createStrokePaint(4f, Color.parseColor("#E0E0E6"), roundCap = true)
    private val iconFillPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#E0E0E6"))
    private val dragHandlePaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#5000E5FF"))

    private val scratchRect = RectF()
    private val glowRect = RectF()
    private val iconPath = Path()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        updateTheme()
    }

    fun updateTheme() {
        val config = resources.configuration
        val isDark = KeyboardTheme.isSystemInDarkMode(config)
        val colors = if (isDark) KeyboardTheme.DARK else KeyboardTheme.LIGHT

        backgroundPaint.color = colors.background
        keyBackgroundPaint.color = colors.keyBackground
        fixedKeyPaint.color = if (isDark) Color.parseColor("#19191D") else Color.parseColor("#F0F2F5")
        keyPressedPaint.color = colors.keyPressed
        keyBorderPaint.color = colors.keyBorder
        primaryTextPaint.color = colors.primaryText
        fixedTextPaint.color = colors.subText
        subTextPaint.color = colors.subText
        keyCornerSubTextPaint.color = colors.subText
        stripBackgroundPaint.color = colors.stripBackground
        pillPaint.color = colors.pillBackground
        pillTextPaint.color = colors.pillText
        stripHintPaint.color = colors.subText
        indicatorGlowPaint.color = colors.indicatorGlow
        iconPaint.color = colors.iconColor
        iconFillPaint.color = colors.iconColor
        movableBorderPaint.color = colors.indicatorGlow
        draggedBorderPaint.color = colors.indicatorGlow
        draggedFillPaint.color = if (isDark) Color.parseColor("#253040") else Color.parseColor("#D0E8F2")
        ghostBorderPaint.color = colors.indicatorGlow
        invalidate()
    }

    fun setLayoutOrder(order: List<RearrangeKeyId>) {
        currentOrder = order.toMutableList()
        keyAtlas.setLayoutOrder(currentOrder)
        if (width > 0 && height > 0) {
            keyAtlas.computeGeometry(width.toFloat(), height.toFloat(), resources.displayMetrics.density)
        }
        invalidate()
    }

    fun getLayoutOrder(): List<RearrangeKeyId> = currentOrder.toList()

    fun resetToDefault() {
        currentOrder = KeyboardLayoutConfig.DEFAULT_ORDER.toMutableList()
        selectedKey = null
        activeKey = null
        isDragging = false
        keyAtlas.setLayoutOrder(currentOrder)
        if (width > 0 && height > 0) {
            keyAtlas.computeGeometry(width.toFloat(), height.toFloat(), resources.displayMetrics.density)
        }
        performHaptic(40)
        invalidate()
    }

    private fun performHaptic(durationMs: Long = 20) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val targetHeight = (260f * density).toInt()
        setMeasuredDimension(width, targetHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val density = resources.displayMetrics.density
        keyAtlas.setLayoutOrder(currentOrder)
        keyAtlas.computeGeometry(w.toFloat(), h.toFloat(), density, 0f, w.toFloat())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                currentTouchX = x
                currentTouchY = y
                isDragging = false

                // Hit test movable keys
                val touched = findMovableKeyAt(x, y)
                if (touched != null) {
                    activeKey = touched
                    performHaptic(15)
                    invalidate()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val curKey = activeKey ?: return false
                currentTouchX = x
                currentTouchY = y
                val dx = x - touchDownX
                val dy = y - touchDownY

                if (!isDragging && (dx * dx + dy * dy >= touchSlop * touchSlop)) {
                    isDragging = true
                    selectedKey = null
                }

                if (isDragging) {
                    val closestSlot = findClosestTrackSlot(x, y)
                    val targetKey = findKeyAtTrackSlot(closestSlot)

                    if (targetKey != null && targetKey != curKey) {
                        val fromIdx = currentOrder.indexOf(curKey)
                        val toIdx = currentOrder.indexOf(targetKey)
                        if (fromIdx != -1 && toIdx != -1) {
                            val item = currentOrder.removeAt(fromIdx)
                            currentOrder.add(toIdx, item)
                            keyAtlas.applyPage0RearrangedLayout(currentOrder)
                            performHaptic(20)
                        }
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val curKey = activeKey
                if (curKey != null) {
                    if (!isDragging) {
                        // Tap selection or tap-to-swap
                        val sel = selectedKey
                        if (sel == null) {
                            selectedKey = curKey
                            performHaptic(25)
                        } else if (sel == curKey) {
                            selectedKey = null
                        } else {
                            // Swap sel and curKey
                            val idx1 = currentOrder.indexOf(sel)
                            val idx2 = currentOrder.indexOf(curKey)
                            if (idx1 != -1 && idx2 != -1) {
                                val temp = currentOrder[idx1]
                                currentOrder[idx1] = currentOrder[idx2]
                                currentOrder[idx2] = temp
                                keyAtlas.applyPage0RearrangedLayout(currentOrder)
                                performHaptic(35)
                            }
                            selectedKey = null
                        }
                    } else {
                        performHaptic(25)
                    }
                    activeKey = null
                    isDragging = false
                    keyAtlas.applyPage0RearrangedLayout(currentOrder)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                activeKey = null
                isDragging = false
                keyAtlas.applyPage0RearrangedLayout(currentOrder)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findMovableKeyAt(x: Float, y: Float): RearrangeKeyId? {
        for (item in currentOrder) {
            val key = keyAtlas.keys[item.id]
            if (key.bounds.contains(x, y)) {
                return item
            }
        }
        return null
    }

    private fun findClosestTrackSlot(x: Float, y: Float): Int {
        var closestSlot = 0
        var minDistanceSq = Float.MAX_VALUE

        val colW = keyAtlas.colWidth
        val rowH = keyAtlas.rowHeight
        val stripH = keyAtlas.stripHeight

        for (i in KeyboardLayoutConfig.TRACK_SLOTS.indices) {
            val coord = KeyboardLayoutConfig.TRACK_SLOTS[i]
            val cx = (coord.col + 0.5f) * colW
            val cy = stripH + (coord.row + 0.5f) * rowH
            val dx = x - cx
            val dy = y - cy
            val distSq = dx * dx + dy * dy
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestSlot = i
            }
        }
        return closestSlot
    }

    private fun findKeyAtTrackSlot(slotIndex: Int): RearrangeKeyId? {
        var currentSlot = 0
        for (item in currentOrder) {
            if (slotIndex in currentSlot until (currentSlot + item.span)) {
                return item
            }
            currentSlot += item.span
        }
        return null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val cornerRadius = 10f * density
        val keyMargin = 3f * density

        // 1. Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // 2. Suggestion / Header Strip
        drawHeaderStrip(canvas, density)

        // 3. Fixed Dial Keys (1-9)
        drawFixedDialKeys(canvas, density, cornerRadius, keyMargin)

        // 4. Movable Puzzle Keys
        drawMovableKeys(canvas, density, cornerRadius, keyMargin)

        // 5. Draw the dragged key lifted under finger
        if (activeKey != null && isDragging) {
            drawDraggedFloatingKey(canvas, density, cornerRadius, keyMargin)
        }
    }

    private fun drawHeaderStrip(canvas: Canvas, density: Float) {
        val stripHeight = keyAtlas.stripHeight
        val pillWidth = width * 0.28f
        scratchRect.set(0f, 0f, width.toFloat(), stripHeight)
        canvas.drawRect(scratchRect, stripBackgroundPaint)

        val pillMargin = 4f * density
        scratchRect.set(pillMargin, pillMargin, pillWidth - pillMargin, stripHeight - pillMargin)
        canvas.drawRoundRect(scratchRect, 8f * density, 8f * density, pillPaint)

        val pillTextY = scratchRect.centerY() - ((pillTextPaint.descent() + pillTextPaint.ascent()) / 2f)
        canvas.drawText("PUZZLE", scratchRect.centerX(), pillTextY, pillTextPaint)

        val hintX = pillWidth + 8f * density
        val hintY = (stripHeight / 2f) - ((stripHintPaint.descent() + stripHintPaint.ascent()) / 2f)
        val hintText = context.getString(R.string.rearrange_strip_hint)
        canvas.drawText(hintText, hintX, hintY, stripHintPaint)
    }

    private fun drawFixedDialKeys(canvas: Canvas, density: Float, cornerRadius: Float, keyMargin: Float) {
        val dialIds = intArrayOf(0, 1, 2, 4, 5, 6, 8, 9, 10)
        for (id in dialIds) {
            val key = keyAtlas.keys[id]
            if (key.bounds.isEmpty) continue

            scratchRect.set(
                key.bounds.left + keyMargin,
                key.bounds.top + keyMargin,
                key.bounds.right - keyMargin,
                key.bounds.bottom - keyMargin
            )

            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, fixedKeyPaint)
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, keyBorderPaint)

            // Primary label
            val labelY = key.centerY - ((fixedTextPaint.descent() + fixedTextPaint.ascent()) / 2f)
            val originalSize = fixedTextPaint.textSize
            if (key.primaryLabel.length > 4) {
                fixedTextPaint.textSize = originalSize * 0.72f
            }
            canvas.drawText(key.primaryLabel, key.centerX, labelY, fixedTextPaint)
            if (key.primaryLabel.length > 4) {
                fixedTextPaint.textSize = originalSize
            }

            // Sublabel
            if (key.subLabel.isNotEmpty()) {
                val numX = scratchRect.right - (7f * density)
                val numY = scratchRect.top + (12f * density)
                canvas.drawText(key.subLabel, numX, numY, keyCornerSubTextPaint)
            }
        }
    }

    private fun drawMovableKeys(canvas: Canvas, density: Float, cornerRadius: Float, keyMargin: Float) {
        for (item in currentOrder) {
            val key = keyAtlas.keys[item.id]
            if (key.bounds.isEmpty) continue

            scratchRect.set(
                key.bounds.left + keyMargin,
                key.bounds.top + keyMargin,
                key.bounds.right - keyMargin,
                key.bounds.bottom - keyMargin
            )

            // If this key is actively being dragged, draw ghost slot placeholder
            if (item == activeKey && isDragging) {
                canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, ghostBorderPaint)
                continue
            }

            // Normal key background
            val isSelected = (item == selectedKey)
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, keyBackgroundPaint)

            // Border (highlighted border for movable keys, gold for selected)
            val borderPaint = if (isSelected) selectedBorderPaint else movableBorderPaint
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, borderPaint)

            // Subtle drag handle dots in top-left
            drawDragHandleIndicator(canvas, scratchRect.left + 8f * density, scratchRect.top + 8f * density, density)

            // Draw content
            drawKeyContent(canvas, key, scratchRect, density)
        }
    }

    private fun drawDraggedFloatingKey(canvas: Canvas, density: Float, cornerRadius: Float, keyMargin: Float) {
        val draggedItem = activeKey ?: return
        val key = keyAtlas.keys[draggedItem.id]
        val keyW = key.bounds.width()
        val keyH = key.bounds.height()

        val halfW = keyW / 2f
        val halfH = keyH / 2f

        scratchRect.set(
            currentTouchX - halfW + keyMargin,
            currentTouchY - halfH + keyMargin,
            currentTouchX + halfW - keyMargin,
            currentTouchY + halfH - keyMargin
        )

        // Drop shadow / elevated background
        canvas.drawRoundRect(scratchRect, cornerRadius * 1.2f, cornerRadius * 1.2f, draggedFillPaint)
        canvas.drawRoundRect(scratchRect, cornerRadius * 1.2f, cornerRadius * 1.2f, draggedBorderPaint)

        // Draw content at currentTouchX, currentTouchY
        val fakeKey = key.copy()
        fakeKey.centerX = scratchRect.centerX()
        fakeKey.centerY = scratchRect.centerY()
        fakeKey.bounds.set(scratchRect)

        drawKeyContent(canvas, fakeKey, scratchRect, density)
    }

    private fun drawDragHandleIndicator(canvas: Canvas, x: Float, y: Float, density: Float) {
        val dotRadius = 1.5f * density
        val spacing = 4f * density
        canvas.drawCircle(x, y, dotRadius, dragHandlePaint)
        canvas.drawCircle(x + spacing, y, dotRadius, dragHandlePaint)
        canvas.drawCircle(x, y + spacing, dotRadius, dragHandlePaint)
        canvas.drawCircle(x + spacing, y + spacing, dotRadius, dragHandlePaint)
    }

    private fun drawKeyContent(canvas: Canvas, key: KeyInfo, rect: RectF, density: Float) {
        when (key.type) {
            KeyType.DEL -> {
                val minDim = minOf(rect.height(), rect.width())
                KeyboardVectorIcons.drawDelIcon(canvas, key.centerX, key.centerY, density, minDim, iconPath, iconPaint)
            }
            KeyType.SHIFT -> {
                val minDim = minOf(rect.height(), rect.width())
                KeyboardVectorIcons.drawShiftIcon(canvas, key.centerX, key.centerY, density, 0, minDim, iconPath, iconPaint, iconFillPaint)
            }
            KeyType.ENTER -> {
                val minDim = minOf(rect.height(), rect.width())
                KeyboardVectorIcons.drawEnterIcon(canvas, key.centerX, key.centerY, density, EditorInfo.IME_ACTION_DONE, minDim, iconPath, iconPaint, iconFillPaint)
            }
            KeyType.LANG_SWITCH -> {
                val langY = key.centerY - (2f * density)
                val startupLang = try {
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                        .getString("startup_language", "ID") ?: "ID"
                } catch (_: Exception) {
                    "ID"
                }
                canvas.drawText(startupLang, key.centerX, langY, primaryTextPaint)

                // SubLabel 😊
                val subX = rect.right - (7f * density)
                val subY = rect.top + (12f * density)
                canvas.drawText("😊", subX, subY, keyCornerSubTextPaint)

                // Active glow bar
                val barWidth = (28f * density).coerceAtMost(rect.width() * 0.60f)
                val barHeight = 3f * density
                val barRadius = 1.5f * density
                val barTop = key.centerY + (12f * density)
                glowRect.set(
                    key.centerX - (barWidth / 2f),
                    barTop,
                    key.centerX + (barWidth / 2f),
                    barTop + barHeight
                )
                canvas.drawRoundRect(glowRect, barRadius, barRadius, indicatorGlowPaint)
            }
            KeyType.SPACE_0 -> {
                val labelY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                canvas.drawText("␣", key.centerX, labelY, primaryTextPaint)

                val numX = rect.right - (7f * density)
                val numY = rect.top + (12f * density)
                canvas.drawText("0", numX, numY, keyCornerSubTextPaint)
            }
            KeyType.PAGE_SWITCH -> {
                val labelY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                canvas.drawText(key.primaryLabel.ifEmpty { "?123" }, key.centerX, labelY, primaryTextPaint)
            }
            else -> {
                val labelY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                canvas.drawText(key.primaryLabel, key.centerX, labelY, primaryTextPaint)
            }
        }
    }
}
