package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class NesStoreTest {
    private static final String CART = "nes_1942.zip";

    public static void main(String[] args) throws Exception {
        NesStore bundled = NesStore.load(Files.newInputStream(Paths.get(args[0])));
        NesStore.Entry mario = bundled.entries().stream().filter(e -> e.set.equals("smb")).findFirst().orElseThrow(AssertionError::new);
        check(mario.archive().equals("nes_smb.zip") && mario.size == 40976 && mario.crc == 0x3337ec46L
                && mario.title.equals("Super Mario Bros. (World)"), "Mario DAT metadata only");
        byte[] fixture = new byte[40976]; // Synthetic cartridge; no original game data.
        fixture[0] = 'N'; fixture[1] = 'E'; fixture[2] = 'S'; fixture[3] = 0x1a;
        fixture[4] = 2; fixture[5] = 1;
        CRC32 crc = new CRC32(); crc.update(fixture);
        String row = String.format(Locale.ROOT, "1942\t%d\t%08x\t2026\tTest cartridge\n", fixture.length, crc.getValue());
        NesStore index = NesStore.load(text(row));
        Path root = Files.createTempDirectory("fba-nes-test");
        try {
            Path roms = Files.createDirectory(root.resolve("roms"));
            byte[] arcadeZip = zip("board-chip.bin", new byte[]{1, 2, 3});
            RomStore.importZip(roms, "1942.zip", new ByteArrayInputStream(arcadeZip), index::archiveTarget);
            check(Arrays.equals(arcadeZip, Files.readAllBytes(roms.resolve("1942.zip"))) && !Files.exists(roms.resolve(CART)), "arcade name collision not routed to NES");
            check(index.importRaw(roms, "Test cartridge (World).NES", new ByteArrayInputStream(fixture)).equals(CART), "raw import identified by bytes");
            Path stored = roms.resolve(CART);
            try (ZipFile zip = new ZipFile(stored.toFile()); InputStream rom = zip.getInputStream(zip.getEntry("1942.nes"))) {
                check(Arrays.equals(fixture, LocalFiles.read(rom, fixture.length)), "converted ZIP keeps original bytes");
            }
            byte[] explicitZip = zip("different-inner-name.nes", fixture);
            RomStore.importZip(roms, "1942.ZIP", new ByteArrayInputStream(explicitZip), index::archiveTarget);
            check(Arrays.equals(arcadeZip, Files.readAllBytes(roms.resolve("1942.zip"))), "bare NES ZIP cannot overwrite arcade ZIP");
            check(Arrays.equals(explicitZip, Files.readAllBytes(stored)), "matching NES ZIP keeps its original bytes");

            StateStore slots = new StateStore(root.resolve("states").toFile(), stored.toFile());
            slots.save(1, new byte[] {7, 8});
            index.importRaw(roms, "copy.nes", new ByteArrayInputStream(fixture));
            check(Arrays.equals(explicitZip, Files.readAllBytes(stored)) && Arrays.equals(new byte[] {7, 8}, slots.load(1)), "re-adding equivalent raw ROM keeps archive and manual save fingerprint");
            Path stage = NesStore.stage(stored.toFile(), Files.createDirectory(root.resolve("run"))).toPath();
            check(stage.endsWith(Paths.get("nes", "1942.zip")) && Arrays.equals(explicitZip, Files.readAllBytes(stage)), "core path uses nes folder and unprefixed name");
            check(Arrays.equals(explicitZip, Files.readAllBytes(stored)), "staging never moves the library file");

            byte[] wrongRevision = fixture.clone(); wrongRevision[16] = 1;
            expectInvalid(() -> index.importRaw(roms, "Test.nes", new ByteArrayInputStream(wrongRevision)));
            expectInvalid(() -> index.importRaw(roms, "fake.nes", new ByteArrayInputStream(new byte[16])));
            expectInvalid(() -> index.importRaw(roms, "large.nes", new ByteArrayInputStream(new byte[10 * 1024 * 1024 + 1])));
            expectInvalid(() -> RomStore.importZip(roms, CART, new ByteArrayInputStream(arcadeZip), index::archiveTarget));
            expectInvalid(() -> NesStore.stage(roms.resolve("1942.zip").toFile(), Files.createDirectory(root.resolve("invalid-run"))));
            expectInvalid(() -> NesStore.load(text(row + row)));
            expectInvalid(() -> NesStore.load(text(row.replace("1942", "../1942"))));
            NesStore ambiguous = NesStore.load(text(row + row.replace("1942", "1942b")));
            expectInvalid(() -> ambiguous.importRaw(roms, "unknown.nes", new ByteArrayInputStream(fixture)));
            check(ambiguous.importRaw(roms, "1942b.nes", new ByteArrayInputStream(fixture)).equals("nes_1942b.zip"), "same-byte variants need an unambiguous set name");
            check(Arrays.equals(explicitZip, Files.readAllBytes(stored)), "rejections preserve library");
            try (java.util.stream.Stream<Path> files = Files.list(roms)) {
                check(files.noneMatch(f -> f.getFileName().toString().endsWith(".part")), "temporary imports cleaned up");
            }
        } finally { LocalFiles.deleteTree(root.toFile()); }
        System.out.println("NES exact-dump import, archive disambiguation, save fingerprint and core path tests passed.");
    }

    private static ByteArrayInputStream text(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }

    private static byte[] zip(String name, byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name)); zip.write(content); zip.closeEntry();
        }
        return output.toByteArray();
    }

    private interface Action { void run() throws IOException; }
    private static void expectInvalid(Action action) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("expected rejection");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
