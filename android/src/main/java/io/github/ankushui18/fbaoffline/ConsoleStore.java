package io.github.ankushui18.fbaoffline;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Indexes the bundled FBNeo drivers, not arbitrary emulator ROM formats. */
final class ConsoleStore {
    static final String INDEX = "console-sets.tsv";
    private static final String FIRMWARE = "bios_";
    private static final int MAX_RAW = 64 * 1024 * 1024, COPIER_HEADER = 512;
    private static final long MAX_ZIP = 80L * 1024 * 1024;
    private static final Pattern SET = Pattern.compile("[a-z0-9_][a-z0-9_-]{0,79}");

    static final class Mismatch extends IOException {
        Mismatch(String message) { super(message); }
    }

    static final class Entry {
        final ConsoleSystem system;
        final String set, title, year, parent;
        final String[] dependencies;
        final long[] signatures;
        final boolean firmware, explicitOnly;
        final Vetted vetted;

        Entry(ConsoleSystem system, String set, String title, String year, String parent, String[] dependencies,
              long[] signatures, boolean firmware, boolean explicitOnly, Vetted vetted) {
            this.system = system;
            this.set = set;
            this.title = title;
            this.year = year;
            this.parent = parent;
            this.dependencies = dependencies;
            this.signatures = signatures;
            this.firmware = firmware;
            this.explicitOnly = explicitOnly;
            this.vetted = vetted;
        }

        String driver() { return system.prefix + "_" + set; }
        String archive() { return (firmware ? FIRMWARE : "") + driver() + ".zip"; }
        String member(int magic) {
            if (vetted != null) return vetted.member;
            if (system == ConsoleSystem.NES) return set + (magic == 0x554e4946 ? ".unf" : ".nes");
            return set + ".bin";
        }

        boolean accepts(long signature, int magic) {
            if (system == ConsoleSystem.NES && !firmware && magic != 0x4e45531a && magic != 0x554e4946) return false;
            return has(signature);
        }

        boolean has(long signature) {
            for (long expected : signatures) if (expected == signature) return true;
            return false;
        }
    }

    /** Only these separately verified bytes may use an otherwise strict driver's patched-ROM path. */
    private static final class Vetted {
        final String driver, member, sha256;
        Vetted(String driver, String member, String sha256) { this.driver = driver; this.member = member; this.sha256 = sha256; }
    }

    static final class Launch {
        final File rom, sessionSystem;
        final String driver, firmwareHint;
        final boolean patched;
        Launch(File rom, File sessionSystem, String driver, Set<String> missingFirmware, boolean patched) {
            this.rom = rom;
            this.sessionSystem = sessionSystem;
            this.driver = driver;
            this.patched = patched;
            this.firmwareHint = missingFirmware.isEmpty() ? "" : " If needed, add the matching " + String.join(", ", missingFirmware) + " BIOS files.";
        }

        File saveDirectory(File shared, File libraryArchive) {
            // Alternate and loose cartridges use their own battery data, even if
            // they share a common FBNeo driver with another local cartridge.
            String name = libraryArchive.getName();
            return sessionSystem == null ? shared : new File(shared, name.substring(0, name.length() - 4));
        }
    }

    private final Map<String, Entry> drivers = new HashMap<>();
    private final Map<String, List<Entry>> bare = new HashMap<>();
    private final Map<Long, List<Entry>> loose = new HashMap<>();

    static ConsoleStore load(InputStream input) throws IOException {
        ConsoleStore index = new ConsoleStore();
        try (BufferedReader rows = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (String line; (line = rows.readLine()) != null;) {
                String[] row = line.split("\t", -1);
                if (index.drivers.size() >= 32768 || row.length != 8 || row[4].isEmpty() || !SET.matcher(row[1]).matches()
                        || (!row[5].isEmpty() && !SET.matcher(row[5]).matches()) || !row[2].matches("[BGP]")) throw invalidIndex();
                ConsoleSystem system = ConsoleSystem.fromPrefix(row[0]);
                String[] deps = row[6].isEmpty() ? new String[0] : row[6].split(",", -1);
                String[] members = row[7].split(",", -1);
                if (system == null || deps.length > 8 || members.length > 32) throw invalidIndex();
                for (String dep : deps) if (!SET.matcher(dep).matches()) throw invalidIndex();
                long[] signatures = new long[members.length];
                for (int i = 0; i < members.length; i++) {
                    String[] sig = members[i].split(":", -1);
                    if (sig.length != 2 || !sig[1].matches("[0-9a-f]{8}")) throw invalidIndex();
                    int size = Integer.parseInt(sig[0]);
                    if (size <= 0 || size > MAX_RAW) throw invalidIndex();
                    signatures[i] = key(size, Long.parseLong(sig[1], 16));
                }
                index.add(new Entry(system, row[1], row[4], row[3], row[5], deps, signatures,
                        row[2].equals("B"), row[2].equals("P"), null));
            }
        } catch (NumberFormatException malformed) { throw invalidIndex(); }
        if (index.drivers.isEmpty()) throw invalidIndex();
        Entry ninja = index.drivers.get("nes_ninjagaiden");
        if (ninja != null && ninja.has(key(262160, 0x11f953f6L))) {
            Vetted verifiedUs = new Vetted(ninja.set, "Ninja Gaiden (USA)(1989)(Tecmo).nes",
                    "6ea9feab80bddb05546444ee62124d98585422c9ef499e4bd20e7249c51fe548");
            index.add(new Entry(ninja.system, "ninjagaiden_alt_ed1d282b", ninja.title + " (alternate dump)", ninja.year,
                    "", new String[0], new long[] {key(262160, 0xed1d282bL)}, false, true, verifiedUs));
        }
        return index;
    }

    private void add(Entry entry) throws IOException {
        if (drivers.put(entry.driver(), entry) != null) throw invalidIndex();
        if (!entry.explicitOnly) bare.computeIfAbsent(entry.set, ignored -> new ArrayList<>()).add(entry);
        if (!entry.firmware && entry.signatures.length == 1) {
            loose.computeIfAbsent(entry.signatures[0], ignored -> new ArrayList<>()).add(entry);
        }
    }

    private static IOException invalidIndex() { return new IOException("The FBNeo console compatibility list isn't available."); }
    Collection<Entry> entries() { return Collections.unmodifiableCollection(drivers.values()); }
    static boolean isFirmware(String name) { return name.startsWith(FIRMWARE) && ConsoleSystem.fromArchive(name.substring(FIRMWARE.length())) != null; }
    private static long key(long size, long crc) { return (size << 32) | crc; }

    String importRaw(Path library, String name, InputStream in) throws IOException {
        String extension = ConsoleSystem.extension(name);
        if (!ConsoleSystem.isRaw(name)) throw new Mismatch("Choose a matching FBNeo ZIP or a supported console ROM file.");
        Files.createDirectories(library);
        Path source = Files.createTempFile(library, ".console-", ".part");
        Path targetPart = null;
        try {
            boolean snes = extension.equals("sfc") || extension.equals("smc");
            Probe probe;
            try (OutputStream out = Files.newOutputStream(source)) {
                probe = Probe.scan(in, out, snes ? MAX_RAW + COPIER_HEADER : MAX_RAW, snes, false);
            }
            List<Entry> matches = rawMatches(extension, probe.signature(), probe.magic, source);
            int offset = 0;
            long signature = probe.signature();
            if (matches.isEmpty() && snes && probe.size > COPIER_HEADER && probe.size % 1024 == COPIER_HEADER) {
                signature = key(probe.size - COPIER_HEADER, probe.withoutHeader);
                matches = rawMatches(extension, signature, probe.magic, source);
                offset = COPIER_HEADER;
            }
            if (matches.isEmpty() && extension.equals("nes")) return LooseNes.importCartridge(library, source);
            Entry entry = choose(matches, name.substring(0, name.lastIndexOf('.')));
            String target = entry.archive();
            Path archive = library.resolve(target);
            if (Files.isRegularFile(archive) && Files.size(archive) <= MAX_ZIP) {
                try (ZipFile current = new ZipFile(archive.toFile())) {
                    if (contains(current, entry)) return target;
                } catch (InterruptedIOException cancelled) { throw cancelled; }
                catch (IOException broken) { /* Replace only after a complete new archive is written. */ }
            }
            targetPart = Files.createTempFile(library, ".console-", ".part");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(targetPart));
                 InputStream rom = Files.newInputStream(source)) {
                ZipEntry member = new ZipEntry(entry.member(probe.magic));
                member.setMethod(ZipEntry.STORED);
                member.setTime(0);
                member.setSize(signature >>> 32);
                member.setCrc(signature & 0xffffffffL);
                out.putNextEntry(member);
                if (offset != 0 && rom.skip(offset) != offset) throw new IOException("Couldn't read the console file.");
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = rom.read(buffer)) != -1;) {
                    LocalFiles.interrupted();
                    out.write(buffer, 0, n);
                }
                out.closeEntry();
            }
            LocalFiles.interrupted();
            Files.move(targetPart, archive, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally {
            Files.deleteIfExists(source);
            if (targetPart != null) Files.deleteIfExists(targetPart);
        }
    }

    private List<Entry> rawMatches(String extension, long signature, int magic, Path source) throws IOException {
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : loose.getOrDefault(signature, Collections.emptyList())) {
            if (!entry.system.accepts(extension) || !entry.accepts(signature, magic)) continue;
            if (entry.vetted != null && !entry.vetted.sha256.equals(LocalFiles.hex(LocalFiles.hash(source.toFile(), MAX_RAW), 32))) continue;
            matches.add(entry);
        }
        return matches;
    }

    private static Entry choose(List<Entry> matches, String stem) throws Mismatch {
        if (matches.size() == 1) return matches.get(0);
        if (matches.isEmpty()) throw new Mismatch("This file doesn't match a supported console ROM in this FBNeo build. Use the matching dump or FBNeo set ZIP.");
        for (Entry entry : matches) if (entry.driver().equalsIgnoreCase(stem)) return entry;
        Entry named = null;
        for (Entry entry : matches) {
            if (!entry.set.equalsIgnoreCase(stem) && !entry.title.equalsIgnoreCase(stem)) continue;
            if (named != null) throw ambiguous();
            named = entry;
        }
        if (named == null) throw ambiguous();
        return named;
    }

    private static Mismatch ambiguous() { return new Mismatch("This file matches several FBNeo sets. Use the system-prefixed set ZIP (for example md_sonic.zip)."); }

    String archiveTarget(String name, ZipFile zip) throws IOException {
        String stem = name.substring(0, name.length() - 4);
        if (isFirmware(name)) stem = stem.substring(FIRMWARE.length());
        if (ConsoleSystem.fromArchive(stem) != null) {
            Entry entry = drivers.get(stem);
            if (entry == null || (isFirmware(name) && !entry.firmware)) throw new Mismatch("That console set name isn't in this FBNeo build.");
            if (!contains(zip, entry)) throw new Mismatch("This ZIP doesn't contain matching ROM data for " + entry.title + ".");
            return checkedTarget(zip, entry);
        }
        // A bare name like 1942 or sonic can belong to more than one system. Never route it by name alone.
        Entry match = null;
        for (Entry entry : bare.getOrDefault(stem, Collections.emptyList())) {
            if (!contains(zip, entry)) continue;
            if (match != null) throw ambiguous();
            match = entry;
        }
        return match == null ? name : checkedTarget(zip, match);
    }

    private static String checkedTarget(ZipFile zip, Entry entry) throws IOException {
        if (Files.size(new File(zip.getName()).toPath()) > MAX_ZIP) throw new Mismatch("Console ZIPs must be 80 MiB or smaller.");
        return entry.archive();
    }

    private static boolean contains(ZipFile zip, Entry entry) throws IOException {
        if (entry.vetted != null && zip.size() != 1) return false;
        long remaining = MAX_ZIP;
        for (Enumeration<? extends ZipEntry> members = zip.entries(); members.hasMoreElements();) {
            LocalFiles.interrupted();
            ZipEntry member = members.nextElement();
            long size = member.getSize();
            if (member.isDirectory() || size <= 0 || size > MAX_RAW || !entry.has(key(size, member.getCrc()))
                    || (entry.vetted != null && !entry.vetted.member.equals(member.getName()))) continue;
            if ((remaining -= size) < 0) throw new Mismatch("Too much ROM data to check in this console ZIP.");
            try (InputStream in = zip.getInputStream(member)) {
                Probe actual = Probe.scan(in, null, (int) size, false, entry.vetted != null);
                if (actual.signature() == key(size, member.getCrc()) && entry.accepts(actual.signature(), actual.magic)
                        && (entry.vetted == null || entry.vetted.sha256.equals(actual.sha256))) return true;
            }
        }
        return false;
    }

    /** Expose only the current game/parents/BIOS in the folder name the pinned core uses for routing. */
    Launch stage(File rom, Path session) throws IOException {
        String name = RomStore.archiveName(rom.getName());
        if (LooseNes.isArchive(name)) return LooseNes.stage(rom, session, name);
        Entry entry = drivers.get(name.substring(0, name.length() - 4));
        if (entry == null || entry.firmware || isFirmware(name)) throw new Mismatch("Re-add a matching FBNeo console game ZIP or ROM file.");
        Path folder = Files.createDirectory(session.resolve(entry.system.folder));
        String shortSet = entry.vetted == null ? entry.set : entry.vetted.driver;
        String unprefixed = shortSet + ".zip";
        Path target = folder.resolve(unprefixed);
        stageFile(rom.toPath(), target);
        Path library = rom.toPath().getParent();
        Set<String> needs = new LinkedHashSet<>(), missingFirmware = new LinkedHashSet<>();
        if (!entry.parent.isEmpty()) needs.add(entry.parent);
        Collections.addAll(needs, entry.dependencies);
        for (String shortName : needs) {
            Entry dep = drivers.get(entry.system.prefix + "_" + shortName);
            if (dep == null || dep == entry) continue;
            Path input = library.resolve(dep.archive());
            String plain = shortName + ".zip";
            if (!Files.isRegularFile(input)) input = library.resolve(plain); // Previously imported bare parent/BIOS.
            if (!Files.isRegularFile(input) && dep.firmware) input = library.resolve(dep.driver() + ".zip");
            if (Files.isRegularFile(input) && Files.size(input) <= MAX_ZIP) stageFile(input, folder.resolve(plain));
            else if (dep.firmware) missingFirmware.add(plain);
        }
        File patched = null;
        if (entry.vetted != null) {
            try (ZipFile zip = new ZipFile(target.toFile())) {
                if (!contains(zip, entry)) throw new Mismatch("This alternate dump no longer matches the verified ROM. Re-add the original file.");
            }
            Path system = session.resolve("system");
            Path patch = Files.createDirectories(system.resolve("fbneo/patched")).resolve(unprefixed);
            stageFile(target, patch);
            patched = system.toFile();
        }
        return new Launch(target.toFile(), patched, entry.system.prefix + "_" + shortSet, missingFirmware, patched != null);
    }

    private static void stageFile(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > MAX_ZIP) throw new Mismatch("Use a matching console ZIP up to 80 MiB.");
        LocalFiles.interrupted();
        try { Files.createLink(target, source); }
        catch (IOException | UnsupportedOperationException noLinks) {
            try (InputStream in = Files.newInputStream(source); OutputStream out = Files.newOutputStream(target)) {
                Probe.scan(in, out, (int) MAX_ZIP, false, false);
            }
        }
    }

    private static final class Probe {
        final int size, magic;
        final long crc, withoutHeader;
        final String sha256;
        Probe(int size, int magic, long crc, long withoutHeader, String sha256) {
            this.size = size; this.magic = magic; this.crc = crc; this.withoutHeader = withoutHeader; this.sha256 = sha256;
        }
        long signature() { return key(size, crc); }

        static Probe scan(InputStream in, OutputStream copy, int limit, boolean skipHeader, boolean hash) throws IOException {
            CRC32 crc = new CRC32(), stripped = new CRC32();
            MessageDigest digest = hash ? LocalFiles.sha256() : null;
            byte[] bytes = new byte[64 * 1024];
            int size = 0, magic = 0;
            for (int n; (n = in.read(bytes)) != -1;) {
                LocalFiles.interrupted();
                if (n > limit - size) throw new Mismatch("This console file is too large or has an incorrect ZIP entry size.");
                for (int i = 0; i < n && size + i < 4; i++) magic = (magic << 8) | (bytes[i] & 0xff);
                if (copy != null) copy.write(bytes, 0, n);
                crc.update(bytes, 0, n);
                if (digest != null) digest.update(bytes, 0, n);
                if (skipHeader) {
                    int skip = Math.max(0, COPIER_HEADER - size);
                    if (n > skip) stripped.update(bytes, skip, n - skip);
                }
                size += n;
            }
            return new Probe(size, magic, crc.getValue(), stripped.getValue(), digest == null ? "" : LocalFiles.hex(digest.digest(), 32));
        }
    }
}
