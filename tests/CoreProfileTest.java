package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CoreProfileTest {
    private interface Read { void run() throws IOException; }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("core-options-");
        try {
            Map<String, String> choices = new LinkedHashMap<>();
            choices.put("fbneo-samplerate", "44100");
            choices.put("fbneo-force-60hz", "enabled");
            String first = CoreProfile.encode(choices);
            check(first.equals("fbneo-force-60hz=enabled\nfbneo-samplerate=44100\n"), "canonical sorted values");
            check(CoreProfile.decode(first).equals(choices), "round trip");
            for (String bad : new String[]{"fbneo-samplerate=44100", "fbneo-samplerate=44100\nfbneo-force-60hz=enabled\n",
                    "fbneo-allow-patched-romsets=enabled\n", "fbneo-hiscores=yes\0no\n", "fbneo-samplerate=\n",
                    "fbneo-samplerate=48000\nfbneo-samplerate=44100\n", "fbneo-samplerate=44100\r\n"}) {
                check(!CoreProfile.valid(bad), "reject malformed, noncanonical or nonportable profiles");
            }
            byte[] saveData = {4, 9, 84};
            Path rom = Files.write(root.resolve("game.zip"), new byte[] {40, 2});
            Path dir = root.resolve("states");
            StateStore regular = new StateStore(dir.toFile(), rom.toFile());
            StateStore tuned = new StateStore(dir.toFile(), rom.toFile(), null, first);
            regular.save(1, saveData);
            tuned.save(2, saveData);
            check(Arrays.equals(regular.load(1), saveData), "old-format default state still works");
            check(Arrays.equals(tuned.load(2), saveData), "nondefault state uses the same profile");
            rejected(() -> regular.load(2), "FBNeo settings");
            rejected(() -> tuned.load(1), "FBNeo settings");
            StateStore.validateBackupSlot(regular.slot(1).toPath());
            StateStore.validateBackupSlot(tuned.slot(2).toPath());
            choices.put("fbneo-samplerate", "48000");
            StateStore other = new StateStore(dir.toFile(), rom.toFile(), null, CoreProfile.encode(choices));
            rejected(() -> other.load(2), "FBNeo settings");
            QuickResumeStore resume = new QuickResumeStore(root.toFile());
            resume.save(tuned, saveData);
            rejected(() -> resume.load(regular), "FBNeo settings");
            rejected(() -> resume.load(other), "FBNeo settings");
            check(Arrays.equals(resume.load(tuned), saveData), "profiled Quick Resume round trip");

            Map<String, Object> prefs = new LinkedHashMap<>();
            prefs.put(CoreProfile.key("game.zip"), first);
            prefs.put(CoreProfile.key("../game.zip"), first);
            prefs.put(CoreProfile.key("other.zip"), "fbneo-allow-patched-romsets=enabled\n");
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            BackupSettings.write(new DataOutputStream(encoded), prefs);
            Map<String, Object> restored = BackupSettings.read(new DataInputStream(new ByteArrayInputStream(encoded.toByteArray())));
            check(restored.size() == 1 && first.equals(restored.get(CoreProfile.key("game.zip"))), "only allowed per-game settings transferred");

            byte[] pinned = Files.readAllBytes(Paths.get(args[0]));
            rejected(() -> HighScoreData.install(root.toFile(), new ByteArrayInputStream(new byte[7])), "hiscore.dat");
            check(!HighScoreData.installed(root.toFile()), "no file on rejection");
            HighScoreData.install(root.toFile(), new ByteArrayInputStream(pinned));
            check(HighScoreData.verified(root.toFile()), "exact pinned DAT installed");
            pinned[pinned.length - 1] ^= 1;
            rejected(() -> HighScoreData.install(root.toFile(), new ByteArrayInputStream(pinned)), "hiscore.dat");
            check(HighScoreData.verified(root.toFile()), "rejected replacement left original intact");
            Path score = Files.write(root.resolve("fbneo/test.hi"), saveData);
            HighScoreData.remove(root.toFile());
            check(!HighScoreData.installed(root.toFile()) && Files.exists(score), "removing data leaves score files");
            System.out.println("FBNeo profile encoding, save/Quick Resume compatibility, portable settings and exact hiscore.dat checks passed.");
        } finally { LocalFiles.deleteTree(root.toFile()); }
    }

    private static void rejected(Read read, String reason) throws IOException {
        try { read.run(); }
        catch (IOException expected) {
            check(expected.getMessage().contains(reason), "expected reason: " + expected.getMessage());
            return;
        }
        throw new AssertionError("expected failure: " + reason);
    }

    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
