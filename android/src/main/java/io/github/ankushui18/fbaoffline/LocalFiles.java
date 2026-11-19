package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.stream.Stream;

/** Shared limits and private-file operations for imports, variants and local linking. */
final class LocalFiles {
    private LocalFiles() {}

    static byte[] read(InputStream source, int limit) throws IOException {
        if (source == null) throw new IOException("Couldn't read the selected file.");
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit, 16 * 1024));
        byte[] buffer = new byte[16 * 1024];
        for (int n; (n = source.read(buffer)) != -1; ) {
            interrupted();
            if (n > limit - out.size()) throw new IOException("The selected file is too large.");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
    }

    static byte[] hash(File file, long limit) throws IOException {
        MessageDigest digest = sha256();
        long bytes = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file.toPath())) {
            for (int n; (n = in.read(buffer)) != -1;) {
                interrupted();
                if ((bytes += n) > limit) throw new IOException("The ROM archive is too large.");
                digest.update(buffer, 0, n);
            }
        }
        return digest.digest();
    }

    static String hex(byte[] bytes, int length) {
        char[] result = new char[length * 2];
        final String digits = "0123456789abcdef";
        for (int i = 0; i < length; i++) {
            int b = bytes[i] & 255;
            result[2 * i] = digits.charAt(b >>> 4);
            result[2 * i + 1] = digits.charAt(b & 15);
        }
        return new String(result);
    }

    static void atomicWrite(File file, byte[] bytes) throws IOException {
        Path parent = file.getParentFile().toPath();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "local-", ".part");
        try {
            Files.write(temp, bytes);
            Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    static void deleteTree(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        try (Stream<Path> entries = Files.walk(dir.toPath())) {
            Iterator<Path> ordered = entries.sorted(Comparator.reverseOrder()).iterator();
            while (ordered.hasNext()) Files.deleteIfExists(ordered.next());
        } catch (IOException ignored) { /* Cache cleanup can retry on the next launch. */ }
    }

    static void interrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("The operation was cancelled.");
    }
}
