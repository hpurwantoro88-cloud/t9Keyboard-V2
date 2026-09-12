#pragma once
#include <cstdint>

struct NativeConfig {
    float touch_variance_sigma = 42.0f;     // Default: 42.0f
    uint32_t long_press_timeout_ms = 350;   // Default: 350
    uint32_t multi_tap_timeout_ms = 600;    // Default: 600
    uint8_t auto_space_enabled = 1;         // 0 or 1
    uint8_t slang_boost_enabled = 1;        // 0 or 1
    uint32_t decay_half_life_days = 30;     // Default: 30
    uint8_t audio_enabled = 1;
    float audio_volume = 0.6f;
    uint32_t audio_style = 0;               // 0=Classic, 1=Soft, 2=Click
};

extern NativeConfig g_config;
