package io.github.ankushui18.fbaoffline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.github.ankushui18.fbaoffline.GameSession.*;

public final class MainActivity extends Activity implements GameSession.Callbacks {
    private static final int PICK_ZIPS = 101, MAX_BATCH = 24;
    private static final int BG = 0xff101318, PANEL = 0xff1b2129, EDGE = 0xff34424c;
    private static final int TEXT = 0xffeef5f3, DIM = 0xffaebec5, MINT = 0xff73f2bd, AMBER = 0xfff8c275;
    private static final int DIRECTIONS = UP | DOWN | LEFT | RIGHT;
    private final ExecutorService importer = Executors.newSingleThreadExecutor(task -> new Thread(task, "rom-import"));
    private File roms, systemDir, saveDir;
    private GameSession game;
    private boolean foreground, importing, muted;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private TextView loading;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }
        File privateData = getNoBackupFilesDir();
        roms = new File(privateData, "roms");
        systemDir = new File(privateData, "system");
        saveDir = new File(privateData, "saves");
        audio = getSystemService(AudioManager.class);
        showLibrary(null);
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        if (game != null) game.foreground = true;
        syncAudioFocus();
    }

    @Override protected void onPause() {
        foreground = false;
        if (game != null) { game.foreground = false; game.resetInputs(); }
        syncAudioFocus();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (game != null) game.stop();
        abandonFocus();
        importer.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() { goBack(); }

    private void goBack() {
        if (game != null) closeGame();
        else finish();
    }

    private LinearLayout page() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                // Before API 30 the decor already fits the system windows.
                view.setPadding(0, 0, 0, 0);
            }
            return insets;
        });
        return root;
    }

    private void showLibrary(String note) {
        LinearLayout root = page();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(14), dp(20), dp(28));
        scroll.addView(content);

        LinearLayout bar = row();
        TextView brand = label("FBA / OFFLINE", 13, MINT, true);
        brand.setLetterSpacing(0.16f);
        bar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button guide = button("Guide", PANEL, TEXT);
        guide.setOnClickListener(v -> showGuide());
        bar.addView(guide, new LinearLayout.LayoutParams(dp(82), dp(48)));
        content.addView(bar);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(surface(0xff192b2e, 0xff31524c, 22));
        gap(content, 16);
        content.addView(hero);
        TextView tagline = label("LOCAL ARCADE  /  FBNEO", 11, MINT, true);
        tagline.setLetterSpacing(0.12f);
        hero.addView(tagline);
        gap(hero, 8);
        hero.addView(label("Your arcade, offline.", 31, TEXT, true));
        gap(hero, 6);
        hero.addView(label("Import ROM sets you own. No accounts, ads, or network access.", 15, DIM, false));
        gap(hero, 18);
        Button add = button(importing ? "Importing…" : "+  Import ZIP", MINT, BG);
        add.setEnabled(!importing);
        add.setOnClickListener(v -> chooseZips());
        hero.addView(add, new LinearLayout.LayoutParams(-1, dp(52)));

        if (note != null) {
            gap(content, 14);
            TextView notice = label(note, 14, AMBER, false);
            notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            content.addView(notice);
        }

        File[] entries = roms.listFiles(RomStore::isArchive);
        if (entries == null) entries = new File[0];
        Arrays.sort(entries, Comparator.comparing(File::getName));
        gap(content, 24);
        LinearLayout heading = row();
        heading.addView(label("Your library", 21, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(label(entries.length + (entries.length == 1 ? " ZIP" : " ZIPs"), 13, DIM, false));
        content.addView(heading);
        gap(content, 10);
        if (entries.length == 0) {
            TextView empty = label("No ROMs yet\n\nChoose a local FBNeo-compatible ZIP and keep its original set name. If a game needs a parent ROM or BIOS, import that ZIP too.", 15, DIM, false);
            empty.setPadding(dp(18), dp(18), dp(18), dp(18));
            empty.setBackground(surface(PANEL, EDGE, 16));
            content.addView(empty);
        } else {
            for (File file : entries) addRomRow(content, file);
        }
        gap(content, 14);
        TextView footer = label("Private storage · FinalBurn Neo 1.0.0.03", 12, DIM, false);
        content.addView(footer);
        setContentView(root);
        root.requestApplyInsets();
    }

    private void addRomRow(LinearLayout content, File file) {
        LinearLayout item = row();
        item.setBackground(surface(PANEL, EDGE, 14));
        item.setPadding(dp(12), dp(6), dp(6), dp(6));
        TextView name = label(file.getName(), 16, TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        Button play = button("Play", 0xff25463e, MINT);
        play.setContentDescription("Play " + file.getName());
        play.setEnabled(!importing);
        play.setOnClickListener(v -> play(file));
        item.addView(play, new LinearLayout.LayoutParams(dp(64), dp(48)));
        Button more = button("⋮", PANEL, DIM);
        more.setContentDescription("Options for " + file.getName());
        more.setEnabled(!importing);
        more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, more);
            menu.getMenu().add("Remove local copy");
            menu.setOnMenuItemClickListener(m -> {
                new AlertDialog.Builder(this).setTitle("Remove " + file.getName() + "?")
                        .setMessage("The original file you picked and local game saves are not deleted.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove", (d, w) -> showLibrary(file.delete() ? "Local copy removed." : "Couldn't remove this ZIP."))
                        .show();
                return true;
            });
            menu.show();
        });
        item.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(item);
        gap(content, 8);
    }

    private void chooseZips() {
        if (importing || game != null) return;
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
        LinearLayout root = page();
        LinearLayout bar = row();
        bar.setPadding(dp(6), 0, dp(6), 0);
        Button back = button("‹ Library", BG, MINT);
        back.setOnClickListener(v -> closeGame());
        bar.addView(back, new LinearLayout.LayoutParams(dp(86), dp(48)));
        TextView title = label(file.getName(), 15, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button pause = button("Pause", PANEL, TEXT);
        Button sound = button(muted ? "Muted" : "Sound", PANEL, TEXT);
        bar.addView(pause, new LinearLayout.LayoutParams(dp(65), dp(48)));
        bar.addView(sound, new LinearLayout.LayoutParams(dp(65), dp(48)));
        root.addView(bar);

        FrameLayout display = new FrameLayout(this);
        display.setBackgroundColor(Color.BLACK);
        SurfaceView pixels = new SurfaceView(this);
        display.addView(pixels, new FrameLayout.LayoutParams(-1, -1));
        loading = label("Loading FBNeo…", 15, DIM, true);
        loading.setGravity(Gravity.CENTER);
        display.addView(loading, new FrameLayout.LayoutParams(-1, -1));
        root.addView(display, new LinearLayout.LayoutParams(-1, 0, 1));

        GameSession session = new GameSession(pixels.getHolder(), file, systemDir, saveDir, this);
        game = session;
        root.addView(controls(session));
        pause.setOnClickListener(v -> {
            session.paused = !session.paused;
            session.resetInputs();
            pause.setText(session.paused ? "Resume" : "Pause");
            syncAudioFocus();
        });
        sound.setOnClickListener(v -> {
            muted = !muted;
            sound.setText(muted ? "Muted" : "Sound");
            syncAudioFocus();
        });
        root.setFocusableInTouchMode(true);
        setContentView(root);
        root.requestFocus();
        root.requestApplyInsets();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        session.foreground = foreground;
        syncAudioFocus();
        session.start();
    }

    private void closeGame() {
        if (game == null) return;
        game.stop();
        game = null;
        loading = null;
        abandonFocus();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showLibrary(null);
    }

    @Override public void ready(GameSession session) {
        runOnUiThread(() -> {
            if (game == session && loading != null && !isDestroyed()) loading.setVisibility(View.GONE);
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

    private LinearLayout controls(GameSession session) {
        LinearLayout deck = row();
        deck.setPadding(dp(8), dp(4), dp(8), dp(4));
        deck.setMotionEventSplittingEnabled(true);
        deck.addView(new Dpad(session), new LinearLayout.LayoutParams(dp(104), dp(104)));
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setMotionEventSplittingEnabled(true);
        center.addView(control(session, "Coin", COIN, PANEL, AMBER), new LinearLayout.LayoutParams(-1, dp(48)));
        center.addView(control(session, "Start", START, PANEL, MINT), new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, dp(104), 1);
        middle.setMargins(dp(6), 0, dp(6), 0);
        deck.addView(center, middle);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setMotionEventSplittingEnabled(true);
        for (int i = 0; i < 2; i++) {
            LinearLayout line = row();
            line.setMotionEventSplittingEnabled(true);
            String[] labels = i == 0 ? new String[] {"X", "Y", "L"} : new String[] {"A", "B", "R"};
            int[] masks = i == 0 ? new int[] {X, Y, L} : new int[] {A, B, R};
            for (int j = 0; j < 3; j++) {
                LinearLayout.LayoutParams key = new LinearLayout.LayoutParams(dp(48), dp(48));
                key.setMargins(dp(2), dp(2), 0, 0);
                line.addView(control(session, labels[j], masks[j], 0xff25463e, MINT), key);
            }
            right.addView(line);
        }
        deck.addView(right, new LinearLayout.LayoutParams(-2, -2));
        return deck;
    }

    private Button control(GameSession session, String text, int mask, int bg, int ink) {
        Button b = button(text, bg, ink);
        b.setFocusable(false);
        Runnable release = () -> session.touch.getAndUpdate(value -> value & ~mask);
        b.setOnClickListener(v -> { // Accessibility activation also produces a short press.
            b.removeCallbacks(release);
            session.touch.getAndUpdate(value -> value | mask);
            b.postDelayed(release, 55);
        });
        b.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    b.removeCallbacks(release);
                    b.setPressed(true);
                    session.touch.getAndUpdate(value -> value | mask);
                    break;
                case MotionEvent.ACTION_UP:
                    b.setPressed(false);
                    if (event.getEventTime() - event.getDownTime() < 28) b.performClick();
                    else release.run();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    b.setPressed(false);
                    release.run();
                    break;
                default:
                    break;
            }
            return true;
        });
        return b;
    }

    private final class Dpad extends View {
        private final GameSession session;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int pointer = -1;

        Dpad(GameSession session) {
            super(MainActivity.this);
            this.session = session;
            setContentDescription("Directional pad. Slide your thumb in a direction.");
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), x = w / 2, y = h / 2, r = Math.min(w, h) / 6;
            paint.setColor(EDGE);
            c.drawRoundRect(0, y - r, w, y + r, dp(9), dp(9), paint);
            c.drawRoundRect(x - r, 0, x + r, h, dp(9), dp(9), paint);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(17));
            int dirs = session.touch.get();
            arrow(c, "▲", x, r, dirs, UP);
            arrow(c, "▼", x, h - r, dirs, DOWN);
            arrow(c, "◀", r, y, dirs, LEFT);
            arrow(c, "▶", w - r, y, dirs, RIGHT);
        }

        private void arrow(Canvas c, String text, float x, float y, int dirs, int dir) {
            paint.setColor((dirs & dir) == 0 ? TEXT : MINT);
            c.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked(), index = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN) pointer = event.getPointerId(index);
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP ||
                    (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(index) == pointer)) pointer = -1;
            int active = pointer < 0 ? -1 : event.findPointerIndex(pointer);
            final int dirs = active < 0 ? 0 : directions(2 * event.getX(active) / getWidth() - 1,
                    2 * event.getY(active) / getHeight() - 1, 0.29f);
            session.touch.getAndUpdate(value -> (value & ~DIRECTIONS) | dirs);
            invalidate();
            if (action == MotionEvent.ACTION_UP) performClick();
            return true;
        }

        @Override public boolean performClick() { return super.performClick(); }
    }

    private static int directions(float x, float y, float threshold) {
        int d = 0;
        if (x < -threshold) d |= LEFT;
        if (x > threshold) d |= RIGHT;
        if (y < -threshold) d |= UP;
        if (y > threshold) d |= DOWN;
        return d;
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (game != null && event.getActionMasked() == MotionEvent.ACTION_MOVE && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            int hat = directions(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), 0.5f);
            int axis = directions(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), 0.32f);
            game.stick.set(hat != 0 ? hat : axis);
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (game == null) return super.dispatchKeyEvent(event);
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

    private void showGuide() {
        new AlertDialog.Builder(this).setTitle("Play offline")
                .setMessage("1. Choose Import ZIP and pick files stored on your device. No games or BIOS are bundled.\n\n"
                        + "2. Keep the original FBNeo set name. For a clone or a game that needs a BIOS, import the required ZIPs alongside it. Importing the same name replaces the local copy.\n\n"
                        + "3. Tap Play. Use Coin and Start, then the touch buttons or an Android gamepad. Old FBA4droid saves and settings are not imported.\n\n"
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

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }

    private TextView label(String string, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(string);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private Button button(String string, int fill, int ink) {
        Button button = new Button(this);
        button.setText(string);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(ink);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44ffffff), surface(fill, fill, 12), null));
        return button;
    }

    private GradientDrawable surface(int fill, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), border);
        return d;
    }
}
