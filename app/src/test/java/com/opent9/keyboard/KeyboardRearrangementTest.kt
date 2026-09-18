package com.opent9.keyboard

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.settings.SettingsObserver
import com.opent9.keyboard.ui.KeyAtlas
import com.opent9.keyboard.ui.KeyboardPage
import com.opent9.keyboard.ui.T9KeyboardView
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

        // Row 3: 12(?123), 13(LANG), 14(SPACE 2 blocks spanning cols 2 & 3)
        assertEquals(12, atlas.grid[3][0].id)
        assertEquals(13, atlas.grid[3][1].id)
        assertEquals(14, atlas.grid[3][2].id)
        assertEquals(14, atlas.grid[3][3].id)

        // Verify bounds
        assertEquals(0f, atlas.keys[0].bounds.left, 0.01f)
        assertEquals(stripHeight, atlas.keys[0].bounds.top, 0.01f)
        assertEquals(3 * colWidth, atlas.keys[3].bounds.left, 0.01f)
        assertEquals(2 * colWidth, atlas.keys[14].bounds.left, 0.01f)
        assertEquals(4 * colWidth, atlas.keys[14].bounds.right, 0.01f)

        // Verify findKeyAt
        val keyDel = atlas.findKeyAt(colWidth * 3.5f, stripHeight + rowHeight * 0.5f)
        assertNotNull(keyDel)
        assertEquals(3, keyDel?.id)

        val keySpaceCol2 = atlas.findKeyAt(colWidth * 2.5f, stripHeight + rowHeight * 3.5f)
        assertNotNull(keySpaceCol2)
        assertEquals(14, keySpaceCol2?.id)

        val keySpaceCol3 = atlas.findKeyAt(colWidth * 3.5f, stripHeight + rowHeight * 3.5f)
        assertNotNull(keySpaceCol3)
        assertEquals(14, keySpaceCol3?.id)
    }

    @Test
    fun testLeftColumnRearrangement() {
        val atlas = KeyAtlas()
        atlas.setLayoutConfiguration(colPosition = "left", rowPosition = "bottom")
        atlas.computeGeometry(1080f, 780f, 3.0f)

        val colWidth = atlas.colWidth
        val rowHeight = atlas.rowHeight
        val stripHeight = atlas.stripHeight

        // Column 0 should now be Action column: DEL(3), SHIFT(7), ENTER(11)
        assertEquals(3, atlas.grid[0][0].id)
        assertEquals(7, atlas.grid[1][0].id)
        assertEquals(11, atlas.grid[2][0].id)

        // Columns 1..3 are dial keys
        assertEquals(0, atlas.grid[0][1].id) // 1
        assertEquals(1, atlas.grid[0][2].id) // 2
        assertEquals(2, atlas.grid[0][3].id) // 3

        assertEquals(4, atlas.grid[1][1].id) // 4
        assertEquals(5, atlas.grid[1][2].id) // 5
        assertEquals(6, atlas.grid[1][3].id) // 6

        assertEquals(8, atlas.grid[2][1].id) // 7
        assertEquals(9, atlas.grid[2][2].id) // 8
        assertEquals(10, atlas.grid[2][3].id) // 9

        // Bottom row utility keys: SPACE(14: cols 0..1), ?123(12), LANG(13)
        assertEquals(14, atlas.grid[3][0].id)
        assertEquals(14, atlas.grid[3][1].id)
        assertEquals(12, atlas.grid[3][2].id)
        assertEquals(13, atlas.grid[3][3].id)

        // DEL key should now be at the left edge
        assertEquals(0f, atlas.keys[3].bounds.left, 0.01f)
        assertEquals(colWidth, atlas.keys[3].bounds.right, 0.01f)

        // Key 1 (id 0) should now start at colWidth
        assertEquals(colWidth, atlas.keys[0].bounds.left, 0.01f)

        // Test findKeyAt on left edge gives DEL
        val touchedDel = atlas.findKeyAt(colWidth * 0.5f, stripHeight + rowHeight * 0.5f)
        assertNotNull(touchedDel)
        assertEquals(3, touchedDel?.id)

        // Test findKeyAt on second column gives Key 1
        val touchedOne = atlas.findKeyAt(colWidth * 1.5f, stripHeight + rowHeight * 0.5f)
        assertNotNull(touchedOne)
        assertEquals(0, touchedOne?.id)
    }

    @Test
    fun testTopRowRearrangement() {
        val atlas = KeyAtlas()
        atlas.setLayoutConfiguration(colPosition = "right", rowPosition = "top")
        atlas.computeGeometry(1080f, 780f, 3.0f)

        val colWidth = atlas.colWidth
        val rowHeight = atlas.rowHeight
        val stripHeight = atlas.stripHeight

        // Row 0 should now be Utility row: ?123(12), LANG(13), SPACE(14: 2 blocks)
        assertEquals(12, atlas.grid[0][0].id)
        assertEquals(13, atlas.grid[0][1].id)
        assertEquals(14, atlas.grid[0][2].id)
        assertEquals(14, atlas.grid[0][3].id)

        // Rows 1..3 are dial keys and action keys
        assertEquals(0, atlas.grid[1][0].id) // 1
        assertEquals(1, atlas.grid[1][1].id) // 2
        assertEquals(2, atlas.grid[1][2].id) // 3
        assertEquals(3, atlas.grid[1][3].id) // DEL

        assertEquals(4, atlas.grid[2][0].id) // 4
        assertEquals(5, atlas.grid[2][1].id) // 5
        assertEquals(6, atlas.grid[2][2].id) // 6
        assertEquals(7, atlas.grid[2][3].id) // SHIFT

        assertEquals(8, atlas.grid[3][0].id) // 7
        assertEquals(9, atlas.grid[3][1].id) // 8
        assertEquals(10, atlas.grid[3][2].id) // 9
        assertEquals(11, atlas.grid[3][3].id) // ENTER

        // ?123 should now be at the top right beneath strip
        assertEquals(stripHeight, atlas.keys[12].bounds.top, 0.01f)
        assertEquals(stripHeight + rowHeight, atlas.keys[12].bounds.bottom, 0.01f)

        // Key 1 should be on row 1
        assertEquals(stripHeight + rowHeight, atlas.keys[0].bounds.top, 0.01f)

        // Test findKeyAt on top row
        val touchedNumSwitch = atlas.findKeyAt(colWidth * 0.5f, stripHeight + rowHeight * 0.5f)
        assertNotNull(touchedNumSwitch)
        assertEquals(12, touchedNumSwitch?.id)

        val touchedKeyOne = atlas.findKeyAt(colWidth * 0.5f, stripHeight + rowHeight * 1.5f)
        assertNotNull(touchedKeyOne)
        assertEquals(0, touchedKeyOne?.id)
    }

    @Test
    fun testLeftColumnAndTopRowRearrangement() {
        val atlas = KeyAtlas()
        atlas.setLayoutConfiguration(colPosition = "left", rowPosition = "top")
        atlas.computeGeometry(1080f, 780f, 3.0f)

        // Utility row on top (Row 0): SPACE(14: cols 0..1), ?123(12), LANG(13)
        assertEquals(14, atlas.grid[0][0].id)
        assertEquals(14, atlas.grid[0][1].id)
        assertEquals(12, atlas.grid[0][2].id)
        assertEquals(13, atlas.grid[0][3].id)

        // Col 0, Rows 1..3: DEL(3), SHIFT(7), ENTER(11)
        assertEquals(3, atlas.grid[1][0].id)
        assertEquals(7, atlas.grid[2][0].id)
        assertEquals(11, atlas.grid[3][0].id)

        // Dial 3x3 at rows 1..3, cols 1..3
        assertEquals(0, atlas.grid[1][1].id)
        assertEquals(1, atlas.grid[1][2].id)
        assertEquals(2, atlas.grid[1][3].id)

        assertEquals(8, atlas.grid[3][1].id)
        assertEquals(9, atlas.grid[3][2].id)
        assertEquals(10, atlas.grid[3][3].id)
    }

    @Test
    fun test4thRowOrderPresets() {
        val atlas = KeyAtlas()

        // 1. Space Center: ?123, SPACE (2 blocks: cols 1..2), LANG
        atlas.setLayoutConfiguration(colPosition = "right", rowPosition = "bottom", rowOrder = "space_center")
        atlas.computeGeometry(1080f, 780f, 3.0f)
        assertEquals(12, atlas.grid[3][0].id) // ?123
        assertEquals(14, atlas.grid[3][1].id) // SPACE
        assertEquals(14, atlas.grid[3][2].id) // SPACE
        assertEquals(13, atlas.grid[3][3].id) // LANG

        // 2. Space Left: SPACE (2 blocks: cols 0..1), LANG, ?123
        atlas.setLayoutConfiguration(colPosition = "right", rowPosition = "bottom", rowOrder = "space_left")
        atlas.computeGeometry(1080f, 780f, 3.0f)
        assertEquals(14, atlas.grid[3][0].id) // SPACE
        assertEquals(14, atlas.grid[3][1].id) // SPACE
        assertEquals(13, atlas.grid[3][2].id) // LANG
        assertEquals(12, atlas.grid[3][3].id) // ?123

        // 3. Flipped: SPACE (2 blocks: cols 0..1), LANG, ?123
        atlas.setLayoutConfiguration(colPosition = "right", rowPosition = "bottom", rowOrder = "reverse")
        atlas.computeGeometry(1080f, 780f, 3.0f)
        assertEquals(14, atlas.grid[3][0].id) // SPACE
        assertEquals(14, atlas.grid[3][1].id) // SPACE
        assertEquals(13, atlas.grid[3][2].id) // LANG
        assertEquals(12, atlas.grid[3][3].id) // ?123
    }

    @Test
    fun test4thColumnOrderPresets() {
        val atlas = KeyAtlas()

        // 1. Inverted / Del Bottom: SHIFT, ENTER, DEL
        atlas.setLayoutConfiguration(colPosition = "right", rowPosition = "bottom", colOrder = "del_bottom")
        atlas.computeGeometry(1080f, 780f, 3.0f)
        assertEquals(7, atlas.grid[0][3].id) // SHIFT
        assertEquals(11, atlas.grid[1][3].id) // ENTER
        assertEquals(3, atlas.grid[2][3].id) // DEL
        assertEquals(14, atlas.grid[3][3].id) // SPACE (2 blocks spanning cols 2..3)

        // 2. Enter Top: ENTER, DEL, SHIFT
        atlas.setLayoutConfiguration(colPosition = "right", rowPosition = "bottom", colOrder = "enter_top")
        atlas.computeGeometry(1080f, 780f, 3.0f)
        assertEquals(11, atlas.grid[0][3].id) // ENTER
        assertEquals(3, atlas.grid[1][3].id) // DEL
        assertEquals(7, atlas.grid[2][3].id) // SHIFT
        assertEquals(14, atlas.grid[3][3].id) // SPACE
    }

    @Test
    fun testT9KeyboardViewIntegrationWithPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putString("col_4th_position", "left")
            .putString("row_4th_position", "top")
            .apply()

        val observer = SettingsObserver(context)
        observer.start()

        val view = T9KeyboardView(context)
        view.settingsObserver = observer

        // Layout with width 1080, height 780
        view.layout(0, 0, 1080, 780)

        // Verify keyAtlas in view reflects left column and top row
        assertEquals(14, view.keyAtlas.grid[0][0].id) // SPACE on top-left (cols 0..1)
        assertEquals(3, view.keyAtlas.grid[1][0].id) // DEL on left
        assertEquals(12, view.keyAtlas.grid[0][2].id) // ?123 on top

        // Now update preferences dynamically while dimensions are unchanged
        prefs.edit()
            .putString("col_4th_position", "right")
            .putString("row_4th_position", "bottom")
            .apply()

        view.reloadLayoutConfiguration()

        // Verify keyAtlas in view updates to right column and bottom row
        assertEquals(0, view.keyAtlas.grid[0][0].id) // 1 on top-left
        assertEquals(3, view.keyAtlas.grid[0][3].id) // DEL on top-right
        assertEquals(14, view.keyAtlas.grid[3][3].id) // SPACE on bottom-right (cols 2..3)

        observer.stop()
    }

    @Test
    fun testPage1TopRowRearrangementNonOverlapping() {
        val atlas = KeyAtlas()
        atlas.setLayoutConfiguration(colPosition = "right", rowPosition = "top")
        atlas.computeGeometry(1080f, 780f, 3.0f)
        atlas.updatePageLayout(KeyboardPage.PAGE_1_NUM_SYM)

        val abcKey = atlas.page1Keys.first { it.primaryLabel == "ABC" }
        // ABC should be at the top row, outside operator scroll container
        assertTrue(abcKey.bounds.bottom <= atlas.page1ScrollContainerBounds.top)
    }
}
