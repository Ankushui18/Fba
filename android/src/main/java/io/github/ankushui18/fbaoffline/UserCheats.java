package io.github.ankushui18.fbaoffline;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Accepts the safe byte-write subset of legacy cheat INIs. Never passes untrusted raw syntax to FBNeo. */
final class UserCheats {
    private static final int FORMAT = 0x43485431, MAX_INI = 512 * 1024, MAX_ZIP = 16 * 1024 * 1024;
    private static final int MAX_GROUPS = 160, MAX_CHOICES = 96, MAX_OPTIONS = 400, MAX_OUTPUT = 256 * 1024;
    private static final Pattern GROUP = Pattern.compile("cheat\\s+\"([^\"]+)\"(?:\\s+advanced)?\\s*\\{?", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPTION = Pattern.compile("([0-9]{1,3})\\s+\"([^\"]+)\"\\s*(,.*)?");
    private static final Pattern TYPE = Pattern.compile("type\\s+([012])", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFAULT = Pattern.compile("default\\s+[0-9]{1,3}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("[0-9]{1,10}|0[xX][a-fA-F0-9]{1,8}");
    private final File dir;

    UserCheats(File dir) { this.dir = dir; }

    private File file(File rom) { return new File(dir, rom.getName() + ".cht"); }
    boolean exists(File rom) { return file(rom).isFile(); }
    void remove(File rom) throws IOException { Files.deleteIfExists(file(rom).toPath()); }

    static final class Rules {
        final byte[] data;
        final int groups, skipped;
        Rules(byte[] data, int groups, int skipped) { this.data = data; this.groups = groups; this.skipped = skipped; }
    }

    Rules install(File rom, String name, InputStream source, File cache) throws IOException {
        String extension = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (extension.endsWith(".zip")) {
            Path temp = Files.createTempFile(cache.toPath(), "cheatpack-", ".zip");
            try {
                Files.write(temp, LocalFiles.read(source, MAX_ZIP));
                return install(rom, fromZip(rom, temp.toFile()));
            } finally { Files.deleteIfExists(temp); }
        }
        if (!extension.endsWith(".ini")) throw new IOException("Choose a legacy .ini or cheat ZIP.");
        return install(rom, LocalFiles.read(source, MAX_INI));
    }

    private static byte[] fromZip(File rom, File archive) throws IOException {
        String entryName = rom.getName().substring(0, rom.getName().length() - 4) + ".ini";
        try (ZipFile zip = new ZipFile(archive)) {
            if (zip.size() > 8192) throw new IOException("The cheat ZIP has too many entries.");
            List<ZipEntry> entries = new ArrayList<>(2);
            zip.stream().filter(e -> e.getName().equalsIgnoreCase(entryName) && !e.isDirectory()).forEach(entries::add);
            if (entries.size() != 1) throw new IOException("The ZIP must contain one " + entryName + " at its top level.");
            try (InputStream in = zip.getInputStream(entries.get(0))) { return LocalFiles.read(in, MAX_INI); }
        }
    }

    Rules install(File rom, byte[] data) throws IOException {
        Rules rules = parse(data);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(rules.data.length + 40);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT);
            out.write(LocalFiles.hash(rom, RomStore.MAX_BYTES));
            out.write(rules.data);
        }
        LocalFiles.atomicWrite(file(rom), bytes.toByteArray());
        return rules;
    }

    byte[] forRom(File rom) throws IOException {
        File entry = file(rom);
        if (!entry.isFile()) return null;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(entry.toPath()))) {
            if (in.readInt() != FORMAT) throw new IOException("Invalid custom cheat data.");
            byte[] hash = new byte[32];
            in.readFully(hash);
            if (!MessageDigest.isEqual(hash, LocalFiles.hash(rom, RomStore.MAX_BYTES))) return null;
            // Parse again rather than treating private files as instructions without validation.
            return parse(LocalFiles.read(in, MAX_OUTPUT)).data;
        }
    }

    static Rules parse(byte[] data) throws IOException {
        if (data.length > MAX_INI) throw new IOException("The cheat INI is too large.");
        String text;
        if (data.length >= 2 && ((data[0] == (byte) 0xff && data[1] == (byte) 0xfe)
                || (data[0] == (byte) 0xfe && data[1] == (byte) 0xff))) text = new String(data, StandardCharsets.UTF_16);
        else {
            try { text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString(); }
            catch (CharacterCodingException old) { text = new String(data, Charset.forName("GB18030")); }
        }
        if (text.startsWith("\ufeff")) text = text.substring(1);
        StringBuilder clean = new StringBuilder();
        Group group = null;
        Set<String> titles = new HashSet<>();
        int groups = 0, skipped = 0, choices = 0;
        for (String raw : (text + "\ncheat \"End of file\"").split("\\r?\\n|\\r")) {
            LocalFiles.interrupted();
            // Only comments outside quoted labels are recognized.
            boolean quoted = false;
            int end = raw.length();
            for (int i = 0; i < raw.length(); i++) {
                if (raw.charAt(i) == '"') quoted = !quoted;
                if (!quoted && raw.charAt(i) == '/' && i + 1 < raw.length() && raw.charAt(i + 1) == '/') { end = i; break; }
            }
            String line = raw.substring(0, end).trim();
            if (line.isEmpty() || line.equals("{") || line.equals("}")) continue;
            Matcher heading = GROUP.matcher(line);
            if (heading.matches()) {
                if (group != null) {
                    if (group.valid && !group.title.isEmpty() && group.options.size() >= 2
                            && groups < MAX_GROUPS && group.options.size() + choices <= MAX_OPTIONS && titles.add(group.title)) {
                        String block = group.serialize();
                        if ((clean.length() + block.length()) <= MAX_OUTPUT) {
                            clean.append(block);
                            choices += group.options.size();
                            groups++;
                        } else skipped++;
                    } else skipped++;
                }
                group = new Group(label(heading.group(1)));
            } else if (group != null) {
                Matcher type = TYPE.matcher(line), option = OPTION.matcher(line);
                if (type.matches()) group.type = type.group(1);
                else if (!DEFAULT.matcher(line).matches()) {
                    if (!option.matches() || !group.add(option)) group.valid = false;
                }
            }
        }
        byte[] result = clean.toString().getBytes(StandardCharsets.UTF_8);
        if (groups == 0) throw new IOException("No supported cheats in this file. Use disabled-by-default FBA INI choices with CPU, address and byte values.");
        if (result.length > MAX_OUTPUT) throw new IOException("The validated cheat list is too large.");
        return new Rules(result, groups, skipped);
    }

    private static String label(String value) {
        StringBuilder clean = new StringBuilder();
        int length = 0;
        for (int at = 0; at < value.length();) {
            int c = value.codePointAt(at);
            at += Character.charCount(c);
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) continue;
            length += c <= 0x7f ? 1 : c <= 0x7ff ? 2 : c <= 0xffff ? 3 : 4;
            if (length > 110) break;
            clean.appendCodePoint(c);
        }
        return clean.toString().trim();
    }

    private static long number(String token) {
        String trimmed = token.trim();
        if (!NUMBER.matcher(trimmed).matches()) throw new NumberFormatException();
        return trimmed.startsWith("0x") || trimmed.startsWith("0X") ? Long.parseLong(trimmed.substring(2), 16) : Long.parseLong(trimmed);
    }

    private static final class Group {
        final String title;
        final List<String> options = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        String type = "0";
        boolean valid = true;
        Group(String title) { this.title = title; }
        boolean add(Matcher option) {
            if (!valid) return false;
            int index = Integer.parseInt(option.group(1));
            String name = label(option.group(2)), raw = option.group(3);
            if (index >= MAX_CHOICES || index != options.size() || name.isEmpty() || !names.add(name) || ((index == 0) != (raw == null))) return false;
            StringBuilder line = new StringBuilder().append(index).append(" \"").append(name).append('"');
            if (raw != null) {
                String[] args = raw.substring(1).split(",", -1);
                if (args.length % 3 != 0 || args.length > 192) return false;
                try {
                    for (int i = 0; i < args.length; i += 3) {
                        long cpu = number(args[i]), address = number(args[i + 1]), value = number(args[i + 2]);
                        if (cpu > 7 || address <= 0 || address > 0x7fffffffL || value > 255) return false;
                        line.append(", ").append(cpu).append(", 0x").append(Long.toHexString(address)).append(", 0x").append(Long.toHexString(value));
                    }
                } catch (NumberFormatException invalid) { return false; }
            }
            options.add(line.toString());
            return true;
        }
        String serialize() { return "cheat \"" + title + "\"\ntype " + type + "\ndefault 0\n" + String.join("\n", options) + "\n\n"; }
    }
}
