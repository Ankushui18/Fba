package io.github.ankushui18.fbaoffline;

/** Digital RetroPad input bits. These positions are part of the libretro ABI. */
final class Joypad {
    static final int B = 1, Y = 1 << 1, COIN = 1 << 2, START = 1 << 3;
    static final int UP = 1 << 4, DOWN = 1 << 5, LEFT = 1 << 6, RIGHT = 1 << 7;
    static final int A = 1 << 8, X = 1 << 9, L = 1 << 10, R = 1 << 11;
    static final int DIRECTIONS = UP | DOWN | LEFT | RIGHT;

    private Joypad() {}

    static int directions(float x, float y, float deadZone) {
        int value = 0;
        if (x < -deadZone) value |= LEFT;
        if (x > deadZone) value |= RIGHT;
        if (y < -deadZone) value |= UP;
        if (y > deadZone) value |= DOWN;
        return value;
    }
}
