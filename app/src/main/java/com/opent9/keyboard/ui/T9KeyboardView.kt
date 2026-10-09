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
    val feedbackManager = KeyboardFeedbackManager(context) { settingsObserver }

    // Pre-allocated Paints (Zero allocations in onDraw)
    private val backgroundPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#121214"), antiAlias = false)
    private val keyBackgroundPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#1E1E22"))
    private val keyPressedPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#34343C"))
    private val keyBorderPaint = KeyboardPaintFactory.createStrokePaint(1.5f, Color.parseColor("#28282E"))
    private val primaryTextPaint = KeyboardPaintFactory.createTextPaint(42f, Paint.Align.CENTER, color = Color.parseColor("#E0E0E6"))
    private val subTextPaint = KeyboardPaintFactory.createTextPaint(24f, Paint.Align.CENTER, color = Color.parseColor("#8E8E98"))
    private val keyCornerSubTextPaint = KeyboardPaintFactory.createTextPaint(20f, Paint.Align.RIGHT, color = Color.parseColor("#8E8E98"))
    private val dualSymTextPaint = KeyboardPaintFactory.createTextPaint(34f, Paint.Align.CENTER, color = Color.parseColor("#E0E0E6"))
    private val stripBackgroundPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#18181C"), antiAlias = false)
    private val pillPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#2A2A32"))
    private val pillTextPaint = KeyboardPaintFactory.createTextPaint(28f, Paint.Align.CENTER, isBold = true, color = Color.parseColor("#00E5FF"))
    private val candidateTextPaint = KeyboardPaintFactory.createTextPaint(34f, Paint.Align.LEFT, color = Color.parseColor("#FFFFFF"))
    private val candidatePrefixPaint = KeyboardPaintFactory.createTextPaint(34f, Paint.Align.LEFT, isBold = true, color = Color.parseColor("#00E5FF"))
    private val dividerPaint = KeyboardPaintFactory.createStrokePaint(2f, Color.parseColor("#2E2E38"))
    private val indicatorGlowPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#00E5FF"))
    private val iconPaint = KeyboardPaintFactory.createStrokePaint(4f, Color.parseColor("#E0E0E6"), roundCap = true)
    private val iconFillPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#E0E0E6"))
    private val page1KeyActionPaint = KeyboardPaintFactory.createFillPaint(Color.parseColor("#25252C"))
    private val page1DigitTextPaint = KeyboardPaintFactory.createTextPaint(42f, Paint.Align.CENTER, color = Color.parseColor("#E0E0E6"))
    private val page1OpTextPaint = KeyboardPaintFactory.createTextPaint(32f, Paint.Align.CENTER, color = Color.parseColor("#E0E0E6"))
    private val page1SymTextPaint = KeyboardPaintFactory.createTextPaint(34f, Paint.Align.CENTER, color = Color.parseColor("#E0E0E6"))
    private val page1SmallTextPaint = KeyboardPaintFactory.createTextPaint(28f, Paint.Align.CENTER, isBold = true, color = Color.parseColor("#E0E0E6"))

    // Dedicated page renderers (Zero allocations in onDraw)
    private val suggestionStripRenderer = SuggestionStripRenderer(
        stripBackgroundPaint = stripBackgroundPaint,
        pillPaint = pillPaint,
        pillTextPaint = pillTextPaint,
        candidateTextPaint = candidateTextPaint,
        candidatePrefixPaint = candidatePrefixPaint,
        dividerPaint = dividerPaint,
        keyBackgroundPaint = keyBackgroundPaint,
        keyBorderPaint = keyBorderPaint,
        subTextPaint = subTextPaint
    )
    private val emojiPageRenderer = EmojiPageRenderer(
        pillPaint = pillPaint,
        keyPressedPaint = keyPressedPaint,
        keyBackgroundPaint = keyBackgroundPaint,
        subTextPaint = subTextPaint,
        primaryTextPaint = primaryTextPaint
    )
    private val page1NumSymRenderer = Page1NumSymRenderer(
        backgroundPaint = backgroundPaint,
        page1KeyActionPaint = page1KeyActionPaint,
        keyBorderPaint = keyBorderPaint,
        keyPressedPaint = keyPressedPaint,
        keyBackgroundPaint = keyBackgroundPaint,
        page1OpTextPaint = page1OpTextPaint,
        page1DigitTextPaint = page1DigitTextPaint,
        page1SmallTextPaint = page1SmallTextPaint,
        page1SymTextPaint = page1SymTextPaint,
        iconPaint = iconPaint,
        iconFillPaint = iconFillPaint
    )
    private val page0KeypadRenderer = Page0KeypadRenderer(
        keyBackgroundPaint = keyBackgroundPaint,
        keyPressedPaint = keyPressedPaint,
        keyBorderPaint = keyBorderPaint,
        primaryTextPaint = primaryTextPaint,
        keyCornerSubTextPaint = keyCornerSubTextPaint,
        dualSymTextPaint = dualSymTextPaint,
        indicatorGlowPaint = indicatorGlowPaint,
        iconPaint = iconPaint,
        iconFillPaint = iconFillPaint
    )

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
            value?.let { applyScrubSettings(it) }
            updateThemeFromConfiguration(resources.configuration)
        }

    init {
        isHapticFeedbackEnabled = true
        updateThemeFromConfiguration(resources.configuration)
    }

    fun applyScrubSettings(observer: SettingsObserver) {
        gestureTracker.spaceScrubRequireHold = observer.isSpaceScrubbingHoldRequired()
        gestureTracker.scrubActivationDistanceDp = when (observer.getSpaceScrubbingSensitivity()) {
            "low" -> 30f
            "high" -> 14f
            else -> 22f
        }
    }

    fun applyTheme(colors: KeyboardColors) {
        currentColors = colors
        isDarkMode = colors.isDark

        KeyboardThemeApplier.applyThemeColors(
            colors = colors,
            backgroundPaint = backgroundPaint,
            keyBackgroundPaint = keyBackgroundPaint,
            keyPressedPaint = keyPressedPaint,
            keyBorderPaint = keyBorderPaint,
            primaryTextPaint = primaryTextPaint,
            subTextPaint = subTextPaint,
            keyCornerSubTextPaint = keyCornerSubTextPaint,
            dualSymTextPaint = dualSymTextPaint,
            stripBackgroundPaint = stripBackgroundPaint,
            pillPaint = pillPaint,
            pillTextPaint = pillTextPaint,
            candidateTextPaint = candidateTextPaint,
            candidatePrefixPaint = candidatePrefixPaint,
            dividerPaint = dividerPaint,
            indicatorGlowPaint = indicatorGlowPaint,
            iconPaint = iconPaint,
            iconFillPaint = iconFillPaint,
            page1KeyActionPaint = page1KeyActionPaint,
            page1DigitTextPaint = page1DigitTextPaint,
            page1OpTextPaint = page1OpTextPaint,
            page1SymTextPaint = page1SymTextPaint,
            page1SmallTextPaint = page1SmallTextPaint
        )

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
        val prefHeightDp = settingsObserver?.getKeyboardHeightDp() ?: 260
        val targetHeight = KeyboardDimensionCalculator.calculateTargetHeight(
            displayMetrics = resources.displayMetrics,
            config = resources.configuration,
            prefHeightDp = prefHeightDp
        )
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

        val oneHandedMode = settingsObserver?.getOneHandedMode() ?: 0
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val (offsetX, contentWidth) = KeyboardDimensionCalculator.calculateErgonomicOffsetAndWidth(
            width = w,
            density = density,
            isLandscape = isLandscape,
            oneHandedMode = oneHandedMode
        )
        settingsObserver?.let { observer ->
            keyAtlas.setLayoutConfiguration(
                colPosition = observer.get4thColumnPosition(),
                rowPosition = observer.get4thRowPosition(),
                rowOrder = observer.get4thRowOrder(),
                colOrder = observer.get4thColumnOrder()
            )
            applyScrubSettings(observer)
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
        val candSp = settingsObserver?.getCandidateFontSizeSp()?.toFloat() ?: 16f
        KeyboardDimensionCalculator.applyDynamicTypography(
            displayMetrics = resources.displayMetrics,
            config = resources.configuration,
            rowHeight = keyAtlas.rowHeight,
            stripHeight = keyAtlas.stripHeight,
            p1ContainerHeight = keyAtlas.page1ScrollContainerBounds.height(),
            candFontSizeSp = candSp,
            primaryTextPaint = primaryTextPaint,
            subTextPaint = subTextPaint,
            keyCornerSubTextPaint = keyCornerSubTextPaint,
            dualSymTextPaint = dualSymTextPaint,
            pillTextPaint = pillTextPaint,
            candidateTextPaint = candidateTextPaint,
            candidatePrefixPaint = candidatePrefixPaint,
            keyBorderPaint = keyBorderPaint,
            dividerPaint = dividerPaint,
            iconPaint = iconPaint,
            page1DigitTextPaint = page1DigitTextPaint,
            page1OpTextPaint = page1OpTextPaint,
            page1SymTextPaint = page1SymTextPaint,
            page1SmallTextPaint = page1SmallTextPaint
        )
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

    var isWordCorrectionActive: Boolean = false

    private fun formatCandidateWord(raw: String): String {
        return CandidateMeasurementHelper.formatCandidateWord(raw, isWordCorrectionActive, shiftState)
    }

    val isUppercase: Boolean
        get() = when (shiftState) {
            2 -> true
            1 -> candidates.isEmpty()
            else -> false
        }

    fun getDisplayLabel(key: KeyInfo): String {
        return CandidateMeasurementHelper.getDisplayLabel(key, shiftState, candidates.isEmpty())
    }

    private fun recomputeCandidateLayout() {
        gestureTracker.maxStripScroll = CandidateMeasurementHelper.recomputeCandidateLayout(
            candidates = candidates,
            candidateTextPaint = candidateTextPaint,
            candidateViewportLeft = keyAtlas.candidateViewportBounds.left,
            candidateViewportWidth = keyAtlas.candidateViewportBounds.width(),
            density = resources.displayMetrics.density,
            candidateItemLeft = candidateItemLeft,
            candidateItemWidth = candidateItemWidth,
            candidateItemRight = candidateItemRight,
            formatWord = { formatCandidateWord(it) }
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (keyAtlas.currentPage == KeyboardPage.PAGE_3_EMOJI) {
            emojiPageRenderer.drawEmojiPage(
                canvas = canvas,
                emojiAtlas = emojiAtlas,
                emojiScrollOffset = gestureTracker.emojiScrollOffset,
                activeTabIndex = activeEmojiTabIndex,
                activeGridIndex = activeEmojiGridIndex,
                activeControlIndex = activeEmojiControlIndex,
                density = resources.displayMetrics.density
            )
            return
        }

        if (keyAtlas.currentPage == KeyboardPage.PAGE_1_NUM_SYM) {
            page1NumSymRenderer.drawPage1(
                canvas = canvas,
                width = width.toFloat(),
                height = height.toFloat(),
                keyAtlas = keyAtlas,
                columnScrollOffset = gestureTracker.page1ColumnScrollOffset,
                activeOperatorIndex = gestureTracker.activeOperatorIndex,
                activeKeyId = activeKeyId,
                imeAction = imeAction,
                density = resources.displayMetrics.density
            )
            return
        }

        if (keyAtlas.currentPage == KeyboardPage.PAGE_2_EXT_SYM) {
            suggestionStripRenderer.drawPage2Strip(
                canvas = canvas,
                keyAtlas = keyAtlas,
                density = resources.displayMetrics.density
            )
        } else {
            // 1. Draw Suggestion Strip (40dp)
            suggestionStripRenderer.drawSuggestionStrip(
                canvas = canvas,
                keyAtlas = keyAtlas,
                scrollOffset = gestureTracker.stripScrollOffset,
                isT9Mode = isT9Mode,
                candidates = candidates,
                candidateItemLeft = candidateItemLeft,
                candidateItemRight = candidateItemRight,
                density = resources.displayMetrics.density,
                formatWord = { formatCandidateWord(it) }
            )
        }

        // 2. Draw Keypad (4 rows x 4 columns)
        page0KeypadRenderer.drawKeypad(
            canvas = canvas,
            keyAtlas = keyAtlas,
            activeKeyId = activeKeyId,
            isT9Mode = isT9Mode,
            activeLanguage = activeLanguage,
            imeAction = imeAction,
            shiftState = shiftState,
            density = resources.displayMetrics.density,
            getDisplayLabel = { getDisplayLabel(it) }
        )
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
        feedbackManager.playClickFeedback()
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
        EmojiInteractionCoordinator.onCategoryTap(categoryIndex, emojiAtlas, { gestureTracker.resetEmojiScroll() }, { invalidate() })
    }

    override fun onEmojiTap(emoji: String) {
        playClickFeedback()
        onEmojiSelectedAction?.invoke(emoji)
        invalidate()
    }

    override fun onEmojiControlTap(controlIndex: Int) {
        playClickFeedback()
        EmojiInteractionCoordinator.onControlTap(controlIndex, keyAtlas, emojiAtlas, { gestureTracker.resetEmojiScroll() }, { invalidate() }, onEmojiControlAction)
    }

    override fun onEmojiPageSwipe(direction: FlickDirection) {
        playClickFeedback()
        EmojiInteractionCoordinator.onPageSwipe(direction, emojiAtlas, { gestureTracker.resetEmojiScroll() }, { invalidate() })
    }

    override fun onEmojiTouchStateChanged(tabIndex: Int?, gridIndex: Int?, controlIndex: Int?) {
        this.activeEmojiTabIndex = tabIndex
        this.activeEmojiGridIndex = gridIndex
        this.activeEmojiControlIndex = controlIndex
        invalidate()
    }

    fun cancelAllGestures() {
        gestureTracker.cancelAllGestures()
        activeKeyId = null
        activeEmojiTabIndex = null
        activeEmojiGridIndex = null
        activeEmojiControlIndex = null
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAllGestures()
    }
}
