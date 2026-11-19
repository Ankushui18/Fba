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
            String saved = store.importRaw(library, file.getFileName().toString(), in);
            if (!saved.equals(expected + ".zip")) throw new AssertionError(saved);
            return library.resolve(saved);
        }
    }

    private void launch(BufferedWriter out, String title, String mode, Path zip) throws Exception {
        ConsoleStore.Launch launch = store.stage(zip.toFile(), Files.createDirectory(root.resolve(title)));
        Path save = Files.createDirectory(saves.resolve(title));
        if (mode.equals("ninja") != (launch.patchedSystem != null)) throw new AssertionError("Patched mode must be scoped to the verified alternate dump.");
        if (mode.equals("missing") != !launch.firmwareHint.isEmpty()) {
            // The GBA BIOS is optional and may legitimately be absent.
            if (!title.equals("gba")) throw new AssertionError("Unexpected BIOS requirements for " + title + ": " + launch.firmwareHint);
        }
        out.write(String.join("\t", title, mode, launch.rom.getAbsolutePath(),
                launch.patchedSystem == null ? system.toString() : launch.patchedSystem.getAbsolutePath(), save.toString()));
        out.newLine();
    }

    private void verify(Path localNinja) throws Exception {
        try (InputStream in = Files.newInputStream(raw.resolve("Unverified NES collision.nes"))) {
            try {
                store.importRaw(library, "Ninja Gaiden (USA).nes", in);
                throw new AssertionError("A checksum alone must not enable alternate dump mode.");
            } catch (ConsoleStore.Mismatch expected) { /* Strong hash did not match. */ }
        }
        try (BufferedWriter out = Files.newBufferedWriter(root.resolve("launches.tsv"), StandardCharsets.UTF_8)) {
            Path nes = add(raw.resolve("Cartridge.nes"), "nes_smb");
            launch(out, "nes", "ok", nes);
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
