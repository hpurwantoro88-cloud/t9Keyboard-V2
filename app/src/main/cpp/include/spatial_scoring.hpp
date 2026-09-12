#pragma once
#include <cmath>
#include <cstdint>

struct KeyCoord {
    int digit;
    float cx;
    float cy;
};

class SpatialScorer {
public:
    SpatialScorer() = default;

    void updateKeyGeometry(int digit, float cx, float cy);
    float calculateLogLikelihood(int digit, float touchX, float touchY, float sigma) const;

private:
    KeyCoord keyCoords[10] = {
        {0, 0.0f, 0.0f}, {1, 0.0f, 0.0f}, {2, 0.0f, 0.0f},
        {3, 0.0f, 0.0f}, {4, 0.0f, 0.0f}, {5, 0.0f, 0.0f},
        {6, 0.0f, 0.0f}, {7, 0.0f, 0.0f}, {8, 0.0f, 0.0f},
        {9, 0.0f, 0.0f}
    };
};
