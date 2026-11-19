package io.github.ankushui18.fbaoffline;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Accepts exact NES dumps from the pinned FBNeo DAT without changing arcade ZIP identities. */
final class NesStore {
    static final String INDEX = "nes-sets.tsv", PREFIX = "nes_";
    private static final int MAX_ROM_BYTES = 10 * 1024 * 1024;
    private static final long MAX_ZIP_BYTES = 32L * 1024 * 1024;

    private final Map<String, Entry> sets;
    private final Map<Long, List<Entry>> signatures = new HashMap<>();

    static final class Entry {
        final String set, year, title;
        final int size;
        final long crc;
        Entry(String set, int size, long crc, String year, String title) {
            this.set = set; this.size = size; this.crc = crc; this.year = year; this.title = title;
        }
        String archive() { return PREFIX + set + ".zip"; }
    }

    /** Safe import feedback (no provider paths or raw exception details). */
    static final class Mismatch extends IOException {
        Mismatch(String message) { super(message); }
    }

    private NesStore(Map<String, Entry> sets) {
        this.sets = Collections.unmodifiableMap(sets);
        for (Entry entry : sets.values()) signatures.computeIfAbsent(key(entry.size, entry.crc), ignored -> new ArrayList<>()).add(entry);
    }

    static NesStore load(InputStream input) throws IOException {
        Map<String, Entry> sets = new HashMap<>();
        try (BufferedReader rows = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (String line; (line = rows.readLine()) != null;) {
                String[] row = line.split("\t", -1);
                if (sets.size() >= 4096 || row.length != 5 || row[0].isEmpty() || row[4].isEmpty()) throw invalidIndex();
                int size = Integer.parseInt(row[1]);
                long crc = Long.parseLong(row[2], 16);
                Entry entry = new Entry(row[0], size, crc, row[3], row[4]);
                if (size < 16 || size > MAX_ROM_BYTES || row[2].length() != 8 || crc < 0 || crc > 0xffffffffL
                        || !entry.archive().equals(RomStore.archiveName(entry.archive())) || sets.put(entry.set, entry) != null) {
                    throw invalidIndex();
                }
            }
        } catch (NumberFormatException invalid) { throw invalidIndex(); }
        if (sets.isEmpty()) throw invalidIndex();
        return new NesStore(sets);
    }

    private static IOException invalidIndex() { return new IOException("The NES compatibility list isn't available."); }
    Collection<Entry> entries() { return sets.values(); }
    static boolean isRaw(String name) { return name != null && name.toLowerCase(Locale.ROOT).endsWith(".nes"); }
    static boolean isNesArchive(String name) { return name.startsWith(PREFIX) && name.length() > 8 && name.endsWith(".zip"); }

    private static boolean ines(byte[] bytes) {
        return bytes.length >= 16 && bytes[0] == 'N' && bytes[1] == 'E' && bytes[2] == 'S' && bytes[3] == 0x1a;
    }

    private static long crc(byte[] bytes) {
        CRC32 sum = new CRC32();
        sum.update(bytes);
        return sum.getValue();
    }

    private static long key(int size, long crc) { return ((long) size << 32) | crc; }

    private Entry identify(String name, byte[] bytes) throws Mismatch {
        if (!ines(bytes)) throw new Mismatch("This isn't an iNES cartridge file. Pick a .nes ROM or a matching FBNeo NES ZIP.");
        List<Entry> matches = signatures.get(key(bytes.length, crc(bytes)));
        if (matches == null) throw new Mismatch("This .nes revision doesn't match the NES sets in this FBNeo build. Try its matching FBNeo NES ZIP.");
        if (matches.size() == 1) return matches.get(0);
        String stem = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
        for (Entry entry : matches) if (entry.set.equals(stem) || (PREFIX + entry.set).equals(stem)) return entry;
        throw new Mismatch("This .nes matches more than one FBNeo set. Use the matching set ZIP to pick the version.");
    }

    String importRaw(Path library, String name, InputStream input) throws IOException {
        if (!isRaw(name)) throw new Mismatch("Choose a .nes file.");
        byte[] bytes = LocalFiles.read(input, MAX_ROM_BYTES);
        Entry entry = identify(name, bytes);
        Path target = library.resolve(entry.archive());
        // Re-adding the same cartridge must not replace an existing equivalent ZIP and invalidate its save fingerprint.
        if (Files.isRegularFile(target) && Files.size(target) <= MAX_ZIP_BYTES) {
            try (ZipFile current = new ZipFile(target.toFile())) {
                if (contains(current, entry)) return entry.archive();
            } catch (InterruptedIOException cancelled) { throw cancelled; }
            catch (IOException damaged) { /* Replace the local copy only after constructing a valid ZIP. */ }
        }
        Files.createDirectories(library);
        Path temp = Files.createTempFile(library, ".nes-", ".part");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                ZipEntry member = new ZipEntry(entry.set + ".nes");
                member.setTime(0);
                member.setMethod(ZipEntry.STORED);
                member.setSize(entry.size);
                member.setCrc(entry.crc);
                zip.putNextEntry(member);
                zip.write(bytes);
                zip.closeEntry();
            }
            LocalFiles.interrupted();
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        return entry.archive();
    }

    // "1942.zip" also names an arcade set: only route a bare NES DAT name after checking its actual payload.
    String archiveTarget(String name, ZipFile zip) throws IOException {
        boolean prefixed = isNesArchive(name);
        Entry entry = sets.get(name.substring(prefixed ? PREFIX.length() : 0, name.length() - 4));
        if (entry == null) return name;
        if (contains(zip, entry)) return entry.archive();
        if (prefixed) throw new Mismatch("This ZIP doesn't contain the matching ROM for " + entry.title + ".");
        return name;
    }

    private static boolean contains(ZipFile zip, Entry expected) throws IOException {
        for (Enumeration<? extends ZipEntry> members = zip.entries(); members.hasMoreElements();) {
            LocalFiles.interrupted();
            ZipEntry member = members.nextElement();
            if (member.isDirectory() || member.getSize() != expected.size || member.getCrc() != expected.crc) continue;
            try (InputStream input = zip.getInputStream(member)) {
                byte[] bytes = LocalFiles.read(input, expected.size);
                if (bytes.length == expected.size && ines(bytes) && crc(bytes) == expected.crc) return true;
            }
        }
        return false;
    }

    /** Libretro selects the NES driver from the folder name, then looks for the unprefixed ZIP. */
    static File stage(File rom, Path sessionCache) throws IOException {
        String name = RomStore.archiveName(rom.getName());
        if (!isNesArchive(name) || Files.size(rom.toPath()) > MAX_ZIP_BYTES) throw new IOException("Use a matching FBNeo NES ZIP up to 32 MiB.");
        Path target = Files.createDirectory(sessionCache.resolve("nes")).resolve(name.substring(PREFIX.length()));
        LocalFiles.interrupted();
        Files.copy(rom.toPath(), target);
        return target.toFile();
    }
}
