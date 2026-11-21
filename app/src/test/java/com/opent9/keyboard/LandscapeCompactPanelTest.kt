package com.opent9.keyboard

import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.settings.SettingsObserver
import com.opent9.keyboard.ui.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LandscapeCompactPanelTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun testLandscapeLayoutSpecVariants() {
        val density = 3.0f
        val width = 2400 // 800dp

        val left = KeyboardDimensionCalculator.calculateLandscapeLayoutSpec(width, density, true, 0, "left")
        assertEquals(0f, left.offsetX, 0.01f)
        assertEquals(1320f, left.contentWidth, 0.01f)
        assertEquals(LandscapePanelDock.LEFT, left.panelDock)

        val right = KeyboardDimensionCalculator.calculateLandscapeLayoutSpec(width, density, true, 0, "right")
        assertEquals(1080f, right.offsetX, 0.01f)
        assertEquals(1320f, right.contentWidth, 0.01f)
        assertEquals(LandscapePanelDock.RIGHT, right.panelDock)

        val center = KeyboardDimensionCalculator.calculateLandscapeLayoutSpec(width, density, true, 0, "center")
        assertEquals(540f, center.offsetX, 0.01f)
        assertEquals(1320f, center.contentWidth, 0.01f)
        assertEquals(LandscapePanelDock.CENTER, center.panelDock)

        val full = KeyboardDimensionCalculator.calculateLandscapeLayoutSpec(width, density, true, 0, "full")
        assertEquals(0f, full.offsetX, 0.01f)
        assertEquals(2400f, full.contentWidth, 0.01f)
        assertEquals(LandscapePanelDock.NONE, full.panelDock)

        // Narrow split-screen landscape (< 520dp) falls back to full width
        val narrow = KeyboardDimensionCalculator.calculateLandscapeLayoutSpec(1400, density, true, 0, "left")
        assertEquals(LandscapePanelDock.NONE, narrow.panelDock)
        assertEquals(1400f, narrow.contentWidth, 0.01f)

        // One-handed mode overrides landscape panel
        val oneHanded = KeyboardDimensionCalculator.calculateLandscapeLayoutSpec(width, density, true, 1, "left")
        assertEquals(LandscapePanelDock.NONE, oneHanded.panelDock)
        assertEquals(width * 0.85f, oneHanded.contentWidth, 0.01f)
    }

    @Test
    fun testKeyAtlasAndSymbolPagesGeometryInLandscapePanelMode() {
        val atlas = KeyAtlas()
        val density = 3.0f
        val width = 2400f
        val height = 540f
        val spec = KeyboardDimensionCalculator.calculateLandscapeLayoutSpec(width.toInt(), density, true, 0, "right")

        atlas.computeGeometry(width, height, density, spec.offsetX, spec.contentWidth, spec.panelDock)

        assertTrue(atlas.isLandscapePanelActive)
        assertEquals(15, atlas.landscapePanelLayout.activeKeyCount)
        assertEquals(0f, atlas.landscapePanelLayout.leftPanelBounds.left, 0.01f)
        assertEquals(spec.offsetX, atlas.landscapePanelLayout.leftPanelBounds.right, 0.01f)

        // Page 0 T9 stays in right compact region; pill width is clamped <= 56dp
        assertEquals(spec.offsetX, atlas.stripBounds.left, 0.01f)
        assertEquals(width, atlas.stripBounds.right, 0.01f)
        assertTrue(atlas.pillBounds.width() <= 56f * density + 0.01f)
        assertEquals(spec.offsetX, atlas.keys[0].bounds.left, 0.01f)

        // Page 2 symbol keyboard uses full landscape width (0..width)
        val symKey0 = atlas.page2Keys.first()
        val symKey9 = atlas.page2Keys[9]
        assertTrue(symKey0.bounds.left < 30f)
        assertTrue(symKey9.bounds.right > width - 30f)

        // Page 4 Emoji grid uses full landscape width
        assertEquals(0f, atlas.emojiAtlas.categoryTabBounds[0].left, 0.01f)
        assertEquals(width, atlas.emojiAtlas.categoryTabBounds.last().right, 0.01f)
    }

    @Test
    fun testCenterGuttersLayout() {
        val atlas = KeyAtlas()
        val density = 3.0f
        val width = 2400f
        val height = 540f
        val spec = KeyboardDimensionCalculator.calculateLandscapeLayoutSpec(width.toInt(), density, true, 0, "center")
        atlas.computeGeometry(width, height, density, spec.offsetX, spec.contentWidth, spec.panelDock)

        assertTrue(atlas.isLandscapePanelActive)
        assertEquals(8, atlas.landscapePanelLayout.activeKeyCount)
        assertFalse(atlas.landscapePanelLayout.leftPanelBounds.isEmpty)
        assertFalse(atlas.landscapePanelLayout.rightPanelBounds.isEmpty)
        assertEquals(LandscapePanelAction.UNDO, atlas.findLandscapePanelKeyAt(100f, 50f)?.action)
        assertEquals(LandscapePanelAction.HIDE_IME, atlas.findLandscapePanelKeyAt(2200f, 480f)?.action)
    }

    @Test
    fun testTouchGestureTrackerRoutesPanelAndStripCorrectlyWhenDockedRight() {
        val atlas = KeyAtlas()
        val density = 3.0f
        val width = 2400f
        val height = 540f
        val spec = KeyboardDimensionCalculator.calculateLandscapeLayoutSpec(width.toInt(), density, true, 0, "right")
        atlas.computeGeometry(width, height, density, spec.offsetX, spec.contentWidth, spec.panelDock)

        val tappedPanelActions = mutableListOf<LandscapePanelAction>()
        var pillTapped = 0

        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {}
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() { pillTapped++ }
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
            override fun onLandscapePanelKeyTap(key: LandscapePanelKey) {
                tappedPanelActions.add(key.action)
            }
        }
        val tracker = TouchGestureTracker(atlas, listener)

        // 1. Tap top-left panel key (y < stripHeight) -> hits UNDO on panel, NOT candidate strip or pill!
        val undoKey = atlas.landscapePanelLayout.getKey(0)
        val cx = undoKey.bounds.centerX()
        val cy = undoKey.bounds.centerY()
        assertTrue(cy < atlas.stripHeight)
        tracker.onTouchEvent(MotionEvent.obtain(100, 100, MotionEvent.ACTION_DOWN, cx, cy, 0)) { null }
        tracker.onTouchEvent(MotionEvent.obtain(100, 140, MotionEvent.ACTION_UP, cx, cy, 0)) { null }
        assertEquals(listOf(LandscapePanelAction.UNDO), tappedPanelActions)
        assertEquals(0, pillTapped)

        // 2. Dragging off panel key cancels tap
        tracker.onTouchEvent(MotionEvent.obtain(200, 200, MotionEvent.ACTION_DOWN, cx, cy, 0)) { null }
        tracker.onTouchEvent(MotionEvent.obtain(200, 240, MotionEvent.ACTION_UP, cx + 400f, cy + 300f, 0)) { null }
        assertEquals(1, tappedPanelActions.size)

        // 3. Tapping pill inside right-docked T9 pane triggers onPillTap()
        val pillX = atlas.pillBounds.centerX()
        val pillY = atlas.pillBounds.centerY()
        tracker.onTouchEvent(MotionEvent.obtain(300, 300, MotionEvent.ACTION_DOWN, pillX, pillY, 0)) { null }
        tracker.onTouchEvent(MotionEvent.obtain(300, 340, MotionEvent.ACTION_UP, pillX, pillY, 0)) { null }
        assertEquals(1, pillTapped)
    }

    @Test
    fun testPanelActionHandlerExecutesAllActionsSafely() {
        val ic = mockk<InputConnection>(relaxed = true)
        every { ic.performContextMenuAction(any()) } returns true

        var committed = 0
        var swapped = 0
        var switchedPage: KeyboardPage? = null
        var hidden = 0

        fun dispatch(action: LandscapePanelAction, composing: Boolean = false) {
            LandscapePanelActionHandler.handleAction(
                action = action,
                ic = ic,
                hasActiveComposing = composing,
                commitActiveCandidate = { committed++ },
                commitMultiTapActive = {},
                flushMultiTapWord = {},
                updateAutoCaps = {},
                onSwapSide = { swapped++ },
                onSwitchToPage = { switchedPage = it },
                onHideKeyboard = { hidden++ }
            )
        }

        dispatch(LandscapePanelAction.CURSOR_LEFT, composing = true)
        assertEquals(1, committed)
        verify { ic.sendKeyEvent(match { it.action == KeyEvent.ACTION_DOWN && it.keyCode == KeyEvent.KEYCODE_DPAD_LEFT }) }

        dispatch(LandscapePanelAction.COPY)
        verify { ic.performContextMenuAction(android.R.id.copy) }

        dispatch(LandscapePanelAction.PASTE)
        verify { ic.performContextMenuAction(android.R.id.paste) }

        dispatch(LandscapePanelAction.CUT)
        verify { ic.performContextMenuAction(android.R.id.cut) }

        dispatch(LandscapePanelAction.SELECT_ALL)
        verify { ic.performContextMenuAction(android.R.id.selectAll) }

        dispatch(LandscapePanelAction.TAB)
        verify { ic.commitText("\t", 1) }

        // Undo fallback to Ctrl+Z when performContextMenuAction returns false
        every { ic.performContextMenuAction(android.R.id.undo) } returns false
        dispatch(LandscapePanelAction.UNDO)
        verify { ic.sendKeyEvent(match { it.keyCode == KeyEvent.KEYCODE_Z && it.isCtrlPressed && !it.isShiftPressed }) }

        dispatch(LandscapePanelAction.SWAP_SIDE)
        assertEquals(1, swapped)

        dispatch(LandscapePanelAction.EMOJI)
        assertEquals(KeyboardPage.PAGE_4_EMOJI, switchedPage)

        dispatch(LandscapePanelAction.HIDE_IME)
        assertEquals(1, hidden)
    }

    @Test
    fun testSettingsObserverLandscapeLayoutAndSwap() {
        val observer = SettingsObserver(context)
        var layoutChanges = 0
        observer.onLayoutConfigChanged = { layoutChanges++ }
        observer.start()

        assertEquals("left", observer.getLandscapeLayoutMode())
        observer.swapLandscapeDockSide()
        assertEquals("right", observer.getLandscapeLayoutMode())
        assertTrue(layoutChanges > 0)

        observer.swapLandscapeDockSide()
        assertEquals("left", observer.getLandscapeLayoutMode())
        observer.stop()
    }

    @Test
    fun testT9KeyboardViewActivatesLandscapePanelOnLandscapeMetrics() {
        val dm = context.resources.displayMetrics
        val oldW = dm.widthPixels
        val oldH = dm.heightPixels
        val oldD = dm.density
        val oldOrient = context.resources.configuration.orientation
        try {
            dm.density = 2.0f
            dm.widthPixels = 1600
            dm.heightPixels = 800
            context.resources.configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

            val view = T9KeyboardView(context)
            val wSpec = View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(wSpec, hSpec)
            view.layout(0, 0, 1600, view.measuredHeight)

            assertTrue(view.keyAtlas.isLandscapePanelActive)
            assertEquals(LandscapePanelDock.LEFT, view.keyAtlas.landscapePanelLayout.dock)
        } finally {
            dm.widthPixels = oldW
            dm.heightPixels = oldH
            dm.density = oldD
            context.resources.configuration.orientation = oldOrient
        }
    }
}
