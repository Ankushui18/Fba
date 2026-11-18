#pragma once

#include <stddef.h>
#include <stdint.h>
#include <string>
#include <vector>

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
const char *open(const char *rom, const char *system, const char *save, bool patched = false, bool linked = false);
void close();
bool step(uint64_t input);
bool reset();
size_t stateSize();
bool saveState(void *data, size_t size);
bool loadState(const void *data, size_t size);
struct Choice { std::string value, label; };
struct Option {
    std::string key, category, title, info;
    std::vector<Choice> choices;
    unsigned defaultChoice, selected;
    bool visible, overridden;
};
const std::vector<Option> &options();
bool setOption(const char *key, int choice);
unsigned inputMask(unsigned port);
uint64_t linkCheckpoint();
Video video();
Audio audio();
double fps();
int sampleRate();
}  // namespace arcade
