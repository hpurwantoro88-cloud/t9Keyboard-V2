#include <jni.h>
#include <string>
#include <android/log.h>
#include "native_config.hpp"
#include "spatial_scoring.hpp"
#include "lexicon_manager.hpp"
#include "dawg_engine.hpp"
#include "multi_tap_engine.hpp"
#include "dynamic_store.hpp"
#include "audio_engine.hpp"

#define LOG_TAG "OpenT9Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

NativeConfig g_config;
static SpatialScorer g_scorer;
static LexiconManager g_lexiconManager;
static DawgEngine g_dawgEngine;
static MultiTapEngine g_multiTapEngine;
static DynamicStore g_dynamicStore;
static AudioEngine g_audioEngine;

extern "C" {

JNIEXPORT void JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeSyncConfig(
    JNIEnv* env, jobject thiz,
    jfloat sigma, jint longPressMs, jint multiTapMs,
    jboolean autoSpace, jboolean slangBoost, jint decayDays) {
    g_config.touch_variance_sigma = sigma;
    g_config.long_press_timeout_ms = longPressMs;
    g_config.multi_tap_timeout_ms = multiTapMs;
    g_config.auto_space_enabled = autoSpace ? 1 : 0;
    g_config.slang_boost_enabled = slangBoost ? 1 : 0;
    g_config.decay_half_life_days = decayDays;
}

JNIEXPORT void JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeSyncAudioConfig(
    JNIEnv* env, jobject thiz,
    jboolean enabled, jfloat volume, jint style) {
    g_config.audio_enabled = enabled ? 1 : 0;
    g_config.audio_volume = volume;
    g_config.audio_style = style;
}

JNIEXPORT jboolean JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeInit(
    JNIEnv* env, jobject thiz, jstring dbPath) {
    const char* path = env->GetStringUTFChars(dbPath, nullptr);
    bool ok = g_dynamicStore.init(path);
    LOGI("nativeInit: dbPath=%s, ok=%d", path ? path : "null", ok ? 1 : 0);
    env->ReleaseStringUTFChars(dbPath, path);
    g_dawgEngine.setDynamicStore(&g_dynamicStore);
    g_audioEngine.start();
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeLoadLexiconFd(
    JNIEnv* env, jobject thiz, jstring langCode, jint fd, jlong offset, jlong length) {
    const char* lang = env->GetStringUTFChars(langCode, nullptr);
    bool ok = g_lexiconManager.loadFromFd(lang, fd, static_cast<off_t>(offset), static_cast<size_t>(length));
    if (ok && std::strcmp(g_lexiconManager.getActiveLanguage(), lang) == 0) {
        g_dawgEngine.setLexiconData(g_lexiconManager.getActiveData(), g_lexiconManager.getActiveSize());
    }
    env->ReleaseStringUTFChars(langCode, lang);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeSwitchLanguage(
    JNIEnv* env, jobject thiz, jstring langCode) {
    const char* lang = env->GetStringUTFChars(langCode, nullptr);
    bool ok = g_lexiconManager.switchLanguage(lang);
    if (ok) {
        g_dawgEngine.setLexiconData(g_lexiconManager.getActiveData(), g_lexiconManager.getActiveSize());
    }
    env->ReleaseStringUTFChars(langCode, lang);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeGetActiveLanguage(
    JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(g_lexiconManager.getActiveLanguage());
}

JNIEXPORT void JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeUpdateKeyGeometry(
    JNIEnv* env, jobject thiz, jint digit, jfloat cx, jfloat cy) {
    g_scorer.updateKeyGeometry(digit, cx, cy);
}

JNIEXPORT jboolean JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativePushStroke(
    JNIEnv* env, jobject thiz, jint digit, jfloat touchX, jfloat touchY) {
    return g_dawgEngine.pushStroke(digit, touchX, touchY, g_scorer, g_config) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativePopStroke(
    JNIEnv* env, jobject thiz) {
    return g_dawgEngine.popStroke() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeResetT9(
    JNIEnv* env, jobject thiz) {
    g_dawgEngine.reset();
}

JNIEXPORT jint JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeGetCandidates(
    JNIEnv* env, jobject thiz, jobject directByteBuffer, jint maxBytes) {
    if (!directByteBuffer) return 0;
    uint8_t* buf = static_cast<uint8_t*>(env->GetDirectBufferAddress(directByteBuffer));
    if (!buf) return 0;
    return g_dawgEngine.serializeCandidates(buf, maxBytes);
}

JNIEXPORT jint JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeMultiTapKeyPress(
    JNIEnv* env, jobject thiz, jint digit, jlong timestampMs, jint shiftState, jobject directByteBuffer) {
    bool committedPrev = false;
    char committedChar = 0;
    char cycleChar = g_multiTapEngine.onKeyPress(
        digit, static_cast<uint64_t>(timestampMs),
        static_cast<ShiftStateEnum>(shiftState),
        &committedPrev, &committedChar);

    if (directByteBuffer) {
        uint8_t* buf = static_cast<uint8_t*>(env->GetDirectBufferAddress(directByteBuffer));
        if (buf) {
            buf[0] = committedPrev ? 1 : 0;
            buf[1] = static_cast<uint8_t>(committedChar);
            buf[2] = static_cast<uint8_t>(cycleChar);
        }
    }
    return static_cast<jint>(cycleChar);
}

JNIEXPORT jboolean JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeMultiTapTimeout(
    JNIEnv* env, jobject thiz, jlong timestampMs, jint timeoutMs, jobject directByteBuffer) {
    char committed = 0;
    bool timedOut = g_multiTapEngine.checkTimeout(
        static_cast<uint64_t>(timestampMs),
        static_cast<uint32_t>(timeoutMs),
        &committed);

    if (timedOut && directByteBuffer) {
        uint8_t* buf = static_cast<uint8_t*>(env->GetDirectBufferAddress(directByteBuffer));
        if (buf) {
            buf[0] = static_cast<uint8_t>(committed);
        }
    }
    return timedOut ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jchar JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeMultiTapCommit(
    JNIEnv* env, jobject thiz) {
    return static_cast<jchar>(g_multiTapEngine.commitActive());
}

JNIEXPORT void JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeMultiTapReset(
    JNIEnv* env, jobject thiz) {
    g_multiTapEngine.reset();
}

JNIEXPORT void JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativePlayClick(
    JNIEnv* env, jobject thiz, jint style, jfloat volume) {
    g_audioEngine.playClick(static_cast<SoundStyle>(style), volume);
}

JNIEXPORT void JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeRecordUsage(
    JNIEnv* env, jobject thiz, jstring word, jlong nowSec) {
    const char* str = env->GetStringUTFChars(word, nullptr);
    bool ok = g_dynamicStore.recordUsage(str, static_cast<uint64_t>(nowSec));
    LOGI("recordUsage: word=%s, nowSec=%llu, ok=%d", str ? str : "null", static_cast<unsigned long long>(nowSec), ok ? 1 : 0);
    env->ReleaseStringUTFChars(word, str);
}

JNIEXPORT jint JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeGetWordUsageCount(
    JNIEnv* env, jobject thiz, jstring word) {
    if (!word) return 0;
    const char* str = env->GetStringUTFChars(word, nullptr);
    uint32_t count = g_dynamicStore.getHitCount(str);
    env->ReleaseStringUTFChars(word, str);
    return static_cast<jint>(count);
}

JNIEXPORT jboolean JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeRemoveWord(
    JNIEnv* env, jobject thiz, jstring word) {
    const char* str = env->GetStringUTFChars(word, nullptr);
    bool ok = g_dynamicStore.removeWord(str);
    env->ReleaseStringUTFChars(word, str);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeResetUserDictionary(
    JNIEnv* env, jobject thiz) {
    g_dynamicStore.reset();
}

JNIEXPORT jboolean JNICALL
Java_com_opent9_keyboard_jni_NativeEngineBridge_nativeIsWordDeleted(
    JNIEnv* env, jobject thiz, jstring word) {
    if (!word) return JNI_FALSE;
    const char* str = env->GetStringUTFChars(word, nullptr);
    bool del = g_dynamicStore.isDeleted(str);
    env->ReleaseStringUTFChars(word, str);
    return del ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
