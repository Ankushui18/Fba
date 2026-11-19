package io.github.ankushui18.fbaoffline;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.regex.Pattern;

/** One local-only autosave. The target ROM/IPS identity and state are replaced together. */
final class QuickResumeStore {
    private static final int MAGIC = 0x46424151, VERSION = 1; // FBAQ
    private static final long MAX_FILE_BYTES = StateStore.MAX_FILE_BYTES + 160;
    private static final Pattern IPS = Pattern.compile("[0-9a-f]{32}");
    private final File file;

    static final class Entry {
        final String rom, variant;

        private Entry(String rom, String variant) throws IOException {
            if (!rom.equals(RomStore.archiveName(rom)) || variant != null && !IPS.matcher(variant).matches()) {
                throw new IOException("Invalid Quick Resume target.");
            }
            this.rom = rom;
            this.variant = variant;
        }
    }

    QuickResumeStore(File privateData) { file = new File(privateData, "quick-resume/latest.state"); }

    boolean exists() { return file.isFile(); }

    // Read only the small identity header for the library button, never load/hash the ROM on the UI thread.
    Entry latest() {
        if (!exists()) return null;
        try (DataInputStream in = open()) { return identity(in); }
        catch (IOException damaged) { return null; }
    }

    void save(StateStore states, byte[] snapshot) throws IOException {
        Entry target = new Entry(states.romName(), states.variant());
        states.writeSnapshot(file, snapshot, out -> {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(target.rom);
            out.writeUTF(target.variant == null ? "" : target.variant);
        });
    }

    byte[] load(StateStore states) throws IOException {
        if (!exists()) throw new IOException("There isn't a Quick Resume save yet.");
        try (DataInputStream in = open()) {
            Entry target = identity(in);
            if (!target.rom.equals(states.romName()) || !Objects.equals(target.variant, states.variant())) {
                throw new IOException("The Quick Resume target changed. Choose Continue again.");
            }
            // Reuse the FBNeo pin, ROM/IPS fingerprint, state length and CRC checks of a manual slot.
            return states.readSnapshot(in, file.length());
        }
    }

    void clear() throws IOException { Files.deleteIfExists(file.toPath()); }

    private DataInputStream open() throws IOException {
        if (Files.size(file.toPath()) > MAX_FILE_BYTES) throw new IOException("The Quick Resume save is too large.");
        return new DataInputStream(new BufferedInputStream(Files.newInputStream(file.toPath())));
    }

    private static Entry identity(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unsupported Quick Resume save.");
        String rom = in.readUTF(), variant = in.readUTF();
        return new Entry(rom, variant.isEmpty() ? null : variant);
    }
}
