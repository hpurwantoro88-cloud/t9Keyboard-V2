package com.opent9.keyboard.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.settings.SettingsObserver

open class T9KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), TouchGestureListener {

    val keyAtlas = KeyAtlas()
    val emojiAtlas: EmojiAtlas get() = keyAtlas.emojiAtlas
    val gestureTracker = TouchGestureTracker(keyAtlas, this)

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
    private val keyCornerSubTextPaint = Paint().apply {
        color = Color.parseColor("#8E8E98")
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
        textSize = 20f
    }
    private val dualSymTextPaint = Paint().apply {
        color = Color.parseColor("#E0E0E6")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 34f
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

    // Page 1 Dedicated Paints (Matching existing font sizes: 42f digits, 32f operators, 34f symbols, 28f action buttons)
    private val page1KeyActionPaint = Paint().apply {
        color = Color.parseColor("#25252C")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val page1DigitTextPaint = Paint().apply {
        color = Color.parseColor("#E0E0E6")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 42f
    }
    private val page1OpTextPaint = Paint().apply {
        color = Color.parseColor("#E0E0E6")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 32f
    }
    private val page1SymTextPaint = Paint().apply {
        color = Color.parseColor("#E0E0E6")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 34f
    }
    private val page1SmallTextPaint = Paint().apply {
        color = Color.parseColor("#E0E0E6")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 28f
        isFakeBoldText = true
    }

    // Pre-allocated paths & rects
    private val scratchRect = RectF()
    private val glowRect = RectF()
    private val iconPath = Path()
    private val page1ClipPath = Path()

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
    var activeEmojiTabIndex: Int? = null
        private set
    var activeEmojiGridIndex: Int? = null
        private set
    var activeEmojiControlIndex: Int? = null
        private set

    // Candidate list (reusable)
    private val candidates = ArrayList<String>(16)

    // Listener callbacks to IME Service
    var onKeyTapAction: ((KeyInfo, Float, Float) -> Unit)? = null
    var onKeyFlickAction: ((KeyInfo, FlickDirection) -> Unit)? = null
    var onKeyLongPressAction: ((KeyInfo) -> Unit)? = null
    var onKeyDeleteRepeatAction: ((Boolean) -> Unit)? = null
    var onSpaceScrubAction: ((Int) -> Unit)? = null
    var onPillTapAction: (() -> Unit)? = null
    var onCandidateTapAction: ((Int) -> Unit)? = null
    var onCandidateLongPressAction: ((Int, String) -> Unit)? = null
    var onOpenSettingsAction: (() -> Unit)? = null
    var onEmojiSelectedAction: ((String) -> Unit)? = null
    var onEmojiControlAction: ((Int) -> Unit)? = null

    var isDarkMode: Boolean = true
        private set
    var currentColors: KeyboardColors = KeyboardTheme.DARK
        private set

    var settingsObserver: SettingsObserver? = null
        set(value) {
            field = value
            updateThemeFromConfiguration(resources.configuration)
        }

    init {
        isHapticFeedbackEnabled = true
        updateThemeFromConfiguration(resources.configuration)
    }

    fun applyTheme(colors: KeyboardColors) {
        currentColors = colors
        isDarkMode = colors.isDark

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

        invalidate()
    }

    fun updateThemeFromConfiguration(config: Configuration = resources.configuration) {
        val themeMode = settingsObserver?.getAppTheme() ?: "system"
        val resolved = KeyboardTheme.resolveColors(config, themeMode)
        applyTheme(resolved)
    }

    public override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateThemeFromConfiguration(newConfig)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val config = resources.configuration
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

        val screenHeightDp = if (density > 0f) displayMetrics.heightPixels / density else 800f
        val prefHeightDp = settingsObserver?.getKeyboardHeightDp() ?: 260

        val targetHeightDp = if (isLandscape) {
            // Constrain landscape height to 45%-50% of screen height to avoid covering the text field
            val maxLandscapeHeightDp = if (screenHeightDp > 100f) screenHeightDp * 0.48f else 180f
            (prefHeightDp * (maxLandscapeHeightDp / 260f)).coerceIn(140f, maxLandscapeHeightDp.coerceAtLeast(160f))
        } else {
            // Portrait mode: follow PRD / user preference (220dp to 320dp, default: 260dp)
            val maxPortraitHeightDp = if (screenHeightDp > 100f) screenHeightDp * 0.45f else 320f
            prefHeightDp.toFloat().coerceAtMost(maxPortraitHeightDp.coerceAtLeast(220f))
        }

        val targetHeight = (targetHeightDp * density).toInt()
        setMeasuredDimension(width, targetHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        reloadLayoutConfiguration()
    }

    fun reloadLayoutConfiguration() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density

        // One-handed mode or tablet ergonomic centering
        val oneHandedMode = settingsObserver?.getOneHandedMode() ?: 0
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthDp = if (density > 0f) w / density else 360f
        val maxErgonomicWidthPx = 480f * density

        val (offsetX, contentWidth) = when {
            oneHandedMode == 1 -> {
                // Left-Handed: 85% width, left-aligned
                Pair(0f, w * 0.85f)
            }
            oneHandedMode == 2 -> {
                // Right-Handed: 85% width, right-aligned
                Pair(w * 0.15f, w * 0.85f)
            }
            widthDp > 600f && !isLandscape -> {
                // Wide tablet in portrait: centered ergonomic width
                val cWidth = minOf(w.toFloat(), maxErgonomicWidthPx)
                Pair((w - cWidth) / 2f, cWidth)
            }
            else -> {
                Pair(0f, w.toFloat())
            }
        }
        settingsObserver?.let { observer ->
            keyAtlas.setLayoutConfiguration(
                colPosition = observer.get4thColumnPosition(),
                rowPosition = observer.get4thRowPosition(),
                rowOrder = observer.get4thRowOrder(),
                colOrder = observer.get4thColumnOrder()
            )
        }

        keyAtlas.computeGeometry(w.toFloat(), h.toFloat(), density, offsetX, contentWidth)
        settingsObserver?.getLongPressDelay()?.let {
            gestureTracker.longPressTimeoutMs = it
        }
        applyDynamicDimensions()

        // Pass key centers to native spatial scorer
        for (i in 0 until 16) {
            val key = keyAtlas.keys[i]
            if (key.digitValue >= 0) {
                NativeEngineBridge.updateKeyGeometry(key.digitValue, key.centerX, key.centerY)
            }
        }
        recomputeCandidateLayout()
        invalidate()
    }

    fun applyDynamicDimensions() {
        val density = resources.displayMetrics.density
        val fontScale = resources.configuration.fontScale
        val scaledDensity = density * if (fontScale > 0f) fontScale else 1f

        val rowHeight = keyAtlas.rowHeight
        val stripHeight = keyAtlas.stripHeight
        val baseRowHeight = 55f * density
        val rowScale = if (baseRowHeight > 0f) (rowHeight / baseRowHeight).coerceIn(0.60f, 1.40f) else 1.0f

        // Dynamic typography based on row and strip sizes
        primaryTextPaint.textSize = 16.5f * density * rowScale
        subTextPaint.textSize = 10f * density * rowScale
        keyCornerSubTextPaint.textSize = 9.5f * density * rowScale
        dualSymTextPaint.textSize = 13.5f * density * rowScale
        pillTextPaint.textSize = (stripHeight * 0.28f).coerceIn(12f * density, 20f * density)

        val candSp = settingsObserver?.getCandidateFontSizeSp()?.toFloat() ?: 16f
        val candSize = (candSp * scaledDensity).coerceAtMost(stripHeight * 0.52f)
        candidateTextPaint.textSize = candSize
        candidatePrefixPaint.textSize = candSize

        // Stroke widths
        keyBorderPaint.strokeWidth = 0.5f * density
        dividerPaint.strokeWidth = (density * 0.67f).coerceIn(1f, 3f)
        iconPaint.strokeWidth = (density * 1.33f).coerceIn(2.5f, 5f)

        // Page 1 Dedicated Paints
        val p1ContainerHeight = keyAtlas.page1ScrollContainerBounds.height()
        val p1RowScale = if (p1ContainerHeight > 0f && baseRowHeight > 0f) {
            ((p1ContainerHeight / 3f) / baseRowHeight).coerceIn(0.60f, 1.40f)
        } else rowScale
        page1DigitTextPaint.textSize = 16.5f * density * p1RowScale
        page1OpTextPaint.textSize = 12.67f * density * p1RowScale
        page1SymTextPaint.textSize = 13.5f * density * p1RowScale
        page1SmallTextPaint.textSize = 11.33f * density * p1RowScale
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
        recomputeCandidateLayout()
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

    private fun formatCandidateWord(raw: String): String {
        return when (shiftState) {
            1 -> raw.replaceFirstChar { it.uppercase() }
            2 -> raw.uppercase()
            else -> if (raw == "I" || raw.startsWith("I'")) raw else raw.lowercase()
        }
    }

    val isUppercase: Boolean
        get() = when (shiftState) {
            2 -> true
            1 -> candidates.isEmpty()
            else -> false
        }

    fun getDisplayLabel(key: KeyInfo): String {
        return if (key.type == KeyType.DIGIT_T9) {
            if (isUppercase) key.upperPrimaryLabel else key.lowerPrimaryLabel
        } else {
            key.primaryLabel
        }
    }

    private fun recomputeCandidateLayout() {
        val density = resources.displayMetrics.density
        val minWidth = 64f * density
        val padding = 24f * density
        var curX = keyAtlas.candidateViewportBounds.left

        for (i in 0 until candidates.size.coerceAtMost(16)) {
            val word = formatCandidateWord(candidates[i])
            val textW = candidateTextPaint.measureText(word)
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

        if (keyAtlas.currentPage == KeyboardPage.PAGE_1_NUM_SYM) {
            drawPage1(canvas)
            return
        }

        if (keyAtlas.currentPage == KeyboardPage.PAGE_2_EXT_SYM) {
            drawPage2Strip(canvas)
        } else {
            // 1. Draw Suggestion Strip (40dp)
            drawSuggestionStrip(canvas)
        }

        // 2. Draw Keypad (4 rows x 4 columns)
        drawKeypad(canvas)
    }

    private fun drawPage2Strip(canvas: Canvas) {
        val density = resources.displayMetrics.density
        canvas.drawRect(keyAtlas.stripBounds, stripBackgroundPaint)

        val tabMargin = 3f * density
        val cornerRadius = 8f * density

        for (i in 0 until 4) {
            val bounds = keyAtlas.symbolLayerTabBounds[i]
            scratchRect.set(
                bounds.left + tabMargin,
                bounds.top + tabMargin,
                bounds.right - tabMargin,
                bounds.bottom - tabMargin
            )

            val isActive = (i == keyAtlas.activeSymbolLayerIndex)
            val bgPaint = if (isActive) pillPaint else keyBackgroundPaint
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, bgPaint)
            if (isActive) {
                canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, keyBorderPaint)
            }

            val textPaint = if (isActive) pillTextPaint else subTextPaint
            val title = keyAtlas.symbolLayerTabTitles[i]
            val textY = bounds.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(title, bounds.centerX(), textY, textPaint)
        }
    }

    private fun drawSuggestionStrip(canvas: Canvas) {
        canvas.drawRect(keyAtlas.stripBounds, stripBackgroundPaint)

        // Fixed Left Pill (12% width)
        val pillMargin = 4f * resources.displayMetrics.density
        val pillRadius = 4f * resources.displayMetrics.density
        scratchRect.set(
            keyAtlas.pillBounds.left + pillMargin,
            keyAtlas.pillBounds.top + pillMargin,
            keyAtlas.pillBounds.right - pillMargin,
            keyAtlas.pillBounds.bottom - pillMargin
        )
        canvas.drawRoundRect(scratchRect, pillRadius, pillRadius, pillPaint)
        val pillLabel = if (isT9Mode) "T9" else "ABC"
        val pillTextY = keyAtlas.pillBounds.centerY() - ((pillTextPaint.descent() + pillTextPaint.ascent()) / 2f)
        canvas.drawText(pillLabel, keyAtlas.pillBounds.centerX(), pillTextY, pillTextPaint)

        // Divider between pill and viewport
        val density = resources.displayMetrics.density
        canvas.drawLine(
            keyAtlas.candidateViewportBounds.left, keyAtlas.stripBounds.top + (2f * density),
            keyAtlas.candidateViewportBounds.left, keyAtlas.stripBounds.bottom - (2f * density),
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
            val word = formatCandidateWord(candidates[i])

            // Highlight 1st candidate
            val paint = if (i == 0) candidatePrefixPaint else candidateTextPaint
            val textX = left + 12f * resources.displayMetrics.density
            canvas.drawText(word, textX, textY, paint)

            // Vertical divider between items
            canvas.drawLine(right, keyAtlas.stripBounds.top + (2.67f * density), right, keyAtlas.stripBounds.bottom - (2.67f * density), dividerPaint)
        }
        canvas.restore()
    }

    private fun drawKeypad(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val keyMargin = 3f * density
        val cornerRadius = 10f * density
        val baseRowHeight = 55f * density
        val rowScale = if (baseRowHeight > 0f) (keyAtlas.rowHeight / baseRowHeight).coerceIn(0.60f, 1.40f) else 1f

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
                    // Alphabet / main symbol (primary) centered in the key
                    val label = getDisplayLabel(key)
                    val labelY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                    val originalSize = primaryTextPaint.textSize
                    if (label.length > 4) {
                        primaryTextPaint.textSize = originalSize * 0.72f
                    }
                    canvas.drawText(label, key.centerX, labelY, primaryTextPaint)
                    if (label.length > 4) {
                        primaryTextPaint.textSize = originalSize
                    }

                    // Number (secondary) in upper right corner of the key
                    if (key.subLabel.isNotEmpty()) {
                        val numX = scratchRect.right - (7f * density)
                        val numY = scratchRect.top + (11f * density * rowScale)
                        canvas.drawText(key.subLabel, numX, numY, keyCornerSubTextPaint)
                    }
                }
                KeyType.LANG_SWITCH -> {
                    // Language code text (EN or ID)
                    val langY = key.centerY - (2f * density * rowScale)
                    canvas.drawText(activeLanguage, key.centerX, langY, primaryTextPaint)

                    // Active T9 Glow Bar directly beneath the language text
                    if (isT9Mode) {
                        val barWidth = (28f * density).coerceAtMost(key.bounds.width() * 0.60f)
                        val barHeight = 3f * density
                        val barRadius = 1.5f * density
                        val barTop = key.centerY + (13f * density * rowScale)
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
                    // Left and right glyphs rendered with equal size and color
                    val glyphOffset = (key.bounds.width() * 0.20f).coerceIn(10f * density, 24f * density)
                    val leftX = key.centerX - glyphOffset
                    val rightX = key.centerX + glyphOffset
                    val symY = key.centerY - ((dualSymTextPaint.descent() + dualSymTextPaint.ascent()) / 2f)
                    canvas.drawText(key.leftGlyph, leftX, symY, dualSymTextPaint)
                    canvas.drawText(key.rightGlyph, rightX, symY, dualSymTextPaint)
                }
                else -> {
                    val labelY = key.centerY - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
                    canvas.drawText(key.primaryLabel, key.centerX, labelY, primaryTextPaint)
                }
            }
        }
    }

    private fun drawEnterIcon(canvas: Canvas, cx: Float, cy: Float, density: Float) {
        val size = (minOf(keyAtlas.rowHeight, keyAtlas.colWidth) * 0.28f).coerceIn(12f * density, 24f * density)
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
        val s = (minOf(keyAtlas.rowHeight, keyAtlas.colWidth) * 0.25f).coerceIn(10f * density, 20f * density)
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
        val s = (minOf(keyAtlas.rowHeight, keyAtlas.colWidth) * 0.25f).coerceIn(10f * density, 20f * density)
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
            } else if (i == activeEmojiTabIndex) {
                canvas.drawRect(bounds, keyPressedPaint)
            }
            val textY = bounds.centerY() - ((subTextPaint.descent() + subTextPaint.ascent()) / 2f)
            canvas.drawText(emojiAtlas.categories[i], bounds.centerX(), textY, subTextPaint)
        }

        // Draw active category emoji grid (4 rows x 7 cols)
        val activeEmojis = emojiAtlas.categoryEmojis[emojiAtlas.activeCategoryIndex]
        for (i in 0 until 28.coerceAtMost(activeEmojis.size)) {
            val bounds = emojiAtlas.emojiGridBounds[i]
            if (i == activeEmojiGridIndex) {
                scratchRect.set(bounds.left + 2f * density, bounds.top + 2f * density, bounds.right - 2f * density, bounds.bottom - 2f * density)
                canvas.drawRoundRect(scratchRect, 8f * density, 8f * density, keyPressedPaint)
            }
            val emojiStr = emojiAtlas.getEmojiString(activeEmojis[i])
            val textY = bounds.centerY() - ((primaryTextPaint.descent() + primaryTextPaint.ascent()) / 2f)
            canvas.drawText(emojiStr, bounds.centerX(), textY, primaryTextPaint)
        }

        // Draw control row (ABC, Recents, Space, Del)
        val ctrlRow = emojiAtlas.controlRowBounds
        val ctrlLabels = arrayOf("ABC", "🕒 Recents", "␣ Space", "⌫ DEL")
        for (i in 0 until 4) {
            val bounds = ctrlRow[i]
            val bgPaint = if (i == activeEmojiControlIndex) keyPressedPaint else keyBackgroundPaint
            canvas.drawRoundRect(bounds, 8f * density, 8f * density, bgPaint)
            val textY = bounds.centerY() - ((subTextPaint.descent() + subTextPaint.ascent()) / 2f)
            canvas.drawText(ctrlLabels[i], bounds.centerX(), textY, subTextPaint)
        }
    }

    private fun drawPage1(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val cornerRadius = 10f * density

        // 1. Draw keyboard background matching active theme
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // 2. Draw Left Scrollable Operator Column
        val opContainer = keyAtlas.page1ScrollContainerBounds
        canvas.drawRoundRect(opContainer, cornerRadius, cornerRadius, page1KeyActionPaint)
        canvas.drawRoundRect(opContainer, cornerRadius, cornerRadius, keyBorderPaint)

        canvas.save()
        page1ClipPath.reset()
        page1ClipPath.addRoundRect(opContainer, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(page1ClipPath)

        val slotHeight = opContainer.height() / 4f
        val scrollY = gestureTracker.page1ColumnScrollOffset
        val opItems = keyAtlas.page1OperatorItems
        for (i in opItems.indices) {
            val itemTop = opContainer.top + (i * slotHeight) + scrollY
            val itemBottom = itemTop + slotHeight

            if (itemBottom < opContainer.top || itemTop > opContainer.bottom) continue

            // Highlight pressed operator
            if (gestureTracker.activeOperatorIndex == i) {
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
                    drawDelIcon(canvas, key.centerX, key.centerY, density)
                }
                KeyType.ENTER -> {
                    drawEnterIcon(canvas, key.centerX, key.centerY, density)
                }
                KeyType.SPACE_0 -> {
                    drawPage1SpaceIcon(canvas, key.centerX, key.centerY, density)
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

    private fun drawPage1SpaceIcon(canvas: Canvas, cx: Float, cy: Float, density: Float) {
        val hw = (minOf(keyAtlas.rowHeight, keyAtlas.colWidth) * 0.14f).coerceIn(6f * density, 12f * density)
        val hh = hw * 0.5f
        iconPath.reset()
        iconPath.moveTo(cx - hw, cy - hh)
        iconPath.lineTo(cx - hw, cy + hh)
        iconPath.lineTo(cx + hw, cy + hh)
        iconPath.lineTo(cx + hw, cy - hh)
        canvas.drawPath(iconPath, iconPaint)
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

    open fun playClickFeedback() {
        val audioEnabled = settingsObserver?.isAudioEnabled() ?: true
        if (audioEnabled) {
            val volume = settingsObserver?.getAudioVolume() ?: 0.6f
            val style = settingsObserver?.getAudioStyle() ?: 0
            if (volume > 0f) {
                if (NativeEngineBridge.isNativeLoaded) {
                    NativeEngineBridge.playClick(style, volume)
                } else {
                    try {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, volume)
                    } catch (_: Exception) {}
                }
            }
        }

        val hapticEnabled = settingsObserver?.isHapticEnabled() ?: true
        if (hapticEnabled) {
            val intensity = settingsObserver?.getVibrationIntensity() ?: 25L
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
        onKeyLongPressAction?.invoke(key)
    }

    override fun onKeyDeleteRepeat(isWordDelete: Boolean) {
        playClickFeedback()
        onKeyDeleteRepeatAction?.invoke(isWordDelete)
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

    override fun onCandidateLongPress(index: Int) {
        if (index in candidates.indices) {
            playClickFeedback()
            onCandidateLongPressAction?.invoke(index, candidates[index])
        }
    }

    override fun onStripScroll(newScrollOffset: Float) {
        invalidate()
    }

    override fun onTouchStateChanged(activeKeyId: Int?) {
        this.activeKeyId = activeKeyId
        invalidate()
    }

    override fun onSymbolLayerChanged(layerIndex: Int) {
        playClickFeedback()
        invalidate()
    }

    override fun onEmojiCategoryTap(categoryIndex: Int) {
        playClickFeedback()
        emojiAtlas.activeCategoryIndex = categoryIndex.coerceIn(0, 8)
        invalidate()
    }

    override fun onEmojiTap(emoji: String) {
        playClickFeedback()
        onEmojiSelectedAction?.invoke(emoji)
        invalidate()
    }

    override fun onEmojiControlTap(controlIndex: Int) {
        playClickFeedback()
        when (controlIndex) {
            0 -> {
                // ABC shortcut: Return directly to Page 0 (PAGE_0_TEXT)
                keyAtlas.updatePageLayout(KeyboardPage.PAGE_0_TEXT)
                invalidate()
                onEmojiControlAction?.invoke(0)
            }
            1 -> {
                // 🕒 Recents
                emojiAtlas.activeCategoryIndex = 0
                invalidate()
                onEmojiControlAction?.invoke(1)
            }
            2 -> {
                // ␣ Space
                onEmojiControlAction?.invoke(2)
            }
            3 -> {
                // ⌫ DEL
                onEmojiControlAction?.invoke(3)
            }
        }
    }

    override fun onEmojiPageSwipe(direction: FlickDirection) {
        playClickFeedback()
        when (direction) {
            FlickDirection.LEFT -> {
                val next = (emojiAtlas.activeCategoryIndex + 1).coerceAtMost(8)
                if (next != emojiAtlas.activeCategoryIndex) {
                    emojiAtlas.activeCategoryIndex = next
                    invalidate()
                }
            }
            FlickDirection.RIGHT -> {
                val prev = (emojiAtlas.activeCategoryIndex - 1).coerceAtLeast(0)
                if (prev != emojiAtlas.activeCategoryIndex) {
                    emojiAtlas.activeCategoryIndex = prev
                    invalidate()
                }
            }
            else -> {}
        }
    }

    override fun onEmojiTouchStateChanged(tabIndex: Int?, gridIndex: Int?, controlIndex: Int?) {
        this.activeEmojiTabIndex = tabIndex
        this.activeEmojiGridIndex = gridIndex
        this.activeEmojiControlIndex = controlIndex
        invalidate()
    }
}
