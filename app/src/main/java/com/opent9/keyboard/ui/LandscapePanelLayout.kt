package com.opent9.keyboard.ui

import android.graphics.RectF

enum class LandscapePanelDock {
    NONE,
    LEFT,
    RIGHT,
    CENTER
}

enum class LandscapePanelAction {
    UNDO,
    REDO,
    SELECT_ALL,
    CURSOR_UP,
    CURSOR_DOWN,
    CURSOR_LEFT,
    CURSOR_RIGHT,
    HOME,
    END,
    COPY,
    CUT,
    PASTE,
    TAB,
    SWAP_SIDE,
    EMOJI,
    HIDE_IME
}

data class LandscapePanelKey(
    val id: Int,
    var action: LandscapePanelAction,
    var label: String,
    val bounds: RectF = RectF()
)

/**
 * Computes geometry and hit-testing for the landscape side edit/utility panel
 * (for Compact Left/Right + Panel and Centered + Side Gutters modes).
 */
class LandscapePanelLayout {

    companion object {
        const val PANEL_KEY_ID_BASE = 1000
    }

    var dock: LandscapePanelDock = LandscapePanelDock.NONE
        private set

    val isActive: Boolean
        get() = dock != LandscapePanelDock.NONE && activeKeyCount > 0

    val leftPanelBounds = RectF()
    val rightPanelBounds = RectF()

    private val keyPool = Array(16) { i ->
        LandscapePanelKey(id = PANEL_KEY_ID_BASE + i, action = LandscapePanelAction.UNDO, label = "")
    }
    var activeKeyCount: Int = 0
        private set

    private var currentDensity: Float = 1f

    fun getKey(index: Int): LandscapePanelKey = keyPool[index]

    fun computeLayout(
        totalWidth: Float,
        totalHeight: Float,
        density: Float,
        t9OffsetX: Float,
        t9ContentWidth: Float,
        requestedDock: LandscapePanelDock
    ) {
        currentDensity = density
        activeKeyCount = 0
        leftPanelBounds.setEmpty()
        rightPanelBounds.setEmpty()

        if (requestedDock == LandscapePanelDock.NONE || totalWidth <= 0f || totalHeight <= 0f) {
            dock = LandscapePanelDock.NONE
            return
        }

        val keyMargin = 2.5f * density
        dock = requestedDock

        when (requestedDock) {
            LandscapePanelDock.LEFT -> {
                val panelLeft = t9OffsetX + t9ContentWidth
                val panelWidth = totalWidth - panelLeft
                if (panelWidth < 120f * density) {
                    dock = LandscapePanelDock.NONE
                    return
                }
                rightPanelBounds.set(panelLeft, 0f, totalWidth, totalHeight)
                populateFourByFourPanel(panelLeft, panelWidth, totalHeight, keyMargin)
            }
            LandscapePanelDock.RIGHT -> {
                val panelRight = t9OffsetX
                val panelWidth = panelRight
                if (panelWidth < 120f * density) {
                    dock = LandscapePanelDock.NONE
                    return
                }
                leftPanelBounds.set(0f, 0f, panelRight, totalHeight)
                populateFourByFourPanel(0f, panelWidth, totalHeight, keyMargin)
            }
            LandscapePanelDock.CENTER -> {
                val leftWidth = t9OffsetX
                val rightLeft = t9OffsetX + t9ContentWidth
                val rightWidth = totalWidth - rightLeft
                if (leftWidth < 48f * density || rightWidth < 48f * density) {
                    dock = LandscapePanelDock.NONE
                    return
                }
                leftPanelBounds.set(0f, 0f, leftWidth, totalHeight)
                rightPanelBounds.set(rightLeft, 0f, totalWidth, totalHeight)
                populateCenterGutters(leftWidth, rightLeft, rightWidth, totalHeight, keyMargin)
            }
            LandscapePanelDock.NONE -> {}
        }
    }

    private fun populateFourByFourPanel(
        panelLeft: Float,
        panelWidth: Float,
        totalHeight: Float,
        keyMargin: Float
    ) {
        val colW = panelWidth / 4f
        val rowH = totalHeight / 4f

        fun addKey(row: Int, col: Int, colSpan: Int, action: LandscapePanelAction, label: String) {
            val k = keyPool[activeKeyCount++]
            k.action = action
            k.label = label
            val left = panelLeft + (col * colW) + keyMargin
            val top = (row * rowH) + keyMargin
            val right = panelLeft + ((col + colSpan) * colW) - keyMargin
            val bottom = ((row + 1) * rowH) - keyMargin
            k.bounds.set(left, top, right, bottom)
        }

        // Row 0
        addKey(0, 0, 1, LandscapePanelAction.UNDO, "Undo")
        addKey(0, 1, 1, LandscapePanelAction.CURSOR_UP, "↑")
        addKey(0, 2, 1, LandscapePanelAction.REDO, "Redo")
        addKey(0, 3, 1, LandscapePanelAction.SELECT_ALL, "SelAll")

        // Row 1
        addKey(1, 0, 1, LandscapePanelAction.CURSOR_LEFT, "←")
        addKey(1, 1, 1, LandscapePanelAction.CURSOR_DOWN, "↓")
        addKey(1, 2, 1, LandscapePanelAction.CURSOR_RIGHT, "→")
        addKey(1, 3, 1, LandscapePanelAction.COPY, "Copy")

        // Row 2
        addKey(2, 0, 1, LandscapePanelAction.HOME, "Home")
        addKey(2, 1, 1, LandscapePanelAction.END, "End")
        addKey(2, 2, 1, LandscapePanelAction.CUT, "Cut")
        addKey(2, 3, 1, LandscapePanelAction.PASTE, "Paste")

        // Row 3
        addKey(3, 0, 2, LandscapePanelAction.SWAP_SIDE, "⇄")
        addKey(3, 2, 1, LandscapePanelAction.EMOJI, "😊")
        addKey(3, 3, 1, LandscapePanelAction.HIDE_IME, "Hide")
    }

    private fun populateCenterGutters(
        leftWidth: Float,
        rightLeft: Float,
        rightWidth: Float,
        totalHeight: Float,
        keyMargin: Float
    ) {
        val rowH = totalHeight / 4f

        fun addGutterKey(left: Float, width: Float, row: Int, action: LandscapePanelAction, label: String) {
            val k = keyPool[activeKeyCount++]
            k.action = action
            k.label = label
            k.bounds.set(
                left + keyMargin,
                (row * rowH) + keyMargin,
                left + width - keyMargin,
                ((row + 1) * rowH) - keyMargin
            )
        }

        // Left gutter
        addGutterKey(0f, leftWidth, 0, LandscapePanelAction.UNDO, "Undo")
        addGutterKey(0f, leftWidth, 1, LandscapePanelAction.CURSOR_LEFT, "←")
        addGutterKey(0f, leftWidth, 2, LandscapePanelAction.CURSOR_RIGHT, "→")
        addGutterKey(0f, leftWidth, 3, LandscapePanelAction.TAB, "Tab")

        // Right gutter
        addGutterKey(rightLeft, rightWidth, 0, LandscapePanelAction.COPY, "Copy")
        addGutterKey(rightLeft, rightWidth, 1, LandscapePanelAction.PASTE, "Paste")
        addGutterKey(rightLeft, rightWidth, 2, LandscapePanelAction.EMOJI, "😊")
        addGutterKey(rightLeft, rightWidth, 3, LandscapePanelAction.HIDE_IME, "Hide")
    }

    fun findKeyAt(x: Float, y: Float): LandscapePanelKey? {
        if (!isActive) return null
        val inPanel = (!leftPanelBounds.isEmpty && leftPanelBounds.contains(x, y)) ||
            (!rightPanelBounds.isEmpty && rightPanelBounds.contains(x, y))
        if (!inPanel) return null

        for (i in 0 until activeKeyCount) {
            val k = keyPool[i]
            if (k.bounds.contains(x, y)) return k
        }

        // Margin tolerance fallback inside panel bounds
        val tol = 6f * currentDensity
        for (i in 0 until activeKeyCount) {
            val k = keyPool[i]
            if (x >= k.bounds.left - tol && x <= k.bounds.right + tol &&
                y >= k.bounds.top - tol && y <= k.bounds.bottom + tol
            ) {
                return k
            }
        }
        return null
    }

    fun isTouchInsideKey(key: LandscapePanelKey, x: Float, y: Float): Boolean {
        val tol = 8f * currentDensity
        return x >= key.bounds.left - tol && x <= key.bounds.right + tol &&
            y >= key.bounds.top - tol && y <= key.bounds.bottom + tol
    }
}
