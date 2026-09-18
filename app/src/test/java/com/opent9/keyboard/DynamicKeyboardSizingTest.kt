package com.opent9.keyboard

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamicKeyboardSizingTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testStandardPortraitGeometryCalculation() {
        val atlas = KeyAtlas()
        // Standard 1080x2400 display with density = 3.0 (xxhdpi)
        // 260dp overall height budget = 260 * 3 = 780px
        atlas.computeGeometry(1080f, 780f, 3.0f)

        assertEquals(1080f, atlas.totalWidth, 0.01f)
        assertEquals(780f, atlas.totalHeight, 0.01f)
        // 40dp suggestion strip = 120px
        assertEquals(120f, atlas.stripHeight, 0.01f)
        // 4 rows of 55dp keys = 165px
        assertEquals(165f, atlas.rowHeight, 0.01f)
        // 4 columns across width = 270px (25% screen width)
        assertEquals(270f, atlas.colWidth, 0.01f)

        // Verify key 0 bounds (Row 0, Col 0: [ 1 .,?!' ])
        val key1 = atlas.keys[0]
        assertEquals(0f, key1.bounds.left, 0.01f)
        assertEquals(120f, key1.bounds.top, 0.01f)
        assertEquals(270f, key1.bounds.right, 0.01f)
        assertEquals(285f, key1.bounds.bottom, 0.01f)

        // Verify key 14 bounds on Page 0 (Row 3, Col 2 & 3: [ ␣ 0 SPACE (2 blocks) ])
        val keySpace = atlas.keys[14]
        assertEquals(540f, keySpace.bounds.left, 0.01f)
        assertEquals(615f, keySpace.bounds.top, 0.01f)
        assertEquals(1080f, keySpace.bounds.right, 0.01f)
        assertEquals(780f, keySpace.bounds.bottom, 0.01f)

        // Verify key 15 bounds on Page 2 (Row 3, Col 3: [ " ' ])
        atlas.updatePageLayout(KeyboardPage.PAGE_2_EXT_SYM)
        val keyQuote = atlas.keys[15]
        assertEquals(810f, keyQuote.bounds.left, 0.01f)
        assertEquals(615f, keyQuote.bounds.top, 0.01f)
        assertEquals(1080f, keyQuote.bounds.right, 0.01f)
        assertEquals(780f, keyQuote.bounds.bottom, 0.01f)
    }

    @Test
    fun testDynamicScaleOnDifferentResolutionsAndDensities() {
        val atlas = KeyAtlas()

        // 720p (xhdpi, density = 2.0)
        // 260dp overall height = 520px
        atlas.computeGeometry(720f, 520f, 2.0f)
        assertEquals(80f, atlas.stripHeight, 0.01f) // 40dp * 2
        assertEquals(110f, atlas.rowHeight, 0.01f) // 55dp * 2
        assertEquals(180f, atlas.colWidth, 0.01f) // 720 / 4

        // 1440p (xxxhdpi, density = 4.0)
        // 260dp overall height = 1040px
        atlas.computeGeometry(1440f, 1040f, 4.0f)
        assertEquals(160f, atlas.stripHeight, 0.01f) // 40dp * 4
        assertEquals(220f, atlas.rowHeight, 0.01f) // 55dp * 4
        assertEquals(360f, atlas.colWidth, 0.01f) // 1440 / 4
    }

    @Test
    fun testDynamicHeightSettingsSliderBounds() {
        val atlas = KeyAtlas()
        val density = 3.0f

        // Slider Minimum: 220dp height (660px at density 3.0)
        val minHeight = 220f * density
        atlas.computeGeometry(1080f, minHeight, density)
        assertTrue(atlas.stripHeight in (30f * density)..(48f * density))
        assertEquals((minHeight - atlas.stripHeight) / 4f, atlas.rowHeight, 0.01f)

        // Slider Maximum: 320dp height (960px at density 3.0)
        val maxHeight = 320f * density
        atlas.computeGeometry(1080f, maxHeight, density)
        assertTrue(atlas.stripHeight in (30f * density)..(48f * density))
        assertEquals((maxHeight - atlas.stripHeight) / 4f, atlas.rowHeight, 0.01f)
    }

    @Test
    fun testOneHandedModeGeometryOffsets() {
        val atlas = KeyAtlas()
        val density = 3.0f
        val fullWidth = 1080f
        val height = 780f

        // Left-Handed Mode (85% content width, 0 offset)
        val leftContentWidth = fullWidth * 0.85f
        atlas.computeGeometry(fullWidth, height, density, offsetX = 0f, contentWidth = leftContentWidth)

        assertEquals(leftContentWidth / 4f, atlas.colWidth, 0.01f)
        assertEquals(0f, atlas.keys[0].bounds.left, 0.01f)
        assertEquals(leftContentWidth, atlas.keys[3].bounds.right, 0.01f)

        // Right-Handed Mode (85% content width, shifted right)
        val rightOffset = fullWidth * 0.15f
        val rightContentWidth = fullWidth * 0.85f
        atlas.computeGeometry(fullWidth, height, density, offsetX = rightOffset, contentWidth = rightContentWidth)

        assertEquals(rightContentWidth / 4f, atlas.colWidth, 0.01f)
        assertEquals(rightOffset, atlas.keys[0].bounds.left, 0.01f)
        assertEquals(fullWidth, atlas.keys[3].bounds.right, 0.01f)
    }

    @Test
    fun testTouchGestureSlopDynamicScaling() {
        val atlas = KeyAtlas()
        val dummyListener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {}
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
        }

        // Density 1.5
        atlas.computeGeometry(540f, 390f, 1.5f)
        val tracker15 = TouchGestureTracker(atlas, dummyListener)
        assertEquals(15f, tracker15.touchSlopPx, 0.01f) // 10dp * 1.5
        assertEquals(18f, tracker15.flickDistanceMinPx, 0.01f) // 12dp * 1.5

        // Density 3.0
        atlas.computeGeometry(1080f, 780f, 3.0f)
        val tracker30 = TouchGestureTracker(atlas, dummyListener)
        assertEquals(30f, tracker30.touchSlopPx, 0.01f) // 10dp * 3.0
        assertEquals(36f, tracker30.flickDistanceMinPx, 0.01f) // 12dp * 3.0
        assertEquals(32f, tracker30.scrubStepPx, 0.01f) // ~32px
    }

    @Test
    fun testEmojiAtlasDynamicHeightDistribution() {
        val emojiAtlas = EmojiAtlas()
        val density = 3.0f

        // Standard 260dp * 3 = 780px
        emojiAtlas.computeLayout(1080f, 780f, density)
        assertEquals(120f, emojiAtlas.categoryTabBounds[0].height(), 0.01f) // 40dp * 3
        assertEquals(135f, emojiAtlas.emojiGridBounds[0].height(), 0.01f) // 45dp * 3
        assertEquals(120f, emojiAtlas.controlRowBounds[0].height(), 0.01f) // 40dp * 3
        val totalEmojiHeight = emojiAtlas.categoryTabBounds[0].height() +
                (4 * emojiAtlas.emojiGridBounds[0].height()) +
                emojiAtlas.controlRowBounds[0].height()
        assertEquals(780f, totalEmojiHeight, 0.01f)

        // Custom Height: 600px at density 2.5
        emojiAtlas.computeLayout(800f, 600f, 2.5f)
        val customTotal = emojiAtlas.categoryTabBounds[0].height() +
                (4 * emojiAtlas.emojiGridBounds[0].height()) +
                emojiAtlas.controlRowBounds[0].height()
        assertEquals(600f, customTotal, 0.01f)
    }

    @Test
    fun testT9KeyboardViewMeasurementAndDynamicPaints() {
        val keyboardView = T9KeyboardView(context)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        keyboardView.measure(widthSpec, heightSpec)
        assertTrue(keyboardView.measuredHeight > 0)

        keyboardView.layout(0, 0, 1080, keyboardView.measuredHeight)
        assertTrue(keyboardView.keyAtlas.rowHeight > 0f)
        assertTrue(keyboardView.keyAtlas.colWidth > 0f)
    }

    @Test
    fun testMotorolaG45PortraitProfile() {
        // Motorola G45 (5G): 720 x 1600 pixels (HD+), density ~ 1.75f (280 dpi)
        val density = 1.75f
        val screenWidth = 720f
        val targetHeight = 260f * density // 455px

        val atlas = KeyAtlas()
        atlas.computeGeometry(screenWidth, targetHeight, density)

        // Key columns: 720 / 4 = 180px (~102.8dp)
        assertEquals(180f, atlas.colWidth, 0.01f)
        // Suggestion strip: 40dp * 1.75 = 70px
        assertEquals(70f, atlas.stripHeight, 0.01f)
        // 4 rows of 55dp keys: 55 * 1.75 = 96.25px
        assertEquals(96.25f, atlas.rowHeight, 0.01f)

        // Verify Touch Slop and Flick thresholds scale to phone screen
        val dummyListener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {}
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
        }
        val tracker = TouchGestureTracker(atlas, dummyListener)
        assertEquals(17.5f, tracker.touchSlopPx, 0.01f) // 10dp * 1.75 (was 30px in old static version!)
        assertEquals(21.0f, tracker.flickDistanceMinPx, 0.01f) // 12dp * 1.75 (was 36px in old static version!)

        // Verify dynamic typography on Motorola G45
        val oldDensity = context.resources.displayMetrics.density
        try {
            context.resources.displayMetrics.density = density
            context.resources.displayMetrics.densityDpi = 280
            context.resources.displayMetrics.widthPixels = 720
            context.resources.displayMetrics.heightPixels = 1600

            val keyboardView = T9KeyboardView(context)
            keyboardView.layout(0, 0, screenWidth.toInt(), targetHeight.toInt())
            val primaryPaint = T9KeyboardView::class.java.getDeclaredField("primaryTextPaint").apply { isAccessible = true }.get(keyboardView) as android.graphics.Paint
            val subPaint = T9KeyboardView::class.java.getDeclaredField("subTextPaint").apply { isAccessible = true }.get(keyboardView) as android.graphics.Paint

            // In the enlarged typography version, primary text scales with density to 16.5dp (28.875px)
            // and sub text scales to 10dp (17.5px) on G45:
            assertEquals(28.875f, primaryPaint.textSize, 0.5f)
            assertEquals(17.5f, subPaint.textSize, 0.5f)
            assertTrue("Sub text should be smaller than primary text", subPaint.textSize < primaryPaint.textSize)
        } finally {
            context.resources.displayMetrics.density = oldDensity
        }
    }

    @Test
    fun testMotorolaG45LandscapeProfile() {
        // Motorola G45 in landscape: width = 1600px, height = 720px, density = 1.75f
        val density = 1.75f
        val screenHeight = 720f // ~411dp
        val screenWidth = 1600f

        val atlas = KeyAtlas()
        // In landscape, height is constrained to ~48% of screen height (~345px / ~197dp)
        val landscapeHeight = screenHeight * 0.48f
        atlas.computeGeometry(screenWidth, landscapeHeight, density)

        // Verify strip and keys fit within the 48% height constraint without overflowing
        assertEquals(landscapeHeight, atlas.totalHeight, 0.01f)
        assertTrue(atlas.stripHeight in (30f * density)..(48f * density))
        val expectedRowHeight = (landscapeHeight - atlas.stripHeight) / 4f
        assertEquals(expectedRowHeight, atlas.rowHeight, 0.01f)
        assertTrue("Row height should be comfortably touchable in landscape", atlas.rowHeight >= 45f)
    }
}
