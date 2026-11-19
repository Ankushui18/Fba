#include "frame_filter.h"
#include "hqx.h"
#include <algorithm>
#include <vector>

namespace {
uint32_t swapRedBlue(uint32_t c) {
    return (c & 0xff00ff00u) | ((c & 0xffu) << 16) | ((c >> 16) & 0xffu);
}
}

namespace arcade {
bool hqxFits(unsigned w, unsigned h, unsigned factor) {
    return factor >= 2 && factor <= 4 && w && h && w <= 2048 / factor && h <= 2048 / factor
        && w * h * factor * factor <= 3u * 1024 * 1024;
}

bool scaleHqx(const uint32_t *rgba, unsigned w, unsigned h, unsigned srcPitch,
              unsigned factor, uint32_t *dst, unsigned dstPitch) {
    if (!rgba || !dst || !hqxFits(w, h, factor) || srcPitch % sizeof(uint32_t) || srcPitch < w * sizeof(uint32_t)
        || dstPitch % sizeof(uint32_t) || dstPitch < w * factor * sizeof(uint32_t)) return false;
    // The presenter can finish a snapshot even after Core.close(). No shared engine state.
    static thread_local std::vector<uint32_t> input;
    input.resize(w * h);
    for (unsigned y = 0; y < h; ++y) {
        const auto *row = rgba + size_t(y) * (srcPitch / sizeof(uint32_t));
        std::transform(row, row + w, input.data() + size_t(y) * w, swapRedBlue);
    }
    static const decltype(&hq2x_32_rb) scale[] = {hq2x_32_rb, hq3x_32_rb, hq4x_32_rb};
    scale[factor - 2](input.data(), w * sizeof(uint32_t), dst, dstPitch, w, h);
    for (unsigned y = 0; y < h * factor; ++y) {
        auto *row = dst + size_t(y) * (dstPitch / sizeof(uint32_t));
        std::transform(row, row + w * factor, row, swapRedBlue);
    }
    return true;
}
}
