#include "engine.h"
#include <cassert>
#include <cstdio>
#include <iostream>
#include <string>

int main(int argc, char **argv) {
    if (argc != 3) return 2;
    if (const auto *error = arcade::open(argv[1], argv[2], argv[2], false, true)) {
        std::fprintf(stderr, "%s\n", error);
        return 1;
    }
    assert(arcade::step(0));
    assert(arcade::inputMask(1));
    uint64_t pad = 0;
    while (std::cin >> pad) {
        assert(arcade::step(pad));
        std::cout << std::hex << arcade::linkCheckpoint() << std::endl;
    }
    arcade::close();
}
