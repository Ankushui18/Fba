package io.github.ankushui18.fbaoffline;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipException;

/** Shared by the file picker and folder browser. The selected provider's MIME type isn't a ROM format. */
final class GameImport {
    private GameImport() {}

    /** Keep provider/OS diagnostics (possibly private paths and identifiers) out of the UI. */
    static String failureMessage(Exception error) {
        if (error instanceof ConsoleStore.Mismatch || error instanceof RomStore.InvalidArchive) return error.getMessage();
        if (error instanceof ZipException) return "This file isn't a readable game ZIP.";
        if (error instanceof SecurityException) return "Android didn't grant read access. Pick the file again or copy it to Downloads first.";
        return "Couldn't read or copy the file. Check storage or choose a local copy from Downloads.";
    }

    // The caller owns the input stream and the document grant.
    static String add(Path library, String displayName, InputStream in, ConsoleStore sets) throws IOException {
        String name = displayName == null ? "" : displayName.trim();
        String ext = ConsoleSystem.extension(name);
        if (ext.equals("fba")) throw new ConsoleStore.Mismatch("Old .fba cache files aren't supported. Choose the matching FBNeo ZIP instead.");
        if (ConsoleSystem.isDisc(name)) throw new ConsoleStore.Mismatch("CD disc images aren't supported by this app build yet.");
        if (in == null) throw new ConsoleStore.Mismatch("The Android picker didn't provide the file contents. Pick it again or copy it to Downloads first.");
        if (ext.equals("zip")) return RomStore.importZip(library, name, in, sets == null ? null : sets::archiveTarget);

        // Some providers hide the suffix or return a generic .bin/.rom display name.
        // Only the unambiguous iNES signature can supply a missing format here.
        if (!ConsoleSystem.isRaw(name) || ext.equals("bin") || ext.equals("rom")) {
            BufferedInputStream buffered = new BufferedInputStream(in);
            byte[] header = new byte[4];
            buffered.mark(header.length);
            for (int pos = 0; pos < header.length;) {
                int count = buffered.read(header, pos, header.length - pos);
                if (count <= 0) break;
                pos += count;
            }
            buffered.reset();
            if (LooseNes.hasHeader(header)) {
                if (ext.equals("bin") || ext.equals("rom")) name = name.substring(0, name.lastIndexOf('.'));
                name += ".nes";
            }
            in = buffered;
        }
        if (!ConsoleSystem.isRaw(name)) throw new ConsoleStore.Mismatch("Choose a .nes cartridge, another supported console ROM, or an FBNeo ZIP.");
        if (sets == null) throw new ConsoleStore.Mismatch("The FBNeo console compatibility list couldn't be read.");
        return sets.importRaw(library, name, in);
    }
}
