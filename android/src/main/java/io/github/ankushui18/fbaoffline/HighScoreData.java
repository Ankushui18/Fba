package io.github.ankushui18.fbaoffline;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** A hiscore DAT supplies memory offsets. Use only the exact data from our FBNeo pin. */
final class HighScoreData {
    private static final int BYTES = 328397;
    private static final String SHA256 = "efd00af9a44241c2aa094d5f1977aa68fc0bb8467dbcf82f32762977d37a6f17";
    private HighScoreData() {}

    private static File file(File system) { return new File(system, "fbneo/hiscore.dat"); }

    static boolean installed(File system) { return file(system).isFile() && file(system).length() == BYTES; }

    static boolean verified(File system) throws IOException {
        return installed(system) && SHA256.equals(LocalFiles.hex(LocalFiles.hash(file(system), BYTES), 32));
    }

    static void install(File system, InputStream in) throws IOException {
        byte[] bytes = LocalFiles.read(in, BYTES);
        if (bytes.length != BYTES || !SHA256.equals(LocalFiles.hex(LocalFiles.sha256().digest(bytes), 32))) {
            throw new IOException("This isn't the hiscore.dat for the FBNeo build. Choose the unmodified file linked in the app's setup guide.");
        }
        LocalFiles.atomicWrite(file(system), bytes);
    }

    static void remove(File system) throws IOException { Files.deleteIfExists(file(system).toPath()); }
}
