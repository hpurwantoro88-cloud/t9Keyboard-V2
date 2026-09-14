#include "multi_tap_engine.hpp"
#include <cctype>
#include <cstring>

MultiTapEngine::MultiTapEngine() {
    reset();
}

void MultiTapEngine::reset() {
    activeDigit = -1;
    cycleIndex = 0;
    lastPressTime = 0;
    currentCycleChar = 0;
}

const char* MultiTapEngine::getCycleSequence(int digit) {
    switch (digit) {
        case 1: return ".,?!'@#1";
        case 2: return "abc2";
        case 3: return "def3";
        case 4: return "ghi4";
        case 5: return "jkl5";
        case 6: return "mno6";
        case 7: return "pqrs7";
        case 8: return "tuv8";
        case 9: return "wxyz9";
        case 0: return " 0\n";
        default: return "";
    }
}

char MultiTapEngine::onKeyPress(int digit, uint64_t timestampMs, ShiftStateEnum shift,
                               bool* outCommittedPrevious, char* outCommittedChar) {
    if (outCommittedPrevious) *outCommittedPrevious = false;
    if (outCommittedChar) *outCommittedChar = 0;

    const char* seq = getCycleSequence(digit);
    if (!seq || seq[0] == '\0') {
        return 0;
    }

    size_t seqLen = std::strlen(seq);

    if (activeDigit != -1 && activeDigit != digit) {
        // Switching to a different key commits previous char
        if (outCommittedPrevious && outCommittedChar) {
            *outCommittedPrevious = true;
            *outCommittedChar = currentCycleChar;
        }
        activeDigit = digit;
        cycleIndex = 0;
    } else if (activeDigit == digit) {
        // Same key pressed: advance cycle
        cycleIndex = (cycleIndex + 1) % seqLen;
    } else {
        // First key press
        activeDigit = digit;
        cycleIndex = 0;
    }

    lastPressTime = timestampMs;
    char baseChar = seq[cycleIndex];

    if ((shift == ShiftStateEnum::TITLECASE || shift == ShiftStateEnum::UPPERCASE) &&
        std::islower(static_cast<unsigned char>(baseChar))) {
        currentCycleChar = static_cast<char>(std::toupper(static_cast<unsigned char>(baseChar)));
    } else {
        currentCycleChar = baseChar;
    }

    return currentCycleChar;
}

bool MultiTapEngine::checkTimeout(uint64_t timestampMs, uint32_t timeoutMs, char* outCommittedChar) {
    if (activeDigit != -1 && (timestampMs - lastPressTime) >= timeoutMs) {
        if (outCommittedChar) {
            *outCommittedChar = currentCycleChar;
        }
        reset();
        return true;
    }
    return false;
}

char MultiTapEngine::commitActive() {
    if (activeDigit == -1) return 0;
    char committed = currentCycleChar;
    reset();
    return committed;
}
