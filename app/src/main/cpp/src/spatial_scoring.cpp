#include "spatial_scoring.hpp"
#include <algorithm>

namespace {

// Adjacent T9 digits (2..9 only) on the 3x4 keypad grid
constexpr int T9_NEIGHBORS[10][5] = {
    {-1},              // 0
    {-1},              // 1
    {3, 4, 5, -1},     // 2
    {2, 5, 6, -1},     // 3
    {2, 5, 7, -1},     // 4
    {2, 4, 6, 8, -1},  // 5
    {3, 5, 8, 9, -1},  // 6
    {4, 5, 8, -1},     // 7
    {5, 7, 9, -1},     // 8
    {5, 6, 8, -1}      // 9
};

constexpr float MAX_NEIGHBOR_LL_GAP = 2.6f;

} // namespace

void SpatialScorer::updateKeyGeometry(int digit, float cx, float cy) {
    if (digit >= 0 && digit <= 9) {
        keyCoords[digit].digit = digit;
        keyCoords[digit].cx = cx;
        keyCoords[digit].cy = cy;
    }
}

bool SpatialScorer::hasKeyGeometry(int digit) const {
    if (digit < 0 || digit > 9) return false;
    return keyCoords[digit].cx != 0.0f || keyCoords[digit].cy != 0.0f;
}

float SpatialScorer::calculateLogLikelihood(int digit, float touchX, float touchY, float sigma) const {
    if (digit < 0 || digit > 9 || sigma <= 0.001f) {
        return 0.0f;
    }
    if ((touchX == 0.0f && touchY == 0.0f) || !hasKeyGeometry(digit)) {
        return 0.0f;
    }

    float dx = touchX - keyCoords[digit].cx;
    float dy = touchY - keyCoords[digit].cy;
    float distSq = dx * dx + dy * dy;
    float twoSigmaSq = 2.0f * sigma * sigma;

    return -(distSq / twoSigmaSq);
}

int SpatialScorer::collectDigitHypotheses(int primaryDigit, float touchX, float touchY, float sigma,
                                          DigitHypothesis* outHypotheses, int maxHypotheses) const {
    if (!outHypotheses || maxHypotheses <= 0 || primaryDigit < 2 || primaryDigit > 9) {
        return 0;
    }

    float primaryLl = calculateLogLikelihood(primaryDigit, touchX, touchY, sigma);
    outHypotheses[0] = { primaryDigit, primaryLl, false };

    if (maxHypotheses == 1 || (touchX == 0.0f && touchY == 0.0f) || !hasKeyGeometry(primaryDigit) || sigma <= 0.001f) {
        return 1;
    }

    DigitHypothesis neighbors[4];
    int neighborCount = 0;

    const int* adjList = T9_NEIGHBORS[primaryDigit];
    for (int i = 0; adjList[i] != -1 && neighborCount < 4; ++i) {
        int adj = adjList[i];
        if (adj < 2 || adj > 9 || !hasKeyGeometry(adj)) continue;
        float adjLl = calculateLogLikelihood(adj, touchX, touchY, sigma);
        if (adjLl >= primaryLl - MAX_NEIGHBOR_LL_GAP) {
            neighbors[neighborCount++] = { adj, adjLl, true };
        }
    }

    if (neighborCount > 1) {
        std::sort(neighbors, neighbors + neighborCount, [](const DigitHypothesis& a, const DigitHypothesis& b) {
            return a.log_likelihood > b.log_likelihood;
        });
    }

    int count = 1;
    for (int i = 0; i < neighborCount && count < maxHypotheses; ++i) {
        outHypotheses[count++] = neighbors[i];
    }
    return count;
}
