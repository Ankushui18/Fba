#include "engine.h"
#include "libretro.h"
#include <algorithm>
#include <cassert>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

static bool playFrames(unsigned frames, uint64_t buttons = 0) {
    bool audible = false;
    for (unsigned i = 0; i < frames; i++) {
        assert(arcade::step(buttons));
        const auto pcm = arcade::audio();
        audible |= std::any_of(pcm.pcm, pcm.pcm + pcm.samples, [](int16_t n) { return n != 0; });
    }
    return audible;
}

static arcade::Option option(const char *category, const char *title) {
    for (const auto &opt : arcade::options()) {
        if (opt.visible && opt.category == category && opt.title.find(title) != std::string::npos) return opt;
    }
    std::fprintf(stderr, "No option %s/%s\n", category, title);
    assert(false);
    return {};
}

static int value(const arcade::Option &opt, const char *name) {
    for (size_t i = 0; i < opt.choices.size(); i++) if (opt.choices[i].label == name) return static_cast<int>(i);
    std::fprintf(stderr, "No value %s for %s\n", name, opt.title.c_str());
    assert(false);
    return -1;
}

static unsigned red(unsigned x) { return arcade::video().rgba[x] & 255; }

static void probeBoard(const char *rom, const char *privateDir) {
    // A synthetic program on the real Gridlee driver exercises end-to-end inputs and core options.
    if (const char *err = arcade::open(rom, privateDir, privateDir)) {
        std::fprintf(stderr, "%s\n", err);
        assert(false);
    }
    assert(playFrames(6));
    auto frame = arcade::video();
    assert(frame.width == 256 && frame.height == 240);
    const unsigned original = red(0), buttons = red(3), dip = red(4);
    assert(original > 0);
    assert(arcade::inputMask(0) != 0 && arcade::inputMask(1) != 0);
    assert(arcade::sampleRate() > 0);
    auto cheat = option("cheat", "Probe Color");
    auto freePlay = option("dipswitch", "Free Play");
    assert(cheat.choices.size() == 2 && cheat.selected == cheat.defaultChoice);

    const uint64_t fire = (1ULL << RETRO_DEVICE_ID_JOYPAD_A) | (1ULL << RETRO_DEVICE_ID_JOYPAD_B);
    playFrames(3, fire << 16);
    assert(red(3) != buttons);  // P2 changes the board's input byte.
    playFrames(3);
    assert(red(3) == buttons);

    std::vector<uint8_t> state(arcade::stateSize());
    assert(!state.empty() && arcade::saveState(state.data(), state.size()));
    assert(!arcade::loadState(state.data(), state.size() - 1));
    assert(arcade::setOption(cheat.key.c_str(), 1));
    playFrames(5);
    assert(red(0) != original);  // The INI actually wrote to video RAM.
    assert(arcade::setOption(freePlay.key.c_str(), value(freePlay, "On")));
    playFrames(5);
    assert(red(4) != dip);
    assert(arcade::setOption(cheat.key.c_str(), cheat.defaultChoice));
    playFrames(2);
    assert(arcade::loadState(state.data(), state.size()));
    playFrames(3);
    assert(red(0) == original);  // Save was captured before the INI wrote to RAM.
    assert(arcade::reset());
    playFrames(4);
    assert(red(0) == original);
    // Restart clears active cheats, not the list; choices can be reapplied.
    cheat = option("cheat", "Probe Color");
    assert(cheat.selected == cheat.defaultChoice && arcade::setOption(cheat.key.c_str(), 1));
    playFrames(5);
    assert(red(0) != original);
    arcade::close();
    assert(!arcade::open(rom, privateDir, privateDir, true));
    playFrames(6);
    assert(red(0) != original);  // Staged IPS override changes the running board program.
    arcade::close();
    assert(!arcade::open(rom, privateDir, privateDir));
    playFrames(6);
    assert(red(0) == original);  // Override is isolated even if present on disk.
    arcade::close();
    std::puts("FBNeo IPS variant changed running video and left the plain launch untouched.");
    std::puts("FBNeo hardware probe: 256x240 video, audible PCM, P2 input, DIP update, cheat memory write, save/load and reset passed.");
}

int main(int argc, char **argv) {
    // args: private dir, unknown set ZIP, incomplete 1942 ZIP, synthetic Gridlee ZIP, optional local ROM
    if (argc != 5 && argc != 6) return 2;
    for (int i = 0; i < 2; i++) {
        assert(arcade::open(argv[2], argv[1], argv[1]) != nullptr);
        assert(!arcade::step(0));
        const char *err = arcade::open(argv[3], argv[1], argv[1]);
        assert(err && strstr(err, "missing ROM data"));
        assert(!arcade::step(0));
    }
    probeBoard(argv[4], argv[1]);
    if (argc == 6) {
        if (const char *err = arcade::open(argv[5], argv[1], argv[1])) {
            std::fprintf(stderr, "%s\n", err);
            return 1;
        }
        size_t produced = 0;
        for (unsigned i = 0; i < 120; i++) {
            assert(arcade::step(0));
            produced += arcade::audio().samples;
        }
        auto video = arcade::video();
        assert(video.width > 0 && video.height > 0 && produced > 0);
        std::printf("Optional ROM emitted %ux%u video and %zu PCM samples.\n", video.width, video.height, produced);
        arcade::close();
    }
    std::puts("Real-core load and failure paths passed.");
}
