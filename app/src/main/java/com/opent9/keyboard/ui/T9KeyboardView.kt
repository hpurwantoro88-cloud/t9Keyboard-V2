package com.opent9.keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.opent9.keyboard.jni.NativeEngineBridge

class T9KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), TouchGestureListener {

    val keyAtlas = KeyAtlas()
    val emojiAtlas = EmojiAtlas()
    val gestureTracker = TouchGestureTracker(keyAtlas, this)

    private val vibrator = context.getSystemService(Vibrator::class.java)

    // Pre-allocated Paints (Zero allocations in onDraw)
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#121214")
        style = Paint.Style.FILL
    }
    private val keyBackgroundPaint = Paint().apply {
        color = Color.parseColor("#1E1E22")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val keyPressedPaint = Paint().apply {
        color = Color.parseColor("#34343C")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val keyBorderPaint = Paint().apply {
        color = Color.parseColor("#28282E")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }
    private val primaryTextPaint = Paint().apply {
        color = Color.parseColor("#E0E0E6")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 42f
    }
    private val subTextPaint = Paint().apply {
        color = Color.parseColor("#8E8E98")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }
    private val stripBackgroundPaint = Paint().apply {
        color = Color.parseColor("#18181C")
        style = Paint.Style.FILL
    }
    private val pillPaint = Paint().apply {
        color = Color.parseColor("#2A2A32")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val pillTextPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 28f
        isFakeBoldText = true
    }
    private val candidateTextPaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        textSize = 34f
    }
    private val candidatePrefixPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        textSize = 34f
        isFakeBoldText = true
    }
    private val dividerPaint = Paint().apply {
        color = Color.parseColor("#2E2E38")
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val indicatorGlowPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val iconPaint = Paint().apply {
        color = Color.parseColor("#E0E0E6")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val iconFillPaint = Paint().apply {
        color = Color.parseColor("#E0E0E6")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Pre-allocated paths & rects
    private val scratchRect = RectF()
    private val glowRect = RectF()
    private val iconPath = Path()

    // Key coordinates pre-allocated arrays
    private val candidateItemLeft = FloatArray(16)
    private val candidateItemRight = FloatArray(16)
    private val candidateItemWidth = FloatArray(16)

    // Keyboard state
    var isT9Mode: Boolean = true
        private set
    var activeLanguage: String = "ID"
        private set
    var shiftState: Int = 0 // 0=Lower, 1=Title, 2=Upper
        private set
    var imeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED
        private set
    var activeKeyId: Int? = null
        private set

    // Candidate list (reusable)
    private val candidates = ArrayList<String>(16)

    // Listener callbacks to IME Service
    var onKeyTapAction: ((KeyInfo, Float, Float) -> Unit)? = null
    var onKeyFlickAction: ((KeyInfo, FlickDirection) -> Unit)? = null
    var onKeyLongPressAction: ((KeyInfo) -> Unit)? = null
    var onSpaceScrubAction: ((Int) -> Unit)? = null
    var onPillTapAction: (() -> Unit)? = null
    var onCandidateTapAction: ((Int) -> Unit)? = null
    var onOpenSettingsAction: (() -> Unit)? = null

    init {
        isHapticFeedbackEnabled = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        // 260dp overall height budget (40dp strip + 220dp keys)
        val targetHeight = (260f * density).toInt()
        setMeasuredDimension(width, targetHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        keyAtlas.computeGeometry(w.toFloat(), h.toFloat(), density)
        emojiAtlas.computeLayout(w.toFloat(), h.toFloat(), density)

        // Pass key centers to native spatial scorer
        for (i in 0 until 16) {
            val key = keyAtlas.keys[i]
            if (key.digitValue >= 0) {
                NativeEngineBridge.updateKeyGeometry(key.digitValue, key.centerX, key.centerY)
            }
        }
        recomputeCandidateLayout()
    }

    fun setT9Mode(enabled: Boolean) {
        isT9Mode = enabled
        invalidate()
    }

    fun setLanguage(lang: String) {
        activeLanguage = lang
        keyAtlas.keys[13].primaryLabel = lang
        invalidate()
    }

    fun setShiftState(state: Int) {
        shiftState = state
        val shiftKey = keyAtlas.keys[7]
        shiftKey.primaryLabel = when (state) {
            1 -> "⬆"
            2 -> "⇪"
            else -> "⇧"
        }
        invalidate()
    }

    fun setImeAction(action: Int) {
        imeAction = action
        invalidate()
    }

    fun updateCandidates(list: List<String>) {
        candidates.clear()
        candidates.addAll(list)
        gestureTracker.resetScroll()
        recomputeCandidateLayout()
        invalidate()
    }

    private fun recomputeCandidateLayout() {
        val density = resources.displayMetrics.density
        val minWidth = 64f * density
        val padding = 24f * density
        var curX = keyAtlas.candidateViewportBounds.left

        for (i in 0 until candidates.size.coerceAtMost(16)) {
            val word = candidates[i]
            val textW = primaryTextPaint.measureText(word)
            val itemW = (textW + padding).coerceAtLeast(minWidth)
            candidateItemLeft[i] = curX
            candidateItemWidth[i] = itemW
            candidateItemRight[i] = curX + itemW
            curX += itemW
        }

        val totalCandWidth = curX - keyAtlas.candidateViewportBounds.left
        val viewportWidth = keyAtlas.candidateViewportBounds.width()
        gestureTracker.maxStripScroll = if (totalCandWidth > viewportWidth) {
            -(totalCandWidth - viewportWidth)
        } else {
            0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (keyAtlas.currentPage == KeyboardPage.PAGE_3_EMOJI) {
            drawEmojiPage(canvas)
            return
        }

        // 1. Draw Suggestion Strip (40dp)
        drawSuggestionStrip(canvas)

        // 2. Draw Keypad (4 rows x 4 columns)
        drawKeypad(canvas)
    }

    private fun drawSuggestionStrip(canvas: Canvas) {
        canvas.drawRect(keyAtlas.stripBounds, stripBackgroundPaint)

        // Fixed Left Pill (12% width)
        val pillMargin = 4f * resources.displayMetrics.density
        scratchRect.set(
            keyAtlas.pillBounds.left + pillMargin,
            keyAtlas.pillBounds.top + pillMargin,
            keyAtlas.pillBounds.right - pillMargin,
            keyAtlas.pillBounds.bottom - pillMargin
        )
        canvas.drawRoundRect(scratchRect, 12f, 12f, pillPaint)
        val pillLabel = if (isT9Mode) "T9" else "ABC"
        val pillTextY = keyAtlas.pillBounds.centerY() - ((pillTextPaint.descent() + pillTextPaint.ascent()) / 2f)
        canvas.drawText(pillLabel, keyAtlas.pillBounds.centerX(), pillTextY, pillTextPaint)

        // Divider between pill and viewport
        canvas.drawLine(
            keyAtlas.candidateViewportBounds.left, keyAtlas.stripBounds.top + 6f,
            keyAtlas.candidateViewportBounds.left, keyAtlas.stripBounds.bottom - 6f,
            dividerPaint
        )

        // Candidate Viewport (88% width) with clip & scroll
        canvas.save()
        canvas.clipRect(keyAtlas.candidateViewportBounds)
        canvas.translate(gestureTracker.stripScrollOffset, 0f)

        val candCount = candidates.size.coerceAtMost(16)
        val textY = keyAtlas.stripBounds.centerY() - ((candidateTextPaint.descent() + candidateTextPaint.ascent()) / 2f)

        for (i in 0 until candCount) {
            val left = candidateItemLeft[i]
            val right = candidateItemRight[i]
            val word = candidates[i]

            // Highlight 1st candidate
            val paint = if (i == 0) candidatePrefixPaint else candidateTextPaint
            val textX = left + 12f * resources.displayMetrics.density
            canvas.drawText(word, textX, textY, paint)

            // Vertical divider between items
            canvas.drawLine(right, keyAtlas.stripBounds.top + 8f, right, keyAtlas.stripBounds.bottom - 8f, dividerPaint)
        }
        canvas.restore()
    }

    private fun drawKeypad(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val keyMargin = 3f * density
        val cornerRadius = 10f * density

        for (i in 0 until 16) {
            val key = keyAtlas.keys[i]
            scratchRect.set(
                key.bounds.left + keyMargin,
                key.bounds.top + keyMargin,
                key.bounds.right - keyMargin,
                key.bounds.bottom - keyMargin
            )

            // Key background (pressed or normal)
            val bgPaint = if (key.id == activeKeyId) keyPressedPaint else keyBackgroundPaint
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, keyBorderPaint)

            // Specific key rendering
            when (key.type) {
                KeyType.DIGIT_T9, KeyType.PUNCT_1, KeyType.SPACE_0 -> {
                    // Digit on top, letters beneath
                    val primaryY = key.centerY - (6f * density)
                    canvas.drawText(key.primaryLabel, key.centerX, primaryY, primaryTextPaint)
                    if (key.subLabel.isNotEmpty()) {
                        val subY = key.centerY + (18f * density)
                        canvas.drawText(key.subLabel, key.centerX, subY, subTextPaint)
                    }
                }
                KeyType.LANG_SWITCH -> {
                    // Language code text (EN or ID)
                    val langY = key.centerY - (2f * density)
                    canvas.drawText(activeLanguage, key.centerX, langY, primaryTextPaint)

                    // Active T9 Glow Bar directly beneath the language text (y+14dp, height 3dp, width 28dp, radius 1.5dp)
                    if (isT9Mode) {
                        val barWidth = 28f * density
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
                }
                KeyType.ENTER -> {
                    drawEnterIcon(canvas, key.centerX, key.centerY, density)
                }
                KeyType.SHIFT -> {
                    drawShiftIcon(canvas, key.centerX, key.centerY, density)
                }
                KeyType.DEL -> {
                    drawDelIcon(canvas, key.centerX, key.centerY, density)
                }
                KeyType.DUAL_SYM -> {
                    // Left and right glyphs
                    val leftX = key.centerX - (18f * density)
                    val rightX = key.centerX + (18f * density)
                    val symY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                    canvas.drawText(key.leftGlyph, leftX, symY, primaryTextPaint)
                    canvas.drawText(key.rightGlyph, rightX, symY, subTextPaint)
                }
                else -> {
                    val labelY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                    canvas.drawText(key.primaryLabel, key.centerX, labelY, primaryTextPaint)
                }
            }
        }
    }

    private fun drawEnterIcon(canvas: Canvas, cx: Float, cy: Float, density: Float) {
        val size = 16f * density
        iconPath.reset()
        when (imeAction) {
            EditorInfo.IME_ACTION_SEARCH -> {
                // Magnifying glass
                val r = size * 0.35f
                canvas.drawCircle(cx - (size * 0.1f), cy - (size * 0.1f), r, iconPaint)
                canvas.drawLine(cx + (size * 0.15f), cy + (size * 0.15f), cx + (size * 0.45f), cy + (size * 0.45f), iconPaint)
            }
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEND -> {
                // Right arrow / Paper plane
                iconPath.moveTo(cx - (size * 0.35f), cy - (size * 0.35f))
                iconPath.lineTo(cx + (size * 0.4f), cy)
                iconPath.lineTo(cx - (size * 0.35f), cy + (size * 0.35f))
                iconPath.lineTo(cx - (size * 0.15f), cy)
                iconPath.close()
                canvas.drawPath(iconPath, iconFillPaint)
            }
            EditorInfo.IME_ACTION_NEXT -> {
                // Tab right arrow
                iconPath.moveTo(cx - (size * 0.3f), cy - (size * 0.35f))
                iconPath.lineTo(cx + (size * 0.2f), cy)
                iconPath.lineTo(cx - (size * 0.3f), cy + (size * 0.35f))
                canvas.drawPath(iconPath, iconPaint)
                canvas.drawLine(cx + (size * 0.3f), cy - (size * 0.35f), cx + (size * 0.3f), cy + (size * 0.35f), iconPaint)
            }
            EditorInfo.IME_ACTION_DONE -> {
                // Checkmark
                iconPath.moveTo(cx - (size * 0.35f), cy)
                iconPath.lineTo(cx - (size * 0.05f), cy + (size * 0.3f))
                iconPath.lineTo(cx + (size * 0.4f), cy - (size * 0.3f))
                canvas.drawPath(iconPath, iconPaint)
            }
            else -> {
                // Return carriage arrow
                iconPath.moveTo(cx + (size * 0.3f), cy - (size * 0.3f))
                iconPath.lineTo(cx + (size * 0.3f), cy + (size * 0.1f))
                iconPath.lineTo(cx - (size * 0.25f), cy + (size * 0.1f))
                canvas.drawPath(iconPath, iconPaint)
                // arrow head
                iconPath.reset()
                iconPath.moveTo(cx - (size * 0.1f), cy - (size * 0.1f))
                iconPath.lineTo(cx - (size * 0.35f), cy + (size * 0.1f))
                iconPath.lineTo(cx - (size * 0.1f), cy + (size * 0.3f))
                canvas.drawPath(iconPath, iconPaint)
            }
        }
    }

    private fun drawShiftIcon(canvas: Canvas, cx: Float, cy: Float, density: Float) {
        val s = 14f * density
        iconPath.reset()
        iconPath.moveTo(cx, cy - (s * 0.5f))
        iconPath.lineTo(cx + (s * 0.45f), cy)
        iconPath.lineTo(cx + (s * 0.2f), cy)
        iconPath.lineTo(cx + (s * 0.2f), cy + (s * 0.45f))
        iconPath.lineTo(cx - (s * 0.2f), cy + (s * 0.45f))
        iconPath.lineTo(cx - (s * 0.2f), cy)
        iconPath.lineTo(cx - (s * 0.45f), cy)
        iconPath.close()

        when (shiftState) {
            0 -> canvas.drawPath(iconPath, iconPaint) // Hollow outline (LOWERCASE)
            1 -> canvas.drawPath(iconPath, iconFillPaint) // Solid filled (TITLECASE)
            2 -> { // Solid filled with base bar (UPPERCASE / CAPS LOCK)
                canvas.drawPath(iconPath, iconFillPaint)
                canvas.drawLine(cx - (s * 0.45f), cy + (s * 0.65f), cx + (s * 0.45f), cy + (s * 0.65f), iconPaint)
            }
        }
    }

    private fun drawDelIcon(canvas: Canvas, cx: Float, cy: Float, density: Float) {
        val s = 14f * density
        iconPath.reset()
        iconPath.moveTo(cx - (s * 0.5f), cy)
        iconPath.lineTo(cx - (s * 0.15f), cy - (s * 0.35f))
        iconPath.lineTo(cx + (s * 0.5f), cy - (s * 0.35f))
        iconPath.lineTo(cx + (s * 0.5f), cy + (s * 0.35f))
        iconPath.lineTo(cx - (s * 0.15f), cy + (s * 0.35f))
        iconPath.close()
        canvas.drawPath(iconPath, iconPaint)

        // Draw inner 'x'
        val xs = s * 0.15f
        val xcx = cx + (s * 0.15f)
        canvas.drawLine(xcx - xs, cy - xs, xcx + xs, cy + xs, iconPaint)
        canvas.drawLine(xcx - xs, cy + xs, xcx + xs, cy - xs, iconPaint)
    }

    private fun drawEmojiPage(canvas: Canvas) {
        val density = resources.displayMetrics.density
        // Draw category tabs (40dp)
        for (i in 0 until 9) {
            val bounds = emojiAtlas.categoryTabBounds[i]
            if (i == emojiAtlas.activeCategoryIndex) {
                canvas.drawRect(bounds, pillPaint)
            }
            val textY = bounds.centerY() - ((subTextPaint.descent() + subTextPaint.ascent()) / 2f)
            canvas.drawText(emojiAtlas.categories[i], bounds.centerX(), textY, subTextPaint)
        }

        // Draw active category emoji grid (4 rows x 7 cols)
        val activeEmojis = emojiAtlas.categoryEmojis[emojiAtlas.activeCategoryIndex]
        for (i in 0 until 28.coerceAtMost(activeEmojis.size)) {
            val bounds = emojiAtlas.emojiGridBounds[i]
            val emojiStr = emojiAtlas.getEmojiString(activeEmojis[i])
            val textY = bounds.centerY() - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
            canvas.drawText(emojiStr, bounds.centerX(), textY, primaryTextPaint)
        }

        // Draw control row (ABC, Recents, Space, Del)
        val ctrlRow = emojiAtlas.controlRowBounds
        val ctrlLabels = arrayOf("ABC", "🕒 Recents", "␣ Space", "⌫ DEL")
        for (i in 0 until 4) {
            val bounds = ctrlRow[i]
            canvas.drawRoundRect(bounds, 8f * density, 8f * density, keyBackgroundPaint)
            val textY = bounds.centerY() - ((subTextPaint.descent() + subTextPaint.ascent()) / 2f)
            canvas.drawText(ctrlLabels[i], bounds.centerX(), textY, subTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureTracker.onTouchEvent(event) { touchX ->
            val adjustedX = touchX - gestureTracker.stripScrollOffset
            val count = candidates.size.coerceAtMost(16)
            var hit: Int? = null
            for (i in 0 until count) {
                if (adjustedX in candidateItemLeft[i]..candidateItemRight[i]) {
                    hit = i
                    break
                }
            }
            hit
        }
    }

    fun playClickFeedback() {
        NativeEngineBridge.playClick(0, 0.6f)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25L)
            }
        } catch (_: Exception) {}
    }

    // TouchGestureListener callbacks
    override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {
        playClickFeedback()
        onKeyTapAction?.invoke(key, touchX, touchY)
    }

    override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {
        playClickFeedback()
        onKeyFlickAction?.invoke(key, direction)
    }

    override fun onKeyLongPress(key: KeyInfo) {
        playClickFeedback()
        if (key.type == KeyType.LANG_SWITCH) {
            onOpenSettingsAction?.invoke()
        } else {
            onKeyLongPressAction?.invoke(key)
        }
    }

    override fun onSpaceScrub(steps: Int) {
        playClickFeedback()
        onSpaceScrubAction?.invoke(steps)
    }

    override fun onPillTap() {
        playClickFeedback()
        onPillTapAction?.invoke()
    }

    override fun onCandidateTap(index: Int) {
        playClickFeedback()
        onCandidateTapAction?.invoke(index)
    }

    override fun onStripScroll(newScrollOffset: Float) {
        invalidate()
    }

    override fun onTouchStateChanged(activeKeyId: Int?) {
        this.activeKeyId = activeKeyId
        invalidate()
    }
}
