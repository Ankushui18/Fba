package io.github.ankushui18.fbaoffline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** The old Files tab's folder browsing, scoped to a user-approved SAF tree. */
final class RomFolders {
    static final String SAVED_FOLDER = "rom_folder_uri";
    private static final int MAX_ITEMS = 256, MAX_DEPTH = 12;
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Host host;

    interface Host {
        <T> void job(String status, Callable<T> task, Consumer<T> done);
        void choose();
        void copy(List<Uri> files, String note);
        void note(String text);
    }

    RomFolders(Activity activity, SharedPreferences prefs, Host host) {
        this.activity = activity; this.prefs = prefs; this.host = host;
    }

    private Uri saved() {
        String value = prefs.getString(SAVED_FOLDER, "");
        return value.isEmpty() ? null : Uri.parse(value);
    }

    void browse() {
        Uri selected = saved();
        if (selected == null || !hasGrant(selected)) {
            prefs.edit().remove(SAVED_FOLDER).apply();
            host.choose();
        } else open(selected);
    }

    void picked(Uri selected) {
        if (selected == null) return;
        try { new DocumentFolder(activity.getContentResolver(), selected); }
        catch (IOException | RuntimeException invalid) { host.note("Choose a folder through the Android file picker."); return; }
        try {
            activity.getContentResolver().takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri previous = saved();
            prefs.edit().putString(SAVED_FOLDER, selected.toString()).apply();
            if (previous != null && !previous.equals(selected)) release(previous);
        } catch (RuntimeException temporary) { /* A non-persistable provider can still be browsed now. */ }
        open(selected);
    }

    private void open(Uri selected) {
        host.job("Opening ROM folder…", () -> {
            DocumentFolder folder = new DocumentFolder(activity.getContentResolver(), selected);
            DocumentFolder.Entry root = new DocumentFolder.Entry(folder.root, folder.name(), true);
            return readPage(folder, Collections.singletonList(root));
        }, this::show);
    }

    private void navigate(DocumentFolder folder, List<DocumentFolder.Entry> path) {
        host.job("Reading ROM folder…", () -> readPage(folder, path), this::show);
    }

    private static Page readPage(DocumentFolder folder, List<DocumentFolder.Entry> path) throws IOException {
        try {
            return new Page(folder, path, folder.list(path.get(path.size() - 1).id, MAX_ITEMS,
                    e -> e.folder || isRomZip(e.name) || isLegacy(e.name)));
        } catch (java.io.InterruptedIOException cancelled) { throw cancelled; }
        catch (IOException unavailable) { return new Page(folder, path, null); }
    }

    private static final class Page {
        final DocumentFolder folder;
        final List<DocumentFolder.Entry> path;
        final DocumentFolder.Listing listing;
        Page(DocumentFolder folder, List<DocumentFolder.Entry> path, DocumentFolder.Listing listing) {
            this.folder = folder; this.path = path; this.listing = listing;
        }
    }

    private void show(Page page) {
        String name = page.path.get(page.path.size() - 1).label();
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle("ROM folder · " + name)
                .setNegativeButton("Close", null).setPositiveButton("Change folder", (d, w) -> host.choose());
        if (page.folder.tree.equals(saved())) dialog.setNeutralButton("Forget", (d, w) -> {
            prefs.edit().remove(SAVED_FOLDER).apply();
            release(page.folder.tree);
            host.note("Folder access removed. Imported ZIPs and saves were kept.");
        });
        if (page.listing == null) {
            dialog.setMessage("Couldn't read this folder. If it was moved or access changed, choose it again.");
            if (page.path.size() > 1) dialog.setNegativeButton("Up", (d, w) -> up(page));
            dialog.show();
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        boolean root = page.path.size() == 1;
        if (!root) add(labels, actions, "↑  Up one folder", () -> up(page));
        ArrayList<Uri> zips = new ArrayList<>();
        for (DocumentFolder.Entry entry : page.listing.entries) {
            if (!entry.folder && isRomZip(entry.name)) zips.add(page.folder.uri(entry.id));
        }
        if (!zips.isEmpty()) add(labels, actions, "Import " + zips.size() + " ZIP(s) in this folder", () ->
                confirmCopy(zips, page.listing.limited ? "Only the listed ZIPs were imported." : null, page));
        if (page.listing.limited) add(labels, actions, "Listing limit reached · choose a smaller folder", () -> show(page));
        for (DocumentFolder.Entry entry : page.listing.entries) {
            String label = entry.label();
            if (entry.folder) add(labels, actions, "Folder · " + label, () -> {
                if (page.path.size() >= MAX_DEPTH || page.path.stream().anyMatch(parent -> parent.id.equals(entry.id))) {
                    host.note("Choose that subfolder directly to browse it."); return;
                }
                ArrayList<DocumentFolder.Entry> next = new ArrayList<>(page.path);
                next.add(entry);
                navigate(page.folder, next);
            });
            else if (isLegacy(entry.name)) add(labels, actions, label + " · legacy cache", () ->
                    new AlertDialog.Builder(activity).setTitle("Old .fba cache")
                            .setMessage("FBA Arcade needs the matching FBNeo ZIP. An E680 .fba cache is not a ZIP.")
                            .setPositiveButton("Back to folder", (d, w) -> show(page)).show());
            else add(labels, actions, label, () -> confirmCopy(Collections.singletonList(page.folder.uri(entry.id)), null, page));
        }
        if (labels.isEmpty()) add(labels, actions, "No ROM ZIPs or subfolders found", () -> show(page));
        dialog.setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run()).show();
    }

    private void up(Page page) {
        navigate(page.folder, new ArrayList<>(page.path.subList(0, page.path.size() - 1)));
    }

    private void confirmCopy(List<Uri> files, String note, Page page) {
        new AlertDialog.Builder(activity).setTitle("Import " + files.size() + " ZIP(s)?")
                .setMessage("Copies ZIPs into the private library, replacing local copies with the same filename. Source files and saves aren't changed. Subfolders are not imported automatically.")
                .setNegativeButton("Back", (d, w) -> show(page))
                .setPositiveButton("Import", (d, w) -> host.copy(files, note)).show();
    }

    private boolean hasGrant(Uri uri) {
        return activity.getContentResolver().getPersistedUriPermissions().stream()
                .anyMatch(p -> p.isReadPermission() && p.getUri().equals(uri));
    }

    private void release(Uri uri) {
        try { activity.getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (RuntimeException ignored) { /* A moved or removed source may already have revoked the grant. */ }
    }

    private static void add(List<String> labels, List<Runnable> actions, String label, Runnable action) {
        labels.add(label); actions.add(action);
    }

    private static boolean isRomZip(String name) {
        try { RomStore.archiveName(name); return true; }
        catch (IOException invalid) { return false; }
    }

    private static boolean isLegacy(String name) { return name.toLowerCase(Locale.ROOT).endsWith(".fba"); }
}
