package io.github.ankushui18.fbaoffline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Html;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static io.github.ankushui18.fbaoffline.ArcadeUi.*;

/** Save states, IPS variants and custom cheat files are all scoped to the library row. */
final class LibraryTools {
    private static final int PICK_IPS = 111, PICK_CHEATS = 112, PICK_PREVIEW = 113, PICK_NOTE = 114, PICK_IPS_FOLDER = 115;
    private final Activity activity;
    private final ArcadeUi ui;
    private final Host host;
    private final File roms, states;
    private final PatchStore patches;
    private final UserCheats cheats;
    private final RomNotes notes;
    private Bundle pending;

    interface Host {
        void play(File rom, PatchStore.Entry patch, int slot);
        <T> void job(String status, Callable<T> task, Consumer<T> done);
        void note(String message);
        void info(File rom);
    }

    LibraryTools(Activity activity, File roms, Bundle saved, Host host) {
        this.activity = activity;
        this.roms = roms;
        this.host = host;
        ui = new ArcadeUi(activity);
        File data = activity.getNoBackupFilesDir();
        states = new File(data, "states");
        patches = new PatchStore(new File(data, "patches"));
        cheats = new UserCheats(new File(data, "user-cheats"));
        notes = new RomNotes(new File(data, "rom-notes"));
        pending = saved == null ? null : saved.getBundle("game-tool-picker");
    }

    void save(Bundle out) { out.putBundle("game-tool-picker", pending); }

    void states(File rom, PatchStore.Entry patch) {
        StateStore store = new StateStore(states, rom, patch == null ? null : patch.id);
        GamePanels.slots(activity, patch == null ? "Save states" : "States · " + patch.title, store, false, slot ->
                new AlertDialog.Builder(activity).setTitle(store.label(slot))
                        .setItems(new String[]{"Play from this state", "Rename state", "Delete state"}, (d, which) -> {
                            if (which == 0) host.play(rom, patch, slot);
                            else if (which == 1) nameState(rom, patch, store, slot);
                            else new AlertDialog.Builder(activity).setTitle("Delete slot " + slot + "?")
                                    .setMessage("This removes the state and its preview. It does not remove the ROM or change the other slots.")
                                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (confirm, button) ->
                                            host.job("Deleting state…", () -> { store.delete(slot); return slot; }, cleared -> {
                                                host.note("Slot " + cleared + " deleted."); states(rom, patch);
                                            })).show();
                        }).setNegativeButton("Back", (d, w) -> states(rom, patch)).show());
    }

    private void nameState(File rom, PatchStore.Entry patch, StateStore store, int slot) {
        EditText input = new EditText(activity);
        input.setSingleLine();
        input.setHint("Optional name for slot " + slot);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(56)});
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(5));
        panel.addView(input);
        new AlertDialog.Builder(activity).setTitle("Rename state").setView(panel).setNegativeButton("Cancel", null)
                .setPositiveButton("Save name", (d, w) -> {
                    String name = input.getText().toString();
                    host.job("Naming state…", () -> { store.rename(slot, name); return slot; }, updated -> states(rom, patch));
                }).show();
    }

    PatchStore.Entry resumePatch(File rom, String id) throws IOException {
        for (PatchStore.Entry patch : patches.entries(rom)) if (patch.id.equals(id)) return patch;
        throw new IOException("Quick Resume needs the original IPS variant. Re-import the same patches in the same order.");
    }

    void ips(File rom) {
        host.job("Reading patch profiles…", () -> patches.entries(rom), list -> showPatches(rom, list));
    }

    private void showPatches(File rom, List<PatchStore.Entry> entries) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(6));
        scroll.addView(content);
        content.addView(ui.text("Play saved variants or apply several patches together. The original ZIP and its saves stay unchanged.", 14, MUTED, false));
        if (entries.isEmpty()) {
            ui.space(content, 9);
            content.addView(ui.text("No patches yet. Add an .ips for one chip or browse a legacy .dat folder.", 14, INK, false));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle("IPS patches · " + rom.getName())
                .setView(scroll).setPositiveButton("Add patches", (d, w) -> patchImportChoices(rom))
                .setNegativeButton("Close", null);
        if (entries.size() > 1) builder.setNeutralButton("Apply selected", (d, w) -> selectPatches(rom, entries));
        AlertDialog dialog = builder.create();
        for (PatchStore.Entry entry : entries) {
            ui.space(content, 6);
            LinearLayout row = ui.row();
            row.setMinimumHeight(ui.dp(60));
            row.setPadding(ui.dp(6), ui.dp(4), ui.dp(7), ui.dp(4));
            row.setBackground(ui.tappable(LIGHT_BLUE, BORDER, 9, false));
            ImageView preview = GamePanels.preview(activity, entry.preview());
            row.addView(preview, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(47)));
            LinearLayout labels = ui.column();
            labels.addView(ui.text(entry.title, 15, INK, true));
            labels.addView(ui.text(entry.detail(), 12, MUTED, false));
            LinearLayout.LayoutParams caption = new LinearLayout.LayoutParams(0, -2, 1);
            caption.setMarginStart(ui.dp(9));
            row.addView(labels, caption);
            row.setFocusable(true);
            row.setOnClickListener(v -> { dialog.dismiss(); patchActions(rom, entry); });
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        dialog.show();
    }

    private void patchActions(File rom, PatchStore.Entry patch) {
        new AlertDialog.Builder(activity).setTitle(patch.title).setItems(
                new String[]{"Play patched game", "Save states for this patch", "Choose preview image", "Patch targets", "Remove patch"}, (d, i) -> {
                    if (i == 0) host.play(rom, patch, 0);
                    else if (i == 1) states(rom, patch);
                    else if (i == 2) pick(rom, PICK_PREVIEW, patch.id, "image/*");
                    else if (i == 3) new AlertDialog.Builder(activity).setTitle("Patched chips · apply order")
                            .setItems(patch.targets.stream().map(PatchStore.Chip::detail).toArray(String[]::new), null)
                            .setPositiveButton("Back", (x, y) -> patchActions(rom, patch)).show();
                    else new AlertDialog.Builder(activity).setTitle("Remove this patch?")
                                .setMessage("The ROM and save files are kept. Re-import the same patch to access its own saves again.")
                                .setNegativeButton("Cancel", null).setPositiveButton("Remove", (x, y) ->
                                        host.job("Removing patch…", () -> { patches.remove(patch); return patch.title; }, name -> {
                                            host.note("Patch removed."); ips(rom);
                                        })).show();
                }).setNegativeButton("Back", (d, w) -> ips(rom)).show();
    }

    private void patchImportChoices(File rom) {
        new AlertDialog.Builder(activity).setTitle("Add patches")
                .setItems(new String[]{"Import one .ips file", "Browse legacy IPS .dat folder"}, (d, i) -> {
                    if (i == 0) pickTarget(rom);
                    else new AlertDialog.Builder(activity).setTitle("Choose a legacy IPS folder")
                            .setMessage("Select the game's folder with its .dat definitions and .ips files (usually .ips/"
                                    + GameCatalog.setName(rom.getName()) + "). If Android hides it, copy the game folder into Documents first.")
                            .setNegativeButton("Back", (x, y) -> patchImportChoices(rom))
                            .setPositiveButton("Choose folder", (x, y) -> pick(rom, PICK_IPS_FOLDER, "", null)).show();
                }).setNegativeButton("Back", (d, w) -> ips(rom)).show();
    }

    private void selectPatches(File rom, List<PatchStore.Entry> entries) {
        String[] labels = entries.stream().map(e -> e.title + " · " + e.detail()).toArray(String[]::new);
        boolean[] selected = new boolean[entries.size()];
        new AlertDialog.Builder(activity).setTitle("Apply selected IPS")
                .setMultiChoiceItems(labels, selected, (d, n, checked) -> selected[n] = checked)
                .setNegativeButton("Back", (d, w) -> ips(rom)).setPositiveButton("Continue", (d, w) -> {
                    List<PatchStore.Entry> stack = new ArrayList<>();
                    for (int i = 0; i < selected.length; i++) if (selected[i]) stack.add(entries.get(i));
                    if (stack.isEmpty()) { ips(rom); return; }
                    if (stack.size() == 1) { patchActions(rom, stack.get(0)); return; }
                    int count = stack.stream().mapToInt(e -> e.targets.size()).sum();
                    if (count > PatchStore.MAX_PARTS) {
                        new AlertDialog.Builder(activity).setTitle("Too many IPS files")
                                .setMessage("Choose at most " + PatchStore.MAX_PARTS + " IPS files in total across the selected variants.")
                                .setPositiveButton("Back", (x, y) -> selectPatches(rom, entries)).show();
                        return;
                    }
                    String[] names = stack.stream().map(e -> e.title).toArray(String[]::new);
                    new AlertDialog.Builder(activity).setTitle("Play combined variant?")
                            .setMessage(String.join(" → ", names) + "\n\nApplied in this order. For overlapping writes, the later patch wins. The combined variant has its own save slots and preview.")
                            .setNegativeButton("Back", (x, y) -> selectPatches(rom, entries))
                            .setPositiveButton("Save & play", (x, y) -> host.job("Combining IPS patches…",
                                    () -> patches.combine(rom, String.join(" + ", names), stack), group -> host.play(rom, group, 0))).show();
                }).show();
    }

    private void browseLegacyPatches(File rom, Uri uri) {
        String set = GameCatalog.setName(rom.getName());
        host.job("Reading legacy IPS folder…", () -> {
            DocumentFolder tree = new DocumentFolder(activity.getContentResolver(), uri);
            DocumentFolder.Listing listing = tree.list(tree.root, 256, e -> e.folder ? e.name.equalsIgnoreCase(set) : patchFile(e.name));
            if (listing.limited) throw new IOException("Choose a smaller folder containing the IPS pack for " + set + ".");
            for (DocumentFolder.Entry e : listing.entries) if (e.folder) {
                listing = tree.list(e.id, 256, f -> !f.folder && patchFile(f.name));
                if (listing.limited) throw new IOException("This IPS folder contains too many files.");
                break;
            }
            Map<String, DocumentFolder.Entry> files = new LinkedHashMap<>();
            List<DocumentFolder.Entry> definitions = new ArrayList<>();
            for (DocumentFolder.Entry entry : listing.entries) {
                String key = entry.name.toLowerCase(Locale.ROOT);
                if (entry.folder) continue;
                if (files.putIfAbsent(key, entry) != null) throw new IOException("Duplicate filename in the IPS pack: " + entry.label());
                if (key.endsWith(".dat")) definitions.add(entry);
            }
            if (definitions.isEmpty()) throw new IOException("No IPS .dat definitions found. Choose the ROM's patch folder, not the .romdata folder.");
            return new LegacyPatchFolder(tree, files, definitions);
        }, folder -> new AlertDialog.Builder(activity).setTitle("IPS definitions · " + set)
                .setItems(folder.definitions.stream().map(DocumentFolder.Entry::label).toArray(String[]::new), (d, which) ->
                        host.job("Validating legacy IPS definition…", () -> importLegacyPatch(rom, set, folder, which),
                                added -> { host.note("Patch saved: " + added.title); ips(rom); }))
                .setNegativeButton("Back", (d, w) -> ips(rom)).show());
    }

    private PatchStore.Entry importLegacyPatch(File rom, String set, LegacyPatchFolder folder, int which) throws IOException {
        DocumentFolder.Entry definition = folder.definitions.get(which);
        byte[] manifest;
        try (InputStream in = open(folder, definition)) { manifest = LocalFiles.read(in, LegacyIps.MAX_DAT); }
        List<PatchStore.Part> parts = LegacyIps.load(set, manifest, GameCatalog.patchTargets(activity, rom), name -> {
            DocumentFolder.Entry file = folder.files.get(name);
            if (file == null) throw new IOException("Missing " + name + " in this IPS folder.");
            return open(folder, file);
        });
        return patches.add(rom, definition.name, parts);
    }

    private InputStream open(LegacyPatchFolder folder, DocumentFolder.Entry file) throws IOException {
        return activity.getContentResolver().openInputStream(folder.tree.uri(file.id));
    }

    private static boolean patchFile(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return key.endsWith(".dat") || key.endsWith(".ips");
    }

    private static final class LegacyPatchFolder {
        final DocumentFolder tree;
        final Map<String, DocumentFolder.Entry> files;
        final List<DocumentFolder.Entry> definitions;
        LegacyPatchFolder(DocumentFolder tree, Map<String, DocumentFolder.Entry> files, List<DocumentFolder.Entry> definitions) {
            this.tree = tree; this.files = files; this.definitions = definitions;
        }
    }

    private void pickTarget(File rom) {
        host.job("Matching ROM chips against FBNeo…", () -> GameCatalog.patchTargets(activity, rom), chips -> {
            if (chips.isEmpty()) {
                new AlertDialog.Builder(activity).setTitle("No matching chip in this ZIP")
                        .setMessage("The IPS tool needs a named chip in this exact FBNeo set with the expected size and CRC. For split sets, select the library row whose ZIP contains the chip.")
                        .setPositiveButton("OK", null).show();
                return;
            }
            String[] names = chips.stream().map(PatchStore.Chip::detail).toArray(String[]::new);
            new AlertDialog.Builder(activity).setTitle("Which chip does this IPS target?").setItems(names,
                    (d, i) -> pick(rom, PICK_IPS, names[i], "*/*")).setNegativeButton("Cancel", null).show();
        });
    }

    String gameNote(File rom) {
        try { return notes.read(rom); }
        catch (IOException unavailable) { return ""; } // A missing/damaged note must not hide game info.
    }

    void noteActions(File rom, boolean existing) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle("Local note · " + rom.getName())
                .setMessage("Import a .htm, .html or .txt note, including old .rominfo files. Only text is kept. Links, images and scripts aren't opened.")
                .setPositiveButton(existing ? "Replace file" : "Import file", (d, w) -> pick(rom, PICK_NOTE, "", "*/*"))
                .setNegativeButton("Back", (d, w) -> host.info(rom));
        if (existing) dialog.setNeutralButton("Remove", (d, w) -> new AlertDialog.Builder(activity)
                .setTitle("Remove local note?").setMessage("The original note and game data stay unchanged.")
                .setNegativeButton("Cancel", (x, y) -> noteActions(rom, true)).setPositiveButton("Remove", (x, y) ->
                        host.job("Removing local note…", () -> { notes.remove(rom); return rom; }, host::info)).show());
        dialog.show();
    }

    void cheatFiles(File rom) {
        boolean custom = cheats.exists(rom);
        String[] choices = custom ? new String[]{"Import replacement INI / ZIP", "Paste INI text", "Restore built-in cheats"}
                : new String[]{"Import INI / ZIP", "Paste INI text"};
        new AlertDialog.Builder(activity).setTitle(custom ? "Custom cheats · " + rom.getName() : "Cheat files · " + rom.getName())
                .setItems(choices, (d, which) -> {
                    if (which == 0) pick(rom, PICK_CHEATS, "", "*/*");
                    else if (which == 1) pasteCheats(rom);
                    else host.job("Restoring built-in cheats…", () -> { cheats.remove(rom); return rom; },
                            r -> host.note("Built-in cheats restored for " + r.getName() + "."));
                }).setNegativeButton("Close", null).show();
    }

    private void pasteCheats(File rom) {
        EditText text = new EditText(activity);
        text.setTextSize(14);
        text.setMinLines(5);
        text.setMaxLines(10);
        text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32768)});
        text.setHint("cheat \"My cheat\"\n0 \"Disabled\"\n1 \"Enabled\", CPU, ADDRESS, VALUE");
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(17), 0, ui.dp(17), ui.dp(5));
        panel.addView(ui.text("Use codes for this exact arcade set. Import saves the supported choices disabled; enable them in Pause → Cheats.", 14, MUTED, false));
        panel.addView(text);
        new AlertDialog.Builder(activity).setTitle("Paste cheat INI").setView(panel).setNegativeButton("Cancel", null)
                .setPositiveButton("Validate & save", (d, w) -> {
                    byte[] data = text.getText().toString().getBytes(StandardCharsets.UTF_8);
                    host.job("Validating cheats…", () -> cheats.install(rom, data), this::cheatsSaved);
                }).show();
    }

    private void cheatsSaved(UserCheats.Rules result) {
        host.note("Imported " + result.groups + " cheat group(s)" + (result.skipped > 0 ? "; skipped " + result.skipped + " unsupported or duplicate group(s)" : "")
                + ". Open Pause → Cheats while playing.");
    }

    private void pick(File rom, int request, String target, String mime) {
        Bundle choice = new Bundle();
        choice.putString("rom", rom.getName());
        choice.putString("target", target);
        choice.putInt("request", request);
        pending = choice;
        try {
            Intent picker = request == PICK_IPS_FOLDER ? new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    : new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);
            activity.startActivityForResult(picker.putExtra(Intent.EXTRA_LOCAL_ONLY, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), request);
        } catch (ActivityNotFoundException unavailable) {
            pending = null;
            host.note("No system file picker is available.");
        }
    }

    boolean result(int request, int result, Intent data) {
        if (request != PICK_IPS && request != PICK_CHEATS && request != PICK_PREVIEW && request != PICK_NOTE && request != PICK_IPS_FOLDER) return false;
        Bundle choice = pending;
        pending = null;
        Uri uri = data == null ? null : data.getData();
        if (result != Activity.RESULT_OK || choice == null || choice.getInt("request") != request
                || uri == null || !"content".equals(uri.getScheme())) return true;
        File rom;
        try { rom = new File(roms, RomStore.archiveName(choice.getString("rom"))); }
        catch (IOException invalid) { return true; }
        if (!RomStore.isArchive(rom)) { host.note("This ROM ZIP is no longer in the library."); return true; }
        String target = choice.getString("target", "");
        if (request == PICK_IPS_FOLDER) {
            browseLegacyPatches(rom, uri);
        } else if (request == PICK_NOTE) {
            host.job("Copying game note…", () -> {
                boolean html = RomNotes.isHtml(displayName(uri));
                try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                    String text = RomNotes.decode(LocalFiles.read(in, RomNotes.MAX_SOURCE_BYTES));
                    if (html) text = Html.fromHtml(RomNotes.htmlBody(text), Html.FROM_HTML_MODE_COMPACT).toString().replace("\ufffc", "");
                    notes.write(rom, text);
                }
                return rom;
            }, host::info);
        } else if (request == PICK_CHEATS) {
            host.job("Validating custom cheats…", () -> {
                try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                    return cheats.install(rom, displayName(uri), in, activity.getCacheDir());
                }
            }, this::cheatsSaved);
        } else if (request == PICK_IPS) {
            host.job("Validating IPS patch…", () -> {
                PatchStore.Chip match = GameCatalog.patchTargets(activity, rom).stream().filter(chip -> chip.detail().equals(target))
                        .findFirst().orElseThrow(() -> new IOException("The selected chip no longer matches."));
                try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                    return patches.add(rom, displayName(uri), match, LocalFiles.read(in, IpsPatch.MAX_BYTES));
                }
            }, added -> { host.note("Patch saved: " + added.title); ips(rom); });
        } else {
            host.job("Copying patch preview…", () -> {
                PatchStore.Entry entry = patches.entries(rom).stream().filter(p -> p.id.equals(target)).findFirst()
                        .orElseThrow(() -> new IOException("This patch is no longer installed."));
                try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("Couldn't read the selected image.");
                    ImageFiles.importArt(in, entry.preview());
                }
                return entry;
            }, updated -> { host.note("Patch preview saved."); ips(rom); });
        }
        return true;
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        return null;
    }
}
