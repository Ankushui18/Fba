package io.github.ankushui18.fbaoffline;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** The actual app protocol over two TCP streams, optionally driving two real FBNeo processes. */
public final class LinkWireTest {
    private static final String CODE = "A3B2D5F7K456";
    private static final int TURNS = 252;

    private static void check(boolean ok) { if (!ok) throw new AssertionError("Local link invariant failed"); }

    private static String console(boolean host, Socket sock, String[] args, String scenario) throws Exception {
        Process core = null;
        try (Socket connection = sock) {
            connection.setSoTimeout(6000);
            connection.setTcpNoDelay(true);
            byte[] game = new byte[32];
            if (!host && scenario.equals("zip")) game[31] = 4;
            String secret = !host && scenario.equals("code") ? "BBBBBBBBBBBB" : CODE;
            if (args.length == 4) {
                core = new ProcessBuilder(args[0], args[1], host ? args[2] : args[3]).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            }
            LinkWire wire = new LinkWire(connection.getInputStream(), connection.getOutputStream(), host, game, secret.getBytes(StandardCharsets.US_ASCII));
            BufferedReader read = core == null ? null : new BufferedReader(new InputStreamReader(core.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter write = core == null ? null : new BufferedWriter(new OutputStreamWriter(core.getOutputStream(), StandardCharsets.US_ASCII));
            long checkpoint = 0, first = 0;
            int frames = 0;
            for (int turn = 0; turn < TURNS; turn++) {
                if (scenario.equals("sync") && !host && turn == 1) checkpoint += 1;
                int p1 = turn % 3 == 0 ? 9 : 16;
                int p2 = turn % 5 == 0 ? 256 : 0;
                boolean pause = turn >= 22 && turn < 26;
                long next = wire.exchange(host ? p1 : p2, host && pause, checkpoint);
                if (pause) { check(next == -1); continue; }
                check(next == (p1 | ((long) p2 << 16)));
                frames++;
                long digest = next;
                if (core != null) {
                    write.write(Long.toString(next)); write.newLine(); write.flush();
                    String line = read.readLine();
                    if (line == null) throw new AssertionError("Native core unexpectedly exited");
                    digest = Long.parseUnsignedLong(line, 16);
                }
                if (frames == 1) first = digest;
                if (frames == 2 && core != null) check(first != digest);
                checkpoint = (frames == 1 || frames % 60 == 0) ? digest : 0;
            }
            check(frames == TURNS - 4);
            if (core != null) {
                write.close();
                check(core.waitFor(4, TimeUnit.SECONDS) && core.exitValue() == 0);
            }
            return scenario.isEmpty() ? "ok" : "unexpected success";
        } catch (IOException mismatch) {
            if (scenario.isEmpty()) throw mismatch;
            return mismatch.getMessage();
        } finally {
            if (core != null && core.isAlive()) core.destroyForcibly();
        }
    }

    private static void pair(ExecutorService peers, String scenario, String expected, String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(6000);
            Future<String> host = peers.submit(() -> console(true, server.accept(), args, scenario));
            Future<String> guest = peers.submit(() -> console(false, new Socket(server.getInetAddress(), server.getLocalPort()), args, scenario));
            String a = host.get(15, TimeUnit.SECONDS), b = guest.get(15, TimeUnit.SECONDS);
            check(a != null && a.contains(expected) && b != null && b.contains(expected));
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService peers = Executors.newFixedThreadPool(2);
        try {
            pair(peers, "", "ok", args);
            pair(peers, "zip", "identical", new String[0]);
            pair(peers, "code", "room codes", new String[0]);
            pair(peers, "sync", "out of sync", new String[0]);
        } finally { peers.shutdownNow(); }
        System.out.println("Link: authenticated P1/P2 input, pause/resume, wrong ZIP/code rejection and desync detection passed"
                + (args.length == 4 ? " with two separate live FBNeo processes." : "."));
    }
}
