package io.github.ankushui18.fbaoffline;

import static io.github.ankushui18.fbaoffline.Joypad.*;
import static io.github.ankushui18.fbaoffline.PadLayout.*;

/** One thumb owns the D-pad until it lifts. Other pointers can share action buttons. */
final class PadInput {
    // Legacy A–F artwork arranged by arcade position, not the art's internal filenames.
    // The generic FBNeo RetroPad order is B/A/Y/X/R/L; some games map it differently.
    static final int[] KEY_BITS = {0, B, A, Y, X, R, L, Joypad.COIN, Joypad.START};
    private int directionPointer = -1;
    float thumbX, thumbY;

    void clear() { directionPointer = -1; thumbX = thumbY = 0; }

    int update(PadLayout layout, int count, int lifted, int[] ids, float[] x, float[] y) {
        PadLayout.Box pad = layout.controls[DPAD];
        int owner = -1;
        for (int i = 0; i < count; i++) {
            if (i != lifted && ids[i] == directionPointer) { owner = i; break; }
        }
        if (owner == -1) {
            for (int i = 0; i < count; i++) {
                if (i != lifted && layout.directionAt(x[i], y[i]) && layout.keyAt(x[i], y[i]) == -1) { owner = i; break; }
            }
            directionPointer = owner == -1 ? -1 : ids[owner];
        }
        thumbX = owner == -1 || pad.w == 0 ? 0 : 2 * (x[owner] - pad.x) / pad.w - 1;
        thumbY = owner == -1 || pad.h == 0 ? 0 : 2 * (y[owner] - pad.y) / pad.h - 1;
        int bits = directions(thumbX, thumbY, layout.deadZone);
        for (int i = 0; i < count; i++) {
            if (i == owner || i == lifted) continue;
            int key = layout.keyAt(x[i], y[i]);
            if (key != -1) bits |= layout.keyBits[key];
        }
        return bits;
    }
}
