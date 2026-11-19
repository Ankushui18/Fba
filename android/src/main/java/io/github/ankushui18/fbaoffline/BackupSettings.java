package io.github.ankushui18.fbaoffline;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Portable UI and core preferences only; never copy Android grants or the shared-preferences file. */
final class BackupSettings {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAGIC = 0x46424142, VERSION = 1, MAX_KEYS = 8192;
    private static final Pattern KEYS = Pattern.compile("key_(?:[0-9]|1[0-5])");
    private static final Pattern COMBOS = Pattern.compile("combo_[0-3]|face_combo_[0-5]");
    private static final Pattern BUILT_IN_SKIN = Pattern.compile("CLASSIC(?:_CIRCLE)?|CHROME(?:_CIRCLE)?");
    private static final String NUMBER = "-?[0-9]{1,5}(?:\\.[0-9]{1,5})?";
    private static final Pattern LAYOUT_OPTIONS = Pattern.compile("[0-9]{1,3}(?:,[0-9]{1,3}){2,4}");
    private static final Pattern LAYOUT_CONTROL = Pattern.compile(NUMBER + "(?:," + NUMBER + "){2},[01]");

    private BackupSettings() {}

    static void write(DataOutputStream out, Map<String, ?> raw) throws IOException {
        Map<String, Object> selected = new TreeMap<>();
        for (Map.Entry<String, ?> option : raw.entrySet()) {
            if (valid(option.getKey(), option.getValue())) selected.put(option.getKey(), option.getValue());
            if (selected.size() > MAX_KEYS) throw new IOException("Too many settings for one backup.");
        }
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(selected.size());
        for (Map.Entry<String, Object> option : selected.entrySet()) {
            out.writeUTF(option.getKey());
            Object value = option.getValue();
            if (value instanceof Boolean) out.writeByte((boolean) value ? 1 : 0);
            else if (value instanceof Integer) { out.writeByte(2); out.writeInt((int) value); }
            else { out.writeByte(3); out.writeUTF((String) value); }
            if (out.size() > MAX_BYTES) throw new IOException("Settings exceed the backup size limit.");
        }
    }

    static Map<String, Object> read(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Choose a supported FBA Arcade backup ZIP.");
        int count = in.readInt();
        if (count < 0 || count > MAX_KEYS) throw new IOException("Invalid backup settings count.");
        Map<String, Object> restored = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String name = in.readUTF();
            Object value;
            switch (in.readUnsignedByte()) {
                case 0: value = false; break;
                case 1: value = true; break;
                case 2: value = in.readInt(); break;
                case 3: value = in.readUTF(); break;
                default: throw new IOException("Unknown backup settings type.");
            }
            if (!valid(name, value) || restored.putIfAbsent(name, value) != null) throw new IOException("Invalid or duplicate backup setting.");
        }
        if (in.read() != -1) throw new IOException("Invalid extra backup settings data.");
        return restored;
    }

    private static boolean valid(String name, Object value) {
        if (name == null || name.length() > 128) return false;
        if (name.startsWith(CoreProfile.PREFIX)) {
            String rom = name.substring(CoreProfile.PREFIX.length());
            return validRom(rom) && value instanceof String && CoreProfile.valid((String) value);
        }
        String key = name;
        boolean perGame = name.startsWith("game:");
        if (perGame) {
            int split = name.indexOf(':', 5);
            if (split < 0) return false;
            if (!validRom(name.substring(5, split))) return false;
            key = name.substring(split + 1);
        }
        switch (key) {
            case "tiles": case "chinese_names": return !perGame && value instanceof Boolean;
            case "custom": return perGame && value instanceof Boolean;
            case "smooth": case "muted": case "gpu": case "show_fps": case "touch_visible":
            case "mirror_keys": case "player_button": case "full_screen_pad": case "pad_animation":
            case "pad_haptic": case "physical_keys": return value instanceof Boolean;
            case "sorting": return !perGame && inRange(value, 0, 4);
            case "aspect": case "frame_skip": return inRange(value, 0, 5);
            case "orientation": case "image_filter": return inRange(value, 0, 4);
            case "touch_player": return inRange(value, 0, 3);
            case "pad_mode": return inRange(value, 0, 2);
            case "turbo_rate": return inRange(value, 2, 30);
            case "skin": return value instanceof String && BUILT_IN_SKIN.matcher((String) value).matches();
            case "pad_portrait": case "pad_landscape": return value instanceof String && validLayout((String) value);
            default:
                if (KEYS.matcher(key).matches()) return inRange(value, -1, 2048);
                return (key.equals("turbo") || COMBOS.matcher(key).matches())
                        && value instanceof Integer && ((int) value & ~PadBindings.FACE_MASK) == 0;
        }
    }

    private static boolean validRom(String name) {
        try { return name.equals(RomStore.archiveName(name)); }
        catch (IOException invalid) { return false; }
    }

    private static boolean validLayout(String value) {
        if (value.length() > 2048) return false;
        String[] rows = value.split(";", -1);
        if (rows.length != PadLayout.COUNT + 1 || !LAYOUT_OPTIONS.matcher(rows[0]).matches()) return false;
        for (int i = 1; i < rows.length; i++) if (!LAYOUT_CONTROL.matcher(rows[i]).matches()) return false;
        return true;
    }

    private static boolean inRange(Object value, int min, int max) {
        return value instanceof Integer && (int) value >= min && (int) value <= max;
    }
}
