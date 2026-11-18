package io.github.ankushui18.fbaoffline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Insets;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
import android.view.DisplayCutout;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.github.ankushui18.fbaoffline.Joypad.*;

public final class MainActivity extends Activity implements GameSession.Callbacks {
    private static final int PICK_ZIPS = 101, MAX_BATCH = 24, HISTORY_LIMIT = 24;
    private static final String RECENT_FILES = "recent_roms";
    private final ExecutorService importer = Executors.newSingleThreadExecutor(task -> new Thread(task, "rom-import"));
    private File roms, systemDir, saveDir;
    private GameSession game;
    private GameScreen player;
    private LibraryScreen library;
    private boolean foreground, importing, muted, touchVisible, loaded;
    private PadSkin skin;
    private SharedPreferences settings;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }
        File privateData = getNoBackupFilesDir();
        roms = new File(privateData, "roms");
        systemDir = new File(privateData, "system");
        saveDir = new File(privateData, "saves");
        audio = getSystemService(AudioManager.class);
        settings = getSharedPreferences("arcade_ui", MODE_PRIVATE);
        skin = PadSkin.CLASSIC.name().equals(settings.getString("skin", "")) ? PadSkin.CLASSIC : PadSkin.CHROME;
        muted = settings.getBoolean("muted", false);
        touchVisible = settings.getBoolean("touch_visible", true);
        library = createLibrary();
        showLibrary(null);
    }

    private LibraryScreen createLibrary() {
        LibraryScreen screen = new LibraryScreen(this, skin, new LibraryScreen.Actions() {
            @Override public void importZips() { chooseZips(); }
            @Override public void settings() { showSkinPicker(); }
            @Override public void play(File file) { MainActivity.this.play(file); }
            @Override public void options(View anchor, File file) { romOptions(anchor, file); }
        });
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                screen.setInsets(safe.left, safe.top, safe.right, safe.bottom);
            }
            return insets;
        });
        return screen;
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        if (game != null) game.foreground = true;
        updateTouch();
        syncAudioFocus();
    }

    @Override protected void onPause() {
        foreground = false;
        if (game != null) { game.foreground = false; game.resetInputs(); }
        updateTouch();
        syncAudioFocus();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (game == null) return;
        if (focused) applySystemBars();
        else game.resetInputs();
        updateTouch();
    }

    @Override protected void onDestroy() {
        if (game != null) game.stop();
        abandonFocus();
        importer.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() { goBack(); }

    private void goBack() {
        if (game != null) togglePause();
        else finish();
    }

    private void applySystemBars() {
        Window window = getWindow();
        boolean playing = game != null;
        window.setStatusBarColor(playing ? Color.BLACK : ArcadeUi.NAVY);
        window.setNavigationBarColor(playing ? Color.BLACK : ArcadeUi.PAGE);
        window.setSoftInputMode(playing ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            int cutout = playing || Build.VERSION.SDK_INT >= 30 ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            if (attrs.layoutInDisplayCutoutMode != cutout) {
                attrs.layoutInDisplayCutoutMode = cutout;
                window.setAttributes(attrs);
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(playing ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                if (playing) {
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.systemBars());
                } else controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            window.getDecorView().setSystemUiVisibility(playing ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE : View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void showLibrary(String note) {
        File[] files = roms.listFiles(RomStore::isArchive);
        List<File> all = Collections.emptyList();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            all = Arrays.asList(files);
        }
        Map<String, File> byName = new HashMap<>();
        for (File file : all) byName.put(file.getName(), file);
        List<File> recent = new ArrayList<>();
        for (String name : settings.getString(RECENT_FILES, "").split("\n")) {
            File file = byName.get(name);
            if (file != null) recent.add(file);
        }
        library.show(all, recent, importing, note);
        if (library.getParent() == null) {
            setContentView(library);
            applySystemBars();
            library.requestFocus();
            library.requestApplyInsets();
        }
    }

    private void rememberPlayed(String name) {
        LinkedHashSet<String> latest = new LinkedHashSet<>();
        latest.add(name);
        for (String previous : settings.getString(RECENT_FILES, "").split("\n")) {
            if (latest.size() == HISTORY_LIMIT) break;
            if (!previous.isEmpty()) latest.add(previous);
        }
        settings.edit().putString(RECENT_FILES, String.join("\n", latest)).apply();
    }

    private void romOptions(View anchor, File file) {
        if (importing || game != null) return;
        PopupMenu options = new PopupMenu(this, anchor);
        options.getMenu().add(0, 1, 0, "ROM details");
        options.getMenu().add(0, 2, 1, "Remove local copy");
        options.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                new AlertDialog.Builder(this).setTitle(file.getName())
                        .setMessage("Local ROM archive · " + Formatter.formatFileSize(this, file.length())
                                + "\n\nStored privately on this device. The original file is unchanged.")
                        .setPositiveButton("Close", null).show();
            } else if (item.getItemId() == 2) {
                new AlertDialog.Builder(this).setTitle("Remove " + file.getName() + "?")
                        .setMessage("The original file you picked and local game saves are not deleted.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove", (d, w) -> showLibrary(file.delete() ? "Local copy removed." : "Couldn't remove this ZIP."))
                        .show();
            }
            return true;
        });
        options.show();
    }

    private void chooseZips() {
        if (importing || game != null) return;
        library.hideSearchKeyboard();
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(pick, PICK_ZIPS); }
        catch (android.content.ActivityNotFoundException unavailable) {
            showLibrary("This device doesn't have a system file picker.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_ZIPS || result != RESULT_OK || data == null || importing || game != null) return;
        LinkedHashSet<Uri> urls = new LinkedHashSet<>();
        ClipData set = data.getClipData();
        if (data.getData() != null) urls.add(data.getData());
        if (set != null) {
            for (int i = 0; i < Math.min(set.getItemCount(), MAX_BATCH + 1); i++) {
                Uri uri = set.getItemAt(i).getUri();
                if (uri != null) urls.add(uri);
            }
        }
        if (urls.isEmpty()) return;
        if (urls.size() > MAX_BATCH || (set != null && set.getItemCount() > MAX_BATCH)) {
            showLibrary("Select up to " + MAX_BATCH + " ZIPs at a time.");
            return;
        }
        List<Uri> chosen = new ArrayList<>(urls);
        importing = true;
        showLibrary("Checking and copying " + chosen.size() + (chosen.size() == 1 ? " file…" : " files…"));
        importer.execute(() -> importZips(chosen));
    }

    private void importZips(List<Uri> chosen) {
        int added = 0;
        String why = null;
        for (Uri uri : chosen) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                if (!"content".equals(uri.getScheme())) throw new IOException("Choose a file through the system picker.");
                String name = null;
                try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                }
                RomStore.archiveName(name);
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("Couldn't read the selected file.");
                    RomStore.importZip(roms.toPath(), name, in);
                }
                added++;
            } catch (java.util.zip.ZipException malformed) {
                if (why == null) why = "Select ZIP archives that your device can open.";
            } catch (IOException | RuntimeException failure) {
                if (why == null) why = "Check that the ZIP is local, correctly named and within 512 MiB, and that storage is available.";
            }
        }
        final String summary = "Imported " + added + " of " + chosen.size() + " ZIP(s)." + (why == null ? "" : " " + why);
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            importing = false;
            showLibrary(summary);
        });
    }

    private void play(File file) {
        if (game != null || importing || !RomStore.isArchive(file)) return;
        if ((!systemDir.isDirectory() && !systemDir.mkdirs()) || (!saveDir.isDirectory() && !saveDir.mkdirs())) {
            showLibrary("Couldn't create private storage for the emulator.");
            return;
        }
        GameScreen screen = new GameScreen(this, file.getName(), skin, touchVisible, muted, new GameScreen.Actions() {
            @Override public void togglePause() { MainActivity.this.togglePause(); }
            @Override public void toggleSound() {
                muted = !muted;
                settings.edit().putBoolean("muted", muted).apply();
                if (player != null) player.setMuted(muted);
                syncAudioFocus();
            }
            @Override public void chooseSkin() { showSkinPicker(); }
            @Override public void toggleControls() {
                touchVisible = !touchVisible;
                settings.edit().putBoolean("touch_visible", touchVisible).apply();
                if (player != null) player.setControlsVisible(touchVisible);
                updateTouch();
            }
            @Override public void exit() { closeGame(); }
        });
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.displayCutout() | WindowInsets.Type.mandatorySystemGestures());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout safe = insets.getDisplayCutout();
                if (safe == null) view.setPadding(0, 0, 0, 0);
                else view.setPadding(safe.getSafeInsetLeft(), safe.getSafeInsetTop(), safe.getSafeInsetRight(), safe.getSafeInsetBottom());
            }
            return insets;
        });
        GameSession session = new GameSession(screen.surface(), file, systemDir, saveDir, this);
        game = session;
        player = screen;
        screen.attach(session);
        loaded = false;
        setContentView(screen);
        screen.focusStage();
        applySystemBars();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screen.requestApplyInsets();
        session.foreground = foreground;
        syncAudioFocus();
        session.start();
    }

    private void togglePause() {
        if (game == null) return;
        game.paused = !game.paused;
        game.resetInputs();
        player.setPaused(game.paused);
        updateTouch();
        syncAudioFocus();
    }

    private void closeGame() {
        if (game == null) return;
        game.stop();
        game.resetInputs();
        game = null;
        player = null;
        loaded = false;
        abandonFocus();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showLibrary(null);
    }

    @Override public void ready(GameSession session) {
        runOnUiThread(() -> {
            if (game != session || isFinishing() || isDestroyed()) return;
            loaded = true;
            rememberPlayed(session.romName());
            player.ready();
            updateTouch();
        });
    }

    @Override public void failed(GameSession session, String reason) {
        runOnUiThread(() -> {
            if (game != session || isFinishing() || isDestroyed()) return;
            closeGame();
            new AlertDialog.Builder(this).setTitle("Couldn't play this ZIP").setMessage(reason)
                    .setPositiveButton("OK", null).show();
        });
    }

    private boolean canPlay() { return game != null && loaded && foreground && hasWindowFocus() && !game.paused; }
    private void updateTouch() { if (player != null) player.setInputEnabled(canPlay()); }

    private void syncAudioFocus() {
        if (game == null || audio == null || muted || !foreground || game.paused) {
            if (game != null) game.soundAllowed = false;
            abandonFocus();
            return;
        }
        if (focusRequest != null) return;
        GameSession session = game;
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(GameSession.AUDIO)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (game == session) session.soundAllowed = change == AudioManager.AUDIOFOCUS_GAIN && foreground && !muted && !session.paused;
                }).build();
        session.soundAllowed = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (audio != null && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        focusRequest = null;
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (canPlay() && event.getActionMasked() == MotionEvent.ACTION_MOVE && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            int hat = directions(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), 0.5f);
            int axis = directions(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), 0.32f);
            game.stick.set(hat != 0 ? hat : axis);
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!canPlay()) return super.dispatchKeyEvent(event);
        final int button = gameButton(event.getKeyCode());
        if (button == 0) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) game.keys.getAndUpdate(value -> value | button);
        if (event.getAction() == KeyEvent.ACTION_UP) game.keys.getAndUpdate(value -> value & ~button);
        return true;
    }

    private static int gameButton(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return RIGHT;
            case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_Z: return A;
            case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_X: return B;
            case KeyEvent.KEYCODE_BUTTON_X: case KeyEvent.KEYCODE_A: return X;
            case KeyEvent.KEYCODE_BUTTON_Y: case KeyEvent.KEYCODE_S: return Y;
            case KeyEvent.KEYCODE_BUTTON_L1: case KeyEvent.KEYCODE_Q: return L;
            case KeyEvent.KEYCODE_BUTTON_R1: case KeyEvent.KEYCODE_W: return R;
            case KeyEvent.KEYCODE_BUTTON_START: case KeyEvent.KEYCODE_ENTER: return START;
            case KeyEvent.KEYCODE_BUTTON_SELECT: case KeyEvent.KEYCODE_SPACE: return COIN;
            default: return 0;
        }
    }

    private void showSkinPicker() {
        ArcadeUi ui = new ArcadeUi(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(20), ui.dp(7), ui.dp(20), ui.dp(6));
        scroll.addView(body);
        body.addView(ui.text("Original FBA4droid artwork. Choose the look you prefer.", 14, ArcadeUi.MUTED, false));
        ui.space(body, 12);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Controller skin").setView(scroll)
                .setPositiveButton("Done", null);
        if (game == null) builder.setNeutralButton("How to play", (d, w) -> showGuide());
        AlertDialog dialog = builder.create();
        for (PadSkin candidate : PadSkin.values()) {
            boolean selected = candidate == skin;
            LinearLayout option = ui.column();
            option.setPadding(ui.dp(10), ui.dp(9), ui.dp(10), ui.dp(9));
            option.setBackground(ui.tappable(selected ? ArcadeUi.LIGHT_BLUE : ArcadeUi.WHITE,
                    selected ? ArcadeUi.BLUE : ArcadeUi.BORDER, 13, false));
            option.setFocusable(true);
            option.setSelected(selected);
            option.setContentDescription(candidate.title + ". " + candidate.detail);
            PadPreview sample = new PadPreview(this, candidate, false);
            sample.setBackground(ui.gradient(0xff253e56, ArcadeUi.NIGHT, 9));
            option.addView(sample, new LinearLayout.LayoutParams(-1, ui.dp(60)));
            ui.space(option, 8);
            LinearLayout title = ui.row();
            title.addView(ui.text(candidate.title, 16, ArcadeUi.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
            if (selected) title.addView(ui.eyebrow("IN USE", ArcadeUi.BLUE));
            option.addView(title);
            ui.space(option, 3);
            option.addView(ui.text(candidate.detail, 13, ArcadeUi.MUTED, false));
            option.setOnClickListener(v -> {
                skin = candidate;
                settings.edit().putString("skin", skin.name()).apply();
                library.showSkin(skin);
                if (player != null) player.setSkin(skin);
                dialog.dismiss();
            });
            body.addView(option, new LinearLayout.LayoutParams(-1, -2));
            ui.space(body, 9);
        }
        dialog.show();
    }

    private void showGuide() {
        new AlertDialog.Builder(this).setTitle("Play offline")
                .setMessage("1. Choose Import ZIP and pick files stored on your device. No games or BIOS are bundled.\n\n"
                        + "2. Keep the original FBNeo set names. Import any required parent or BIOS ZIPs alongside the game. Importing the same name replaces the local copy.\n\n"
                        + "3. Tap a ROM to play. Tap Coin, then Start. Hold the D-pad and action buttons together, or use an Android gamepad.\n\n"
                        + "4. Use Pause to change the skin, sound or touch controls, or go back to the library. This app doesn't import old FBA4droid saves or settings.\n\n"
                        + "This independent app uses FinalBurn Neo. Non-commercial use only.")
                .setPositiveButton("Got it", null).setNeutralButton("Core license", (d, w) -> showLicense()).show();
    }

    private void showLicense() {
        StringBuilder text = new StringBuilder();
        try (Reader reader = new InputStreamReader(getAssets().open("FBNeo-LICENSE.txt"), StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) text.append(buffer, 0, count);
        } catch (IOException missing) {
            text.append("See the upstream FinalBurn Neo source for the complete license.");
        }
        new AlertDialog.Builder(this).setTitle("FinalBurn Neo license").setMessage(text).setPositiveButton("Close", null).show();
    }

}
