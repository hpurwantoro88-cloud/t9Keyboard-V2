package com.opent9.keyboard.ui

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class PageController(private val keyAtlas: KeyAtlas) {

    fun handleKeyFlick(
        key: KeyInfo,
        direction: FlickDirection,
        ic: InputConnection?,
        onSwitchLanguage: () -> Unit,
        onToggleT9Mode: () -> Unit,
        onClearField: () -> Unit,
        onDeletePrecedingWord: () -> Unit,
        onForceSubmit: () -> Unit
    ) {
        if (ic == null) return

        when (keyAtlas.currentPage) {
            KeyboardPage.PAGE_0_TEXT -> handlePage0Flick(
                key, direction, ic,
                onSwitchLanguage, onToggleT9Mode, onClearField, onDeletePrecedingWord, onForceSubmit
            )
            KeyboardPage.PAGE_1_NUM_SYM -> handlePage1Flick(key, direction, ic)
            KeyboardPage.PAGE_2_EXT_SYM -> handlePage2Flick(key, direction, ic)
            KeyboardPage.PAGE_3_EMOJI -> {}
        }
    }

    private fun handlePage0Flick(
        key: KeyInfo,
        direction: FlickDirection,
        ic: InputConnection?,
        onSwitchLanguage: () -> Unit,
        onToggleT9Mode: () -> Unit,
        onClearField: () -> Unit,
        onDeletePrecedingWord: () -> Unit,
        onForceSubmit: () -> Unit
    ) {
        if (ic == null) return

        when (key.id) {
            0 -> { // [ 1 .,?!' ]
                when (direction) {
                    FlickDirection.UP -> ic.commitText("1", 1)
                    FlickDirection.LEFT -> ic.commitText(",", 1)
                    FlickDirection.RIGHT -> ic.commitText(".", 1)
                    FlickDirection.DOWN -> keyAtlas.updatePageLayout(KeyboardPage.PAGE_1_NUM_SYM)
                }
            }
            1, 2, 4, 5, 6, 8, 9, 10 -> { // [ 2 ABC ] to [ 9 WXYZ ]
                when (direction) {
                    FlickDirection.UP -> key.digitValue.let { if (it >= 0) ic.commitText(it.toString(), 1) }
                    else -> {}
                }
            }
            3 -> { // [ ⌫ DEL ]
                when (direction) {
                    FlickDirection.UP, FlickDirection.LEFT -> onDeletePrecedingWord()
                    FlickDirection.DOWN -> onClearField()
                    FlickDirection.RIGHT -> {}
                }
            }
            7 -> { // [ ⇧ SHIFT ]
                when (direction) {
                    FlickDirection.UP -> {} // Handled via shift lock
                    else -> {}
                }
            }
            11 -> { // [ ↵ ENTER ]
                when (direction) {
                    FlickDirection.UP -> onForceSubmit()
                    FlickDirection.DOWN -> ic.commitText("\t", 1)
                    else -> {}
                }
            }
            12 -> { // [ ?123 ]
                when (direction) {
                    FlickDirection.DOWN -> keyAtlas.updatePageLayout(KeyboardPage.PAGE_2_EXT_SYM)
                    else -> {}
                }
            }
            13 -> { // [ EN / ID ]
                when (direction) {
                    FlickDirection.UP -> onToggleT9Mode()
                    FlickDirection.DOWN -> onSwitchLanguage()
                    else -> {}
                }
            }
            14 -> { // [ 0 ␣ SPACE ]
                when (direction) {
                    FlickDirection.UP -> ic.commitText("0", 1)
                    FlickDirection.DOWN -> ic.commitText("\u00A0", 1) // Non-breaking space
                    FlickDirection.LEFT -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                    FlickDirection.RIGHT -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                }
            }
            15 -> { // [ 😊 / . ]
                when (direction) {
                    FlickDirection.UP -> ic.commitText(". ", 1)
                    FlickDirection.DOWN -> ic.commitText(", ", 1)
                    FlickDirection.LEFT -> ic.commitText("-", 1) // Reduplication
                    FlickDirection.RIGHT -> ic.commitText(".", 1)
                }
            }
        }
    }

    private fun handlePage1Flick(key: KeyInfo, direction: FlickDirection, ic: InputConnection) {
        when (key.id) {
            7 -> { // [ + - ]
                when (direction) {
                    FlickDirection.LEFT -> ic.commitText("+", 1)
                    FlickDirection.RIGHT -> ic.commitText("-", 1)
                    FlickDirection.DOWN -> ic.commitText("_", 1)
                    else -> ic.commitText("+", 1)
                }
            }
            15 -> { // [ . , ]
                when (direction) {
                    FlickDirection.LEFT -> ic.commitText(".", 1)
                    FlickDirection.RIGHT -> ic.commitText(",", 1)
                    FlickDirection.DOWN -> ic.commitText(":", 1)
                    else -> ic.commitText(".", 1)
                }
            }
            else -> {}
        }
    }

    private fun handlePage2Flick(key: KeyInfo, direction: FlickDirection, ic: InputConnection) {
        if (key.type != KeyType.DUAL_SYM) return

        when (direction) {
            FlickDirection.LEFT -> {
                if (key.leftGlyph.isNotEmpty()) ic.commitText(key.leftGlyph, 1)
            }
            FlickDirection.RIGHT -> {
                if (key.rightGlyph.isNotEmpty()) ic.commitText(key.rightGlyph, 1)
            }
            FlickDirection.DOWN -> {
                // Paired symbols with cursor placed inside
                when (key.id) {
                    0 -> ic.commitText("^", 1)
                    1 -> ic.commitText("/", 1)
                    2 -> commitPairWithCursorInside(ic, "{", "}")
                    4 -> commitPairWithCursorInside(ic, "[", "]")
                    5 -> commitPairWithCursorInside(ic, "<", ">")
                    6 -> commitPairWithCursorInside(ic, "(", ")")
                    7 -> ic.commitText("*", 1)
                    8 -> ic.commitText("/", 1)
                    9 -> ic.commitText("¢", 1)
                    10 -> ic.commitText("¥", 1)
                    15 -> commitPairWithCursorInside(ic, "\"", "\"")
                }
            }
            FlickDirection.UP -> {
                if (key.leftGlyph.isNotEmpty()) ic.commitText(key.leftGlyph, 1)
            }
        }
    }

    private fun commitPairWithCursorInside(ic: InputConnection, open: String, close: String) {
        ic.commitText(open + close, 1)
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
    }
}
