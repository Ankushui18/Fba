package io.github.ankushui18.fbaoffline;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** ROMs stay as archives in app-private storage; entries are never extracted by the importer. */
final class RomStore {
    static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final long MAX_EXPANDED = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 8192;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_][a-z0-9_-]{0,89}\\.zip");

    private RomStore() {}

    static String archiveName(String displayName) throws IOException {
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (!NAME.matcher(name).matches()) {
            throw new IOException("Choose a .zip with its original FBNeo set name (letters, numbers, hyphens or underscores).");
        }
        return name;
    }

    static boolean isArchive(File file) {
        return file.isFile() && NAME.matcher(file.getName()).matches();
    }

    static void importZip(Path directory, String sourceName, InputStream in) throws IOException {
        importZip(directory, sourceName, in, MAX_BYTES);
    }

    // Lower byte limit for unit tests without huge fixtures.
    static void importZip(Path directory, String sourceName, InputStream in, long limit) throws IOException {
        String name = archiveName(sourceName);
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, ".import-", ".part");
        try {
            byte[] buffer = new byte[64 * 1024];
            long written = 0;
            try (OutputStream out = Files.newOutputStream(temp)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    checkInterrupted();
                    written += read;
                    if (written > limit) throw new IOException("ZIP exceeds the import size limit.");
                    out.write(buffer, 0, read);
                }
            }
            validateZip(temp);
            checkInterrupted();
            Files.move(temp, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled.");
    }

    private static void validateZip(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            if (zip.size() == 0 || zip.size() > MAX_ENTRIES) throw new IOException("Unsupported ZIP entry count.");
            long total = 0;
            boolean hasFiles = false;
            Set<String> seen = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                checkInterrupted();
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.length() > 512 || name.startsWith("/") || name.indexOf('\\') >= 0 ||
                        name.indexOf(':') >= 0 || name.indexOf('\0') >= 0 || !seen.add(name.toLowerCase(Locale.ROOT))) {
                    throw new IOException("ZIP has an invalid or duplicate entry name.");
                }
                for (String part : name.split("/")) {
                    if (part.equals("..") || part.equals(".")) throw new IOException("ZIP has an invalid entry path.");
                }
                if (entry.isDirectory()) continue;
                if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED) {
                    throw new IOException("Unsupported ZIP compression.");
                }
                long size = entry.getSize();
                if (size < 0 || size > MAX_EXPANDED - total) throw new IOException("ZIP is too large after decompression.");
                total += size;
                hasFiles = true;
            }
            if (!hasFiles) throw new IOException("ZIP has no ROM files.");
        }
    }
}
