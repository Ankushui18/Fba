package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class QuickResumeTest {
    private static final String IPS = "a123456789abcdefa123456789abcdef";
    private static final byte[] IN_GAME = {12, 7, 5}, NEXT = {3, 16, 8, 19}, MANUAL = {7, 7};

    private interface Attempt { void run() throws IOException; }

    public static void main(String[] args) throws IOException {
        Path privateData = Files.createTempDirectory("quick-resume-test-");
        try {
            Path roms = Files.createDirectory(privateData.resolve("roms"));
            Path cache = Files.createDirectory(privateData.resolve("cache"));
            Path firstRom = Files.write(roms.resolve("first.zip"), new byte[] {8, 7}), lastRom = Files.write(roms.resolve("last.zip"), new byte[] {5, 6});
            Path saved = privateData.resolve("quick-resume/latest.state");
            StateStore first = state(privateData, firstRom, null), patched = state(privateData, lastRom, IPS);
            first.save(3, MANUAL);
            first.rename(3, "Keep me");
            patched.save(5, MANUAL);
            QuickResumeStore quick = new QuickResumeStore(privateData.toFile());
            check(!quick.exists() && quick.latest() == null, "no Continue target without an autosave");
            rejects(() -> quick.load(first), "yet");
            quick.save(first, IN_GAME);
            QuickResumeStore reopened = new QuickResumeStore(privateData.toFile());
            check(reopened.latest().rom.equals("first.zip") && reopened.latest().variant == null, "saved base game identity");
            check(Arrays.equals(reopened.load(state(privateData, firstRom, null)), IN_GAME), "resume after reopening");
            quick.save(patched, NEXT);
            check(reopened.latest().rom.equals("last.zip") && IPS.equals(reopened.latest().variant), "new exit selects the exact IPS profile");
            check(Arrays.equals(reopened.load(state(privateData, lastRom, IPS)), NEXT), "restored patched progress");
            rejects(() -> quick.load(first), "target changed");
            rejects(() -> quick.load(state(privateData, lastRom, null)), "target changed");
            rejects(() -> quick.load(state(privateData, lastRom, "b" + IPS.substring(1))), "target changed");

            byte[] valid = Files.readAllBytes(saved);
            rejects(() -> quick.save(first, null), "doesn't support");
            rejects(() -> quick.save(first, new byte[0]), "doesn't support");
            rejects(() -> quick.save(state(privateData, roms.resolve("missing.zip"), null), IN_GAME), null);
            check(Arrays.equals(Files.readAllBytes(saved), valid), "failed saves keep both payload and target");
            try (Stream<Path> files = Files.list(saved.getParent())) { check(files.count() == 1, "only one committed save, no failed temp files"); }

            Files.write(lastRom, new byte[] {5, 7});
            rejects(() -> reopened.load(state(privateData, lastRom, IPS)), "ROM revision");
            Files.write(lastRom, new byte[] {5, 6});
            try (RandomAccessFile broken = new RandomAccessFile(saved.toFile(), "rw")) {
                broken.seek(broken.length() - 1);
                broken.write(0x9a);
            }
            rejects(() -> reopened.load(patched), "damaged");
            Files.write(saved, Arrays.copyOf(valid, valid.length - 1));
            rejects(() -> quick.load(patched), null);
            byte[] unsupported = valid.clone();
            unsupported[7] = 42; // Version is the second big-endian integer.
            Files.write(saved, unsupported);
            check(quick.latest() == null && quick.exists(), "unknown format isn't offered, but can be cleared");
            rejects(() -> reopened.load(patched), "Unsupported");
            Files.write(saved, valid);

            ByteArrayOutputStream zip = new ByteArrayOutputStream();
            Map<String, Object> options = new HashMap<>();
            options.put("quick_resume_enabled", true);
            options.put("muted", true);
            check(BackupStore.exportZip(privateData, options, zip) == 2, "ZIP only counts manual slots");
            Path restored = privateData.resolve("new-phone");
            BackupStore.restoreZip(restored, cache, new ByteArrayInputStream(zip.toByteArray()), prefs -> {
                check(prefs.size() == 1 && Boolean.TRUE.equals(prefs.get("muted")), "Quick Resume opt-in is device-local");
            });
            check(!new QuickResumeStore(restored.toFile()).exists(), "manual backup doesn't transfer the autosave");

            reopened.clear();
            check(quick.latest() == null && !quick.exists(), "clear removes only Quick Resume");
            reopened.clear(); // Idempotent.
            check(Arrays.equals(first.load(3), MANUAL) && first.label(3).endsWith("Keep me")
                    && Arrays.equals(patched.load(5), MANUAL), "manual saves/labels survive all autosave actions");
            System.out.println("Quick Resume: persisted solo/IPS checkpoints, mismatch/CRC checks, atomic failure safety and manual/backup isolation passed.");
        } finally { LocalFiles.deleteTree(privateData.toFile()); }
    }

    private static StateStore state(Path data, Path rom, String variant) {
        return new StateStore(data.resolve("states").toFile(), rom.toFile(), variant);
    }

    private static void rejects(Attempt action, String message) throws IOException {
        try { action.run(); }
        catch (IOException rejected) {
            if (message != null && (rejected.getMessage() == null || !rejected.getMessage().contains(message))) throw rejected;
            return;
        }
        throw new AssertionError("accepted wrong or damaged auto-save: " + message);
    }

    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
