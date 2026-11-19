# FBA Arcade privacy, asset provenance and core audit

See [licenses and corresponding source](../LICENSE.md) for credits, the original
FBNeo terms and the exact sources used for the Preview APK. The legacy APK and
ZIP described below are historical audit inputs, not supported downloads or
build dependencies. They are no longer tracked in the active Preview source tree.

## What the original contains

- Legacy APK `fba4droid_18644.apk`: `com.joyemu.fbaapp`, version `1.77`
  (`versionCode=20200306`), minimum API 14, target API 28, ARMv7 only.
  All 908 APK file entries match the 908 file entries in `fba4droid_18644.zip`
  by SHA-256. The ZIP adds directory entries but lacks the APK v2 signing
  block. Neither supplies the original Android or native source.
- Legacy static inspection identified an installed-app update path, a
  JavaScript-enabled browser, outbound links including a Baidu forum, Tencent
  Bugly initialization (crash reporting/update code), and AppConnect ad code
  with a path for downloading/loading code. The manifest declares internet,
  phone state, external storage and APK-install-request permissions among
  others. These are capabilities and code paths, **not observations of data
  being sent**. The old app wasn't installed or run.
- `assets/n.dat` and `assets/p.dat` in the old APK are ZIP containers with
  firmware/ROM data. Neither these assets nor the old DEX, native libraries,
  SDKs, signing files, HTML, or compiled resource XML enter the new build.
  FBA Arcade reuses selected static artwork and data, not legacy code.

## What matches upstream

Compared to [finalburnneo/FBNeo at `3e33b87`](https://github.com/finalburnneo/FBNeo/tree/3e33b87ed6826466d753f9c703e0eb514beb3788),
the read-only ELF inspection gives:

| Old library | `nBurnVer` in ELF | Exported `BurnDrv*` data symbols | Also found as same-named driver definitions in upstream |
|---|---:|---:|---:|
| `libfba.so` | `0.2.97.44` | 8,372 | 7,385 (88.21%) |
| `libfbas.so` | `0.2.97.08` | 802 | 382 (47.63%) |

Both identify as **FinalBurn Alpha** in their strings; the wrapper exposes
`Java_com_joyemu_fba_FbaEmu_*` JNI functions, separate from the core. The two
core ELF files together occupy 33,738,072 of 62,472,033 bytes (54.0%) of the old
APK's **uncompressed payload**. That size, symbol names, versions, and the old
`assets/license.txt` are evidence of FB Alpha lineage, not proof of a particular
FBNeo revision, exact function equivalence, or a percentage of copied source.
For a second comparison, 3,361 of 4,900 legacy `drvrom.xml` set names (68.59%)
also appear in the Libretro port's current arcade DAT (8,556 entries). Identical
names do not guarantee the same ROM CRCs, parent/BIOS needs, or playability.

The regular APK compiles **FBNeo 1.0.0.03** from the upstream-linked Libretro port at
[`2da48555`](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af)
(2026-11-18). It links a new JNI bridge and Android frontend. No compiled
code or emulator frontend source from the old APK is used. The main project
and the Libretro port are pinned separately: the former is the comparison
reference, the latter has the Android build and libretro frontend ABI. The
APK applies the bundled [session patch](../android/patches/fbneo-session.patch).
It enables deterministic time and random seeds before Link startup, refreshes
the current driver's control descriptors after switching games, and prevents
cleanup of an uninitialized driver on a missing-ROM or BIOS error. The patch
and core licenses are bundled in the APK. Starting with 0.5.9, the
[hiscore patch](../android/patches/fbneo-hiscore.patch) also ships with it.
It prevents partial `.hi` files from being loaded into score memory. The app
verifies user-selected `hiscore.dat` offline; it neither bundles it nor fetches it.
If you have a local copy you have the right to inspect, reproduce the comparison:

```sh
# With the two clean checkouts at the above commits:
python3 tools/compare_legacy.py /path/to/local/fba4droid_18644.apk /path/to/FBNeo /path/to/FBNeo-libretro
```

## What FBA Arcade reuses

| Legacy asset | Use in the new frontend |
|---|---|
| `skin_default`, `skin_1`, selected drawables | 152 static PNGs, including the original round joystick bases and knobs, directional and button pressed frames, and launcher/menu images. The drag editor and runtime use the same normalized layout. |
| `gameicon.zip` | 3,847 bounded RGBA thumbnails. The original ICO decoder is not bundled. Look up the exact set first, then parent art or a fallback icon. A user-picked image or a captured frame can override the thumbnail. |
| `drvrom.xml`, `listcn.txt` | Board, grouped categories and Chinese titles. Joined locally to the pinned arcade DAT for English titles, ROM dependencies and year/manufacturer. The 10,095 rows are metadata, not a supported-games count. |
| Pinned FBNeo arcade DAT | Indexed names, sizes and CRCs for the chips of 8,545 sets, used to select an IPS target. No chip data is stored. |
| Pinned FBNeo console/computer DATs | 21,539 game set names and 20 firmware sets across 19 systems. The metadata-only TSV contains names, years, sizes, CRCs and dependencies. `tools/make-console-index.py` regenerates it from the immutable pinned Git objects. No ROM or BIOS payload is stored in it. |
| `cheat_en.zip`, `cheat.zip` | Sanitized, exact-set definitions for 3,131 arcade sets, with English preferred and Chinese fallback. Unsupported groups, includes and non-byte-triple rules are excluded; all remaining groups start off. |

`tools/import_legacy_art.py` records each PNG's source path and validates the
APK hash. `tools/prepare_game_catalog.py` pins both the original APK and the
FBNeo arcade DAT by SHA-256. It only exports bounded PNGs, TSV metadata and a
constrained native cheat grammar. These steps are optional and are not part of
building the APK. Regenerate legacy assets only from a local copy you may use,
and redistribute them only if you have the required rights. With Pillow 13.0.0
in a Python virtual environment, run:

```sh
python tools/import_legacy_art.py /path/to/local/fba4droid_18644.apk
python tools/prepare_game_catalog.py /path/to/pinned/FBNeo-libretro --apk /path/to/local/fba4droid_18644.apk
python tools/prepare_rom_targets.py /path/to/pinned/FBNeo-libretro
python tools/make-console-index.py /path/to/pinned/FBNeo-libretro android/src/main/assets/console-sets.tsv
```

Only the current game's INI is staged in private `system/fbneo/cheats` while
it runs. This file needs to stay there because FBNeo rereads it when options
change or the game is reset. It is removed on close; stale staging files are
cleared at the next launch. A clone doesn't inherit a parent's cheat writes.
The emulation thread owns the libretro calls, including the V2 core option
updates, state serialization and reset. Tests verify a memory write with the
real core; they don't validate every original cheat against every compatible
ROM revision. Turning a cheat off stops the rule; it doesn't necessarily undo
memory changes that already happened.

## Feature parity and intentional gaps

See the [FBA4droid 1.77 comparison](FBA4DROID_PARITY.md) for the original evidence,
the 0.4.0 baseline and the changes in **0.5.0**. FBA Arcade remembers one
read-only Android folder grant. It only copies selected content to private
storage and can release the grant independently of any copy. Skin imports keep
only allowed, re-encoded PNG frames; local game info keeps plain text only,
not executable HTML. Old ads, telemetry, browser, updater and dynamic loading
were not restored.

Custom INI validation, current-core state format and save scopes are unchanged.
IPS groups add same-size changes for several checked chip members; staging only
the modified chips in a private session cache leaves the original ZIP and plain
saves unchanged. No raw definition from a picked document is passed to the core.
HQx is [separate LGPL HQx](../android/third_party/hqx/README.md); none of the
reviewed xBRZ copies established a compatible license grant. Old `.romdata`,
E680 `.fba` caches and Alpha states are not loaded. Digital mapping is not true
analog trackball or lightgun input.

The current APK declares only the local multiplayer permissions: `INTERNET`,
`ACCESS_LOCAL_NETWORK`, legacy `BLUETOOTH` through API 30, and
`BLUETOOTH_CONNECT`. Solo play does not need a network connection or a runtime
network/Bluetooth grant. Link binds a selected private IPv4 address and checks
the peer subnet, or opens an authenticated RFCOMM socket to a bonded peer;
the room code authenticates the input exchange.
There is no DNS, cloud service, discovery, SDK tracker or background listener.
The host and guest exchange set fingerprints and control data, not ROM/state
files. The frontend has no arbitrary host-side code execution or install path.

**Manual backup in 0.5.2:** Android system backup stays disabled. Only the
selected document URI is read or written; no grant or raw preferences file
is copied. Restore stages the ZIP in private cache, checks bounded sizes,
entry paths, completeness, checksums, slot headers and typed settings, and
only then adds missing slots. The ROM fingerprint is checked at load time.
Checksums detect damage, not who made the ZIP. Restore your own backups only.

**Quick Resume in 0.5.3:** Opt-in solo exit captures one snapshot on the core
worker, separately from the manual slots. The exact ROM/IPS target and state
are replaced together in private no-backup storage, only after a successful
write. Continue validates the core pin, ROM/IPS fingerprint and state checksum
before loading. Link matches, backgrounding and force-stop do not update the
auto-save. The checkpoint and its opt-in setting aren't in the portable ZIP.

## Verification and installation

- The ARM64 APK passes v2/v3 signature, launcher-only manifest, payload/CRC,
  native dependencies/imports, non-executable stack and **16 KiB APK and ELF
  alignment** checks. The verifier enforces a fixed manifest and networking
  API allowlist and compares the catalog and Link patch bytes against source.
  The `--legacy-offline` verifier mode still checks archived permission-free
  builds. These checks are not an antivirus verdict or a runtime network
  observation.
- The shell build uses Android SDK 37.0/Build Tools 36.1.0 from pinned
  [`whynusn/android-sdk-bundle@3dd1d84`](https://github.com/whynusn/android-sdk-bundle/tree/3dd1d84b2a76870a5995107613af83f6114450ae)
  (bundle checksums verified), Temurin 17.0.20.1 and NDK 23.0.7599858 from the
  pinned [Chromium mirror](https://github.com/chromium-full-mirror/android_ndk/tree/310956bd122ec2b96049f8d7398de6b717f3452e).
  The linker is explicitly configured for 16 KiB. Use official tools and a
  current NDK before public distribution.
- `tools/test.sh` checks imports, custom/auto/compact layouts, multitouch,
  state validation, backup round trips and no-overwrite/error handling,
  Quick Resume identity and isolation, IPS, INI validation, HQx pixel parity and frame handoff,
  the local link protocol and payload indexes. `tools/smoke-core.sh` runs a
  synthetic board program on the unchanged pinned Linux core: video, PCM, both player inputs, DIP,
  imported cheat writes, state restore/reset, and actual changed video from an
  multi-chip IPS variant followed by an unchanged regular launch. `tools/smoke-link.sh`
  drives two Link cores via the same wire protocol and verifies synchronized
  gameplay, pause and mismatch rejection. No physical Android devices were
  available; see the [device checklist](ANDROID_SMOKE.md).

| Original signed ARM64 test build | APK SHA-256 |
|---|---|
| FBA Arcade v0.4.0 Offline (versionCode 4) | `95043e0b7fa1db5b9f0ac606e13555cd39cfc81079b4dcb6f64b74e456934e9c` |
| FBA Arcade v0.4.0 Link beta (versionCode 4) | `27d33e5bf8ced4657acb3cf29d3824e8cd02bc3b14e82f205f538001b316c1fe` |
| FBA Arcade v0.3.0 (versionCode 3) | `ce39792bfad79f0a6ca8f1577a54c3daa0d4bd0b4f475cf0d9ac34d2983eb560` |
| Published v0.2.0 (versionCode 2) | `2e6b5d7ff292602e3574d0cdc5220cdfe8ef87be0f847243c89b1f90c4b5aa06` |
| Published v0.1.0 (versionCode 1) | `8d13b5ff5b563da0bf0af7415cb9520914622470fd978f3652ef45b02f16c930` |

The original v0.4.0 test build is `io.github.ankushui18.fbaarcade.test`, with
certificate SHA-256
`bd3ea132c39a00e884b83456f2f692e76bc73172015930af2be3f9462d016558`.
The two previous builds share `io.github.ankushui18.fbaoffline` and the
certificate SHA-256
`ce12cfdadf51d70c95bb24825b58d1af017acf8d23618ee74aaf781aa0d37cd9`.
The older signing key was unavailable when v0.3.0 was made. The 0.4.0 APKs
share the v0.3.0 test ID and signer, so install one **over that test build**
without uninstalling. They replace one another; both can coexist with the
original and v0.1/0.2 installs and do not access those apps' private data.

The restored source no longer has the original APKs or their signing key.
Recovery builds use `io.github.ankushui18.fbaarcade.recovery` and a new test
certificate. They are separate installs from the originals and do not
migrate or read the older app's private library or saves. The two Recovery
flavors share their own ID and signer, so they replace each other. The signed
0.5.0 and 0.5.1 Recovery test APKs use the same signer (SHA-256
`9a536eff83d3a9892a458927edcbb4e7209ccab339094f726c676d2ec197e1a3`) and a higher
versionCode (5 and 6), so 0.5.1 can update 0.5.0 or 0.4.0 Recovery without
uninstalling.

The current 0.5.9 APK uses launcher label **FBA Arcade**, version code 14,
and the same app ID (`io.github.ankushui18.fbaarcade.recovery.preview`) and
signer (SHA-256 `d8a45e30010cfbee84fef2227b3e0c98e585046658c0a0582df0845d8fd0d396`)
as 0.5.8 through 0.5.1 Recovery Preview. It cannot update original Recovery.
The console importer adds no permissions or changes to the Link wire protocol.
It wraps a recognized loose game in a private ZIP and stages the game and
imported dependencies under the expected system folder for the duration of
play. The library retains its stable archive names for saves. The specific
Ninja Gaiden alternate dump is checked by SHA-256 and uses a session-private
FBNeo override, not a global exception to checksum checks.

Standard `.nes` files outside the indexed sets can use the existing FBNeo
RomData interface. The app verifies the header, size, CRC and content-based
archive identity, and generates the fixed descriptor in private session
storage. It doesn't load user-supplied DAT files or enable patched-ROM mode
for ordinary direct imports. This release changes no native core or Link
protocol code. Add games accepts files selected through Android document
providers; it doesn't upload ROMs or fetch them from a server itself.
