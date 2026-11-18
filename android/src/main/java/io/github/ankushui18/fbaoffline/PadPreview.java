package io.github.ankushui18.fbaoffline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A small illustration composed from the same original sprites used during play. */
final class PadPreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean ornament;
    private Drawable pad, first, second;
    private PadSkin skin;

    PadPreview(Context context, PadSkin skin, boolean ornament) {
        super(context);
        this.ornament = ornament;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSkin(skin);
    }

    void setSkin(PadSkin next) {
        if (next == skin) return;
        skin = next;
        pad = getContext().getDrawable(ornament ? R.drawable.legacy_classic_button_dpad : skin.directions[0]);
        first = getContext().getDrawable(skin.buttons[0][0]);
        second = getContext().getDrawable(skin.buttons[1][0]);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float unit = Math.min(w / 120, h / (ornament ? 104 : 52));
        c.save();
        c.translate((w - 120 * unit) / 2, h / 2f);
        c.scale(unit, unit);
        if (ornament) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(0x4097d1ff);
            c.drawCircle(66, 0, 41, paint);
            c.drawCircle(66, 0, 49, paint);
            c.drawLine(9, 43, 40, 43, paint);
            c.drawLine(93, -44, 117, -44, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff83cfff);
            c.drawCircle(10, 43, 2, paint);
            c.drawCircle(115, -44, 2, paint);
            draw(c, pad, 5, -35, 70);
            draw(c, second, 74, -30, 34);
            draw(c, first, 62, 3, 39);
        } else {
            draw(c, pad, 6, -25, 50);
            draw(c, first, 62, -14, 28);
            draw(c, second, 90, -21, 27);
        }
        c.restore();
    }

    private static void draw(Canvas c, Drawable d, int x, int y, int side) {
        d.setBounds(x, y, x + side, y + side);
        d.draw(c);
    }
}
