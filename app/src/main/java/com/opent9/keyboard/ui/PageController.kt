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
            KeyboardPage.PAGE_1_NUM_SYM,
            KeyboardPage.PAGE_2_EXT_SYM,
            KeyboardPage.PAGE_3_EXT_SYM_ALT,
            KeyboardPage.PAGE_4_EMOJI -> {}
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
            12 -> { // [ ?123 ]
                when (direction) {
                    FlickDirection.DOWN -> onSwitchPage(KeyboardPage.PAGE_2_EXT_SYM)
                    else -> {}
                }
            }
            13 -> { // [ EN / ID / 😊 ]
                when (direction) {
                    FlickDirection.UP -> onSwitchLanguage()
                    FlickDirection.DOWN -> onSwitchPage(KeyboardPage.PAGE_4_EMOJI)
                    else -> {}
                }
            }

            14 -> { // [ 0 ␣ SPACE ]
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
            }
        }
    }
}

