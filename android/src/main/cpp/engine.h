#pragma once

#include <stddef.h>
#include <stdint.h>

namespace arcade {
struct Video {
    const uint32_t *rgba;
    unsigned width, height, rotation;
    float aspect;
};

struct Audio {
    const int16_t *pcm;
    size_t samples;
};

// Only call from the emulation thread. The pointers returned by these accessors
// are owned by the engine; the values can change on the next step/close.
const char *open(const char *rom, const char *system, const char *save);
void close();
bool step(uint16_t input);
Video video();
Audio audio();
double fps();
int sampleRate();
}  // namespace arcade
