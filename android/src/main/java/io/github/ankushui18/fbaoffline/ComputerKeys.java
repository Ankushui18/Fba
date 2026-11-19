package io.github.ankushui18.fbaoffline;

import static android.view.KeyEvent.*;

/** Maps physical Android keys to libretro's retro_key values, not arcade pad aliases. */
final class ComputerKeys {
    private ComputerKeys() {}

    static boolean supported(ConsoleSystem system) { return system == ConsoleSystem.MSX || system == ConsoleSystem.SPECTRUM; }

    static int code(int key) {
        if (key >= KEYCODE_A && key <= KEYCODE_Z) return 'a' + key - KEYCODE_A;
        if (key >= KEYCODE_0 && key <= KEYCODE_9) return '0' + key - KEYCODE_0;
        if (key >= KEYCODE_NUMPAD_0 && key <= KEYCODE_NUMPAD_9) return '0' + key - KEYCODE_NUMPAD_0;
        if (key >= KEYCODE_F1 && key <= KEYCODE_F11) return 282 + key - KEYCODE_F1;
        switch (key) {
            case KEYCODE_ENTER: case KEYCODE_NUMPAD_ENTER: return 13;
            case KEYCODE_SPACE: return ' ';
            case KEYCODE_TAB: return 9;
            case KEYCODE_DEL: return 8;
            case KEYCODE_FORWARD_DEL: return 127;
            case KEYCODE_F12: return 27; // Physical Escape still pauses the frontend.
            case KEYCODE_DPAD_UP: return 273;
            case KEYCODE_DPAD_DOWN: return 274;
            case KEYCODE_DPAD_RIGHT: return 275;
            case KEYCODE_DPAD_LEFT: return 276;
            case KEYCODE_INSERT: return 277;
            case KEYCODE_MOVE_HOME: return 278;
            case KEYCODE_MOVE_END: return 279;
            case KEYCODE_CAPS_LOCK: return 301;
            case KEYCODE_SHIFT_RIGHT: return 303;
            case KEYCODE_SHIFT_LEFT: return 304;
            case KEYCODE_CTRL_RIGHT: return 305;
            case KEYCODE_CTRL_LEFT: return 306;
            case KEYCODE_ALT_RIGHT: return 307;
            case KEYCODE_ALT_LEFT: return 308;
            case KEYCODE_BREAK: return 318;
            case KEYCODE_SEMICOLON: return ':';
            case KEYCODE_APOSTROPHE: return '"';
            case KEYCODE_PERIOD: return '.';
            case KEYCODE_COMMA: return ',';
            case KEYCODE_MINUS: return '-';
            case KEYCODE_EQUALS: return '=';
            case KEYCODE_SLASH: return '/';
            default: return 0;
        }
    }
}
