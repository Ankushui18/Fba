package io.github.ankushui18.fbaoffline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static io.github.ankushui18.fbaoffline.ArcadeUi.*;

/** Save states, IPS variants and custom cheat files are all scoped to the library row. */
final class LibraryTools {
    private static final int PICK_IPS = 111, PICK_CHEATS = 112, PICK_PREVIEW = 113;
    private final Activity activity;
    private final ArcadeUi ui;
    private final Host host;
    private final File roms, states;
    private final PatchStore patches;
    private final UserCheats cheats;
    private Bundle pending;

    interface Host {
        void play(File rom, PatchStore.Entry patch, int slot);
        <T> void job(String status, Callable<T> task, Consumer<T> done);
        void note(String message);
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

    void ips(File rom) {
        host.job("Reading patch profiles…", () -> patches.entries(rom), list -> showPatches(rom, list));
    }

    private void showPatches(File rom, List<PatchStore.Entry> entries) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(6));
        scroll.addView(content);
        content.addView(ui.text("Launch a separate patched variant. The original ZIP and its saves stay unchanged.", 14, MUTED, false));
        if (entries.isEmpty()) {
            ui.space(content, 9);
            content.addView(ui.text("No patches yet. To start, import a standard .ips for one ROM chip.", 14, INK, false));
        }
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("IPS patches · " + rom.getName())
                .setView(scroll).setPositiveButton("Import IPS", (d, w) -> pickTarget(rom))
                .setNegativeButton("Close", null).create();
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
            labels.addView(ui.text(entry.chip.detail(), 12, MUTED, false));
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
                new String[]{"Play patched game", "Save states for this patch", "Choose preview image", "Remove patch"}, (d, i) -> {
                    if (i == 0) host.play(rom, patch, 0);
                    else if (i == 1) states(rom, patch);
                    else if (i == 2) pick(rom, PICK_PREVIEW, patch.id, "image/*");
                    else new AlertDialog.Builder(activity).setTitle("Remove this patch?")
                                .setMessage("The ROM and save files are kept. Re-import the same patch to access its own saves again.")
                                .setNegativeButton("Cancel", null).setPositiveButton("Remove", (x, y) ->
                                        host.job("Removing patch…", () -> { patches.remove(patch); return patch.title; }, name -> {
                                            host.note("Patch removed."); ips(rom);
                                        })).show();
                }).setNegativeButton("Back", (d, w) -> ips(rom)).show();
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
            activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_LOCAL_ONLY, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), request);
        } catch (ActivityNotFoundException unavailable) {
            pending = null;
            host.note("No system file picker is available.");
        }
    }

    boolean result(int request, int result, Intent data) {
        if (request != PICK_IPS && request != PICK_CHEATS && request != PICK_PREVIEW) return false;
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
        if (request == PICK_CHEATS) {
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
