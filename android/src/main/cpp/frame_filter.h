#pragma once

#include <stdint.h>

namespace arcade {
bool hqxFits(unsigned width, unsigned height, unsigned factor);
// Input/destination are Android little-endian RGBA words. Destination pitch is in bytes.
bool scaleHqx(const uint32_t *rgba, unsigned width, unsigned height, unsigned factor, uint32_t *dst, unsigned pitch);
void clearFilter();
}
