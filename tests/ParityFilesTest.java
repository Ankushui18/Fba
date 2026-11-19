package io.github.ankushui18.fbaoffline;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public final class ParityFilesTest {
    interface Action { void run() throws IOException; }
    private static void reject(Action action) throws IOException {
        try { action.run(); throw new AssertionError("Accepted invalid source data"); }
        catch (IOException expected) { /* expected */ }
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }

    private static void notes(File root) throws IOException {
        RomNotes store = new RomNotes(root);
        File rom = new File("gridlee.zip"), other = new File("1942.zip");
        check(store.read(rom).isEmpty(), "unimported note");
        String old = "<p>攻略 &amp; controls</p>";
        check(RomNotes.decode(old.getBytes(StandardCharsets.UTF_16)).equals(old), "legacy UTF-16 BOM");
        check(RomNotes.decode(old.getBytes(Charset.forName("GBK"))).equals(old), "legacy Chinese GBK");
        check(RomNotes.decode(("\ufeff" + old).getBytes(StandardCharsets.UTF_8)).equals(old), "UTF-8 BOM");
        String html = "<head><title>ignore</title></head><style>.ignored{}</style>"
                + "<!-- comment --><script>ignored()</script><IMG src='https://invalid.test/fake'>" + old;
        check(RomNotes.htmlBody(html).equals(old), "discard scripts, metadata and remote image tags");
        check(RomNotes.htmlBody("note<script>not a note").equals("note"), "discard unterminated script");
        check(RomNotes.isHtml("GRIDLEE.HTM") && !RomNotes.isHtml("gridlee.txt"), "legacy extensions");
        reject(() -> RomNotes.isHtml("gridlee.exe"));
        reject(() -> RomNotes.decode(new byte[]{80, 0, 68, 70}));
        reject(() -> RomNotes.decode(new byte[RomNotes.MAX_SOURCE_BYTES + 1]));
        store.write(rom, " Arcade\r\n\r\n\r\n攻略\t技巧\u202e  ");
        check(store.read(rom).equals("Arcade\n\n攻略 技巧"), "only normalized text is stored");
        check(store.read(other).isEmpty(), "notes stay scoped to named game");
        reject(() -> store.write(rom, " \u202e "));
        char[] tooLong = new char[16_001]; Arrays.fill(tooLong, 'a');
        reject(() -> store.write(rom, new String(tooLong)));
        check(store.read(rom).startsWith("Arcade"), "invalid replacement leaves the current note in place");
        store.remove(rom);
        check(store.read(rom).isEmpty(), "remove private note");
    }

    private static byte[] header(int w, int h) {
        byte[] data = new byte[45];
        ByteBuffer.wrap(data).putLong(0x89504e470d0a1a0aL).putInt(13).putInt(0x49484452).putInt(w).putInt(h);
        return data;
    }

    private static void artwork() throws IOException {
        check(LegacySkinFiles.name("BuTtOn_4_P.PNG").equals("button_4_p.png"), "legacy pressed combo frame");
        check(LegacySkinFiles.name("BUTTON_PLAYER3.png").equals("button_player3.png"), "player frame");
        check(LegacySkinFiles.name("ANALOG_2.png").equals(LegacySkinFiles.KNOB), "round stick knob");
        check(LegacySkinFiles.preview(LegacySkinFiles.name("icon2.png")), "digital preview icon");
        for (String file : new String[]{"../BUTTON_A.png", "a/button_a.png", "BUTTON_PAUSE.png.exe", "autorun.ini", "pad_analog.ini", "button_5.png"}) {
            check(LegacySkinFiles.name(file) == null, "reject unrecognized path/frame: " + file);
        }
        check(LegacySkinFiles.pngSide(header(225, 180)) == 225, "read PNG bounds before decode");
        reject(() -> LegacySkinFiles.pngSide(header(0, 1)));
        reject(() -> LegacySkinFiles.pngSide(header(8192, 8192)));
        reject(() -> LegacySkinFiles.pngSide(header(-1, 256)));
        reject(() -> LegacySkinFiles.pngSide(new byte[45]));
        reject(() -> LegacySkinFiles.pngSide(new byte[LegacySkinFiles.MAX_SOURCE_BYTES + 1]));
    }

    public static void main(String[] args) throws IOException {
        File root = Files.createTempDirectory("parity-files-test").toFile();
        try { notes(root); artwork(); }
        finally { LocalFiles.deleteTree(root); }
        System.out.println("Legacy ROM note decoding/storage and skin filename/PNG bounds passed.");
    }
}
