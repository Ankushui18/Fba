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
        arcade::close();
        if (mode == "local-battery") {
            std::ifstream battery(save + "/fbneo/nes_fba_local.fs", std::ios::binary);
            assert(battery && battery.get() == 0x5a); // Actual write from the cartridge test program.
        }
    }
    assert(passed >= 24);
}
