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
import java.time.LocalDate;
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
    private static final int PICK_GAMES = 101, PICK_FOLDER = 102, PICK_ART = 103, EXPORT_SHOT = 104, PICK_SKIN = 105;
    private static final int EXPORT_BACKUP = 106, RESTORE_BACKUP = 107, PICK_HISCORE_DATA = 108;
    private static final int MAX_BATCH = 64, HISTORY_LIMIT = 24;
    private static final String RECENT_FILES = "recent_roms", FAVORITES = "favorites", QUICK_RESUME = "quick_resume_enabled";
    private static final String[] PAD_MODES = {"Custom", "Auto (game buttons)", "Compact (fixed)"};
    private static final String[] FILTERS = {"Sharp pixels", "Smooth (bilinear)", "HQ2x", "HQ3x", "HQ4x"};
    private static final String[] SKIPS = {"Off", "Skip 1", "Skip 2", "Skip 3", "Skip 4", "Auto"};
    private static final String[] FACE = {"A", "B", "C", "D", "E", "F"};
    private static final String[] ASPECTS = {"Fit game aspect", "Fill screen", "Force 4:3", "Force 3:4", "Integer scale", "Original size (1×)"};
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
    private RomFolders romFolders;
    private BackupDocuments backups;
    private QuickResumeStore quickResume;
    private SkinStore skins;
    private LinkMenu linkMenu;
    private boolean foreground, importing, muted, touchVisible, loaded, exiting;
    private PadArt skin;
    private String skinSelection;
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
        backups = new BackupDocuments(this, settings);
        quickResume = new QuickResumeStore(privateData);
        skins = new SkinStore(new File(privateData, "controller-skins"));
        refreshSkin();
        if (saved != null) { artSet = saved.getString("artSet"); exportPath = saved.getString("exportPath"); }
        catalog = new GameCatalog(this);
        linkMenu = new LinkMenu(this, catalog);
        library = createLibrary();
        libraryTools = new LibraryTools(this, roms, saved, new LibraryTools.Host() {
            @Override public void play(File rom, PatchStore.Entry patch, int slot) { MainActivity.this.play(rom, patch, slot); }
            @Override public <T> void job(String status, Callable<T> task, Consumer<T> done) { libraryJob(status, task, done); }
            @Override public void note(String message) { showLibrary(message); }
            @Override public void info(File rom) { gameInfo(rom); }
        });
        romFolders = new RomFolders(this, settings, new RomFolders.Host() {
            @Override public <T> void job(String status, Callable<T> task, Consumer<T> done) { libraryJob(status, task, done); }
            @Override public void choose() { chooseTree(PICK_FOLDER); }
            @Override public void copy(List<Uri> files, String note) { startGameImport(files, note); }
            @Override public void note(String text) { showLibrary(text); }
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
            @Override public void pickGames() { chooseGames(); }
            @Override public void browseFolder() { browseRoms(); }
            @Override public void settings() { showSettings(); }
            @Override public void play(File file) { MainActivity.this.play(file); }
            @Override public void continueGame(QuickResumeStore.Entry entry) { continueQuick(entry); }
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
        window.setStatusBarColor(playing ? Color.BLACK : ArcadeUi.PAGE);
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
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(playing ? 0 : light, light);
                if (playing) {
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.systemBars());
                } else controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            window.getDecorView().setSystemUiVisibility(playing ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void showLibrary(String note) {
        File[] files = roms.listFiles(file -> RomStore.isArchive(file) && !ConsoleStore.isFirmware(file.getName()));
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
        QuickResumeStore.Entry checkpoint = quickResumeEnabled() ? quickResume.latest() : null;
        library.show(all, recent, favorites(), checkpoint, importing, note);
        if (library.getParent() == null) {
            setContentView(library);
            applySystemBars();
            library.scrollToTop();
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
        boolean console = ConsoleSystem.fromArchive(file.getName()) != null;
        if (LinkMenu.AVAILABLE && !console) menu.getMenu().add(0, 9, 0, "Local multiplayer");
        menu.getMenu().add(0, 6, 1, "Save states");
        menu.getMenu().add(0, 2, 2, favorite ? "Remove from Favorites" : "Add to Favorites");
        menu.getMenu().add(0, 1, 3, "Game info");
        if (!console) menu.getMenu().add(0, 7, 4, "IPS patches");
        menu.getMenu().add(0, 8, 5, "Cheat files");
        menu.getMenu().add(0, 3, 6, "Choose thumbnail image");
        if (catalog.cover(GameCatalog.setName(file.getName())).isFile()) menu.getMenu().add(0, 4, 7, "Restore original thumbnail");
        if (settings.contains(CoreProfile.key(file.getName()))) menu.getMenu().add(0, 10, 8, "Reset FBNeo settings");
        menu.getMenu().add(0, 5, 9, "Remove local copy");
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
                case 10: resetCoreProfile(file); break;
                case 5:
                    new AlertDialog.Builder(this).setTitle("Remove " + catalog.game(file).title(library.chineseNames()) + "?")
                            .setMessage("The original file and saved games won't be deleted.")
                            .setPositiveButton("Remove", (d, w) -> showLibrary(file.delete() ? "Local copy removed." : "Couldn't remove the local copy."))
                            .setNegativeButton("Cancel", null).show();
                    break;
                default: return false;
            }
            return true;
        });
        menu.show();
    }

    private String coreProfile(String rom) { return settings.getString(CoreProfile.key(rom), ""); }

    private void resetCoreProfile(File file) {
        new AlertDialog.Builder(this).setTitle("Reset this game's FBNeo settings?")
                .setMessage("Restores default core settings without deleting saves or changing controls. States saved with custom settings still need those same choices.")
                .setNegativeButton("Cancel", null).setPositiveButton("Reset", (d, w) -> {
                    settings.edit().remove(CoreProfile.key(file.getName())).apply();
                    showLibrary("FBNeo settings reset for " + catalog.game(file).title(library.chineseNames()) + ".");
                }).show();
    }

    private void gameInfo(File file) {
        if (game != null) { showGameInfo(file, libraryTools.gameNote(file)); return; }
        library.hideKeyboard();
        libraryJob("Reading game info…", () -> libraryTools.gameNote(file), note -> showGameInfo(file, note));
    }

    private void showGameInfo(File file, String note) {
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
        infoLine(body, ui, "Local note", note);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(found.title(library.chineseNames())).setView(scroll)
                .setPositiveButton("Close", null);
        if (game == null) dialog.setNegativeButton("Local note", (d, w) -> libraryTools.noteActions(file, !note.isEmpty()));
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

    private void browseRoms() {
        if (importing || game != null) return;
        library.hideKeyboard();
        romFolders.browse();
    }

    private void chooseTree(int request) {
        if (importing || game != null) return;
        library.hideKeyboard();
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (request == PICK_FOLDER) pick.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(pick, request); }
        catch (android.content.ActivityNotFoundException missing) { showLibrary("This device doesn't have a folder picker."); }
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

    private void chooseGames() {
        if (importing || game != null) return;
        library.hideKeyboard();
        // Providers often label .nes as application/octet-stream or use no NES MIME type.
        // Keep the picker unfiltered and let the importer check the selected bytes.
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(pick, PICK_GAMES); }
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
        if (result != RESULT_OK) return;
        if (data == null) {
            if (request == PICK_GAMES) noDocumentSelected();
            return;
        }
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
        if (request == EXPORT_BACKUP || request == RESTORE_BACKUP) {
            if (chosenUri == null || !"content".equals(chosenUri.getScheme())) {
                showLibrary("Choose a file with the Android document picker.");
            } else if (request == EXPORT_BACKUP) {
                libraryJob("Exporting save slots and settings…", () -> backups.exportZip(chosenUri), slots ->
                        showLibrary("Backup ZIP saved · " + slots + (slots == 1 ? " save slot" : " save slots") + " + settings. Keep a copy outside the app."));
            } else confirmRestore(chosenUri);
            return;
        }
        if (request == PICK_HISCORE_DATA) {
            if (chosenUri == null || !"content".equals(chosenUri.getScheme())) { showLibrary("Choose a file in the Android picker."); return; }
            libraryJob("Checking high score data…", () -> {
                GameSession.awaitIdle();
                try (InputStream in = getContentResolver().openInputStream(chosenUri)) { HighScoreData.install(systemDir, in); }
                return null;
            }, unused -> showLibrary("Verified high score data installed. Enable it in Pause → FBNeo settings for games that support it."));
            return;
        }
        if (request == PICK_FOLDER) { romFolders.picked(chosenUri); return; }
        if (request == PICK_SKIN) {
            libraryJob("Copying controller skin…", () -> skins.install(getContentResolver(), chosenUri), added -> {
                selectSkin(added.key(added.roundByDefault()));
                showLibrary("Controller skin imported: " + added.title);
                showImportedSkin(added);
            });
            return;
        }
        if (request != PICK_GAMES) return;
        LinkedHashSet<Uri> urls = new LinkedHashSet<>();
        ClipData set = data.getClipData();
        if (data.getData() != null) urls.add(data.getData());
        if (set != null) {
            for (int i = 0; i < Math.min(set.getItemCount(), MAX_BATCH + 1); i++) {
                Uri uri = set.getItemAt(i).getUri();
                if (uri != null) urls.add(uri);
            }
        }
        if (urls.isEmpty()) {
            noDocumentSelected();
            return;
        }
        if (urls.size() > MAX_BATCH || (set != null && set.getItemCount() > MAX_BATCH)) {
            showLibrary("Select up to " + MAX_BATCH + " game files at a time.");
            return;
        }
        startGameImport(new ArrayList<>(urls), null);
    }

    private void noDocumentSelected() {
        if (!importing && game == null) showLibrary("The picker didn't return a file. Try Downloads or Browse folder.");
    }

    private void startGameImport(List<Uri> chosen, String note) {
        if (importing || game != null || chosen.isEmpty()) return;
        importing = true;
        showLibrary("Checking and copying " + chosen.size() + (chosen.size() == 1 ? " file…" : " files…"));
        boolean chinese = library.chineseNames();
        importer.execute(() -> importGames(chosen, note, chinese));
    }

    private void importGames(List<Uri> chosen, String folderNote, boolean chinese) {
        ImportReport report = new ImportReport(roms);
        ConsoleStore index = null;
        try { index = catalog.consoleSets(); }
        catch (IOException unavailable) { /* An unavailable index must not block arcade ZIP imports. */ }
        for (int i = 0; i < chosen.size(); i++) {
            if (Thread.currentThread().isInterrupted()) return;
            Uri uri = chosen.get(i);
            String name = null, archive = null, error = null;
            try {
                if (!"content".equals(uri.getScheme())) throw new ConsoleStore.Mismatch("Choose a file through the Android picker.");
                name = documentName(uri);
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    archive = GameImport.add(roms.toPath(), name, in, index);
                }
            } catch (IOException | RuntimeException failure) {
                error = GameImport.failureMessage(failure);
            }
            String sourceLabel = name == null ? "Selected file " + (i + 1) : name;
            if (archive == null) {
                report.notAdded(sourceLabel, error);
            } else {
                // A provider close error does not undo an already committed private copy.
                catalog.rememberCartridgeName(archive, name);
                report.saved(sourceLabel, archive, catalog.game(new File(roms, archive)).title(chinese));
            }
        }
        finishImport(report, folderNote);
    }

    private String documentName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name;
            }
        } catch (RuntimeException ignored) { /* Some providers grant data but don't expose metadata. */ }
        String path = uri.getLastPathSegment();
        if (path == null) return null;
        String name = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf(':')) + 1);
        return ConsoleSystem.isRaw(name) || ConsoleSystem.extension(name).equals("zip") ? name : null;
    }

    private void finishImport(ImportReport report, String folderNote) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            importing = false;
            if (report.hasGames()) library.revealGames();
            showLibrary(report.summary() + (folderNote == null ? "" : " · " + folderNote));
            if (report.hasDetails()) {
                AlertDialog details = new AlertDialog.Builder(this).setTitle("Import results")
                        .setMessage(report.details()).setPositiveButton("OK", null).show();
                TextView message = details.findViewById(android.R.id.message);
                if (message != null) message.setTextIsSelectable(true);
            }
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

    private void play(File file, PatchStore.Entry patch, int slot, GameLink link) { launch(file, patch, slot, link, false); }

    private void continueQuick(QuickResumeStore.Entry saved) {
        if (!quickResumeEnabled() || game != null || importing || editor != null) return;
        File file = new File(roms, saved.rom);
        if (!RomStore.isArchive(file)) {
            showLibrary("Re-import " + saved.rom + " with the same ROM revision to continue.");
            return;
        }
        if (saved.variant == null) launch(file, null, 0, null, true);
        else libraryJob("Finding the saved IPS variant…", () -> libraryTools.resumePatch(file, saved.variant),
                patch -> launch(file, patch, 0, null, true));
    }

    private void launch(File file, PatchStore.Entry patch, int slot, GameLink link, boolean continuing) {
        if (game != null || importing || editor != null || !RomStore.isArchive(file) || ConsoleStore.isFirmware(file.getName())) return;
        if (ConsoleSystem.isDisc(file.getName())) { showLibrary("CD disc images aren't supported by this app build yet."); return; }
        if ((!systemDir.isDirectory() && !systemDir.mkdirs()) || (!saveDir.isDirectory() && !saveDir.mkdirs())) {
            showLibrary("Couldn't create private storage for the emulator.");
            return;
        }
        String profile = patch == null && link == null ? coreProfile(file.getName()) : "";
        if (!CoreProfile.valid(profile)) {
            showLibrary("Invalid saved FBNeo settings. Reset them from the game's library menu.");
            return;
        }
        currentFile = file;
        refreshSkin();
        String set = GameCatalog.setName(file.getName());
        String title = catalog.game(file).title(library.chineseNames());
        if (patch != null) title += " · IPS: " + patch.title;
        if (link != null) title += " · Link P" + (link.player() + 1);
        GameScreen screen = new GameScreen(this, title, skin, touchVisible, link, patch == null, this::gameAction);
        immersiveInsets(screen);
        GameSession session = new GameSession(this, screen.surface(), file, systemDir, saveDir, catalog, catalog.cover(set), patch, slot, link, continuing, profile, this);
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
        if (session == null || exiting) return;
        if (action == GameScreen.Action.RESUME) { togglePause(); return; }
        if (action == GameScreen.Action.EXIT) { exitGame(session); return; }
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
            case CORE_SETTINGS: showBootSettings(session); break;
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
            String[][] options = Core.options(false);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || game != session || exiting || !session.paused) return;
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

    private void showBootSettings(GameSession session) {
        if (!session.hasCoreSettings()) return;
        if (!session.post(() -> {
            String[][] options = Core.options(true);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || game != session || exiting || !session.paused) return;
                CoreSettingsPanel.show(this, options, coreProfile(session.romName()), HighScoreData.installed(systemDir), profile -> {
                    if (isFinishing() || isDestroyed() || game != session || exiting) return;
                    SharedPreferences.Editor edit = settings.edit();
                    String key = CoreProfile.key(session.romName());
                    if (profile.isEmpty()) edit.remove(key); else edit.putString(key, profile);
                    edit.apply();
                    toast(profile.equals(session.bootProfile) ? "Settings match the current session."
                            : "Saved. Exit and start normally to apply.");
                });
            });
        })) toast("Emulator busy. Try again.");
    }

    interface CoreTask { void run() throws IOException; }

    private void submit(GameSession session, CoreTask task, Runnable success) {
        if (game != session || exiting) return;
        if (!session.post(() -> {
            String message = null;
            try { task.run(); }
            catch (IOException | RuntimeException failure) { message = failure.getMessage() == null ? "Couldn't complete this action." : failure.getMessage(); }
            final String error = message;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || game != session || exiting) return;
                if (error != null) new AlertDialog.Builder(this).setTitle("Game action failed")
                        .setMessage(error).setPositiveButton("Close", null).show();
                else success.run();
            });
        })) toast("Emulator busy. Try again.");
    }

    private void togglePause() { if (game != null && editor == null && !exiting) setPaused(!game.paused); }

    private void setPaused(boolean pause) {
        if (game == null) return;
        game.paused = pause;
        clearInputs();
        player.setPaused(pause);
        updateTouch();
        syncAudioFocus();
    }

    private void exitGame(GameSession session) {
        if (session.link != null || !loaded || !quickResumeEnabled()) { closeGame(); return; }
        if (session.hasCoreSettings() && !session.bootProfile.equals(coreProfile(session.romName()))) {
            new AlertDialog.Builder(this).setTitle("Exit to apply FBNeo settings?")
                    .setMessage("Start the game normally to use the new settings. Quick Resume won't be replaced by a state from the old settings.")
                    .setPositiveButton("Exit", (d, w) -> { if (game == session) closeGame("Start normally to apply the saved FBNeo settings."); })
                    .setNegativeButton("Stay paused", null).show();
            return;
        }
        exiting = true;
        setPaused(true);
        player.setSavingQuickResume(true);
        if (!session.exitWithQuickResume(error -> runOnUiThread(() -> finishQuickExit(session, error)))) {
            finishQuickExit(session, "The emulator is busy. You can try again.");
        }
    }

    private void finishQuickExit(GameSession session, String error) {
        if (game != session || isDestroyed() || isFinishing()) return;
        if (error == null) { closeGame("Quick Resume saved. Use Continue to pick up here."); return; }
        exiting = false;
        player.setSavingQuickResume(false);
        new AlertDialog.Builder(this).setTitle("Couldn't save Quick Resume")
                .setMessage(error + "\n\nAny previous auto-save and manual slots are kept.")
                .setNegativeButton("Stay paused", null)
                .setPositiveButton("Exit without saving", (d, w) -> {
                    if (game == session) closeGame("Quick Resume wasn't updated.");
                }).show();
    }

    private void closeGame() { closeGame(null); }

    private void closeGame(String note) {
        if (game == null) return;
        if (editor != null) closeEditor();
        clearInputs();
        game.stop();
        game = null;
        player = null;
        currentFile = null;
        ports.clear();
        loaded = exiting = false;
        refreshSkin();
        abandonFocus();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        showLibrary(note);
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
            boolean resuming = session.continuing && !loaded;
            File rom = currentFile;
            closeGame(exiting ? "Quick Resume wasn't updated." : null);
            String title = resuming ? "Couldn't continue" : session.link == null ? "Couldn't play this game" : "Local link ended";
            AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(title).setMessage(reason).setPositiveButton("OK", null);
            if (resuming) dialog.setNeutralButton("Start normally", (d, w) -> play(rom, session.patch, 0));
            dialog.show();
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
        String selected = readText("skin", PadSkin.CLASSIC_CIRCLE.name());
        if (!selected.equals(skinSelection)) {
            skin = skins.resolve(selected);
            skinSelection = selected;
        }
        touchVisible = readBool("touch_visible", true);
        muted = readBool("muted", false);
    }

    private static int range(int value, int max) { return Math.max(0, Math.min(max, value)); }
    private void applySettings() {
        refreshSkin();
        if (game == null || player == null) return;
        clearInputs();
        for (int i = 0; i < keyMap.length; i++) keyMap[i] = readInt("key_" + i, 0);
        int[] combos = new int[PadBindings.EXTRAS], faces = new int[FACE.length];
        for (int i = 0; i < combos.length; i++) combos[i] = readInt("combo_" + i, 0);
        for (int i = 0; i < faces.length; i++) faces[i] = readInt("face_combo_" + i, 0);
        bindings = new PadBindings(faces, combos);
        game.turboMask = readInt("turbo", 0) & PadBindings.FACE_MASK;
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
                bindings, !linked && readBool("player_button", false), range(readInt("pad_mode", 0), 2), readBool("full_screen_pad", false));
        player.setFeedback(readBool("pad_animation", true), readBool("pad_haptic", false));
        int requested = ORIENTATION_CODES[range(readInt("orientation", 0), ORIENTATION_CODES.length - 1)];
        if (getRequestedOrientation() != requested) setRequestedOrientation(requested);
        updateTouch();
        syncAudioFocus();
    }

    private void showSettings() {
        if (editor != null || importing || game != null) return;
        library.hideKeyboard();
        String[] items = {"Virtual controls", "Display & audio", "Physical keys", "Quick Resume · " + status(quickResumeEnabled()),
                "Backup & restore", "Browse ROM folder", "How to play", "Core license", "Reset game settings", "High score data"};
        new AlertDialog.Builder(this).setTitle("Settings").setItems(items, (d, which) -> {
            switch (which) {
                case 0: showControls(); break;
                case 1: showDisplay(); break;
                case 2: showKeyBindings(); break;
                case 3: showQuickResume(); break;
                case 4: showBackup(); break;
                case 5: browseRoms(); break;
                case 6: showGuide(); break;
                case 7: showLicense(); break;
                case 8: resetSettings(); break;
                case 9: showHighScoreData(); break;
                default: break;
            }
        }).setNegativeButton("Close", null).show();
    }

    private void showHighScoreData() {
        boolean installed = HighScoreData.installed(systemDir);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("High score data · " + (installed ? "Installed" : "Not installed"))
                .setMessage("Choose the unmodified hiscore.dat linked in the README's High score setup. The app verifies the exact FBNeo revision and does not download anything.\n\n"
                        + "Then use Pause → FBNeo settings → Save high scores in a supported game. This does not apply to IPS variants or Link.")
                .setNegativeButton("Close", null).setPositiveButton(installed ? "Re-import" : "Import file", (d, w) -> {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivityForResult(pick, PICK_HISCORE_DATA); }
                    catch (android.content.ActivityNotFoundException unavailable) { toast("No document picker is available."); }
                });
        if (installed) dialog.setNeutralButton("Remove data", (d, w) -> new AlertDialog.Builder(this).setTitle("Remove high score data?")
                .setMessage("Saved scores stay on the device. Games with this option on will need the file again or a reset of their FBNeo settings.")
                .setNegativeButton("Cancel", null).setPositiveButton("Remove", (confirm, which) ->
                        libraryJob("Removing high score data…", () -> {
                            GameSession.awaitIdle();
                            HighScoreData.remove(systemDir);
                            return null;
                        }, unused -> showLibrary("High score data removed. Score files were kept."))).show());
        dialog.show();
    }

    private boolean quickResumeEnabled() { return settings.getBoolean(QUICK_RESUME, false); }

    private void showQuickResume() {
        boolean enabled = quickResumeEnabled();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Quick Resume · " + status(enabled))
                .setMessage("Save one recent solo game when you choose Pause → Exit game. Tap Continue in the library to pick up there. Each successful auto-save replaces the previous one, not your manual slots.\n\n"
                        + "Closing or backgrounding the app and exiting a Link match don't trigger an auto-save. This save stays on this device; use a manual slot for ZIP backups.")
                .setNegativeButton("Close", null)
                .setPositiveButton(enabled ? "Turn off" : "Turn on", (d, w) -> {
                    settings.edit().putBoolean(QUICK_RESUME, !enabled).apply();
                    showLibrary(enabled ? "Quick Resume off. Any old auto-save is kept." : "Quick Resume on. Use Exit game to save your place.");
                });
        if (quickResume.exists()) dialog.setNeutralButton("Clear auto-save", (d, w) -> clearQuickResume());
        dialog.show();
    }

    private void clearQuickResume() {
        new AlertDialog.Builder(this).setTitle("Clear the Quick Resume save?")
                .setMessage("This removes only the auto-save. Your six manual slots and settings are kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> libraryJob("Clearing auto-save…", () -> {
                    GameSession.awaitIdle();
                    quickResume.clear();
                    return null;
                }, unused -> showLibrary("Quick Resume save cleared."))).show();
    }

    private void showBackup() {
        new AlertDialog.Builder(this).setTitle("Backup & restore")
                .setMessage("Export save states, slot names and portable settings to a ZIP. Use Pause → Save in each game before exporting. Keep the ZIP in Downloads for a reinstall, or copy it to your new phone.\n\n"
                        + "ROMs, patches, in-game battery saves, previews and imported artwork aren't included. Keep your original files separately.")
                .setPositiveButton("Export ZIP", (d, w) -> chooseBackup(true))
                .setNeutralButton("Restore ZIP", (d, w) -> chooseBackup(false))
                .setNegativeButton("Close", null).show();
    }

    private void chooseBackup(boolean export) {
        if (importing || game != null) return;
        Intent pick = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        if (export) pick.setType("application/zip").putExtra(Intent.EXTRA_TITLE, "FBA-Arcade-Backup-" + LocalDate.now() + ".zip")
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        else pick.setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Some providers label ZIPs generically.
        try { startActivityForResult(pick, export ? EXPORT_BACKUP : RESTORE_BACKUP); }
        catch (android.content.ActivityNotFoundException missing) { showLibrary("This device doesn't have a system file picker."); }
    }

    private void confirmRestore(Uri document) {
        new AlertDialog.Builder(this).setTitle("Restore this backup ZIP?")
                .setMessage("Only restore a backup you made. Existing save slots stay unchanged; the ZIP fills empty slots. Settings in the ZIP replace matching settings on this device.\n\n"
                        + "To load a restored slot, use the same ROM ZIP and, for patched saves, the same IPS files applied in the same order.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (d, w) -> libraryJob("Checking and restoring backup…", () -> backups.restoreZip(document), result -> {
                    library.reloadSettings();
                    refreshSkin();
                    showLibrary("Backup restored · " + result.slots + " new slots · " + result.kept + " occupied slots kept · " + result.settings + " settings applied.");
                })).show();
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
                "Touch controls · " + status(touchVisible), "Button combinations · A–F and 1–4", "Turbo / autofire",
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
        String[] labels = new String[FACE.length + PadBindings.EXTRAS];
        for (int i = 0; i < labels.length; i++) {
            int mask = readInt(comboKey(i), 0) & PadBindings.FACE_MASK;
            List<String> names = new ArrayList<>();
            for (int j = 0; j < FACE.length; j++) if ((mask & PadBindings.FACE_BITS[j]) != 0) names.add(FACE[j]);
            labels[i] = comboLabel(i) + " · " + (names.isEmpty() ? i < FACE.length ? "Normal" : "Hidden" : String.join(" + ", names));
        }
        choice("Button combinations", labels, -1, i -> faceButtons(comboLabel(i) + " sends…", comboKey(i)));
    }

    private static String comboLabel(int i) { return i < FACE.length ? FACE[i] : "Combo " + (i - FACE.length + 1); }
    private static String comboKey(int i) { return i < FACE.length ? "face_combo_" + i : "combo_" + (i - FACE.length); }

    private void showTurbo() {
        int rate = readInt("turbo_rate", 10);
        choice("Turbo / autofire", new String[] {"Choose buttons to repeat", "Repeat speed · " + rate + " Hz"}, -1, i -> {
            if (i == 0) faceButtons("Repeat after a short hold", "turbo");
            else choice("Repeat speed", new String[] {"5 Hz", "10 Hz", "15 Hz", "20 Hz"}, Math.max(0, Math.min(3, rate / 5 - 1)),
                    n -> writeInt("turbo_rate", (n + 1) * 5));
        });
    }

    private void faceButtons(String title, String key) {
        int mask = readInt(key, 0);
        boolean[] selected = new boolean[FACE.length];
        for (int i = 0; i < selected.length; i++) selected[i] = (mask & PadBindings.FACE_BITS[i]) != 0;
        new AlertDialog.Builder(this).setTitle(title).setMultiChoiceItems(FACE, selected, (d, n, checked) -> selected[n] = checked)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
                    int next = 0;
                    for (int i = 0; i < selected.length; i++) if (selected[i]) next |= PadBindings.FACE_BITS[i];
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
        body.addView(ui.text(game == null ? "Built-in and imported FBA4droid artwork. For a custom skin, choose its individual PNG folder."
                : "Choose a skin. To import a new folder, return to library Settings.", 14, ArcadeUi.MUTED, false));
        ui.space(body, 9);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Controller skin").setView(scroll)
                .setNegativeButton("Close", null);
        if (game == null) builder.setPositiveButton("Import folder", (d, w) -> chooseTree(PICK_SKIN));
        AlertDialog dialog = builder.create();
        for (SkinStore.Entry entry : skins.entries()) {
            boolean selected = skin.key.equals(entry.key(true)) || skin.key.equals(entry.key(false));
            android.widget.Button button = ui.button(entry.title + (selected ? "  ✓" : "") + " · Imported", ArcadeUi.LIGHT_BLUE, ArcadeUi.NAVY, 10);
            button.setOnClickListener(v -> { dialog.dismiss(); showImportedSkin(entry); });
            body.addView(button, new LinearLayout.LayoutParams(-1, -2));
            ui.space(body, 6);
        }
        for (PadSkin candidate : PadSkin.values()) {
            boolean selected = candidate.name().equals(skin.key);
            LinearLayout option = ui.column();
            option.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
            option.setBackground(ui.tappable(selected ? ArcadeUi.LIGHT_BLUE : ArcadeUi.WHITE,
                    selected ? ArcadeUi.BLUE : ArcadeUi.BORDER, 12, false));
            option.setFocusable(true);
            option.setSelected(selected);
            option.setContentDescription(candidate.title + ". " + candidate.detail);
            PadPreview preview = new PadPreview(this, new PadArt(candidate));
            preview.setBackground(ui.gradient(0xff253e56, ArcadeUi.NIGHT, 8));
            option.addView(preview, new LinearLayout.LayoutParams(-1, ui.dp(61)));
            ui.space(option, 6);
            option.addView(ui.text(candidate.title + (selected ? "  ✓" : ""), 15, ArcadeUi.INK, true));
            option.addView(ui.text(candidate.detail, 12, ArcadeUi.MUTED, false));
            option.setOnClickListener(v -> { selectSkin(candidate.name()); dialog.dismiss(); });
            body.addView(option, new LinearLayout.LayoutParams(-1, -2));
            ui.space(body, 7);
        }
        dialog.show();
    }

    private void showImportedSkin(SkinStore.Entry entry) {
        ArcadeUi ui = new ArcadeUi(this);
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(17), ui.dp(5), ui.dp(17), ui.dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        body.addView(ui.text("Graphics only. Missing frames use built-in artwork. Your touch layout stays the same.", 14, ArcadeUi.MUTED, false));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(entry.title).setView(scroll)
                .setNegativeButton("Back", (d, w) -> showSkinPicker());
        if (game == null) builder.setNeutralButton("Remove", (d, w) -> removeSkin(entry));
        AlertDialog dialog = builder.create();
        for (boolean round : new boolean[]{true, false}) {
            ui.space(body, 7);
            android.graphics.Bitmap icon = entry.preview(round);
            if (icon != null) {
                ImageView preview = new ImageView(this);
                preview.setImageBitmap(icon);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                preview.setBackground(ui.gradient(0xff253e56, ArcadeUi.NIGHT, 8));
                body.addView(preview, new LinearLayout.LayoutParams(-1, ui.dp(58)));
            }
            String key = entry.key(round);
            android.widget.Button button = ui.button((round ? "Use round joystick" : "Use D-pad") + (skin.key.equals(key) ? "  ✓" : ""),
                    ArcadeUi.LIGHT_BLUE, ArcadeUi.NAVY, 10);
            button.setOnClickListener(v -> { selectSkin(key); dialog.dismiss(); });
            body.addView(button, new LinearLayout.LayoutParams(-1, -2));
        }
        dialog.show();
    }

    private void selectSkin(String key) {
        settings.edit().putString(target("skin"), key).apply();
        applySettings();
    }

    private void removeSkin(SkinStore.Entry entry) {
        new AlertDialog.Builder(this).setTitle("Remove imported skin?")
                .setMessage("Removes this private copy, not the original PNGs or your layout.")
                .setNegativeButton("Cancel", (d, w) -> showImportedSkin(entry)).setPositiveButton("Remove", (d, w) ->
                        libraryJob("Removing controller skin…", () -> { skins.remove(entry); return entry; }, removed -> {
                            SharedPreferences.Editor edit = settings.edit();
                            for (Map.Entry<String, ?> pref : settings.getAll().entrySet()) {
                                if (removed.key(true).equals(pref.getValue()) || removed.key(false).equals(pref.getValue())) edit.remove(pref.getKey());
                            }
                            edit.apply(); skinSelection = null; refreshSkin();
                            showLibrary("Imported skin removed."); showSkinPicker();
                        })).show();
    }

    private void showGuide() {
        new AlertDialog.Builder(this).setTitle("How to play")
                .setMessage("1. Tap Add games (or + Add). Choose a .nes file directly, another supported console ROM, or an FBNeo ZIP. In the picker, open Downloads or your storage folder if the file isn't in Recent. Keep FBNeo ZIP names and add any required parent or BIOS ZIPs.\n\n"
                        + "2. Tap a game. Use the console controls, or Coin then Start for arcade. Touch controls and gamepads can be used together.\n\n"
                        + "3. Tap Pause for cheats, save slots and game settings. Under Controls → Edit layout, drag each control and change its size. Rotate to edit the other layout.\n\n"
                        + "4. Open the ⋮ menu on a game row to manage states, import local notes or cheats, or launch a separate IPS variant.\n\n"
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
                .setMessage("Restore global and per-game controls, display, audio and FBNeo defaults. Keeps ROMs, saves, patches, cheats, thumbnails, favorites and history.")
                .setNegativeButton("Cancel", null).setPositiveButton("Reset", (d, w) -> {
                    SharedPreferences.Editor reset = settings.edit();
                    for (String key : settings.getAll().keySet()) {
                        if (!key.equals("tiles") && !key.equals("sorting") && !key.equals("chinese_names") && !key.equals(RomFolders.SAVED_FOLDER)
                                && !key.equals(FAVORITES) && !key.equals(RECENT_FILES)) reset.remove(key);
                    }
                    reset.apply();
                    refreshSkin(); showLibrary("Game settings reset.");
                }).show();
    }

    private void listenForKey(int index) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Bind " + HardwareKeys.NAMES[index])
                .setMessage((index >= HardwareKeys.NAMES.length - PadBindings.EXTRAS
                        ? "Set the combo's buttons in Virtual controls first. This binding sends that same combination.\n\n" : "")
                        + "Press a gamepad button or keyboard key. Escape or the controller's Mode button pauses the game.")
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
        final SparseIntArray buttons = new SparseIntArray(), computer = new SparseIntArray();
        int axis;
        Pressed(int port) { this.port = port; }
    }
    private final android.util.SparseArray<Pressed> hardware = new android.util.SparseArray<>();
    private final int[] keyMap = new int[HardwareKeys.NAMES.length];
    private PadBindings bindings = PadBindings.DEFAULT;
    private android.hardware.input.InputManager inputManager;
    private final android.hardware.input.InputManager.InputDeviceListener devices = new android.hardware.input.InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) {}
        @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }
        @Override public void onInputDeviceRemoved(int id) {
            ports.delete(id);
            hardware.remove(id);
            publishHardware();
            publishComputerKeys();
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

    private void publishComputerKeys() {
        if (game == null || !ComputerKeys.supported(game.console)) return;
        android.util.SparseBooleanArray unique = new android.util.SparseBooleanArray();
        for (int i = 0; i < hardware.size(); i++) {
            SparseIntArray pressed = hardware.valueAt(i).computer;
            for (int j = 0; j < pressed.size(); j++) unique.put(pressed.valueAt(j), true);
        }
        int[] next = new int[unique.size()];
        for (int i = 0; i < next.length; i++) next[i] = unique.keyAt(i);
        if (!Arrays.equals(next, game.keyboard)) game.keyboard = next;
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
        if (ComputerKeys.supported(game.console) && event.isFromSource(InputDevice.SOURCE_KEYBOARD)
                && !event.isFromSource(InputDevice.SOURCE_GAMEPAD) && !KeyEvent.isGamepadButton(key)) {
            int retroKey = ComputerKeys.code(key);
            if (retroKey != 0 && (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP)) {
                Pressed state = pressed(event.getDeviceId(), 0);
                if (event.getAction() == KeyEvent.ACTION_DOWN) state.computer.put(key, retroKey);
                else state.computer.delete(key);
                publishComputerKeys();
                return true;
            }
        }
        int button = HardwareKeys.mask(key, keyMap, bindings);
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
