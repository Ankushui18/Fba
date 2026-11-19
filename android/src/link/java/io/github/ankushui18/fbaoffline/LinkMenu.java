package io.github.ankushui18.fbaoffline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static io.github.ankushui18.fbaoffline.ArcadeUi.MUTED;

/** Explicit opt-in menus. No scanning, service discovery, relay, or background connection. */
final class LinkMenu {
    static final boolean AVAILABLE = true;
    private static final int LINK_PERMISSION = 114;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Activity activity;
    private final GameCatalog catalog;
    private final ArcadeUi ui;
    private Runnable permissionGranted;

    LinkMenu(Activity activity, GameCatalog catalog) {
        this.activity = activity;
        this.catalog = catalog;
        ui = new ArcadeUi(activity);
    }

    void show(File rom, Consumer<GameLink> launch) {
        new AlertDialog.Builder(activity).setTitle("Local multiplayer · beta")
                .setMessage("Start a fresh two-player game. Both phones need this Link APK and identical " + rom.getName()
                        + " and required parent/BIOS ZIPs. Pair the phones first for Bluetooth, or connect both to the same Wi-Fi. "
                        + "Your ROMs and saves are never sent. Use only with someone nearby you trust.")
                .setPositiveButton("Host · P1", (d, w) -> method(true, launch))
                .setNeutralButton("Join · P2", (d, w) -> method(false, launch))
                .setNegativeButton("Cancel", null).show();
    }

    private void method(boolean host, Consumer<GameLink> launch) {
        new AlertDialog.Builder(activity).setTitle(host ? "Host game" : "Join game")
                .setItems(new String[]{"Wi-Fi / local IPv4", "Bluetooth · paired devices"}, (d, i) -> {
                    if (i == 0) allow(Build.VERSION.SDK_INT >= 37 ? Manifest.permission.ACCESS_LOCAL_NETWORK : null, () -> local(host, launch));
                    else allow(Build.VERSION.SDK_INT >= 31 ? Manifest.permission.BLUETOOTH_CONNECT : null, () -> bluetooth(host, launch));
                }).setNegativeButton("Cancel", null).show();
    }

    private void allow(String permission, Runnable granted) {
        if (permission == null || activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) granted.run();
        else {
            permissionGranted = granted;
            activity.requestPermissions(new String[]{permission}, LINK_PERMISSION);
        }
    }

    void result(int request, String[] permissions, int[] grants) {
        if (request != LINK_PERMISSION) return;
        Runnable next = permissionGranted;
        permissionGranted = null;
        if (next != null && grants.length == 1 && grants[0] == PackageManager.PERMISSION_GRANTED) next.run();
        else fail("Permission denied. Offline games still work. You can allow the permission in Android Settings to use this link method.");
    }

    private void local(boolean host, Consumer<GameLink> launch) {
        List<LinkTransport.Lane> lanes;
        try { lanes = LinkTransport.lanes(); }
        catch (IOException | SecurityException unavailable) { fail("Couldn't read a private IPv4 address. Connect to Wi-Fi and check Local Network permission."); return; }
        if (lanes.isEmpty()) { fail("No private IPv4 connection. Connect both phones to the same Wi-Fi or hotspot."); return; }
        if (!host || lanes.size() == 1) { lanForm(host, lanes, lanes.get(0), launch); return; }
        String[] addresses = lanes.stream().map(LinkTransport.Lane::text).toArray(String[]::new);
        new AlertDialog.Builder(activity).setTitle("Choose your local Wi-Fi address")
                .setItems(addresses, (d, i) -> lanForm(true, lanes, lanes.get(i), launch)).setNegativeButton("Cancel", null).show();
    }

    private void lanForm(boolean host, List<LinkTransport.Lane> lanes, LinkTransport.Lane own, Consumer<GameLink> launch) {
        String code = host ? code() : "";
        LinearLayout form = form();
        EditText port = field(form, "Port", 5, InputType.TYPE_CLASS_NUMBER);
        port.setText(Integer.toString(LinkTransport.DEFAULT_PORT));
        EditText ip = host ? null : field(form, "Host IP address", 15, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText room = host ? null : field(form, "Room code", 16, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        form.addView(ui.text(host ? "Your address: " + own.text() + "\nRoom code: " + LinkTransport.showCode(code)
                + "\nShare the address, port and code with Player 2."
                : "Use the address, port and code shown on the host phone. Public addresses and DNS names are not supported.", 14, MUTED, false));
        AlertDialog dialog = dialog(host ? "Host on Wi-Fi / LAN" : "Join on Wi-Fi / LAN", form, host ? "Start hosting" : "Connect");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int number = Integer.parseInt(port.getText().toString());
                if (number < 1024 || number > 65535) throw new NumberFormatException();
                if (host) {
                    dialog.dismiss(); launch.accept(LinkTransport.wifi(catalog, true, own, null, number, code));
                } else {
                    Inet4Address remote = LinkTransport.ipv4(ip.getText().toString());
                    LinkTransport.Lane local = lanes.stream().filter(lane -> lane.contains(remote) && !lane.ip.equals(remote))
                            .findFirst().orElseThrow(() -> new IOException("Join from another phone on the same private subnet."));
                    String secret = roomCode(room);
                    dialog.dismiss(); launch.accept(LinkTransport.wifi(catalog, false, local, remote, number, secret));
                }
            } catch (NumberFormatException invalid) { port.setError("Enter a port from 1024 to 65535."); }
            catch (IOException invalid) { fail(invalid.getMessage()); }
        });
    }

    private void bluetooth(boolean host, Consumer<GameLink> launch) {
        BluetoothManager manager = activity.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        try {
            if (adapter == null || !adapter.isEnabled()) { fail("Turn on Bluetooth in Android Settings and pair the two phones first."); return; }
            List<BluetoothDevice> paired = new ArrayList<>(adapter.getBondedDevices());
            if (paired.isEmpty()) { fail("No paired Bluetooth devices. Pair the phones in Android Settings, then try again."); return; }
            if (host) {
                String code = code();
                new AlertDialog.Builder(activity).setTitle("Host on Bluetooth")
                        .setMessage("Tell Player 2 to select this phone from paired devices and enter room code " + LinkTransport.showCode(code) + ".")
                        .setPositiveButton("Start hosting", (d, w) -> launch.accept(LinkTransport.bluetooth(catalog, true, adapter, null, code)))
                        .setNegativeButton("Cancel", null).show();
            } else {
                String[] names = new String[paired.size()];
                for (int i = 0; i < names.length; i++) {
                    BluetoothDevice device = paired.get(i);
                    String name = device.getName();
                    names[i] = (name == null || name.isEmpty() ? "Paired device" : name) + " · " + device.getAddress();
                }
                new AlertDialog.Builder(activity).setTitle("Choose the paired host phone")
                        .setItems(names, (d, i) -> bluetoothRoom(adapter, paired.get(i), launch))
                        .setNegativeButton("Cancel", null).show();
            }
        } catch (SecurityException denied) { fail("Allow Nearby Devices permission to use Bluetooth link."); }
    }

    private void bluetoothRoom(BluetoothAdapter adapter, BluetoothDevice peer, Consumer<GameLink> launch) {
        LinearLayout form = form();
        EditText room = field(form, "Room code from the host", 16, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        AlertDialog dialog = dialog("Join on Bluetooth", form, "Connect");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String secret = roomCode(room);
                dialog.dismiss(); launch.accept(LinkTransport.bluetooth(catalog, false, adapter, peer, secret));
            } catch (IOException invalid) { room.setError(invalid.getMessage()); }
        });
    }

    private static String code() {
        StringBuilder code = new StringBuilder(12);
        for (int i = 0; i < 12; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return code.toString();
    }

    private static String roomCode(EditText edit) throws IOException {
        String code = edit.getText().toString().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (code.length() != 12 || !code.matches("[" + ALPHABET + "]{12}")) throw new IOException("Enter the host's 12-character room code.");
        return code;
    }

    private LinearLayout form() {
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(18), ui.dp(4), ui.dp(18), ui.dp(6));
        return body;
    }

    private EditText field(LinearLayout body, String label, int length, int type) {
        EditText field = new EditText(activity);
        field.setHint(label);
        field.setInputType(type);
        field.setSingleLine();
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});
        body.addView(field);
        return field;
    }

    private AlertDialog dialog(String title, LinearLayout form, String action) {
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        return new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton("Cancel", null)
                .setPositiveButton(action, null).show();
    }

    private void fail(String text) {
        new AlertDialog.Builder(activity).setTitle("Local multiplayer").setMessage(text).setPositiveButton("OK", null).show();
    }
}
