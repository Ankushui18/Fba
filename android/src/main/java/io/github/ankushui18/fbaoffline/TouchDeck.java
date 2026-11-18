package io.github.ankushui18.fbaoffline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import static io.github.ankushui18.fbaoffline.Joypad.DIRECTIONS;
import static io.github.ankushui18.fbaoffline.PadLayout.*;

/** Routes all touch contacts against the same geometry that draws the legacy skins. */
final class TouchDeck extends ViewGroup {

    private static final String[] ARROWS = {"Up", "Down", "Left", "Right"};
    private static final int[] ARROW_BITS = {Joypad.UP, Joypad.DOWN, Joypad.LEFT, Joypad.RIGHT};
    private static final int TAP_MS = 48;
    private final ArcadeUi ui;
    private final PadInput pointers = new PadInput();
    private final ImageButton[] buttons = new ImageButton[KEY_END];
    private final View[] arrowTargets = new View[4];
    private final TextView[] captions = new TextView[2];
    private final Dpad dpad;
    private final long[] downAt = new long[KEY_END];
    private final Runnable[] release = new Runnable[KEY_END + ARROW_BITS.length];
    private int[] ids = new int[8];
    private float[] xs = new float[8], ys = new float[8];
    private PadLayout layout;
    private PadSkin skin;
    private GameSession session;
    private boolean interactive, tracking;
    private int held;
    private final int[] pulse = new int[KEY_END + ARROW_BITS.length];

    TouchDeck(Context context, PadSkin initialSkin) {
        super(context);
        ui = new ArcadeUi(context);
        setClipChildren(false);
        dpad = new Dpad(context);
        addView(dpad);
        dpad.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = FIRST_ACTION; i < KEY_END; i++) {
            final int key = i;
            ImageButton button = new ImageButton(context);
            button.setBackground(null);
            button.setScaleType(ImageView.ScaleType.FIT_CENTER);
            button.setPadding(0, 0, 0, 0);
            button.setContentDescription(NAMES[i]);
            button.setFocusable(true);
            button.setSoundEffectsEnabled(false);
            button.setOnClickListener(v -> {
                if (layout != null) tap(key, layout.keyBits[key]);
            });
            buttons[i] = button;
            addView(button);
            release[key] = () -> endTap(key);
        }
        for (int i = 0; i < ARROW_BITS.length; i++) {
            int bit = ARROW_BITS[i], key = KEY_END + i;
            ImageButton target = new ImageButton(context);
            target.setBackground(null);
            target.setContentDescription(ARROWS[i]);
            target.setFocusable(true);
            target.setSoundEffectsEnabled(false);
            target.setOnClickListener(v -> tap(key, bit));
            addView(target);
            arrowTargets[i] = target;
            release[key] = () -> endTap(key);
        }
        for (int i = 0; i < captions.length; i++) {
            TextView caption = ui.eyebrow(NAMES[COIN + i].toUpperCase(java.util.Locale.ROOT), Color.WHITE);
            caption.setTextSize(10);
            caption.setGravity(Gravity.CENTER);
            caption.setSingleLine();
            caption.setShadowLayer(ui.dp(2), 0, ui.dp(1), Color.BLACK);
            caption.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(caption);
            captions[i] = caption;
        }
        setSkin(initialSkin);
        setInputEnabled(false);
    }

    void attach(GameSession session) { this.session = session; }

    void setGeometry(PadLayout next) {
        if (layout != next) {
            clear();
            layout = next;
            float alpha = next.opacity;
            dpad.setAlpha(alpha);
            for (int i = FIRST_ACTION; i < KEY_END; i++) {
                buttons[i].setAlpha(alpha);
                buttons[i].setVisibility(next.controls[i].w > 0 && next.controls[i].h > 0 ? VISIBLE : INVISIBLE);
            }
            for (View arrow : arrowTargets) arrow.setVisibility(next.controls[DPAD].w > 0 ? VISIBLE : INVISIBLE);
            for (TextView label : captions) label.setAlpha(alpha);
        }
    }

    private boolean animated = true, haptic;

    void setFeedback(boolean animated, boolean haptic) {
        this.animated = animated;
        this.haptic = haptic;
        publish();
    }

    void setSkin(PadSkin next) {
        if (next == skin) return;
        clear();
        skin = next;
        dpad.setArt(next);
        for (int i = FIRST_ACTION; i < KEY_END; i++) {
            int normal = i < COMBO ? next.buttons[i - FIRST_ACTION][0] : next.combos[i - COMBO];
            int pressed = i < COMBO ? next.buttons[i - FIRST_ACTION][1] : next.combosPressed[i - COMBO];
            buttons[i].setImageDrawable(buttonArt(getContext(), normal, pressed));
        }
        for (TextView caption : captions) caption.setVisibility(skin.classic ? VISIBLE : GONE);
        requestLayout();
    }

    static Drawable buttonArt(Context context, int normal, int pressed) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, context.getDrawable(pressed));
        states.addState(new int[0], context.getDrawable(normal));
        return states;
    }

    void setInputEnabled(boolean enabled) {
        interactive = enabled;
        setImportantForAccessibility(enabled ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (!enabled) clear();
    }

    void clear() {
        held = 0;
        java.util.Arrays.fill(pulse, 0);
        tracking = false;
        pointers.clear();
        for (Runnable callback : release) if (callback != null) removeCallbacks(callback);
        publish();
    }

    private void tap(int key, int bit) {
        if (!interactive || bit == 0) return;
        if ((bit & DIRECTIONS) != 0) java.util.Arrays.fill(pulse, KEY_END, pulse.length, 0);
        pulse[key] = bit;
        removeCallbacks(release[key]);
        postDelayed(release[key], TAP_MS);
        publish();
    }

    private void endTap(int key) {
        pulse[key] = 0;
        publish();
    }

    private void publish() {
        int pulsing = 0;
        for (int key : pulse) pulsing |= key;
        int mask = held | (pulsing & ((held & DIRECTIONS) == 0 ? -1 : ~DIRECTIONS));
        if (session != null) session.touch.set(mask);
        for (int i = FIRST_ACTION; i < KEY_END; i++) {
            int bit = layout == null ? 0 : layout.keyBits[i];
            buttons[i].setPressed(animated && bit != 0 && (mask & bit) == bit);
        }
        updateStickArt(mask);
    }

    private void updateStickArt(int mask) {
        dpad.showDirections(animated ? mask & DIRECTIONS : 0, animated ? pointers.thumbX : 0, animated ? pointers.thumbY : 0);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return interactive; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!interactive || layout == null) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) { clear(); return true; }
        if (action == MotionEvent.ACTION_DOWN) {
            pointers.clear();
            tracking = true;
        } else if (!tracking) {
            // Ignore fingers that were still down when a resize, pause or focus loss cleared input.
            return false;
        }
        int count = event.getPointerCount();
        if (count > ids.length) { ids = new int[count]; xs = new float[count]; ys = new float[count]; }
        for (int i = 0; i < count; i++) {
            ids[i] = event.getPointerId(i);
            xs[i] = event.getX(i);
            ys[i] = event.getY(i);
        }
        int lifted = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP ? event.getActionIndex() : -1;
        int next = pointers.update(layout, count, lifted, ids, xs, ys);
        int changed = next ^ held;
        if (changed != 0) {
            if (haptic && (next & ~held) != 0) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            held = next;
            long now = SystemClock.uptimeMillis();
            for (int i = FIRST_ACTION; i < COMBO; i++) {
                int bit = layout.keyBits[i];
                if ((changed & bit) == 0) continue;
                if ((held & bit) != 0) downAt[i] = now;
                else if (lifted >= 0 && now - downAt[i] < TAP_MS) tap(i, bit);
            }
            publish();
        }
        updateStickArt(held);
        if (action == MotionEvent.ACTION_UP) tracking = false;
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        if (layout == null) return;
        Box pad = layout.controls[DPAD];
        measureExactly(dpad, pad.w, pad.h);
        for (View arrow : arrowTargets) measureExactly(arrow, pad.w / 3, pad.h / 3);
        for (int i = FIRST_ACTION; i < KEY_END; i++) {
            Box box = layout.controls[i];
            int captionHeight = captionHeight(i, box);
            measureExactly(buttons[i], box.w, box.h - captionHeight);
            if (i == COIN || i == START) measureExactly(captions[i - COIN], box.w, captionHeight);
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (layout == null) return;
        Box pad = layout.controls[DPAD];
        dpad.layout(pad.x, pad.y, pad.right(), pad.bottom());
        int square = pad.w / 3;
        position(arrowTargets[0], pad.x + square, pad.y);
        position(arrowTargets[1], pad.x + square, pad.bottom() - square);
        position(arrowTargets[2], pad.x, pad.y + square);
        position(arrowTargets[3], pad.right() - square, pad.y + square);
        for (int i = FIRST_ACTION; i < KEY_END; i++) {
            Box box = layout.controls[i];
            position(buttons[i], box.x, box.y);
            if (i == COIN || i == START) position(captions[i - COIN], box.x, box.bottom() - captionHeight(i, box));
        }
    }

    private int captionHeight(int key, Box box) {
        return skin.classic && (key == COIN || key == START) && box.w > box.h ? Math.min(ui.dp(13), box.h / 3) : 0;
    }

    static void measureExactly(View view, int w, int h) {
        view.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    static void position(View view, int x, int y) {
        view.layout(x, y, x + view.getMeasuredWidth(), y + view.getMeasuredHeight());
    }

    private static final class Dpad extends View {
        private final Drawable[] art = new Drawable[5];
        private int direction;
        private float thumbX, thumbY;
        private Drawable ring, knob;

        Dpad(Context context) { super(context); }

        void setArt(PadSkin skin) {
            for (int i = 0; i < skin.directions.length; i++) art[i] = getContext().getDrawable(skin.directions[i]);
            ring = skin.circle == 0 ? null : getContext().getDrawable(skin.circle);
            knob = skin.knob == 0 ? null : getContext().getDrawable(skin.knob);
            invalidate();
        }

        void showDirections(int next, float x, float y) {
            if (next == direction && (ring == null || (x == thumbX && y == thumbY))) return;
            direction = next;
            thumbX = x; thumbY = y;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (ring != null) {
                paint(canvas, ring);
                float unit = Math.max(1f, (float) Math.hypot(thumbX, thumbY));
                int half = Math.round(getWidth() * .21f);
                int x = Math.round(getWidth() * (.5f + .28f * thumbX / unit));
                int y = Math.round(getHeight() * (.5f + .28f * thumbY / unit));
                knob.setBounds(x - half, y - half, x + half, y + half);
                knob.draw(canvas);
                return;
            }
            if (direction == 0) { paint(canvas, art[0]); return; }
            for (int i = 0; i < ARROW_BITS.length; i++) {
                if (direction == ARROW_BITS[i]) { paint(canvas, art[i + 1]); return; }
            }
            // The legacy skins have cardinal pressed frames, not diagonal frames.
            // Composite the active arms with the original center for diagonals.
            float w = getWidth(), h = getHeight();
            int state = canvas.save();
            for (int i = 0; i < ARROW_BITS.length; i++) if ((direction & ARROW_BITS[i]) != 0) clipArm(canvas, i, w, h, false);
            paint(canvas, art[0]);
            canvas.restoreToCount(state);
            for (int i = 0; i < ARROW_BITS.length; i++) {
                if ((direction & ARROW_BITS[i]) == 0) continue;
                state = canvas.save();
                clipArm(canvas, i, w, h, true);
                paint(canvas, art[i + 1]);
                canvas.restoreToCount(state);
            }
        }

        private void paint(Canvas canvas, Drawable sprite) {
            sprite.setBounds(0, 0, getWidth(), getHeight());
            sprite.draw(canvas);
        }

        private static void clipArm(Canvas canvas, int arm, float w, float h, boolean inside) {
            float left = arm == 3 ? w * .64f : 0;
            float top = arm == 1 ? h * .64f : arm > 1 ? h * .36f : 0;
            float right = arm == 2 ? w * .36f : w;
            float bottom = arm == 0 ? h * .36f : arm > 1 ? h * .64f : h;
            if (inside) canvas.clipRect(left, top, right, bottom);
            else canvas.clipOutRect(left, top, right, bottom);
        }
    }
}
