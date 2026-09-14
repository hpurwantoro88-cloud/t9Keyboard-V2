package com.opent9.keyboard

import android.view.MotionEvent
import com.opent9.keyboard.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TouchGestureTrackerTest {

    @Test
    fun testKeyAtlasGeometry() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f) // 260dp * 3 = 780px height
        assertEquals(1080f, atlas.totalWidth, 0.01f)
        assertEquals(780f, atlas.totalHeight, 0.01f)
        assertEquals(120f, atlas.stripHeight, 0.01f) // 40dp * 3

        // Key columns: exactly 25% of width = 270px
        assertEquals(270f, atlas.colWidth, 0.01f)
        // Key rows: (780 - 120) / 4 = 165px (55dp * 3)
        assertEquals(165f, atlas.rowHeight, 0.01f)

        // Test finding keys
        val key0 = atlas.findKeyAt(100f, 150f)
        assertNotNull(key0)
        assertEquals(0, key0?.id)

        val keySpace = atlas.findKeyAt(600f, 700f)
        assertNotNull(keySpace)
        assertEquals(14, keySpace?.id)
        assertEquals(KeyType.SPACE_0, keySpace?.type)
    }

    @Test
    fun test4QuadrantFlickDisambiguation() {
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
        val tracker = TouchGestureTracker(atlas, dummyListener)

        // Right: theta in [-45, 45) -> dx > 0, dy = 0
        assertEquals(FlickDirection.RIGHT, tracker.disambiguateFlick(60f, 0f))
        assertEquals(FlickDirection.RIGHT, tracker.disambiguateFlick(60f, -20f))

        // Up: theta in [45, 135) -> dy < 0, small dx
        assertEquals(FlickDirection.UP, tracker.disambiguateFlick(0f, -60f))
        assertEquals(FlickDirection.UP, tracker.disambiguateFlick(20f, -60f))

        // Left: theta in [135, 225) -> dx < 0, small dy
        assertEquals(FlickDirection.LEFT, tracker.disambiguateFlick(-60f, 0f))
        assertEquals(FlickDirection.LEFT, tracker.disambiguateFlick(-60f, 20f))

        // Down: theta in [-135, -45) -> dy > 0, small dx
        assertEquals(FlickDirection.DOWN, tracker.disambiguateFlick(0f, 60f))
        assertEquals(FlickDirection.DOWN, tracker.disambiguateFlick(20f, 60f))
    }

    @Test
    fun testFlickTriggeredDuringMove() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)

        var flickedKey: KeyInfo? = null
        var flickedDirection: FlickDirection? = null

        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {
                flickedKey = key
                flickedDirection = direction
            }
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
        }
        val tracker = TouchGestureTracker(atlas, listener)

        val downTime = 1000L
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        tracker.onTouchEvent(down) { null }

        // Drag 50px right in MOVE
        val move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 150f, 200f, 0)
        tracker.onTouchEvent(move) { null }

        assertNotNull(flickedKey)
        assertEquals(0, flickedKey?.id)
        assertEquals(FlickDirection.RIGHT, flickedDirection)

        // UP should not re-trigger
        flickedKey = null
        val up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 150f, 200f, 0)
        tracker.onTouchEvent(up) { null }
        assertEquals(null, flickedKey)
    }

    @Test
    fun testFlickTriggeredOnReleaseWithoutTimeoutDrop() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)

        var flickedKey: KeyInfo? = null
        var flickedDirection: FlickDirection? = null

        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {
                flickedKey = key
                flickedDirection = direction
            }
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
        }
        val tracker = TouchGestureTracker(atlas, listener)

        val downTime = 1000L
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        tracker.onTouchEvent(down) { null }

        // Release at 100, 260 (+60px down) after 600ms (simulating mouse drag in emulator on Key 0)
        val up = MotionEvent.obtain(downTime, downTime + 600, MotionEvent.ACTION_UP, 100f, 260f, 0)
        tracker.onTouchEvent(up) { null }

        assertNotNull(flickedKey)
        assertEquals(0, flickedKey?.id)
        assertEquals(FlickDirection.DOWN, flickedDirection)
    }

    @Test
    fun testDigitKeyFlickDisabledAndFallsBackToTap() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)

        var tappedKey: KeyInfo? = null
        var flickedKey: KeyInfo? = null

        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {
                tappedKey = key
            }
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {
                flickedKey = key
            }
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
        }
        val tracker = TouchGestureTracker(atlas, listener)

        // Key 1 (id 1, digit 2 "ABC") at (400f, 200f)
        val downTime = 1000L
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 400f, 200f, 0)
        tracker.onTouchEvent(down) { null }

        // Fast upward release (+60px up) simulating speed typing swipe
        val up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, 400f, 140f, 0)
        tracker.onTouchEvent(up) { null }

        // Flick must NOT be triggered on digit keys on Page 0
        assertEquals(null, flickedKey)
        // Must fall back to clean key tap
        assertNotNull(tappedKey)
        assertEquals(1, tappedKey?.id)
    }

    @Test
    fun testPage2TabStripTapSwitchesLayer() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)
        atlas.updatePageLayout(KeyboardPage.PAGE_2_EXT_SYM)
        assertEquals(0, atlas.activeSymbolLayerIndex)

        var changedLayerIndex = -1
        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {}
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
            override fun onSymbolLayerChanged(layerIndex: Int) {
                changedLayerIndex = layerIndex
            }
        }
        val tracker = TouchGestureTracker(atlas, listener)

        // Tap tab 1: x in [270, 540], y in [0, 120] (stripHeight = 120f)
        val t0 = 1000L
        tracker.onTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 350f, 60f, 0)) { null }
        tracker.onTouchEvent(MotionEvent.obtain(t0, t0 + 50, MotionEvent.ACTION_UP, 350f, 60f, 0)) { null }

        assertEquals(1, atlas.activeSymbolLayerIndex)
        assertEquals(1, changedLayerIndex)
        // Key 0 on layer 1 is "@ #"
        assertEquals("@ #", atlas.keys[0].primaryLabel)

        // Tap tab 2: x in [540, 810]
        val t1 = 2000L
        tracker.onTouchEvent(MotionEvent.obtain(t1, t1, MotionEvent.ACTION_DOWN, 600f, 60f, 0)) { null }
        tracker.onTouchEvent(MotionEvent.obtain(t1, t1 + 50, MotionEvent.ACTION_UP, 600f, 60f, 0)) { null }

        assertEquals(2, atlas.activeSymbolLayerIndex)
        assertEquals(2, changedLayerIndex)
        // Key 0 on layer 2 is "° ℃"
        assertEquals("° ℃", atlas.keys[0].primaryLabel)
    }

    @Test
    fun testPage2VerticalSwipe3x3GridSwitchesLayer() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)
        atlas.updatePageLayout(KeyboardPage.PAGE_2_EXT_SYM)
        assertEquals(0, atlas.activeSymbolLayerIndex)

        var changedLayerIndex = -1
        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {}
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
            override fun onSymbolLayerChanged(layerIndex: Int) {
                changedLayerIndex = layerIndex
            }
        }
        val tracker = TouchGestureTracker(atlas, listener)

        // Inside 3x3 grid: x=200, y=300 -> row 1, col 0
        // Swipe UP: dy = -60px (|dy| = 60 >= 36)
        val t0 = 1000L
        tracker.onTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 200f, 300f, 0)) { null }
        tracker.onTouchEvent(MotionEvent.obtain(t0, t0 + 50, MotionEvent.ACTION_MOVE, 200f, 240f, 0)) { null }

        assertEquals(1, atlas.activeSymbolLayerIndex)
        assertEquals(1, changedLayerIndex)
        assertEquals("@ #", atlas.keys[0].primaryLabel)

        // Finish touch
        tracker.onTouchEvent(MotionEvent.obtain(t0, t0 + 100, MotionEvent.ACTION_UP, 200f, 240f, 0)) { null }

        // Swipe DOWN: dy = +60px
        val t1 = 2000L
        tracker.onTouchEvent(MotionEvent.obtain(t1, t1, MotionEvent.ACTION_DOWN, 200f, 240f, 0)) { null }
        tracker.onTouchEvent(MotionEvent.obtain(t1, t1 + 50, MotionEvent.ACTION_MOVE, 200f, 300f, 0)) { null }

        assertEquals(0, atlas.activeSymbolLayerIndex)
        assertEquals(0, changedLayerIndex)
        assertEquals("~ `", atlas.keys[0].primaryLabel)
    }

    @Test
    fun testPage2HorizontalFlickOn3x3KeyFlicksKey() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)
        atlas.updatePageLayout(KeyboardPage.PAGE_2_EXT_SYM)
        assertEquals(0, atlas.activeSymbolLayerIndex)

        var flickedKey: KeyInfo? = null
        var flickedDirection: FlickDirection? = null

        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {
                flickedKey = key
                flickedDirection = direction
            }
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
        }
        val tracker = TouchGestureTracker(atlas, listener)

        // Horizontal flick right on key 0 (x=100, y=200) -> move to x=160, y=200 (dx = 60, dy = 0)
        val t0 = 1000L
        tracker.onTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 100f, 200f, 0)) { null }
        tracker.onTouchEvent(MotionEvent.obtain(t0, t0 + 50, MotionEvent.ACTION_MOVE, 160f, 200f, 0)) { null }

        // Layer should NOT change
        assertEquals(0, atlas.activeSymbolLayerIndex)
        assertNotNull(flickedKey)
        assertEquals(0, flickedKey?.id)
        assertEquals(FlickDirection.RIGHT, flickedDirection)
    }

    @Test
    fun testCandidateLongPress() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)

        var longPressedCandidateIndex: Int? = null
        var tappedCandidateIndex: Int? = null

        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {}
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {
                tappedCandidateIndex = index
            }
            override fun onCandidateLongPress(index: Int) {
                longPressedCandidateIndex = index
            }
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
        }
        val tracker = TouchGestureTracker(atlas, listener)

        val t0 = 1000L
        val down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 300f, 50f, 0)
        tracker.onTouchEvent(down) { 1 }

        assertEquals(1, tracker.activeCandidateIndex)

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(1, longPressedCandidateIndex)

        val up = MotionEvent.obtain(t0, t0 + 400, MotionEvent.ACTION_UP, 300f, 50f, 0)
        tracker.onTouchEvent(up) { 1 }

        org.junit.Assert.assertNull(tappedCandidateIndex)
    }

    @Test
    fun testDelKeyRightwardMovementTreatedAsTap() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)

        var tappedKey: KeyInfo? = null
        var flickedKey: KeyInfo? = null

        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {
                tappedKey = key
            }
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {
                flickedKey = key
            }
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
        }
        val tracker = TouchGestureTracker(atlas, listener)

        // Find DEL key (Key 3, top-right of keypad)
        val delKey = atlas.keys.first { it.type == KeyType.DEL }
        val cx = delKey.centerX
        val cy = delKey.centerY

        val t0 = 2000L
        val down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, cx, cy, 0)
        tracker.onTouchEvent(down) { null }

        // Drift 40px to the right and release within 100ms
        val up = MotionEvent.obtain(t0, t0 + 80, MotionEvent.ACTION_UP, cx + 40f, cy, 0)
        tracker.onTouchEvent(up) { null }

        // Must register as tap on DEL, not swallowed by a no-op right flick!
        assertEquals(delKey.id, tappedKey?.id)
        org.junit.Assert.assertNull(flickedKey)
    }

    @Test
    fun testSpaceAndLanguageMarginHitTesting() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)

        // Utility row is y in [615, 780]
        val y = 700f

        // Center of Key 13 (LANG_SWITCH) is at x = 405f
        val langKey = atlas.findKeyAt(405f, y)
        assertNotNull(langKey)
        assertEquals(KeyType.LANG_SWITCH, langKey?.type)

        // Boundary between Key 13 and Key 14 is at x = 540f
        // Touch at x = 530f (within 24px of boundary) should resolve to SPACE_0 to protect space taps
        val spaceMarginKey = atlas.findKeyAt(530f, y)
        assertNotNull(spaceMarginKey)
        assertEquals(KeyType.SPACE_0, spaceMarginKey?.type)

        // Center of Key 14 (SPACE_0) is at x = 675f
        val spaceKey = atlas.findKeyAt(675f, y)
        assertNotNull(spaceKey)
        assertEquals(KeyType.SPACE_0, spaceKey?.type)
    }

    @Test
    fun testDelKeyHoldTriggersContinuousAcceleratedDelete() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)

        val deleteRepeatEvents = ArrayList<Boolean>()
        var longPressCalled = false

        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {}
            override fun onKeyLongPress(key: KeyInfo) {
                longPressCalled = true
            }
            override fun onKeyDeleteRepeat(isWordDelete: Boolean) {
                deleteRepeatEvents.add(isWordDelete)
            }
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
        }
        val tracker = TouchGestureTracker(atlas, listener)

        val delKey = atlas.keys.first { it.type == KeyType.DEL }
        val cx = delKey.centerX
        val cy = delKey.centerY

        val t0 = 1000L
        tracker.onTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, cx, cy, 0)) { null }

        val longPressField = TouchGestureTracker::class.java.getDeclaredField("longPressRunnable").apply {
            isAccessible = true
        }
        val longPressRunnable = longPressField.get(tracker) as Runnable
        longPressRunnable.run()

        // Key DEL should NOT call onKeyLongPress; it must invoke onKeyDeleteRepeat(isWordDelete = false)
        org.junit.Assert.assertFalse("Key DEL should not trigger onKeyLongPress", longPressCalled)
        assertEquals(1, deleteRepeatEvents.size)
        org.junit.Assert.assertFalse("Initial repeat delete must be character delete, not word delete", deleteRepeatEvents[0])

        // Execute repeatDeleteRunnable before 1200ms
        val repeatField = TouchGestureTracker::class.java.getDeclaredField("repeatDeleteRunnable").apply {
            isAccessible = true
        }
        val repeatRunnable = repeatField.get(tracker) as Runnable
        repeatRunnable.run()

        assertEquals(2, deleteRepeatEvents.size)
        org.junit.Assert.assertFalse("Repeat delete before 1200ms must be character delete", deleteRepeatEvents[1])

        // Simulate elapsed > 1200ms
        val touchDownTimeField = TouchGestureTracker::class.java.getDeclaredField("touchDownTime").apply {
            isAccessible = true
        }
        touchDownTimeField.setLong(tracker, System.currentTimeMillis() - 1300L)
        repeatRunnable.run()

        assertEquals(3, deleteRepeatEvents.size)
        org.junit.Assert.assertTrue("Repeat delete at >1200ms must accelerate to word delete", deleteRepeatEvents[2])
    }
}
