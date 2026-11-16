#pragma once
#include <cstdint>

struct KeyCoord {
    int digit;
    float cx;
    float cy;
};

struct DigitHypothesis {
    int digit;
    float log_likelihood;
    bool is_neighbor;
};

class SpatialScorer {
public:
    static constexpr int MAX_HYPOTHESES = 3;

    SpatialScorer() = default;

    void updateKeyGeometry(int digit, float cx, float cy);
    bool hasKeyGeometry(int digit) const;
    float calculateLogLikelihood(int digit, float touchX, float touchY, float sigma) const;

    /**
     * Collects the pressed digit and up to 2 spatially nearby T9 neighbor digits (2..9)
     * whose touch likelihood is within a soft border threshold of the primary digit.
     * Returns 1 hypothesis (neutral) when coordinates or geometry are uninitialized.
     */
    int collectDigitHypotheses(int primaryDigit, float touchX, float touchY, float sigma,
                               DigitHypothesis* outHypotheses, int maxHypotheses) const;

private:
    KeyCoord keyCoords[10] = {
        {0, 0.0f, 0.0f}, {1, 0.0f, 0.0f}, {2, 0.0f, 0.0f},
        {3, 0.0f, 0.0f}, {4, 0.0f, 0.0f}, {5, 0.0f, 0.0f},
        {6, 0.0f, 0.0f}, {7, 0.0f, 0.0f}, {8, 0.0f, 0.0f},
        {9, 0.0f, 0.0f}
    };
};
