package com.opent9.keyboard.ui

/**
 * Coordinates emoji tab, swipe, and control interactions for T9KeyboardView.
 */
object EmojiInteractionCoordinator {

    fun onCategoryTap(
        categoryIndex: Int,
        emojiAtlas: EmojiAtlas,
        resetEmojiScroll: () -> Unit,
        invalidate: () -> Unit
    ) {
        val maxTab = emojiAtlas.categories.size - 1
        val safeIndex = categoryIndex.coerceIn(0, maxTab)
        if (safeIndex != emojiAtlas.activeCategoryIndex) {
            emojiAtlas.activeCategoryIndex = safeIndex
            resetEmojiScroll()
        }
        invalidate()
    }

    fun onControlTap(
        controlIndex: Int,
        keyAtlas: KeyAtlas,
        emojiAtlas: EmojiAtlas,
        resetEmojiScroll: () -> Unit,
        invalidate: () -> Unit,
        onEmojiControlAction: ((Int) -> Unit)?
    ) {
        when (controlIndex) {
            0 -> {
                keyAtlas.updatePageLayout(KeyboardPage.PAGE_0_TEXT)
                invalidate()
                onEmojiControlAction?.invoke(0)
            }
            1 -> {
                emojiAtlas.activeCategoryIndex = 0
                resetEmojiScroll()
                invalidate()
                onEmojiControlAction?.invoke(1)
            }
            2 -> {
                onEmojiControlAction?.invoke(2)
            }
            3 -> {
                onEmojiControlAction?.invoke(3)
            }
        }
    }

    fun onPageSwipe(
        direction: FlickDirection,
        emojiAtlas: EmojiAtlas,
        resetEmojiScroll: () -> Unit,
        invalidate: () -> Unit
    ) {
        val maxTab = emojiAtlas.categories.size - 1
        when (direction) {
            FlickDirection.LEFT -> {
                val next = (emojiAtlas.activeCategoryIndex + 1).coerceAtMost(maxTab)
                if (next != emojiAtlas.activeCategoryIndex) {
                    emojiAtlas.activeCategoryIndex = next
                    resetEmojiScroll()
                    invalidate()
                }
            }
            FlickDirection.RIGHT -> {
                val prev = (emojiAtlas.activeCategoryIndex - 1).coerceAtLeast(0)
                if (prev != emojiAtlas.activeCategoryIndex) {
                    emojiAtlas.activeCategoryIndex = prev
                    resetEmojiScroll()
                    invalidate()
                }
            }
            else -> {}
        }
    }
}
