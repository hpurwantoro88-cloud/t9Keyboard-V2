package com.opent9.keyboard.ui

import android.graphics.RectF

enum class KeyboardPage {
    PAGE_0_TEXT,
    PAGE_1_NUM_SYM,
    PAGE_2_EXT_SYM,
    PAGE_3_EMOJI
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
    EMOJI_DOT,
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

data class SymbolLayerKey(
    val left: String,
    val right: String,
    val downGlyph: String = ""
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

    var col4thPosition: String = "right"
    var row4thPosition: String = "bottom"
    var row4thOrder: String = "default"
    var col4thOrder: String = "default"
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

    // Page 2 3x3 Scrollable Symbol Layer Elements
    val symbolGrid3x3Bounds = RectF()
    val symbolLayerTabBounds = Array(4) { RectF() }
    val symbolLayerTabTitles = arrayOf("{ }", "@ #", "° √", "© $")
    val symbol3x3KeyIndices = intArrayOf(0, 1, 2, 4, 5, 6, 8, 9, 10)

    val symbolLayers: Array<Array<SymbolLayerKey>> = arrayOf(
        // Layer 0: Brackets, Math & Currencies
        arrayOf(
            SymbolLayerKey("~", "`", "^"),
            SymbolLayerKey("\\", "|", "/"),
            SymbolLayerKey("{", "}", "{}"),
            SymbolLayerKey("[", "]", "[]"),
            SymbolLayerKey("<", ">", "<>"),
            SymbolLayerKey("(", ")", "()"),
            SymbolLayerKey("%", "‰", "/"),
            SymbolLayerKey("Rp", "$", "¢"),
            SymbolLayerKey("€", "£", "¥")
        ),
        // Layer 1: Web, Programming & Punctuation
        arrayOf(
            SymbolLayerKey("@", "#", "_"),
            SymbolLayerKey("&", "*", "^"),
            SymbolLayerKey("=", "≠", "≈"),
            SymbolLayerKey("/", "_", "-"),
            SymbolLayerKey(":", ";", "|"),
            SymbolLayerKey("!", "?", "~"),
            SymbolLayerKey("^", "|", "\\"),
            SymbolLayerKey("+", "-", "±"),
            SymbolLayerKey("×", "÷", "/")
        ),
        // Layer 2: Technical, Math, Units & Bullets
        arrayOf(
            SymbolLayerKey("°", "℃", "℉"),
            SymbolLayerKey("§", "¶", "†"),
            SymbolLayerKey("±", "∓", "="),
            SymbolLayerKey("•", "·", "…"),
            SymbolLayerKey("√", "∛", "^"),
            SymbolLayerKey("π", "∞", "∆"),
            SymbolLayerKey("²", "³", "¹"),
            SymbolLayerKey("µ", "∑", "Ω"),
            SymbolLayerKey("≤", "≥", "≠")
        ),
        // Layer 3: Currencies, Legal, Typography & Quotes
        arrayOf(
            SymbolLayerKey("¢", "¥", "Rp"),
            SymbolLayerKey("₩", "₽", "₺"),
            SymbolLayerKey("₿", "฿", "$"),
            SymbolLayerKey("©", "®", "™"),
            SymbolLayerKey("™", "℠", "℗"),
            SymbolLayerKey("¿", "¡", "?"),
            SymbolLayerKey("«", "»", "\"\""),
            SymbolLayerKey("“", "”", "„"),
            SymbolLayerKey("‘", "’", "‚")
        )
    )

    var activeSymbolLayerIndex: Int = 0
        private set

    fun setSymbolLayer(index: Int): Boolean {
        val clamped = index.coerceIn(0, symbolLayers.size - 1)
        if (activeSymbolLayerIndex != clamped) {
            activeSymbolLayerIndex = clamped
            if (currentPage == KeyboardPage.PAGE_2_EXT_SYM) {
                configurePage2()
            }
            return true
        }
        return false
    }

    fun nextSymbolLayer(): Boolean {
        return if (activeSymbolLayerIndex < symbolLayers.size - 1) {
            setSymbolLayer(activeSymbolLayerIndex + 1)
        } else false
    }

    fun prevSymbolLayer(): Boolean {
        return if (activeSymbolLayerIndex > 0) {
            setSymbolLayer(activeSymbolLayerIndex - 1)
        } else false
    }

    fun setLayoutConfiguration(
        colPosition: String = "right",
        rowPosition: String = "bottom",
        rowOrder: String = "default",
        colOrder: String = "default"
    ) {
        col4thPosition = colPosition
        row4thPosition = rowPosition
        row4thOrder = rowOrder
        col4thOrder = colOrder
    }

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

        val dialRows = if (row4thPosition == "top") intArrayOf(1, 2, 3) else intArrayOf(0, 1, 2)
        val dialCols = if (col4thPosition == "left") intArrayOf(1, 2, 3) else intArrayOf(0, 1, 2)
        val actionCol = if (col4thPosition == "left") 0 else 3
        val utilityRow = if (row4thPosition == "top") 0 else 3

        // 1. 3x3 dial keys
        val dialKeyIds = arrayOf(
            intArrayOf(0, 1, 2),
            intArrayOf(4, 5, 6),
            intArrayOf(8, 9, 10)
        )
        for (i in 0..2) {
            for (j in 0..2) {
                val key = keys[dialKeyIds[i][j]]
                setKeyGridPosition(key, dialRows[i], dialCols[j], offsetX)
            }
        }

        // 2. Action column keys (DEL: 3, SHIFT: 7, ENTER: 11)
        val actionKeyIds = when (col4thOrder) {
            "del_bottom" -> intArrayOf(7, 11, 3) // SHIFT, ENTER, DEL
            "enter_top" -> intArrayOf(11, 3, 7)  // ENTER, DEL, SHIFT
            "shift_top" -> intArrayOf(7, 3, 11)  // SHIFT, DEL, ENTER
            else -> intArrayOf(3, 7, 11)         // DEL, SHIFT, ENTER (default)
        }
        for (i in 0..2) {
            val key = keys[actionKeyIds[i]]
            setKeyGridPosition(key, dialRows[i], actionCol, offsetX)
        }

        // 3. Utility row keys and corner key
        if (row4thOrder == "reverse") {
            // Flipped: Emoji, Space, Language, ?123
            setKeyGridPosition(keys[15], utilityRow, 0, offsetX)
            setKeyGridPosition(keys[14], utilityRow, 1, offsetX)
            setKeyGridPosition(keys[13], utilityRow, 2, offsetX)
            setKeyGridPosition(keys[12], utilityRow, 3, offsetX)
        } else {
            val utilityKeyIds = when (row4thOrder) {
                "space_center" -> intArrayOf(12, 14, 13) // ?123, SPACE, LANG
                "space_left" -> intArrayOf(14, 13, 12)   // SPACE, LANG, ?123
                else -> intArrayOf(12, 13, 14)           // ?123, LANG, SPACE (default)
            }
            for (j in 0..2) {
                val key = keys[utilityKeyIds[j]]
                setKeyGridPosition(key, utilityRow, dialCols[j], offsetX)
            }
            val cornerKey = keys[15]
            setKeyGridPosition(cornerKey, utilityRow, actionCol, offsetX)
        }

        val grid3x3Left = offsetX + (dialCols[0] * colWidth)
        val grid3x3Top = stripHeight + (dialRows[0] * rowHeight)
        symbolGrid3x3Bounds.set(grid3x3Left, grid3x3Top, grid3x3Left + (colWidth * 3f), grid3x3Top + (rowHeight * 3f))
        val tabW = contentWidth / 4f
        for (i in 0 until 4) {
            symbolLayerTabBounds[i].set(offsetX + (i * tabW), 0f, offsetX + ((i + 1) * tabW), stripHeight)
        }

        computePage1Geometry(contentWidth, height, density, offsetX)
        emojiAtlas.computeLayout(contentWidth, height, density, offsetX)
        updatePageLayout(currentPage)
    }

    private fun computePage1Geometry(width: Float, height: Float, density: Float, offsetX: Float = 0f) {
        val topPadding = 12f * density
        val bottomPadding = 6f * density
        val keyMargin = 3f * density
        val availableHeight = height - topPadding - bottomPadding
        val p1RowHeight = availableHeight / 4f

        val sideColWidth = width * 0.14f
        val midColWidth = (width - 2f * sideColWidth) / 3f

        val actionColOnLeft = (col4thPosition == "left")
        val opColLeft = if (actionColOnLeft) width - sideColWidth else 0f
        val actionColLeft = if (actionColOnLeft) 0f else width - sideColWidth
        val midColStart = sideColWidth

        val row0Index = if (row4thPosition == "top") 1 else 0
        val row1Index = if (row4thPosition == "top") 2 else 1
        val row2Index = if (row4thPosition == "top") 3 else 2
        val row3Index = if (row4thPosition == "top") 0 else 3

        val r0Top = topPadding + (row0Index * p1RowHeight)
        val r0Bottom = r0Top + p1RowHeight
        val r1Top = topPadding + (row1Index * p1RowHeight)
        val r1Bottom = r1Top + p1RowHeight
        val r2Top = topPadding + (row2Index * p1RowHeight)
        val r2Bottom = r2Top + p1RowHeight
        val r3Top = topPadding + (row3Index * p1RowHeight)
        val r3Bottom = r3Top + p1RowHeight

        // Operator column container spans the 3 dial rows
        val opTop = if (row4thPosition == "top") r0Top else topPadding
        val opBottom = if (row4thPosition == "top") r2Bottom else topPadding + (3f * p1RowHeight)
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
            KeyboardPage.PAGE_2_EXT_SYM -> configurePage2()
            KeyboardPage.PAGE_3_EMOJI -> configurePage3()
        }
    }

    private fun configurePage3() {
        // Page 3: In-Canvas Native Emoji Picker
        // In the fallback keypad layout, ensure key 12 is ABC pointing to PAGE_0_TEXT
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

        // Row 4: [ ?123 ] [ EN / ID ] [ ␣ 0 SPACE ] [ 😊 / . ]
        setupKey(12, KeyType.PAGE_SWITCH, "?123", "")
        setupKey(13, KeyType.LANG_SWITCH, "EN", "") // Updated dynamically by language state
        setupKey(14, KeyType.SPACE_0, "␣", "0", digit = 0)
        setupKey(15, KeyType.EMOJI_DOT, "😊", ".")
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

    fun configurePage2() {
        val layer = symbolLayers[activeSymbolLayerIndex.coerceIn(0, symbolLayers.size - 1)]
        for (i in 0 until 9) {
            val keyIndex = symbol3x3KeyIndices[i]
            val item = layer[i]
            setupKey(keyIndex, KeyType.DUAL_SYM, "${item.left} ${item.right}", "", left = item.left, right = item.right)
        }

        // Fixed Column 3 (Col index 3)
        setupKey(3, KeyType.DEL, "⌫", "")
        setupKey(7, KeyType.DUAL_SYM, "× ÷", "", left = "×", right = "÷")
        setupKey(11, KeyType.ENTER, "↵", "")

        // Fixed Row 3 (Bottom Row)
        setupKey(12, KeyType.PAGE_SWITCH, "ABC", "")
        setupKey(13, KeyType.PAGE_SWITCH, "123", "")
        setupKey(14, KeyType.SPACE_0, "0", "␣", digit = 0)
        setupKey(15, KeyType.DUAL_SYM, "\" '", "", left = "\"", right = "'")
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
        if (currentPage == KeyboardPage.PAGE_3_EMOJI) {
            return null
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

        if (candidateKey.type == KeyType.LANG_SWITCH) {
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
