#include "spatial_scoring.hpp"
#include <algorithm>

void SpatialScorer::updateKeyGeometry(int digit, float cx, float cy) {
    if (digit >= 0 && digit <= 9) {
        keyCoords[digit].digit = digit;
        keyCoords[digit].cx = cx;
        keyCoords[digit].cy = cy;
    }
}

float SpatialScorer::calculateLogLikelihood(int digit, float touchX, float touchY, float sigma) const {
    if (digit < 0 || digit > 9 || sigma <= 0.001f) {
        return 0.0f;
    }
    // If coordinates were not initialized or neutral (0, 0) touch was supplied, fallback to neutral
    if ((touchX == 0.0f && touchY == 0.0f) || (keyCoords[digit].cx == 0.0f && keyCoords[digit].cy == 0.0f)) {
        return 0.0f;
    }

    float dx = touchX - keyCoords[digit].cx;
    float dy = touchY - keyCoords[digit].cy;
    float distSq = dx * dx + dy * dy;
    float twoSigmaSq = 2.0f * sigma * sigma;

    // Log-likelihood: - distSq / (2 * sigma^2)
    return - (distSq / twoSigmaSq);
}
