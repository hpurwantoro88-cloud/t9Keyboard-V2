package com.opent9.keyboard.ui

enum class ShiftMode(val stateValue: Int) {
    LOWERCASE(0),
    TITLECASE(1),
    UPPERCASE(2)
}

class ShiftController {

    var currentMode: ShiftMode = ShiftMode.LOWERCASE
        private set

    private var isCapsLockLocked: Boolean = false
    private var lastShiftTapTime: Long = 0L
    private var isManualOverride: Boolean = false

    fun isManualOverrideActive(): Boolean = isManualOverride

    fun clearManualOverride() {
        isManualOverride = false
    }

    /**
     * Programmatically apply auto-capitalization.
     * Returns true if currentMode was changed.
     */
    fun setAutoCapsMode(mode: ShiftMode): Boolean {
        if (isCapsLockLocked || isManualOverride) {
            return false
        }
        if (currentMode != mode) {
            currentMode = mode
            return true
        }
        return false
    }

    fun onShiftTap(nowMs: Long): ShiftMode {
        isManualOverride = true
        if (isCapsLockLocked) {
            // Unlocking caps lock always returns to lowercase
            isCapsLockLocked = false
            currentMode = ShiftMode.LOWERCASE
            lastShiftTapTime = nowMs
            return currentMode
        }

        // Check for double-tap to lock Caps Lock (within 350ms)
        if (nowMs - lastShiftTapTime <= 350L) {
            isCapsLockLocked = true
            currentMode = ShiftMode.UPPERCASE
            lastShiftTapTime = 0L
            return currentMode
        }

        lastShiftTapTime = nowMs

        // Standard 3-state cycle: Lower -> Title -> Upper -> Lower
        currentMode = when (currentMode) {
            ShiftMode.LOWERCASE -> ShiftMode.TITLECASE
            ShiftMode.TITLECASE -> ShiftMode.UPPERCASE
            ShiftMode.UPPERCASE -> ShiftMode.LOWERCASE
        }
        return currentMode
    }

    fun onShiftFlickUp(): ShiftMode {
        isManualOverride = true
        isCapsLockLocked = true
        currentMode = ShiftMode.UPPERCASE
        return currentMode
    }

    fun onShiftLongPress(): ShiftMode {
        isManualOverride = true
        isCapsLockLocked = true
        currentMode = ShiftMode.UPPERCASE
        return currentMode
    }

    /**
     * Called when a character or word is committed.
     * If TITLECASE was active and not locked in caps lock, auto-reset to LOWERCASE.
     * Clears manual override on character commit.
     */
    fun onCharacterCommitted(): ShiftMode {
        isManualOverride = false
        if (!isCapsLockLocked && currentMode == ShiftMode.TITLECASE) {
            currentMode = ShiftMode.LOWERCASE
        }
        return currentMode
    }

    fun isCapsLocked(): Boolean = isCapsLockLocked

    fun reset() {
        isCapsLockLocked = false
        currentMode = ShiftMode.LOWERCASE
        lastShiftTapTime = 0L
        isManualOverride = false
    }
}
