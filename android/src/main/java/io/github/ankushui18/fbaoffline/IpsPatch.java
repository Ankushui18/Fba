package io.github.ankushui18.fbaoffline;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/** Classic IPS literal and RLE records, limited to in-place changes to a known ROM chip. */
final class IpsPatch {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_RECORDS = 16384, MAX_WRITES = 24 * 1024 * 1024;

    private IpsPatch() {}

    static void validate(byte[] patch, int chipSize) throws IOException { visit(patch, chipSize, null); }

    static void apply(byte[] patch, RandomAccessFile chip) throws IOException {
        if (chip.length() > Integer.MAX_VALUE) throw new IOException("ROM chip is too large.");
        visit(patch, (int) chip.length(), chip);
    }

    private static void visit(byte[] patch, int chipSize, RandomAccessFile output) throws IOException {
        if (patch.length < 8 || patch.length > MAX_BYTES || patch[0] != 'P' || patch[1] != 'A'
                || patch[2] != 'T' || patch[3] != 'C' || patch[4] != 'H') throw new IOException("Choose a standard IPS file (PATCH header).");
        int at = 5, records = 0, bytes = 0;
        byte[] fill = output == null ? null : new byte[4096];
        while (at + 3 <= patch.length) {
            LocalFiles.interrupted();
            int offset = u24(patch, at);
            at += 3;
            if (offset == 0x454f46) { // EOF
                if (records == 0) throw new IOException("This IPS file contains no changes.");
                if (at == patch.length || (at + 3 == patch.length && u24(patch, at) == chipSize)) return;
                throw new IOException("This IPS needs to resize the chip. Only same-size patches are supported.");
            }
            if (++records > MAX_RECORDS || at + 2 > patch.length) break;
            int literal = u16(patch, at), length = literal;
            at += 2;
            int repeated = 0;
            if (literal == 0) {
                if (at + 3 > patch.length) break;
                length = u16(patch, at);
                repeated = patch[at + 2] & 255;
                at += 3;
            }
            if (length == 0 || at + literal > patch.length || (bytes += length) > MAX_WRITES) break;
            if (length > chipSize - offset) throw new IOException("A patch record is outside the selected chip. Check the ROM revision and chip name.");
            if (output != null) {
                output.seek(offset);
                if (literal > 0) output.write(patch, at, literal);
                else {
                    Arrays.fill(fill, (byte) repeated);
                    for (int left = length; left > 0; left -= fill.length) output.write(fill, 0, Math.min(fill.length, left));
                }
            }
            at += literal;
        }
        throw new IOException("This IPS file is truncated or exceeds the patch limits.");
    }

    private static int u16(byte[] b, int i) { return ((b[i] & 255) << 8) | (b[i + 1] & 255); }
    private static int u24(byte[] b, int i) { return ((b[i] & 255) << 16) | u16(b, i + 1); }
}
