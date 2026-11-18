package io.github.ankushui18.fbaoffline;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Black full-screen stage in landscape, with a dedicated controller dock in portrait. */
final class GameScreen extends FrameLayout {
    interface Actions {
        void togglePause();
        void toggleSound();
        void chooseSkin();
        void toggleControls();
        void exit();
    }

    private final ArcadeUi ui;
    private final Stage stage;
    private final FrameLayout menu;
    private TextView skinValue, soundValue, controlsValue;

    GameScreen(Context context, String name, PadSkin skin, boolean touchVisible, boolean muted, Actions actions) {
        super(context);
        ui = new ArcadeUi(context);
        setBackgroundColor(Color.BLACK);
        stage = new Stage(context, name, skin, touchVisible, actions::togglePause);
        addView(stage, new LayoutParams(-1, -1));
        menu = pauseMenu(context, name, actions);
        addView(menu, new LayoutParams(-1, -1));
        menu.setVisibility(GONE);
        setControlsVisible(touchVisible);
        setSkin(skin);
        setMuted(muted);
    }

    SurfaceHolder surface() { return stage.pixels.getHolder(); }
    void attach(GameSession session) { stage.deck.attach(session); }
    void ready() { stage.loading.setVisibility(GONE); }
    void setInputEnabled(boolean enabled) { stage.deck.setInputEnabled(enabled && stage.controlsVisible); }

    void setSkin(PadSkin skin) {
        stage.deck.setSkin(skin);
        skinValue.setText(skin.title);
    }

    void setMuted(boolean muted) { soundValue.setText(muted ? "Off" : "On"); }

    void setControlsVisible(boolean visible) {
        stage.controlsVisible = visible;
        stage.deck.setVisibility(visible ? VISIBLE : GONE);
        controlsValue.setText(visible ? "On" : "Off");
        stage.requestLayout();
    }

    void setPaused(boolean paused) {
        stage.setImportantForAccessibility(paused ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        menu.setVisibility(paused ? VISIBLE : GONE);
        if (paused) menu.requestFocus();
        else stage.requestFocus();
    }

    void focusStage() { stage.requestFocus(); }

    private FrameLayout pauseMenu(Context context, String name, Actions actions) {
        FrameLayout scrim = new FrameLayout(context);
        scrim.setBackgroundColor(0xc507111e);
        scrim.setClickable(true);
        scrim.setFocusableInTouchMode(true);

        FrameLayout bound = ui.widthLimit(380);
        LayoutParams centered = new LayoutParams(-1, -2, Gravity.CENTER);
        centered.setMargins(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12));
        scrim.addView(bound, centered);
        ScrollView scroll = new ScrollView(context);
        scroll.setBackground(ui.surface(ArcadeUi.NIGHT, ArcadeUi.NIGHT_EDGE, 22));
        scroll.setClipToOutline(true);
        scroll.setVerticalScrollBarEnabled(false);
        bound.addView(scroll, new LayoutParams(-1, -2));
        LinearLayout card = ui.column();
        card.setPadding(ui.dp(18), ui.dp(17), ui.dp(18), ui.dp(13));
        scroll.addView(card);
        card.addView(ui.eyebrow("GAME PAUSED", 0xff8fcbff));
        ui.space(card, 5);
        TextView title = ui.text(name, 24, Color.WHITE, true);
        ArcadeUi.singleLine(title);
        card.addView(title);
        ui.space(card, 14);
        item(card, R.drawable.legacy_ui_start, "Resume game", null, true, actions::togglePause);
        ui.space(card, 8);
        soundValue = item(card, R.drawable.legacy_ui_menu_setting, "Game sound", "On", false, actions::toggleSound);
        skinValue = item(card, R.drawable.legacy_ui_menu_keyconfig, "Controller skin", "", false, actions::chooseSkin);
        controlsValue = item(card, R.drawable.legacy_ui_menu_softinput, "Touch controls", "On", false, actions::toggleControls);
        View line = new View(context);
        line.setBackgroundColor(ArcadeUi.NIGHT_EDGE);
        card.addView(line, new LinearLayout.LayoutParams(-1, ui.dp(1)));
        item(card, R.drawable.legacy_ui_menu_quit, "Back to library", null, false, actions::exit);
        return scrim;
    }

    private TextView item(LinearLayout card, int icon, String label, String value, boolean primary, Runnable onClick) {
        LinearLayout row = ui.row();
        int fill = primary ? ArcadeUi.BLUE : ArcadeUi.NIGHT;
        row.setBackground(ui.tappable(fill, fill, 12, true));
        row.setPadding(ui.dp(10), ui.dp(7), ui.dp(10), ui.dp(7));
        row.setMinimumHeight(ui.dp(48));
        ImageView image = ui.art(icon);
        image.setColorFilter(primary ? Color.WHITE : ArcadeUi.NIGHT_DIM);
        row.addView(image, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
        TextView text = ui.text(label, 15, Color.WHITE, true);
        LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(0, -2, 1);
        space.leftMargin = ui.dp(10);
        row.addView(text, space);
        TextView current = null;
        if (value != null) {
            current = ui.text(value, 12, ArcadeUi.NIGHT_DIM, true);
            current.setGravity(Gravity.CENTER);
            current.setPadding(ui.dp(7), ui.dp(4), ui.dp(7), ui.dp(4));
            current.setBackground(ui.surface(0xff1a2c43, 0xff1a2c43, 6));
            row.addView(current);
        }
        if (Build.VERSION.SDK_INT >= 28) row.setScreenReaderFocusable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> onClick.run());
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return current;
    }

    private final class Stage extends FrameLayout {
        final SurfaceView pixels;
        final TouchDeck deck;
        final TextView loading;
        private final View dock;
        private final LinearLayout title;
        private final ImageButton pause;
        private PadLayout layout;
        private int previousWidth = -1, previousHeight = -1;
        private float previousDensity;
        private boolean previousControls, controlsVisible;

        Stage(Context context, String name, PadSkin skin, boolean showControls, Runnable openMenu) {
            super(context);
            setFocusableInTouchMode(true);
            setMotionEventSplittingEnabled(true);
            controlsVisible = showControls;
            dock = new View(context);
            dock.setBackground(ui.gradient(0xff1b344e, 0xff0e1a2a, 0));
            addView(dock);
            pixels = new SurfaceView(context);
            pixels.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(pixels);

            loading = ui.text("Loading FBNeo…", 15, ArcadeUi.NIGHT_DIM, true);
            loading.setGravity(Gravity.CENTER);
            loading.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
            addView(loading);
            title = ui.column();
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.addView(ui.eyebrow("NOW PLAYING", ArcadeUi.NIGHT_DIM));
            ui.space(title, 3);
            TextView gameName = ui.text(name, 16, Color.WHITE, true);
            ArcadeUi.singleLine(gameName);
            title.addView(gameName);
            addView(title);
            deck = new TouchDeck(context, skin);
            addView(deck);

            pause = ui.icon(R.drawable.legacy_classic_button_pause, "Pause game", 0xaa101e30, 0, 14, 6);
            Drawable frame = TouchDeck.buttonArt(context, R.drawable.legacy_classic_button_pause, R.drawable.legacy_classic_button_pause_p);
            pause.setImageDrawable(frame);
            pause.setOnClickListener(v -> openMenu.run());
            addView(pause);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(w, h);
            float density = getResources().getDisplayMetrics().density;
            if (layout == null || w != previousWidth || h != previousHeight || density != previousDensity || previousControls != controlsVisible) {
                layout = new PadLayout(w, h, density, controlsVisible);
                previousWidth = w;
                previousHeight = h;
                previousDensity = density;
                previousControls = controlsVisible;
                deck.setGeometry(layout);
                title.setVisibility(layout.overlay ? GONE : VISIBLE);
            }
            TouchDeck.measureExactly(dock, w, h - layout.dockTop);
            TouchDeck.measureExactly(pixels, layout.video.w, layout.video.h);
            TouchDeck.measureExactly(loading, layout.video.w, layout.video.h);
            TouchDeck.measureExactly(title, layout.title.w, layout.title.h);
            TouchDeck.measureExactly(deck, w, h);
            PadLayout.Box menuBox = layout.controls[PadLayout.MENU];
            TouchDeck.measureExactly(pause, menuBox.w, menuBox.h);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            TouchDeck.position(dock, 0, layout.dockTop);
            TouchDeck.position(pixels, layout.video.x, layout.video.y);
            TouchDeck.position(loading, layout.video.x, layout.video.y);
            TouchDeck.position(title, layout.title.x, layout.title.y);
            TouchDeck.position(deck, 0, 0);
            PadLayout.Box menuBox = layout.controls[PadLayout.MENU];
            TouchDeck.position(pause, menuBox.x, menuBox.y);
        }
    }
}
