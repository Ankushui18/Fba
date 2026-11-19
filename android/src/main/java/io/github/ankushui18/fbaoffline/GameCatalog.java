package io.github.ankushui18.fbaoffline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Local, indexed artwork and metadata; recycled rows never decode images on the UI thread. */
final class GameCatalog {
    private final Context app;
    private final File coverDir;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "game-art"));
    private volatile Map<String, Game> games = Collections.emptyMap();
    private final LruCache<String, Bitmap> icons = new LruCache<String, Bitmap>(10 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private volatile boolean closed;
    private ZipFile art;

    static final class Game {
        final String set, english, chinese, board, year, company, parent, dependency, genre;
        Game(String[] row) {
            set = row[0]; english = row[1]; chinese = row[2]; board = row[3]; year = row[4];
            company = row[5]; parent = row[6]; dependency = row[7]; genre = row[8];
        }
        String title(boolean chineseNames) { return chineseNames && !chinese.isEmpty() ? chinese : english; }
        String subtitle() {
            if (board.isEmpty()) return year.isEmpty() ? set + ".zip" : year;
            return year.isEmpty() ? board : board + " · " + year;
        }
    }

    GameCatalog(Context context) {
        app = context.getApplicationContext();
        coverDir = new File(app.getNoBackupFilesDir(), "screenshots");
    }

    void load(Runnable done) {
        worker.execute(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(app.getAssets().open("game-catalog.tsv"), StandardCharsets.UTF_8))) {
                HashMap<String, Game> decoded = new HashMap<>();
                String line;
                while ((line = in.readLine()) != null) {
                    String[] row = line.split("\t", -1);
                    if (row.length == 9) decoded.put(row[0], new Game(row));
                }
                games = Collections.unmodifiableMap(decoded);
            } catch (IOException ignored) { /* Show archive names when catalog is unavailable. */ }
            main.post(() -> { if (!closed) done.run(); });
        });
    }

    static String setName(String filename) { return filename.substring(0, filename.length() - 4); }

    Game game(File archive) {
        String name = setName(archive.getName());
        Game game = games.get(name);
        return game != null ? game : new Game(new String[] {name, name, "", "", "", "", "", "", ""});
    }

    File cover(String set) { return new File(coverDir, set + ".png"); }

    void showArt(ImageView view, Game game) {
        if (closed) return;
        File screenshot = cover(game.set);
        String key = game.set + "@" + screenshot.lastModified();
        if (key.equals(view.getTag())) return;
        view.setTag(key);
        view.setImageResource(R.drawable.ic_library_pad);
        Bitmap saved = icons.get(key);
        if (saved != null) { view.setImageBitmap(saved); return; }
        worker.execute(() -> {
            if (closed) return;
            Bitmap image = icons.get(key);
            try {
                if (image == null && screenshot.isFile()) image = BitmapFactory.decodeFile(screenshot.getAbsolutePath());
                if (image == null) image = icon(game.set);
                if (image == null && !game.parent.isEmpty()) image = icon(game.parent);
                if (image != null) icons.put(key, image);
            } catch (IOException | RuntimeException ignored) { return; }
            final Bitmap result = image;
            if (result != null) main.post(() -> {
                if (!closed && key.equals(view.getTag())) view.setImageBitmap(result);
            });
        });
    }

    private Bitmap icon(String set) throws IOException {
        if (art == null) art = new ZipFile(cacheBundle(app, "game-art.zip"));
        ZipEntry image = art.getEntry(set + ".png");
        if (image == null || image.getSize() < 1 || image.getSize() > 128 * 1024) return null;
        try (InputStream in = art.getInputStream(image)) { return BitmapFactory.decodeStream(in); }
    }

    void close() {
        closed = true;
        worker.execute(() -> {
            if (art != null) try { art.close(); } catch (IOException ignored) {}
            icons.evictAll();
        });
        worker.shutdown();
    }

    // Bundles are immutable APK data. Give a version-specific cache name so an upgrade never uses stale entries.
    private static File cacheBundle(Context context, String name) throws IOException {
        File cache = new File(context.getCacheDir(), "catalog-3-" + name);
        if (cache.isFile()) return cache;
        Path scratch = Files.createTempFile(context.getCacheDir().toPath(), "catalog-", ".part");
        try (InputStream source = context.getAssets().open(name)) {
            Files.copy(source, scratch, StandardCopyOption.REPLACE_EXISTING);
            Files.move(scratch, cache.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return cache;
        } finally { Files.deleteIfExists(scratch); }
    }

    static java.util.List<PatchStore.Chip> patchTargets(Context context, File rom) throws IOException {
        try (ZipFile index = new ZipFile(cacheBundle(context, "ips-targets.zip"))) {
            ZipEntry row = index.getEntry(setName(rom.getName()) + ".tsv");
            if (row == null) return Collections.emptyList();
            try (InputStream in = index.getInputStream(row)) { return PatchStore.targets(rom, in); }
        }
    }

    /** Give FBNeo one validated, exact-set INI until the current session closes. */
    static File stageCheats(Context context, File system, File rom) throws IOException {
        String set = setName(rom.getName());
        File dir = new File(system, "fbneo/cheats");
        Files.createDirectories(dir.toPath());
        // This private directory is exclusively for these ephemeral INIs, including leftovers from process death.
        File[] old = dir.listFiles((d, n) -> n.endsWith(".ini") || n.endsWith(".part"));
        if (old != null) for (File entry : old) Files.deleteIfExists(entry.toPath());
        byte[] custom = null;
        try { custom = new UserCheats(new File(context.getNoBackupFilesDir(), "user-cheats")).forRom(rom); }
        catch (IOException corrupt) { /* Skip an unavailable custom list, then try the bundled set. */ }
        if (custom != null) {
            File ini = new File(dir, set + ".ini");
            LocalFiles.atomicWrite(ini, custom);
            return ini;
        }
        try (ZipFile bundle = new ZipFile(cacheBundle(context, "game-cheats.zip"))) {
            ZipEntry source = bundle.getEntry(set + ".ini");
            if (source == null) return null;
            if (source.getSize() <= 0 || source.getSize() > 256 * 1024) throw new IOException("Invalid cheat data.");
            File ini = new File(dir, set + ".ini");
            Path temp = Files.createTempFile(dir.toPath(), "cheats-", ".part");
            try (InputStream in = bundle.getInputStream(source)) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, ini.toPath(), StandardCopyOption.ATOMIC_MOVE);
                return ini;
            } finally { Files.deleteIfExists(temp); }
        }
    }
}
