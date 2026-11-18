package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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
        zip(file, java.util.Collections.singletonMap(member, data));
    }

    private static void zip(File file, Map<String, byte[]> members) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            for (Map.Entry<String, byte[]> e : members.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue()); out.closeEntry();
            }
        }
    }

    private static void testPack(File root) throws IOException {
        File rom = new File(root, "pair.zip");
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("main.bin", new byte[]{0,1,2,3,4,5,6,7,8,9});
        files.put("pal.bin", new byte[]{9,8,7,6,5,4,3,2,1,0});
        zip(rom, files);
        StringBuilder index = new StringBuilder();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            CRC32 crc = new CRC32(); crc.update(e.getValue());
            index.append(e.getKey()).append("\t10\t").append(Long.toHexString(crc.getValue())).append('\n');
        }
        List<PatchStore.Chip> targets = PatchStore.targets(rom, new ByteArrayInputStream(ascii(index.toString())));
        byte[] palette = {'P','A','T','C','H', 0,0,2, 0,0, 0,2, 0x55, 'E','O','F'};
        byte[] later = IPS.clone(); later[10] = 0x66;
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        payloads.put("code.ips", IPS);
        payloads.put("color.ips", palette);
        LegacyIps.OpenPatch pick = name -> {
            if (!payloads.containsKey(name)) throw new IOException("Missing fixture IPS");
            return new ByteArrayInputStream(payloads.get(name));
        };
        byte[] dat = ("\ufeff# map\r\nMAIN.bin code\r\npal.bin pair/Color.IPS CRC("
                + Long.toHexString(targets.get(1).crc) + ")\r\n[en_US]\r\nDescription only\r\n").getBytes(StandardCharsets.UTF_8);
        List<PatchStore.Part> parts = LegacyIps.load("pair", dat, targets, pick);
        check(parts.size() == 2 && parts.get(0).target.name.equals("main.bin"));
        for (String rejected : Arrays.asList("[en_US]\nmain.bin code", "main.bin ../code", "main.bin /code",
                "main.bin other/code", "main.bin code CRC(01234567)", "main.bin code resize=64", "main.bin missing", "absent.bin code")) {
            reject(() -> LegacyIps.load("pair", ascii(rejected), targets, pick));
        }
        reject(() -> LegacyIps.load("pair", ascii(String.join("", java.util.Collections.nCopies(9, "main.bin code\n"))), targets, pick));
        PatchStore patches = new PatchStore(new File(root, "patches"));
        PatchStore.Entry pair = patches.add(rom, "Two chips", parts);
        PatchStore.Entry overlay = patches.add(rom, "Final layer", targets.get(0), later);
        PatchStore.Entry variant = patches.combine(rom, "Together", Arrays.asList(pair, overlay));
        check(variant.targets.size() == 3 && variant.detail().equals("3 IPS files · 2 chip(s)"));
        check(!variant.id.equals(patches.combine(rom, "Opposite", Arrays.asList(overlay, pair)).id));
        check(variant.id.equals(patches.combine(rom, "Named again", Arrays.asList(pair, overlay)).id));
        reject(() -> patches.combine(rom, "Too many", java.util.Collections.nCopies(5, pair)));
        patches.remove(pair); patches.remove(overlay); // The combined profile owns its bytes.
        File override = new File(root, "pack/pair.zip");
        byte[] base = LocalFiles.hash(rom, RomStore.MAX_BYTES);
        PatchStore.stage(rom, variant, override);
        try (ZipFile zip = new ZipFile(override)) {
            check(zip.size() == 2);
            try (java.io.InputStream in = zip.getInputStream(zip.getEntry("main.bin"))) { check(LocalFiles.read(in, 10)[7] == 0x66); }
            try (java.io.InputStream in = zip.getInputStream(zip.getEntry("pal.bin"))) { check(LocalFiles.read(in, 10)[3] == 0x55); }
        }
        check(Arrays.equals(base, LocalFiles.hash(rom, RomStore.MAX_BYTES)));
        StateStore grouped = new StateStore(new File(root, "states"), rom, variant.id);
        grouped.save(1, new byte[]{44});
        check(grouped.load(1)[0] == 44 && !new StateStore(new File(root, "states"), rom, pair.id).slot(1).exists());
        byte[] staged = LocalFiles.hash(override, RomStore.MAX_BYTES);
        files.get("pal.bin")[0] = 66; zip(rom, files);
        reject(() -> PatchStore.stage(rom, variant, override));
        check(Arrays.equals(staged, LocalFiles.hash(override, RomStore.MAX_BYTES))); // No partial override from a failed stage.
        byte[] damaged = Files.readAllBytes(variant.file.toPath()); damaged[damaged.length - 5] ^= 1;
        Files.write(variant.file.toPath(), damaged);
        reject(() -> PatchStore.stage(rom, variant, override));
    }

    private static void unit(File root) throws IOException {
        File rom = new File(root, "probe.zip");
        byte[] chip = new byte[]{1,2,3,4,5,6,7,8,9,10};
        zip(rom, "chip.bin", chip);
        CRC32 crc = new CRC32(); crc.update(chip);
        byte[] index = ascii(String.format("chip.bin\t10\t%08x\nchip.bin\t19\t12345678\nmissing\t5\t23456789\n", crc.getValue()));
        List<PatchStore.Chip> targets = PatchStore.targets(rom, new ByteArrayInputStream(index));
        check(targets.size() == 1);
        File profiles = new File(root, "patches");
        // Independently serialized v0.4.0 profile and ID; old state paths must still resolve.
        File previous = new File(profiles, "probe.zip/4b63f9807ca7550440ad692587af6ef6.ipsp");
        Files.createDirectories(previous.getParentFile().toPath());
        try (DataOutputStream legacy = new DataOutputStream(Files.newOutputStream(previous.toPath()))) {
            legacy.writeInt(0x49505331);
            legacy.writeUTF("Demo.ips");
            legacy.writeUTF("chip.bin");
            legacy.writeInt(10);
            legacy.writeLong(crc.getValue());
            legacy.write(IPS);
        }
        PatchStore patches = new PatchStore(profiles);
        PatchStore.Entry profile = patches.entries(rom).get(0);
        check(profile.file.equals(previous));
        File scratch = new File(root, "sandbox/fbneo/patched/probe.zip");
        byte[] baseHash = LocalFiles.hash(rom, RomStore.MAX_BYTES);
        PatchStore.stage(rom, profile, scratch);
        try (ZipFile zip = new ZipFile(scratch); java.io.InputStream in = zip.getInputStream(zip.getEntry("chip.bin"))) {
            chip[7] = 0x44;
            check(Arrays.equals(chip, LocalFiles.read(in, chip.length)));
        }
        check(Arrays.equals(baseHash, LocalFiles.hash(rom, RomStore.MAX_BYTES)));
        check(patches.add(rom, "Same patch again", targets.get(0), IPS).id.equals(profile.id));
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
        testPack(root);
        System.out.println("Single/multi-file IPS, legacy DAT mapping, ordered apply, isolated states and custom cheats passed.");
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
            String first = System.getProperty("probe.chip"), second = System.getProperty("probe.palette");
            byte[] def = ascii(first + " code\n" + second + " palette\n[en_US]\nSynthetic probe\n");
            byte[] palette = {'P','A','T','C','H', 0,0,20, 0,1, 0x0e, 'E','O','F'};
            List<PatchStore.Part> parts = LegacyIps.load("gridlee", def, targets,
                    name -> new ByteArrayInputStream(name.equals("code.ips") ? IPS : palette));
            PatchStore.Entry patch = new PatchStore(data).add(rom, "Probe multi-chip DAT", parts);
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
