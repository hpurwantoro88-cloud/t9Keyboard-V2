package com.opent9.keyboard.ui

import android.graphics.RectF
import com.opent9.keyboard.settings.KeyboardLayoutConfig
import com.opent9.keyboard.settings.RearrangeKeyId

enum class KeyboardPage {
    PAGE_0_TEXT,
    PAGE_1_NUM_SYM,
    PAGE_2_EXT_SYM,
    PAGE_3_EXT_SYM_ALT,
    PAGE_4_EMOJI;

    companion object {
        @JvmField
        val PAGE_3_EMOJI = PAGE_4_EMOJI
    }
}


enum class KeyType {
    DIGIT_T9,
    PUNCT_1,
    SPACE_0,
    DEL,
    SHIFT,
    ENTER,
    PAGE_SWITCH,
    LANG_SWITCH,
    DIRECT_SYM,
    DUAL_SYM
}

data class KeyInfo(
    val id: Int,
    var row: Int,
    var col: Int,
    var type: KeyType,
    val bounds: RectF = RectF(),
    var centerX: Float = 0f,
    var centerY: Float = 0f,
    var primaryLabel: String = "",
    var lowerPrimaryLabel: String = "",
    var upperPrimaryLabel: String = "",
    var subLabel: String = "",
    var leftGlyph: String = "",
    var rightGlyph: String = "",
    var digitValue: Int = -1,
    var isActionKey: Boolean = false
)

class KeyAtlas {

    var totalWidth: Float = 0f
        private set
    var totalHeight: Float = 0f
        private set
    var stripHeight: Float = 0f
        private set
    var rowHeight: Float = 0f
        private set
    var colWidth: Float = 0f
        private set

    val stripBounds = RectF()
    val pillBounds = RectF()
    val candidateViewportBounds = RectF()

    val keys = Array(16) { i ->
        KeyInfo(
            id = i,
            row = i / 4,
            col = i % 4,
            type = KeyType.DIGIT_T9
        )
    }

    var lastOffsetX: Float = 0f
        private set

    val grid: Array<Array<KeyInfo>> = Array(4) { r ->
        Array(4) { c -> keys[r * 4 + c] }
    }

    var currentPage: KeyboardPage = KeyboardPage.PAGE_0_TEXT

    var density: Float = 1f
        private set

    val emojiAtlas = EmojiAtlas()

    // Page 1 Dedicated Layout Elements
    val page1ScrollContainerBounds = RectF()
    val page1OperatorItems = arrayOf("+", "-", "*", "/", "(", ")")
    val page1OperatorKeys = Array(6) { i ->
        KeyInfo(
            id = 220 + i,
            row = i,
            col = 0,
            type = KeyType.DIRECT_SYM,
            primaryLabel = page1OperatorItems[i],
            isActionKey = true
        )
    }
    var maxPage1ColumnScroll = 0f
        private set

    val page1Keys = listOf(
        KeyInfo(id = 201, row = 0, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = "1", isActionKey = false),
        KeyInfo(id = 202, row = 0, col = 2, type = KeyType.DIRECT_SYM, primaryLabel = "2", isActionKey = false),
        KeyInfo(id = 203, row = 0, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = "3", isActionKey = false),
        KeyInfo(id = 210, row = 0, col = 4, type = KeyType.DIRECT_SYM, primaryLabel = "%", isActionKey = true),

        KeyInfo(id = 204, row = 1, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = "4", isActionKey = false),
        KeyInfo(id = 205, row = 1, col = 2, type = KeyType.DIRECT_SYM, primaryLabel = "5", isActionKey = false),
        KeyInfo(id = 206, row = 1, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = "6", isActionKey = false),
        KeyInfo(id = 211, row = 1, col = 4, type = KeyType.SPACE_0, primaryLabel = "␣", isActionKey = true),

        KeyInfo(id = 207, row = 2, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = "7", isActionKey = false),
        KeyInfo(id = 208, row = 2, col = 2, type = KeyType.DIRECT_SYM, primaryLabel = "8", isActionKey = false),
        KeyInfo(id = 209, row = 2, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = "9", isActionKey = false),
        KeyInfo(id = 212, row = 2, col = 4, type = KeyType.DEL, primaryLabel = "⌫", isActionKey = true),

        KeyInfo(id = 214, row = 3, col = 0, type = KeyType.PAGE_SWITCH, primaryLabel = "ABC", isActionKey = true),
        KeyInfo(id = 215, row = 3, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = ",", isActionKey = true),
        KeyInfo(id = 216, row = 3, col = 1, type = KeyType.PAGE_SWITCH, primaryLabel = "!?#", isActionKey = false),
        KeyInfo(id = 200, row = 3, col = 2, type = KeyType.DIRECT_SYM, primaryLabel = "0", isActionKey = false),
        KeyInfo(id = 217, row = 3, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = "=", isActionKey = false),
        KeyInfo(id = 218, row = 3, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = ".", isActionKey = true),
        KeyInfo(id = 213, row = 3, col = 4, type = KeyType.ENTER, primaryLabel = "↵", isActionKey = true)
    )

    private val page1KeyMap = page1Keys.associateBy { it.primaryLabel }

    // Page 2 (Primary Symbols - Gboard style: Gambar Kiri)
    val page2Keys = listOf(
        // Row 0 (10 keys)
        KeyInfo(id = 300, row = 0, col = 0, type = KeyType.DIRECT_SYM, primaryLabel = "1"),
        KeyInfo(id = 301, row = 0, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = "2"),
        KeyInfo(id = 302, row = 0, col = 2, type = KeyType.DIRECT_SYM, primaryLabel = "3"),
        KeyInfo(id = 303, row = 0, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = "4"),
        KeyInfo(id = 304, row = 0, col = 4, type = KeyType.DIRECT_SYM, primaryLabel = "5"),
        KeyInfo(id = 305, row = 0, col = 5, type = KeyType.DIRECT_SYM, primaryLabel = "6"),
        KeyInfo(id = 306, row = 0, col = 6, type = KeyType.DIRECT_SYM, primaryLabel = "7"),
        KeyInfo(id = 307, row = 0, col = 7, type = KeyType.DIRECT_SYM, primaryLabel = "8"),
        KeyInfo(id = 308, row = 0, col = 8, type = KeyType.DIRECT_SYM, primaryLabel = "9"),
        KeyInfo(id = 309, row = 0, col = 9, type = KeyType.DIRECT_SYM, primaryLabel = "0"),

        // Row 1 (10 keys)
        KeyInfo(id = 310, row = 1, col = 0, type = KeyType.DIRECT_SYM, primaryLabel = "@"),
        KeyInfo(id = 311, row = 1, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = "#"),
        KeyInfo(id = 312, row = 1, col = 2, type = KeyType.DIRECT_SYM, primaryLabel = "$"),
        KeyInfo(id = 313, row = 1, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = "_"),
        KeyInfo(id = 314, row = 1, col = 4, type = KeyType.DIRECT_SYM, primaryLabel = "&"),
        KeyInfo(id = 315, row = 1, col = 5, type = KeyType.DIRECT_SYM, primaryLabel = "-"),
        KeyInfo(id = 316, row = 1, col = 6, type = KeyType.DIRECT_SYM, primaryLabel = "+"),
        KeyInfo(id = 317, row = 1, col = 7, type = KeyType.DIRECT_SYM, primaryLabel = "("),
        KeyInfo(id = 318, row = 1, col = 8, type = KeyType.DIRECT_SYM, primaryLabel = ")"),
        KeyInfo(id = 319, row = 1, col = 9, type = KeyType.DIRECT_SYM, primaryLabel = "/"),

        // Row 2 (9 keys: 1.5w, 7x 1w, 1.5w)
        KeyInfo(id = 320, row = 2, col = 0, type = KeyType.PAGE_SWITCH, primaryLabel = "=\\<", isActionKey = true),
        KeyInfo(id = 321, row = 2, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = "*"),
        KeyInfo(id = 322, row = 2, col = 2, type = KeyType.DIRECT_SYM, primaryLabel = "\""),
        KeyInfo(id = 323, row = 2, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = "'"),
        KeyInfo(id = 324, row = 2, col = 4, type = KeyType.DIRECT_SYM, primaryLabel = ":"),
        KeyInfo(id = 325, row = 2, col = 5, type = KeyType.DIRECT_SYM, primaryLabel = ";"),
        KeyInfo(id = 326, row = 2, col = 6, type = KeyType.DIRECT_SYM, primaryLabel = "!"),
        KeyInfo(id = 327, row = 2, col = 7, type = KeyType.DIRECT_SYM, primaryLabel = "?"),
        KeyInfo(id = 328, row = 2, col = 8, type = KeyType.DEL, primaryLabel = "⌫", isActionKey = true),

        // Row 3 (6 keys: 1.5w, 1w, 1w, 4w, 1w, 1.5w)
        KeyInfo(id = 329, row = 3, col = 0, type = KeyType.PAGE_SWITCH, primaryLabel = "ABC", isActionKey = true),
        KeyInfo(id = 330, row = 3, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = ",", isActionKey = true),
        KeyInfo(id = 331, row = 3, col = 2, type = KeyType.PAGE_SWITCH, primaryLabel = "12 34", isActionKey = true),
        KeyInfo(id = 332, row = 3, col = 3, type = KeyType.SPACE_0, primaryLabel = "␣", isActionKey = false),
        KeyInfo(id = 333, row = 3, col = 4, type = KeyType.DIRECT_SYM, primaryLabel = ".", isActionKey = true),
        KeyInfo(id = 334, row = 3, col = 5, type = KeyType.ENTER, primaryLabel = "↵", isActionKey = true)
    )

    // Page 3 (Extended Symbols - Gboard style: Gambar Kanan)
    val page3Keys = listOf(
        // Row 0 (10 keys)
        KeyInfo(id = 400, row = 0, col = 0, type = KeyType.DIRECT_SYM, primaryLabel = "~"),
        KeyInfo(id = 401, row = 0, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = "`"),
        KeyInfo(id = 402, row = 0, col = 2, type = KeyType.DIRECT_SYM, primaryLabel = "|"),
        KeyInfo(id = 403, row = 0, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = "•"),
        KeyInfo(id = 404, row = 0, col = 4, type = KeyType.DIRECT_SYM, primaryLabel = "√"),
        KeyInfo(id = 405, row = 0, col = 5, type = KeyType.DIRECT_SYM, primaryLabel = "π"),
        KeyInfo(id = 406, row = 0, col = 6, type = KeyType.DIRECT_SYM, primaryLabel = "÷"),
        KeyInfo(id = 407, row = 0, col = 7, type = KeyType.DIRECT_SYM, primaryLabel = "×"),
        KeyInfo(id = 408, row = 0, col = 8, type = KeyType.DIRECT_SYM, primaryLabel = "§"),
        KeyInfo(id = 409, row = 0, col = 9, type = KeyType.DIRECT_SYM, primaryLabel = "Δ"),

        // Row 1 (10 keys)
        KeyInfo(id = 410, row = 1, col = 0, type = KeyType.DIRECT_SYM, primaryLabel = "£"),
        KeyInfo(id = 411, row = 1, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = "¢"),
        KeyInfo(id = 412, row = 1, col = 2, type = KeyType.DIRECT_SYM, primaryLabel = "€"),
        KeyInfo(id = 413, row = 1, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = "¥"),
        KeyInfo(id = 414, row = 1, col = 4, type = KeyType.DIRECT_SYM, primaryLabel = "^"),
        KeyInfo(id = 415, row = 1, col = 5, type = KeyType.DIRECT_SYM, primaryLabel = "°"),
        KeyInfo(id = 416, row = 1, col = 6, type = KeyType.DIRECT_SYM, primaryLabel = "="),
        KeyInfo(id = 417, row = 1, col = 7, type = KeyType.DIRECT_SYM, primaryLabel = "{"),
        KeyInfo(id = 418, row = 1, col = 8, type = KeyType.DIRECT_SYM, primaryLabel = "}"),
        KeyInfo(id = 419, row = 1, col = 9, type = KeyType.DIRECT_SYM, primaryLabel = "\\"),

        // Row 2 (9 keys: 1.5w, 7x 1w, 1.5w)
        KeyInfo(id = 420, row = 2, col = 0, type = KeyType.PAGE_SWITCH, primaryLabel = "?123", isActionKey = true),
        KeyInfo(id = 421, row = 2, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = "%"),
        KeyInfo(id = 422, row = 2, col = 2, type = KeyType.DIRECT_SYM, primaryLabel = "©"),
        KeyInfo(id = 423, row = 2, col = 3, type = KeyType.DIRECT_SYM, primaryLabel = "®"),
        KeyInfo(id = 424, row = 2, col = 4, type = KeyType.DIRECT_SYM, primaryLabel = "™"),
        KeyInfo(id = 425, row = 2, col = 5, type = KeyType.DIRECT_SYM, primaryLabel = "✓"),
        KeyInfo(id = 426, row = 2, col = 6, type = KeyType.DIRECT_SYM, primaryLabel = "["),
        KeyInfo(id = 427, row = 2, col = 7, type = KeyType.DIRECT_SYM, primaryLabel = "]"),
        KeyInfo(id = 428, row = 2, col = 8, type = KeyType.DEL, primaryLabel = "⌫", isActionKey = true),

        // Row 3 (6 keys: 1.5w, 1w, 1w, 4w, 1w, 1.5w)
        KeyInfo(id = 429, row = 3, col = 0, type = KeyType.PAGE_SWITCH, primaryLabel = "ABC", isActionKey = true),
        KeyInfo(id = 430, row = 3, col = 1, type = KeyType.DIRECT_SYM, primaryLabel = "<", isActionKey = true),
        KeyInfo(id = 431, row = 3, col = 2, type = KeyType.PAGE_SWITCH, primaryLabel = "12 34", isActionKey = true),
        KeyInfo(id = 432, row = 3, col = 3, type = KeyType.SPACE_0, primaryLabel = "␣", isActionKey = false),
        KeyInfo(id = 433, row = 3, col = 4, type = KeyType.DIRECT_SYM, primaryLabel = ">", isActionKey = true),
        KeyInfo(id = 434, row = 3, col = 5, type = KeyType.ENTER, primaryLabel = "↵", isActionKey = true)
    )


    private fun setKeyGridPosition(key: KeyInfo, r: Int, c: Int, offsetX: Float) {
        key.row = r
        key.col = c
        val left = offsetX + (c * colWidth)
        val top = stripHeight + (r * rowHeight)
        val right = left + colWidth
        val bottom = top + rowHeight
        key.bounds.set(left, top, right, bottom)
        key.centerX = left + (colWidth / 2f)
        key.centerY = top + (rowHeight / 2f)
        grid[r][c] = key
    }

    var layoutOrder: List<RearrangeKeyId> = KeyboardLayoutConfig.DEFAULT_ORDER
        private set

    fun setLayoutOrder(order: List<RearrangeKeyId>) {
        layoutOrder = order
        if (currentPage == KeyboardPage.PAGE_0_TEXT) {
            applyPage0RearrangedLayout(order, lastOffsetX)
        }
    }

    fun applyPage0RearrangedLayout(order: List<RearrangeKeyId> = layoutOrder, offsetX: Float = lastOffsetX) {
        if (colWidth <= 0f || rowHeight <= 0f) return
        layoutOrder = order

        // Reset references for affected cells in grid (col 3 and row 3)
        for (r in 0..3) {
            grid[r][3] = keys[r * 4 + 3]
        }
        for (c in 0..3) {
            grid[3][c] = keys[12 + c]
        }

        var slotIndex = 0
        for (keyEnum in order) {
            if (slotIndex >= KeyboardLayoutConfig.TRACK_SLOTS.size) break
            val key = keys[keyEnum.id]
            if (keyEnum.span == 1) {
                val coord = KeyboardLayoutConfig.TRACK_SLOTS[slotIndex]
                setKeyGridPosition(key, coord.row, coord.col, offsetX)
                slotIndex += 1
            } else if (keyEnum.span == 2 && slotIndex + 1 < KeyboardLayoutConfig.TRACK_SLOTS.size) {
                val coord1 = KeyboardLayoutConfig.TRACK_SLOTS[slotIndex]
                val coord2 = KeyboardLayoutConfig.TRACK_SLOTS[slotIndex + 1]

                val minCol = minOf(coord1.col, coord2.col)
                val maxCol = maxOf(coord1.col, coord2.col)
                val minRow = minOf(coord1.row, coord2.row)
                val maxRow = maxOf(coord1.row, coord2.row)

                val left = offsetX + (minCol * colWidth)
                val top = stripHeight + (minRow * rowHeight)
                val right = offsetX + ((maxCol + 1) * colWidth)
                val bottom = stripHeight + ((maxRow + 1) * rowHeight)

                key.row = minRow
                key.col = minCol
                key.bounds.set(left, top, right, bottom)
                key.centerX = (left + right) / 2f
                key.centerY = (top + bottom) / 2f

                grid[coord1.row][coord1.col] = key
                grid[coord2.row][coord2.col] = key

                slotIndex += 2
            }
        }

        keys[15].bounds.set(0f, 0f, 0f, 0f)
        keys[15].centerX = 0f
        keys[15].centerY = 0f
    }

    fun applyPage0UtilityRowLayout(offsetX: Float = lastOffsetX) {
        applyPage0RearrangedLayout(layoutOrder, offsetX)
    }

    fun computeGeometry(
        width: Float,
        height: Float,
        density: Float,
        offsetX: Float = 0f,
        contentWidth: Float = width
    ) {
        this.density = density
        totalWidth = width
        totalHeight = height
        stripHeight = (height * (40f / 260f)).coerceIn(30f * density, 48f * density)
        val keypadHeight = height - stripHeight
        rowHeight = keypadHeight / 4f
        colWidth = contentWidth / 4f
        lastOffsetX = offsetX

        stripBounds.set(offsetX, 0f, offsetX + contentWidth, stripHeight)
        val pillWidth = contentWidth * 0.12f
        pillBounds.set(offsetX, 0f, offsetX + pillWidth, stripHeight)
        candidateViewportBounds.set(offsetX + pillWidth, 0f, offsetX + contentWidth, stripHeight)

        // 1. 3x3 dial keys (Rows 0..2, Cols 0..2)
        val dialKeyIds = arrayOf(
            intArrayOf(0, 1, 2),
            intArrayOf(4, 5, 6),
            intArrayOf(8, 9, 10)
        )
        for (i in 0..2) {
            for (j in 0..2) {
                val key = keys[dialKeyIds[i][j]]
                setKeyGridPosition(key, i, j, offsetX)
            }
        }

        // 2 & 3. Movable action keys and utility row keys (Column 3 and Row 3)
        applyPage0RearrangedLayout(layoutOrder, offsetX)

        computePage1Geometry(contentWidth, height, density, offsetX)
        computePage23Geometry(page2Keys, contentWidth, height, density, offsetX)
        computePage23Geometry(page3Keys, contentWidth, height, density, offsetX)
        emojiAtlas.computeLayout(contentWidth, height, density, offsetX)
        updatePageLayout(currentPage)
    }

    fun computePage23Geometry(
        keysList: List<KeyInfo>,
        width: Float,
        height: Float,
        density: Float,
        offsetX: Float
    ) {
        val keyMargin = 2f * density
        val keypadHeight = height - stripHeight
        val rHeight = keypadHeight / 4f
        val uWidth = width / 10f

        for (key in keysList) {
            val rTop = stripHeight + (key.row * rHeight)
            val rBottom = rTop + rHeight
            val (startUnit, spanUnits) = when (key.row) {
                0, 1 -> Pair(key.col * 1.0f, 1.0f)
                2 -> when (key.col) {
                    0 -> Pair(0.0f, 1.5f)
                    in 1..7 -> Pair(1.5f + (key.col - 1) * 1.0f, 1.0f)
                    else -> Pair(8.5f, 1.5f)
                }
                3 -> when (key.col) {
                    0 -> Pair(0.0f, 1.5f)
                    1 -> Pair(1.5f, 1.0f)
                    2 -> Pair(2.5f, 1.0f)
                    3 -> Pair(3.5f, 4.0f)
                    4 -> Pair(7.5f, 1.0f)
                    else -> Pair(8.5f, 1.5f)
                }
                else -> Pair(0f, 1f)
            }
            val left = offsetX + (startUnit * uWidth)
            val right = offsetX + ((startUnit + spanUnits) * uWidth)
            key.bounds.set(left + keyMargin, rTop + keyMargin, right - keyMargin, rBottom - keyMargin)
            key.centerX = key.bounds.centerX()
            key.centerY = key.bounds.centerY()
        }
    }


    private fun computePage1Geometry(width: Float, height: Float, density: Float, offsetX: Float = 0f) {
        val topPadding = 12f * density
        val bottomPadding = 6f * density
        val keyMargin = 3f * density
        val availableHeight = height - topPadding - bottomPadding
        val p1RowHeight = availableHeight / 4f

        val sideColWidth = width * 0.14f
        val midColWidth = (width - 2f * sideColWidth) / 3f

        val opColLeft = 0f
        val actionColLeft = width - sideColWidth
        val midColStart = sideColWidth

        val r0Top = topPadding + (0 * p1RowHeight)
        val r0Bottom = r0Top + p1RowHeight
        val r1Top = topPadding + (1 * p1RowHeight)
        val r1Bottom = r1Top + p1RowHeight
        val r2Top = topPadding + (2 * p1RowHeight)
        val r2Bottom = r2Top + p1RowHeight
        val r3Top = topPadding + (3 * p1RowHeight)
        val r3Bottom = r3Top + p1RowHeight

        // Operator column container spans the 3 dial rows (Rows 0..2)
        val opTop = topPadding
        val opBottom = topPadding + (3f * p1RowHeight)
        page1ScrollContainerBounds.set(
            offsetX + opColLeft + keyMargin,
            opTop + keyMargin,
            offsetX + opColLeft + sideColWidth - keyMargin,
            opBottom - keyMargin
        )
        val slotHeight = page1ScrollContainerBounds.height() / 4f
        maxPage1ColumnScroll = -((page1OperatorItems.size - 4) * slotHeight)

        fun setKeyBounds(key: KeyInfo, left: Float, top: Float, right: Float, bottom: Float) {
            key.bounds.set(offsetX + left + keyMargin, top + keyMargin, offsetX + right - keyMargin, bottom - keyMargin)
            key.centerX = key.bounds.centerX()
            key.centerY = key.bounds.centerY()
        }

        // Row 0: 1, 2, 3, %
        setKeyBounds(page1KeyMap["1"]!!, midColStart, r0Top, midColStart + midColWidth, r0Bottom)
        setKeyBounds(page1KeyMap["2"]!!, midColStart + midColWidth, r0Top, midColStart + 2f * midColWidth, r0Bottom)
        setKeyBounds(page1KeyMap["3"]!!, midColStart + 2f * midColWidth, r0Top, midColStart + 3f * midColWidth, r0Bottom)
        setKeyBounds(page1KeyMap["%"]!!, actionColLeft, r0Top, actionColLeft + sideColWidth, r0Bottom)

        // Row 1: 4, 5, 6, Space
        setKeyBounds(page1KeyMap["4"]!!, midColStart, r1Top, midColStart + midColWidth, r1Bottom)
        setKeyBounds(page1KeyMap["5"]!!, midColStart + midColWidth, r1Top, midColStart + 2f * midColWidth, r1Bottom)
        setKeyBounds(page1KeyMap["6"]!!, midColStart + 2f * midColWidth, r1Top, midColStart + 3f * midColWidth, r1Bottom)
        setKeyBounds(page1KeyMap["␣"]!!, actionColLeft, r1Top, actionColLeft + sideColWidth, r1Bottom)

        // Row 2: 7, 8, 9, DEL
        setKeyBounds(page1KeyMap["7"]!!, midColStart, r2Top, midColStart + midColWidth, r2Bottom)
        setKeyBounds(page1KeyMap["8"]!!, midColStart + midColWidth, r2Top, midColStart + 2f * midColWidth, r2Bottom)
        setKeyBounds(page1KeyMap["9"]!!, midColStart + 2f * midColWidth, r2Top, midColStart + 3f * midColWidth, r2Bottom)
        setKeyBounds(page1KeyMap["⌫"]!!, actionColLeft, r2Top, actionColLeft + sideColWidth, r2Bottom)

        // Row 3: ABC, [, , !?#], 0, [= , .], Enter
        setKeyBounds(page1KeyMap["ABC"]!!, opColLeft, r3Top, opColLeft + sideColWidth, r3Bottom)

        // Col 1 split: , (42%) and !?# (58%)
        val col1Left = midColStart
        val col1Right = midColStart + midColWidth
        val splitCol1 = col1Left + (midColWidth * 0.42f)
        setKeyBounds(page1KeyMap[","]!!, col1Left, r3Top, splitCol1, r3Bottom)
        setKeyBounds(page1KeyMap["!?#"]!!, splitCol1, r3Top, col1Right, r3Bottom)

        // Col 2: 0
        setKeyBounds(page1KeyMap["0"]!!, midColStart + midColWidth, r3Top, midColStart + 2f * midColWidth, r3Bottom)

        // Col 3 split: = (58%) and . (42%)
        val col3Left = midColStart + 2f * midColWidth
        val col3Right = midColStart + 3f * midColWidth
        val splitCol3 = col3Left + (midColWidth * 0.58f)
        setKeyBounds(page1KeyMap["="]!!, col3Left, r3Top, splitCol3, r3Bottom)
        setKeyBounds(page1KeyMap["."]!!, splitCol3, r3Top, col3Right, r3Bottom)

        // Col 4: Enter
        setKeyBounds(page1KeyMap["↵"]!!, actionColLeft, r3Top, actionColLeft + sideColWidth, r3Bottom)
    }

    fun updatePageLayout(page: KeyboardPage) {
        currentPage = page
        when (page) {
            KeyboardPage.PAGE_0_TEXT -> configurePage0()
            KeyboardPage.PAGE_1_NUM_SYM -> configurePage1()
            KeyboardPage.PAGE_2_EXT_SYM, KeyboardPage.PAGE_3_EXT_SYM_ALT -> {}
            KeyboardPage.PAGE_4_EMOJI -> configureEmoji()
        }
    }

    private fun configureEmoji() {
        setupKey(12, KeyType.PAGE_SWITCH, "ABC", "")
    }

    private fun configurePage0() {
        // Row 1: [ .,?!'@# 1 ] [ ABC 2 ] [ DEF 3 ] [ ⌫ DEL ]
        setupKey(0, KeyType.PUNCT_1, ".,?!'@#", "1", digit = 1)
        setupKey(1, KeyType.DIGIT_T9, "ABC", "2", digit = 2)
        setupKey(2, KeyType.DIGIT_T9, "DEF", "3", digit = 3)
        setupKey(3, KeyType.DEL, "⌫", "")

        // Row 2: [ GHI 4 ] [ JKL 5 ] [ MNO 6 ] [ ⇧ SHIFT ]
        setupKey(4, KeyType.DIGIT_T9, "GHI", "4", digit = 4)
        setupKey(5, KeyType.DIGIT_T9, "JKL", "5", digit = 5)
        setupKey(6, KeyType.DIGIT_T9, "MNO", "6", digit = 6)
        setupKey(7, KeyType.SHIFT, "⇧", "")

        // Row 3: [ PQRS 7 ] [ TUV 8 ] [ WXYZ 9 ] [ ↵ ENTER ]
        setupKey(8, KeyType.DIGIT_T9, "PQRS", "7", digit = 7)
        setupKey(9, KeyType.DIGIT_T9, "TUV", "8", digit = 8)
        setupKey(10, KeyType.DIGIT_T9, "WXYZ", "9", digit = 9)
        setupKey(11, KeyType.ENTER, "↵", "")

        // Row 4: [ ?123 ] [ EN / ID / 😊 ] [ ␣ 0 SPACE (2 blocks) ]
        setupKey(12, KeyType.PAGE_SWITCH, "?123", "")
        setupKey(13, KeyType.LANG_SWITCH, keys[13].primaryLabel.ifEmpty { "EN" }, "😊")
        setupKey(14, KeyType.SPACE_0, "␣", "0", digit = 0)
        setupKey(15, KeyType.DIRECT_SYM, "", "")
        applyPage0RearrangedLayout(layoutOrder, lastOffsetX)
    }


    private fun configurePage1() {
        // Row 1: [ 1 ] [ 2 ] [ 3 ] [ ⌫ DEL ]
        setupKey(0, KeyType.DIRECT_SYM, "1", "")
        setupKey(1, KeyType.DIRECT_SYM, "2", "")
        setupKey(2, KeyType.DIRECT_SYM, "3", "")
        setupKey(3, KeyType.DEL, "⌫", "")

        // Row 2: [ 4 ] [ 5 ] [ 6 ] [ + - ]
        setupKey(4, KeyType.DIRECT_SYM, "4", "")
        setupKey(5, KeyType.DIRECT_SYM, "5", "")
        setupKey(6, KeyType.DIRECT_SYM, "6", "")
        setupKey(7, KeyType.DUAL_SYM, "+ -", "", left = "+", right = "-")

        // Row 3: [ 7 ] [ 8 ] [ 9 ] [ ↵ ENTER ]
        setupKey(8, KeyType.DIRECT_SYM, "7", "")
        setupKey(9, KeyType.DIRECT_SYM, "8", "")
        setupKey(10, KeyType.DIRECT_SYM, "9", "")
        setupKey(11, KeyType.ENTER, "↵", "")

        // Row 4: [ ABC ] [ =\\< ] [ 0 ␣ SPACE ] [ . , ]
        setupKey(12, KeyType.PAGE_SWITCH, "ABC", "")
        setupKey(13, KeyType.PAGE_SWITCH, "=\\<", "")
        setupKey(14, KeyType.SPACE_0, "0", "␣", digit = 0)
        setupKey(15, KeyType.DUAL_SYM, ". ,", "", left = ".", right = ",")
    }

    private fun setupKey(
        index: Int,
        type: KeyType,
        primary: String,
        sub: String,
        left: String = "",
        right: String = "",
        digit: Int = -1
    ) {
        val k = keys[index]
        k.type = type
        k.primaryLabel = primary
        k.lowerPrimaryLabel = primary.lowercase()
        k.upperPrimaryLabel = primary.uppercase()
        k.subLabel = sub
        k.leftGlyph = left
        k.rightGlyph = right
        k.digitValue = digit
    }

    fun findPage1OperatorIndex(y: Float, scrollOffset: Float): Int? {
        if (y < page1ScrollContainerBounds.top || y > page1ScrollContainerBounds.bottom) return null
        val slotHeight = page1ScrollContainerBounds.height() / 4f
        val localY = y - page1ScrollContainerBounds.top - scrollOffset
        val idx = (localY / slotHeight).toInt()
        return if (idx in 0 until page1OperatorItems.size) idx else null
    }

    fun findKeyAt(x: Float, y: Float): KeyInfo? {
        if (currentPage == KeyboardPage.PAGE_4_EMOJI) {
            return null
        }

        if (currentPage == KeyboardPage.PAGE_2_EXT_SYM) {
            for (key in page2Keys) {
                if (key.bounds.contains(x, y)) return key
            }
            var closest: KeyInfo? = null
            var minDistance = Float.MAX_VALUE
            for (key in page2Keys) {
                val dx = x - key.centerX
                val dy = y - key.centerY
                val dist = dx * dx + dy * dy
                if (dist < minDistance) {
                    minDistance = dist
                    closest = key
                }
            }
            val maxTouchDist = 48f * density
            return if (minDistance <= maxTouchDist * maxTouchDist) closest else null
        }

        if (currentPage == KeyboardPage.PAGE_3_EXT_SYM_ALT) {
            for (key in page3Keys) {
                if (key.bounds.contains(x, y)) return key
            }
            var closest: KeyInfo? = null
            var minDistance = Float.MAX_VALUE
            for (key in page3Keys) {
                val dx = x - key.centerX
                val dy = y - key.centerY
                val dist = dx * dx + dy * dy
                if (dist < minDistance) {
                    minDistance = dist
                    closest = key
                }
            }
            val maxTouchDist = 48f * density
            return if (minDistance <= maxTouchDist * maxTouchDist) closest else null
        }

        if (currentPage == KeyboardPage.PAGE_1_NUM_SYM) {
            // If in operator scroll container, handled directly by TouchGestureTracker
            if (page1ScrollContainerBounds.contains(x, y)) {
                return null
            }
            for (key in page1Keys) {
                if (key.bounds.contains(x, y)) {
                    return key
                }
            }
            // Fallback for margin touches to nearest key
            var closest: KeyInfo? = null
            var minDistance = Float.MAX_VALUE
            for (key in page1Keys) {
                val dx = x - key.centerX
                val dy = y - key.centerY
                val dist = dx * dx + dy * dy
                if (dist < minDistance) {
                    minDistance = dist
                    closest = key
                }
            }
            val maxTouchDist = 48f * density
            return if (minDistance <= maxTouchDist * maxTouchDist) closest else null
        }

        if (y < stripHeight) return null
        val r = ((y - stripHeight) / rowHeight).toInt().coerceIn(0, 3)
        val c = ((x - lastOffsetX) / colWidth).toInt().coerceIn(0, 3)
        val candidateKey = grid[r][c]

        if (candidateKey.id == 12 || candidateKey.type == KeyType.LANG_SWITCH) {
            val spaceMargin = 8f * density
            if (c + 1 in 0..3 && grid[r][c + 1].type == KeyType.SPACE_0 && x >= candidateKey.bounds.right - spaceMargin) {
                return grid[r][c + 1]
            }
            if (c - 1 in 0..3 && grid[r][c - 1].type == KeyType.SPACE_0 && x <= candidateKey.bounds.left + spaceMargin) {
                return grid[r][c - 1]
            }
        }

        return candidateKey
    }
}
