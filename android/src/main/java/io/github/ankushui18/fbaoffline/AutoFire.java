package io.github.ankushui18.fbaoffline;

import java.util.Arrays;

/** Emulation-thread repeat timer. A tap starts pressed; only held face bits pulse. */
final class AutoFire {
    private final int[][] held = new int[4][PadBindings.FACE_BITS.length];

    int apply(int port, int input, int mask, int delay, int period) {
        int result = input, on = (period + 1) / 2;
        int[] age = held[port];
        for (int i = 0; i < age.length; i++) {
            int button = PadBindings.FACE_BITS[i];
            if ((input & mask & button) == 0) {
                age[i] = 0;
                continue;
            }
            // No synchronized global wave: a newly pressed button always registers.
            if (age[i] >= delay + period) age[i] = delay;
            if (age[i] >= delay + on) result &= ~button;
            age[i]++;
        }
        return result;
    }

    void reset() { for (int[] port : held) Arrays.fill(port, 0); }
}
