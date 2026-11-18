#include "engine.h"
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <algorithm>
#include <cmath>
#include "libretro.h"

// The Java frontend calls all core APIs on its one emulation thread.
namespace {
constexpr unsigned kMaxSide = 2048;
constexpr size_t kAudioSamples = 8192;
// Padded storage also accommodates FBNeo's fixed-width directory copies.
char romPath[256], systemDir[4096], saveDir[4096];
bool initialized, loaded, driverStarted, opening, failed, shutdownRequested;
char errorText[256];
unsigned width, height, rotation;
float displayAspect;
double coreFps, coreSampleRate;
retro_pixel_format pixelFormat;
uint16_t buttons;
uint32_t frame[kMaxSide * kMaxSide];
int16_t audio[kAudioSamples];
size_t audioSize;

void logCore(enum retro_log_level level, const char *format, ...) {
    if (!opening) return;
    char text[320];
    va_list args;
    va_start(args, format);
    const int len = vsnprintf(text, sizeof(text), format, args);
    va_end(args);
    if (len < 0) return;
    if (strcmp(text, "[FBNeo] Driver successfully started\n") == 0) driverStarted = true;
    // Don't log private paths or ROM information to logcat. Categorize expected errors only.
    if (level != RETRO_LOG_ERROR || errorText[0] != '\0') return;
    const char *message = nullptr;
    if (strstr(text, "CRC") || strstr(text, "None of those archives")) {
        message = "This ZIP is missing ROM data for the current FBNeo set. Keep the expected name and import any required parent or BIOS ZIP alongside it.";
    } else if (strstr(text, "marked as not working")) {
        message = "This set isn't marked playable by FBNeo.";
    }
    if (message) snprintf(errorText, sizeof(errorText), "%s", message);
}

void updateTiming(const retro_system_av_info &av) {
    displayAspect = av.geometry.aspect_ratio;
    coreFps = av.timing.fps;
    coreSampleRate = av.timing.sample_rate;
}

bool environment(unsigned cmd, void *data) {
    if (cmd == RETRO_ENVIRONMENT_SHUTDOWN) {
        shutdownRequested = true;
        return true;
    }
    if (cmd == RETRO_ENVIRONMENT_GET_INPUT_BITMASKS) return true;
    if (!data) return false;
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *static_cast<bool *>(data) = true;
            return true;
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *static_cast<const char **>(data) = systemDir;
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *static_cast<const char **>(data) = saveDir;
            return true;
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            static_cast<retro_log_callback *>(data)->log = logCore;
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE:
            static_cast<retro_variable *>(data)->value = nullptr;
            return false;   // Use the core's own defaults; no netplay, patches or external options.
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *static_cast<bool *>(data) = false;
            return true;
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            *static_cast<unsigned *>(data) = RETRO_LANGUAGE_ENGLISH;
            return true;
        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            *static_cast<int *>(data) = 3;
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const auto format = *static_cast<const retro_pixel_format *>(data);
            if (format != RETRO_PIXEL_FORMAT_XRGB8888 && format != RETRO_PIXEL_FORMAT_RGB565 &&
                format != RETRO_PIXEL_FORMAT_0RGB1555) return false;
            pixelFormat = format;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_ROTATION:
            rotation = *static_cast<const unsigned *>(data) & 3u;
            return true;
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
            displayAspect = static_cast<const retro_game_geometry *>(data)->aspect_ratio;
            return true;
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
            updateTiming(*static_cast<const retro_system_av_info *>(data));
            return true;
        default:
            return false;
    }
}

void videoRefresh(const void *data, unsigned w, unsigned h, size_t pitch) {
    if (!data) return;   // A duplicate frame; keep the last frame.
    const bool rgb32 = pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888;
    if (w == 0 || h == 0 || w > kMaxSide || h > kMaxSide || pitch < w * (rgb32 ? 4u : 2u)) {
        failed = true;
        return;
    }
    width = w;
    height = h;
    const auto *src = static_cast<const uint8_t *>(data);
    for (unsigned y = 0; y < h; y++, src += pitch) {
        uint32_t *dst = frame + y * w;
        if (rgb32) {
            for (unsigned x = 0; x < w; x++) {
                uint32_t p;
                memcpy(&p, src + 4u * x, sizeof(p));
                dst[x] = 0xff000000u | ((p & 0xffu) << 16) | (p & 0xff00u) | ((p >> 16) & 0xffu);
            }
        } else {
            const bool sixGreenBits = pixelFormat == RETRO_PIXEL_FORMAT_RGB565;
            for (unsigned x = 0; x < w; x++) {
                uint16_t p;
                memcpy(&p, src + 2u * x, sizeof(p));
                const unsigned r = (p >> (sixGreenBits ? 11u : 10u)) & 31u;
                const unsigned g = (p >> 5) & (sixGreenBits ? 63u : 31u);
                const unsigned b = p & 31u;
                const unsigned green = sixGreenBits ? (g << 2) | (g >> 4) : (g << 3) | (g >> 2);
                dst[x] = 0xff000000u | (((b << 3) | (b >> 2)) << 16) | (green << 8) | ((r << 3) | (r >> 2));
            }
        }
    }
}

size_t audioBatch(const int16_t *data, size_t frames) {
    const size_t copy = std::min(frames, (kAudioSamples - audioSize) / 2);
    if (data && copy != 0) {
        memcpy(audio + audioSize, data, copy * 2 * sizeof(*audio));
        audioSize += copy * 2;
    }
    return frames;   // Drop overflow instead of blocking core callbacks.
}

void audioSingle(int16_t left, int16_t right) {
    const int16_t stereo[] = {left, right};
    audioBatch(stereo, 1);
}

void inputPoll() {}

int16_t inputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0 || index != 0 || device != RETRO_DEVICE_JOYPAD) return 0;
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return static_cast<int16_t>(buttons);
    return id < 16 ? (buttons >> id) & 1u : 0;
}

template <size_t Size>
bool readPath(const char *path, char (&output)[Size]) {
    if (!path || path[0] != '/') return false;
    const size_t len = strlen(path);
    if (len >= Size || len >= 256) return false;
    memset(output, 0, Size);
    memcpy(output, path, len);
    return true;
}
}  // namespace

namespace arcade {
void close() {
    if (loaded) retro_unload_game();
    if (initialized) retro_deinit();
    initialized = loaded = false;
}

const char *open(const char *rom, const char *system, const char *save) {
    if (initialized) return "An arcade session is already open.";
    if (!readPath(rom, romPath) || !readPath(system, systemDir) || !readPath(save, saveDir)) {
        return "The local file path is invalid or too long.";
    }
    if (retro_api_version() != RETRO_API_VERSION) return "Incompatible FBNeo core.";
    width = height = rotation = buttons = 0;
    displayAspect = 0.f;
    driverStarted = failed = shutdownRequested = false;
    errorText[0] = '\0';
    pixelFormat = RETRO_PIXEL_FORMAT_0RGB1555;
    retro_set_environment(environment);
    retro_set_video_refresh(videoRefresh);
    retro_set_audio_sample(audioSingle);
    retro_set_audio_sample_batch(audioBatch);
    retro_set_input_poll(inputPoll);
    retro_set_input_state(inputState);
    retro_init();
    initialized = true;
    const retro_game_info game = {romPath, nullptr, 0, nullptr};
    opening = true;
    loaded = retro_load_game(&game);
    opening = false;
    // This pinned core returns true for its own in-core error screen too. Only its
    // driver-start message identifies a playable session; expose failures in the app UI.
    if (!loaded || !driverStarted) {
        close();
        return errorText[0] ? errorText :
            "FBNeo couldn't start this set. Use a supported arcade ZIP with its original set name, parent and BIOS files if required.";
    }
    retro_system_av_info info = {};
    retro_get_system_av_info(&info);
    updateTiming(info);
    return nullptr;
}

bool step(uint16_t input) {
    if (!loaded) return false;
    buttons = input;
    audioSize = 0;
    retro_run();
    return !shutdownRequested && !failed;
}

Video video() { return {frame, width, height, rotation, displayAspect}; }
Audio audio() { return {::audio, audioSize}; }
double fps() { return coreFps; }
int sampleRate() {
    return std::isfinite(coreSampleRate) && coreSampleRate >= 8000 && coreSampleRate <= 192000
        ? static_cast<int>(std::lround(coreSampleRate)) : 0;
}
}  // namespace arcade
