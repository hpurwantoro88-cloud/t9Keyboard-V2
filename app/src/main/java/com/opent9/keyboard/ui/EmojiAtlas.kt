package com.opent9.keyboard.ui

import android.graphics.RectF

class EmojiAtlas {

    val categories = arrayOf(
        "😀 Smileys", "👤 Memoji"
    )

    // Full Gboard-style Smileys & Emotion emojis (~170 emojis)
    val smileysAndEmoticons = arrayOf(
        // Classic smileys & faces
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "🫠", "😉", "😊", "😇",
        "🥰", "😍", "🤩", "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑",
        "🤗", "🫣", "🤭", "🥱", "🤫", "🫡", "🫢", "🤐", "🤨", "🧐", "🤓", "😎", "🥸", "🥳",
        "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢",
        "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥",
        "😓", "🫨", "😶", "😶‍🌫️", "😐", "😑", "😬", "🫥", "🙄", "😯", "😦", "😧", "😮", "😲",
        "😴", "🤤", "😪", "😵", "😵‍💫", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑",
        "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃",
        // Cat faces
        "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾",
        // Monkey faces
        "🙈", "🙉", "🙊",
        // Heart & emotion symbols
        "💋", "💌", "💘", "💝", "💖", "💗", "💓", "💞", "💕", "💟", "💔", "❤️", "🧡", "💛",
        "💚", "💙", "💜", "🤎", "🖤", "🤍",
        // Expressive symbols
        "💯", "💢", "💥", "💫", "💦", "💨", "🕳️", "💬", "👁️‍🗨️", "🗨️", "🗯️", "💭", "💤"
    )

    // Common Memoji list: people, avatars, roles, expressions, gestures & animojis
    val commonMemojis = arrayOf(
        // People & Avatars
        "👶", "👧", "🧒", "👦", "👩", "🧑", "👨", "👩‍🦱", "🧑‍🦱", "👨‍🦱", "👩‍🦰", "🧑‍🦰", "👨‍🦰",
        "👱‍♀️", "👱", "👱‍♂️", "👩‍🦳", "🧑‍🦳", "👨‍🦳", "👩‍🦲", "🧑‍🦲", "👨‍🦲", "👵", "🧓", "👴",
        "👲", "👳‍♀️", "👳", "👳‍♂️", "🧕", "👮‍♀️", "👮", "👮‍♂️", "👷‍♀️", "👷", "👷‍♂️",
        "💂‍♀️", "💂", "💂‍♂️", "🕵️‍♀️", "🕵️", "🕵️‍♂️", "👩‍⚕️", "🧑‍⚕️", "👨‍⚕️", "👩‍🎓", "🧑‍🎓", "👨‍🎓",
        "👩‍🏫", "🧑‍🏫", "👨‍🏫", "👩‍⚖️", "🧑‍⚖️", "👨‍⚖️", "👩‍🌾", "🧑‍🌾", "👨‍🌾", "👩‍🍳", "🧑‍🍳", "👨‍🍳",
        "👩‍🔧", "🧑‍🔧", "👨‍🔧", "👩‍🏭", "🧑‍🏭", "👨‍🏭", "👩‍💼", "🧑‍💼", "👨‍💼", "👩‍🔬", "🧑‍🔬", "👨‍🔬",
        "👩‍💻", "🧑‍💻", "👨‍💻", "👩‍🎤", "🧑‍🎤", "👨‍🎤", "👩‍🎨", "🧑‍🎨", "👨‍🎨", "👩‍✈️", "🧑‍✈️", "👨‍✈️",
        "👩‍🚀", "🧑‍🚀", "👨‍🚀", "👩‍🚒", "🧑‍🚒", "👨‍🚒", "👸", "🤴", "👰", "🤵", "🦸", "🦹", "🧙",
        "🧝", "🧛", "🧟", "🧞", "🧜", "🧚", "👼",
        // Avatar Expressions & Actions
        "🙇", "💁", "🙅", "🙆", "🙋", "🧏", "🤦", "🤷", "🙎", "🙍", "💇", "💆", "🧖",
        "🚶", "🏃", "💃", "🕺", "👯", "🧘", "🛀", "🛌",
        // Animoji Animals
        "🐵", "🐶", "🐺", "🐱", "🦁", "🐯", "🦒", "🦊", "🦝", "🐮", "🐷", "🐗", "🐭", "🐹",
        "🐰", "🐻", "🐨", "🐼", "🐸", "🦓", "🐴", "🦄", "🐔", "🐲", "🐙",
        // Hand Gestures
        "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉", "👆",
        "🖕", "👇", "☝️", "🫵", "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "👏", "🙌", "🫶",
        "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪"
    )

    val categoryEmojiStrings: Array<Array<String>> = arrayOf(
        smileysAndEmoticons,
        commonMemojis
    )

    // Dynamic Recents
    val recentEmojis = mutableListOf(
        "😀", "😂", "🤣", "😍", "😘", "😊", "😁",
        "😄", "😅", "😆", "😉", "😋", "😎", "🥰",
        "🥳", "🥺", "😭", "😡", "👍", "👏", "🔥",
        "❤️", "💯", "🎉", "✨", "🙏", "👌", "🚀"
    )

    var activeCategoryIndex = 0 // default to Tab 0: Smileys & Emoticons
    private val scratchChars = CharArray(2)

    val gridCols = 7
    var tabHeight = 0f
        private set
    var ctrlHeight = 0f
        private set
    var gridTop = 0f
        private set
    var gridBottom = 0f
        private set
    var visibleGridHeight = 0f
        private set
    var gridRowHeight = 0f
        private set
    var gridColWidth = 0f
        private set
    var currentOffsetX = 0f
        private set

    val categoryTabBounds = Array(2) { RectF() }
    val emojiGridBounds = Array(28) { RectF() }
    val controlRowBounds = Array(4) { RectF() } // ABC, Recents, Space, Del

    fun computeLayout(width: Float, height: Float, density: Float, offsetX: Float = 0f) {
        currentOffsetX = offsetX
        tabHeight = (height * (40f / 260f)).coerceIn(30f * density, 48f * density)
        val tabWidth = width / 2f
        for (i in 0 until 2) {
            categoryTabBounds[i].set(offsetX + (i * tabWidth), 0f, offsetX + ((i + 1) * tabWidth), tabHeight)
        }

        ctrlHeight = (height * (40f / 260f)).coerceIn(30f * density, 48f * density)
        gridTop = tabHeight
        gridBottom = height - ctrlHeight
        visibleGridHeight = gridBottom - gridTop
        gridRowHeight = visibleGridHeight / 4f
        gridColWidth = width / gridCols.toFloat()

        for (i in 0 until 28) {
            val r = i / gridCols
            val c = i % gridCols
            val left = offsetX + (c * gridColWidth)
            val top = gridTop + (r * gridRowHeight)
            emojiGridBounds[i].set(left, top, left + gridColWidth, top + gridRowHeight)
        }

        val ctrlTop = gridBottom
        // [ ABC (25%) ] [ 🕒 Recents (25%) ] [ ␣ Space (25%) ] [ ⌫ DEL (25%) ]
        val ctrlColWidth = width / 4f
        for (i in 0 until 4) {
            controlRowBounds[i].set(offsetX + (i * ctrlColWidth), ctrlTop, offsetX + ((i + 1) * ctrlColWidth), ctrlTop + ctrlHeight)
        }
    }

    fun computeMaxScroll(tabIndex: Int): Float {
        val safeTab = tabIndex.coerceIn(0, categoryEmojiStrings.size - 1)
        val items = categoryEmojiStrings[safeTab]
        val totalRows = (items.size + gridCols - 1) / gridCols
        val totalHeight = totalRows * gridRowHeight
        return minOf(0f, visibleGridHeight - totalHeight)
    }

    fun getActiveEmojiList(): Array<String> {
        val safeTab = activeCategoryIndex.coerceIn(0, categoryEmojiStrings.size - 1)
        return categoryEmojiStrings[safeTab]
    }

    fun getEmojiString(tabIndex: Int, index: Int): String {
        val safeTab = tabIndex.coerceIn(0, categoryEmojiStrings.size - 1)
        val items = categoryEmojiStrings[safeTab]
        return if (index in items.indices) items[index] else ""
    }

    fun getEmojiString(codepoint: Int): String {
        val count = Character.toChars(codepoint, scratchChars, 0)
        return String(scratchChars, 0, count)
    }

    fun recordRecentEmoji(emoji: String) {
        recentEmojis.remove(emoji)
        recentEmojis.add(0, emoji)
        if (recentEmojis.size > 28) {
            recentEmojis.removeAt(recentEmojis.size - 1)
        }
    }

    fun recordRecentEmoji(codepoint: Int) {
        recordRecentEmoji(getEmojiString(codepoint))
    }

    fun findCategoryTabAt(x: Float, y: Float): Int? {
        if (categoryTabBounds[0].isEmpty) return null
        if (y < 0f || y >= tabHeight) return null
        val tabWidth = categoryTabBounds[0].width()
        if (tabWidth <= 0f) return null
        val idx = ((x - currentOffsetX) / tabWidth).toInt()
        return if (idx in 0 until categories.size) idx else null
    }

    fun findEmojiIndexAt(x: Float, y: Float, scrollY: Float = 0f): Int? {
        if (y < gridTop || y >= gridBottom) return null
        if (gridRowHeight <= 0f || gridColWidth <= 0f) return null
        val relX = x - currentOffsetX
        val relY = y - gridTop - scrollY
        if (relX < 0f || relY < 0f) return null
        val c = (relX / gridColWidth).toInt()
        val r = (relY / gridRowHeight).toInt()
        if (c !in 0 until gridCols || r < 0) return null
        val idx = r * gridCols + c
        val activeEmojis = getActiveEmojiList()
        return if (idx in activeEmojis.indices) idx else null
    }

    fun findControlIndexAt(x: Float, y: Float): Int? {
        if (controlRowBounds[0].isEmpty) return null
        val ctrlTop = controlRowBounds[0].top
        if (y < ctrlTop) return null
        val ctrlWidth = controlRowBounds[0].width()
        if (ctrlWidth <= 0f) return null
        val idx = ((x - currentOffsetX) / ctrlWidth).toInt()
        return if (idx in 0..3) idx else null
    }
}
