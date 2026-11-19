package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ImportReportTest {
    public static void main(String[] args) throws Exception {
        byte[] firmware = {1, 2, 3, 4};
        String index = ConsoleStoreTest.row("fds", "fdsbios", "B", "FDS BIOS", "", "", firmware);
        ConsoleStore sets = ConsoleStore.load(new ByteArrayInputStream(index.getBytes(StandardCharsets.UTF_8)));
        Path library = Files.createTempDirectory("fba-import-report");
        try {
            byte[] nes = new byte[16 + 16384 + 8192];
            System.arraycopy(new byte[]{'N', 'E', 'S', 0x1a, 1, 1}, 0, nes, 0, 6);
            byte[] unknown = nes.clone();
            unknown[6] = (byte) 0xf0; unknown[7] = (byte) 0xf0;
            String original = "Game 1";
            ImportReport mixed = new ImportReport(library.toFile());
            add(mixed, library, sets, "Short dump.nes", "unused", Arrays.copyOf(nes, nes.length - 5));
            add(mixed, library, sets, "Game 1.nes", original, nes);
            add(mixed, library, sets, "Renamed copy.nes", original, nes);
            add(mixed, library, sets, "fdsbios.zip", "FDS BIOS", bios(firmware));
            add(mixed, library, sets, "\u202E\nUnknown board.nes", "unused", unknown);
            add(mixed, library, sets, "Broken.zip", "unused", new byte[]{1});
            check(mixed.hasGames() && mixed.hasDetails(), "mixed result reveals games without hiding failures");
            check(mixed.summary().equals("Added: Game 1 · 1 already listed · BIOS added · 3 not added"), mixed.summary());
            String details = mixed.details();
            check(details.contains("Short dump.nes\nNot added: The .nes file is incomplete")
                    && details.contains("Renamed copy.nes\nAlready listed as Game 1.")
                    && details.contains("Unknown board.nes\nNot added: NES mapper 255")
                    && details.contains("Broken.zip\nNot added: This file isn't a readable game ZIP.")
                    && !details.contains("\u202E"), details);

            ImportReport reimport = new ImportReport(library.toFile());
            add(reimport, library, sets, "Game 1 again.nes", original, nes);
            check(reimport.hasGames() && reimport.summary().equals("1 already listed") && reimport.hasDetails(), "preexisting entry isn't counted as new");
            ImportReport rejected = new ImportReport(library.toFile());
            add(rejected, library, sets, "Not a cartridge.nes", "unused", new byte[16]);
            check(!rejected.hasGames() && rejected.hasDetails() && rejected.summary().equals("1 not added"), "single failed import");
            check(rejected.details().contains("Not a cartridge.nes\nNot added: This isn't a valid .nes"), rejected.details());
            ImportReport biosOnly = new ImportReport(library.toFile());
            add(biosOnly, library, sets, "fdsbios.zip", "FDS BIOS", bios(firmware));
            check(!biosOnly.hasGames() && !biosOnly.hasDetails() && biosOnly.summary().equals("BIOS added"), "BIOS alone isn't a library entry");

            StringBuilder oversized = new StringBuilder();
            for (int i = 0; i < 300; i++) oversized.append("\uD83C\uDFAE");
            ImportReport names = new ImportReport(library.toFile());
            names.notAdded(oversized.toString(), null);
            String label = names.details().split("\n")[2];
            check(label.codePointCount(0, label.length()) == 140 && label.endsWith("…"), "long provider labels stay bounded");
            check(names.details().contains("Couldn't import this file."), "missing error fallback");
            check(!GameImport.failureMessage(new IOException("/private/path")).contains("/private/path")
                    && GameImport.failureMessage(new SecurityException("content://private")).contains("read access"), "provider errors don't expose details");
            try (java.util.stream.Stream<Path> entries = Files.list(library)) {
                check(entries.count() == 2, "only the cartridge and BIOS were copied");
            }
        } finally { LocalFiles.deleteTree(library.toFile()); }
        System.out.println("Import results: mixed, duplicate, BIOS-only and rejected batches retain the right counts and file reasons.");
    }

    private static void add(ImportReport report, Path library, ConsoleStore sets, String name, String title, byte[] data) {
        try {
            report.saved(name, GameImport.add(library, name, new ByteArrayInputStream(data), sets), title);
        } catch (IOException failure) {
            report.notAdded(name, GameImport.failureMessage(failure));
        }
    }

    private static byte[] bios(byte[] data) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("fdsbios.nes"));
            zip.write(data);
        }
        return buffer.toByteArray();
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
