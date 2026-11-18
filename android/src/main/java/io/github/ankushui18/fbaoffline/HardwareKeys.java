package io.github.ankushui18.fbaoffline;

import android.view.KeyEvent;

import static io.github.ankushui18.fbaoffline.Joypad.*;
import static android.view.KeyEvent.*;

/** The labels match the virtual buttons, rather than the gamepad's manufacturer labels. */
final class HardwareKeys {
    static final String[] NAMES = {"Up", "Down", "Left", "Right", "A", "B", "C", "D", "E", "F", "Coin", "Start"};
    static final int[] FACE_BITS = {B, A, Y, X, R, L};
    static final int FACE_MASK = A | B | X | Y | L | R;
    private static final int[] BITS = {UP, DOWN, LEFT, RIGHT, B, A, Y, X, R, L, COIN, START};
    private static final int[] BUTTONS = {KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT,
            KEYCODE_BUTTON_B, KEYCODE_BUTTON_A, KEYCODE_BUTTON_Y, KEYCODE_BUTTON_X, KEYCODE_BUTTON_R1, KEYCODE_BUTTON_L1,
            KEYCODE_BUTTON_SELECT, KEYCODE_BUTTON_START};
    private static final int[] KEYBOARD = {-1, -1, -1, -1, KEYCODE_X, KEYCODE_Z, KEYCODE_S, KEYCODE_A,
            KEYCODE_W, KEYCODE_Q, KEYCODE_SPACE, KEYCODE_ENTER};

    private HardwareKeys() {}

    static int mask(int code, int[] overrides) {
        for (int i = 0; i < BITS.length; i++) if (overrides[i] > 0 && overrides[i] == code) return BITS[i];
        for (int i = 0; i < BITS.length; i++) if (overrides[i] == 0 && (code == BUTTONS[i] || code == KEYBOARD[i])) return BITS[i];
        return 0;
    }

    static String label(int index, int override) {
        if (override < 0) return "Not bound";
        if (override > 0) return keyName(override);
        return keyName(BUTTONS[index]) + (KEYBOARD[index] > 0 ? " / " + keyName(KEYBOARD[index]) : "");
    }

    private static String keyName(int code) {
        return KeyEvent.keyCodeToString(code).replace("KEYCODE_", "").replace("BUTTON_", "Pad ").replace('_', ' ');
    }

    static boolean canBind(int code) {
        return code > 0 && code != KEYCODE_BACK && code != KEYCODE_ESCAPE && code != KEYCODE_BUTTON_MODE
                && code != KEYCODE_HOME && code != KEYCODE_POWER && code != KEYCODE_APP_SWITCH
                && code != KEYCODE_VOLUME_UP && code != KEYCODE_VOLUME_DOWN && code != KEYCODE_VOLUME_MUTE;
    }
}
