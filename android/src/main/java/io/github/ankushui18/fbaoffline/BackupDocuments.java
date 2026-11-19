package io.github.ankushui18.fbaoffline;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** SAF I/O and the typed preferences bridge. Called only from a library background job. */
final class BackupDocuments {
    private final Context app;
    private final SharedPreferences prefs;

    BackupDocuments(Context context, SharedPreferences prefs) {
        app = context.getApplicationContext();
        this.prefs = prefs;
    }

    int exportZip(Uri document) throws IOException {
        GameSession.awaitIdle();
        Path work = Files.createTempDirectory(app.getCacheDir().toPath(), "arcade-export-");
        try {
            Path zip = work.resolve("backup.zip");
            int slots;
            try (OutputStream out = Files.newOutputStream(zip)) {
                slots = BackupStore.exportZip(app.getNoBackupFilesDir().toPath(), prefs.getAll(), out);
            }
            LocalFiles.interrupted();
            // Hand the picker only a complete ZIP. Report a provider/write failure, not success.
            try (InputStream in = Files.newInputStream(zip);
                 OutputStream out = app.getContentResolver().openOutputStream(document, "wt")) {
                if (out == null) throw new IOException("Couldn't open the backup destination.");
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = in.read(buffer)) != -1;) {
                    LocalFiles.interrupted();
                    out.write(buffer, 0, n);
                }
            }
            return slots;
        } finally { LocalFiles.deleteTree(work.toFile()); }
    }

    BackupStore.Restored restoreZip(Uri document) throws IOException {
        GameSession.awaitIdle();
        return BackupStore.restoreZip(app.getNoBackupFilesDir().toPath(), app.getCacheDir().toPath(),
                app.getContentResolver().openInputStream(document), this::restoreSettings);
    }

    private void restoreSettings(Map<String, Object> restored) throws IOException {
        if (restored.isEmpty()) return;
        Map<String, ?> previous = prefs.getAll();
        SharedPreferences.Editor update = prefs.edit(), undo = prefs.edit();
        for (Map.Entry<String, Object> option : restored.entrySet()) {
            String key = option.getKey();
            put(update, key, option.getValue());
            put(undo, key, previous.get(key));
        }
        LocalFiles.interrupted();
        boolean committed = false;
        try { committed = update.commit(); }
        finally {
            // A failed commit may already have changed the in-memory preferences.
            if (!committed) undo.commit();
        }
        if (!committed) throw new IOException("Couldn't save the restored settings. Check available storage and try again.");
    }

    @SuppressWarnings("unchecked")
    private static void put(SharedPreferences.Editor edit, String key, Object value) {
        if (value == null) edit.remove(key);
        else if (value instanceof Boolean) edit.putBoolean(key, (boolean) value);
        else if (value instanceof Integer) edit.putInt(key, (int) value);
        else if (value instanceof String) edit.putString(key, (String) value);
        // Other SharedPreferences types aren't v1 backup settings, but preserve them on rollback.
        else if (value instanceof Float) edit.putFloat(key, (float) value);
        else if (value instanceof Long) edit.putLong(key, (long) value);
        else edit.putStringSet(key, (Set<String>) value);
    }
}
