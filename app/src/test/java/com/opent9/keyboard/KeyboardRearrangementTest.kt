package com.opent9.keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.settings.KeyboardLayoutConfig
import com.opent9.keyboard.settings.RearrangeKeyId
import com.opent9.keyboard.settings.RearrangeKeyboardView
import com.opent9.keyboard.ui.KeyAtlas
import com.opent9.keyboard.ui.KeyboardPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardRearrangementTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDefaultLayoutPositions() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3.0f)

        val colWidth = atlas.colWidth
        val rowHeight = atlas.rowHeight
        val stripHeight = atlas.stripHeight

        // Row 0: 0(1), 1(2), 2(3), 3(DEL)
        assertEquals(0, atlas.grid[0][0].id)
        assertEquals(1, atlas.grid[0][1].id)
        assertEquals(2, atlas.grid[0][2].id)
        assertEquals(3, atlas.grid[0][3].id)

        // Row 1: 4(4), 5(5), 6(6), 7(SHIFT)
        assertEquals(4, atlas.grid[1][0].id)
        assertEquals(5, atlas.grid[1][1].id)
        assertEquals(6, atlas.grid[1][2].id)
        assertEquals(7, atlas.grid[1][3].id)

        // Row 2: 8(7), 9(8), 10(9), 11(ENTER)
        assertEquals(8, atlas.grid[2][0].id)
        assertEquals(9, atlas.grid[2][1].id)
        assertEquals(10, atlas.grid[2][2].id)
        assertEquals(11, atlas.grid[2][3].id)

        // Row 3: 12(?123), 14(SPACE left), 14(SPACE right), 13(EN/ID/😊 at corner)
        assertEquals(12, atlas.grid[3][0].id)
        assertEquals(14, atlas.grid[3][1].id)
        assertEquals(14, atlas.grid[3][2].id)
        assertEquals(13, atlas.grid[3][3].id)

        // Verify Column 4 from top to bottom:
        // Row 0: DEL (3)
        // Row 1: SHIFT (7)
        // Row 2: ENTER (11)
        // Row 3: EN / ID / 😊 (13)
        assertEquals(3, atlas.grid[0][3].id)
        assertEquals(7, atlas.grid[1][3].id)
        assertEquals(11, atlas.grid[2][3].id)
        assertEquals(13, atlas.grid[3][3].id)

        // Verify bounds
        assertEquals(0f, atlas.keys[0].bounds.left, 0.01f)
        assertEquals(stripHeight, atlas.keys[0].bounds.top, 0.01f)
        assertEquals(3 * colWidth, atlas.keys[3].bounds.left, 0.01f)
        assertEquals(0 * colWidth, atlas.keys[12].bounds.left, 0.01f)
        assertEquals(1 * colWidth, atlas.keys[14].bounds.left, 0.01f)
        assertEquals(3 * colWidth, atlas.keys[14].bounds.right, 0.01f)
        assertEquals(3 * colWidth, atlas.keys[13].bounds.left, 0.01f)
        assertEquals(4 * colWidth, atlas.keys[13].bounds.right, 0.01f)
        assertTrue(atlas.keys[15].bounds.isEmpty)

        // Verify findKeyAt
        val keyDel = atlas.findKeyAt(colWidth * 3.5f, stripHeight + rowHeight * 0.5f)
        assertNotNull(keyDel)
        assertEquals(3, keyDel?.id)

        val keyShift = atlas.findKeyAt(colWidth * 3.5f, stripHeight + rowHeight * 1.5f)
        assertNotNull(keyShift)
        assertEquals(7, keyShift?.id)

        val keyEnter = atlas.findKeyAt(colWidth * 3.5f, stripHeight + rowHeight * 2.5f)
        assertNotNull(keyEnter)
        assertEquals(11, keyEnter?.id)

        val keyLang = atlas.findKeyAt(colWidth * 3.5f, stripHeight + rowHeight * 3.5f)
        assertNotNull(keyLang)
        assertEquals(13, keyLang?.id)

        val keyPage = atlas.findKeyAt(colWidth * 0.5f, stripHeight + rowHeight * 3.5f)
        assertNotNull(keyPage)
        assertEquals(12, keyPage?.id)

        val keySpaceCol1 = atlas.findKeyAt(colWidth * 1.5f, stripHeight + rowHeight * 3.5f)
        assertNotNull(keySpaceCol1)
        assertEquals(14, keySpaceCol1?.id)

        val keySpaceCol2 = atlas.findKeyAt(colWidth * 2.5f, stripHeight + rowHeight * 3.5f)
        assertNotNull(keySpaceCol2)
        assertEquals(14, keySpaceCol2?.id)
    }

    @Test
    fun testSerializationAndDeserialization() {
        val serialized = KeyboardLayoutConfig.serialize(KeyboardLayoutConfig.DEFAULT_ORDER)
        assertEquals("DEL,SHIFT,ENTER,LANG,SPACE,PAGE", serialized)

        val deserialized = KeyboardLayoutConfig.deserialize(serialized)
        assertEquals(KeyboardLayoutConfig.DEFAULT_ORDER, deserialized)

        // Corrupted or incomplete string falls back to default safely
        val fallback1 = KeyboardLayoutConfig.deserialize("DEL,SHIFT,ENTER")
        assertEquals(KeyboardLayoutConfig.DEFAULT_ORDER, fallback1)

        val fallback2 = KeyboardLayoutConfig.deserialize("")
        assertEquals(KeyboardLayoutConfig.DEFAULT_ORDER, fallback2)

        val fallbackNull = KeyboardLayoutConfig.deserialize(null)
        assertEquals(KeyboardLayoutConfig.DEFAULT_ORDER, fallbackNull)
    }

    @Test
    fun testCustomRearrangedLayout() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3.0f)

        // Swap DEL (originally Slot 0) and PAGE (originally Slot 6)
        val customOrder = listOf(
            RearrangeKeyId.PAGE,
            RearrangeKeyId.SHIFT,
            RearrangeKeyId.ENTER,
            RearrangeKeyId.LANG,
            RearrangeKeyId.SPACE,
            RearrangeKeyId.DEL
        )
        atlas.setLayoutOrder(customOrder)

        val colWidth = atlas.colWidth
        val rowHeight = atlas.rowHeight
        val stripHeight = atlas.stripHeight

        // PAGE (12) is now at Slot 0 (Row 0, Col 3)
        assertEquals(12, atlas.grid[0][3].id)
        val keyPage = atlas.findKeyAt(colWidth * 3.5f, stripHeight + rowHeight * 0.5f)
        assertNotNull(keyPage)
        assertEquals(12, keyPage?.id)

        // DEL (3) is now at Slot 6 (Row 3, Col 0)
        assertEquals(3, atlas.grid[3][0].id)
        val keyDel = atlas.findKeyAt(colWidth * 0.5f, stripHeight + rowHeight * 3.5f)
        assertNotNull(keyDel)
        assertEquals(3, keyDel?.id)
    }

    @Test
    fun testSpaceInColumn4() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3.0f)

        // Place SPACE at slots 0 & 1 (Row 0 & 1 of Column 3)
        val customOrder = listOf(
            RearrangeKeyId.SPACE,
            RearrangeKeyId.DEL,
            RearrangeKeyId.SHIFT,
            RearrangeKeyId.ENTER,
            RearrangeKeyId.LANG,
            RearrangeKeyId.PAGE
        )
        atlas.setLayoutOrder(customOrder)

        val colWidth = atlas.colWidth
        val rowHeight = atlas.rowHeight
        val stripHeight = atlas.stripHeight

        // SPACE (14) spans Row 0 and Row 1 in Column 3
        assertEquals(14, atlas.grid[0][3].id)
        assertEquals(14, atlas.grid[1][3].id)

        // Verify vertical bounds of SPACE
        assertEquals(3 * colWidth, atlas.keys[14].bounds.left, 0.01f)
        assertEquals(4 * colWidth, atlas.keys[14].bounds.right, 0.01f)
        assertEquals(stripHeight, atlas.keys[14].bounds.top, 0.01f)
        assertEquals(stripHeight + 2 * rowHeight, atlas.keys[14].bounds.bottom, 0.01f)

        val touchRow0Col3 = atlas.findKeyAt(colWidth * 3.5f, stripHeight + rowHeight * 0.5f)
        assertNotNull(touchRow0Col3)
        assertEquals(14, touchRow0Col3?.id)

        val touchRow1Col3 = atlas.findKeyAt(colWidth * 3.5f, stripHeight + rowHeight * 1.5f)
        assertNotNull(touchRow1Col3)
        assertEquals(14, touchRow1Col3?.id)
    }

    @Test
    fun testMoveItemSliding() {
        val initial = KeyboardLayoutConfig.DEFAULT_ORDER
        // Move PAGE from index 5 to index 0
        val moved = KeyboardLayoutConfig.moveItem(initial, 5, 0)
        assertEquals(RearrangeKeyId.PAGE, moved[0])
        assertEquals(RearrangeKeyId.DEL, moved[1])
        assertEquals(RearrangeKeyId.SHIFT, moved[2])
        assertEquals(RearrangeKeyId.ENTER, moved[3])
        assertEquals(RearrangeKeyId.LANG, moved[4])
        assertEquals(RearrangeKeyId.SPACE, moved[5])
    }

    @Test
    fun testRearrangeKeyboardViewReset() {
        val view = RearrangeKeyboardView(context)
        view.measure(1080, 780)
        view.layout(0, 0, 1080, 780)

        // Set custom order
        val custom = listOf(
            RearrangeKeyId.PAGE,
            RearrangeKeyId.SPACE,
            RearrangeKeyId.LANG,
            RearrangeKeyId.ENTER,
            RearrangeKeyId.SHIFT,
            RearrangeKeyId.DEL
        )
        view.setLayoutOrder(custom)
        assertEquals(custom, view.getLayoutOrder())

        // Reset to default
        view.resetToDefault()
        assertEquals(KeyboardLayoutConfig.DEFAULT_ORDER, view.getLayoutOrder())
    }

    @Test
    fun testPage1LayoutNonOverlapping() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3.0f)
        atlas.updatePageLayout(KeyboardPage.PAGE_1_NUM_SYM)

        val abcKey = atlas.page1Keys.first { it.primaryLabel == "ABC" }
        assertNotNull(abcKey)
        assertTrue(abcKey.bounds.top >= atlas.page1ScrollContainerBounds.bottom)
    }
}
