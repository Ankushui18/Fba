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
            byte[] padded = Arrays.copyOf(nrom, nrom.length + 512);
            check(name.equals(add(library, "Padded.nes", padded, sets)), "uniform padding preserves cartridge identity");
            Arrays.fill(padded, nrom.length, padded.length, (byte) 0xff);
            check(name.equals(add(library, "Other padding.nes", padded, sets)) && Arrays.equals(packed, Files.readAllBytes(archive)),
                    "FF padding, same saved bytes");
            padded[padded.length - 1] = 1;
            expect("extra bytes", () -> add(library, "trailing data.nes", padded, sets));
            expect("extra bytes", () -> add(library, "oversized tail.nes", Arrays.copyOf(nrom, nrom.length + 513), sets));
            expect("extra bytes", () -> {
                byte[] miscRom = Arrays.copyOf(pal, pal.length + 64);
                miscRom[14] = 1; // NES 2.0 declares additional ROM data, not padding.
                add(library, "extra rom.nes", miscRom, sets);
            });
            expect("mapper 255", () -> add(library, "mapper.nes", cart(2, 1, 255), sets));
            expect("Mapper 0 has an unsupported ROM layout", () -> add(library, "layout.nes", cart(4, 1, 0), sets));
            expect("Mapper 9 has an unsupported ROM layout", () -> add(library, "invalid MMC2.nes", cart(8, 0, 9), sets));
            expect("Mapper 11 has an unsupported ROM layout", () -> add(library, "oversized Color Dreams.nes", cart(16, 16, 11), sets));
            expect("Mapper 34 has an unsupported ROM layout", () -> add(library, "oversized NINA-001.nes", cart(8, 8, 34), sets));
            expect("Mapper 87 has an unsupported ROM layout", () -> add(library, "Jaleco without CHR ROM.nes", cart(2, 0, 87), sets));
            expect("Mapper 94 has an unsupported ROM layout", () -> add(library, "UN1ROM with CHR ROM.nes", cart(8, 1, 94), sets));
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
            expect("Mapper 4, submapper 1", () -> {
                byte[] board = pal.clone(); board[8] = 0x10;
                add(library, "MMC6.nes", board, sets);
            });
            expect("Mapper 34, submapper 2", () -> {
                byte[] wrongBoard = cart(2, 4, 34); wrongBoard[7] |= 8; wrongBoard[8] = 0x20;
                add(library, "BNROM with CHR ROM.nes", wrongBoard, sets);
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
