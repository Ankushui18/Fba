#include <android/bitmap.h>
#include <jni.h>
#include <string.h>
#include <algorithm>
#include "engine.h"

namespace {
class UtfChars {
    JNIEnv *env;
    jstring string;
public:
    const char *const value;
    UtfChars(JNIEnv *e, jstring s) : env(e), string(s), value(s ? e->GetStringUTFChars(s, nullptr) : nullptr) {}
    ~UtfChars() { if (value) env->ReleaseStringUTFChars(string, value); }
    UtfChars(const UtfChars &) = delete;
    UtfChars &operator=(const UtfChars &) = delete;
};
}  // namespace

#define ENTRY(fn) Java_io_github_ankushui18_fbaoffline_Core_##fn

extern "C" JNIEXPORT jstring JNICALL ENTRY(open)(JNIEnv *env, jclass, jstring rom, jstring system, jstring save) {
    const UtfChars romPath(env, rom);
    if (env->ExceptionCheck()) return nullptr;
    const UtfChars systemPath(env, system);
    if (env->ExceptionCheck()) return nullptr;
    const UtfChars savePath(env, save);
    if (env->ExceptionCheck()) return nullptr;
    const char *error = arcade::open(romPath.value, systemPath.value, savePath.value);
    return error ? env->NewStringUTF(error) : nullptr;
}

extern "C" JNIEXPORT void JNICALL ENTRY(close)(JNIEnv *, jclass) { arcade::close(); }

extern "C" JNIEXPORT jint JNICALL ENTRY(step)(JNIEnv *env, jclass, jint input, jshortArray output) {
    if (!arcade::step(static_cast<uint16_t>(input))) return -1;
    const auto audio = arcade::audio();
    const jsize len = std::min(static_cast<jsize>(audio.samples), env->GetArrayLength(output));
    if (len > 0) env->SetShortArrayRegion(output, 0, len, audio.pcm);
    return len;
}

extern "C" JNIEXPORT jint JNICALL ENTRY(frameSize)(JNIEnv *, jclass) {
    const auto video = arcade::video();
    return (video.width << 16) | video.height;
}
extern "C" JNIEXPORT jint JNICALL ENTRY(rotation)(JNIEnv *, jclass) { return arcade::video().rotation; }
extern "C" JNIEXPORT jfloat JNICALL ENTRY(aspect)(JNIEnv *, jclass) { return arcade::video().aspect; }
extern "C" JNIEXPORT jdouble JNICALL ENTRY(fps)(JNIEnv *, jclass) { return arcade::fps(); }
extern "C" JNIEXPORT jint JNICALL ENTRY(sampleRate)(JNIEnv *, jclass) { return arcade::sampleRate(); }

extern "C" JNIEXPORT jboolean JNICALL ENTRY(copyFrame)(JNIEnv *env, jclass, jobject bitmap) {
    const auto video = arcade::video();
    const unsigned width = video.width, height = video.height;
    AndroidBitmapInfo info = {};
    if (!bitmap || AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || info.width != width || info.height != height ||
        !width || !height || info.stride < width * sizeof(uint32_t)) return JNI_FALSE;
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    auto *row = static_cast<uint8_t *>(pixels);
    for (unsigned y = 0; y < height; y++, row += info.stride) {
        memcpy(row, video.rgba + width * y, width * sizeof(uint32_t));
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}
