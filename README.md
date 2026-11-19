# FBA Arcade — FinalBurn Neo for Android

FBA Arcade is an unofficial **Android arcade and retro console emulator
frontend** powered by FinalBurn Neo (FBNeo). Play compatible arcade ZIP sets
offline, add supported `.nes` cartridges directly, or import matching games
for SNES, Sega Genesis / Mega Drive, GBA and other supported systems.

> **Free for personal, non-commercial play. Not for sale.** This is not an
> official FBNeo release. We do not own the upstream emulator code or the
> third-party games and assets. The Preview APK includes no game ROMs or
> original BIOS archives. See [licenses, credits and corresponding source](LICENSE.md).

[Download APK](#install-and-play) · [Supported systems and formats](docs/CONSOLE_SUPPORT.md) · [Local multiplayer setup](docs/LINK_PLAY.md) · [Report an issue](#get-help)

## Install and play

[**Download FBA Arcade 0.5.9 Preview APK**](artifacts/fba-arcade-0.5.9-preview-link-arm64-v8a-test.apk)
— signed, 64-bit ARM, **Android 8 or later**. One APK includes offline solo play,
local Wi-Fi/Bluetooth arcade multiplayer, [Quick Resume](#quick-resume), and
[Backup & restore](#back-up-saves-and-settings). There is no separate Offline build.

Download the `.apk`, not the [SHA-256 checksum](artifacts/FBA-Arcade-0.5.9-SHA256SUMS.txt).
**Install over Recovery Preview 0.5.1–0.5.8 without uninstalling.** The app ID
and signer match, so the library, saves and settings stay in place. Use **Add
games** again for files that were previously skipped.

This is not an update for the original **FBA Arcade Recovery** or **FBA4droid**
(`com.joyemu.fbaapp`). Leave those installed if you need their data. The backup
feature only reads data from the FBA Arcade install you're using. See the
[0.5.8 compatibility audit](docs/COMPATIBILITY_0.5.8.md) for the baseline against
FBA4droid and FinalBurn Alpha. See [what 0.5.9 adds and verifies](docs/FBNEO_SETTINGS.md)
for current settings behavior and remaining gaps.

1. Tap **Add games** on the welcome screen (or **+ Add** once games are in your
   library). Choose your `.nes` or other game file. In Android's **Recent**
   view, the file may not be listed; open **Downloads** or the folder where
   you saved it. Extract the `.nes` first if it is inside a general ZIP or 7z
   archive. For arcade ZIPs, keep the FBNeo set names and include any required parent and BIOS ZIPs. A legacy FB
   Alpha archive with the same name may be a different revision. To import from
   a folder, use **Browse folder** on the welcome screen or **Settings → Browse
   ROM folder**. The Android picker can remember that folder for next time.
2. Tap a game, then **Coin → Start** for arcade or the console controls (usually **Start**). Use touch,
   an Android gamepad, or a keyboard. Keyboard defaults: arrows for movement,
   X/Z/S/A/W/Q for arcade A–F, Space for Coin/Select, Enter for Start and Escape
   for Pause. MSX1/ZX Spectrum forward physical keyboard keys to the computer.

### Consoles and computers

Use **Add games** for `.nes` files: known FBNeo dumps keep their existing
library entries, and ordinary iNES / NES 2.0 cartridges with [supported
mappers](docs/CONSOLE_SUPPORT.md#direct-nes-files) don't need a matching ZIP.
This build covers [19 console and computer systems](docs/CONSOLE_SUPPORT.md);
other systems such as SNES, Genesis / Mega Drive and GBA require a matching
FBNeo dump or set ZIP. Supply only ROMs and BIOS files you have the right to use.
If a selected file doesn't appear, check **Import results** for the reason or the
name of the existing entry. Console games run solo; Link is for arcade games.
CD images (Neo Geo CD, PCE CD and Sega CD) are not imported by this build.

## Game and library tools

| Where | What it does |
|---|---|
| **Filter & sort** | Board/category filters, list/grid layout, English/Chinese titles and sorting by title, set, year, import date or board. Search, Favorites and Recent stay on the library screen. |
| **Game row ⋮ → Game info** | Catalog and artwork plus **Local note** to import/replace/remove an old `.rominfo` `.htm`/`.html` or `.txt` note. Only plain text is kept. The row menu can still import a cover, restore art or remove the private ZIP copy without deleting saves. |
| **Game row ⋮ → Save states** | Preview, rename and delete individual slots, or launch directly from a slot. **Pause → Save / Load** uses the same six slots. **Pause → Screenshot** captures a clean frame, updates the cover/patch preview, and can export PNG. |
| **Game row ⋮ → IPS patches** | Import a single `.ips` by chip, browse a legacy `.dat` folder for simple multi-file packs, or **Apply selected** profiles together. Each saved variant has its own preview, states and battery storage. The source ZIP is unchanged. |
| **Game row ⋮ → Cheat files** | Import a legacy `.ini`, select a cheat ZIP containing `<set>.ini` at its top level, or paste INI text. A compatible custom list replaces the bundled list for that exact ZIP revision; **Restore built-in cheats** reverses it. |
| **Pause → Cheats / DIP switches** | Choose a cheat or cabinet option, then **Apply → Resume**. Cheats start off and restart clears active choices. Some DIP changes need restart. Cheats may already have changed game memory when turned off. |
| **Pause → FBNeo settings** | Per-game CPU clock, timing, video depth, core frameskip, audio tuning, direction priority and high scores where supported. Save, exit, then start normally for changes to apply. These settings do not apply to Link or IPS variants. |
| **Settings → Virtual controls** or **Pause → Controls** | Four built-in skins. From library Settings, **Controller skin → Import folder** copies legacy PNG frames from the chosen skin folder. Apply D-pad or round stick art. Edit layouts independently in portrait and landscape; Auto uses digital button mappings, Compact uses smaller fixed positions. |
| **Virtual controls / Physical keys** | Independent joystick and button touch expansion under **Edit layout → Options**, plus opacity, scale, dead zone, animation and haptics. Set combinations on A–F or Combo 1–4; bind the latter under **Physical keys → EXT1–4**. Held-button repeat starts after a short hold. Up to four local digital input ports. |
| **Pause → Display** | Fit/fill/aspect ratios, integer scale, **Original size (1×)**, orientation, fullscreen portrait, sharp/bilinear or HQ2x/HQ3x/HQ4x, render frameskip, game/video FPS and sound. Optional GPU canvas takes effect next launch. |
| **Settings → Reset game settings** | Clear global and per-game playback preferences without removing the library, folder grant, imported skins or notes, cheats, patches, states, thumbnails, favorites or history. |

The library gear edits global defaults. In a game settings panel, choose
**For this game** to customize it or **Use global** to clear overrides. The
round stick maps digital directions, not analog trackball or lightgun input.

### FBNeo settings

Start a regular solo game, open **Pause → FBNeo settings**, choose options, and
tap **Save**. Exit the game and tap its library row to apply changes. **Restart**
inside the game resets the running driver, not its boot settings. Some choices
only affect compatible hardware (for example FM interpolation or CPU clock).
Core frameskip skips draws inside FBNeo; **Display → Frameskip** skips only
presentation. Leave either off before comparing performance and smoothness.

Default settings still load existing Preview states. If you change FBNeo settings,
manual states and Quick Resume require the same choices used when saved. You can
restore defaults without deleting states from **Game row ⋮ → Reset FBNeo settings**.
[Details and test results](docs/FBNEO_SETTINGS.md).

### High score setup

Download the pinned [hiscore.dat](https://github.com/libretro/FBNeo/raw/2da48555c13ce5d011edf2d6ec829b7393c070af/metadata/hiscore.dat)
from FBNeo. In library **Settings → High score data**, choose that unmodified
file. The app checks its size and SHA-256 before copying it into private storage.
In a supported game, turn on **Pause → FBNeo settings → Save high scores**,
save the settings, then exit and reopen. Obtain a score and use **Exit game**
so FBNeo can save it. The driver must have a compatible table in the DAT.
The `.dat` is optional, isn't a ROM or BIOS, and is not bundled or downloaded by the app.

### Quick Resume

1. From the library gear, choose **Settings → Quick Resume → Turn on**. It is
   off by default, including after an update from 0.5.2.
2. Play a solo game. Choose **Pause → Exit game** and wait for **Quick Resume
   saved**. Returning to Android Home, force-stopping, and exiting a Link match
   do not save a checkpoint.
3. Tap **Continue** at the top of the games list to load the last successful
   solo checkpoint. Tapping a game row still starts it normally. Continue needs
   the same ROM ZIP revision and IPS variant (if used) and the same FBNeo settings.

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
   IPS profile for a patched save). Save states check the ROM/variant, FBNeo
   version and boot settings at load time.

The ZIP includes save states and slot names, plus portable global and per-game
settings (including display, audio, controller layouts and bindings, built-in
skins, library preferences and FBNeo boot profiles). It does **not** include ROMs,
BIOS, IPS files, in-game battery/memory-card saves, score files or their DAT, previews or
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
it without deleting any copies. Games, notes, skins and patches are copied to
private storage. Add games can show other Android document providers; any
fetching by a remote provider is handled by that provider, not FBA Arcade. Limits: 64 files per selection, 256 items per folder listing,
512 MiB per arcade ZIP, 80 MiB per console ZIP and 64 MiB per loose console
file (plus an optional 512-byte SNES copier header). Uninstalling deletes this install's
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
./tools/verify-apk.sh artifacts/fba-arcade-0.5.9-link-arm64-v8a-test.apk
```

No Gradle/Maven dependencies or legacy APK files are needed. `tools/build-core.sh`
fetches and checks the [FBNeo Libretro pin `2da48555`](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af).
Set `FBNEO_SOURCE` to a clean checkout of that pin to build without fetching the
core. The build always includes Link and applies the [session patch](android/patches/fbneo-session.patch)
and [score file read check](android/patches/fbneo-hiscore.patch) in a separate source directory. `ANDROID_PLATFORM`,
`ANDROID_BUILD_TOOLS`, `JOBS` and `APK_OUTPUT` are optional build overrides.
`ANDROID_ABIS=x86_64` builds an x86-64 APK. To audit an older permission-free
Offline APK, use `./tools/verify-apk.sh OLD.apk --legacy-offline`.
See [Android acceptance checks](docs/ANDROID_SMOKE.md).

For real-core Linux smoke tests, build the patched host core as shown in
[Link setup](docs/LINK_PLAY.md#host-test), then run `tools/smoke-core.sh`,
`tools/smoke-console.sh` and `tools/smoke-hiscore.sh` with that `.so`. They use temporary synthetic fixtures,
not the original games. A compatible local arcade ZIP or the verified NES
alternate dump can optionally be tested by the respective script.

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

## Get help

Check [supported formats and BIOS notes](docs/CONSOLE_SUPPORT.md), then
[report an issue](https://github.com/Ankushui18/Fba/issues/new) with the FBA Arcade
version, Android version and device model. For a game that didn't add, include
its filename and the **Import results** reason. Do not attach ROMs, BIOS files
or backups. For an attribution concern, include the affected file or asset path.

## Credits and license

Thanks to Team FBNeo, the original Final Burn and MAME contributors, Libretro,
and the authors of the bundled libraries. The FBNeo-based build is for
non-commercial play, not for sale or donation solicitation. [Licenses, credits
and corresponding source](LICENSE.md) lists the exact core revision, published
changes and third-party asset provenance. The APK carries the full FBNeo license,
patch and library notices; the [verbatim FBNeo text](LICENSES/FBNeo-LICENSE.txt)
is also available here. Separate components such as HQx retain their own licenses.
