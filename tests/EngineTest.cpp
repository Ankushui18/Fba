#include <cassert>
#include <cstdio>
#include <cstring>
#include <initializer_list>
#include <vector>
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
bool variableUpdate = false, typing = false;
uint32_t ticks;
constexpr char cheatKey[] = "fbneo-cheat-probe";
constexpr char hiddenKey[] = "fbneo-dipswitch-secret";
constexpr char ignoredKey[] = "fbneo-unrelated";
constexpr char disabled[] = "0 - Disabled";
constexpr char enabled[] = "1 - Enabled";

void helpOptions(bool reverse) {
    char translated[] = "Test memory write";
    struct retro_core_option_v2_definition definitions[4] = {};
    definitions[0].key = cheatKey;
    definitions[0].desc_categorized = translated;
    definitions[0].info = "Test core metadata";
    definitions[0].category_key = "cheat";
    definitions[0].values[reverse ? 1 : 0] = {disabled, "Default"};
    definitions[0].values[reverse ? 0 : 1] = {enabled, "Active"};
    definitions[0].default_value = disabled;
    definitions[1] = definitions[0];
    definitions[1].key = hiddenKey;
    definitions[1].category_key = "dipswitch";
    definitions[2] = definitions[0];
    definitions[2].key = ignoredKey;
    definitions[2].category_key = "system";
    struct retro_core_options_v2 options = {nullptr, definitions};
    assert(environment(RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2, &options));
    translated[0] = 'T' + 1;
}
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
    unsigned version = 0;
    assert(environment(RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION, &version) && version == 2);
    ticks = 0;
    variableUpdate = false;
    helpOptions(false);
    struct retro_core_option_display hidden = {hiddenKey, false};
    assert(environment(RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY, &hidden));
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
    assert(input(1, RETRO_DEVICE_JOYPAD, 0, RETRO_DEVICE_ID_JOYPAD_MASK) == (1 << RETRO_DEVICE_ID_JOYPAD_SELECT));
    assert(input(2, RETRO_DEVICE_JOYPAD, 0, RETRO_DEVICE_ID_JOYPAD_MASK) == 0);
    assert(input(3, RETRO_DEVICE_JOYPAD, 0, RETRO_DEVICE_ID_JOYPAD_START) == 1);
    assert(input(4, RETRO_DEVICE_JOYPAD, 0, RETRO_DEVICE_ID_JOYPAD_MASK) == 0);
    assert(input(2, RETRO_DEVICE_KEYBOARD, 0, RETROK_RETURN) == typing);
    assert(input(0, RETRO_DEVICE_KEYBOARD, 0, RETROK_LSHIFT) == typing);
    assert(input(2, RETRO_DEVICE_KEYBOARD, 0, RETROK_a) == 0);
    assert(input(2, RETRO_DEVICE_KEYBOARD, 0, RETROK_LAST) == 0);
    ++ticks;
    assert(environment(RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE, &variableUpdate));
    const uint32_t pixels32[] = {0xff123456, 0xff996600};
    const uint16_t pixels16[] = {0xf800, 0x07e0};
    const int16_t audio[] = {30, 40, 50, 60};
    if (rgb565) video(pixels16, 2, 1, sizeof(pixels16));
    else video(pixels32, 2, 1, sizeof(pixels32));
    video(nullptr, 2, 1, 0); // Duplicate frames must leave existing pixels undisturbed.
    sample(10, 20);
    assert(batch(audio, 2) == 2);
}

void retro_reset() {
    assert(running);
    ticks = 0;
    retro_variable off = {cheatKey, disabled};
    assert(environment(RETRO_ENVIRONMENT_SET_VARIABLE, &off));
}
size_t retro_serialize_size() { return sizeof(ticks); }
bool retro_serialize(void *data, size_t size) {
    assert(size == sizeof(ticks));
    memcpy(data, &ticks, size);
    return true;
}
bool retro_unserialize(const void *data, size_t size) {
    assert(size == sizeof(ticks));
    memcpy(&ticks, data, size);
    return true;
}

int main() {
    assert(arcade::open("invalid.zip", "/sys", "/save"));
    const char *err = arcade::open("/invalid.zip", "/sys", "/save");
    assert(err && strstr(err, "missing ROM data") && !active && !running);
    assert(!arcade::step(0) && !arcade::reset() && arcade::stateSize() == 0);
    const uint64_t buttons = (1ULL << RETRO_DEVICE_ID_JOYPAD_A) |
        (1ULL << (16 + RETRO_DEVICE_ID_JOYPAD_SELECT)) | (1ULL << (48 + RETRO_DEVICE_ID_JOYPAD_START));
    for (bool rgb16 : {false, true}) {
        assert(arcade::open(rgb16 ? "/rgb565.zip" : "/rgb32.zip", "/sys", "/save") == nullptr);
        assert(arcade::open("/another.zip", "/sys", "/save"));
        assert(arcade::fps() == 59.9 && arcade::sampleRate() == 48000);
        assert(arcade::options().size() == 2);
        const auto &first = arcade::options()[0];
        assert(first.visible && first.title == "Test memory write" && first.choices[0].label == "Default");
        assert(!arcade::setOption(hiddenKey, 1) && !arcade::setOption(ignoredKey, 0));
        assert(!arcade::setOption(cheatKey, -1) && !arcade::setOption(cheatKey, 2));
        assert(arcade::setOption(cheatKey, 1));
        helpOptions(true); // Relabel/reorder: preserve the selected option by value.
        retro_variable chosen = {cheatKey, nullptr};
        assert(environment(RETRO_ENVIRONMENT_GET_VARIABLE, &chosen) && strcmp(chosen.value, enabled) == 0);
        assert(arcade::options()[0].selected == 0 && arcade::options()[0].defaultChoice == 1);
        assert(!arcade::options()[1].visible);
        assert(!input(2, RETRO_DEVICE_KEYBOARD, 0, RETROK_RETURN));
        const int keys[] = {RETROK_RETURN, RETROK_LSHIFT, RETROK_LAST, -1};
        arcade::keyboard(keys, 4);
        typing = true;
        assert(arcade::step(buttons) && variableUpdate);
        typing = false;
        arcade::keyboard(nullptr, 0);
        auto frame = arcade::video();
        assert(frame.width == 2 && frame.height == 1 && frame.rotation == 1 && frame.aspect == .75f);
        assert(frame.rgba[0] == (rgb16 ? 0xff0000ffu : 0xff563412u));
        assert(frame.rgba[1] == (rgb16 ? 0xff00ff00u : 0xff006699u));
        const auto pcm = arcade::audio();
        assert(pcm.samples == 6 && pcm.pcm[0] == 10 && pcm.pcm[5] == 60);
        std::vector<uint8_t> snapshot(arcade::stateSize());
        assert(arcade::saveState(snapshot.data(), snapshot.size()) && ticks == 1);
        assert(arcade::step(buttons) && !variableUpdate && ticks == 2);
        assert(!arcade::loadState(snapshot.data(), snapshot.size() + 1));
        assert(arcade::loadState(snapshot.data(), snapshot.size()) && ticks == 1);
        assert(arcade::reset() && ticks == 0 && arcade::options()[0].selected == 1);
        assert(arcade::step(buttons) && variableUpdate);
        arcade::close();
        assert(!arcade::step(0) && arcade::options().empty());
    }
    assert(arcade::open("/link.zip", "/sys", "/save", false, true) == nullptr);
    const int key = RETROK_RETURN;
    arcade::keyboard(&key, 1);
    assert(arcade::step(buttons)); // Local Link never forwards computer keyboard data.
    arcade::close();
    std::puts("Engine video/audio, options, joypad and keyboard input, reset and state tests passed.");
}
