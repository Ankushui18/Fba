#include <algorithm>
#include <array>
#include <cassert>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <unordered_set>
#include <vector>
#include "engine.h"
#include "libretro.h"

static void probeGbaProfile(const std::string &rom, const std::string &system, const std::string &save,
                            double normalFps, int normalRate) {
    constexpr char tuned[] = "fbneo-force-60hz=enabled\nfbneo-samplerate=44100\n";
    assert(normalFps > 59 && normalFps < 60 && normalRate > 47000);
    const char *error = arcade::open(rom.c_str(), system.c_str(), save.c_str(), false, false, tuned);
    if (error) std::fprintf(stderr, "GBA core profile: %s\n", error);
    assert(!error);
    assert(arcade::fps() == 60 && arcade::sampleRate() == 44100);
    for (int i = 0; i < 12; i++) assert(arcade::step(0));
    assert(arcade::video().width == 240 && arcade::audio().samples == 1470);
    std::vector<uint8_t> state(arcade::stateSize());
    assert(!state.empty() && arcade::saveState(state.data(), state.size()) && arcade::loadState(state.data(), state.size()));
    arcade::close();
    assert(!arcade::open(rom.c_str(), system.c_str(), save.c_str()));
    assert(arcade::fps() == normalFps && arcade::sampleRate() == normalRate && arcade::step(0));
    arcade::close();
    std::printf("GBA boot options: custom 60.00 Hz / 44100 Hz PCM; defaults %.2f Hz / %d Hz restored on reopen.\n", normalFps, normalRate);
}

static uint32_t genesisCounter(unsigned direction) {
    constexpr auto up = 1u << RETRO_DEVICE_ID_JOYPAD_UP, down = 1u << RETRO_DEVICE_ID_JOYPAD_DOWN;
    assert(arcade::step(up));
    for (int i = 0; i < 10; i++) assert(arcade::step(up | down));
    std::vector<uint8_t> state(arcade::stateSize());
    assert(arcade::saveState(state.data(), state.size()));
    // The marker in the synthetic cartridge's work RAM, scanned as 16-bit words.
    constexpr std::array<uint8_t, 4> little{0xde, 0xc0, 0x0d, 0xf0}, big{0xc0, 0xde, 0xf0, 0x0d};
    auto marker = std::search(state.begin(), state.end(), little.begin(), little.end());
    unsigned swap = marker != state.end();
    if (!swap) marker = std::search(state.begin(), state.end(), big.begin(), big.end());
    assert(state.end() - marker >= 10 && (marker[8 ^ swap] & 3) == direction);
    uint32_t loops = 0;
    for (unsigned byte = 4; byte < 8; byte++) loops = loops * 256 + marker[byte ^ swap];
    return loops;
}

static void probeGenesisProfile(const std::string &rom, const std::string &system, const std::string &save) {
    assert(!arcade::open(rom.c_str(), system.c_str(), save.c_str()));
    const auto normal = genesisCounter(1); // Down wins after holding Up, then pressing Down.
    arcade::close();
    constexpr char tuned[] = "fbneo-cpu-speed-adjust=200%\nfbneo-socd=1\n";
    const char *error = arcade::open(rom.c_str(), system.c_str(), save.c_str(), false, false, tuned);
    if (error) std::fprintf(stderr, "Genesis core profile: %s\n", error);
    assert(!error);
    const auto faster = genesisCounter(3); // Both directions are neutral.
    assert(normal > 100 && faster > normal + normal / 20);
    arcade::close();
    assert(!arcade::open(rom.c_str(), system.c_str(), save.c_str()));
    assert(genesisCounter(1) == normal);
    arcade::close();
    std::printf("Genesis boot options: CPU work counter %u → %u, opposite-direction priority → neutral; restored defaults.\n", normal, faster);
}

int main(int argc, char **argv) {
    assert(argc == 2);
    std::ifstream launches(argv[1]);
    assert(launches.is_open());
    unsigned passed = 0;
    for (std::string line; std::getline(launches, line);) {
        std::istringstream row(line);
        std::string title, mode, rom, system, save;
        assert(std::getline(row, title, '\t') && std::getline(row, mode, '\t') && std::getline(row, rom, '\t')
            && std::getline(row, system, '\t') && std::getline(row, save, '\t'));
        const bool ninja = mode == "ninja", loose = mode.rfind("local", 0) == 0;
        if (ninja) {
            const char *strict = arcade::open(rom.c_str(), system.c_str(), save.c_str());
            assert(strict && strstr(strict, "missing ROM data"));
        }
        const char *error = arcade::open(rom.c_str(), system.c_str(), save.c_str(), ninja);
        if (mode == "missing" || mode == "bad-crc") {
            assert(error && strstr(error, "missing ROM data"));
            std::printf("%s: rejected (%s)\n", title.c_str(), mode == "missing" ? "no BIOS" : "wrong CRC");
            continue;
        }
        if (error) { std::fprintf(stderr, "%s: %s\n", title.c_str(), error); return 1; }
        std::unordered_set<uint64_t> frames;
        size_t audio = 0, nonzero = 0;
        uint32_t beforeStart = 0;
        for (int i = 0; i < (ninja ? 920 : 60); ++i) {
            uint64_t pad = 0;
            if ((loose && i >= 25 && i < 28) || (i >= 130 && i < 133) || (i >= 300 && i < 303)) pad |= 1u << RETRO_DEVICE_ID_JOYPAD_START;
            if (i >= 670 && i < 745) pad |= 1u << RETRO_DEVICE_ID_JOYPAD_RIGHT;
            if (i >= 700 && i < 708) pad |= 1u << RETRO_DEVICE_ID_JOYPAD_A;
            assert(arcade::step(pad));
            if (loose && i == 24) beforeStart = arcade::video().rgba[0];
            if (ninja) frames.insert(arcade::linkCheckpoint());
            const auto pcm = arcade::audio();
            audio += pcm.samples;
            for (size_t j = 0; j < pcm.samples; ++j) nonzero += pcm.pcm[j] != 0;
        }
        const auto frame = arcade::video();
        const auto pad = arcade::inputMask(0);
        assert(frame.width >= 128 && frame.height >= 128 && arcade::fps() >= 49 && arcade::fps() <= 62 && audio > 0);
        assert((pad & (1u << RETRO_DEVICE_ID_JOYPAD_RIGHT)) != 0);
        if (ninja) assert(frames.size() > 30 && nonzero > 0);
        if (loose) {
            assert(frame.width == 256 && frame.height == 224 && frame.rgba[0] != beforeStart && nonzero > 0);
            assert(arcade::fps() == (mode == "local-pal" ? 50.0 : 60.0));
            assert((pad & (1u << RETRO_DEVICE_ID_JOYPAD_START)) != 0);
        }
        std::vector<uint8_t> state(arcade::stateSize());
        assert(!state.empty() && arcade::saveState(state.data(), state.size()) && arcade::loadState(state.data(), state.size()));
        std::printf("%s: %ux%u at %.2f Hz, pad 0x%x, %zu audio samples, %zu-byte state round trip\n",
                    title.c_str(), frame.width, frame.height, arcade::fps(), pad, audio, state.size());
        ++passed;
        if (loose) assert(arcade::reset() && arcade::step(0));
        const double normalFps = arcade::fps();
        const int normalRate = arcade::sampleRate();
        arcade::close();
        if (title == "gba") probeGbaProfile(rom, system, save, normalFps, normalRate);
        if (title == "genesis") probeGenesisProfile(rom, system, save);
        if (mode == "local-battery") {
            std::ifstream battery(save + "/fbneo/nes_fba_local.fs", std::ios::binary);
            assert(battery && battery.get() == 0x5a); // Actual write from the cartridge test program.
        }
    }
    assert(passed >= 24);
}
