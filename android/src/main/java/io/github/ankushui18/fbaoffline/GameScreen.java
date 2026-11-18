package io.github.ankushui18.fbaoffline;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
    enum Action { RESUME, SAVE, LOAD, SCREENSHOT, CHEATS, DIPS, CONTROLLER, DISPLAY, KEYS, RESTART, INFO, EXIT, PLAYER }
    interface Actions { void invoke(Action action); }

    private final ArcadeUi ui;
    private final Stage stage;
    private final FrameLayout menu;
    private GameSession session;

    GameScreen(Context context, String name, PadSkin skin, boolean touchVisible, GameLink link, Actions actions) {
        super(context);
        ui = new ArcadeUi(context);
        setBackgroundColor(Color.BLACK);
        stage = new Stage(context, name, skin, touchVisible, actions);
        addView(stage, new LayoutParams(-1, -1));
        if (link != null) stage.loading.setText(link.status() + "\nOpen Pause → Exit link to cancel.");
        menu = pauseMenu(context, name, actions, link != null);
        addView(menu, new LayoutParams(-1, -1));
        menu.setVisibility(GONE);
        setControlsVisible(touchVisible);
        setSkin(skin);
    }

    SurfaceHolder surface() { return stage.pixels.getHolder(); }
    void attach(GameSession session) { this.session = session; stage.deck.attach(session); }
    void ready() { stage.loading.setVisibility(GONE); stage.layout = null; stage.requestLayout(); }
    void peerWait(boolean waiting) {
        stage.loading.setText("Other player paused");
        stage.loading.setVisibility(waiting ? VISIBLE : GONE);
    }
    void setInputEnabled(boolean enabled) { stage.deck.setInputEnabled(enabled && stage.controlsVisible); }
    void setSkin(PadSkin skin) { stage.skin = skin; stage.deck.setSkin(skin); stage.playerArt(); }
    void setFeedback(boolean animated, boolean haptic) { stage.deck.setFeedback(animated, haptic); }
    void setPlayer(int port) { stage.touchPlayer = port; stage.playerArt(); }
    void setPad(PadProfile portrait, PadProfile landscape, int[] comboBits, boolean showPlayer, int mode, boolean fullScreen) {
        stage.profiles = new PadProfile[]{portrait, landscape};
        stage.combos = comboBits;
        stage.showPlayer = showPlayer;
        stage.mode = mode;
        stage.fullScreen = fullScreen;
        stage.layout = null;
        stage.requestLayout();
    }

    void setControlsVisible(boolean visible) {
        stage.controlsVisible = visible;
        stage.deck.setVisibility(visible ? VISIBLE : GONE);
        stage.requestLayout();
    }

    void setFps(boolean show) {
        stage.fps.setVisibility(show ? VISIBLE : GONE);
        stage.removeCallbacks(stage.fpsRefresh);
        if (show) stage.post(stage.fpsRefresh);
    }

    void setPaused(boolean paused) {
        stage.setImportantForAccessibility(paused ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        menu.setVisibility(paused ? VISIBLE : GONE);
        if (paused) menu.requestFocus();
        else stage.requestFocus();
    }

    void focusStage() { stage.requestFocus(); }

    private FrameLayout pauseMenu(Context context, String name, Actions actions, boolean linked) {
        FrameLayout scrim = new FrameLayout(context);
        scrim.setBackgroundColor(0xc507111e);
        scrim.setClickable(true);
        scrim.setFocusableInTouchMode(true);
        FrameLayout bound = ui.widthLimit(460);
        LayoutParams centered = new LayoutParams(-1, -2, Gravity.CENTER);
        centered.setMargins(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9));
        scrim.addView(bound, centered);
        ScrollView scroll = new ScrollView(context);
        scroll.setBackground(ui.surface(ArcadeUi.NIGHT, ArcadeUi.NIGHT_EDGE, 18));
        scroll.setClipToOutline(true);
        scroll.setVerticalScrollBarEnabled(false);
        bound.addView(scroll, new LayoutParams(-1, -2));
        LinearLayout card = ui.column();
        card.setPadding(ui.dp(12), ui.dp(13), ui.dp(12), ui.dp(10));
        scroll.addView(card);
        LinearLayout heading = ui.row();
        LinearLayout title = ui.column();
        title.addView(ui.eyebrow("GAME MENU", 0xff8fcbff));
        ui.space(title, 4);
        TextView nameLabel = ui.text(name, 19, Color.WHITE, true);
        ArcadeUi.singleLine(nameLabel);
        title.addView(nameLabel);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Button resume = ui.button("Resume", ArcadeUi.BLUE, Color.WHITE, 10);
        resume.setOnClickListener(v -> actions.invoke(Action.RESUME));
        heading.addView(resume, new LinearLayout.LayoutParams(-2, ui.dp(44)));
        card.addView(heading);
        ui.space(card, 8);
        if (linked) {
            grid(card, actions, new String[]{"Controls", "Display", "Keys", "Game info", "Exit link"},
                    new int[]{R.drawable.legacy_ui_menu_softinput, R.drawable.legacy_ui_menu_list, R.drawable.legacy_ui_menu_keyconfig,
                            R.drawable.legacy_ui_menu_gameinfo, R.drawable.legacy_ui_menu_quit},
                    new Action[]{Action.CONTROLLER, Action.DISPLAY, Action.KEYS, Action.INFO, Action.EXIT});
            return scrim;
        }
        grid(card, actions, new String[]{"Save", "Load", "Screenshot", "Cheats", "DIP switches", "Controls", "Display", "Keys",
                        "Restart", "Game info", "Exit game"},
                new int[]{R.drawable.legacy_ui_menu_savestate, R.drawable.legacy_ui_menu_loadstate, R.drawable.legacy_ui_menu_screenshot,
                        R.drawable.legacy_ui_menu_cheat, R.drawable.legacy_ui_menu_dipswitch, R.drawable.legacy_ui_menu_softinput,
                        R.drawable.legacy_ui_menu_list, R.drawable.legacy_ui_menu_keyconfig, R.drawable.legacy_ui_menu_restart,
                        R.drawable.legacy_ui_menu_gameinfo, R.drawable.legacy_ui_menu_quit},
                new Action[]{Action.SAVE, Action.LOAD, Action.SCREENSHOT, Action.CHEATS, Action.DIPS, Action.CONTROLLER,
                        Action.DISPLAY, Action.KEYS, Action.RESTART, Action.INFO, Action.EXIT});
        return scrim;
    }

    private void grid(LinearLayout card, Actions actions, String[] labels, int[] icons, Action[] ids) {
        for (int i = 0; i < labels.length; i += 3) {
            LinearLayout line = ui.row();
            for (int j = i; j < i + 3; j++) {
                LinearLayout tile = ui.column();
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(ui.dp(2), ui.dp(7), ui.dp(2), ui.dp(7));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
                p.setMargins(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(3));
                if (j < labels.length) {
                    Action target = ids[j];
                    tile.setMinimumHeight(ui.dp(62));
                    tile.setBackground(ui.tappable(0xff1b3149, ArcadeUi.NIGHT_EDGE, 9, true));
                    ImageView image = ui.art(icons[j]);
                    image.setColorFilter(0xffb8defd);
                    tile.addView(image, new LinearLayout.LayoutParams(ui.dp(23), ui.dp(23)));
                    ui.space(tile, 5);
                    TextView label = ui.text(labels[j], 12, Color.WHITE, true);
                    label.setGravity(Gravity.CENTER);
                    tile.addView(label, new LinearLayout.LayoutParams(-1, -2));
                    tile.setContentDescription(labels[j]);
                    tile.setFocusable(true);
                    tile.setOnClickListener(v -> actions.invoke(target));
                }
                line.addView(tile, p);
            }
            card.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private final class Stage extends FrameLayout {
        final SurfaceView pixels;
        final TouchDeck deck;
        final TextView loading;
        private final View dock;
        private final LinearLayout title;
        private final ImageButton pause, player;
        private final TextView fps;
        private final Runnable fpsRefresh = new Runnable() {
            @Override public void run() {
                if (fps.getVisibility() != VISIBLE) return;
                int n = session == null ? 0 : session.measuredFps;
                fps.setText(n > 0 ? n + " FPS" : "— FPS");
                postDelayed(this, 650);
            }
        };
        private PadSkin skin;
        private PadProfile[] profiles = {new PadProfile(), new PadProfile()};
        private int[] combos = new int[4];
        private boolean showPlayer, fullScreen;
        private int mode;
        private int touchPlayer;
        private PadLayout layout;
        private int previousWidth = -1, previousHeight = -1;
        private float previousDensity;
        private boolean previousControls, controlsVisible;

        Stage(Context context, String name, PadSkin skin, boolean showControls, Actions actions) {
            super(context);
            setFocusableInTouchMode(true);
            setMotionEventSplittingEnabled(true);
            controlsVisible = showControls;
            this.skin = skin;
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
            pause.setOnClickListener(v -> actions.invoke(Action.RESUME));
            player = ui.icon(skin.player[0], "Change touch player", Color.TRANSPARENT, 0, 10, 0);
            player.setOnClickListener(v -> actions.invoke(Action.PLAYER));
            addView(player);
            addView(pause);
            fps = ui.eyebrow("— FPS", 0xff8bc6ff);
            fps.setGravity(Gravity.CENTER);
            fps.setBackground(ui.surface(0x99101e30, 0x99101e30, 7));
            fps.setVisibility(GONE);
            addView(fps);
        }

        void playerArt() {
            player.setImageResource(skin.player[touchPlayer]);
            player.setContentDescription("Touch player " + (touchPlayer + 1) + ". Tap to change.");
        }

        @Override protected void onDetachedFromWindow() {
            removeCallbacks(fpsRefresh);
            super.onDetachedFromWindow();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(w, h);
            float density = getResources().getDisplayMetrics().density;
            if (layout == null || w != previousWidth || h != previousHeight || density != previousDensity || previousControls != controlsVisible) {
                layout = new PadLayout(w, h, density, controlsVisible, profiles[w > h ? 1 : 0], combos, showPlayer, false, mode, session == null ? 0 : session.inputMask(touchPlayer), fullScreen);
                pause.setAlpha(layout.opacity);
                player.setAlpha(layout.opacity);
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
            PadLayout.Box playerBox = layout.controls[PadLayout.PLAYER];
            TouchDeck.measureExactly(player, playerBox.w, playerBox.h);
            TouchDeck.measureExactly(fps, Math.min(w, ui.dp(66)), Math.min(h, ui.dp(23)));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            TouchDeck.position(dock, 0, layout.dockTop);
            TouchDeck.position(pixels, layout.video.x, layout.video.y);
            TouchDeck.position(loading, layout.video.x, layout.video.y);
            TouchDeck.position(title, layout.title.x, layout.title.y);
            TouchDeck.position(deck, 0, 0);
            PadLayout.Box menuBox = layout.controls[PadLayout.MENU];
            TouchDeck.position(pause, menuBox.x, menuBox.y);
            PadLayout.Box playerBox = layout.controls[PadLayout.PLAYER];
            TouchDeck.position(player, playerBox.x, playerBox.y);
            TouchDeck.position(fps, Math.max(0, (getWidth() - fps.getMeasuredWidth()) / 2), layout.video.y + ui.dp(3));
        }
    }
}
