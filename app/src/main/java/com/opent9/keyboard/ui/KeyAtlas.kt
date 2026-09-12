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
    val row: Int,
    val col: Int,
    var type: KeyType,
    val bounds: RectF = RectF(),
    var centerX: Float = 0f,
    var centerY: Float = 0f,
    var primaryLabel: String = "",
    var subLabel: String = "",
    var leftGlyph: String = "",
    var rightGlyph: String = "",
    var digitValue: Int = -1
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

    var currentPage: KeyboardPage = KeyboardPage.PAGE_0_TEXT

    fun computeGeometry(width: Float, height: Float, density: Float) {
        totalWidth = width
        totalHeight = height
        stripHeight = 40f * density
        val keypadHeight = height - stripHeight
        rowHeight = keypadHeight / 4f
        colWidth = width / 4f

        stripBounds.set(0f, 0f, width, stripHeight)
        val pillWidth = width * 0.12f
        pillBounds.set(0f, 0f, pillWidth, stripHeight)
        candidateViewportBounds.set(pillWidth, 0f, width, stripHeight)

        for (i in 0 until 16) {
            val r = i / 4
            val c = i % 4
            val left = c * colWidth
            val top = stripHeight + (r * rowHeight)
            val right = left + colWidth
            val bottom = top + rowHeight

            val key = keys[i]
            key.bounds.set(left, top, right, bottom)
            key.centerX = left + (colWidth / 2f)
            key.centerY = top + (rowHeight / 2f)
        }

        updatePageLayout(currentPage)
    }

    fun updatePageLayout(page: KeyboardPage) {
        currentPage = page
        when (page) {
            KeyboardPage.PAGE_0_TEXT -> configurePage0()
            KeyboardPage.PAGE_1_NUM_SYM -> configurePage1()
            KeyboardPage.PAGE_2_EXT_SYM -> configurePage2()
            KeyboardPage.PAGE_3_EMOJI -> {} // Emoji has dedicated 7-column layout
        }
    }

    private fun configurePage0() {
        // Row 1: [ 1 .,?!' ] [ 2 ABC ] [ 3 DEF ] [ ⌫ DEL ]
        setupKey(0, KeyType.PUNCT_1, "1", ".,?!'", digit = 1)
        setupKey(1, KeyType.DIGIT_T9, "2", "ABC", digit = 2)
        setupKey(2, KeyType.DIGIT_T9, "3", "DEF", digit = 3)
        setupKey(3, KeyType.DEL, "⌫", "")

        // Row 2: [ 4 GHI ] [ 5 JKL ] [ 6 MNO ] [ ⇧ SHIFT ]
        setupKey(4, KeyType.DIGIT_T9, "4", "GHI", digit = 4)
        setupKey(5, KeyType.DIGIT_T9, "5", "JKL", digit = 5)
        setupKey(6, KeyType.DIGIT_T9, "6", "MNO", digit = 6)
        setupKey(7, KeyType.SHIFT, "⇧", "")

        // Row 3: [ 7 PQRS ] [ 8 TUV ] [ 9 WXYZ ] [ ↵ ENTER ]
        setupKey(8, KeyType.DIGIT_T9, "7", "PQRS", digit = 7)
        setupKey(9, KeyType.DIGIT_T9, "8", "TUV", digit = 8)
        setupKey(10, KeyType.DIGIT_T9, "9", "WXYZ", digit = 9)
        setupKey(11, KeyType.ENTER, "↵", "")

        // Row 4: [ ?123 ] [ EN / ID ] [ 0 ␣ SPACE ] [ 😊 / . ]
        setupKey(12, KeyType.PAGE_SWITCH, "?123", "")
        setupKey(13, KeyType.LANG_SWITCH, "EN", "") // Updated dynamically by language state
        setupKey(14, KeyType.SPACE_0, "0", "␣", digit = 0)
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

    private fun configurePage2() {
        // Row 1: [ ~ ` ] [ \\ | ] [ { } ] [ ⌫ DEL ]
        setupKey(0, KeyType.DUAL_SYM, "~ `", "", left = "~", right = "`")
        setupKey(1, KeyType.DUAL_SYM, "\\ |", "", left = "\\", right = "|")
        setupKey(2, KeyType.DUAL_SYM, "{ }", "", left = "{", right = "}")
        setupKey(3, KeyType.DEL, "⌫", "")

        // Row 2: [ [ ] ] [ < > ] [ ( ) ] [ × ÷ ]
        setupKey(4, KeyType.DUAL_SYM, "[ ]", "", left = "[", right = "]")
        setupKey(5, KeyType.DUAL_SYM, "< >", "", left = "<", right = ">")
        setupKey(6, KeyType.DUAL_SYM, "( )", "", left = "(", right = ")")
        setupKey(7, KeyType.DUAL_SYM, "× ÷", "", left = "×", right = "÷")

        // Row 3: [ % ‰ ] [ Rp $ ] [ € £ ] [ ↵ ENTER ]
        setupKey(8, KeyType.DUAL_SYM, "% ‰", "", left = "%", right = "‰")
        setupKey(9, KeyType.DUAL_SYM, "Rp $", "", left = "Rp", right = "$")
        setupKey(10, KeyType.DUAL_SYM, "€ £", "", left = "€", right = "£")
        setupKey(11, KeyType.ENTER, "↵", "")

        // Row 4: [ ABC ] [ 123 ] [ 0 ␣ SPACE ] [ \" ' ]
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
        k.subLabel = sub
        k.leftGlyph = left
        k.rightGlyph = right
        k.digitValue = digit
    }

    fun findKeyAt(x: Float, y: Float): KeyInfo? {
        if (y < stripHeight) return null
        val r = ((y - stripHeight) / rowHeight).toInt().coerceIn(0, 3)
        val c = (x / colWidth).toInt().coerceIn(0, 3)
        val idx = r * 4 + c
        return if (idx in 0..15) keys[idx] else null
    }
}
