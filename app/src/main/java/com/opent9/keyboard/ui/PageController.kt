package com.opent9.keyboard.ui

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class PageController(private val keyAtlas: KeyAtlas) {

    fun handleKeyFlick(
        key: KeyInfo,
        direction: FlickDirection,
        ic: InputConnection?,
        onSwitchLanguage: () -> Unit,
        onClearField: () -> Unit,
        onDeletePrecedingWord: () -> Unit,
        onForceSubmit: () -> Unit,
        onOpenSettings: () -> Unit = {},
        onSwitchPage: (KeyboardPage) -> Unit = { keyAtlas.updatePageLayout(it) }
    ) {
        if (ic == null) return

        // Handle universal system key flicks across all pages (DEL, ENTER, SPACE)
        when (key.type) {
            KeyType.DEL -> {
                when (direction) {
                    FlickDirection.UP, FlickDirection.LEFT -> onDeletePrecedingWord()
                    FlickDirection.DOWN -> onClearField()
                    FlickDirection.RIGHT -> {}
                }
                return
            }
            KeyType.ENTER -> {
                when (direction) {
                    FlickDirection.UP -> onForceSubmit()
                    FlickDirection.DOWN -> ic.commitText("\t", 1)
                    else -> {}
                }
                return
            }
            KeyType.SPACE_0 -> {
                when (direction) {
                    FlickDirection.DOWN -> ic.commitText("\u00A0", 1) // Non-breaking space
                    FlickDirection.LEFT -> {
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
                    }
                    FlickDirection.RIGHT -> {
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                    }
                    else -> {}
                }
                return
            }
            else -> {}
        }

        when (keyAtlas.currentPage) {
            KeyboardPage.PAGE_0_TEXT -> handlePage0Flick(
                key, direction, ic,
                onSwitchLanguage, onClearField, onDeletePrecedingWord, onForceSubmit, onOpenSettings, onSwitchPage
            )
            KeyboardPage.PAGE_1_NUM_SYM -> handlePage1Flick(key, direction, ic)
            KeyboardPage.PAGE_2_EXT_SYM -> handlePage2Flick(key, direction, ic)
            KeyboardPage.PAGE_3_EMOJI -> {}
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handlePage0Flick(
        key: KeyInfo,
        direction: FlickDirection,
        ic: InputConnection?,
        onSwitchLanguage: () -> Unit,
        onClearField: () -> Unit,
        onDeletePrecedingWord: () -> Unit,
        onForceSubmit: () -> Unit,
        onOpenSettings: () -> Unit = {},
        onSwitchPage: (KeyboardPage) -> Unit = { keyAtlas.updatePageLayout(it) }
    ) {
        if (ic == null) return

        when (key.id) {
            0 -> { // [ 1 .,?!'@# ]
                when (direction) {
                    FlickDirection.LEFT -> ic.commitText(",", 1)
                    FlickDirection.RIGHT -> ic.commitText(".", 1)
                    FlickDirection.DOWN -> onSwitchPage(KeyboardPage.PAGE_1_NUM_SYM)
                    else -> {}
                }
            }
            1, 2, 4, 5, 6, 8, 9, 10 -> { // [ 2 ABC ] to [ 9 WXYZ ]
                // Flicks disabled on Page 0 digit keys (numbers accessed via long-press or Page 1)
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
            12 -> { // [ ?123 / EN / 😊 Combined Utility Key ]
                when (direction) {
                    FlickDirection.UP -> onSwitchLanguage()
                    FlickDirection.DOWN -> onSwitchPage(KeyboardPage.PAGE_3_EMOJI)
                    else -> {}
                }
            }
            13 -> { // Legacy / Fallback
                when (direction) {
                    FlickDirection.UP -> onSwitchLanguage()
                    FlickDirection.DOWN -> onSwitchPage(KeyboardPage.PAGE_3_EMOJI)
                    else -> {}
                }
            }
            14 -> { // [ 0 ␣ SPACE ]
                when (direction) {
                    FlickDirection.DOWN -> ic.commitText("\u00A0", 1) // Non-breaking space
                    FlickDirection.LEFT -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                    FlickDirection.RIGHT -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    else -> {}
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
                when (key.id) {
                    7 -> ic.commitText("*", 1)
                    15 -> commitPairWithCursorInside(ic, "\"", "\"")
                    else -> {
                        val slot = keyAtlas.symbol3x3KeyIndices.indexOf(key.id)
                        if (slot in 0..8) {
                            val layer = keyAtlas.symbolLayers[keyAtlas.activeSymbolLayerIndex.coerceIn(0, keyAtlas.symbolLayers.size - 1)]
                            val down = layer[slot].downGlyph
                            when (down) {
                                "{}" -> commitPairWithCursorInside(ic, "{", "}")
                                "[]" -> commitPairWithCursorInside(ic, "[", "]")
                                "<>" -> commitPairWithCursorInside(ic, "<", ">")
                                "()" -> commitPairWithCursorInside(ic, "(", ")")
                                "\"\"" -> commitPairWithCursorInside(ic, "«", "»")
                                else -> if (down.isNotEmpty()) {
                                    ic.commitText(down, 1)
                                }
                            }
                        }
                    }
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
