package com.opent9.keyboard

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
}
