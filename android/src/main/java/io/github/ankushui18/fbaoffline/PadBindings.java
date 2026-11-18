package io.github.ankushui18.fbaoffline;

import static io.github.ankushui18.fbaoffline.Joypad.*;

/** A–F and the four extra buttons map directly to core bits, never to other mappings. */
final class PadBindings {
    static final int[] FACE_BITS = {B, A, Y, X, R, L};
    static final int FACE_MASK = A | B | X | Y | L | R;
    static final int EXTRAS = 4;
    static final PadBindings DEFAULT = new PadBindings(null, null);
    private static final int[] MOVEMENT = {UP, DOWN, LEFT, RIGHT};
    private final int[] faces = FACE_BITS.clone(), extras = new int[EXTRAS];

    PadBindings(int[] faceCombos, int[] extraCombos) {
        if (faceCombos != null) for (int i = 0; i < Math.min(faces.length, faceCombos.length); i++) {
            int bits = faceCombos[i] & FACE_MASK;
            if (bits != 0) faces[i] = bits;
        }
        if (extraCombos != null) for (int i = 0; i < Math.min(extras.length, extraCombos.length); i++) {
            extras[i] = extraCombos[i] & FACE_MASK;
        }
    }

    int face(int index) { return faces[index]; }
    int extra(int index) { return extras[index]; }

    int physical(int index) {
        if (index < MOVEMENT.length) return MOVEMENT[index];
        if (index < 10) return face(index - MOVEMENT.length);
        if (index == 10) return COIN;
        if (index == 11) return START;
        return extra(index - 12);
    }
}
