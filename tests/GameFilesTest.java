package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class GameFilesTest {
    private static final byte[] IPS = {'P','A','T','C','H', 0,0,7, 0,1, 0x44, 'E','O','F'};

    interface Action { void run() throws IOException; }
    private static void reject(Action action) throws IOException {
        try { action.run(); throw new AssertionError("Accepted bad data"); }
        catch (IOException expected) { /* expected */ }
    }
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }

    private static void zip(File file, String member, byte[] data) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            out.putNextEntry(new ZipEntry(member)); out.write(data); out.closeEntry();
        }
    }

    private static void unit(File root) throws IOException {
        File rom = new File(root, "probe.zip");
        byte[] chip = new byte[]{1,2,3,4,5,6,7,8,9,10};
        zip(rom, "chip.bin", chip);
        CRC32 crc = new CRC32(); crc.update(chip);
        byte[] index = ascii(String.format("chip.bin\t10\t%08x\nchip.bin\t19\t12345678\nmissing\t5\t23456789\n", crc.getValue()));
        List<PatchStore.Chip> targets = PatchStore.targets(rom, new ByteArrayInputStream(index));
        check(targets.size() == 1);
        PatchStore patches = new PatchStore(new File(root, "patches"));
        PatchStore.Entry profile = patches.add(rom, "Demo.ips", targets.get(0), IPS);
        File scratch = new File(root, "sandbox/fbneo/patched/probe.zip");
        byte[] baseHash = LocalFiles.hash(rom, RomStore.MAX_BYTES);
        PatchStore.stage(rom, profile, scratch);
        try (ZipFile zip = new ZipFile(scratch); java.io.InputStream in = zip.getInputStream(zip.getEntry("chip.bin"))) {
            chip[7] = 0x44;
            check(Arrays.equals(chip, LocalFiles.read(in, chip.length)));
        }
        check(Arrays.equals(baseHash, LocalFiles.hash(rom, RomStore.MAX_BYTES)));
        check(patches.entries(rom).size() == 1);
        IpsPatch.validate(new byte[]{'P','A','T','C','H', 0,0,2, 0,0, 0,4, 11, 'E','O','F', 0,0,10}, 10);
        reject(() -> IpsPatch.validate(IPS, 7));
        reject(() -> IpsPatch.validate(Arrays.copyOf(IPS, IPS.length - 1), 10));
        reject(() -> IpsPatch.validate(new byte[]{'P','A','T','C','H', 'E','O','F'}, 10));
        reject(() -> IpsPatch.validate(new byte[]{'P','A','T','C','H', 0,0,2, 0,0, 0,0, 11, 'E','O','F'}, 10));
        chip[0] = 77;
        zip(rom, "chip.bin", chip);
        reject(() -> PatchStore.stage(rom, profile, scratch));
        patches.remove(profile);
        check(patches.entries(rom).isEmpty());

        File store = new File(root, "states");
        StateStore plain = new StateStore(store, rom);
        StateStore patched = new StateStore(store, rom, profile.id);
        plain.save(1, new byte[]{99}); patched.save(1, new byte[]{55});
        check(patched.load(1)[0] == 55 && plain.load(1)[0] == 99);
        Files.copy(plain.slot(1).toPath(), patched.slot(2).toPath());
        reject(() -> patched.load(2));
        patched.rename(1, "Round 2");
        check(patched.label(1).endsWith("Round 2") && plain.label(1).equals("Slot 1"));
        patched.delete(1);
        check(patched.label(1).equals("Slot 1"));
        check(plain.slot(1).isFile() && !patched.slot(1).exists());

        String input = "include \"outside\"\ncheat \"Unsafe syntax\"\n0 \"Disabled\"\n1 \"Invalid\", 8, 37, 4\n"
                + "cheat \"Health\" advanced {\ntype 1\ndefault 1\n0 \"Off\"\n1 \"Max/plus\",0,0x2200,0xff // comment\n}\n";
        UserCheats rules = new UserCheats(new File(root, "cheats"));
        UserCheats.Rules installed = rules.install(rom, input.getBytes(StandardCharsets.UTF_16));
        check(installed.groups == 1 && installed.skipped == 1);
        String cleaned = new String(rules.forRom(rom), StandardCharsets.UTF_8);
        check(cleaned.equals("cheat \"Health\"\ntype 1\ndefault 0\n0 \"Off\"\n1 \"Max/plus\", 0, 0x2200, 0xff\n\n"));
        reject(() -> rules.install(rom, ascii("include \"file\"\n")));
        reject(() -> rules.install(rom, ascii("cheat \"Bad\"\n0 \"Off\"\n1 \"On\",0,0x200,256")));
        check(rules.exists(rom) && rules.forRom(rom) != null);
        File cheatPack = new File(root, "cheat.zip");
        zip(cheatPack, "PROBE.INI", ascii(input));
        try (java.io.InputStream in = Files.newInputStream(cheatPack.toPath())) {
            check(rules.install(rom, "cheat.zip", in, root).groups == 1);
        }
        zip(rom, "chip.bin", new byte[]{1,2,3});
        check(rules.forRom(rom) == null);  // Replacing the ZIP cannot activate codes for a different revision.
        rules.remove(rom);
        check(!rules.exists(rom));
        System.out.println("IPS and user-cheat validation, stage/restore, and isolated state slots passed.");
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 5) {
            // Exercise the actual app staging path on the Linux FBNeo fixture.
            File rom = new File(args[0]), scratch = new File(args[1]);
            File data = new File(args[2]);
            File idx = new File(args[3]), ini = new File(args[4]);
            List<PatchStore.Chip> targets;
            try (ZipFile zip = new ZipFile(idx); java.io.InputStream in = zip.getInputStream(zip.getEntry("gridlee.tsv"))) {
                targets = PatchStore.targets(rom, in);
            }
            String first = System.getProperty("probe.chip");
            PatchStore.Chip target = targets.stream().filter(c -> c.name.equals(first)).findFirst().orElseThrow(AssertionError::new);
            PatchStore.Entry patch = new PatchStore(data).add(rom, "Probe IPS", target, IPS);
            PatchStore.stage(rom, patch, scratch);
            byte[] raw = Files.readAllBytes(ini.toPath());
            UserCheats user = new UserCheats(data);
            check(user.install(rom, raw).groups == 1);
            LocalFiles.atomicWrite(ini, user.forRom(rom));
            return;
        }
        Path root = Files.createTempDirectory("game-files-test");
        try { unit(root.toFile()); } finally { LocalFiles.deleteTree(root.toFile()); }
    }
}
