package io.github.ankushui18.fbaoffline;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
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

/** Validated IPS variants. Only patched chips are staged; the original ZIP is never written. */
final class PatchStore {
    private static final int SINGLE = 0x49505331, GROUP = 0x49505332, MAX_PROFILES = 32;
    static final int MAX_PARTS = 8, MAX_PATCH_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CHIP = 64 * 1024 * 1024, MAX_CHIPS_BYTES = 128 * 1024 * 1024;
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
        String key() { return name.toLowerCase(Locale.ROOT); }
        String detail() { return name + " · " + String.format(Locale.ROOT, "CRC %08X", crc); }
    }

    static final class Part {
        final Chip target;
        final byte[] data;
        Part(Chip target, byte[] data) { this.target = target; this.data = data; }
    }

    static final class Entry {
        final String id, title;
        final List<Chip> targets;
        final File file;
        Entry(File file, String title, List<Chip> targets) {
            this.file = file; this.title = title; this.targets = Collections.unmodifiableList(targets);
            id = file.getName().substring(0, 32);
        }
        File preview() { return new File(file.getParentFile(), id + ".png"); }
        String detail() {
            return targets.size() == 1 ? targets.get(0).detail()
                    : targets.size() + " IPS files · " + targets.stream().map(Chip::key).distinct().count() + " chip(s)";
        }
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
                ZipEntry entry = present.get(chip.key());
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
            try (DataInputStream in = openProfile(file)) {
                result.add(metadata(file, in, new int[MAX_PARTS]));
            } catch (IOException ignored) { /* A damaged profile stays inactive. */ }
        }
        result.sort(Comparator.comparing((Entry e) -> e.title, String.CASE_INSENSITIVE_ORDER).thenComparing(e -> e.id));
        return result;
    }

    Entry add(File rom, String label, Chip target, byte[] patch) throws IOException {
        return add(rom, label, Collections.singletonList(new Part(target, patch)));
    }

    Entry combine(File rom, String label, List<Entry> selection) throws IOException {
        if (selection.size() < 2 || selection.size() > MAX_PARTS) throw new IOException("Select between 2 and 8 IPS variants.");
        List<Part> parts = new ArrayList<>();
        int remaining = MAX_PATCH_BYTES;
        for (Entry entry : selection) {
            List<Part> next = load(entry, MAX_PARTS - parts.size(), remaining);
            parts.addAll(next);
            for (Part part : next) remaining -= part.data.length;
        }
        return add(rom, label, parts);
    }

    Entry add(File rom, String label, List<Part> parts) throws IOException {
        Map<String, List<Part>> chips = checked(parts);
        try (ZipFile zip = new ZipFile(rom)) {
            for (List<Part> sameChip : chips.values()) compatible(zip, sameChip.get(0).target);
        }
        File file = new File(folder(rom), identity(parts) + ".ipsp");
        if (!file.isFile() && entries(rom).size() >= MAX_PROFILES) throw new IOException("Remove an old patch before importing another.");
        String title = label == null ? "Imported IPS" : label.replaceAll("[\\p{Cntrl}\\p{C}]", " ").trim();
        if (title.isEmpty()) title = "Imported IPS";
        if (title.length() > 100) title = title.substring(0, 100);
        boolean single = parts.size() == 1;
        List<Chip> targets = new ArrayList<>();
        Path parent = file.getParentFile().toPath();
        Files.createDirectories(parent);
        Path staging = Files.createTempFile(parent, "patch-", ".part");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(staging)))) {
                out.writeInt(single ? SINGLE : GROUP);
                out.writeUTF(title);
                if (!single) out.writeByte(parts.size());
                for (Part part : parts) {
                    targets.add(part.target);
                    out.writeUTF(part.target.name);
                    out.writeInt(part.target.size);
                    out.writeLong(part.target.crc);
                    if (!single) out.writeInt(part.data.length);
                }
                for (Part part : parts) out.write(part.data);
            }
            LocalFiles.interrupted();
            Files.move(staging, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(staging); }
        return new Entry(file, title, targets);
    }

    void remove(Entry entry) throws IOException {
        Files.deleteIfExists(entry.file.toPath());
        Files.deleteIfExists(entry.preview().toPath());
    }

    static void stage(File rom, Entry patch, File override) throws IOException {
        Map<String, List<Part>> chips = checked(load(patch, MAX_PARTS, MAX_PATCH_BYTES));
        Path location = override.getParentFile().toPath();
        Files.createDirectories(location);
        Path scratch = Files.createTempFile(location, "chip-", ".part");
        Path staging = null;
        try {
            staging = Files.createTempFile(location, "override-", ".part");
            try (ZipFile source = new ZipFile(rom);
                 ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(staging)))) {
                for (List<Part> patches : chips.values()) {
                    Chip target = patches.get(0).target;
                    copyCheckedChip(source, target, scratch);
                    try (RandomAccessFile out = new RandomAccessFile(scratch.toFile(), "rw")) {
                        for (Part part : patches) IpsPatch.apply(part.data, out);
                    }
                    zip.putNextEntry(new ZipEntry(target.name));
                    Files.copy(scratch, zip);
                    zip.closeEntry();
                    LocalFiles.interrupted();
                }
            }
            Files.move(staging, override.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(scratch);
            if (staging != null) Files.deleteIfExists(staging);
        }
    }

    private static Map<String, List<Part>> checked(List<Part> parts) throws IOException {
        if (parts.isEmpty() || parts.size() > MAX_PARTS) throw new IOException("A variant can contain 1–8 standard IPS files.");
        int bytes = 0, chipBytes = 0;
        Map<String, List<Part>> chips = new LinkedHashMap<>();
        for (Part part : parts) {
            if (part.data.length > MAX_PATCH_BYTES - bytes) throw new IOException("Patch files exceed 16 MiB combined.");
            bytes += part.data.length;
            IpsPatch.validate(part.data, part.target.size);
            List<Part> group = chips.computeIfAbsent(part.target.key(), key -> new ArrayList<>());
            if (group.isEmpty()) {
                if (part.target.size > MAX_CHIPS_BYTES - chipBytes) throw new IOException("Patched chips exceed 128 MiB combined.");
                chipBytes += part.target.size;
            } else if (group.get(0).target.crc != part.target.crc || group.get(0).target.size != part.target.size) {
                throw new IOException("Selected patches target different revisions of the same chip.");
            }
            group.add(part);
        }
        return chips;
    }

    private static String identity(List<Part> parts) {
        if (parts.size() == 1) return LocalFiles.hex(partHash(parts.get(0)), 16); // Keep v0.4 profiles and states addressable.
        MessageDigest sha = LocalFiles.sha256();
        sha.update(new byte[]{'I', 'P', 'S', '2', (byte) parts.size()});
        for (Part part : parts) sha.update(partHash(part));
        return LocalFiles.hex(sha.digest(), 16);
    }

    private static byte[] partHash(Part part) {
        MessageDigest sha = LocalFiles.sha256();
        sha.update(part.target.detail().getBytes(StandardCharsets.UTF_8));
        sha.update(ByteBuffer.allocate(4).putInt(part.target.size).array());
        return sha.digest(part.data);
    }

    private static DataInputStream openProfile(File file) throws IOException {
        if (file.length() > MAX_PATCH_BYTES + 4096L) throw new IOException("This patch profile is too large.");
        return new DataInputStream(new BufferedInputStream(Files.newInputStream(file.toPath())));
    }

    private static Entry metadata(File file, DataInputStream in, int[] lengths) throws IOException {
        int format = in.readInt();
        if (format != SINGLE && format != GROUP) throw new IOException("Unrecognized IPS profile.");
        String title = in.readUTF();
        if (title.isEmpty() || title.length() > 100) throw new IOException("Invalid IPS title.");
        int count = format == GROUP ? in.readUnsignedByte() : 1, bytes = 0;
        if (count < (format == GROUP ? 2 : 1) || count > MAX_PARTS) throw new IOException("Invalid IPS file count.");
        List<Chip> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            targets.add(new Chip(in.readUTF(), in.readInt(), in.readLong()));
            if (format == SINGLE) lengths[i] = -1;
            else {
                int len = in.readInt();
                if (len < 8 || len > IpsPatch.MAX_BYTES || len > MAX_PATCH_BYTES - bytes) throw new IOException("Invalid IPS profile size.");
                lengths[i] = len;
                bytes += len;
            }
        }
        return new Entry(file, title, targets);
    }

    private static List<Part> load(Entry patch, int maxParts, int maxBytes) throws IOException {
        List<Part> parts = new ArrayList<>();
        int[] lengths = new int[MAX_PARTS];
        try (DataInputStream in = openProfile(patch.file)) {
            Entry entry = metadata(patch.file, in, lengths);
            if (entry.targets.size() > maxParts) throw new IOException("Select at most 8 IPS files in total.");
            for (int i = 0; i < entry.targets.size(); i++) {
                LocalFiles.interrupted();
                int len = lengths[i];
                if (len > maxBytes) throw new IOException("Patch files exceed 16 MiB combined.");
                byte[] data;
                if (len < 0) data = LocalFiles.read(in, Math.min(IpsPatch.MAX_BYTES, maxBytes));
                else { data = new byte[len]; in.readFully(data); }
                maxBytes -= data.length;
                parts.add(new Part(entry.targets.get(i), data));
            }
            if (in.read() != -1 || !identity(parts).equals(patch.id)) throw new IOException("This patch profile is damaged. Re-import the IPS file(s).");
        }
        return parts;
    }

    private static ZipEntry compatible(ZipFile zip, Chip chip) throws IOException {
        ZipEntry match = zip.stream().filter(e -> e.getName().equalsIgnoreCase(chip.name) && !e.isDirectory()).findFirst().orElse(null);
        if (match == null || match.getSize() != chip.size || match.getCrc() != chip.crc) {
            throw new IOException("Chip " + chip.name + " doesn't match this ROM revision. Restore the matching ZIP or choose the original game.");
        }
        return match;
    }

    private static void copyCheckedChip(ZipFile zip, Chip target, Path scratch) throws IOException {
        CRC32 crc = new CRC32();
        int bytes = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = zip.getInputStream(compatible(zip, target)); OutputStream out = Files.newOutputStream(scratch)) {
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
