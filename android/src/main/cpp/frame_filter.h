#pragma once

#include <stdint.h>

namespace arcade {
bool hqxFits(unsigned width, unsigned height, unsigned factor);
// Input/destination are Android little-endian RGBA words. Both pitches are in bytes.
bool scaleHqx(const uint32_t *rgba, unsigned width, unsigned height, unsigned srcPitch,
    unsigned factor, uint32_t *dst, unsigned dstPitch);
}
