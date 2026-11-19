package io.github.ankushui18.fbaoffline;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Atomic private slots bound to the ROM, variant, core pin and boot settings. */
final class StateStore {
    private static final String PIN = "2da48555c13ce5d011edf2d6ec829b7393c070af";
    private static final String FORMAT = "FBA1:" + PIN, PROFILE_FORMAT = "FBA2:" + PIN;
    static final int SLOTS = 6, MAX_BYTES = 48 * 1024 * 1024;
    static final long MAX_FILE_BYTES = MAX_BYTES + 128L;
    private final File rom, slots;
    private final String variant, profile;
    private byte[] fingerprint;

    StateStore(File root, File rom) { this(root, rom, null); }
    StateStore(File root, File rom, String variant) { this(root, rom, variant, ""); }

    StateStore(File root, File rom, String variant, String profile) {
        this.rom = rom;
        this.variant = variant;
        this.profile = profile;
        File folder = new File(root, rom.getName());
        slots = variant == null ? folder : new File(folder, "ips/" + variant);
    }

    File slot(int number) {
        if (number < 1 || number > SLOTS) throw new IllegalArgumentException("Invalid state slot.");
        return new File(slots, "slot-" + number + ".state");
    }

    File preview(int number) { return new File(slot(number).getPath() + ".png"); }

    private File labelFile(int number) { return new File(slot(number).getPath() + ".name"); }

    String label(int number) {
        if (slot(number).isFile() && labelFile(number).isFile()) {
            try (FileInputStream in = new FileInputStream(labelFile(number))) {
                String name = new String(LocalFiles.read(in, 256), StandardCharsets.UTF_8);
                if (!name.isEmpty()) return "Slot " + number + " · " + name;
            } catch (IOException ignored) { /* A missing label doesn't invalidate the save. */ }
        }
        return "Slot " + number;
    }

    void rename(int number, String name) throws IOException {
        if (!slot(number).isFile()) throw new IOException("This slot is empty.");
        String clean = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
        if (clean.length() > 56) clean = clean.substring(0, 56);
        File label = labelFile(number);
        if (clean.isEmpty()) Files.deleteIfExists(label.toPath());
        else LocalFiles.atomicWrite(label, clean.getBytes(StandardCharsets.UTF_8));
    }

    void save(int number, byte[] state) throws IOException { writeSnapshot(slot(number), state, null); }

    String romName() { return rom.getName(); }
    String variant() { return variant; }

    interface Preface { void write(DataOutputStream out) throws IOException; }

    /** Manual slots and Quick Resume share the same checked state payload and atomic replacement. */
    void writeSnapshot(File file, byte[] state, Preface preface) throws IOException {
        if (state == null || state.length == 0 || state.length > MAX_BYTES) throw new IOException("This driver doesn't support saving a state.");
        Path parent = file.getParentFile().toPath();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, "slot-", ".part");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                if (preface != null) preface.write(out);
                out.writeUTF(profile.isEmpty() ? FORMAT : PROFILE_FORMAT);
                out.write(snapshotFingerprint());
                out.writeInt(state.length);
                out.writeInt(crc(state));
                out.write(state);
            }
            LocalFiles.interrupted();
            Files.move(tmp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }

    byte[] load(int number) throws IOException {
        File file = slot(number);
        if (!file.isFile()) throw new IOException("This slot is empty.");
        try (DataInputStream in = openSlot(file.toPath())) { return readSnapshot(in, file.length()); }
    }

    byte[] readSnapshot(DataInputStream in, long fileBytes) throws IOException {
        Header header = new Header(in);
        boolean customized = !profile.isEmpty();
        if (header.profiled != customized || !Arrays.equals(header.fingerprint, snapshotFingerprint())) {
            throw new IOException(header.profiled || customized
                    ? "This state needs the same ROM revision and FBNeo settings used when it was saved."
                    : "This state belongs to a different ROM revision.");
        }
        if (header.length > fileBytes) throw new IOException("Invalid state length.");
        byte[] data = new byte[header.length];
        in.readFully(data);
        header.checkEnd(in, crc(data));
        return data;
    }

    // A portable backup can be restored before any ROMs are installed. Check the slot
    // format and payload now; the usual ROM/IPS fingerprint check still runs on load.
    static void validateBackupSlot(Path file) throws IOException {
        try (DataInputStream in = openSlot(file)) {
            Header header = new Header(in);
            CRC32 checksum = new CRC32();
            byte[] buffer = new byte[Math.min(header.length, 64 * 1024)];
            for (int left = header.length; left > 0;) {
                LocalFiles.interrupted();
                int size = Math.min(left, buffer.length);
                in.readFully(buffer, 0, size);
                checksum.update(buffer, 0, size);
                left -= size;
            }
            header.checkEnd(in, (int) checksum.getValue());
        }
    }

    private static DataInputStream openSlot(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("This state file is too large.");
        return new DataInputStream(new BufferedInputStream(Files.newInputStream(file)));
    }

    private static final class Header {
        final byte[] fingerprint = new byte[32];
        final int length, checksum;
        final boolean profiled;

        Header(DataInputStream in) throws IOException {
            String format = in.readUTF();
            profiled = PROFILE_FORMAT.equals(format);
            if (!profiled && !FORMAT.equals(format)) throw new IOException("This state needs a different FBNeo build.");
            in.readFully(fingerprint);
            length = in.readInt();
            checksum = in.readInt();
            if (length <= 0 || length > MAX_BYTES) throw new IOException("Invalid state length.");
        }

        void checkEnd(DataInputStream in, int crc) throws IOException {
            if (in.read() != -1 || crc != checksum) throw new IOException("This state file is damaged.");
        }
    }

    void delete(int number) throws IOException {
        Files.deleteIfExists(slot(number).toPath());
        Files.deleteIfExists(preview(number).toPath());
        Files.deleteIfExists(labelFile(number).toPath());
    }

    private byte[] snapshotFingerprint() throws IOException {
        if (fingerprint != null) return fingerprint;
        byte[] base = LocalFiles.hash(rom, RomStore.MAX_BYTES);
        if (variant != null) {
            MessageDigest digest = LocalFiles.sha256();
            digest.update(base);
            base = digest.digest(variant.getBytes(StandardCharsets.US_ASCII));
        }
        if (profile.isEmpty()) return fingerprint = base; // Preserve the original plain and IPS fingerprints.
        MessageDigest digest = LocalFiles.sha256();
        digest.update(base);
        return fingerprint = digest.digest(profile.getBytes(StandardCharsets.US_ASCII));
    }

    private static int crc(byte[] state) {
        CRC32 crc = new CRC32();
        crc.update(state);
        return (int) crc.getValue();
    }
}
