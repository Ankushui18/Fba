package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BackupStoreTest {
    private static final String ROM = "test.zip", EXTRA = "other.zip";
    private static final String BASE = "states/" + ROM + "/slot-1.state";
    private static final String IPS = "13456789abcdef0213456789abcdef02";
    private static final byte[] ROM_BYTES = {8, 33, 5};
    private static final byte[] SAVED = {15, 10, 80, 6, 9};
    private static final byte[] EXISTING = {4, 3, 2, 1};

    private interface Attempt { void run() throws IOException; }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("backup-test-");
        try {
            Path cache = Files.createDirectory(root.resolve("cache"));
            Path from = root.resolve("old-phone"), to = root.resolve("new-phone"), occupied = root.resolve("occupied");
            StateStore regular = store(from, ROM, null), patched = store(from, ROM, IPS);
            regular.save(1, SAVED);
            regular.rename(1, "Boss 2 · नया");
            regular.save(6, SAVED);
            patched.save(1, new byte[] {77});
            patched.rename(1, "IPS · スタート");
            Files.write(regular.preview(1).toPath(), new byte[] {1});
            write(from.resolve("saves/fbneo/test.fs"), new byte[] {92});
            write(from.resolve("patches/" + ROM + "/" + IPS + ".profile"), new byte[] {83});

            Map<String, Object> portable = new LinkedHashMap<>();
            PadProfile layout = new PadProfile();
            layout.y[1] = 0.64f;
            portable.put("muted", true);
            portable.put("skin", "CLASSIC_CIRCLE");
            portable.put("image_filter", 4);
            portable.put("tiles", true);
            portable.put("sorting", 2);
            portable.put("pad_portrait", layout.encode());
            portable.put("game:" + ROM + ":custom", true);
            portable.put("game:" + ROM + ":frame_skip", 3);
            portable.put("game:" + ROM + ":face_combo_2", PadBindings.FACE_BITS[3]);
            portable.put("game:" + ROM + ":key_15", 225);
            Map<String, Object> preferences = new LinkedHashMap<>(portable);
            preferences.put("rom_folder_uri", "content://old/folder");
            preferences.put("favorites", Collections.singleton(ROM));
            preferences.put("recent_roms", ROM);
            preferences.put("game:" + ROM + ":skin", "imported:" + IPS + ":round");
            preferences.put("game:../test.zip:custom", true);
            preferences.put("unknown", "not portable");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            check(BackupStore.exportZip(from, preferences, out) == 3, "counted all ordinary and IPS slots");
            byte[] exported = out.toByteArray();
            Map<String, Object> newPrefs = new LinkedHashMap<>();
            newPrefs.put("rom_folder_uri", "content://new/folder");
            newPrefs.put("pad_haptic", true);
            newPrefs.put("image_filter", 0);
            BackupStore.Restored restored = restore(to, cache, exported, newPrefs::putAll);
            check(restored.slots == 3 && restored.kept == 0 && restored.settings == portable.size(), "fresh restore counts");
            Map<String, Object> expectedPrefs = new LinkedHashMap<>(portable);
            expectedPrefs.put("rom_folder_uri", "content://new/folder");
            expectedPrefs.put("pad_haptic", true);
            check(newPrefs.equals(expectedPrefs), "typed settings restored without overwriting unrelated prefs or grants");
            check(!Files.exists(to.resolve("roms")) && !Files.exists(to.resolve("patches")) && !Files.exists(to.resolve("saves")), "only save slots and settings transfer");

            StateStore onNew = store(to, ROM, null);
            check(Arrays.equals(onNew.load(1), SAVED) && Arrays.equals(onNew.load(6), SAVED), "base slots load after ROM reimport");
            check(onNew.label(1).endsWith("Boss 2 · नया") && !onNew.preview(1).exists(), "slot titles transfer without thumbnails");
            StateStore newIps = store(to, ROM, IPS);
            check(Arrays.equals(newIps.load(1), new byte[] {77}) && newIps.label(1).endsWith("IPS · スタート"), "IPS save isolated and loadable");
            Files.write(to.resolve("roms/" + ROM), new byte[] {8, 33, 6});
            rejected(() -> new StateStore(to.resolve("states").toFile(), to.resolve("roms/" + ROM).toFile()).load(1), "ROM revision");

            StateStore existing = store(occupied, ROM, null);
            existing.save(1, EXISTING);
            existing.rename(1, "Keep this");
            byte[] preview = {23, 19};
            Files.write(existing.preview(1).toPath(), preview);
            StateStore unrelated = store(occupied, EXTRA, null);
            unrelated.save(2, EXISTING);
            restored = restore(occupied, cache, exported, p -> {});
            check(restored.slots == 2 && restored.kept == 1, "only empty slots filled");
            check(Arrays.equals(existing.load(1), EXISTING) && existing.label(1).endsWith("Keep this")
                    && Arrays.equals(Files.readAllBytes(existing.preview(1).toPath()), preview), "preserved occupied slot, name and preview");
            check(Arrays.equals(unrelated.load(2), EXISTING), "preserved an unrelated game's slot");
            restored = restore(occupied, cache, exported, p -> {});
            check(restored.slots == 0 && restored.kept == 3, "repeat restore doesn't overwrite");

            rejectsInvalid(root, cache, exported, Files.readAllBytes(regular.slot(1).toPath()));
            Path failed = root.resolve("failed-commit");
            StateStore preserved = store(failed, ROM, null);
            preserved.save(1, EXISTING);
            rejected(() -> restore(failed, cache, exported, p -> { throw new IOException("settings disk full"); }), "settings disk full");
            check(Arrays.equals(preserved.load(1), EXISTING) && !preserved.slot(6).exists()
                    && !Files.exists(failed.resolve("states/" + ROM + "/ips/" + IPS + "/slot-1.state")), "rolled back all additions on commit failure");

            out.reset();
            check(BackupStore.exportZip(root.resolve("empty-library"), portable, out) == 0, "settings-only export");
            restored = restore(root.resolve("settings-only"), cache, out.toByteArray(), p -> check(p.equals(portable), "settings-only restore"));
            check(restored.slots == 0 && restored.settings == portable.size(), "settings-only restore counts");
            try (Stream<Path> leftovers = Files.list(cache)) { check(leftovers.count() == 0, "no private staging files left"); }
            System.out.println("Backup ZIP round trips, IPS isolation, settings, malformed archives and no-overwrite restore tests passed.");
        } finally { LocalFiles.deleteTree(root.toFile()); }
    }

    private static void rejectsInvalid(Path root, Path cache, byte[] exported, byte[] validSlot) throws IOException {
        Path target = root.resolve("invalid-target");
        StateStore live = store(target, ROM, null);
        live.save(2, EXISTING);
        live.rename(2, "Original");
        BackupStore.SettingsWriter never = p -> { throw new AssertionError("invalid ZIP touched preferences"); };
        rejected(() -> restore(target, cache, Arrays.copyOf(exported, exported.length - 4), never), null);
        rejected(() -> restore(target, cache, new byte[600], never), null);
        rejected(() -> restore(target, cache, zip(BASE, validSlot), never), "ZIP exported");
        byte[] prefs = settings();
        for (String path : new String[] {"../outside.state", "/" + BASE, "states\\test.zip\\slot-1.state",
                "states/../slot-1.state", "states/TEST.ZIP/slot-1.state", "states/test.zip/slot-7.state",
                "roms/test.zip", "states/test.zip/ips/../slot-1.state"}) {
            rejected(() -> restore(target, cache, zip(BASE, validSlot, "settings.dat", prefs, path, validSlot), never), null);
        }
        byte[] duplicate = zip(BASE, validSlot, "settings.dat", prefs, BASE.replace("slot-1", "slot-3"), validSlot);
        replace(duplicate, "slot-3.state", "slot-1.state");
        rejected(() -> restore(target, cache, duplicate, never), "duplicate");
        rejected(() -> restore(target, cache, zip("settings.dat", prefs, BASE + ".name", new byte[] {'A'}), never), "no matching save");
        byte[] wrongCrc = zip(BASE, validSlot, "settings.dat", prefs);
        int payload = 30 + BASE.length(); // Local header for the first STORED entry.
        wrongCrc[payload] ^= 1;
        rejected(() -> restore(target, cache, wrongCrc, never), "damaged");
        byte[] wrongState = validSlot.clone();
        wrongState[wrongState.length - 1] ^= 1; // ZIP CRC rebuilt, state CRC not rebuilt.
        rejected(() -> restore(target, cache, zip(BASE, wrongState, "settings.dat", prefs), never), "damaged");
        byte[] wrongVersion = prefs.clone();
        wrongVersion[7] = 2;
        rejected(() -> restore(target, cache, zip(BASE, validSlot, "settings.dat", wrongVersion), never), "supported");
        rejected(() -> restore(target, cache, zip(BASE, validSlot, "settings.dat", settings("rom_folder_uri")), never), "setting");
        rejected(() -> restore(target, cache, zip(BASE, validSlot, "settings.dat", settings("image_filter")), never), "setting");
        rejected(() -> restore(target, cache, zip(BASE, validSlot, "settings.dat", settings("tiles", "tiles")), never), "duplicate");
        rejected(() -> restore(target, cache, zip(BASE, validSlot, "settings.dat", new byte[BackupSettings.MAX_BYTES + 1]), never), "size limits");
        rejected(() -> BackupStore.restoreZip(target, cache, new ByteArrayInputStream(exported) {
            @Override public void close() throws IOException { throw new IOException("provider read failed"); }
        }, never), "provider read failed");
        check(!Files.exists(root.resolve("outside.state")) && !live.slot(1).exists() && !live.slot(6).exists()
                && Arrays.equals(live.load(2), EXISTING) && live.label(2).endsWith("Original"), "invalid ZIP left user data untouched");
    }

    private static void replace(byte[] data, String old, String replacement) {
        byte[] from = old.getBytes(StandardCharsets.US_ASCII), to = replacement.getBytes(StandardCharsets.US_ASCII);
        check(from.length == to.length, "fixture replacement keeps offsets");
        int count = 0;
        for (int i = 0; i <= data.length - from.length; i++) {
            int j = 0;
            while (j < from.length && data[i + j] == from[j]) j++;
            if (j == from.length) { System.arraycopy(to, 0, data, i, to.length); count++; }
        }
        check(count == 2, "changed local and central ZIP names");
    }

    private static byte[] zip(Object... entries) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(raw)) {
            for (int i = 0; i < entries.length; i += 2) {
                ZipEntry item = new ZipEntry((String) entries[i]);
                byte[] bytes = (byte[]) entries[i + 1];
                CRC32 crc = new CRC32();
                crc.update(bytes);
                item.setMethod(ZipEntry.STORED);
                item.setSize(bytes.length);
                item.setCrc(crc.getValue());
                zip.putNextEntry(item);
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return raw.toByteArray();
    }

    private static byte[] settings(String... booleans) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(0x46424142);
            out.writeInt(1);
            out.writeInt(booleans.length);
            for (String name : booleans) { out.writeUTF(name); out.writeByte(1); }
        }
        return raw.toByteArray();
    }

    private static void rejected(Attempt task, String reason) throws IOException {
        try { task.run(); }
        catch (IOException expected) {
            if (reason != null && (expected.getMessage() == null || !expected.getMessage().contains(reason))) throw expected;
            return;
        }
        throw new AssertionError("bad data accepted: " + reason);
    }

    private static BackupStore.Restored restore(Path target, Path cache, byte[] archive, BackupStore.SettingsWriter prefs) throws IOException {
        return BackupStore.restoreZip(target, cache, new ByteArrayInputStream(archive), prefs);
    }

    private static StateStore store(Path root, String romName, String patch) throws IOException {
        return new StateStore(root.resolve("states").toFile(), write(root.resolve("roms/" + romName), ROM_BYTES).toFile(), patch);
    }

    private static Path write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.write(file, bytes);
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
