package io.github.ankushui18.fbaoffline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A small illustration composed from the same original sprites used during play. */
final class PadPreview extends View {
    private Drawable pad, knob, first, second;
    private PadArt skin;

    PadPreview(Context context, PadArt skin) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSkin(skin);
    }

    void setSkin(PadArt next) {
        if (next == skin) return;
        skin = next;
        pad = skin.ring(getContext());
        if (pad == null) pad = skin.direction(getContext(), 0);
        knob = skin.knob(getContext());
        first = skin.button(getContext(), PadLayout.FIRST_ACTION, false);
        second = skin.button(getContext(), PadLayout.FIRST_ACTION + 1, false);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float unit = Math.min(w / 120, h / 52);
        c.save();
        c.translate((w - 120 * unit) / 2, h / 2f);
        c.scale(unit, unit);
        draw(c, pad, 6, -25, 50);
        if (knob != null) draw(c, knob, 20, -11, 22);
        draw(c, first, 62, -14, 28);
        draw(c, second, 90, -21, 27);
        c.restore();
    }

    private static void draw(Canvas c, Drawable d, int x, int y, int side) {
        d.setBounds(x, y, x + side, y + side);
        d.draw(c);
    }
}
