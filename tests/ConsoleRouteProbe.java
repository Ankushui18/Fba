package io.github.ankushui18.fbaoffline;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.ZipFile;

/** Exercises the real importer/staging code before handing its paths to the native smoke test. */
public final class ConsoleRouteProbe {
    private final ConsoleStore store;
    private final Path root, library, raw, system, saves;

    private ConsoleRouteProbe(Path index, Path root) throws Exception {
        store = ConsoleStore.load(Files.newInputStream(index));
        this.root = root;
        library = Files.createDirectory(root.resolve("library"));
        raw = root.resolve("raw");
        system = Files.createDirectory(root.resolve("system"));
        saves = Files.createDirectory(root.resolve("saves"));
    }

    private Path add(Path file, String expected) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            String saved = GameImport.add(library, file.getFileName().toString(), in, store);
            if (expected == null ? !LooseNes.isArchive(saved) : !saved.equals(expected + ".zip")) throw new AssertionError(saved);
            return library.resolve(saved);
        }
    }

    private Path addWithPadding(String source, String padded, String expected) throws Exception {
        Path file = raw.resolve(padded);
        byte[] input = Files.readAllBytes(file);
        Path zip = add(file, expected);
        byte[] packed = Files.readAllBytes(zip);
        byte[] original = Files.readAllBytes(raw.resolve(source));
        try (ZipFile archive = new ZipFile(zip.toFile()); InputStream member = archive.getInputStream(archive.entries().nextElement())) {
            if (!Arrays.equals(original, LocalFiles.read(member, original.length))) throw new AssertionError("Only filler bytes may be removed.");
        }
        if (!zip.equals(add(raw.resolve(source), expected)) || !Arrays.equals(packed, Files.readAllBytes(zip))
                || !Arrays.equals(input, Files.readAllBytes(file))) {
            throw new AssertionError("Padded and exact bytes must share save identity without editing the source.");
        }
        return zip;
    }

    private void launch(BufferedWriter out, String title, String mode, Path zip) throws Exception {
        ConsoleStore.Launch launch = store.stage(zip.toFile(), Files.createDirectory(root.resolve(title)));
        Path save = Files.createDirectories(launch.saveDirectory(saves.toFile(), zip.toFile()).toPath());
        if (mode.equals("ninja") != launch.patched) throw new AssertionError("Patched mode must be scoped to the verified alternate dump.");
        if (LooseNes.isArchive(zip.getFileName().toString()) && (launch.sessionSystem == null || !launch.rom.getName().endsWith(".dat"))) {
            throw new AssertionError("Loose NES import must stage its own descriptor and system directory.");
        }
        if (mode.equals("bad-crc")) {
            Path descriptor = launch.rom.toPath();
            String bad = new String(Files.readAllBytes(descriptor), StandardCharsets.US_ASCII)
                    .replaceFirst("0x[0-9a-f]{8} BRF_ESS", "0x12345678 BRF_ESS");
            Files.write(descriptor, bad.getBytes(StandardCharsets.US_ASCII));
        }
        if (mode.equals("missing") != !launch.firmwareHint.isEmpty()) {
            // The GBA BIOS is optional and may legitimately be absent.
            if (!title.equals("gba")) throw new AssertionError("Unexpected BIOS requirements for " + title + ": " + launch.firmwareHint);
        }
        out.write(String.join("\t", title, mode, launch.rom.getAbsolutePath(),
                launch.sessionSystem == null ? system.toString() : launch.sessionSystem.getAbsolutePath(), save.toString()));
        out.newLine();
    }

    private void verify(Path localNinja) throws Exception {
        try (BufferedWriter out = Files.newBufferedWriter(root.resolve("launches.tsv"), StandardCharsets.UTF_8)) {
            Path nes = addWithPadding("Cartridge.nes", "Padded matching cartridge.nes", "nes_smb");
            launch(out, "nes-from-padded-dat", "ok", nes);
            launch(out, "snes", "ok", add(raw.resolve("Headered cartridge.smc"), "snes_supermarioworld"));
            launch(out, "genesis", "ok", add(raw.resolve("Genesis cartridge.bin"), "md_sonic"));
            launch(out, "sms", "ok", add(raw.resolve("Master System cartridge.sms"), "sms_alexkidd"));
            launch(out, "gba", "ok", add(raw.resolve("Handheld cartridge.gba"), "gba_2048"));
            Path disk = add(raw.resolve("Famicom disk.fds"), "fds_19neunzehn");
            launch(out, "fds-without-bios", "missing", disk);
            try (InputStream in = Files.newInputStream(raw.resolve("fdsbios.zip"))) {
                String bios = RomStore.importZip(library, "fdsbios.zip", in, store::archiveTarget);
                if (!bios.equals("bios_fds_fdsbios.zip")) throw new AssertionError(bios);
            }
            launch(out, "fds-with-bios", "ok", disk);
            launch(out, "loose-padded-nrom", "local-battery", addWithPadding("Homebrew.nes", "Padded NROM.nes", null));
            launch(out, "loose-no-extension", "local-battery", add(raw.resolve("Headerless filename"), null));
            launch(out, "loose-mmc1", "local-battery", add(raw.resolve("MMC1.NES"), null));
            launch(out, "loose-uxrom", "local", add(raw.resolve("UxROM.nes"), null));
            Path cnrom = add(raw.resolve("CNROM.nes"), null);
            launch(out, "loose-bad-crc", "bad-crc", cnrom);
            launch(out, "loose-cnrom", "local", cnrom);
            launch(out, "loose-mmc3-pal", "local-pal", add(raw.resolve("MMC3 PAL.nes"), null));
            launch(out, "loose-axrom", "local", add(raw.resolve("AxROM.nes"), null));
            launch(out, "loose-gxrom", "local", add(raw.resolve("GxROM.nes"), null));
            launch(out, "loose-mmc2", "local", add(raw.resolve("MMC2.nes"), null));
            launch(out, "loose-mmc4", "local-battery", add(raw.resolve("MMC4.nes"), null));
            launch(out, "loose-color-dreams", "local", add(raw.resolve("Color Dreams.nes"), null));
            launch(out, "loose-bnrom", "local", add(raw.resolve("BNROM.nes"), null));
            launch(out, "loose-nina001", "local", add(raw.resolve("NINA-001.nes"), null));
            launch(out, "loose-camerica", "local", add(raw.resolve("Camerica.nes"), null));
            launch(out, "loose-jaleco", "local", add(raw.resolve("Jaleco.nes"), null));
            launch(out, "loose-un1rom", "local", add(raw.resolve("UN1ROM.nes"), null));
            // A synthetic CRC collision must launch only through the separate generic path.
            launch(out, "loose-unverified-collision", "local", add(raw.resolve("Unverified NES collision.nes"), null));
            launch(out, "nes-after-loose", "ok", nes);
            if (localNinja != null) {
                Path ninja = add(localNinja, "nes_ninjagaiden_alt_ed1d282b");
                try (ZipFile zip = new ZipFile(ninja.toFile()); InputStream content = zip.getInputStream(zip.entries().nextElement())) {
                    if (!Arrays.equals(Files.readAllBytes(localNinja), LocalFiles.read(content, 262160))) throw new AssertionError("The original ROM bytes must not change.");
                }
                launch(out, "ninja", "ninja", ninja);
                launch(out, "nes-after-ninja", "ok", nes);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new ConsoleRouteProbe(Paths.get(args[0]), Paths.get(args[1])).verify(args.length > 2 ? Paths.get(args[2]) : null);
    }
}
