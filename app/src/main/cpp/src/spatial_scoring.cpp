#include "spatial_scoring.hpp"
#include <algorithm>
#include <cmath>

#ifdef OPENT9_EVAL_TUNING
float g_evalMinNeighborLog10Posterior = -1.70f;
#endif

namespace {

constexpr float ALPHA_X = 0.22f;
constexpr float ALPHA_Y = 0.26f;
constexpr float MIN_SIGMA_SCALE = 0.50f;
constexpr float MAX_SIGMA_SCALE = 2.00f;
// Minimum posterior probability P(k | x, y) >= 0.02 (~log10(0.02) = -1.699) to consider a neighbor hypothesis
constexpr float MIN_NEIGHBOR_LOG10_POSTERIOR = -1.70f;
constexpr float LN_10 = 2.30258509299f;

constexpr int HORIZ_PAIRS[][2] = {{2, 3}, {4, 5}, {5, 6}, {7, 8}, {8, 9}};
constexpr int VERT_PAIRS[][2] = {{2, 5}, {3, 6}, {4, 7}, {5, 8}, {6, 9}};

} // namespace

void SpatialScorer::updateKeyGeometry(int digit, float cx, float cy) {
    if (digit >= 0 && digit <= 9) {
        keyCoords[digit].digit = digit;
        keyCoords[digit].cx = cx;
        keyCoords[digit].cy = cy;
        recomputeKeySpacing();
    }
}

bool SpatialScorer::hasKeyGeometry(int digit) const {
    if (digit < 0 || digit > 9) return false;
    return keyCoords[digit].cx != 0.0f || keyCoords[digit].cy != 0.0f;
}

void SpatialScorer::recomputeKeySpacing() {
    float sumW = 0.0f;
    int countW = 0;
    for (const auto& p : HORIZ_PAIRS) {
        if (hasKeyGeometry(p[0]) && hasKeyGeometry(p[1])) {
            float dx = std::fabs(keyCoords[p[1]].cx - keyCoords[p[0]].cx);
            if (dx > 1.0f) {
                sumW += dx;
                countW++;
            }
        }
    }
    keyWidth = (countW > 0) ? (sumW / static_cast<float>(countW)) : 0.0f;

    float sumH = 0.0f;
    int countH = 0;
    for (const auto& p : VERT_PAIRS) {
        if (hasKeyGeometry(p[0]) && hasKeyGeometry(p[1])) {
            float dy = std::fabs(keyCoords[p[1]].cy - keyCoords[p[0]].cy);
            if (dy > 1.0f) {
                sumH += dy;
                countH++;
            }
        }
    }
    keyHeight = (countH > 0) ? (sumH / static_cast<float>(countH)) : 0.0f;
}

void SpatialScorer::resolveSigmas(float sigma, float& outSigmaX, float& outSigmaY) const {
    if (keyWidth > 1.0f && keyHeight > 1.0f) {
        float sx = ALPHA_X * keyWidth;
        float sy = ALPHA_Y * keyHeight;
        if (sigma > 0.001f) {
            float minS = sigma * MIN_SIGMA_SCALE;
            float maxS = sigma * MAX_SIGMA_SCALE;
            sx = std::clamp(sx, minS, maxS);
            sy = std::clamp(sy, minS, maxS);
        }
        outSigmaX = sx;
        outSigmaY = sy;
    } else {
        outSigmaX = sigma;
        outSigmaY = sigma;
    }
}

float SpatialScorer::rawLogLikelihood(int digit, float touchX, float touchY, float sigmaX, float sigmaY) const {
    float dx = touchX - keyCoords[digit].cx;
    float dy = touchY - keyCoords[digit].cy;
    float nx = dx / sigmaX;
    float ny = dy / sigmaY;
    return -0.5f * (nx * nx + ny * ny);
}

int SpatialScorer::computePosteriors(float touchX, float touchY, float sigma,
                                     float outPosteriors[10], bool outValid[10]) const {
    float sigmaX = sigma;
    float sigmaY = sigma;
    resolveSigmas(sigma, sigmaX, sigmaY);

    float lls[10]{};
    float maxLl = -1e30f;
    int validCount = 0;

    for (int d = 2; d <= 9; ++d) {
        outPosteriors[d] = 0.0f;
        outValid[d] = hasKeyGeometry(d);
        if (outValid[d]) {
            lls[d] = rawLogLikelihood(d, touchX, touchY, sigmaX, sigmaY);
            if (lls[d] > maxLl) maxLl = lls[d];
            validCount++;
        }
    }

    if (validCount <= 1) {
        for (int d = 2; d <= 9; ++d) {
            if (outValid[d]) {
                outPosteriors[d] = lls[d] / LN_10;
            }
        }
        return validCount;
    }

    double sumExp = 0.0;
    for (int d = 2; d <= 9; ++d) {
        if (outValid[d]) {
            sumExp += std::exp(static_cast<double>(lls[d] - maxLl));
        }
    }
    double logSumExp = static_cast<double>(maxLl) + std::log(sumExp);
    for (int d = 2; d <= 9; ++d) {
        if (outValid[d]) {
            outPosteriors[d] = static_cast<float>((static_cast<double>(lls[d]) - logSumExp) / static_cast<double>(LN_10));
        }
    }
    return validCount;
}

float SpatialScorer::calculateLogLikelihood(int digit, float touchX, float touchY, float sigma) const {
    if (digit < 0 || digit > 9 || sigma <= 0.001f) {
        return 0.0f;
    }
    if ((touchX == 0.0f && touchY == 0.0f) || !hasKeyGeometry(digit)) {
        return 0.0f;
    }
    if (digit < 2) {
        float sigmaX = sigma;
        float sigmaY = sigma;
        resolveSigmas(sigma, sigmaX, sigmaY);
        return rawLogLikelihood(digit, touchX, touchY, sigmaX, sigmaY) / LN_10;
    }

    float posteriors[10]{};
    bool valid[10]{};
    computePosteriors(touchX, touchY, sigma, posteriors, valid);
    return valid[digit] ? posteriors[digit] : 0.0f;
}

int SpatialScorer::collectDigitHypotheses(int primaryDigit, float touchX, float touchY, float sigma,
                                          DigitHypothesis* outHypotheses, int maxHypotheses) const {
    if (!outHypotheses || maxHypotheses <= 0 || primaryDigit < 2 || primaryDigit > 9) {
        return 0;
    }

    if ((touchX == 0.0f && touchY == 0.0f) || !hasKeyGeometry(primaryDigit) || sigma <= 0.001f) {
        outHypotheses[0] = { primaryDigit, 0.0f, false };
        return 1;
    }

    float posteriors[10]{};
    bool valid[10]{};
    computePosteriors(touchX, touchY, sigma, posteriors, valid);

    outHypotheses[0] = { primaryDigit, posteriors[primaryDigit], false };
    if (maxHypotheses == 1) {
        return 1;
    }

    DigitHypothesis neighbors[8];
    int neighborCount = 0;

#ifdef OPENT9_EVAL_TUNING
    const float minNeighborPosterior = g_evalMinNeighborLog10Posterior;
#else
    const float minNeighborPosterior = MIN_NEIGHBOR_LOG10_POSTERIOR;
#endif
    for (int d = 2; d <= 9; ++d) {
        if (d == primaryDigit || !valid[d]) continue;
        if (posteriors[d] >= minNeighborPosterior) {
            neighbors[neighborCount++] = { d, posteriors[d], true };
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
