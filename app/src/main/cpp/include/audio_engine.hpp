#pragma once
#include <cstdint>
#include <memory>
#include <atomic>
#include <vector>
#include <oboe/Oboe.h>

enum class SoundStyle : uint32_t {
    MECHANICAL_CLASSIC = 0,
    MODERN_SOFT = 1,
    MINIMALIST_CLICK = 2
};

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    AudioEngine();
    ~AudioEngine();

    bool start();
    void stop();

    void playClick(SoundStyle style, float volume);

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) override;

private:
    std::shared_ptr<oboe::AudioStream> stream;
    std::atomic<bool> isPlaying{false};
    std::atomic<size_t> playbackFrameIndex{0};
    std::atomic<SoundStyle> currentStyle{SoundStyle::MECHANICAL_CLASSIC};
    std::atomic<float> currentVolume{0.6f};

    // Pre-synthesized waveform buffers (at 48000 Hz, ~15-20ms short clicks)
    std::vector<float> mechanicalWaveform;
    std::vector<float> modernWaveform;
    std::vector<float> minimalistWaveform;

    void generateWaveforms();
};
