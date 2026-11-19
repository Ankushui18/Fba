package io.github.ankushui18.fbaoffline;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Counts library entries, not just successful file copies, and preserves each failure reason. */
final class ImportReport {
    private static final int MAX_LABEL = 140, MAX_REASON = 280;
    private final Set<String> listed = new HashSet<>();
    // One detail per skipped or already-listed file, within the picker's/folder's batch limit.
    private final List<String> attention = new ArrayList<>();
    private int added, existing, bios, rejected;
    private String lastTitle;

    ImportReport(File library) {
        File[] archives = library.listFiles(RomStore::isArchive);
        if (archives != null) for (File archive : archives) listed.add(archive.getName());
    }

    void saved(String sourceName, String archive, String title) {
        if (ConsoleStore.isFirmware(archive)) {
            bios++;
            return;
        }
        String label = boundedText(title, archive, MAX_LABEL);
        if (listed.add(archive)) {
            added++;
            lastTitle = label;
        } else {
            existing++;
            attention.add(boundedText(sourceName, "Selected file", MAX_LABEL) + "\nAlready listed as " + label + ". No second entry was created.");
        }
    }

    void notAdded(String sourceName, String reason) {
        rejected++;
        attention.add(boundedText(sourceName, "Selected file", MAX_LABEL) + "\nNot added: "
                + boundedText(reason, "Couldn't import this file.", MAX_REASON));
    }

    boolean hasGames() { return added > 0 || existing > 0; }
    boolean hasDetails() { return !attention.isEmpty(); }

    String summary() {
        List<String> parts = new ArrayList<>(4);
        if (added == 1) parts.add("Added: " + lastTitle);
        else if (added > 1) parts.add(added + " games added");
        if (existing > 0) parts.add(existing + " already listed");
        if (bios > 0) parts.add(bios == 1 ? "BIOS added" : bios + " BIOS files added");
        if (rejected > 0) parts.add(rejected + " not added");
        return parts.isEmpty() ? "No files added." : String.join(" · ", parts);
    }

    String details() { return summary() + "\n\n" + String.join("\n\n", attention); }

    private static String boundedText(String raw, String fallback, int max) {
        String text = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]", " ").trim();
        if (text.codePointCount(0, text.length()) > max) text = text.substring(0, text.offsetByCodePoints(0, max - 1)) + "…";
        return text.isEmpty() ? fallback : text;
    }
}
