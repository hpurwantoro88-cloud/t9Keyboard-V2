package com.opent9.keyboard.ui

import android.graphics.RectF

class EmojiAtlas {

    val categoryTabIcons = arrayOf(
        "🕒", "😀", "👋", "🐶", "🍔", "💡", "❤️", ";-)"
    )

    val categories = arrayOf(
        "Recents", "Smileys", "People", "Animals", "Food", "Objects", "Symbols", "Emoticons"
    )

    // Curated Smileys & Emotion emojis (deduplicated)
    val smileysAndEmoticons = arrayOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "🫠", "😉", "😊", "😇",
        "🥰", "😍", "🤩", "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑",
        "🤗", "🫣", "🤭", "🥱", "🤫", "🫡", "🫢", "🤐", "🤨", "🧐", "🤓", "😎", "🥸", "🥳",
        "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢",
        "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥",
        "😓", "🫨", "😶", "😶‍🌫️", "😐", "😑", "😬", "🫥", "🙄", "😯", "😦", "😧", "😮", "😲",
        "😴", "🤤", "😪", "😵", "😵‍💫", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤠", "😈",
        "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸",
        "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "🙈", "🙉", "🙊", "💋", "💌", "💘", "💝",
        "💖", "💗", "💓", "💞", "💕", "💟", "💔", "❤️", "🧡", "💛", "💚", "💙", "💜", "🤎",
        "🖤", "🤍", "🔥", "✨", "💯", "💢", "💥", "💫", "💦", "💨", "💬", "💭", "💤"
    )

    // People & Gestures
    val commonMemojis = arrayOf(
        "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉", "👆",
        "👇", "☝️", "🫵", "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "👏", "🙌", "🫶", "👐",
        "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "👶", "👧", "🧒", "👦", "👩", "🧑", "👨",
        "👩‍🦱", "🧑‍🦱", "👨‍🦱", "👩‍🦰", "🧑‍🦰", "👨‍🦰", "👱‍♀️", "👱", "👱‍♂️", "👩‍🦳", "🧑‍🦳", "👨‍🦳",
        "👵", "🧓", "👴", "👲", "🧕", "👮", "👷", "💂", "🕵️", "👩‍⚕️", "👨‍⚕️", "👩‍🎓", "👨‍🎓",
        "👩‍🏫", "👨‍🏫", "👩‍🍳", "👨‍🍳", "👩‍🔧", "👨‍🔧", "👩‍💼", "👨‍💼", "👩‍💻", "👨‍💻", "👸", "🤴",
        "👰", "🤵", "🦸", "🦹", "🧙", "🧚", "👼", "🙇", "💁", "🙅", "🙆", "🙋", "🤦", "🤷",
        "🙎", "🙍", "💇", "💆", "🚶", "🏃", "💃", "🕺", "🧘"
    )

    // Animals & Nature
    val animalsAndNature = arrayOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸",
        "🐵", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝",
        "🐛", "🦋", "🐌", "🐞", "🐜", "🦟", "🐢", "🐍", "🦎", "🐙", "🦑", "🦐", "🦞", "🦀",
        "🐡", "🐠", "🐟", "🐬", "🐳", "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🐘", "🦛", "🦏",
        "🐪", "🐫", "🦒", "🦘", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🐐", "🦌", "🐕",
        "🐩", "🐈", "🐓", "🦃", "🦚", "🦜", "🦢", "🕊️", "🐇", "🦝", "🌲", "🌳", "🌴", "🌵",
        "🌾", "🌿", "🍀", "🍁", "🍂", "🍃", "🍄", "🌸", "🌺", "🌹", "🌷", "🌻", "🌼", "☀️",
        "🌙", "⭐", "🌟", "🌈", "⚡", "❄️"
    )

    // Food & Drink
    val foodAndDrink = arrayOf(
        "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭",
        "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🌽", "🥕", "🧄", "🧅",
        "🥔", "🍠", "🥐", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓", "🍗",
        "🍖", "🌭", "🍔", "🍟", "🍕", "🥪", "🥙", "🌮", "🌯", "🥗", "🥘", "🍝", "🍜", "🍲",
        "🍛", "🍣", "🍱", "🥟", "🍤", "🍙", "🍚", "🍘", "🍢", "🍧", "🍨", "🍦", "🍰", "🎂",
        "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "🌰", "🥜", "🍯", "🥛", "☕", "🍵", "🧃",
        "🥤", "🍶", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹"
    )

    // Everyday Objects & Celebrations
    val objectsList = arrayOf(
        "🎉", "🎊", "🎈", "🎁", "🎀", "🏆", "🥇", "🎯", "🎮", "🎵", "🎶", "🎤", "🎧", "🎸",
        "⌚", "📱", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "📷", "📸", "📹", "🎥", "📞", "☎️", "📻",
        "⏱️", "⏰", "⏳", "🔋", "🔌", "💡", "🔦", "🕯️", "🗑️", "💵", "💰", "💳", "💎", "⚖️",
        "🧰", "🔧", "🔨", "🛠️", "⚙️", "🔑", "🗝️", "🚪", "📦", "✉️", "📧", "📝", "✏️", "🖊️",
        "📌", "📍", "📎", "📏", "✂️", "🔒", "🔓", "🔍", "🔎", "🏠", "✈️", "🚀"
    )

    // Essential Hearts & Symbols
    val symbolsList = arrayOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🤎", "🖤", "🤍", "💔", "❣️", "💕", "💞", "💓",
        "💗", "💖", "💘", "💝", "💟", "✅", "✔️", "☑️", "❌", "⭕", "🚫", "⛔", "⚠️", "❗",
        "❕", "❓", "❔", "‼️", "⁉️", "💯", "💢", "♻️", "💤", "🆗", "🆙", "🆒", "🆕", "🆓",
        "➕", "➖", "➗", "✖️", "♾️", "💲", "™️", "©️", "®️", "🔴", "🟠", "🟡", "🟢", "🔵",
        "🟣", "⚫", "⚪"
    )

    // Classic SwiftKey-style Text Emoticons (6 columns x 4 visible rows on top)
    val textEmoticons = arrayOf(
        ":-)", ":-(", ";-)", ":-P", ":-D", ":-|",
        ":-*", ":O", "B-)", ":-$", ":3", ":-/",
        ":-[", ":-\\", "O:-)", ":'(", "(^_-)", "(-:",
        ":-{", "8-)", "^O^", "^_^", "(^O^)", "<3",
        ":)", ":(", ";)", ":D", ":P", "XD",
        ":'D", "T_T", ";-;", ">_<", "o_O", "-_-",
        "^_~", "\\o/", "(>_<)", "¯\\_(ツ)_/¯", "<33", "</3"
    )

    // Dynamic Recents (24 items = 4 full rows of 6 columns)
    val recentEmojis = mutableListOf(
        "😀", "😂", "🤣", "😍", "😘", "😊",
        "😁", "😄", "😅", "😆", "😉", "😋",
        "😎", "🥰", "🥳", "🥺", "😭", "😡",
        "👍", "👏", "🔥", "❤️", "🙏", ";-)"
    )

    val categoryEmojiStrings: Array<Array<String>> = arrayOf(
        recentEmojis.toTypedArray(),
        smileysAndEmoticons,
        commonMemojis,
        animalsAndNature,
        foodAndDrink,
        objectsList,
        symbolsList,
        textEmoticons
    )

    var activeCategoryIndex = 1 // default to Tab 1: Smileys
    private val scratchChars = CharArray(2)

    val gridCols = 6
    val visibleRows = 4
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

    val categoryTabBounds = Array(categories.size) { RectF() }
    val emojiGridBounds = Array(gridCols * visibleRows) { RectF() }
    val controlRowBounds = Array(4) { RectF() } // ABC, Recents, Space, Del

    fun computeLayout(width: Float, height: Float, density: Float, offsetX: Float = 0f) {
        currentOffsetX = offsetX
        tabHeight = (height * (40f / 260f)).coerceIn(30f * density, 48f * density)
        val tabWidth = width / categories.size.toFloat()
        for (i in 0 until categories.size) {
            categoryTabBounds[i].set(offsetX + (i * tabWidth), 0f, offsetX + ((i + 1) * tabWidth), tabHeight)
        }

        ctrlHeight = (height * (40f / 260f)).coerceIn(30f * density, 48f * density)
        gridTop = tabHeight
        gridBottom = height - ctrlHeight
        visibleGridHeight = gridBottom - gridTop
        gridRowHeight = visibleGridHeight / visibleRows.toFloat()
        gridColWidth = width / gridCols.toFloat()

        for (i in emojiGridBounds.indices) {
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

    fun isTextEmoticon(item: String): Boolean {
        if (item.isEmpty()) return false
        val firstCodePoint = item.codePointAt(0)
        // Standard emoji blocks start well above ASCII (or use surrogate pairs / regional indicators)
        return firstCodePoint in 0x20..0x7E
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
        val maxRecents = gridCols * visibleRows
        while (recentEmojis.size > maxRecents) {
            recentEmojis.removeAt(recentEmojis.size - 1)
        }
        categoryEmojiStrings[0] = recentEmojis.toTypedArray()
    }

    fun recordRecentEmoji(codepoint: Int) {
        recordRecentEmoji(getEmojiString(codepoint))
    }

    fun findCategoryTabAt(x: Float, y: Float): Int? {
        if (categoryTabBounds[0].isEmpty) return null
        if (y < 0f || y >= tabHeight) return null
        val tabWidth = categoryTabBounds[0].width()
        if (tabWidth <= 0f) return null
        val relX = x - currentOffsetX
        if (relX < 0f) return null
        val idx = (relX / tabWidth).toInt()
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
        val relX = x - currentOffsetX
        if (relX < 0f) return null
        val idx = (relX / ctrlWidth).toInt()
        return if (idx in 0..3) idx else null
    }
}
