package com.opent9.keyboard.settings

/**
 * Identifiers for the 6 customizable keys on Row 4 and Column 4.
 */
enum class RearrangeKeyId(val id: Int, val span: Int, val displayName: String) {
    DEL(3, 1, "⌫ DEL"),
    SHIFT(7, 1, "⇧ SHIFT"),
    ENTER(11, 1, "↵ ENTER"),
    LANG(13, 1, "EN / ID / 😊"),
    SPACE(14, 2, "SPACE (2 blok)"),
    PAGE(12, 1, "?123");

    companion object {
        fun fromId(id: Int): RearrangeKeyId? = entries.firstOrNull { it.id == id }
        fun fromName(name: String): RearrangeKeyId? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

data class SlotCoord(val row: Int, val col: Int)

/**
 * Configuration and track coordinate mapping for row 4 and column 4 rearrangement.
 */
object KeyboardLayoutConfig {
    const val PREF_KEY = "custom_keyboard_layout"

    /**
     * Default layout order:
     * Kolom ke-4: DEL, SHIFT, ENTER, LANG
     * Baris ke-4: PAGE, SPACE (2 blok), [LANG at corner]
     *
     * Track slot mapping (7 slots total):
     * Slot 0: Row 0, Col 3 -> DEL
     * Slot 1: Row 1, Col 3 -> SHIFT
     * Slot 2: Row 2, Col 3 -> ENTER
     * Slot 3: Row 3, Col 3 -> LANG (Corner)
     * Slot 4: Row 3, Col 2 -> SPACE (right block)
     * Slot 5: Row 3, Col 1 -> SPACE (left block)
     * Slot 6: Row 3, Col 0 -> PAGE (?123)
     */
    val DEFAULT_ORDER: List<RearrangeKeyId> = listOf(
        RearrangeKeyId.DEL,
        RearrangeKeyId.SHIFT,
        RearrangeKeyId.ENTER,
        RearrangeKeyId.LANG,
        RearrangeKeyId.SPACE,
        RearrangeKeyId.PAGE
    )

    val TRACK_SLOTS = listOf(
        SlotCoord(0, 3), // Slot 0
        SlotCoord(1, 3), // Slot 1
        SlotCoord(2, 3), // Slot 2
        SlotCoord(3, 3), // Slot 3 (Corner)
        SlotCoord(3, 2), // Slot 4
        SlotCoord(3, 1), // Slot 5
        SlotCoord(3, 0)  // Slot 6
    )

    fun serialize(order: List<RearrangeKeyId>): String {
        return order.joinToString(",") { it.name }
    }

    fun deserialize(raw: String?): List<RearrangeKeyId> {
        if (raw.isNullOrBlank()) return DEFAULT_ORDER
        val parts = raw.split(",").mapNotNull { RearrangeKeyId.fromName(it.trim()) }
        if (parts.size == DEFAULT_ORDER.size &&
            parts.toSet().size == DEFAULT_ORDER.size &&
            parts.sumOf { it.span } == TRACK_SLOTS.size
        ) {
            return parts
        }
        return DEFAULT_ORDER
    }

    fun moveItem(order: List<RearrangeKeyId>, fromIndex: Int, toIndex: Int): List<RearrangeKeyId> {
        if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) {
            return order
        }
        val mutable = order.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }
}
