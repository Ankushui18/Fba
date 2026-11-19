# FBA Arcade Link beta

Install the [FBA Arcade 0.5.6 APK](../README.md#install-and-play) on both phones.
It includes solo play and arcade Link multiplayer, and updates 0.5.5, 0.5.4, 0.5.3,
0.5.2 or 0.5.1 Recovery Preview without uninstalling. It does not update the
separately signed original Recovery. Console imports are solo-only. The wire and
compatibility ID are unchanged from 0.5.0; 0.4.0 peers are still rejected.

## Start a two-player game

1. Import byte-for-byte identical game and any required parent/BIOS ZIPs on
   both phones. Use a set with two digital players and the same Android ABI
   and a compatible Link build. This isn't a connection to the old FBA4droid APK.
2. For Wi-Fi, put both phones on the same private IPv4 Wi-Fi or hotspot.
   Networks that isolate clients may not work. For Bluetooth, pair the two
   phones in Android settings before opening the game. Link doesn't scan for
   devices or make a phone discoverable.
3. On the first phone, use the game's **⋮ → Local multiplayer → Host · P1**.
   Choose **Wi-Fi / local IPv4** or **Bluetooth · paired devices**. Share the
   room code privately. For Wi-Fi, also note the host address and port.
4. On the second phone, select the same game and **Join · P2**. Use the same
   connection method. Enter the address and port, or pick the paired host,
   then enter its room code. Connect after the host starts listening.
5. Once connected, both screens play the same game. Local touch and physical
   controls drive the assigned player, including Coin and Start. Pausing on
   either device stops game frames for both. To leave, use **Pause → Exit link**.
   Moving either app to the background ends the session.

Link uses the Android Internet permission to open a LAN socket, Local Network
permission where enforced (Android 17+), and Nearby Devices (`BLUETOOTH_CONNECT`)
on Android 12+ for Bluetooth (legacy `BLUETOOTH` on Android 8–11). It asks for
runtime permissions only for the selected connection method. Denying them
leaves solo play available. No access-point scan, location, or Bluetooth
advertisement permission is requested.

## Beta scope

- Both phones run their own FBNeo instances from a new deterministic boot. The
  Link build applies `android/patches/fbneo-session.patch` to request the
  core's existing deterministic random/time mode before driver init. Local
  Link files are temporary; they do not read or change solo states, scores or
  emulated battery storage. Exiting a Link match never changes Quick Resume.
  Saves, patches, cheats and DIP changes remain solo features.
- Link exchanges compatibility fingerprints and authenticated fixed-size
  digital inputs, pause flags and periodic AV checksums. It **does not** send
  ROM contents, saves, device files, or telemetry; no relay or DNS lookup is
  involved. Frames are authenticated, not encrypted. Use on a trusted local
  network with someone you know. No public Internet/IP, remote discovery or old
  FBA4droid protocol is supported.
- This is lockstep, not rollback. Slow connections slow play. If a driver
  diverges, the session ends on a checksum mismatch rather than importing a
  peer's native state. Matching archives reduce compatibility problems but
  don't prove every driver will stay in sync.
- The protocol passed a two-process Linux FBNeo test. **Real Android Wi-Fi
  and Bluetooth/RFCOMM sessions have not been device-tested.** Follow the
  [Android acceptance checklist](ANDROID_SMOKE.md) to test your phones.

## Host test

Use the unchanged FBNeo pin from the main README, and build the Link core in
its own source directory. From the repository root:

```sh
export FBNEO_SOURCE="$PWD/.cache/FBNeo-libretro"
./tools/prepare-link-core.sh "$FBNEO_SOURCE" "$PWD/.cache/FBNeo-link-host"
make -C .cache/FBNeo-link-host/src/burner/libretro -j2 platform=unix
./tools/smoke-link.sh .cache/FBNeo-link-host/src/burner/libretro/fbneo_libretro.so
```

The test drives two independent synthetic arcade-board instances over a
loopback TCP connection with the app wire protocol: player input, pause,
wrong code/revision rejection, and clean termination on desync. It never
packages the fixture and does not exercise Android permission dialogs or
Bluetooth hardware.
