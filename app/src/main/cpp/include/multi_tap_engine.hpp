#pragma once
#include <cstdint>
#include <cstddef>

enum class ShiftStateEnum : int {
    LOWERCASE = 0,
    TITLECASE = 1,
    UPPERCASE = 2
};

class MultiTapEngine {
public:
    MultiTapEngine();

    // Handles key stroke. If previous multi-tap char was committed due to key switch,
    // outCommittedPrevious is set to true and outCommittedChar contains the committed char.
    // Returns currently active cycling char.
    char onKeyPress(int digit, uint64_t timestampMs, ShiftStateEnum shift,
                    bool* outCommittedPrevious, char* outCommittedChar);

    // Checks if 600ms timeout has elapsed since last key press
    bool checkTimeout(uint64_t timestampMs, uint32_t timeoutMs, char* outCommittedChar);

    // Explicitly commits active character
    char commitActive();

    bool hasActiveChar() const { return activeDigit != -1; }
    char getActiveChar() const { return currentCycleChar; }
    int getActiveDigit() const { return activeDigit; }

    void reset();

private:
    int activeDigit = -1;
    int cycleIndex = 0;
    uint64_t lastPressTime = 0;
    char currentCycleChar = 0;

    static const char* getCycleSequence(int digit);
};
