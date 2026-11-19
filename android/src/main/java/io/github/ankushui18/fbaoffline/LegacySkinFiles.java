package io.github.ankushui18.fbaoffline;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** The original skin's filenames, in the layout's arcade (not RetroPad) order. */
final class LegacySkinFiles {
    static final String RING = "analog.png", KNOB = "analog_2.png";
    static final String ROUND_ICON = "icon1.png", CROSS_ICON = "icon2.png";
    static final String PAUSE = "button_pause.png";
    static final String[] DIRECTIONS = {"button_dpad.png", "button_dpad_up.png", "button_dpad_down.png",
            "button_dpad_left.png", "button_dpad_right.png"};
    static final String[] BUTTONS = {"button_x.png", "button_a.png", "button_y.png", "button_b.png", "button_l1.png",
            "button_r1.png", "button_coin.png", "button_start.png", "button_1.png", "button_2.png", "button_3.png", "button_4.png"};
    static final String[] PLAYERS = {"button_player.png", "button_player2.png", "button_player3.png", "button_player4.png"};
    static final Set<String> NAMES;
    private static final long PNG_SIGNATURE = 0x89504e470d0a1a0aL;
    static final int MAX_SOURCE_SIDE = 2048, MAX_STORED_SIDE = 256, MAX_SOURCE_BYTES = 2 * 1024 * 1024;

    static {
        Set<String> names = new LinkedHashSet<>(Arrays.asList(RING, KNOB, ROUND_ICON, CROSS_ICON, PAUSE, pressed(PAUSE)));
        Collections.addAll(names, DIRECTIONS);
        Collections.addAll(names, PLAYERS);
        for (String button : BUTTONS) { names.add(button); names.add(pressed(button)); }
        NAMES = Collections.unmodifiableSet(names);
    }

    private LegacySkinFiles() {}

    static String pressed(String idle) { return idle.substring(0, idle.length() - 4) + "_p.png"; }

    static String name(String filename) {
        String key = filename.toLowerCase(Locale.ROOT);
        return NAMES.contains(key) ? key : null;
    }

    static boolean preview(String name) { return ROUND_ICON.equals(name) || CROSS_ICON.equals(name); }

    static int pngSide(byte[] data) throws IOException {
        if (data.length < 45 || data.length > MAX_SOURCE_BYTES) throw new IOException("Choose a valid skin PNG under 2 MiB.");
        ByteBuffer head = ByteBuffer.wrap(data);
        if (head.getLong() != PNG_SIGNATURE || head.getInt() != 13 || head.getInt() != 0x49484452) {
            throw new IOException("Skin frames must be PNG images.");
        }
        int w = head.getInt(), h = head.getInt();
        if (w <= 0 || h <= 0 || w > MAX_SOURCE_SIDE || h > MAX_SOURCE_SIDE) {
            throw new IOException("Skin PNGs must be at most 2048 × 2048 pixels.");
        }
        return Math.max(w, h);
    }
}
