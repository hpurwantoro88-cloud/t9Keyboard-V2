#include "audio_engine.hpp"
#include <cmath>
#include <cstring>
#include <algorithm>

constexpr int SAMPLE_RATE = 48000;

AudioEngine::AudioEngine() {
    generateWaveforms();
}

AudioEngine::~AudioEngine() {
    stop();
}

void AudioEngine::generateWaveforms() {
    // 1. Mechanical Classic: 2.5kHz resonant burst with rapid exponential decay (~12ms)
    size_t mechLen = static_cast<size_t>(SAMPLE_RATE * 0.012f);
    mechanicalWaveform.resize(mechLen);
    for (size_t i = 0; i < mechLen; ++i) {
        float t = static_cast<float>(i) / SAMPLE_RATE;
        float env = std::exp(-t * 600.0f);
        float tone = 0.6f * std::sin(2.0f * M_PI * 2400.0f * t) +
                     0.4f * std::sin(2.0f * M_PI * 4800.0f * t);
        mechanicalWaveform[i] = tone * env;
    }

    // 2. Modern Soft: 800Hz gentle rounded thud (~10ms)
    size_t softLen = static_cast<size_t>(SAMPLE_RATE * 0.010f);
    modernWaveform.resize(softLen);
    for (size_t i = 0; i < softLen; ++i) {
        float t = static_cast<float>(i) / SAMPLE_RATE;
        float env = std::exp(-t * 500.0f);
        float tone = std::sin(2.0f * M_PI * 800.0f * t);
        modernWaveform[i] = tone * env;
    }

    // 3. Minimalist Click: Short sharp 4kHz click (~5ms)
    size_t minLen = static_cast<size_t>(SAMPLE_RATE * 0.005f);
    minimalistWaveform.resize(minLen);
    for (size_t i = 0; i < minLen; ++i) {
        float t = static_cast<float>(i) / SAMPLE_RATE;
        float env = std::exp(-t * 1200.0f);
        float tone = std::sin(2.0f * M_PI * 3800.0f * t);
        minimalistWaveform[i] = tone * env;
    }
}

bool AudioEngine::start() {
    if (stream) return true;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(oboe::ChannelCount::Mono);
    builder.setSampleRate(SAMPLE_RATE);
    builder.setDataCallback(this);

    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK) {
        stream.reset();
        return false;
    }

    result = stream->requestStart();
    if (result != oboe::Result::OK) {
        stream->close();
        stream.reset();
        return false;
    }

    return true;
}

void AudioEngine::stop() {
    if (stream) {
        stream->stop();
        stream->close();
        stream.reset();
    }
    isPlaying = false;
}

void AudioEngine::playClick(SoundStyle style, float volume) {
    if (!stream || stream->getState() != oboe::StreamState::Started) {
        start();
    }
    currentStyle.store(style, std::memory_order_relaxed);
    currentVolume.store(std::clamp(volume, 0.0f, 1.0f), std::memory_order_relaxed);
    playbackFrameIndex.store(0, std::memory_order_release);
    isPlaying.store(true, std::memory_order_release);
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *oboeStream,
    void *audioData,
    int32_t numFrames) {

    float *output = static_cast<float*>(audioData);
    if (!isPlaying.load(std::memory_order_acquire)) {
        std::memset(output, 0, sizeof(float) * numFrames);
        return oboe::DataCallbackResult::Continue;
    }

    const std::vector<float>* waveform = nullptr;
    switch (currentStyle.load(std::memory_order_relaxed)) {
        case SoundStyle::MODERN_SOFT:
            waveform = &modernWaveform;
            break;
        case SoundStyle::MINIMALIST_CLICK:
            waveform = &minimalistWaveform;
            break;
        case SoundStyle::MECHANICAL_CLASSIC:
        default:
            waveform = &mechanicalWaveform;
            break;
    }

    size_t curIdx = playbackFrameIndex.load(std::memory_order_acquire);
    float vol = currentVolume.load(std::memory_order_relaxed);
    size_t waveSize = waveform->size();

    for (int32_t i = 0; i < numFrames; ++i) {
        if (curIdx < waveSize) {
            output[i] = (*waveform)[curIdx++] * vol;
        } else {
            output[i] = 0.0f;
        }
    }

    playbackFrameIndex.store(curIdx, std::memory_order_release);
    if (curIdx >= waveSize) {
        isPlaying.store(false, std::memory_order_release);
    }

    return oboe::DataCallbackResult::Continue;
}
