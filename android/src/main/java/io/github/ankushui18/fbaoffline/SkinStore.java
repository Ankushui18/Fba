package io.github.ankushui18.fbaoffline;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Copies one legacy skin directory from SAF; only flattened images and a short label are kept. */
final class SkinStore {
    private static final int MAX_SKINS = 8, MAX_IMPORT_BYTES = 16 * 1024 * 1024;
    private static final Pattern ID = Pattern.compile("[0-9a-f]{32}");
    private static final String PREFIX = "imported:";
    private static final String LABEL = "label.txt";
    private final File root;

    static final class Entry {
        final File dir;
        final String title;
        Entry(File dir, String title) { this.dir = dir; this.title = title; }
        String key(boolean round) { return PREFIX + dir.getName() + (round ? ":round" : ":cross"); }
        boolean roundByDefault() {
            return new File(dir, LegacySkinFiles.RING).isFile() || !new File(dir, LegacySkinFiles.DIRECTIONS[0]).isFile();
        }
        Bitmap preview(boolean round) { return bitmap(new File(dir, round ? LegacySkinFiles.ROUND_ICON : LegacySkinFiles.CROSS_ICON)); }
    }

    SkinStore(File root) { this.root = root; }

    List<Entry> entries() {
        List<Entry> items = new ArrayList<>();
        File[] folders = root.listFiles(dir -> dir.isDirectory() && ID.matcher(dir.getName()).matches());
        if (folders != null) for (File dir : folders) {
            if (items.size() == MAX_SKINS) break;
            items.add(new Entry(dir, label(dir)));
        }
        items.sort(Comparator.comparing((Entry e) -> e.title, String.CASE_INSENSITIVE_ORDER).thenComparing(e -> e.dir.getName()));
        return items;
    }

    Entry install(ContentResolver resolver, Uri uri) throws IOException {
        if (entries().size() >= MAX_SKINS) throw new IOException("Remove an imported skin before adding another (limit: " + MAX_SKINS + ").");
        DocumentFolder source = new DocumentFolder(resolver, uri);
        DocumentFolder.Listing listing = source.list(source.root, LegacySkinFiles.NAMES.size(),
                e -> !e.folder && LegacySkinFiles.name(e.name) != null);
        if (listing.limited) throw new IOException("Choose the individual skin folder with its PNGs, not the entire skin collection.");
        String title = source.name();
        Files.createDirectories(root.toPath());
        File staging = Files.createTempDirectory(root.toPath(), "incoming-").toFile();
        try {
            int bytes = 0, sprites = 0;
            for (DocumentFolder.Entry frame : listing.entries) {
                String name = LegacySkinFiles.name(frame.name);
                File out = new File(staging, name);
                if (out.exists()) throw new IOException("Duplicate PNG name: " + frame.label());
                byte[] data;
                try (InputStream in = resolver.openInputStream(source.uri(frame.id))) {
                    data = LocalFiles.read(in, LegacySkinFiles.MAX_SOURCE_BYTES);
                }
                if ((bytes += data.length) > MAX_IMPORT_BYTES) throw new IOException("Choose a skin with at most 16 MiB of PNGs.");
                importPng(data, out, frame.label());
                if (!LegacySkinFiles.preview(name)) sprites++;
            }
            if (sprites == 0) throw new IOException("No legacy control PNGs. Choose the skin's folder, e.g. one with BUTTON_A.png or ANALOG.png.");
            LocalFiles.atomicWrite(new File(staging, LABEL), title.getBytes(StandardCharsets.UTF_8));
            LocalFiles.interrupted();
            File installed = new File(root, UUID.randomUUID().toString().replace("-", ""));
            Files.move(staging.toPath(), installed.toPath(), StandardCopyOption.ATOMIC_MOVE);
            return new Entry(installed, title);
        } finally { LocalFiles.deleteTree(staging); }
    }

    void remove(Entry entry) throws IOException {
        LocalFiles.deleteTree(entry.dir);
        if (entry.dir.exists()) throw new IOException("Couldn't remove this skin.");
    }

    PadArt resolve(String key) {
        if (!key.startsWith(PREFIX)) return new PadArt(PadSkin.stored(key));
        String[] parts = key.split(":", -1);
        if (parts.length != 3 || !ID.matcher(parts[1]).matches() || !("round".equals(parts[2]) || "cross".equals(parts[2]))) {
            return new PadArt(PadSkin.CLASSIC_CIRCLE);
        }
        File dir = new File(root, parts[1]);
        if (!dir.isDirectory()) return new PadArt(PadSkin.CLASSIC_CIRCLE);
        boolean round = "round".equals(parts[2]);
        Map<String, Bitmap> art = new HashMap<>();
        for (String name : LegacySkinFiles.NAMES) {
            if (LegacySkinFiles.preview(name) || (!round && (LegacySkinFiles.RING.equals(name) || LegacySkinFiles.KNOB.equals(name)))) continue;
            Bitmap image = bitmap(new File(dir, name));
            if (image != null) art.put(name, image);
        }
        return new PadArt(round ? PadSkin.CLASSIC_CIRCLE : PadSkin.CLASSIC, key, label(dir), round ? "Imported · round stick" : "Imported · D-pad", art);
    }

    private static String label(File dir) {
        try (InputStream in = Files.newInputStream(new File(dir, LABEL).toPath())) {
            return DocumentFolder.display(new String(LocalFiles.read(in, 512), StandardCharsets.UTF_8), "Imported skin");
        } catch (IOException missing) { return "Imported skin"; }
    }

    private static Bitmap bitmap(File source) {
        if (!source.isFile()) return null;
        return BitmapFactory.decodeFile(source.getPath()); // A private, already scaled/re-encoded bitmap.
    }

    private static void importPng(byte[] data, File file, String label) throws IOException {
        int side = LegacySkinFiles.pngSide(data);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (side > options.inSampleSize * LegacySkinFiles.MAX_STORED_SIDE) options.inSampleSize *= 2;
        Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (decoded == null) throw new IOException("Couldn't decode skin PNG: " + label);
        try {
            if (decoded.getWidth() > LegacySkinFiles.MAX_STORED_SIDE || decoded.getHeight() > LegacySkinFiles.MAX_STORED_SIDE) {
                throw new IOException("Couldn't scale skin PNG: " + label);
            }
            ImageFiles.write(file, decoded);
        } finally { decoded.recycle(); }
    }
}
