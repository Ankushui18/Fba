package io.github.ankushui18.fbaoffline;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.regex.Pattern;

/** Per-set plain text only. Raw HTML is never stored or loaded by an Android browser. */
final class RomNotes {
    static final int MAX_SOURCE_BYTES = 64 * 1024;
    private static final int MAX_TEXT = 16_000;
    // Strip non-note content before sending markup to Android's text-only converter.
    private static final Pattern HTML_CHROME = Pattern.compile("(?is)<!--.*?(?:-->|\\z)|<(script|style|head)\\b[^<>]*+>.*?(?:</\\1\\s*>|\\z)|<img\\b[^<>]*+>");
    private final File directory;

    RomNotes(File directory) { this.directory = directory; }

    private File file(File rom) throws IOException {
        String name = RomStore.archiveName(rom.getName());
        return new File(directory, name.substring(0, name.length() - 4) + ".txt");
    }

    String read(File rom) throws IOException {
        File file = file(rom);
        if (!file.isFile()) return "";
        try (InputStream in = Files.newInputStream(file.toPath())) { return clean(decode(LocalFiles.read(in, MAX_SOURCE_BYTES))); }
    }

    void write(File rom, String text) throws IOException {
        String clean = clean(text);
        if (clean.isEmpty()) throw new IOException("Couldn't find any note text in that file.");
        LocalFiles.atomicWrite(file(rom), clean.getBytes(StandardCharsets.UTF_8));
    }

    void remove(File rom) throws IOException { Files.deleteIfExists(file(rom).toPath()); }

    static boolean isHtml(String name) throws IOException {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".htm") || lower.endsWith(".html")) return true;
        if (lower.endsWith(".txt")) return false;
        throw new IOException("Choose a .htm, .html or .txt note for this game.");
    }

    static String decode(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_SOURCE_BYTES) throw new IOException("Choose a non-empty note under 64 KiB.");
        int a = bytes[0] & 255, b = bytes.length > 1 ? bytes[1] & 255 : -1;
        try {
            if ((a == 0xfe && b == 0xff) || (a == 0xff && b == 0xfe)) {
                return decode(bytes, 0, StandardCharsets.UTF_16);
            }
            if (a == 0xef && b == 0xbb && bytes.length > 2 && (bytes[2] & 255) == 0xbf) {
                return decode(bytes, 3, StandardCharsets.UTF_8);
            }
            for (byte n : bytes) if (n == 0) throw new IOException("Choose a text or HTML note, not a binary file.");
            try { return decode(bytes, 0, StandardCharsets.UTF_8); }
            catch (CharacterCodingException legacy) { return decode(bytes, 0, Charset.forName("GB18030")); }
        } catch (CharacterCodingException invalid) {
            throw new IOException("Couldn't read the note's encoding. Save it as UTF-8 text or HTML and try again.", invalid);
        }
    }

    static String htmlBody(String html) { return HTML_CHROME.matcher(html).replaceAll(""); }

    private static String decode(byte[] data, int start, Charset charset) throws CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data, start, data.length - start)).toString();
    }

    private static String clean(String text) throws IOException {
        String clean = text.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[^\\S\\n]+", " ")
                .replaceAll("[\\p{Cc}\\p{Cf}&&[^\\n]]", "").replaceAll("\\n{3,}", "\n\n").trim();
        if (clean.codePointCount(0, clean.length()) > MAX_TEXT) throw new IOException("Keep the note under 16,000 visible characters.");
        return clean;
    }
}
