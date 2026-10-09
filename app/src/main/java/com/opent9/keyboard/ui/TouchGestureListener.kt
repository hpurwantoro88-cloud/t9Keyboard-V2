package com.opent9.keyboard.ui

interface TouchGestureListener {
    fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float)
    fun onKeyFlick(key: KeyInfo, direction: FlickDirection)
    fun onKeyLongPress(key: KeyInfo)
    fun onKeyDeleteRepeat(isWordDelete: Boolean) {}
    fun onSpaceScrub(steps: Int) // +1 for DPAD_RIGHT, -1 for DPAD_LEFT
    fun onPillTap()
    fun onCandidateTap(index: Int)
    fun onCandidateLongPress(index: Int) {}
    fun onStripScroll(newScrollOffset: Float)
    fun onTouchStateChanged(activeKeyId: Int?)

    // Page 3 Emoji callbacks
    fun onEmojiCategoryTap(categoryIndex: Int) {}
    fun onEmojiTap(emoji: String) {}
    fun onEmojiControlTap(controlIndex: Int) {}
    fun onEmojiPageSwipe(direction: FlickDirection) {}
    fun onEmojiTouchStateChanged(tabIndex: Int?, gridIndex: Int?, controlIndex: Int?) {}

    // Page 2 Symbol layer callback
    fun onSymbolLayerChanged(layerIndex: Int) {}
}
