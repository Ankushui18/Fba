#include <cassert>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>
#include "engine.h"
#include "libretro.h"

namespace {
constexpr char enabled[] = "fbneo-hiscores=enabled\n";
constexpr unsigned scoreOffset = 0x801; // $e801 in 1942's synthetic Z80 #1 RAM.
constexpr unsigned scoreBytes = 0x190 + 1 + 8 + 1;

void open(const char *rom, const char *system, const char *save, const char *profile = nullptr, bool linked = false) {
    const char *error = arcade::open(rom, system, save, false, linked, profile);
    if (error) std::fprintf(stderr, "%s\n", error);
    assert(!error);
}

uint8_t score() {
    for (int n = 0; n < 12; n++) assert(arcade::step(0));
    const auto *ram = static_cast<const uint8_t *>(retro_get_memory_data(RETRO_MEMORY_SYSTEM_RAM));
    assert(ram && retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM) > scoreOffset);
    return ram[scoreOffset];
}

std::vector<uint8_t> readFile(const std::string &path) {
    std::ifstream in(path, std::ios::binary);
    assert(in);
    return {std::istreambuf_iterator<char>(in), std::istreambuf_iterator<char>()};
}

void writeFile(const std::string &path, const std::vector<uint8_t> &data) {
    std::ofstream out(path, std::ios::binary);
    out.write(reinterpret_cast<const char *>(data.data()), data.size());
    assert(out);
}
}

int main(int argc, char **argv) {
    assert(argc == 5); // Synthetic arcade set, pinned data dir, save dir, existing empty system dir.
    const std::string file = std::string(argv[3]) + "/fbneo/1942.hi";
    open(argv[1], argv[2], argv[3]);
    assert(score() == 0x2a);
    arcade::close();
    assert(!std::ifstream(file)); // Disabled by default even if DAT is installed.
    open(argv[1], argv[4], argv[3], enabled);
    assert(score() == 0x2a);
    arcade::close();
    assert(!std::ifstream(file)); // Missing DAT: native core does not write.
    open(argv[1], argv[2], argv[3], enabled);
    assert(score() == 0x2a);
    arcade::close();
    auto data = readFile(file);
    assert(data.size() == scoreBytes && data[1] == 0x2a);
    data[1] = 0x5a;
    writeFile(file, data);
    open(argv[1], argv[2], argv[3], enabled);
    assert(score() == 0x5a && arcade::reset() && score() == 0x5a);
    auto state = std::vector<uint8_t>(arcade::stateSize());
    assert(arcade::saveState(state.data(), state.size()) && arcade::loadState(state.data(), state.size()));
    arcade::close();
    assert(readFile(file) == data);
    open(argv[1], argv[2], argv[3], enabled, true);
    assert(score() == 0x2a); // Link ignores the option even with accessible score data.
    arcade::close();
    assert(readFile(file) == data);
    // Neither truncated data nor an oversized table may be applied to RAM.
    for (const auto size : {2u, scoreBytes + 1}) {
        auto invalid = data;
        invalid.resize(size);
        writeFile(file, invalid);
        open(argv[1], argv[2], argv[3], enabled);
        assert(score() == 0x2a);
        arcade::close();
        auto regenerated = readFile(file);
        assert(regenerated.size() == scoreBytes && regenerated[1] == 0x2a);
    }
    std::puts("FBNeo high scores: exact DAT, actual score write/reload/reset, save-state round trip, damaged-file rejection and Link isolation passed.");
}
