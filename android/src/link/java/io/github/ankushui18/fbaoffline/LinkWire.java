package io.github.ankushui18.fbaoffline;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Fixed-size authenticated local lockstep. This protocol never transfers ROMs, states, or paths. */
final class LinkWire {
    static final int INPUT_MASK = 0xfff;
    private static final int PROTOCOL = 0x46424c31; // FBL1
    private static final int NONCE = 16, DIGEST = 32, TAG = 16, BODY = 15, PACKET = BODY + TAG;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final boolean host;
    private final Mac mac;
    private final byte[] send = new byte[PACKET], receive = new byte[PACKET];
    private final ByteBuffer writer = ByteBuffer.wrap(send), reader = ByteBuffer.wrap(receive);
    private int turn;

    LinkWire(InputStream input, OutputStream output, boolean host, byte[] game, byte[] secret) throws IOException {
        if (game.length != DIGEST || secret.length < 8) throw new IllegalArgumentException("Invalid link credentials.");
        this.host = host;
        in = new DataInputStream(input);
        out = new DataOutputStream(output);
        byte[] self = new byte[NONCE], peer = new byte[NONCE], peerGame = new byte[DIGEST], proof = new byte[DIGEST];
        new SecureRandom().nextBytes(self);
        out.writeInt(PROTOCOL);
        out.writeBoolean(host);
        out.write(game);
        out.write(self);
        out.flush();
        if (in.readInt() != PROTOCOL || in.readUnsignedByte() != (host ? 0 : 1)) throw new IOException("Choose Host on one phone and Join on the other, using the same Link APK.");
        in.readFully(peerGame);
        in.readFully(peer);
        if (!MessageDigest.isEqual(game, peerGame)) throw new IOException("Both phones need the same Link build, CPU architecture and identical game, parent and BIOS ZIPs.");
        try {
            Mac negotiate = Mac.getInstance("HmacSHA256");
            negotiate.init(new SecretKeySpec(secret, "HmacSHA256"));
            negotiate.update((byte) 0x46);
            negotiate.update(host ? self : peer);
            negotiate.update(host ? peer : self);
            byte[] session = negotiate.doFinal(game);
            mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(session, "HmacSHA256"));
            Arrays.fill(session, (byte) 0);
            out.write(mac.doFinal(new byte[]{0x53, (byte) (host ? 1 : 0)}));
            out.flush();
            in.readFully(proof);
            if (!MessageDigest.isEqual(proof, mac.doFinal(new byte[]{0x53, (byte) (host ? 0 : 1)}))) {
                throw new IOException("The two phones have different room codes.");
            }
        } catch (GeneralSecurityException unavailable) { throw new IOException("Couldn't authenticate this local link.", unavailable); }
    }

    // A pause is a link tick but not a game frame. Both sides agree before running each frame.
    long exchange(int local, boolean paused, long checkpoint) throws IOException {
        if (turn < 0) throw new IOException("This session is too long. Start a new link.");
        writer.clear();
        writer.putInt(turn).putShort((short) (local & INPUT_MASK)).put((byte) (paused ? 1 : 0)).putLong(checkpoint);
        System.arraycopy(tag(send, host), 0, send, BODY, TAG);
        out.write(send);
        out.flush();
        in.readFully(receive);
        byte[] signature = tag(receive, !host);
        int different = 0;
        for (int i = 0; i < TAG; i++) different |= signature[i] ^ receive[BODY + i];
        if (different != 0) throw new IOException("Local link lost packet authentication.");
        reader.clear();
        int seq = reader.getInt(), remote = reader.getShort() & 0xffff, pause = reader.get() & 255;
        long remoteCheck = reader.getLong();
        if (seq != turn || pause > 1 || (remote & ~INPUT_MASK) != 0) throw new IOException("Local link received an out-of-order frame.");
        if (remoteCheck != checkpoint) throw new IOException("The emulators went out of sync. This set may not support local link.");
        turn++;
        if (paused || pause == 1) return -1;
        return host ? (local & INPUT_MASK) | ((long) remote << 16) : remote | ((long) (local & INPUT_MASK) << 16);
    }

    private byte[] tag(byte[] packet, boolean fromHost) {
        mac.update((byte) (fromHost ? 0x31 : 0x32));
        mac.update(packet, 0, BODY);
        return mac.doFinal();
    }
}
