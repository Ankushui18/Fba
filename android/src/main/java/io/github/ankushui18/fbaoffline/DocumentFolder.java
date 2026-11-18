package io.github.ankushui18.fbaoffline;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static android.provider.DocumentsContract.Document.*;

/** Bounded, read-only access to exactly the tree selected in Android's picker. */
final class DocumentFolder {
    private static final int MAX_SCAN = 4096;
    final Uri tree;
    final String root;
    private final ContentResolver resolver;

    static final class Entry {
        final String id, name;
        final boolean folder;
        Entry(String id, String name, boolean folder) { this.id = id; this.name = name; this.folder = folder; }
        String label() { return display(name, folder ? "Folder" : "File"); }
    }

    static final class Listing {
        final List<Entry> entries;
        final boolean limited;
        Listing(List<Entry> entries, boolean limited) { this.entries = entries; this.limited = limited; }
    }

    DocumentFolder(ContentResolver resolver, Uri tree) throws IOException {
        if (tree == null || !"content".equals(tree.getScheme()) || !DocumentsContract.isTreeUri(tree)) {
            throw new IOException("Choose a folder with the Android file picker.");
        }
        this.resolver = resolver;
        this.tree = tree;
        root = DocumentsContract.getTreeDocumentId(tree);
    }

    Uri uri(String id) { return DocumentsContract.buildDocumentUriUsingTree(tree, id); }

    String name() {
        try (Cursor cursor = resolver.query(uri(root), new String[] {COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return display(cursor.getString(0), "Selected folder");
        } catch (RuntimeException ignored) { /* Some providers only supply names when listing children. */ }
        return "Selected folder";
    }

    Listing list(String parent, int limit, Predicate<Entry> include) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent);
        String[] fields = {COLUMN_DOCUMENT_ID, COLUMN_DISPLAY_NAME, COLUMN_MIME_TYPE};
        ArrayList<Entry> result = new ArrayList<>();
        boolean limited = false;
        try (Cursor list = resolver.query(children, fields, null, null, null)) {
            if (list == null) throw new IOException("Couldn't list this folder. Choose it again if access has changed.");
            for (int scanned = 0; list.moveToNext(); scanned++) {
                LocalFiles.interrupted();
                if (scanned == MAX_SCAN) { limited = true; break; }
                String id = list.getString(0), name = list.getString(1);
                if (id == null || name == null || id.length() > 4096 || name.length() > 512) continue;
                Entry entry = new Entry(id, name, MIME_TYPE_DIR.equals(list.getString(2)));
                if (!include.test(entry)) continue;
                if (result.size() == limit) { limited = true; break; }
                result.add(entry);
            }
        } catch (RuntimeException unavailable) {
            throw new IOException("Couldn't read this folder. Choose it again if access has changed.", unavailable);
        }
        result.sort(Comparator.comparing((Entry e) -> !e.folder).thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
        return new Listing(result, limited);
    }

    static String display(String raw, String fallback) {
        String label = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
        if (label.length() > 80) label = label.substring(0, 80).trim();
        return label.isEmpty() ? fallback : label;
    }
}
