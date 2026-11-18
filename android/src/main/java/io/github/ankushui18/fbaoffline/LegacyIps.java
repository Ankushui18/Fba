package io.github.ankushui18.fbaoffline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded subset of the 1.77 .dat map: chip name, IPS stem, then optional descriptive sections. */
final class LegacyIps {
    static final int MAX_DAT = 32 * 1024;
    private static final String NAME = "[a-zA-Z0-9_][a-zA-Z0-9_.()+-]{0,139}";
    private static final Pattern MAPPING = Pattern.compile("(" + NAME + ")[ \\t]+((?:" + NAME + "/)?" + NAME + ")"
            + "(?:[ \\t]+CRC\\(([a-f0-9]{8})\\))?(?:[ \\t]+(?:#|//).*)?", Pattern.CASE_INSENSITIVE);

    interface OpenPatch { InputStream open(String name) throws IOException; }

    private LegacyIps() {}

    static List<PatchStore.Part> load(String set, byte[] definition, List<PatchStore.Chip> matched, OpenPatch open) throws IOException {
        if (definition.length == 0 || definition.length > MAX_DAT) throw new IOException("Choose a non-empty IPS definition under 32 KiB.");
        String text = new String(definition, StandardCharsets.UTF_8);
        if (text.charAt(0) == '\ufeff') text = text.substring(1);
        if (text.indexOf('\0') >= 0) throw new IOException("Choose a plain-text IPS .dat definition.");
        Map<String, PatchStore.Chip> chips = new HashMap<>();
        for (PatchStore.Chip chip : matched) chips.putIfAbsent(chip.key(), chip);
        List<PatchStore.Part> parts = new ArrayList<>();
        int remaining = PatchStore.MAX_PATCH_BYTES, number = 0;
        for (String raw : text.split("\\r\\n|\\n|\\r", -1)) {
            number++;
            LocalFiles.interrupted();
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
            if (line.startsWith("[") && line.endsWith("]")) break; // Localized descriptions are not instructions.
            if (parts.size() == PatchStore.MAX_PARTS) throw new IOException("This pack has more than 8 IPS files.");
            Matcher row = MAPPING.matcher(line);
            if (line.length() > 400 || !row.matches()) throw new IOException("Unsupported IPS mapping at .dat line " + number + ". Use a simple same-folder chip / IPS map.");
            PatchStore.Chip chip = chips.get(row.group(1).toLowerCase(Locale.ROOT));
            if (chip == null || (row.group(3) != null && chip.crc != Long.parseLong(row.group(3), 16))) {
                throw new IOException("Chip " + row.group(1) + " is not a matching FBNeo chip in this ZIP.");
            }
            String name = row.group(2).toLowerCase(Locale.ROOT);
            int slash = name.indexOf('/');
            if (slash >= 0) {
                if (!name.substring(0, slash).equals(set)) throw new IOException("This pack refers to a different ROM folder.");
                name = name.substring(slash + 1);
            }
            if (!name.endsWith(".ips")) name += ".ips";
            try (InputStream in = open.open(name)) {
                byte[] patch = LocalFiles.read(in, Math.min(IpsPatch.MAX_BYTES, remaining));
                IpsPatch.validate(patch, chip.size);
                parts.add(new PatchStore.Part(chip, patch));
                remaining -= patch.length;
            }
        }
        if (parts.isEmpty()) throw new IOException("No chip-to-IPS mappings found before the descriptions in this .dat.");
        return parts;
    }
}
