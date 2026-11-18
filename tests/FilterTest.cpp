#include "frame_filter.h"
#include <algorithm>
#include <cassert>
#include <cstdio>
#include <vector>

int main() {
    const uint32_t red = 0xff0000ffu, white = 0xffffffffu;
    const uint32_t source[] = {white, white, red, white, red, red, red, red, red};
    for (unsigned scale = 2; scale <= 4; scale++) {
        const unsigned side = 3 * scale, stride = side + 3;
        std::vector<uint32_t> out(side * stride, 0);
        assert(arcade::hqxFits(3, 3, scale));
        assert(arcade::scaleHqx(source, 3, 3, scale, out.data(), stride * 4));
        assert(out[0] == white && out[side - 1] == red);
        bool interpolated = false;
        for (unsigned y = 0; y < side; y++) {
            for (unsigned x = 0; x < side; x++) {
                auto p = out[y * stride + x];
                assert((p & 0xff0000ffu) == red);  // preserve alpha, red and channel order
                interpolated |= p != white && p != red;
            }
            assert(out[y * stride + side] == 0);
        }
        assert(interpolated);
    }
    assert(!arcade::hqxFits(1024, 240, 4));
    assert(!arcade::hqxFits(0, 240, 2));
    arcade::clearFilter();
    std::puts("HQ2x/HQ3x/HQ4x interpolate an edge with correct RGBA channels and bounded output.");
}
