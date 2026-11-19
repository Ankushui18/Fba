package io.github.ankushui18.fbaoffline;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/** Versioned manual backups. Validate and stage the entire archive before touching user data. */
final class BackupStore {
    private static final String SETTINGS = "settings.dat";
    private static final long MAX_TOTAL = 256L * 1024 * 1024;
    private static final long MAX_ARCHIVE = MAX_TOTAL + 4L * 1024 * 1024;
    private static final int MAX_FILES = 4096, MAX_SCAN = 32768, MAX_LABEL = 256;
    private static final Pattern IPS = Pattern.compile("[a-f0-9]{32}");
    private static final Pattern BAD_LABEL = Pattern.compile("[\\p{Cntrl}\\p{Cf}]");
    private static final Pattern SLOT = Pattern.compile("slot-[1-" + StateStore.SLOTS + "]\\.state(?:\\.name)?");

    interface SettingsWriter { void write(Map<String, Object> values) throws IOException; }

    static final class Restored {
        final int slots, kept, settings;
        Restored(int slots, int kept, int settings) { this.slots = slots; this.kept = kept; this.settings = settings; }
    }

    private BackupStore() {}

    static int exportZip(Path data, Map<String, ?> settings, OutputStream output) throws IOException {
        ByteArrayOutputStream metadata = new ByteArrayOutputStream();
        BackupSettings.write(new DataOutputStream(metadata), settings);
        List<String> slots = localSlots(data);
        long remaining = MAX_TOTAL - metadata.size();
        int files = 1;
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            for (String name : slots) {
                LocalFiles.interrupted();
                Path state = data.resolve(name);
                StateStore.validateBackupSlot(state);
                if (++files > MAX_FILES) throw new IOException("Too many files for one backup.");
                remaining -= writeEntry(zip, name, state, Math.min(remaining, StateStore.MAX_FILE_BYTES));
                Path title = data.resolve(name + ".name");
                if (Files.isRegularFile(title, NOFOLLOW_LINKS)) {
                    checkTitle(title);
                    if (++files > MAX_FILES) throw new IOException("Too many files for one backup.");
                    remaining -= writeEntry(zip, name + ".name", title, Math.min(remaining, MAX_LABEL));
                }
            }
            // Required for restore. Write it last so an aborted export is never accepted
            // as a successful backup of just the slots written before the failure.
            LocalFiles.interrupted();
            zip.putNextEntry(new ZipEntry(SETTINGS));
            metadata.writeTo(zip);
            zip.closeEntry();
        }
        return slots.size();
    }

    /** Takes ownership of the selected document stream; closes it before installing any files. */
    static Restored restoreZip(Path data, Path cache, InputStream source, SettingsWriter writer) throws IOException {
        if (source == null) throw new IOException("Couldn't open the backup ZIP.");
        Path work = null;
        try {
            Path archive;
            try (InputStream in = source) {
                work = Files.createTempDirectory(cache, "arcade-restore-");
                archive = work.resolve("backup.zip");
                try (OutputStream out = Files.newOutputStream(archive)) { copy(in, out, MAX_ARCHIVE, null); }
            }
            int count = checkZipEnd(archive);
            Map<String, Path> entries = stage(archive, work, count);
            Path metadata = entries.remove(SETTINGS);
            if (metadata == null) throw new IOException("Choose a ZIP exported by FBA Arcade Backup & restore.");
            Map<String, Object> settings;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(metadata)))) {
                settings = BackupSettings.read(in);
            }
            return install(data, entries, settings, writer);
        } finally { if (work != null) LocalFiles.deleteTree(work.toFile()); }
    }

    private static List<String> localSlots(Path data) throws IOException {
        List<String> list = new ArrayList<>();
        Path root = data.resolve("states");
        if (!Files.exists(root, NOFOLLOW_LINKS)) return list;
        if (!Files.isDirectory(root, NOFOLLOW_LINKS)) throw new IOException("Couldn't read the private save folder.");
        try (Stream<Path> paths = Files.walk(root, 4)) {
            Iterator<Path> iterator = paths.iterator();
            int scanned = 0;
            while (iterator.hasNext()) {
                LocalFiles.interrupted();
                if (++scanned > MAX_SCAN) throw new IOException("Too many files in the private save folder.");
                Path path = iterator.next();
                if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) continue;
                String name = data.relativize(path).toString();
                if (name.endsWith(".state") && isSlotPath(name)) {
                    if (list.size() >= MAX_FILES - 1) throw new IOException("Too many save slots for one backup.");
                    list.add(name);
                }
            }
        }
        Collections.sort(list);
        return list;
    }

    private static long writeEntry(ZipOutputStream zip, String name, Path path, long limit) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        long count;
        try (InputStream in = Files.newInputStream(path)) { count = copy(in, zip, limit, null); }
        zip.closeEntry();
        return count;
    }

    private static Map<String, Path> stage(Path archive, Path work, int count) throws IOException {
        Map<String, Path> entries = new TreeMap<>();
        long remaining = MAX_TOTAL;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            if (zip.size() != count) throw new IOException("Invalid backup ZIP index.");
            Enumeration<? extends ZipEntry> members = zip.entries();
            while (members.hasMoreElements()) {
                LocalFiles.interrupted();
                ZipEntry member = members.nextElement();
                String name = member.getName();
                boolean settings = SETTINGS.equals(name), title = name.endsWith(".name");
                if ((!settings && !isSlotPath(name)) || entries.containsKey(name)) {
                    throw new IOException("The backup contains an unsupported or duplicate file.");
                }
                long len = member.getSize();
                long limit = settings ? BackupSettings.MAX_BYTES : title ? MAX_LABEL : StateStore.MAX_FILE_BYTES;
                if (len <= 0 || len > Math.min(remaining, limit)) throw new IOException("The backup exceeds its size limits.");
                Path staged = Files.createTempFile(work, "data-", ".part");
                CRC32 crc = new CRC32();
                try (InputStream in = zip.getInputStream(member); OutputStream out = Files.newOutputStream(staged)) {
                    if (copy(in, out, len, crc) != len || crc.getValue() != member.getCrc()) throw new IOException("The backup ZIP is damaged.");
                }
                if (title) checkTitle(staged);
                else if (!settings) StateStore.validateBackupSlot(staged);
                entries.put(name, staged);
                remaining -= len;
            }
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Invalid backup ZIP entry.", malformed);
        }
        if (entries.size() != count) throw new IOException("Invalid backup ZIP index.");
        return entries;
    }

    private static Restored install(Path data, Map<String, Path> entries, Map<String, Object> settings,
                                     SettingsWriter writer) throws IOException {
        List<String> missing = new ArrayList<>();
        int kept = 0;
        for (String name : entries.keySet()) {
            if (name.endsWith(".name")) {
                if (!entries.containsKey(name.substring(0, name.length() - 5))) throw new IOException("A slot name has no matching save.");
                continue;
            }
            Path target = data.resolve(name);
            checkParents(data, target);
            if (Files.exists(target, NOFOLLOW_LINKS) || Files.exists(data.resolve(name + ".name"), NOFOLLOW_LINKS)
                    || Files.exists(data.resolve(name + ".png"), NOFOLLOW_LINKS)) kept++;
            else missing.add(name);
        }
        List<Path> added = new ArrayList<>();
        try {
            for (String name : missing) {
                LocalFiles.interrupted();
                Path target = data.resolve(name);
                Files.createDirectories(target.getParent());
                Files.move(entries.get(name), target); // Never request REPLACE_EXISTING.
                added.add(target);
                if (entries.containsKey(name + ".name")) {
                    Path title = data.resolve(name + ".name");
                    Files.move(entries.get(name + ".name"), title);
                    added.add(title);
                }
            }
            LocalFiles.interrupted();
            writer.write(Collections.unmodifiableMap(settings));
            return new Restored(missing.size(), kept, settings.size());
        } catch (IOException | RuntimeException failed) {
            for (int i = added.size() - 1; i >= 0; --i) {
                try { Files.deleteIfExists(added.get(i)); }
                catch (IOException cleanup) { failed.addSuppressed(cleanup); }
            }
            throw failed;
        }
    }

    private static void checkParents(Path root, Path file) throws IOException {
        for (Path part : root.relativize(file.getParent())) {
            root = root.resolve(part);
            if (Files.exists(root, NOFOLLOW_LINKS) && !Files.isDirectory(root, NOFOLLOW_LINKS)) {
                throw new IOException("The existing save path isn't a directory.");
            }
        }
    }

    private static boolean isSlotPath(String name) {
        if (name.length() > 160) return false;
        String[] parts = name.split("/", -1);
        if ((parts.length != 3 && parts.length != 5) || !parts[0].equals("states")
                || !SLOT.matcher(parts[parts.length - 1]).matches()) return false;
        try { if (!parts[1].equals(RomStore.archiveName(parts[1]))) return false; }
        catch (IOException invalid) { return false; }
        return parts.length == 3 || parts[2].equals("ips") && IPS.matcher(parts[3]).matches();
    }

    private static void checkTitle(Path title) throws IOException {
        String name;
        try (InputStream in = Files.newInputStream(title)) {
            name = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(LocalFiles.read(in, MAX_LABEL))).toString();
        }
        if (name.isEmpty() || name.length() > 56 || BAD_LABEL.matcher(name).find()) {
            throw new IOException("Invalid backup slot name.");
        }
    }

    // Our small, single-volume ZIPs never use ZIP64 or comments. Bound the central directory
    // before ZipFile parses it, and reject truncated backups, even with intact early entries.
    private static int checkZipEnd(Path archive) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(archive.toFile(), "r")) {
            byte[] end = new byte[22];
            if (in.length() < end.length) throw new IOException("The backup ZIP is incomplete.");
            in.seek(in.length() - end.length);
            in.readFully(end);
            ByteBuffer eof = ByteBuffer.wrap(end).order(ByteOrder.LITTLE_ENDIAN);
            if (eof.getInt() != 0x06054b50 || eof.getInt() != 0) throw new IOException("Invalid backup ZIP footer.");
            int diskCount = Short.toUnsignedInt(eof.getShort()), count = Short.toUnsignedInt(eof.getShort());
            long bytes = Integer.toUnsignedLong(eof.getInt()), offset = Integer.toUnsignedLong(eof.getInt());
            if (count == 0 || count > MAX_FILES || diskCount != count || bytes == 0 || bytes > 2 * 1024 * 1024
                    || offset + bytes + end.length != in.length() || eof.getShort() != 0) throw new IOException("Invalid backup ZIP index.");
            return count;
        }
    }

    private static long copy(InputStream in, OutputStream out, long limit, CRC32 crc) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        for (int size; (size = in.read(buffer)) != -1; ) {
            LocalFiles.interrupted();
            if (size > limit - total) throw new IOException("The backup exceeds its size limits.");
            if (crc != null) crc.update(buffer, 0, size);
            out.write(buffer, 0, size);
            total += size;
        }
        return total;
    }
}
