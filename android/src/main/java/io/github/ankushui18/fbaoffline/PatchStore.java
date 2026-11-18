package io.github.ankushui18.fbaoffline;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Imports IPS variants without modifying the original set. The core only sees a validated one-chip ZIP. */
final class PatchStore {
    private static final int FORMAT = 0x49505331, MAX_PROFILES = 32;
    private static final int MAX_CHIP = 64 * 1024 * 1024;
    private static final Pattern CHIP = Pattern.compile("[a-zA-Z0-9_][a-zA-Z0-9 ._()+\\-]{0,139}");
    private static final Pattern PROFILE = Pattern.compile("[a-f0-9]{32}\\.ipsp");
    private final File root;

    static final class Chip {
        final String name;
        final int size;
        final long crc;
        Chip(String name, int size, long crc) throws IOException {
            if (!CHIP.matcher(name).matches() || size <= 0 || size > MAX_CHIP || crc <= 0 || crc > 0xffffffffL) {
                throw new IOException("Invalid ROM chip metadata.");
            }
            this.name = name; this.size = size; this.crc = crc;
        }
        String detail() { return name + " · " + String.format(Locale.ROOT, "CRC %08X", crc); }
    }

    static final class Entry {
        final String id, title;
        final Chip chip;
        final File file;
        Entry(File file, String title, Chip chip) {
            this.file = file; this.title = title; this.chip = chip;
            id = file.getName().substring(0, 32);
        }
        File preview() { return new File(file.getParentFile(), id + ".png"); }
    }

    PatchStore(File root) { this.root = root; }

    private File folder(File rom) { return new File(root, rom.getName()); }

    static List<Chip> targets(File rom, InputStream index) throws IOException {
        List<Chip> matched = new ArrayList<>();
        if (index == null) return matched;
        Map<String, ZipEntry> present = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(rom);
             BufferedReader in = new BufferedReader(new InputStreamReader(index, StandardCharsets.US_ASCII))) {
            zip.stream().filter(e -> !e.isDirectory()).forEach(e -> present.putIfAbsent(e.getName().toLowerCase(Locale.ROOT), e));
            for (int records = 0; records < 2048; records++) {
                String line = in.readLine();
                if (line == null) break;
                String[] cols = line.split("\\t", -1);
                if (cols.length != 3) throw new IOException("Invalid chip index.");
                Chip chip = new Chip(cols[0], Integer.parseInt(cols[1]), Long.parseLong(cols[2], 16));
                ZipEntry entry = present.get(chip.name.toLowerCase(Locale.ROOT));
                if (entry != null && entry.getSize() == chip.size && entry.getCrc() == chip.crc) matched.add(chip);
            }
        } catch (IllegalArgumentException corrupt) { throw new IOException("Couldn't read the FBNeo chip index.", corrupt); }
        return matched;
    }

    List<Entry> entries(File rom) {
        List<Entry> result = new ArrayList<>();
        File[] files = folder(rom).listFiles((dir, name) -> PROFILE.matcher(name).matches());
        if (files != null) for (File file : files) {
            if (result.size() == MAX_PROFILES) break;
            try (DataInputStream in = new DataInputStream(Files.newInputStream(file.toPath()))) {
                result.add(metadata(file, in));
            } catch (IOException ignored) { /* A damaged profile stays inactive. */ }
        }
        result.sort(Comparator.comparing(e -> e.title, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    Entry add(File rom, String label, Chip target, byte[] patch) throws IOException {
        IpsPatch.validate(patch, target.size);
        try (ZipFile zip = new ZipFile(rom)) { compatible(zip, target); }
        File file = new File(folder(rom), identity(target, patch) + ".ipsp");
        if (!file.isFile() && entries(rom).size() >= MAX_PROFILES) throw new IOException("Remove an old patch before importing another.");
        String title = label == null ? "Imported IPS" : label.replaceAll("[\\p{Cntrl}\\p{C}]", " ").trim();
        if (title.isEmpty()) title = "Imported IPS";
        if (title.length() > 100) title = title.substring(0, 100);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(patch.length + 320);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT);
            out.writeUTF(title);
            out.writeUTF(target.name);
            out.writeInt(target.size);
            out.writeLong(target.crc);
            out.write(patch);
        }
        LocalFiles.atomicWrite(file, bytes.toByteArray());
        return new Entry(file, title, target);
    }

    void remove(Entry entry) throws IOException {
        Files.deleteIfExists(entry.file.toPath());
        Files.deleteIfExists(entry.preview().toPath());
    }

    static void stage(File rom, Entry patch, File override) throws IOException {
        byte[] data;
        Chip target;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(patch.file.toPath()))) {
            target = metadata(patch.file, in).chip;
            data = LocalFiles.read(in, IpsPatch.MAX_BYTES);
        }
        IpsPatch.validate(data, target.size);
        if (!identity(target, data).equals(patch.id)) throw new IOException("This patch profile is damaged. Re-import the IPS file.");
        Path location = override.getParentFile().toPath();
        Files.createDirectories(location);
        Path scratch = Files.createTempFile(location, "chip-", ".part");
        try {
            copyCheckedChip(rom, target, scratch);
            try (RandomAccessFile chip = new RandomAccessFile(scratch.toFile(), "rw")) { IpsPatch.apply(data, chip); }
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(override.toPath()))) {
                zip.putNextEntry(new ZipEntry(target.name));
                Files.copy(scratch, zip);
                zip.closeEntry();
            }
        } finally { Files.deleteIfExists(scratch); }
    }

    private static String identity(Chip target, byte[] patch) {
        MessageDigest sha = LocalFiles.sha256();
        sha.update(target.detail().getBytes(StandardCharsets.UTF_8));
        sha.update(java.nio.ByteBuffer.allocate(4).putInt(target.size).array());
        return LocalFiles.hex(sha.digest(patch), 16);
    }

    private static Entry metadata(File file, DataInputStream in) throws IOException {
        if (in.readInt() != FORMAT) throw new IOException("Unrecognized IPS profile.");
        String title = in.readUTF();
        if (title.isEmpty() || title.length() > 100) throw new IOException("Invalid IPS title.");
        return new Entry(file, title, new Chip(in.readUTF(), in.readInt(), in.readLong()));
    }

    private static ZipEntry compatible(ZipFile zip, Chip chip) throws IOException {
        ZipEntry match = zip.stream().filter(e -> e.getName().equalsIgnoreCase(chip.name) && !e.isDirectory()).findFirst().orElse(null);
        if (match == null || match.getSize() != chip.size || match.getCrc() != chip.crc) {
            throw new IOException("This patch was made for a different chip revision. Restore the matching ZIP or choose the original game.");
        }
        return match;
    }

    private static void copyCheckedChip(File rom, Chip target, Path scratch) throws IOException {
        CRC32 crc = new CRC32();
        int bytes = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipFile zip = new ZipFile(rom);
             InputStream in = zip.getInputStream(compatible(zip, target));
             OutputStream out = Files.newOutputStream(scratch)) {
            for (int n; (n = in.read(buffer)) != -1;) {
                LocalFiles.interrupted();
                if ((bytes += n) > target.size) throw new IOException("Invalid ZIP member size.");
                crc.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }
        if (bytes != target.size || crc.getValue() != target.crc) throw new IOException("The ROM chip failed its CRC check.");
    }
}
