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
import java.util.EnumSet;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ConsoleStoreTest {
    public static void main(String[] args) throws Exception {
        ConsoleStore bundled = ConsoleStore.load(Files.newInputStream(Paths.get(args[0])));
        EnumSet<ConsoleSystem> systems = EnumSet.noneOf(ConsoleSystem.class);
        for (ConsoleStore.Entry entry : bundled.entries()) if (!entry.firmware) systems.add(entry.system);
        check(systems.size() == ConsoleSystem.values().length && systems.size() == 19, "all pinned DAT systems");
        check(bundled.entries().stream().anyMatch(e -> e.driver().equals("snes_supermarioworld") && e.has(524288L << 32 | 0xb19ed489L)), "SNES release metadata");
        check(bundled.entries().stream().anyMatch(e -> e.driver().equals("md_sonic") && e.has(524288L << 32 | 0xf9394e97L)), "Genesis metadata");
        check(bundled.entries().stream().anyMatch(e -> e.driver().equals("nes_smb") && e.has(40976L << 32 | 0x3337ec46L)), "NES identity retained");
        check(bundled.entries().stream().anyMatch(e -> e.driver().equals("md_segacd") && e.firmware), "Sega CD BIOS is not a cartridge");

        byte[] cart = new byte[16 + 1024];
        cart[0] = 'N'; cart[1] = 'E'; cart[2] = 'S'; cart[3] = 0x1a;
        byte[] md = {6, 22, 5}, linked = {4, 12, 5}, fds = {18, 29, 39, 49}, bios = {1, 2, 3, 4};
        byte[] snes = new byte[1024]; snes[512] = 7;
        byte[] headered = new byte[512 + snes.length];
        System.arraycopy(snes, 0, headered, 512, snes.length);
        String nesRow = row("nes", "1942", "G", "1942 NES", "", "", cart);
        ConsoleStore index = ConsoleStore.load(text(nesRow + row("md", "1942", "G", "1942 MD", "", "", md)
                + row("md", "exact", "P", "Canvas", "", "", linked) + row("snes", "world", "G", "World", "", "", snes)
                + row("fds", "disk", "G", "Disk", "parent", "fdsbios", fds) + row("fds", "parent", "G", "Parent", "", "", linked)
                + row("fds", "fdsbios", "B", "FDS BIOS", "", "", bios) + row("msx", "multifile", "G", "Multifile", "", "", md, bios)));
        Path root = Files.createTempDirectory("fba-console-test");
        try {
            Path library = root.resolve("roms");
            byte[] arcade = zip("arcade.u1", new byte[]{42});
            importZip(index, library, "1942.zip", arcade);
            check("nes_1942.zip".equals(index.importRaw(library, "1942 (USA).NES", bytes(cart))), "iNES matching");
            check("md_1942.zip".equals(index.importRaw(library, "Full Title.gen", bytes(md))), "Genesis raw matching");
            check("1942.zip".equals(importZip(index, library, "1942.zip", arcade)), "arcade colliding stem preserved");
            check(Arrays.equals(arcade, Files.readAllBytes(library.resolve("1942.zip"))), "arcade archive stays separate");
            byte[] prefixed = zip("canonical-name.nes", cart);
            check("nes_1942.zip".equals(importZip(index, library, "1942.ZIP", prefixed)), "collision routed by CRC not name");
            index.importRaw(library, "other-name.nes", bytes(cart));
            check(Arrays.equals(prefixed, Files.readAllBytes(library.resolve("nes_1942.zip"))), "same ROM keeps the existing save fingerprint");
            check("md_1942.zip".equals(importZip(index, library, "1942.zip", zip("chip.bin", md))), "same stem different system routed separately");
            check("exact.zip".equals(importZip(index, library, "exact.zip", zip("chip.bin", linked))), "arcade-equivalent bytes require explicit prefix");
            check("md_exact.zip".equals(importZip(index, library, "md_exact.zip", zip("chip.bin", linked))), "explicit prefix can still select console driver");

            check("snes_world.zip".equals(index.importRaw(library, "World.smc", bytes(headered))), "recognized SNES copier header");
            try (ZipFile z = new ZipFile(library.resolve("snes_world.zip").toFile()); InputStream in = z.getInputStream(z.getEntry("world.bin"))) {
                check(Arrays.equals(snes, LocalFiles.read(in, snes.length)), "raw ZIP contains exactly the DAT payload");
            }
            check(index.stage(library.resolve("snes_world.zip").toFile(), Files.createDirectory(root.resolve("snes-run")))
                    .rom.toPath().endsWith(Paths.get("snes", "world.zip")), "SNES driver/archive routing");

            index.importRaw(library, "Disk.fds", bytes(fds));
            ConsoleStore.Launch unavailable = index.stage(library.resolve("fds_disk.zip").toFile(), Files.createDirectory(root.resolve("missing-bios")));
            check(unavailable.firmwareHint.contains("fdsbios.zip") && unavailable.sessionSystem == null && !unavailable.patched, "firmware guidance without disabling CRC checks");
            check("bios_fds_fdsbios.zip".equals(importZip(index, library, "fdsbios.zip", zip("fdsbios.nes", bios))), "BIOS stored as non-playable dependency");
            check("fds_parent.zip".equals(importZip(index, library, "fds_parent.zip", zip("parent.fds", linked))), "matching parent ZIP");
            ConsoleStore.Launch ready = index.stage(library.resolve("fds_disk.zip").toFile(), Files.createDirectory(root.resolve("ready")));
            Path folder = ready.rom.toPath().getParent();
            check(ready.firmwareHint.isEmpty() && folder.endsWith("fds") && Files.isRegularFile(folder.resolve("fdsbios.zip"))
                    && Files.isRegularFile(folder.resolve("parent.zip")), "scoped unprefixed staging for parent and BIOS");
            expectInvalid(() -> index.stage(library.resolve("bios_fds_fdsbios.zip").toFile(), Files.createDirectory(root.resolve("not-a-game"))));
            check("msx_multifile.zip".equals(importZip(index, library, "multifile.zip", zip("part.rom", bios))), "split/multifile sets accept known members");

            expectInvalid(() -> importZip(index, library, "nes_1942.zip", arcade));
            expectInvalid(() -> importZip(index, library, "snes_nonexistent.zip", arcade));
            expectInvalid(() -> index.importRaw(library, "wrong-system.md", bytes(snes)));
            headered[700] = 1;
            expectInvalid(() -> index.importRaw(library, "wrong-revision.smc", bytes(headered)));
            expectInvalid(() -> index.importRaw(library, "oversize.gba", new InputStream() {
                int remaining = 64 * 1024 * 1024 + 1;
                @Override public int read() { return remaining-- > 0 ? 0 : -1; }
                @Override public int read(byte[] dest) {
                    if (remaining <= 0) return -1;
                    int n = Math.min(dest.length, remaining);
                    remaining -= n;
                    return n;
                }
            }));
            byte[] forged = zip("chip.bin", md);
            forged[30 + "chip.bin".length()] ^= 1; // ZIP table says the correct CRC; actual payload doesn't.
            expectInvalid(() -> importZip(index, library, "md_1942.zip", forged));
            try (java.util.stream.Stream<Path> entries = Files.list(library)) {
                check(entries.noneMatch(e -> e.getFileName().toString().endsWith(".part")), "failed imports leave no partial files");
            }
            ConsoleStore ties = ConsoleStore.load(text(nesRow + row("nes", "1942b", "G", "Same bytes", "", "", cart)
                    + row("snes", "shared", "G", "SNES Shared", "", "", md) + row("md", "shared", "G", "MD Shared", "", "", md)));
            expectInvalid(() -> ties.importRaw(library, "mystery.nes", bytes(cart)));
            check("nes_1942b.zip".equals(ties.importRaw(library, "nes_1942b.nes", bytes(cart))), "explicit set picks duplicate dump");
            expectInvalid(() -> importZip(ties, library, "shared.zip", zip("chip.bin", md)));
            check("md_shared.zip".equals(ties.importRaw(library, "shared.gen", bytes(md))), "specific suffix disambiguates cross-system content");
            expectInvalid(() -> ConsoleStore.load(text(nesRow + nesRow)));
            expectInvalid(() -> ConsoleStore.load(text(nesRow.replace("\t1942\t", "\t../1942\t"))));
            check(ConsoleSystem.isDisc("game.CHD") && ConsoleSystem.isDisc("pcecd_disc.zip") && !ConsoleSystem.isDisc("md_segacd.zip"), "disc images are separate from set ZIPs");
        } finally { LocalFiles.deleteTree(root.toFile()); }
        System.out.println("Console import, CRC routing, BIOS/parent staging, compatibility and atomicity tests passed.");
    }

    static String row(String family, String set, String role, String title, String parent, String deps, byte[]... members) {
        StringBuilder line = new StringBuilder(String.join("\t", family, set, role, "1989", title, parent, deps, ""));
        for (int i = 0; i < members.length; i++) {
            CRC32 sum = new CRC32(); sum.update(members[i]);
            if (i != 0) line.append(',');
            line.append(members[i].length).append(':').append(String.format(Locale.ROOT, "%08x", sum.getValue()));
        }
        return line + "\n";
    }

    private static String importZip(ConsoleStore index, Path library, String name, byte[] zip) throws IOException {
        return RomStore.importZip(library, name, bytes(zip), index::archiveTarget);
    }

    private static byte[] zip(String name, byte[] data) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            CRC32 sum = new CRC32(); sum.update(data);
            ZipEntry member = new ZipEntry(name);
            member.setMethod(ZipEntry.STORED); member.setSize(data.length); member.setCrc(sum.getValue());
            zip.putNextEntry(member); zip.write(data); zip.closeEntry();
        }
        return buffer.toByteArray();
    }

    private static ByteArrayInputStream bytes(byte[] bytes) { return new ByteArrayInputStream(bytes); }
    private static ByteArrayInputStream text(String rows) { return bytes(rows.getBytes(StandardCharsets.UTF_8)); }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private interface Job { void run() throws IOException; }
    private static void expectInvalid(Job job) throws Exception {
        try { job.run(); throw new AssertionError("Expected rejection"); }
        catch (IOException expected) { /* Rejected without committing a change. */ }
    }
}
