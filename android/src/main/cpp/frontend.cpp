#include <android/bitmap.h>
#include <jni.h>
#include <string.h>
#include <algorithm>
#include <new>
#include "engine.h"
#include "libretro.h"
#include "frame_filter.h"

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

bool rgbaInfo(JNIEnv *env, jobject bitmap, AndroidBitmapInfo &info) {
    return bitmap && AndroidBitmap_getInfo(env, bitmap, &info) == ANDROID_BITMAP_RESULT_SUCCESS
        && info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 && info.width && info.height
        && info.stride >= size_t(info.width) * sizeof(uint32_t);
}

class BitmapPixels {
    JNIEnv *env;
    jobject bitmap;
    bool locked;
public:
    void *data = nullptr;
    BitmapPixels(JNIEnv *env, jobject bitmap) : env(env), bitmap(bitmap) {
        locked = AndroidBitmap_lockPixels(env, bitmap, &data) == ANDROID_BITMAP_RESULT_SUCCESS;
    }
    ~BitmapPixels() { if (locked) AndroidBitmap_unlockPixels(env, bitmap); }
    explicit operator bool() const { return locked && data; }
    BitmapPixels(const BitmapPixels &) = delete;
    BitmapPixels &operator=(const BitmapPixels &) = delete;
};
}  // namespace

#define ENTRY(fn) Java_io_github_ankushui18_fbaoffline_Core_##fn

extern "C" JNIEXPORT jstring JNICALL ENTRY(open)(JNIEnv *env, jclass, jstring rom, jstring system, jstring save,
                                               jboolean patched, jboolean linked, jstring profile) {
    const UtfChars romPath(env, rom);
    if (env->ExceptionCheck()) return nullptr;
    const UtfChars systemPath(env, system);
    if (env->ExceptionCheck()) return nullptr;
    const UtfChars savePath(env, save);
    if (env->ExceptionCheck()) return nullptr;
    const UtfChars bootProfile(env, profile);
    if (env->ExceptionCheck()) return nullptr;
    const char *error = arcade::open(romPath.value, systemPath.value, savePath.value, patched, linked, bootProfile.value);
    return error ? env->NewStringUTF(error) : nullptr;
}

extern "C" JNIEXPORT void JNICALL ENTRY(close)(JNIEnv *, jclass) { arcade::close(); }

extern "C" JNIEXPORT jint JNICALL ENTRY(step)(JNIEnv *env, jclass, jlong input, jshortArray output) {
    if (!arcade::step(static_cast<uint64_t>(input))) return -1;
    const auto audio = arcade::audio();
    const jsize len = std::min(static_cast<jsize>(audio.samples), env->GetArrayLength(output));
    if (len > 0) env->SetShortArrayRegion(output, 0, len, audio.pcm);
    return len;
}

extern "C" JNIEXPORT void JNICALL ENTRY(keyboard)(JNIEnv *env, jclass, jintArray pressed) {
    jint keys[RETROK_LAST];
    const auto count = pressed ? std::min(env->GetArrayLength(pressed), static_cast<jsize>(sizeof(keys) / sizeof(keys[0]))) : 0;
    if (count) env->GetIntArrayRegion(pressed, 0, count, keys);
    if (!env->ExceptionCheck()) arcade::keyboard(keys, count);
}

extern "C" JNIEXPORT jint JNICALL ENTRY(frameSize)(JNIEnv *, jclass) {
    const auto video = arcade::video();
    return (video.width << 16) | video.height;
}
extern "C" JNIEXPORT jboolean JNICALL ENTRY(videoUpdated)(JNIEnv *, jclass) { return arcade::videoUpdated(); }
extern "C" JNIEXPORT jlong JNICALL ENTRY(linkCheckpoint)(JNIEnv *, jclass) { return static_cast<jlong>(arcade::linkCheckpoint()); }
extern "C" JNIEXPORT jint JNICALL ENTRY(inputMask)(JNIEnv *, jclass, jint port) { return arcade::inputMask(port); }
extern "C" JNIEXPORT jint JNICALL ENTRY(rotation)(JNIEnv *, jclass) { return arcade::video().rotation; }
extern "C" JNIEXPORT jfloat JNICALL ENTRY(aspect)(JNIEnv *, jclass) { return arcade::video().aspect; }
extern "C" JNIEXPORT jdouble JNICALL ENTRY(fps)(JNIEnv *, jclass) { return arcade::fps(); }
extern "C" JNIEXPORT jint JNICALL ENTRY(sampleRate)(JNIEnv *, jclass) { return arcade::sampleRate(); }

extern "C" JNIEXPORT jboolean JNICALL ENTRY(copyFrame)(JNIEnv *env, jclass, jobject bitmap) {
    const auto video = arcade::video();
    AndroidBitmapInfo info = {};
    if (!rgbaInfo(env, bitmap, info) || info.width != video.width || info.height != video.height) return JNI_FALSE;
    BitmapPixels pixels(env, bitmap);
    if (!pixels) return JNI_FALSE;
    const size_t rowBytes = video.width * sizeof(uint32_t);
    if (rowBytes == info.stride) memcpy(pixels.data, video.rgba, rowBytes * video.height);
    else {
        auto *dst = static_cast<uint8_t *>(pixels.data);
        for (unsigned y = 0; y < video.height; ++y)
            memcpy(dst + size_t(y) * info.stride, video.rgba + size_t(y) * video.width, rowBytes);
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL ENTRY(scaleFrame)(JNIEnv *env, jclass, jobject src, jobject dst, jint factor) {
    AndroidBitmapInfo input = {}, output = {};
    if (!rgbaInfo(env, src, input) || !arcade::hqxFits(input.width, input.height, factor)
        || !rgbaInfo(env, dst, output) || output.width != input.width * factor || output.height != input.height * factor) return JNI_FALSE;
    BitmapPixels from(env, src);
    if (!from) return JNI_FALSE;
    BitmapPixels into(env, dst);
    if (!into) return JNI_FALSE;
    try {
        return arcade::scaleHqx(static_cast<const uint32_t *>(from.data), input.width, input.height, input.stride,
                               factor, static_cast<uint32_t *>(into.data), output.stride);
    } catch (const std::bad_alloc &) { return JNI_FALSE; }
}

extern "C" JNIEXPORT jboolean JNICALL ENTRY(reset)(JNIEnv *, jclass) { return arcade::reset(); }

extern "C" JNIEXPORT jbyteArray JNICALL ENTRY(captureState)(JNIEnv *env, jclass) {
    const auto size = arcade::stateSize();
    if (!size) return nullptr;
    jbyteArray output = env->NewByteArray(static_cast<jsize>(size));
    if (!output) return nullptr;
    auto *data = env->GetByteArrayElements(output, nullptr);
    if (!data) return nullptr;
    const bool ok = arcade::saveState(data, size);
    env->ReleaseByteArrayElements(output, data, ok ? 0 : JNI_ABORT);
    return ok ? output : nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL ENTRY(restoreState)(JNIEnv *env, jclass, jbyteArray input) {
    if (!input) return false;
    const auto size = static_cast<size_t>(env->GetArrayLength(input));
    if (!size || size != arcade::stateSize()) return false;
    const auto *data = env->GetByteArrayElements(input, nullptr);
    if (!data) return false;
    const bool ok = arcade::loadState(data, size);
    env->ReleaseByteArrayElements(input, const_cast<jbyte *>(data), JNI_ABORT);
    return ok;
}

extern "C" JNIEXPORT jobjectArray JNICALL ENTRY(options)(JNIEnv *env, jclass, jboolean bootOnly) {
    const auto &options = arcade::options();
    jclass string = env->FindClass("java/lang/String");
    if (!string) return nullptr;
    jclass rowType = env->FindClass("[Ljava/lang/String;");
    if (!rowType) return nullptr;
    jmethodID constructor = env->GetMethodID(string, "<init>", "([BLjava/lang/String;)V");
    if (!constructor) return nullptr;
    jstring utf8 = env->NewStringUTF("UTF-8");
    if (!utf8) return nullptr;
    size_t visible = 0;
    for (const auto &option : options) if (option.visible && option.bootOnly == (bootOnly != 0)) visible++;
    auto out = env->NewObjectArray(static_cast<jsize>(visible), rowType, nullptr);
    if (!out) return nullptr;
    jsize index = 0;
    for (const auto &option : options) {
        if (!option.visible || option.bootOnly != (bootOnly != 0)) continue;
        auto row = env->NewObjectArray(static_cast<jsize>(6 + (bootOnly ? 2 : 1) * option.choices.size()), string, nullptr);
        if (!row) return nullptr;
        // Core metadata is UTF-8, not JNI Modified UTF-8.
        auto field = [&](jsize column, const std::string &value) {
            if (env->ExceptionCheck()) return;
            jbyteArray bytes = env->NewByteArray(static_cast<jsize>(value.size()));
            if (!bytes) return;
            env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(value.size()), reinterpret_cast<const jbyte *>(value.data()));
            if (!env->ExceptionCheck()) {
                jobject text = env->NewObject(string, constructor, bytes, utf8);
                if (text) env->SetObjectArrayElement(row, column, text);
                env->DeleteLocalRef(text);
            }
            env->DeleteLocalRef(bytes);
        };
        // Keys are ASCII or UTF-8 (legacy translations). Java returns the original key for selection.
        field(0, option.key);
        field(1, option.category);
        field(2, option.title);
        field(3, option.info);
        field(4, std::to_string(option.selected));
        field(5, std::to_string(option.defaultChoice));
        for (size_t j = 0; j < option.choices.size(); j++) {
            field(static_cast<jsize>(6 + j), option.choices[j].label);
            if (bootOnly) field(static_cast<jsize>(6 + option.choices.size() + j), option.choices[j].value);
        }
        if (env->ExceptionCheck()) return nullptr;
        env->SetObjectArrayElement(out, index++, row);
        env->DeleteLocalRef(row);
    }
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL ENTRY(setOption)(JNIEnv *env, jclass, jstring key, jint choice) {
    if (!key) return false;
    // Use standard UTF-8 to match the core even for supplementary characters.
    jclass string = env->GetObjectClass(key);
    if (!string) return false;
    jmethodID getBytes = env->GetMethodID(string, "getBytes", "(Ljava/lang/String;)[B");
    if (!getBytes) return false;
    jstring utf8 = env->NewStringUTF("UTF-8");
    if (!utf8) return false;
    auto bytes = static_cast<jbyteArray>(env->CallObjectMethod(key, getBytes, utf8));
    if (!bytes || env->ExceptionCheck() || env->GetArrayLength(bytes) > 512) return false;
    char name[513] = {};
    env->GetByteArrayRegion(bytes, 0, env->GetArrayLength(bytes), reinterpret_cast<jbyte *>(name));
    return !env->ExceptionCheck() && arcade::setOption(name, choice);
}
