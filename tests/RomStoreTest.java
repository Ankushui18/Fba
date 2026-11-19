package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class RomStoreTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("offline-rom-test");
        try {
            byte[] valid = zip("rom.bin", 7);
            RomStore.importZip(dir, "ARCADE_1.ZIP", new ByteArrayInputStream(valid));
            Path target = dir.resolve("arcade_1.zip");
            check(Arrays.equals(valid, Files.readAllBytes(target)), "copy retains entire archive");
            check(!Files.exists(dir.resolve("rom.bin")), "no extracting ROM entries");

            byte[] next = zip("sub/rom.bin", 8);
            RomStore.importZip(dir, "arcade_1.zip", new ByteArrayInputStream(next));
            check(Arrays.equals(next, Files.readAllBytes(target)), "same name replaces local copy atomically");

            expectInvalid(() -> RomStore.importZip(dir, "arcade_1.zip", new ByteArrayInputStream(new byte[3])));
            expectInvalid(() -> RomStore.importZip(dir, "arcade_1.zip", new ByteArrayInputStream(valid), 5));
            expectInvalid(() -> RomStore.importZip(dir, "arcade_1.zip", new ByteArrayInputStream(zip("/absolute.bin", 0))));
            expectInvalid(() -> RomStore.importZip(dir, "arcade_1.zip", new ByteArrayInputStream(zip("folder/", 0))));
            expectInvalid(() -> RomStore.importZip(dir, "bad.apk", new ByteArrayInputStream(valid)));
            expectInvalid(() -> RomStore.importZip(dir, "sub/set.zip", new ByteArrayInputStream(valid)));
            check(Arrays.equals(next, Files.readAllBytes(target)), "failures preserve existing ROM");
            try (java.util.stream.Stream<Path> files = Files.list(dir)) {
                check(files.count() == 1L, "temporary import cleanup");
            }
        } finally {
            try (java.util.stream.Stream<Path> files = Files.list(dir)) {
                for (Path file : (Iterable<Path>) files::iterator) Files.delete(file);
            }
            Files.delete(dir);
        }
        System.out.println("ROM import tests passed.");
    }

    private static byte[] zip(String name, int value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry(name));
            if (!name.endsWith("/")) out.write(value);
            out.closeEntry();
        }
        return bytes.toByteArray();
    }

    private interface IO { void run() throws IOException; }

    private static void expectInvalid(IO operation) throws IOException {
        try { operation.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("expected import rejection");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
