package io.github.ankushui18.fbaoffline;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

public final class StateStoreTest {
    interface Checked { void run() throws IOException; }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("fba-state-test");
        try {
            Path roms = Files.createDirectory(root.resolve("roms"));
            Path game = Files.write(roms.resolve("test.zip"), new byte[] {1, 2, 3});
            Path states = root.resolve("states");
            StateStore store = new StateStore(states.toFile(), game.toFile());
            rejected(() -> store.load(1), "empty");
            rejected(() -> store.save(1, new byte[0]), "doesn't support");
            try { store.slot(7); throw new AssertionError("out-of-range slot"); }
            catch (IllegalArgumentException expected) {}
            byte[] before = {42, 34}, after = {8, 12, 17};
            store.save(1, before);
            store.save(6, before);
            store.save(1, after);
            if (!Arrays.equals(store.load(6), before)) throw new AssertionError("slots are independent");
            if (!Arrays.equals(store.load(1), after)) throw new AssertionError("atomic replacement reads new state");
            try (RandomAccessFile slot = new RandomAccessFile(store.slot(1), "rw")) {
                slot.seek(slot.length() - 1);
                slot.write(99);
            }
            rejected(() -> store.load(1), "damaged");
            try (RandomAccessFile slot = new RandomAccessFile(store.slot(6), "rw")) {
                slot.setLength(slot.length() - 1);
            }
            rejected(() -> store.load(6), null);
            store.save(6, before);
            Files.write(game, new byte[] {1, 2, 4});
            StateStore reopened = new StateStore(states.toFile(), game.toFile());
            rejected(() -> reopened.load(6), "different ROM revision");
            Files.write(store.preview(6).toPath(), new byte[] {4, 5});
            store.delete(6);
            if (store.slot(6).exists() || store.preview(6).exists()) throw new AssertionError("slot/preview deletion");
            System.out.println("State slots, checksum/revision checks and atomic replacement tests passed.");
        } finally {
            try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
            }
        }
    }

    private static void rejected(Checked action, String reason) throws IOException {
        try { action.run(); }
        catch (IOException expected) {
            if (reason != null && !expected.getMessage().contains(reason)) throw expected;
            return;
        }
        throw new AssertionError("accepted invalid state: " + reason);
    }
}
