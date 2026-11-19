package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

public final class LooseNesTest {
    public static void main(String[] args) throws Exception {
        ConsoleStore sets = ConsoleStore.load(Files.newInputStream(Paths.get(args[0])));
        Path root = Files.createTempDirectory("fba-loose-nes");
        try {
            Path library = root.resolve("roms"), saves = root.resolve("saves");
            byte[] nrom = cart(2, 1, 0);
            String name = add(library, "Homebrew (World).NES", nrom, sets);
            check(LooseNes.isArchive(name) && name.equals("nes_local_" + LocalFiles.hex(LocalFiles.sha256().digest(nrom), 32) + ".zip"), "content identity, no DAT entry necessary");
            Path archive = library.resolve(name);
            byte[] packed = Files.readAllBytes(archive);
            check(name.equals(add(library, "Renamed.nes", nrom, sets)), "re-add under another name, no duplicate");
            check(name.equals(add(library, "provider-id-with-no-extension", nrom, sets)), "missing extension from provider");
            check(name.equals(add(library, null, nrom, sets)), "missing name from provider");
            check(name.equals(add(library, "application.bin", nrom, sets)), "generic provider suffix");
            check(name.equals(GameImport.add(library, null, new ByteArrayInputStream(nrom) {
                @Override public synchronized int read(byte[] bytes, int off, int count) {
                    return super.read(bytes, off, Math.min(count, 1)); // Provider chunks its header.
                }
            }, sets)), "partial provider reads are replayed correctly");
            check(Arrays.equals(packed, Files.readAllBytes(archive)), "re-add keeps state fingerprint");
            Path slots = root.resolve("states");
            byte[] snapshot = {17, 27, 37};
            StateStore states = new StateStore(slots.toFile(), archive.toFile());
            states.save(1, snapshot);
            QuickResumeStore resume = new QuickResumeStore(root.toFile());
            resume.save(states, snapshot);
            Files.delete(archive);
            check(name.equals(add(library, "Different name.nes", nrom, sets)) && Arrays.equals(packed, Files.readAllBytes(archive)),
                    "archive bytes don't depend on title, even after removal/reimport");
            StateStore reopened = new StateStore(slots.toFile(), archive.toFile());
            check(Arrays.equals(snapshot, reopened.load(1)) && Arrays.equals(snapshot, resume.load(reopened)),
                    "manual slot and Quick Resume survive removal/reimport");
            check(LooseNes.displayName("  Homebrew (World).NES ").equals("Homebrew (World)"), "human-readable library title");
            check(LooseNes.displayName("\u202E\nTest.NES").equals("Test"), "strip display control characters");

            ConsoleStore.Launch launch = sets.stage(archive.toFile(), Files.createDirectory(root.resolve("play")));
            check(!launch.patched && launch.sessionSystem.isDirectory() && launch.rom.toPath().endsWith(Paths.get("nes", "fba_local.dat")), "private RomData path with patched-ROM mode off");
            String description = new String(Files.readAllBytes(launch.rom.toPath()), StandardCharsets.US_ASCII);
            check(description.startsWith("DrvName nes_smb\nZipName nes_fba_local\n") && description.contains("image.nes 0xa010 0x"), "single fixed descriptor, not user data");
            try (ZipFile staged = new ZipFile(launch.rom.toPath().resolveSibling("fba_local.zip").toFile());
                 InputStream in = staged.getInputStream(staged.getEntry("cartridge.nes"))) {
                check(staged.size() == 1 && Arrays.equals(nrom, LocalFiles.read(in, nrom.length)), "unchanged bytes handed to FBNeo");
            }

            byte[] pal = cart(2, 1, 4);
            pal[7] = 8; pal[12] = 1;  // NES 2.0, submapper 0, PAL
            String palName = add(library, "PAL.nes", pal, sets);
            ConsoleStore.Launch palLaunch = sets.stage(library.resolve(palName).toFile(), Files.createDirectory(root.resolve("pal")));
            check(!launch.saveDirectory(saves.toFile(), archive.toFile()).equals(palLaunch.saveDirectory(saves.toFile(), library.resolve(palName).toFile())),
                    "shared native driver still has independent battery dirs");
            byte[] archaic = cart(16, 16, 1);
            System.arraycopy("DiskDude!".getBytes(StandardCharsets.US_ASCII), 0, archaic, 7, 9);
            check(LooseNes.isArchive(add(library, "Older header.nes", archaic, sets)), "legacy iNES DiskDude header");

            expect("isn't a valid .nes", () -> add(library, "fake.nes", new byte[40976], sets));
            expect("incomplete", () -> add(library, "truncated.nes", Arrays.copyOf(nrom, nrom.length - 1), sets));
            expect("extra data", () -> add(library, "trailing.nes", Arrays.copyOf(nrom, nrom.length + 1), sets));
            expect("mapper 255", () -> add(library, "mapper.nes", cart(2, 1, 255), sets));
            expect("ROM layout", () -> add(library, "layout.nes", cart(4, 1, 0), sets));
            expect("standard home NES", () -> {
                byte[] vs = nrom.clone(); vs[7] = 1;
                add(library, "vs.nes", vs, sets);
            });
            expect("extended ROM sizes", () -> {
                byte[] extended = pal.clone(); extended[9] = 0x0f;
                add(library, "extended.nes", extended, sets);
            });
            expect("controller variant", () -> {
                byte[] zapper = pal.clone(); zapper[15] = 8;
                add(library, "zapper.nes", zapper, sets);
            });
            expect("Choose a .nes", () -> add(library, "not-a-rom", new byte[]{1, 2, 3}, sets));
            expect("CD disc images", () -> add(library, "disc.chd", nrom, sets));
            expect("Old .fba", () -> add(library, "game.fba", nrom, sets));
            expect("set name", () -> add(library, name, packed, sets)); // private wrapper isn't a user ZIP set

            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
                out.putNextEntry(new ZipEntry("cartridge.nes"));
                out.write(pal);
            }
            expect("copy is damaged", () -> sets.stage(archive.toFile(), Files.createDirectory(root.resolve("tampered"))));
            check(name.equals(add(library, "Verified again.nes", nrom, sets)), "re-add repairs damaged copy");
            check(Arrays.equals(packed, Files.readAllBytes(archive)), "repair restores original save identity");
            try (java.util.stream.Stream<Path> files = Files.list(library)) {
                check(files.noneMatch(p -> p.getFileName().toString().endsWith(".part")), "no partial imports");
            }
        } finally { LocalFiles.deleteTree(root.toFile()); }
        System.out.println("Loose NES: provider routing, iNES/NES 2.0 validation, stable archives, strict staging and save isolation passed.");
    }

    private static byte[] cart(int prg, int chr, int mapper) {
        byte[] data = new byte[16 + prg * 16384 + chr * 8192];
        data[0] = 'N'; data[1] = 'E'; data[2] = 'S'; data[3] = 0x1a;
        data[4] = (byte) prg; data[5] = (byte) chr; data[6] = (byte) (mapper << 4); data[7] = (byte) (mapper & 0xf0);
        return data;
    }

    private static String add(Path library, String name, byte[] data, ConsoleStore sets) throws IOException {
        return GameImport.add(library, name, new ByteArrayInputStream(data), sets);
    }

    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    private interface Action { void run() throws IOException; }
    private static void expect(String message, Action action) throws Exception {
        try { action.run(); throw new AssertionError("Unexpected import: " + message); }
        catch (ConsoleStore.Mismatch error) {
            if (!error.getMessage().contains(message)) throw error;
        }
    }
}
