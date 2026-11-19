# FBA Arcade

An independent Android frontend for FinalBurn Neo, with the supplied
FBA4droid's game art and original controller skins. It is not an
update of the original `com.joyemu.fbaapp`.

## Install and play

[**Download FBA Arcade 0.5.4 APK**](https://github.com/Ankushui18/Fba/raw/refs/heads/arena/efa257c2-fba/artifacts/fba-arcade-0.5.4-preview-link-arm64-v8a-test.apk)
— signed, 64-bit ARM, **Android 8 or later**. The opening screen gives new
users a single **Add games** button, and the populated library puts **Continue**
and games first. The same APK includes offline solo play, [local Wi-Fi/Bluetooth
multiplayer](docs/LINK_PLAY.md), [Quick Resume](#quick-resume), and
[Backup & restore](#back-up-saves-and-settings). There is no separate Offline build.

Download the `.apk`, not the [SHA-256 checksum](artifacts/FBA-Arcade-0.5.4-SHA256SUMS.txt).
**Install over 0.5.3, 0.5.2 (Link or the earlier Offline flavor), or 0.5.1
Recovery Preview without uninstalling.** The app ID and signer match, so the
library, saves and settings stay in place.

The separately signed original **FBA Arcade Recovery** and **FBA4droid** are not
updated by this APK. Leave them installed if you need their data. The backup
feature only reads data from the FBA Arcade install you're using.

The previous [0.4.0 Recovery prerelease](https://github.com/Ankushui18/Fba/releases/tag/v0.4.0-recovery)
is unchanged. See the [feature-by-feature 1.77 comparison](docs/FBA4DROID_PARITY.md)
for the restored features and remaining compatibility gaps.

1. Tap **Add games** on the welcome screen (or **+ Add** once games are in your
   library). Choose compatible ZIPs you have the right to use. Keep the FBNeo set
   names and include any required parent and BIOS ZIPs. A legacy FB Alpha archive
   with the same name may be a different revision. To import from a folder, use
   **Browse folder** on the welcome screen or **Settings → Browse ROM folder**.
   The Android picker can remember that folder so you can browse it again.
2. Tap a game, then **Coin** and **Start**. Use touch, an Android gamepad, or a
   keyboard. Keyboard defaults: arrows for movement, X/Z/S/A/W/Q for arcade
   A–F, Space for Coin, Enter for Start and Escape for Pause.

## Game and library tools

| Where | What it does |
|---|---|
| **Filter & sort** | Board/category filters, list/grid layout, English/Chinese titles and sorting by title, set, year, import date or board. Search, Favorites and Recent stay on the library screen. |
| **Game row ⋮ → Game info** | Catalog and artwork plus **Local note** to import/replace/remove an old `.rominfo` `.htm`/`.html` or `.txt` note. Only plain text is kept. The row menu can still import a cover, restore art or remove the private ZIP copy without deleting saves. |
| **Game row ⋮ → Save states** | Preview, rename and delete individual slots, or launch directly from a slot. **Pause → Save / Load** uses the same six slots. **Pause → Screenshot** captures a clean frame, updates the cover/patch preview, and can export PNG. |
| **Game row ⋮ → IPS patches** | Import a single `.ips` by chip, browse a legacy `.dat` folder for simple multi-file packs, or **Apply selected** profiles together. Each saved variant has its own preview, states and battery storage. The source ZIP is unchanged. |
| **Game row ⋮ → Cheat files** | Import a legacy `.ini`, select a cheat ZIP containing `<set>.ini` at its top level, or paste INI text. A compatible custom list replaces the bundled list for that exact ZIP revision; **Restore built-in cheats** reverses it. |
| **Pause → Cheats / DIP switches** | Choose a cheat or cabinet option, then **Apply → Resume**. Cheats start off and restart clears active choices. Some DIP changes need restart. Cheats may already have changed game memory when turned off. |
| **Settings → Virtual controls** or **Pause → Controls** | Four built-in skins. From library Settings, **Controller skin → Import folder** copies legacy PNG frames from the chosen skin folder. Apply D-pad or round stick art. Edit layouts independently in portrait and landscape; Auto uses digital button mappings, Compact uses smaller fixed positions. |
| **Virtual controls / Physical keys** | Independent joystick and button touch expansion under **Edit layout → Options**, plus opacity, scale, dead zone, animation and haptics. Set combinations on A–F or Combo 1–4; bind the latter under **Physical keys → EXT1–4**. Held-button repeat starts after a short hold. Up to four local digital input ports. |
| **Pause → Display** | Fit/fill/aspect ratios, integer scale, **Original size (1×)**, orientation, fullscreen portrait, sharp/bilinear or HQ2x/HQ3x/HQ4x, render frameskip, game/video FPS and sound. Optional GPU canvas takes effect next launch. |
| **Settings → Reset game settings** | Clear global and per-game playback preferences without removing the library, folder grant, imported skins or notes, cheats, patches, states, thumbnails, favorites or history. |

The library gear edits global defaults. In a game settings panel, choose
**For this game** to customize it or **Use global** to clear overrides. The
round stick maps digital directions, not analog trackball or lightgun input.

### Quick Resume

1. From the library gear, choose **Settings → Quick Resume → Turn on**. It is
   off by default, including after an update from 0.5.2.
2. Play a solo game. Choose **Pause → Exit game** and wait for **Quick Resume
   saved**. Returning to Android Home, force-stopping, and exiting a Link match
   do not save a checkpoint.
3. Tap **Continue** at the top of the games list to load the last successful
   solo checkpoint. Tapping a game row still starts it normally. Continue needs
   the same ROM ZIP revision and IPS variant if you used patches.

There is one automatic slot, separate from all six manual slots. Each successful
solo exit replaces that auto-save. If capture fails, you can stay paused or exit
without replacing the previous one. Turning Quick Resume off hides Continue but
keeps the checkpoint; **Clear auto-save** deletes only that checkpoint. The
checkpoint and its opt-in setting stay on this phone. Use **Pause → Save** if
you want that progress included in a portable ZIP.

### Back up saves and settings

1. In each game whose progress you want to transfer, use **Pause → Save** to
   write a save-state slot, then return to the library. Open **Settings → Backup
   & restore → Export ZIP** and choose a location such as **Downloads**. Keep a
   copy of the ZIP off the phone before a reinstall or a phone change.
2. On the destination install, choose **Settings → Backup & restore → Restore
   ZIP** and confirm. The app adds missing slots and **never overwrites occupied
   slots**. Settings included in the ZIP replace matching settings; preferences
   not in the ZIP stay as they are. The result lists added and skipped slots.
3. Re-import the **same ROM ZIPs** (and required parent/BIOS files) separately.
   For an IPS save, recreate the variant with the same files in the same apply
   order. Load from **Pause → Load** or **Game row ⋮ → Save states** (under the
   IPS profile for a patched save). Save states still check the ROM/variant
   fingerprint and FBNeo version at load time.

The ZIP includes save states and slot names, plus portable global and per-game
settings (including display, audio, controller layouts and bindings, built-in
skin choices, and library display preferences). It does **not** include ROMs,
BIOS, IPS files, in-game battery/memory-card saves, high scores, previews or
screenshots, custom skins/notes/cheats, favorites/history, or Android folder
permissions. Keep your original files separately and reselect folders as needed.

This is a manual, unencrypted ZIP, not a full device backup. Restore only ZIPs
exported by your own FBA Arcade install. Backups support up to 256 MiB expanded
and 4,096 files. Keep enough free device space for a private temporary copy of
the ZIP and the unpacked saves during restore.

### Image-filter performance

HQx and Canvas drawing run on a separate, lower-priority presenter. If it
falls behind, it replaces the queued image rather than delaying a game step,
audio write, or Link exchange. The FPS overlay is **game / video** (emulated
frames / frames submitted to Android). HQx keeps the same pixels; on a slower
phone try HQ2x or Bilinear if video FPS is low. **Original size (1×)** bypasses
filtering, and screenshots and save previews still use the raw core frame.

### Compatibility limits

- IPS uses literal and RLE writes to named, **same-size chips** in a matching
  current FBNeo ZIP. Classic `.ips` doesn't prove the source revision. A `.dat`
  supported by the [legacy subset](docs/FBA4DROID_PARITY.md#legacy-ips-subset) maps
  chip names to same-folder IPS files. Up to 8 IPS files per saved variant;
  later selected writes win. No cross-set chips, resizing, IPS32 or `.romdata`
  board overrides. Real old packs beyond the tested subset can differ.
- If Android's picker hides `.skin`, `.rominfo` or `.ips`, copy the individual
  skin/patch folder or note to a visible directory first. Skin import accepts
  recognized PNG filenames only (including pressed frames and preview icons).
  It does not run anything in a skin or import legacy control-layout files.
- Custom INI files support the sequential FBA byte-write option syntax. Includes,
  script-like directives and incompatible groups are not run. Example format
  (use actual CPU, address and value from a cheat for the selected set):

  ```text
  cheat "Example"
  type 0
  default 0
  0 "Off"
  1 "On", 0, 0x800, 0x55
  ```

- **Old `.fba` cache files and FinalBurn Alpha save states are not compatible.**
  No matching files were supplied for format validation. Use current FBNeo ZIPs
  and keep your old install for old states. xBRZ is not bundled. Wi-Fi and
  Bluetooth in Link beta are not compatible with the old FBA4droid protocol.
- No old ads, analytics, browser, updater, ROMs or BIOS images were included.

## Data and privacy

The Android picker gives read access to the files or folder you select. The ROM
browser remembers one read-only folder grant where supported; **Forget** releases
it without deleting any copies. ZIPs, notes, skins and patches are copied to
private storage and stay on the device. Limits: 64 ZIPs per file selection, 256
items per folder listing, and 512 MiB per ZIP. Uninstalling deletes this install's
data. Export a backup ZIP and any screenshots you want to keep first. Manual
backup/restore uses the Android document picker; it doesn't add permissions,
a cloud account or automatic uploads.
Solo play does not need a network connection. The APK declares only the
[permissions used for local multiplayer](docs/LINK_PLAY.md) and runs no
background network service. See [asset provenance and privacy](docs/PRIVACY_AND_CORE.md).

## Build and verify

On Linux x86-64, install JDK 17+, GNU make, Git, binutils (`readelf`), C/C++
compilers, Python 3, Android SDK Platform 37 and Build Tools 36.1.0, and an
up-to-date NDK (r28c or newer recommended for distribution). Export absolute
paths:

```sh
export JAVA_HOME=/path/to/jdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
./tools/build-apk.sh
./tools/test.sh
./tools/verify-apk.sh artifacts/fba-arcade-0.5.4-link-arm64-v8a-test.apk
```

No Gradle/Maven dependencies or legacy APK files are needed. `tools/build-core.sh`
fetches and checks the [FBNeo Libretro pin `2da48555`](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af).
Set `FBNEO_SOURCE` to a clean checkout of that pin to build without fetching the
core. The build always includes Link and applies the recorded deterministic
startup patch in a separate source directory. `ANDROID_PLATFORM`,
`ANDROID_BUILD_TOOLS`, `JOBS` and `APK_OUTPUT` are optional build overrides.
`ANDROID_ABIS=x86_64` builds an x86-64 APK. To audit an older permission-free
Offline APK, use `./tools/verify-apk.sh OLD.apk --legacy-offline`.
See [Android acceptance checks](docs/ANDROID_SMOKE.md).

For a real-core Linux smoke test, build the pinned core with
`make -C "$FBNEO_SOURCE/src/burner/libretro" -j2 platform=unix` and run
`tools/smoke-core.sh "$FBNEO_SOURCE/src/burner/libretro/fbneo_libretro.so"`.
This uses an ephemeral synthetic board probe, not the original game ROM. An
optional second argument is a compatible local ZIP to smoke test too. For the
separate two-core protocol test, see [Link setup](docs/LINK_PLAY.md#host-test).

**Signing:** By default the scripts use a local test key under `.cache/signing/`
(never checked in) and the ID `io.github.ankushui18.fbaarcade.test`. Keep the
matching key for upgrades. `ARCADE_RECOVERY=preview ./tools/build-apk.sh`
uses `io.github.ankushui18.fbaarcade.recovery.preview`, the app ID of the
published APK, and requires the original Preview key. `ARCADE_RECOVERY=1`
uses `io.github.ankushui18.fbaarcade.recovery` and requires that separate app's
key. The script verifies each upgrade certificate and will not generate
replacement keys for these IDs. These are signing profiles, not feature
variants; all produce the same solo and local multiplayer features.
`APK_KEYSTORE` and `APK_KEY_ALIAS` use
your own signer and the original frontend ID `io.github.ankushui18.fbaoffline`;
the script prompts for the password locally. A new key cannot update an
existing install.

## Credits and license

This is not an official FBNeo or FBA4droid release. The pinned source, FBNeo's
non-commercial license and library notices are included in the APK. HQx is a
separate LGPL-2.1-or-later library; [its source and replacement instructions](android/third_party/hqx/README.md)
are in this repository. Preserve notices and make corresponding sources
available when redistributing. The supplied artwork and asset data remain the
property of their owners. Verify your rights before redistributing. Do not sell
the build or distribute unauthorized ROM images.
