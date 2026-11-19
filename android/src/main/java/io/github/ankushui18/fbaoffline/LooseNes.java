package io.github.ankushui18.fbaoffline;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Loads ordinary, non-DAT NES cartridges through the pinned FBNeo RomData interface. */
final class LooseNes {
    private static final String PREFIX = "nes_local_", MEMBER = "cartridge.nes", DRIVER = "nes_fba_local";
    private static final Pattern ARCHIVE = Pattern.compile(PREFIX + "[0-9a-f]{64}\\.zip");
    private static final int MAX_BYTES = 16 + 512 + 768 * 1024, MAX_DUMP_PADDING = 512;
    private static final String MATCHING_SET = " A matching FBNeo NES ZIP can still work if this game has one.";

    private LooseNes() {}

    static boolean isArchive(String name) { return name != null && ARCHIVE.matcher(name).matches(); }

    static boolean hasHeader(byte[] data) {
        return data.length >= 4 && data[0] == 'N' && data[1] == 'E' && data[2] == 'S' && data[3] == 0x1a;
    }

    static String displayName(String name) {
        if (name == null) return "";
        String clean = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
        String ext = ConsoleSystem.extension(clean);
        if (ext.equals("nes") || ext.equals("bin") || ext.equals("rom")) clean = clean.substring(0, clean.lastIndexOf('.')).trim();
        int n = clean.codePointCount(0, clean.length());
        return n <= 80 ? clean : clean.substring(0, clean.offsetByCodePoints(0, 80)).trim();
    }

    /** Old dumps can end in a small run of filler bytes. Only touch the private import copy. */
    static boolean trimPadding(Path copy, int length) throws IOException {
        if (length <= 16) return false;
        try (RandomAccessFile file = new RandomAccessFile(copy.toFile(), "rw")) {
            byte[] header = new byte[16];
            file.readFully(header);
            if (!hasHeader(header) || header[4] == 0 || (header[7] & 3) != 0) return false;
            if ((header[7] & 12) == 8 && (header[9] != 0 || header[14] != 0)) return false;
            int end = payloadEnd(header);
            int padding = length - end;
            if (padding <= 0 || padding > MAX_DUMP_PADDING) return false;
            file.seek(end);
            int fill = file.read();
            if (fill != 0 && fill != 255) return false;
            for (int i = 1; i < padding; i++) if (file.read() != fill) return false;
            LocalFiles.interrupted();
            file.setLength(end);
            return true;
        }
    }

    private static int payloadEnd(byte[] header) {
        return 16 + ((header[6] & 4) == 0 ? 0 : 512) + (header[4] & 255) * 16384 + (header[5] & 255) * 8192;
    }

    static String importCartridge(Path library, Path source) throws IOException {
        if (Files.size(source) > MAX_BYTES) throw invalid("This cartridge is too large for direct NES play." + MATCHING_SET);
        Cartridge cartridge;
        try (InputStream in = Files.newInputStream(source)) {
            cartridge = new Cartridge(LocalFiles.read(in, MAX_BYTES));
        }
        String name = cartridge.archive();
        Path target = library.resolve(name);
        if (Files.isRegularFile(target)) {
            try {
                readArchive(target.toFile(), name);
                return name; // Preserve existing ZIP bytes and state fingerprints, even after a renamed import.
            } catch (InterruptedIOException cancelled) { throw cancelled; }
            catch (IOException damaged) { /* Replace only with a complete verified archive. */ }
        }
        Path temp = Files.createTempFile(library, ".nes-", ".part");
        try {
            cartridge.writeZip(temp);
            LocalFiles.interrupted();
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return name;
        } finally { Files.deleteIfExists(temp); }
    }

    static ConsoleStore.Launch stage(File archive, Path session, String name) throws IOException {
        Cartridge cart = readArchive(archive, name);
        Path folder = Files.createDirectory(session.resolve("nes"));
        cart.writeZip(folder.resolve("fba_local.zip"));
        Path descriptor = folder.resolve("fba_local.dat");
        // nes_smb uses the ordinary two-pad NESInit, which reads mapper and mirroring
        // from the cartridge. No Mario ROM is used. All DAT fields are generated here.
        // Give the descriptor a different chip name than the archive member; FBNeo
        // can only match it by CRC, not via RomData's fallback name match.
        Files.write(descriptor, String.format(Locale.ROOT,
                "DrvName nes_smb\nZipName %s\nFullName \"Local NES cartridge\"\n"
                        + "image.nes 0x%x 0x%08x BRF_ESS BRF_PRG\n", DRIVER, cart.data.length, cart.crc)
                .getBytes(StandardCharsets.US_ASCII));
        return new ConsoleStore.Launch(descriptor.toFile(), Files.createDirectory(session.resolve("system")).toFile(),
                DRIVER, Collections.emptySet(), false);
    }

    private static Cartridge readArchive(File file, String name) throws IOException {
        if (!file.isFile() || file.length() > MAX_BYTES + 4096) throw damaged();
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry member = zip.getEntry(MEMBER);
            if (zip.size() != 1 || member == null || member.getSize() <= 0 || member.getSize() > MAX_BYTES) throw damaged();
            try (InputStream in = zip.getInputStream(member)) {
                Cartridge cart = new Cartridge(LocalFiles.read(in, (int) member.getSize()));
                if (cart.data.length != member.getSize() || cart.crc != member.getCrc() || !cart.archive().equals(name)) throw damaged();
                return cart;
            }
        } catch (java.util.zip.ZipException malformed) { throw damaged(); }
    }

    private static ConsoleStore.Mismatch damaged() { return invalid("This local NES copy is damaged. Re-add the original .nes file."); }
    private static ConsoleStore.Mismatch invalid(String message) { return new ConsoleStore.Mismatch(message); }

    private static final class Cartridge {
        final byte[] data;
        final long crc;
        final String sha;

        Cartridge(byte[] data) throws IOException {
            int mapper = validateHeader(data);
            this.data = data;
            CRC32 sum = new CRC32();
            sum.update(data);
            crc = sum.getValue();
            // The pinned core has CRC-specific geometry/mapper overrides, VS
            // protection and a mapper-71 PRG hot patch. Keep them on the exact set path.
            // The RomData parser also reserves UINT32_MAX as an invalid field.
            if (crc == 0x2a798367L || crc == 0xdd65a6ccL || crc == 0x9f2251e4L || crc == 0xd81d33daL
                    || crc == 0xac95e2c9L || (mapper == 71 && crc == 0xa64e926aL) || crc == 0xffffffffL) {
                throw invalid("This cartridge needs a matching FBNeo NES set rather than the direct loader.");
            }
            sha = LocalFiles.hex(LocalFiles.sha256().digest(data), 32);
        }

        String archive() { return PREFIX + sha + ".zip"; }

        void writeZip(Path path) throws IOException {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
                ZipEntry member = new ZipEntry(MEMBER);
                member.setMethod(ZipEntry.STORED);
                member.setTime(0);
                member.setSize(data.length);
                member.setCrc(crc);
                zip.putNextEntry(member);
                zip.write(data);
                zip.closeEntry();
            }
        }
    }

    private static int validateHeader(byte[] data) throws IOException {
        if (data.length < 16 || !hasHeader(data)) {
            throw invalid("This isn't a valid .nes cartridge. Select an extracted iNES or NES 2.0 file, not a renamed ZIP.");
        }
        int prg = data[4] & 255, chr = data[5] & 255, flags = data[6] & 255, kind = data[7] & 255;
        boolean diskDude = new String(data, 7, 9, StandardCharsets.US_ASCII).equals("DiskDude!");
        boolean nes20 = (kind & 12) == 8;
        if ((kind & 3) != 0 || (!nes20 && !diskDude && (kind & 12) != 0)) {
            throw invalid("This .nes header isn't for a standard home NES cartridge." + MATCHING_SET);
        }
        if (nes20 && data[9] != 0) {
            throw invalid("This NES 2.0 file uses extended ROM sizes that this direct loader doesn't support." + MATCHING_SET);
        }
        int required = payloadEnd(data);
        if (prg == 0) throw invalid("The .nes header declares no program ROM.");
        if (data.length < required) throw invalid("The .nes file is incomplete: its header needs " + required + " bytes but the file has " + data.length + ".");
        if (data.length != required) throw invalid("The .nes file has " + (data.length - required) + " extra bytes after the declared cartridge data." + MATCHING_SET);
        int mapper = (flags >>> 4) | (diskDude ? 0 : kind & 0xf0);
        if (nes20) mapper |= (data[8] & 15) << 8;
        boolean layout;
        switch (mapper) {
            case 0: layout = prg <= 2 && chr <= 1; break;                 // NROM
            case 1: layout = prg <= 32 && chr <= 16; break;              // MMC1
            case 2: layout = prg <= 16 && chr <= 1; break;               // UxROM
            case 3: layout = prg <= 2 && chr >= 1 && chr <= 4; break;    // CNROM
            case 4: layout = prg >= 2 && prg <= 32 && chr <= 32; break;  // MMC3
            case 7: layout = prg >= 2 && prg <= 16 && chr == 0; break;   // AxROM
            case 9: layout = prg >= 2 && prg <= 8 && chr >= 1 && chr <= 16; break; // MMC2
            case 10: layout = prg >= 2 && prg <= 16 && chr >= 1 && chr <= 16; break; // MMC4
            case 11: layout = prg >= 2 && prg <= 8 && chr >= 1 && chr <= 16; break; // Color Dreams
            case 34: layout = prg >= 2 && (chr == 0 ? prg <= 16 : prg <= 4 && chr <= 8); break; // BNROM / NINA-001
            case 66: layout = prg >= 2 && prg <= 8 && chr >= 1 && chr <= 4; break; // GxROM
            case 71: layout = prg >= 2 && prg <= 16 && chr == 0; break;  // Camerica
            case 87: layout = prg <= 2 && chr >= 1 && chr <= 4; break;   // Jaleco/JF boards
            case 94: layout = prg >= 2 && prg <= 8 && chr == 0; break;    // UN1ROM
            default: throw invalid("NES mapper " + mapper + " isn't supported for direct play yet." + MATCHING_SET);
        }
        if (!layout || (prg & (prg - 1)) != 0 || (chr & (chr - 1)) != 0) {
            throw invalid("Mapper " + mapper + " has an unsupported ROM layout (" + (prg * 16) + " KiB program, "
                    + (chr * 8) + " KiB graphics)." + MATCHING_SET);
        }
        if (nes20) {
            int submapper = (data[8] & 255) >>> 4;
            boolean mapper34Variant = mapper == 34 && (submapper == 1 && chr > 0 || submapper == 2 && chr == 0);
            if (submapper != 0 && !mapper34Variant) {
                throw invalid("Mapper " + mapper + ", submapper " + submapper + " isn't supported for direct play yet." + MATCHING_SET);
            }
            int wram = data[10] & 15, sram = (data[10] & 255) >>> 4;
            int ramLimit = mapper == 1 ? 9 : 7;
            if (wram > ramLimit || sram > ramLimit || (wram != 0 && sram != 0)
                    || ((flags & 4) != 0 && Math.max(wram, sram) != 7) || (chr == 0 ? data[11] != 7 : data[11] != 0)) {
                throw invalid("Mapper " + mapper + " declares a RAM layout not supported for direct play." + MATCHING_SET);
            }
            if ((data[12] & 255) > 1 || data[13] != 0 || data[14] != 0 || (data[15] & 255) > 1) {
                throw invalid("Mapper " + mapper + " uses a NES 2.0 timing, cartridge or controller variant not supported for direct play." + MATCHING_SET);
            }
        }
        return mapper;
    }
}
