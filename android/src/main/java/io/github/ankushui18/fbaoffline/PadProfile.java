package io.github.ankushui18.fbaoffline;

import java.util.Arrays;
import java.util.Locale;

/** Normalized layout shared by play and the drag editor, independently saved per orientation. */
final class PadProfile {
    final float[] x = new float[PadLayout.COUNT], y = new float[PadLayout.COUNT], scale = new float[PadLayout.COUNT];
    final boolean[] hidden = new boolean[PadLayout.COUNT];
    int opacity = 90, overall = 100, deadZone = 27, buttonMargin, stickMargin;

    PadProfile() {
        Arrays.fill(x, -1);
        Arrays.fill(y, -1);
        Arrays.fill(scale, 1);
    }

    String encode() {
        StringBuilder out = new StringBuilder().append(opacity).append(',').append(overall).append(',').append(deadZone).append(',')
                .append(buttonMargin).append(',').append(stickMargin);
        for (int i = 0; i < x.length; i++) out.append(';').append(String.format(Locale.ROOT, "%.5f,%.5f,%.3f,%d", x[i], y[i], scale[i], hidden[i] ? 1 : 0));
        return out.toString();
    }

    static PadProfile decode(String encoded) {
        PadProfile p = new PadProfile();
        if (encoded == null || encoded.length() > 2048) return p;
        String[] rows = encoded.split(";", -1);
        if (rows.length != PadLayout.COUNT + 1) return p;
        try {
            String[] attrs = rows[0].split(",");
            p.opacity = Math.round(clamp(Float.parseFloat(attrs[0]), 20, 100));
            p.overall = Math.round(clamp(Float.parseFloat(attrs[1]), 60, 160));
            p.deadZone = Math.round(clamp(Float.parseFloat(attrs[2]), 12, 65));
            if (attrs.length > 3) p.buttonMargin = Math.round(clamp(Float.parseFloat(attrs[3]), 0, 35));
            // v0.4.0 stored one expansion value for both hit zones.
            p.stickMargin = attrs.length > 4 ? Math.round(clamp(Float.parseFloat(attrs[4]), 0, 35)) : p.buttonMargin;
            for (int i = 0; i < PadLayout.COUNT; i++) {
                String[] key = rows[i + 1].split(",");
                float x = Float.parseFloat(key[0]), y = Float.parseFloat(key[1]);
                if (x >= 0 && y >= 0) { p.x[i] = clamp(x, 0, 1); p.y[i] = clamp(y, 0, 1); }
                p.scale[i] = clamp(Float.parseFloat(key[2]), .5f, 1.85f);
                p.hidden[i] = i != PadLayout.MENU && key[3].equals("1");
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) { return new PadProfile(); }
        return p;
    }

    static float clamp(float n, float min, float max) {
        return !Float.isFinite(n) ? min : Math.max(min, Math.min(max, n));
    }
}
