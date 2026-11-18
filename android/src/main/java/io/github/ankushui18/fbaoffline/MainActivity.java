package io.github.ankushui18.fbaoffline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Insets;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
import android.util.SparseIntArray;
import android.view.DisplayCutout;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

import static io.github.ankushui18.fbaoffline.Joypad.directions;

public final class MainActivity extends Activity implements GameSession.Callbacks {
    private static final int PICK_ZIPS = 101, PICK_FOLDER = 102, PICK_ART = 103, EXPORT_SHOT = 104;
    private static final int MAX_BATCH = 64, FOLDER_LIMIT = 256, HISTORY_LIMIT = 24;
    private static final String RECENT_FILES = "recent_roms", FAVORITES = "favorites";
    private static final String[] PAD_MODES = {"Custom", "Auto (game buttons)", "Compact (fixed)"};
    private static final String[] FILTERS = {"Sharp pixels", "Smooth (bilinear)", "HQ2x", "HQ3x", "HQ4x"};
    private static final String[] SKIPS = {"Off", "Skip 1", "Skip 2", "Skip 3", "Skip 4", "Auto"};
    private static final String[] FACE = {"A", "B", "C", "D", "E", "F"};
    private static final String[] ASPECTS = {"Fit game aspect", "Fill screen", "Force 4:3", "Force 3:4", "Integer scale"};
    private static final String[] ORIENTATIONS = {"Follow device", "Landscape", "Reverse landscape", "Portrait", "Reverse portrait"};
    private static final int[] ORIENTATION_CODES = {ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT};
    private final ExecutorService importer = Executors.newSingleThreadExecutor(task -> new Thread(task, "rom-import"));
    private File roms, systemDir, saveDir, currentFile;
    private GameSession game;
    private GameScreen player;
    private PadEditor editor;
    private LibraryScreen library;
    private GameCatalog catalog;
    private LibraryTools libraryTools;
    private LinkMenu linkMenu;
    private boolean foreground, importing, muted, touchVisible, loaded;
    private PadSkin skin;
    private SharedPreferences settings;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private String artSet, exportPath;
    private final SparseIntArray ports = new SparseIntArray();

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        File privateData = getNoBackupFilesDir();
        roms = new File(privateData, "roms");
        systemDir = new File(privateData, "system");
        saveDir = new File(privateData, "saves");
        audio = getSystemService(AudioManager.class);
        inputManager = getSystemService(android.hardware.input.InputManager.class);
        if (inputManager != null) inputManager.registerInputDeviceListener(devices, null);
        settings = getSharedPreferences("arcade_ui", MODE_PRIVATE);
        muted = settings.getBoolean("muted", false);
        touchVisible = settings.getBoolean("touch_visible", true);
        refreshSkin();
        if (saved != null) { artSet = saved.getString("artSet"); exportPath = saved.getString("exportPath"); }
        catalog = new GameCatalog(this);
        linkMenu = new LinkMenu(this, catalog);
        library = createLibrary();
        libraryTools = new LibraryTools(this, roms, saved, new LibraryTools.Host() {
            @Override public void play(File rom, PatchStore.Entry patch, int slot) { MainActivity.this.play(rom, patch, slot); }
            @Override public <T> void job(String status, Callable<T> task, Consumer<T> done) { libraryJob(status, task, done); }
            @Override public void note(String message) { showLibrary(message); }
        });
        showLibrary(null);
        catalog.load(library::refreshCatalog);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("artSet", artSet);
        out.putString("exportPath", exportPath);
        libraryTools.save(out);
        super.onSaveInstanceState(out);
    }

    private LibraryScreen createLibrary() {
        LibraryScreen screen = new LibraryScreen(this, catalog, settings, new LibraryScreen.Actions() {
            @Override public void importZips() { chooseZips(); }
            @Override public void importFolder() { chooseFolder(); }
            @Override public void settings() { showSettings(); }
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
        if (game != null) {
            if (game.link != null) closeGame(); // No multiplayer sockets or playback in the background.
            else { game.foreground = false; clearInputs(); }
        }
        updateTouch();
        syncAudioFocus();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && (game != null || editor != null)) applySystemBars();
        if (game == null) return;
        if (!focused) clearInputs();
        updateTouch();
    }

    @Override protected void onDestroy() {
        if (game != null) game.stop();
        abandonFocus();
        importer.shutdownNow();
        if (inputManager != null) inputManager.unregisterInputDeviceListener(devices);
        catalog.close();
        super.onDestroy();
    }

    @Override public void onBackPressed() { goBack(); }

    private void goBack() {
        if (editor != null) closeEditor();
        else if (game != null) togglePause();
        else finish();
    }

    private void applySystemBars() {
        Window window = getWindow();
        boolean playing = game != null || editor != null;
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
        library.show(all, recent, favorites(), importing, note);
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

    private Set<String> favorites() { return settings.getStringSet(FAVORITES, Collections.emptySet()); }

    private void romOptions(View anchor, File file) {
        if (importing || game != null) return;
        PopupMenu menu = new PopupMenu(this, anchor);
        boolean favorite = favorites().contains(file.getName());
        menu.getMenu().add(0, 1, 0, "Game info");
        menu.getMenu().add(0, 2, 1, favorite ? "Remove from Favorites" : "Add to Favorites");
        menu.getMenu().add(0, 3, 2, "Choose thumbnail image");
        if (catalog.cover(GameCatalog.setName(file.getName())).isFile()) menu.getMenu().add(0, 4, 3, "Restore original thumbnail");
        menu.getMenu().add(0, 6, 4, "Save states");
        menu.getMenu().add(0, 7, 5, "IPS patches");
        menu.getMenu().add(0, 8, 6, "Cheat files");
        if (LinkMenu.AVAILABLE) menu.getMenu().add(0, 9, 7, "Local multiplayer · beta");
        menu.getMenu().add(0, 5, 8, "Remove local copy");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: gameInfo(file); break;
                case 2:
                    Set<String> next = new HashSet<>(favorites());
                    if (favorite) next.remove(file.getName()); else next.add(file.getName());
                    settings.edit().putStringSet(FAVORITES, next).apply();
                    showLibrary(null);
                    break;
                case 3: pickArt(GameCatalog.setName(file.getName())); break;
                case 4:
                    showLibrary(catalog.cover(GameCatalog.setName(file.getName())).delete() ? "Original thumbnail restored." : "Couldn't remove the custom image.");
                    break;
                case 6: libraryTools.states(file, null); break;
                case 7: libraryTools.ips(file); break;
                case 8: libraryTools.cheatFiles(file); break;
                case 9: linkMenu.show(file, connection -> play(file, null, 0, connection)); break;
                case 5:
                    new AlertDialog.Builder(this).setTitle("Remove " + file.getName() + "?")
                            .setMessage("The original ZIP and saved games won't be deleted.")
                            .setPositiveButton("Remove", (d, w) -> showLibrary(file.delete() ? "Local copy removed." : "Couldn't remove this ZIP."))
                            .setNegativeButton("Cancel", null).show();
                    break;
                default: return false;
            }
            return true;
        });
        menu.show();
    }

    private void gameInfo(File file) {
        ArcadeUi ui = new ArcadeUi(this);
        GameCatalog.Game found = catalog.game(file);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(17), ui.dp(5), ui.dp(17), ui.dp(7));
        scroll.addView(body);
        ImageView art = ui.art(R.drawable.legacy_ui_games);
        art.setBackground(ui.surface(ArcadeUi.LIGHT_BLUE, ArcadeUi.BORDER, 11));
        art.setPadding(ui.dp(5), ui.dp(5), ui.dp(5), ui.dp(5));
        catalog.showArt(art, found);
        body.addView(art, new LinearLayout.LayoutParams(-1, ui.dp(132)));
        ui.space(body, 11);
        infoLine(body, ui, "ROM set", file.getName() + " · " + Formatter.formatShortFileSize(this, file.length()));
        infoLine(body, ui, "Board", found.board);
        infoLine(body, ui, "Category", found.genre);
        infoLine(body, ui, "Manufacturer", found.company);
        infoLine(body, ui, "Year", found.year);
        infoLine(body, ui, "Parent", found.parent);
        if (!found.dependency.equals(found.parent)) infoLine(body, ui, "Dependency", found.dependency);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(found.title(library.chineseNames())).setView(scroll)
                .setPositiveButton("Close", null);
        if ((game == null || game.link == null) && catalog.cover(found.set).isFile()) dialog.setNeutralButton("Export image", (d, w) -> exportArt(found.set));
        dialog.show();
    }

    private void infoLine(LinearLayout body, ArcadeUi ui, String title, String value) {
        if (value.isEmpty()) return;
        body.addView(ui.text(title, 12, ArcadeUi.BLUE, true));
        ui.space(body, 3);
        body.addView(ui.text(value, 14, ArcadeUi.INK, false));
        ui.space(body, 8);
    }

    private void chooseFolder() {
        if (importing || game != null) return;
        library.hideKeyboard();
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), PICK_FOLDER);
        } catch (android.content.ActivityNotFoundException missing) { showLibrary("This device doesn't have a folder picker."); }
    }

    private void importFolder(Uri tree) {
        List<Uri> chosen = new ArrayList<>();
        String reason = null;
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
            String[] fields = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME};
            try (Cursor list = getContentResolver().query(children, fields, null, null, null)) {
                while (list != null && list.moveToNext() && chosen.size() < FOLDER_LIMIT) {
                    if (Thread.currentThread().isInterrupted()) return;
                    String name = list.getString(1);
                    try { RomStore.archiveName(name); } catch (IOException ignored) { continue; }
                    chosen.add(DocumentsContract.buildDocumentUriUsingTree(tree, list.getString(0)));
                }
                if (chosen.size() == FOLDER_LIMIT) reason = "Imported up to the first " + FOLDER_LIMIT + " ZIPs in the folder.";
            }
        } catch (RuntimeException unavailable) { finishImport("Couldn't read this folder."); return; }
        if (chosen.isEmpty()) finishImport("No ROM ZIPs found in this folder. Choose the folder containing the ZIPs directly.");
        else importZips(chosen, reason);
    }

    private void pickArt(String set) {
        artSet = set;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(pick, PICK_ART); }
        catch (android.content.ActivityNotFoundException unavailable) { artSet = null; toast("No image picker is available."); }
    }

    private void exportArt(String set) { exportImage(catalog.cover(set), set + ".png"); }

    private void exportImage(File image, String name) {
        exportPath = image.getAbsolutePath();
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/png").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, name).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { startActivityForResult(create, EXPORT_SHOT); }
        catch (android.content.ActivityNotFoundException unavailable) { exportPath = null; toast("No file picker is available."); }
    }

    private void chooseZips() {
        if (importing || game != null) return;
        library.hideKeyboard();
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(pick, PICK_ZIPS); }
        catch (android.content.ActivityNotFoundException unavailable) {
            showLibrary("This device doesn't have a system file picker.");
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (!isFinishing() && !isDestroyed()) linkMenu.result(request, permissions, results);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (libraryTools.result(request, result, data)) return;
        if (result != RESULT_OK || data == null) return;
        Uri chosenUri = data.getData();
        if (request == PICK_ART || request == EXPORT_SHOT) {
            File file = request == PICK_ART ? artSet == null ? null : catalog.cover(artSet)
                    : exportPath == null ? null : new File(exportPath);
            artSet = exportPath = null;
            if (file == null || chosenUri == null || !"content".equals(chosenUri.getScheme())) return;
            importer.execute(() -> {
                String message;
                try {
                    if (request == PICK_ART) {
                        try (InputStream in = getContentResolver().openInputStream(chosenUri)) {
                            if (in == null) throw new IOException("Couldn't read the selected image.");
                            ImageFiles.importArt(in, file);
                        }
                        message = "Game thumbnail saved.";
                    } else {
                        try (OutputStream out = getContentResolver().openOutputStream(chosenUri)) {
                            if (out == null) throw new IOException("Couldn't open the export destination.");
                            Files.copy(file.toPath(), out);
                        }
                        message = "Image exported.";
                    }
                } catch (IOException | RuntimeException err) { message = "Couldn't copy this image. Choose a local PNG or JPEG."; }
                final String note = message;
                runOnUiThread(() -> {
                    if (!isDestroyed() && !isFinishing()) {
                        if (game == null) showLibrary(note); else toast(note);
                    }
                });
            });
            return;
        }
        if (importing || game != null) return;
        if (request == PICK_FOLDER) {
            if (chosenUri != null && "content".equals(chosenUri.getScheme())) {
                importing = true;
                showLibrary("Reading ROM ZIPs in the selected folder…");
                importer.execute(() -> importFolder(chosenUri));
            }
            return;
        }
        if (request != PICK_ZIPS) return;
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
        importer.execute(() -> importZips(chosen, null));
    }

    private void importZips(List<Uri> chosen, String folderNote) {
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
                if (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".fba")) {
                    if (why == null) why = "Old .fba cache files aren't supported. Choose the matching FBNeo ZIP instead.";
                    continue;
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
        finishImport("Imported " + added + " of " + chosen.size() + " ZIP(s)." + (why == null ? "" : " " + why)
                + (folderNote == null ? "" : " " + folderNote));
    }

    private void finishImport(String summary) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            importing = false;
            showLibrary(summary);
        });
    }

    private <T> void libraryJob(String status, Callable<T> task, Consumer<T> done) {
        if (importing || game != null || isFinishing()) return;
        importing = true;
        showLibrary(status);
        importer.execute(() -> {
            T value = null;
            String error = null;
            try { value = task.call(); }
            catch (Exception failed) { error = failed.getMessage() == null ? "Couldn't complete this library action." : failed.getMessage(); }
            final T result = value;
            final String reason = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                importing = false;
                showLibrary(reason);
                if (reason == null) done.accept(result);
                else new AlertDialog.Builder(this).setTitle("Library action failed").setMessage(reason).setPositiveButton("OK", null).show();
            });
        });
    }

    private void play(File file) { play(file, null, 0); }

    private void play(File file, PatchStore.Entry patch, int slot) { play(file, patch, slot, null); }

    private void play(File file, PatchStore.Entry patch, int slot, GameLink link) {
        if (game != null || importing || editor != null || !RomStore.isArchive(file)) return;
        if ((!systemDir.isDirectory() && !systemDir.mkdirs()) || (!saveDir.isDirectory() && !saveDir.mkdirs())) {
            showLibrary("Couldn't create private storage for the emulator.");
            return;
        }
        currentFile = file;
        refreshSkin();
        String set = GameCatalog.setName(file.getName());
        String title = catalog.game(file).title(library.chineseNames());
        if (patch != null) title += " · IPS: " + patch.title;
        if (link != null) title += " · Link P" + (link.player() + 1);
        GameScreen screen = new GameScreen(this, title, skin, touchVisible, link, this::gameAction);
        immersiveInsets(screen);
        GameSession session = new GameSession(this, screen.surface(), file, systemDir, saveDir, catalog.cover(set), patch, slot, link, this);
        game = session;
        player = screen;
        screen.attach(session);
        loaded = false;
        session.foreground = foreground;
        setContentView(screen);
        applySettings();
        screen.focusStage();
        applySystemBars();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screen.requestApplyInsets();
        session.start();
    }

    private void immersiveInsets(View screen) {
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
    }

    private void gameAction(GameScreen.Action action) {
        GameSession session = game;
        if (session == null) return;
        if (action == GameScreen.Action.RESUME) { togglePause(); return; }
        if (action == GameScreen.Action.EXIT) { closeGame(); return; }
        if (!loaded) { toast("Wait for FBNeo to finish loading."); return; }
        switch (action) {
            case SAVE:
            case LOAD:
                boolean saving = action == GameScreen.Action.SAVE;
                GamePanels.slots(this, session.states, saving, slot -> {
                    if (saving) submit(session, () -> session.saveSlot(slot), () -> toast("Saved in slot " + slot + "."));
                    else submit(session, () -> session.loadSlot(slot), () -> {
                        setPaused(false); toast("Loaded slot " + slot + ".");
                    });
                });
                break;
            case SCREENSHOT:
                String set = GameCatalog.setName(session.romName());
                submit(session, session::screenshot, () -> new AlertDialog.Builder(this).setTitle("Screenshot saved")
                        .setMessage(session.patch == null ? "The captured frame is now this game's thumbnail." : "The captured frame is now this patch's preview.")
                        .setNeutralButton("Export PNG", (d, w) -> exportImage(session.cover(), set + (session.patch == null ? "" : "-ips") + ".png")).setPositiveButton("OK", null).show());
                break;
            case CHEATS: showCoreOptions(session, true); break;
            case DIPS: showCoreOptions(session, false); break;
            case CONTROLLER: showControls(); break;
            case DISPLAY: showDisplay(); break;
            case KEYS: showKeyBindings(); break;
            case INFO: gameInfo(currentFile); break;
            case RESTART:
                new AlertDialog.Builder(this).setTitle("Restart this game?")
                        .setMessage("Progress since the last save will be lost.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Restart", (d, w) -> submit(session, () -> {
                            if (!Core.reset()) throw new IOException("Couldn't reset this driver.");
                        }, () -> setPaused(false))).show();
                break;
            case PLAYER:
                writeInt("touch_player", (session.touchPort + 1) % 4);
                toast("Touch controls · Player " + (session.touchPort + 1));
                break;
            default: break;
        }
    }

    private void showCoreOptions(GameSession session, boolean cheats) {
        if (!session.post(() -> {
            String[][] options = Core.options();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || game != session || !session.paused) return;
                GamePanels.options(this, cheats ? "Game cheats" : "DIP switches", options, cheats, changes -> {
                    submit(session, () -> {
                        for (Map.Entry<String, Integer> entry : changes.entrySet()) {
                            if (!Core.setOption(entry.getKey(), entry.getValue())) throw new IOException("A choice is no longer available for this driver.");
                        }
                    }, () -> toast(cheats ? "Cheat choices set. Resume to apply." : "Cabinet choices set. Resume to apply."));
                });
            });
        })) toast("Emulator busy. Try again.");
    }

    interface CoreTask { void run() throws IOException; }

    private void submit(GameSession session, CoreTask task, Runnable success) {
        if (!session.post(() -> {
            String message = null;
            try { task.run(); }
            catch (IOException | RuntimeException failure) { message = failure.getMessage() == null ? "Couldn't complete this action." : failure.getMessage(); }
            final String error = message;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || game != session) return;
                if (error != null) new AlertDialog.Builder(this).setTitle("Game action failed")
                        .setMessage(error).setPositiveButton("Close", null).show();
                else success.run();
            });
        })) toast("Emulator busy. Try again.");
    }

    private void togglePause() { if (game != null && editor == null) setPaused(!game.paused); }

    private void setPaused(boolean pause) {
        if (game == null) return;
        game.paused = pause;
        clearInputs();
        player.setPaused(pause);
        updateTouch();
        syncAudioFocus();
    }

    private void closeGame() {
        if (game == null) return;
        if (editor != null) closeEditor();
        clearInputs();
        game.stop();
        game = null;
        player = null;
        currentFile = null;
        ports.clear();
        loaded = false;
        refreshSkin();
        abandonFocus();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
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

    @Override public void waiting(GameSession session, boolean waiting) {
        runOnUiThread(() -> { if (game == session && !isFinishing() && !isDestroyed()) player.peerWait(waiting); });
    }

    @Override public void failed(GameSession session, String reason) {
        runOnUiThread(() -> {
            if (game != session || isFinishing() || isDestroyed()) return;
            closeGame();
            new AlertDialog.Builder(this).setTitle(session.link == null ? "Couldn't play this ZIP" : "Local link ended").setMessage(reason)
                    .setPositiveButton("OK", null).show();
        });
    }

    private boolean canPlay() { return game != null && loaded && editor == null && foreground && hasWindowFocus() && !game.paused; }
    private void updateTouch() { if (player != null) player.setInputEnabled(canPlay()); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

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

    private String gamePrefix() { return currentFile == null ? "" : "game:" + currentFile.getName() + ":"; }
    private boolean custom() { return currentFile != null && settings.getBoolean(gamePrefix() + "custom", false); }
    private String target(String key) { return custom() ? gamePrefix() + key : key; }
    private int readInt(String key, int fallback) { return settings.getInt(target(key), settings.getInt(key, fallback)); }
    private boolean readBool(String key, boolean fallback) { return settings.getBoolean(target(key), settings.getBoolean(key, fallback)); }
    private String readText(String key, String fallback) { return settings.getString(target(key), settings.getString(key, fallback)); }
    private void writeInt(String key, int value) { settings.edit().putInt(target(key), value).apply(); applySettings(); }
    private void writeBool(String key, boolean value) { settings.edit().putBoolean(target(key), value).apply(); applySettings(); }
    private void refreshSkin() {
        skin = PadSkin.stored(readText("skin", PadSkin.CLASSIC_CIRCLE.name()));
        touchVisible = readBool("touch_visible", true);
        muted = readBool("muted", false);
    }

    private static int range(int value, int max) { return Math.max(0, Math.min(max, value)); }
    private void applySettings() {
        refreshSkin();
        if (game == null || player == null) return;
        clearInputs();
        for (int i = 0; i < keyMap.length; i++) keyMap[i] = readInt("key_" + i, 0);
        int[] combos = new int[4];
        for (int i = 0; i < combos.length; i++) combos[i] = readInt("combo_" + i, 0) & HardwareKeys.FACE_MASK;
        game.turboMask = readInt("turbo", 0) & HardwareKeys.FACE_MASK;
        game.turboRate = Math.max(2, Math.min(30, readInt("turbo_rate", 10)));
        boolean linked = game.link != null;
        game.touchPort = linked ? game.link.player() : range(readInt("touch_player", 0), 3);
        game.filter = imageFilter();
        game.gpu = readBool("gpu", false);
        game.mirrorHardware = !linked && readBool("mirror_keys", false);
        game.displayMode = range(readInt("aspect", 0), ASPECTS.length - 1);
        int skip = range(readInt("frame_skip", 0), SKIPS.length - 1);
        game.frameSkip = skip == SKIPS.length - 1 ? -1 : skip;
        player.setSkin(skin);
        player.setControlsVisible(touchVisible);
        player.setPlayer(game.touchPort);
        player.setFps(readBool("show_fps", false));
        player.setPad(PadProfile.decode(readText("pad_portrait", null)), PadProfile.decode(readText("pad_landscape", null)),
                combos, !linked && readBool("player_button", false), range(readInt("pad_mode", 0), 2), readBool("full_screen_pad", false));
        player.setFeedback(readBool("pad_animation", true), readBool("pad_haptic", false));
        int requested = ORIENTATION_CODES[range(readInt("orientation", 0), ORIENTATION_CODES.length - 1)];
        if (getRequestedOrientation() != requested) setRequestedOrientation(requested);
        updateTouch();
        syncAudioFocus();
    }

    private void showSettings() {
        if (editor != null) return;
        String[] items = {"Virtual controls", "Display & audio", "Physical keys", "Import ROM folder", "Quick guide", "Core license", "Reset game settings"};
        new AlertDialog.Builder(this).setTitle("Settings · Global defaults").setItems(items, (d, which) -> {
            switch (which) {
                case 0: showControls(); break;
                case 1: showDisplay(); break;
                case 2: showKeyBindings(); break;
                case 3: chooseFolder(); break;
                case 4: showGuide(); break;
                case 5: showLicense(); break;
                case 6: resetSettings(); break;
                default: break;
            }
        }).setNegativeButton("Close", null).show();
    }

    private AlertDialog.Builder settingsPanel(String title, Runnable reopened) {
        AlertDialog.Builder panel = new AlertDialog.Builder(this)
                .setTitle(title + " · " + (custom() ? "This game" : "Global")).setNegativeButton("Close", null);
        if (currentFile != null) panel.setNeutralButton(custom() ? "Use global" : "For this game", (d, w) -> {
            if (!custom()) {
                settings.edit().putBoolean(gamePrefix() + "custom", true).apply();
                applySettings(); reopened.run();
            } else {
                new AlertDialog.Builder(this).setTitle("Use global defaults for this game?")
                        .setMessage("This clears this game's overrides, not its saves or screenshots.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Use global", (dialog, which) -> {
                            String prefix = gamePrefix();
                            SharedPreferences.Editor edit = settings.edit();
                            if (!prefix.isEmpty()) for (String key : settings.getAll().keySet()) if (key.startsWith(prefix)) edit.remove(key);
                            edit.apply(); applySettings(); reopened.run();
                        }).show();
            }
        });
        return panel;
    }

    private void showControls() {
        String[] menu = {"Controller skin · " + skin.title, "Edit layout · drag and resize",
                "Touch controls · " + status(touchVisible), "Combo buttons · 1–4", "Turbo / autofire",
                "Touch player · " + (range(readInt("touch_player", 0), 3) + 1),
                "Show player selector · " + status(readBool("player_button", false)),
                "Button animation · " + status(readBool("pad_animation", true)),
                "Haptic feedback · " + status(readBool("pad_haptic", false)),
                "Layout mode · " + PAD_MODES[range(readInt("pad_mode", 0), 2)]};
        settingsPanel("Virtual controls", this::showControls).setItems(menu, (d, i) -> {
            switch (i) {
                case 0: showSkinPicker(); break;
                case 1: openEditor(); break;
                case 2: writeBool("touch_visible", !touchVisible); showControls(); break;
                case 3: showCombos(); break;
                case 4: showTurbo(); break;
                case 5:
                    if (game != null && game.link != null) { toast("Assigned by the local link session."); break; }
                    choice("Touch player", new String[] {"Player 1", "Player 2", "Player 3", "Player 4"}, range(readInt("touch_player", 0), 3),
                            n -> writeInt("touch_player", n)); break;
                case 6: writeBool("player_button", !readBool("player_button", false)); showControls(); break;
                case 7: writeBool("pad_animation", !readBool("pad_animation", true)); showControls(); break;
                case 8: writeBool("pad_haptic", !readBool("pad_haptic", false)); showControls(); break;
                case 9: choice("Layout mode", PAD_MODES, range(readInt("pad_mode", 0), 2), n -> writeInt("pad_mode", n)); break;
                default: break;
            }
        }).show();
    }

    private String status(boolean value) { return value ? "On" : "Off"; }

    private int imageFilter() { return range(readInt("image_filter", readBool("smooth", false) ? 1 : 0), FILTERS.length - 1); }

    private void showDisplay() {
        int aspect = range(readInt("aspect", 0), ASPECTS.length - 1), orient = range(readInt("orientation", 0), ORIENTATIONS.length - 1);
        int drop = range(readInt("frame_skip", 0), SKIPS.length - 1), filter = imageFilter();
        boolean fps = readBool("show_fps", false), gpu = readBool("gpu", false), full = readBool("full_screen_pad", false);
        String[] menu = {"Screen size · " + ASPECTS[aspect], "Orientation · " + ORIENTATIONS[orient],
                "Image filter · " + FILTERS[filter], "Frameskip · " + SKIPS[drop],
                "FPS counter · " + status(fps), "Sound · " + status(!muted),
                "GPU canvas · " + status(gpu), "Full-screen portrait · " + status(full)};
        settingsPanel("Display & audio", this::showDisplay).setItems(menu, (d, i) -> {
            switch (i) {
                case 0: choice("Screen size", ASPECTS, aspect, n -> writeInt("aspect", n)); break;
                case 1: choice("Orientation", ORIENTATIONS, orient, n -> writeInt("orientation", n)); break;
                case 2: choice("Image filter", FILTERS, filter, n -> writeInt("image_filter", n)); break;
                case 3: choice("Render frameskip", SKIPS, drop, n -> writeInt("frame_skip", n)); break;
                case 4: writeBool("show_fps", !fps); showDisplay(); break;
                case 5: writeBool("muted", !muted); showDisplay(); break;
                case 6: writeBool("gpu", !gpu); if (game != null) toast("Renderer changes on the next game launch."); showDisplay(); break;
                case 7: writeBool("full_screen_pad", !full); showDisplay(); break;
                default: break;
            }
        }).show();
    }

    private void choice(String title, String[] labels, int selected, IntConsumer update) {
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, selected,
                (dialog, which) -> { dialog.dismiss(); update.accept(which); }).setNegativeButton("Cancel", null).show();
    }

    private void showCombos() {
        String[] choices = new String[4];
        for (int i = 0; i < choices.length; i++) {
            int mask = readInt("combo_" + i, 0);
            List<String> names = new ArrayList<>();
            for (int j = 0; j < FACE.length; j++) if ((mask & HardwareKeys.FACE_BITS[j]) != 0) names.add(FACE[j]);
            choices[i] = "Combo " + (i + 1) + " · " + (names.isEmpty() ? "Hidden" : String.join(" + ", names));
        }
        choice("Combo buttons", choices, -1, i -> faceButtons("Combo " + (i + 1), "combo_" + i));
    }

    private void showTurbo() {
        int rate = readInt("turbo_rate", 10);
        choice("Turbo / autofire", new String[] {"Choose buttons to repeat", "Repeat speed · " + rate + " Hz"}, -1, i -> {
            if (i == 0) faceButtons("Repeat these buttons while held", "turbo");
            else choice("Repeat speed", new String[] {"5 Hz", "10 Hz", "15 Hz", "20 Hz"}, Math.max(0, Math.min(3, rate / 5 - 1)),
                    n -> writeInt("turbo_rate", (n + 1) * 5));
        });
    }

    private void faceButtons(String title, String key) {
        int mask = readInt(key, 0);
        boolean[] selected = new boolean[FACE.length];
        for (int i = 0; i < selected.length; i++) selected[i] = (mask & HardwareKeys.FACE_BITS[i]) != 0;
        new AlertDialog.Builder(this).setTitle(title).setMultiChoiceItems(FACE, selected, (d, n, checked) -> selected[n] = checked)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
                    int next = 0;
                    for (int i = 0; i < selected.length; i++) if (selected[i]) next |= HardwareKeys.FACE_BITS[i];
                    writeInt(key, next);
                }).show();
    }

    private void openEditor() {
        if (editor != null) return;
        if (game == null) library.hideKeyboard();
        else if (!game.paused) setPaused(true);
        String portrait = target("pad_portrait"), landscape = target("pad_landscape"), mode = target("pad_mode");
        PadProfile tall = PadProfile.decode(readText("pad_portrait", null)), wide = PadProfile.decode(readText("pad_landscape", null));
        editor = new PadEditor(this, skin, tall, wide, readBool("full_screen_pad", false), new PadEditor.Actions() {
            @Override public void save(PadProfile portraitDraft, PadProfile landscapeDraft) {
                settings.edit().putString(portrait, portraitDraft.encode()).putString(landscape, landscapeDraft.encode()).putInt(mode, PadLayout.CUSTOM).apply();
                applySettings(); closeEditor(); toast("Control layout saved.");
            }
            @Override public void cancel() { closeEditor(); }
        });
        immersiveInsets(editor);
        if (player != null) player.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        else library.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addContentView(editor, new FrameLayout.LayoutParams(-1, -1));
        applySystemBars();
        editor.requestApplyInsets(); editor.requestFocus();
    }

    private void closeEditor() {
        if (editor == null) return;
        ((android.view.ViewGroup) editor.getParent()).removeView(editor);
        editor = null;
        if (player != null) { player.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO); player.setPaused(game.paused); }
        else { library.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO); library.requestFocus(); library.requestApplyInsets(); }
        applySystemBars();
        updateTouch();
    }

    private void showSkinPicker() {
        ArcadeUi ui = new ArcadeUi(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(17), ui.dp(5), ui.dp(17), ui.dp(6));
        scroll.addView(body);
        body.addView(ui.text("Original FBA4droid artwork", 14, ArcadeUi.MUTED, false));
        ui.space(body, 11);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Controller skin").setView(scroll)
                .setNegativeButton("Close", null).create();
        for (PadSkin candidate : PadSkin.values()) {
            boolean selected = candidate == skin;
            LinearLayout option = ui.column();
            option.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
            option.setBackground(ui.tappable(selected ? ArcadeUi.LIGHT_BLUE : ArcadeUi.WHITE,
                    selected ? ArcadeUi.BLUE : ArcadeUi.BORDER, 12, false));
            option.setFocusable(true);
            option.setSelected(selected);
            option.setContentDescription(candidate.title + ". " + candidate.detail);
            PadPreview preview = new PadPreview(this, candidate, false);
            preview.setBackground(ui.gradient(0xff253e56, ArcadeUi.NIGHT, 8));
            option.addView(preview, new LinearLayout.LayoutParams(-1, ui.dp(61)));
            ui.space(option, 6);
            option.addView(ui.text(candidate.title + (selected ? "  ✓" : ""), 15, ArcadeUi.INK, true));
            option.addView(ui.text(candidate.detail, 12, ArcadeUi.MUTED, false));
            option.setOnClickListener(v -> { settings.edit().putString(target("skin"), candidate.name()).apply(); applySettings(); dialog.dismiss(); });
            body.addView(option, new LinearLayout.LayoutParams(-1, -2));
            ui.space(body, 7);
        }
        dialog.show();
    }

    private void showGuide() {
        new AlertDialog.Builder(this).setTitle("How to play")
                .setMessage("1. Import ROM ZIPs or choose the folder containing them. Keep the FBNeo names and import any parent or BIOS ZIPs alongside them. No ROMs or BIOS are included.\n\n"
                        + "2. Tap a game, then tap Coin and Start. Touch controls and connected gamepads can be used together.\n\n"
                        + "3. Tap Pause for cheats, save slots and game settings. Under Controls → Edit layout, drag each control and change its size. Rotate to edit the other layout.\n\n"
                        + "4. Open the ⋮ menu on a game row to manage states, import custom cheats, or launch a separate IPS variant.\n\n"
                        + "Independent FinalBurn Neo frontend. Non-commercial use only.")
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

    private void showKeyBindings() {
        int keys = HardwareKeys.NAMES.length;
        String[] labels = new String[keys + 2];
        for (int i = 0; i < keys; i++) labels[i] = HardwareKeys.NAMES[i] + " · " + HardwareKeys.label(i, readInt("key_" + i, 0));
        labels[keys] = "Physical controls · " + status(readBool("physical_keys", true));
        labels[keys + 1] = "Mirror to all local players · " + status(readBool("mirror_keys", false));
        settingsPanel("Physical keys", this::showKeyBindings).setItems(labels, (d, i) -> {
            if (i < keys) listenForKey(i);
            else {
                String key = i == keys ? "physical_keys" : "mirror_keys";
                writeBool(key, !readBool(key, i == keys)); showKeyBindings();
            }
        }).show();
    }

    private void resetSettings() {
        new AlertDialog.Builder(this).setTitle("Reset game settings?")
                .setMessage("Restore all game and global controls, display and audio defaults. Keeps ROMs, saves, patches, cheats, thumbnails, favorites and history.")
                .setNegativeButton("Cancel", null).setPositiveButton("Reset", (d, w) -> {
                    SharedPreferences.Editor reset = settings.edit();
                    for (String key : settings.getAll().keySet()) {
                        if (!key.equals("tiles") && !key.equals("sorting") && !key.equals("chinese_names")
                                && !key.equals(FAVORITES) && !key.equals(RECENT_FILES)) reset.remove(key);
                    }
                    reset.apply();
                    refreshSkin(); showLibrary("Game settings reset.");
                }).show();
    }

    private void listenForKey(int index) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Bind " + HardwareKeys.NAMES[index])
                .setMessage("Press a gamepad button or keyboard key. Connected gamepads use separate player ports; the keyboard uses Player 1. Escape or the controller's Mode button pauses the game.")
                .setNeutralButton("Default", (d, w) -> writeInt("key_" + index, 0))
                .setPositiveButton("Unbind", (d, w) -> writeInt("key_" + index, -1)).setNegativeButton("Cancel", null).create();
        dialog.setOnKeyListener((d, code, event) -> {
            if (!HardwareKeys.canBind(code)) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) {
                SharedPreferences.Editor edit = settings.edit();
                for (int i = 0; i < HardwareKeys.NAMES.length; i++) {
                    if (i != index && readInt("key_" + i, 0) == code) edit.putInt(target("key_" + i), -1);
                }
                edit.putInt(target("key_" + index), code).apply();
                applySettings();
                dialog.dismiss();
            }
            return true;
        });
        dialog.show();
    }

    private int port(InputEvent event) {
        if (game != null && game.link != null) return game.link.player();
        InputDevice device = event.getDevice();
        if (device == null || !(device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))) return 0;
        int id = device.getId();
        int existing = ports.get(id, -1);
        if (existing >= 0) return existing;
        for (int i = 0; i < 4; i++) if (ports.indexOfValue(i) < 0) { ports.put(id, i); return i; }
        return -1;
    }

    private static final class Pressed {
        final int port;
        final SparseIntArray buttons = new SparseIntArray();
        int axis;
        Pressed(int port) { this.port = port; }
    }
    private final android.util.SparseArray<Pressed> hardware = new android.util.SparseArray<>();
    private final int[] keyMap = new int[HardwareKeys.NAMES.length];
    private android.hardware.input.InputManager inputManager;
    private final android.hardware.input.InputManager.InputDeviceListener devices = new android.hardware.input.InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) {}
        @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }
        @Override public void onInputDeviceRemoved(int id) {
            ports.delete(id);
            hardware.remove(id);
            publishHardware();
        }
    };

    private void clearInputs() {
        hardware.clear();
        if (game != null) game.resetInputs();
    }

    private Pressed pressed(int id, int port) {
        Pressed state = hardware.get(id);
        if (state == null) { state = new Pressed(port); hardware.put(id, state); }
        return state;
    }

    private void publishHardware() {
        if (game == null) return;
        for (int p = 0; p < 4; p++) {
            int digital = 0, axis = 0;
            for (int i = 0; i < hardware.size(); i++) {
                Pressed state = hardware.valueAt(i);
                if (state.port != p) continue;
                for (int j = 0; j < state.buttons.size(); j++) digital |= state.buttons.valueAt(j);
                axis |= state.axis;
            }
            game.keys.set(p, digital);
            game.stick.set(p, axis);
        }
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (canPlay() && readBool("physical_keys", true) && event.getActionMasked() == MotionEvent.ACTION_MOVE && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            int port = port(event);
            if (port < 0) return true;
            int hat = directions(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), .5f);
            int axis = directions(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), .32f);
            pressed(event.getDeviceId(), port).axis = hat == 0 ? axis : hat;
            publishHardware();
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (game != null && editor == null && (key == KeyEvent.KEYCODE_ESCAPE || key == KeyEvent.KEYCODE_BUTTON_MODE)) {
            if (event.getAction() == KeyEvent.ACTION_UP) togglePause();
            return true;
        }
        if (!canPlay() || !readBool("physical_keys", true)) return super.dispatchKeyEvent(event);
        int button = HardwareKeys.mask(key, keyMap);
        if (button == 0) return super.dispatchKeyEvent(event);
        int port = port(event);
        if (port < 0) return true;
        Pressed state = pressed(event.getDeviceId(), port);
        if (event.getAction() == KeyEvent.ACTION_DOWN) state.buttons.put(key, button);
        if (event.getAction() == KeyEvent.ACTION_UP) state.buttons.delete(key);
        publishHardware();
        return true;
    }
}
