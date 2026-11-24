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

    /**
     * Returns normalized log10 posterior log10(P(digit | touchX, touchY)) using
     * anisotropic (sigmaX, sigmaY) derived from key spacing when full grid geometry
     * is available, falling back to isotropic sigma otherwise.
     */
    float calculateLogLikelihood(int digit, float touchX, float touchY, float sigma) const;

    /**
     * Collects the pressed digit and up to 2 spatially nearby T9 digit hypotheses (2..9)
     * whose normalized posterior P(k | x, y) exceeds the border threshold.
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
    float keyWidth = 0.0f;
    float keyHeight = 0.0f;

    void recomputeKeySpacing();
    void resolveSigmas(float sigma, float& outSigmaX, float& outSigmaY) const;
    float rawLogLikelihood(int digit, float touchX, float touchY, float sigmaX, float sigmaY) const;
    int computePosteriors(float touchX, float touchY, float sigma, float outPosteriors[10], bool outValid[10]) const;
};
