#include "engine.h"
#include <cassert>
#include <cstdio>
#include <cstring>

int main(int argc, char **argv) {
    // args: private directory, unknown set ZIP, incomplete 1942 ZIP, optional real ROM ZIP
    if (argc != 4 && argc != 5) return 2;
    for (int i = 0; i < 2; i++) {
        assert(arcade::open(argv[2], argv[1], argv[1]) != nullptr);
        assert(!arcade::step(0));
        const char *err = arcade::open(argv[3], argv[1], argv[1]);
        assert(err && strstr(err, "missing ROM data"));
        assert(!arcade::step(0));
    }
    if (argc == 5) {
        if (const char *err = arcade::open(argv[4], argv[1], argv[1])) {
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
        std::printf("FBNeo emitted %ux%u video and %zu PCM samples.\n", video.width, video.height, produced);
        arcade::close();
    }
    std::puts("Real-core load/failure smoke checks passed.");
}
