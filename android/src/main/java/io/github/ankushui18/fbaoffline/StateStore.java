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

/** Atomic, versioned private state slots. A state is valid only for the same ROM bytes and core pin. */
final class StateStore {
    private static final String FORMAT = "FBA1:2da48555c13ce5d011edf2d6ec829b7393c070af";
    static final int SLOTS = 6, MAX_BYTES = 48 * 1024 * 1024;
    private final File rom, slots;
    private final String variant;
    private byte[] fingerprint;

    StateStore(File root, File rom) { this(root, rom, null); }

    StateStore(File root, File rom, String variant) {
        this.rom = rom;
        this.variant = variant;
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

    void save(int number, byte[] state) throws IOException {
        if (state == null || state.length == 0 || state.length > MAX_BYTES) throw new IOException("This driver doesn't support saving a state.");
        File file = slot(number);
        Path parent = file.getParentFile().toPath();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, "slot-", ".part");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeUTF(FORMAT);
                out.write(romFingerprint());
                out.writeInt(state.length);
                out.writeInt(crc(state));
                out.write(state);
            }
            Files.move(tmp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }

    byte[] load(int number) throws IOException {
        File file = slot(number);
        if (!file.isFile()) throw new IOException("This slot is empty.");
        if (file.length() > MAX_BYTES + 128L) throw new IOException("This state file is too large.");
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (!FORMAT.equals(in.readUTF())) throw new IOException("This state needs a different FBNeo build.");
            byte[] target = new byte[32];
            in.readFully(target);
            if (!Arrays.equals(target, romFingerprint())) throw new IOException("This state belongs to a different ROM revision.");
            int len = in.readInt(), crc = in.readInt();
            if (len <= 0 || len > MAX_BYTES || len > file.length()) throw new IOException("Invalid state length.");
            byte[] data = new byte[len];
            in.readFully(data);
            if (in.read() != -1 || crc(data) != crc) throw new IOException("This state file is damaged.");
            return data;
        }
    }

    void delete(int number) throws IOException {
        Files.deleteIfExists(slot(number).toPath());
        Files.deleteIfExists(preview(number).toPath());
        Files.deleteIfExists(labelFile(number).toPath());
    }

    private byte[] romFingerprint() throws IOException {
        if (fingerprint != null) return fingerprint;
        byte[] base = LocalFiles.hash(rom, RomStore.MAX_BYTES);
        if (variant == null) return fingerprint = base; // Preserve v0.3 state compatibility.
        MessageDigest digest = LocalFiles.sha256();
        digest.update(base);
        return fingerprint = digest.digest(variant.getBytes(StandardCharsets.US_ASCII));
    }

    private static int crc(byte[] state) {
        CRC32 crc = new CRC32();
        crc.update(state);
        return (int) crc.getValue();
    }
}
