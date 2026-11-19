package io.github.ankushui18.fbaoffline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.Collections;
import java.util.Map;

import static io.github.ankushui18.fbaoffline.PadLayout.*;

/** Built-in art with optional sanitized PNG replacements. The skin never defines input or geometry. */
final class PadArt {
    final String title, detail, key;
    private final PadSkin base;
    private final Map<String, Bitmap> frames;

    PadArt(PadSkin base) { this(base, base.name(), base.title, base.detail, Collections.emptyMap()); }

    PadArt(PadSkin base, String key, String title, String detail, Map<String, Bitmap> frames) {
        this.base = base; this.key = key; this.title = title; this.detail = detail; this.frames = frames;
    }

    Drawable ring(Context c) { return base.circle == 0 ? null : art(c, LegacySkinFiles.RING, base.circle); }
    Drawable knob(Context c) { return base.knob == 0 ? null : art(c, LegacySkinFiles.KNOB, base.knob); }

    Drawable direction(Context c, int i) {
        String name = LegacySkinFiles.DIRECTIONS[i];
        if (i > 0 && !frames.containsKey(name) && frames.containsKey(LegacySkinFiles.DIRECTIONS[0])) name = LegacySkinFiles.DIRECTIONS[0];
        return art(c, name, base.directions[i]);
    }

    Drawable button(Context c, int key, boolean pressed) {
        String name = LegacySkinFiles.BUTTONS[key - FIRST_ACTION];
        int fallback = key < COMBO ? base.buttons[key - FIRST_ACTION][pressed ? 1 : 0]
                : (pressed ? base.combosPressed : base.combos)[key - COMBO];
        return buttonFrame(c, name, pressed, fallback);
    }

    Drawable pause(Context c, boolean pressed) {
        return buttonFrame(c, LegacySkinFiles.PAUSE, pressed,
                pressed ? R.drawable.legacy_classic_button_pause_p : R.drawable.legacy_classic_button_pause);
    }

    Drawable player(Context c, int player) { return art(c, LegacySkinFiles.PLAYERS[player], base.player[player]); }

    boolean needsCaption(int key) {
        return base.classic && (key == COIN || key == START) && !frames.containsKey(LegacySkinFiles.BUTTONS[key - FIRST_ACTION]);
    }

    private Drawable buttonFrame(Context c, String name, boolean pressed, int fallback) {
        String active = LegacySkinFiles.pressed(name);
        if (pressed && (frames.containsKey(active) || !frames.containsKey(name))) name = active;
        return art(c, name, fallback);
    }

    private Drawable art(Context context, String name, int fallback) {
        Bitmap image = frames.get(name);
        return image == null ? (fallback == 0 ? null : context.getDrawable(fallback)) : new BitmapDrawable(context.getResources(), image);
    }
}
