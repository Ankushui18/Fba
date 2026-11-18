#include <cassert>
#include <cstdio>
#include <cstring>
#include <initializer_list>
#include "engine.h"
#include "libretro.h"

// Tiny deterministic libretro implementation to exercise the real frontend on a host.
// This file is never linked into the APK.
namespace {
retro_environment_t environment;
retro_video_refresh_t video;
retro_audio_sample_t sample;
retro_audio_sample_batch_t batch;
retro_input_poll_t poll;
retro_input_state_t input;
retro_log_callback logger;
bool active = false, running = false, rgb565 = false;
}

unsigned retro_api_version() { return RETRO_API_VERSION; }
void retro_set_environment(retro_environment_t fn) { environment = fn; }
void retro_set_video_refresh(retro_video_refresh_t fn) { video = fn; }
void retro_set_audio_sample(retro_audio_sample_t fn) { sample = fn; }
void retro_set_audio_sample_batch(retro_audio_sample_batch_t fn) { batch = fn; }
void retro_set_input_poll(retro_input_poll_t fn) { poll = fn; }
void retro_set_input_state(retro_input_state_t fn) { input = fn; }
void retro_init() {
    assert(!active && environment(RETRO_ENVIRONMENT_GET_LOG_INTERFACE, &logger));
    assert(environment(RETRO_ENVIRONMENT_GET_INPUT_BITMASKS, nullptr));
    active = true;
}
void retro_deinit() { assert(active && !running); active = false; }
bool retro_load_game(const retro_game_info *game) {
    assert(active && game->path[0] == '/');
    const char *dir = nullptr;
    assert(environment(RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY, &dir) && strcmp(dir, "/save") == 0);
    assert(environment(RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY, &dir) && strcmp(dir, "/sys") == 0);
    running = true;
    if (strcmp(game->path, "/invalid.zip") == 0) {
        logger.log(RETRO_LOG_ERROR, "%s", "[FBNeo] ROM is required (CRC mismatch)\n");
        return true; // Mirrors the pinned core's in-core error screen.
    }
    rgb565 = strcmp(game->path, "/rgb565.zip") == 0;
    retro_pixel_format fmt = rgb565 ? RETRO_PIXEL_FORMAT_RGB565 : RETRO_PIXEL_FORMAT_XRGB8888;
    unsigned rot = 1;
    assert(environment(RETRO_ENVIRONMENT_SET_PIXEL_FORMAT, &fmt));
    assert(environment(RETRO_ENVIRONMENT_SET_ROTATION, &rot));
    logger.log(RETRO_LOG_INFO, "%s", "[FBNeo] Driver successfully started\n");
    return true;
}
void retro_get_system_av_info(retro_system_av_info *av) {
    *av = {{2, 1, 2, 1, 0.75f}, {59.9, 48000.0}};
}
void retro_unload_game() { assert(running && active); running = false; }
void retro_run() {
    poll();
    assert(input(0, RETRO_DEVICE_JOYPAD, 0, RETRO_DEVICE_ID_JOYPAD_A) == 1);
    assert(input(0, RETRO_DEVICE_JOYPAD, 0, RETRO_DEVICE_ID_JOYPAD_MASK) == (1 << RETRO_DEVICE_ID_JOYPAD_A));
    assert(input(1, RETRO_DEVICE_JOYPAD, 0, RETRO_DEVICE_ID_JOYPAD_MASK) == 0);
    const uint32_t pixels32[] = {0xff123456, 0xff996600};
    const uint16_t pixels16[] = {0xf800, 0x07e0};
    const int16_t audio[] = {30, 40, 50, 60};
    if (rgb565) video(pixels16, 2, 1, sizeof(pixels16));
    else video(pixels32, 2, 1, sizeof(pixels32));
    video(nullptr, 2, 1, 0); // Duplicate frames must leave existing pixels undisturbed.
    sample(10, 20);
    assert(batch(audio, 2) == 2);
}

int main() {
    assert(arcade::open("invalid.zip", "/sys", "/save"));
    const char *err = arcade::open("/invalid.zip", "/sys", "/save");
    assert(err && strstr(err, "missing ROM data") && !active && !running);
    assert(!arcade::step(0));
    for (bool rgb16 : {false, true}) {
        assert(arcade::open(rgb16 ? "/rgb565.zip" : "/rgb32.zip", "/sys", "/save") == nullptr);
        assert(arcade::open("/another.zip", "/sys", "/save"));
        assert(arcade::fps() == 59.9 && arcade::sampleRate() == 48000);
        assert(arcade::step(1 << RETRO_DEVICE_ID_JOYPAD_A));
        auto frame = arcade::video();
        assert(frame.width == 2 && frame.height == 1 && frame.rotation == 1 && frame.aspect == .75f);
        assert(frame.rgba[0] == (rgb16 ? 0xff0000ffu : 0xff563412u));
        assert(frame.rgba[1] == (rgb16 ? 0xff00ff00u : 0xff006699u));
        const auto pcm = arcade::audio();
        assert(pcm.samples == 6 && pcm.pcm[0] == 10 && pcm.pcm[5] == 60);
        arcade::close();
        assert(!arcade::step(0));
    }
    std::puts("Engine callback tests passed.");
}
