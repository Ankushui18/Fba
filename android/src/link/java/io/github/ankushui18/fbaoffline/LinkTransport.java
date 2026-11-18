package io.github.ankushui18.fbaoffline;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Two-player link on an explicitly selected private subnet or an already-paired Bluetooth device. */
final class LinkTransport implements GameLink {
    static final int DEFAULT_PORT = 42688;
    private static final UUID SERVICE = UUID.fromString("0741e8e6-50c3-4490-8b33-c54f414241f1");
    private static final long FRAME_TIMEOUT = TimeUnit.SECONDS.toNanos(8);
    private static final ScheduledThreadPoolExecutor WATCH = new ScheduledThreadPoolExecutor(1, job -> {
        Thread thread = new Thread(job, "local-link-watch"); thread.setDaemon(true); return thread;
    });
    static { WATCH.setRemoveOnCancelPolicy(true); }

    final boolean host;
    private final GameCatalog catalog;
    private final Lane lan;
    private final Inet4Address address;
    private final int port;
    private final BluetoothAdapter bluetooth;
    private final BluetoothDevice peer;
    private final String code;
    private final String status;
    private volatile boolean stopped, timeout;
    private volatile long deadline;
    private Closeable socket, listener;
    private ScheduledFuture<?> timer;
    private LinkWire wire;

    static final class Lane {
        final Inet4Address ip;
        final int prefix;
        Lane(Inet4Address ip, int prefix) { this.ip = ip; this.prefix = prefix; }
        boolean contains(InetAddress candidate) {
            if (!(candidate instanceof Inet4Address) || !candidate.isSiteLocalAddress() || prefix < 1 || prefix > 32) return false;
            byte[] a = ip.getAddress(), b = candidate.getAddress();
            for (int i = 0; i < prefix; i++) if (((a[i / 8] ^ b[i / 8]) & (128 >>> (i % 8))) != 0) return false;
            return true;
        }
        String text() { return ip.getHostAddress(); }
    }

    static List<Lane> lanes() throws IOException {
        List<Lane> result = new ArrayList<>();
        java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return result;
        for (NetworkInterface iface : Collections.list(interfaces)) {
            if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) continue;
            for (InterfaceAddress ia : iface.getInterfaceAddresses()) {
                InetAddress ip = ia.getAddress();
                if (ip instanceof Inet4Address && ip.isSiteLocalAddress() && ia.getNetworkPrefixLength() > 0) {
                    result.add(new Lane((Inet4Address) ip, ia.getNetworkPrefixLength()));
                }
            }
        }
        return result;
    }

    static Inet4Address ipv4(String text) throws IOException {
        String[] parts = text.trim().split("\\.", -1);
        if (parts.length != 4) throw new IOException("Enter the host's numeric IPv4 address.");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("[0-9]{1,3}") || Integer.parseInt(parts[i]) > 255) throw new IOException("Enter the host's numeric IPv4 address.");
            bytes[i] = (byte) Integer.parseInt(parts[i]);
        }
        Inet4Address ip = (Inet4Address) InetAddress.getByAddress(bytes);
        if (!ip.isSiteLocalAddress()) throw new IOException("Only private local-network addresses are supported.");
        return ip;
    }

    static LinkTransport wifi(GameCatalog catalog, boolean host, Lane local, Inet4Address remote, int port, String code) {
        return new LinkTransport(catalog, host, local, remote, port, null, null, code,
                host ? "Hosting at " + local.text() + ":" + port : "Joining " + remote.getHostAddress() + ":" + port);
    }

    static LinkTransport bluetooth(GameCatalog catalog, boolean host, BluetoothAdapter adapter, BluetoothDevice peer, String code) {
        return new LinkTransport(catalog, host, null, null, 0, adapter, peer, code, host ? "Hosting on Bluetooth" : "Joining over Bluetooth");
    }

    private LinkTransport(GameCatalog catalog, boolean host, Lane lan, Inet4Address address, int port,
                          BluetoothAdapter bluetooth, BluetoothDevice peer, String code, String status) {
        this.catalog = catalog; this.host = host; this.lan = lan; this.address = address; this.port = port;
        this.bluetooth = bluetooth; this.peer = peer; this.code = code;
        this.status = status + (host ? "\nRoom code: " + showCode(code) + "\nWaiting for Player 2…" : "\nConnecting to Player 1…");
    }

    static String showCode(String code) { return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8); }
    @Override public int player() { return host ? 0 : 1; }
    @Override public String status() { return status; }

    @Override public void connect(File rom) throws IOException {
        if (stopped) throw new IOException("Local link cancelled.");
        try {
            byte[] game = identity(catalog, rom);
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
            synchronized (this) {
                if (stopped) throw new IOException("Local link cancelled.");
                timer = WATCH.scheduleWithFixedDelay(() -> {
                    if (System.nanoTime() - deadline >= 0) { timeout = true; stop(); }
                }, 250, 250, TimeUnit.MILLISECONDS);
            }
            if (lan == null) {
                BluetoothSocket connection;
                if (host) {
                    BluetoothServerSocket server = bluetooth.listenUsingRfcommWithServiceRecord("FBA Arcade Link", SERVICE);
                    attach(server, true);
                    connection = server.accept(35000);
                    attach(connection, false);
                    firstConnected();
                    if (connection.getRemoteDevice().getBondState() != BluetoothDevice.BOND_BONDED) throw new IOException("Pair both phones in Android Bluetooth settings first.");
                } else {
                    connection = peer.createRfcommSocketToServiceRecord(SERVICE);
                    attach(connection, false);
                    connection.connect();
                }
                negotiate(connection.getInputStream(), connection.getOutputStream(), game);
            } else {
                Socket connection;
                if (host) {
                    ServerSocket server = new ServerSocket();
                    attach(server, true);
                    server.setSoTimeout(35000);
                    server.bind(new InetSocketAddress(lan.ip, port), 1);
                    connection = server.accept();
                    attach(connection, false);
                    firstConnected();
                    if (!lan.contains(connection.getInetAddress())) throw new IOException("The peer must be on the same private subnet.");
                } else {
                    connection = new Socket();
                    attach(connection, false);
                    connection.bind(new InetSocketAddress(lan.ip, 0));
                    connection.connect(new InetSocketAddress(address, port), 6000);
                }
                connection.setTcpNoDelay(true);
                connection.setSoTimeout(8000);
                negotiate(connection.getInputStream(), connection.getOutputStream(), game);
            }
        } catch (SecurityException missing) {
            throw new IOException("Allow the requested Local Network or Nearby Devices permission, then retry.", missing);
        } catch (SocketTimeoutException unavailable) {
            throw new IOException("No reply from the other phone. Check the address, network and room code.", unavailable);
        } catch (IOException unavailable) {
            if (timeout) throw new IOException("Couldn't reach the other phone before the link timed out.", unavailable);
            throw unavailable;
        }
    }

    private void negotiate(InputStream input, OutputStream output, byte[] game) throws IOException {
        deadline = System.nanoTime() + FRAME_TIMEOUT;
        wire = new LinkWire(input, output, host, game, code.getBytes(StandardCharsets.US_ASCII));
    }

    @Override public long exchange(int input, boolean pause, long checkpoint) throws IOException {
        if (stopped) throw new IOException("Local link ended.");
        deadline = System.nanoTime() + FRAME_TIMEOUT;
        try { return wire.exchange(input, pause, checkpoint); }
        catch (java.io.EOFException | java.net.SocketException | SocketTimeoutException closed) {
            throw new IOException("The other phone disconnected or stopped replying.", closed);
        }
    }

    private synchronized void attach(Closeable resource, boolean server) throws IOException {
        if (stopped) {
            resource.close();
            throw new IOException("Local link cancelled.");
        }
        if (server) listener = resource; else socket = resource;
    }

    private synchronized void firstConnected() { close(listener); listener = null; }

    @Override public synchronized void stop() {
        stopped = true;
        if (timer != null) { timer.cancel(false); timer = null; }
        close(listener); close(socket);
        listener = socket = null;
        // Do not interrupt the reusable emulation worker. Socket closure cancels I/O.
    }

    private static void close(Closeable resource) {
        if (resource != null) try { resource.close(); } catch (IOException ignored) {}
    }

    private byte[] identity(GameCatalog catalog, File rom) throws IOException {
        MessageDigest hash = LocalFiles.sha256();
        hash.update(("FBA Arcade Link 0.4.0 / deterministic FBNeo 2da48555 / " + System.getProperty("os.arch") + "\n").getBytes(StandardCharsets.US_ASCII));
        ArrayDeque<File> next = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        next.add(rom);
        while (!next.isEmpty()) {
            if (stopped) throw new IOException("Local link cancelled.");
            File file = next.removeFirst();
            if (!seen.add(file.getName())) continue;
            if (seen.size() > 16) throw new IOException("Too many ROM dependencies for local link.");
            hash.update((file.getName() + "\n").getBytes(StandardCharsets.US_ASCII));
            if (RomStore.isArchive(file)) {
                hash.update((byte) 1);
                hash.update(LocalFiles.hash(file, RomStore.MAX_BYTES));
            } else hash.update((byte) 0); // Merged sets can omit a separately installed parent archive.
            GameCatalog.Game game = catalog.game(file);
            for (String dep : new String[]{game.parent, game.dependency}) if (!dep.isEmpty()) {
                next.add(new File(rom.getParentFile(), RomStore.archiveName(dep + ".zip")));
            }
        }
        return hash.digest();
    }
}
