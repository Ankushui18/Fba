#include "frame_filter.h"
#include "hqx.h"
#include <algorithm>
#include <vector>

namespace {
std::vector<uint32_t> input; // Owned by the single emulation/render thread.
uint32_t swapRedBlue(uint32_t c) {
    return (c & 0xff00ff00u) | ((c & 0xffu) << 16) | ((c >> 16) & 0xffu);
}
}

namespace arcade {
bool hqxFits(unsigned w, unsigned h, unsigned factor) {
    return factor >= 2 && factor <= 4 && w && h && w <= 2048 / factor && h <= 2048 / factor
        && w * h * factor * factor <= 3u * 1024 * 1024;
}

bool scaleHqx(const uint32_t *rgba, unsigned w, unsigned h, unsigned factor, uint32_t *dst, unsigned pitch) {
    if (!rgba || !dst || !hqxFits(w, h, factor) || pitch % sizeof(uint32_t) || pitch < w * factor * sizeof(uint32_t)) return false;
    input.resize(w * h);
    std::transform(rgba, rgba + w * h, input.begin(), swapRedBlue);
    static const decltype(&hq2x_32_rb) scale[] = {hq2x_32_rb, hq3x_32_rb, hq4x_32_rb};
    scale[factor - 2](input.data(), w * sizeof(uint32_t), dst, pitch, w, h);
    for (unsigned y = 0; y < h * factor; ++y, dst += pitch / sizeof(uint32_t))
        std::transform(dst, dst + w * factor, dst, swapRedBlue);
    return true;
}

void clearFilter() { std::vector<uint32_t>().swap(input); }
}
