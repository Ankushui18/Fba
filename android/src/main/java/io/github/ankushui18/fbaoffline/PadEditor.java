package io.github.ankushui18.fbaoffline;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import static io.github.ankushui18.fbaoffline.PadLayout.*;

/** Full-window drag editor; controls are positioned in exactly the coordinates used in play. */
final class PadEditor extends FrameLayout {
    interface Actions {
        void save(PadProfile portrait, PadProfile landscape);
        void cancel();
    }

    private final ArcadeUi ui;
    private final PadProfile[] draft;
    private final boolean fullScreen;
    private final Preview preview;
    private final LinearLayout panel;
    private final TextView selection;
    private final SeekBar size;
    private int selected = DPAD;
    private boolean top = true, updating;

    PadEditor(Context context, PadArt skin, PadProfile portrait, PadProfile landscape, boolean fullScreen, Actions actions) {
        super(context);
        ui = new ArcadeUi(context);
        draft = new PadProfile[] {portrait, landscape};
        this.fullScreen = fullScreen;
        setBackgroundColor(0xff0c1725);
        setFocusableInTouchMode(true);
        setClickable(true);
        preview = new Preview(context, skin);
        addView(preview, new LayoutParams(-1, -1));
        panel = ui.column();
        panel.setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(10));
        panel.setBackground(ui.surface(0xfd132b46, ArcadeUi.NIGHT_EDGE, 16));
        addView(panel, panelParams());
        LinearLayout title = ui.row();
        TextView label = ui.text("Edit controls", 17, Color.WHITE, true);
        title.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton flip = ui.icon(R.drawable.legacy_ui_move, "Move editor panel to other edge", 0xff243c56, 0xffd9ebfc, 11, 11);
        flip.setOnClickListener(v -> { top = !top; panel.setLayoutParams(panelParams()); });
        title.addView(flip, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        panel.addView(title);
        LinearLayout tools = ui.row();
        selection = ui.text("", 14, 0xffdaeeff, true);
        selection.setBackground(ui.tappable(0xff233e5c, 0xff345f86, 9, true));
        selection.setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9));
        selection.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle("Select a control")
                .setSingleChoiceItems(NAMES, selected, (dialog, which) -> { select(which); dialog.dismiss(); })
                .setNegativeButton("Cancel", null).show());
        tools.addView(selection, new LinearLayout.LayoutParams(-2, -2));
        size = new SeekBar(context);
        size.setContentDescription("Selected control size");
        size.setMax(135);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (!updating && user) { profile().scale[selected] = (50 + progress) / 100f; preview.invalidate(); updateLabel(); }
            }
        });
        tools.addView(size, new LinearLayout.LayoutParams(0, ui.dp(44), 1));
        panel.addView(tools);
        LinearLayout buttons = ui.row();
        action(buttons, "Cancel", false, actions::cancel);
        action(buttons, "Options", false, this::options);
        action(buttons, "Save", true, () -> actions.save(draft[0], draft[1]));
        panel.addView(buttons);
        select(DPAD);
        requestFocus();
    }

    private void action(LinearLayout row, String text, boolean primary, Runnable action) {
        android.widget.Button button = ui.button(text, primary ? ArcadeUi.BLUE : 0xff203b57, Color.WHITE, 9);
        button.setPadding(ui.dp(5), ui.dp(6), ui.dp(5), ui.dp(6));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMarginEnd(ui.dp(5));
        row.addView(button, p);
    }

    private void options() {
        int orientation = orientation();
        PadProfile current = draft[orientation];
        LinearLayout controls = ui.column();
        controls.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(4));
        controls.addView(ui.text("Drag a button on the preview. Portrait and landscape edits are saved separately.", 14, ArcadeUi.MUTED, false));
        ui.space(controls, 10);
        addSlider(controls, "Opacity", 20, 100, current.opacity, n -> { current.opacity = n; preview.invalidate(); });
        addSlider(controls, "All buttons size", 60, 160, current.overall, n -> { current.overall = n; preview.invalidate(); });
        addSlider(controls, "Joystick dead zone", 12, 65, current.deadZone, n -> { current.deadZone = n; preview.invalidate(); });
        addSlider(controls, "Extra joystick touch area", 0, 35, current.stickMargin, n -> { current.stickMargin = n; preview.invalidate(); });
        addSlider(controls, "Extra button touch area", 0, 35, current.buttonMargin, n -> { current.buttonMargin = n; preview.invalidate(); });
        if (preview.layout != null) {
            Box box = preview.layout.controls[selected];
            int x = Math.round(100 * (box.x + box.w / 2f) / Math.max(1, preview.getWidth()));
            int y = Math.round(100 * (box.y + box.h / 2f) / Math.max(1, preview.getHeight()));
            addSlider(controls, NAMES[selected] + " position · left to right", 0, 100, x, n -> {
                if (current.y[selected] < 0) current.y[selected] = y / 100f;
                current.x[selected] = n / 100f;
                preview.invalidate();
            });
            addSlider(controls, NAMES[selected] + " position · top to bottom", 0, 100, y, n -> {
                if (current.x[selected] < 0) current.x[selected] = x / 100f;
                current.y[selected] = n / 100f;
                preview.invalidate();
            });
        }
        android.widget.CheckBox hide = new android.widget.CheckBox(getContext());
        hide.setText("Hide " + NAMES[selected] + " in play");
        hide.setEnabled(selected != MENU);
        hide.setChecked(current.hidden[selected]);
        hide.setOnCheckedChangeListener((v, checked) -> { current.hidden[selected] = checked; preview.invalidate(); });
        controls.addView(hide);
        android.widget.ScrollView scroll = new android.widget.ScrollView(getContext());
        scroll.addView(controls);
        new AlertDialog.Builder(getContext()).setTitle("Control options").setView(scroll)
                .setNeutralButton("Reset layout", (d, w) -> { draft[orientation] = new PadProfile(); select(selected); })
                .setPositiveButton("Done", null).show();
    }

    private void addSlider(LinearLayout parent, String caption, int min, int max, int value, java.util.function.IntConsumer change) {
        TextView title = ui.text(caption + " · " + value + "%", 14, ArcadeUi.INK, true);
        parent.addView(title);
        SeekBar bar = new SeekBar(getContext());
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setContentDescription(caption);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
            @Override public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                int n = min + progress;
                title.setText(caption + " · " + n + "%");
                if (fromUser) change.accept(n);
            }
        });
        parent.addView(bar, new LinearLayout.LayoutParams(-1, ui.dp(43)));
    }

    private int orientation() { return preview.getWidth() > preview.getHeight() ? 1 : 0; }
    private PadProfile profile() { return draft[orientation()]; }

    private LayoutParams panelParams() {
        LayoutParams p = new LayoutParams(-1, -2, (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL);
        p.setMargins(ui.dp(12), ui.dp(5), ui.dp(12), ui.dp(5));
        return p;
    }

    private void select(int index) {
        selected = index;
        updating = true;
        size.setProgress(Math.round(profile().scale[index] * 100) - 50);
        updating = false;
        updateLabel();
        preview.invalidate();
    }

    private void updateLabel() { selection.setText(NAMES[selected] + " · " + Math.round(profile().scale[selected] * 100) + "% ▾"); }

    private final class Preview extends View {
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable[] artwork = new Drawable[COUNT];
        private final Drawable knob;
        private PadLayout layout;
        private int pointer = -1, picked = -1;
        private float offsetX, offsetY;

        Preview(Context context, PadArt skin) {
            super(context);
            artwork[DPAD] = skin.ring(context);
            if (artwork[DPAD] == null) artwork[DPAD] = skin.direction(context, 0);
            knob = skin.knob(context);
            for (int i = FIRST_ACTION; i < KEY_END; i++) artwork[i] = skin.button(context, i, false);
            artwork[PLAYER] = skin.player(context, 0);
            artwork[MENU] = skin.pause(context, false);
            setContentDescription("Drag a control. Use the options panel to change size or visibility.");
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            pointer = picked = -1;
            if (PadEditor.this.size != null) select(selected);
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            layout = new PadLayout(w, h, getResources().getDisplayMetrics().density, true, profile(), null, true, true,
                    PadLayout.CUSTOM, 0, fullScreen);
            ink.setColor(0xff152b43);
            int grid = Math.max(1, ui.dp(24));
            for (int i = grid; i < w; i += grid) canvas.drawLine(i, 0, i, h, ink);
            for (int i = grid; i < h; i += grid) canvas.drawLine(0, i, w, i, ink);
            ink.setColor(0xff102137);
            Box video = layout.video;
            canvas.drawRoundRect(video.x + ui.dp(8), video.y + ui.dp(3), video.right() - ui.dp(8), video.bottom(), 12, 12, ink);
            ink.setColor(0xff86a9c7);
            ink.setTextSize(ui.dp(14));
            ink.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("DRAG ANY BUTTON  ·  " + (w > h ? "LANDSCAPE" : "PORTRAIT"), w / 2f,
                    video.y + video.h / 2f, ink);
            for (int i = 0; i < COUNT; i++) if (i != selected) control(canvas, i);
            control(canvas, selected);
        }

        private void control(Canvas c, int index) {
            Box box = layout.controls[index];
            if (box.w == 0 || box.h == 0) return;
            float margin = index == DPAD ? layout.stickMargin : index < KEY_END ? layout.buttonMargin : 0;
            if (index == selected && margin > 0) {
                float extra = Math.min(box.w, box.h) * margin;
                ink.setColor(0x4c8bd9ff);
                c.drawRect(box.x - extra, box.y - extra, box.right() + extra, box.bottom() + extra, ink);
            }
            int alpha = profile().hidden[index] ? 75 : Math.round(255 * layout.opacity);
            drawArt(c, artwork[index], box.x, box.y, box.w, box.h, alpha);
            if (index == DPAD && knob != null) {
                int size = Math.round(box.w * .42f);
                drawArt(c, knob, box.x + (box.w - size) / 2, box.y + (box.h - size) / 2, size, size, alpha);
            }
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(Math.max(1, ui.dp(index == selected ? 2 : 1)));
            ink.setColor(index == selected ? 0xff8bd9ff : 0x496da2c8);
            c.drawRoundRect(box.x, box.y, box.right(), box.bottom(), ui.dp(7), ui.dp(7), ink);
            ink.setStyle(Paint.Style.FILL);
        }

        private void drawArt(Canvas c, Drawable drawable, int x, int y, int w, int h, int alpha) {
            int iw = drawable.getIntrinsicWidth(), ih = drawable.getIntrinsicHeight();
            if (iw > 0 && ih > 0) {
                float fit = Math.min((float) w / iw, (float) h / ih);
                int rw = Math.round(iw * fit), rh = Math.round(ih * fit);
                x += (w - rw) / 2; y += (h - rh) / 2; w = rw; h = rh;
            }
            drawable.setAlpha(alpha);
            drawable.setBounds(x, y, x + w, y + h);
            drawable.draw(c);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (layout == null) return false;
            int action = event.getActionMasked(), index = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN) {
                float x = event.getX(), y = event.getY();
                picked = -1;
                // The selected control is drawn last and wins overlaps while editing.
                if (layout.controls[selected].contains(x, y)) picked = selected;
                for (int i = COUNT - 1; i >= 0 && picked < 0; i--) if (layout.controls[i].contains(x, y)) picked = i;
                if (picked < 0) return true;
                pointer = event.getPointerId(index);
                Box box = layout.controls[picked];
                offsetX = x - box.x - box.w / 2f;
                offsetY = y - box.y - box.h / 2f;
                select(picked);
            } else if (action == MotionEvent.ACTION_MOVE && pointer != -1 && picked >= 0) {
                int at = event.findPointerIndex(pointer);
                if (at >= 0) {
                    profile().x[picked] = PadProfile.clamp((event.getX(at) - offsetX) / getWidth(), 0, 1);
                    profile().y[picked] = PadProfile.clamp((event.getY(at) - offsetY) / getHeight(), 0, 1);
                    invalidate();
                }
            } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP ||
                    (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(index) == pointer)) {
                pointer = picked = -1;
                if (action == MotionEvent.ACTION_UP) performClick();
            }
            return true;
        }
        @Override public boolean performClick() { return super.performClick(); }
    }
}
