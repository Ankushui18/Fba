# FBA Arcade

An independent Android frontend for FinalBurn Neo, with the supplied
FBA4droid's game art, blue library and original controller skins. It is not an
update of the original `com.joyemu.fbaapp`.

## Install and play

The original 0.4.0 signing key was not recovered. The rebuilt ARM64 APKs
need Android 8 or later and install as **FBA Arcade Recovery** alongside old
versions. Keep the old app if you need its library or saves. Recovery Offline
and Recovery Link beta replace each other; choose one APK.

| Rebuilt 0.4.0 test APK | Use it for |
|---|---|
| `artifacts/fba-arcade-0.4.0-recovery-arm64-v8a-test.apk` | Offline play, **zero Android permissions**. |
| `artifacts/fba-arcade-0.4.0-recovery-link-arm64-v8a-test.apk` | Optional **Link beta** for two-phone play; real Wi-Fi/Bluetooth hardware testing is pending. |

The original files below are documented for users who already have them.

| Original signed 0.4.0 test APK | Use it for |
|---|---|
| `artifacts/fba-arcade-0.4.0-arm64-v8a-test.apk` | The regular build. All solo/local controller features, **zero Android permissions**. |
| `artifacts/fba-arcade-0.4.0-link-arm64-v8a-test.apk` | Optional **Link beta**. The same solo features plus two-phone Wi-Fi/LAN or paired Bluetooth play. See [Link setup and limitations](docs/LINK_PLAY.md). |

Both are for 64-bit ARM phones on Android 8 or later. Both have versionCode 4,
use the same application ID and signer as **our v0.3.0 test APK**, and are
alternatives to each other. Install one **over v0.3.0 without uninstalling** to
keep its library, settings and saves. The original FBA4droid and our v0.1/0.2
installs are separate apps: keep them installed if you still need their data.

1. In **Games**, tap the folder icon to select ZIPs, or use **Files → Choose
   folder**. Import compatible ROM archives you have the right to use. Keep the
   FBNeo set names, and import any required parent and BIOS ZIPs alongside them.
   A legacy FB Alpha archive with the same name may be a different revision.
2. Tap a game, then **Coin** and **Start**. Use touch, an Android gamepad, or a
   keyboard. Keyboard defaults: arrows for movement, X/Z/S/A/W/Q for arcade
   A–F, Space for Coin, Enter for Start and Escape for Pause.

## Game and library tools

| Where | What it does |
|---|---|
| **Games → Sorting / Filter** | List/grid with thumbnails, Favorites, Recent, search, board/category filters, English/Chinese titles and sorting by title, set, year, import date or board. |
| **Game row ⋮ → Game info** | Title, set name, board, maker, year, parent/BIOS dependency and thumbnail. The row menu can import a cover, restore original art, or remove the private ZIP copy without deleting saves. |
| **Game row ⋮ → Save states** | Preview, rename and delete individual slots, or launch directly from a slot. **Pause → Save / Load** uses the same six slots. **Pause → Screenshot** captures a clean frame, updates the cover/patch preview, and can export PNG. |
| **Game row ⋮ → IPS patches** | Select the chip targeted by a standard `.ips`, then import it. Choose a saved patch to play it as a separate variant with its own preview, saves and emulated battery storage. The original ZIP is not modified. |
| **Game row ⋮ → Cheat files** | Import a legacy `.ini`, select a cheat ZIP containing `<set>.ini` at its top level, or paste INI text. A compatible custom list replaces the bundled list for that exact ZIP revision; **Restore built-in cheats** reverses it. |
| **Pause → Cheats / DIP switches** | Choose a cheat or cabinet option, then **Apply → Resume**. Cheats start off and restart clears active choices. Some DIP changes need restart. Cheats may already have changed game memory when turned off. |
| **Settings → Virtual controls** or **Pause → Controls** | Four original-art skins: Classic and Chrome, each with D-pad and round joystick. Custom drag/resize/hide positions are saved separately for portrait and landscape. Auto uses the game's digital button mappings; Compact uses fixed, smaller positions. Saving in the editor selects Custom. |
| **Virtual controls / Physical keys** | Opacity, scale, dead zone, enlarged touch zone, press animation, haptics, four combo buttons, held-button turbo, player selector, rebindable physical keys and optional hardware input mirroring. Up to four local digital input ports, where the game supports them. |
| **Pause → Display** | Aspect/size, orientation, full-screen portrait, sharp/bilinear or HQ2x/HQ3x/HQ4x, render frameskip 0–4 or Auto, FPS counter and sound. Optional GPU canvas takes effect next game launch. |
| **Settings → Reset game settings** | Clear global and per-game playback preferences without removing games, cheats, patches, states, thumbnails, favorites or history. |

The library gear edits global defaults. In a game settings panel, choose
**For this game** to customize it or **Use global** to clear overrides. The
round stick maps digital directions, not analog trackball or lightgun input.

### Compatibility limits

- IPS supports literal and RLE changes to **one named, same-size chip** per
  variant. That chip must be present in the selected ZIP with the pinned DAT's
  expected size and CRC. Standard IPS does not identify the source revision;
  select the chip and patch made for your set. Legacy multi-chip `.dat` packs,
  IPS32 and patches needing expanded ROM space are not supported.
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

The system picker grants access to files or a folder you select; imports are
copied into private app storage and stay on the device. Limits: 64 ZIPs per
picker selection, 256 at the top level of an imported folder, 512 MiB per ZIP.
Uninstalling deletes this install's data. Export screenshots you want to keep.
The optional Link APK adds narrowly scoped local multiplayer traffic and no
background service. See [asset provenance and privacy](docs/PRIVACY_AND_CORE.md).

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
ARCADE_LINK=1 ./tools/build-apk.sh
./tools/test.sh
./tools/verify-apk.sh artifacts/fba-arcade-0.4.0-arm64-v8a-test.apk
./tools/verify-apk.sh artifacts/fba-arcade-0.4.0-link-arm64-v8a-test.apk --link
```

No Gradle/Maven dependencies or legacy APK files are needed. `tools/build-core.sh`
fetches and checks the [FBNeo Libretro pin `2da48555`](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af).
Set `FBNEO_SOURCE` to a clean checkout of that pin to build offline. The regular
build uses it unchanged; Link applies the small, recorded deterministic startup
patch in a separate source directory. `ANDROID_PLATFORM`, `ANDROID_BUILD_TOOLS`,
`JOBS` and `APK_OUTPUT` are optional build overrides. `ANDROID_ABIS=x86_64` builds
an x86-64 APK. See [Android acceptance checks](docs/ANDROID_SMOKE.md).

For a real-core Linux smoke test, build the pinned core with
`make -C "$FBNEO_SOURCE/src/burner/libretro" -j2 platform=unix` and run
`tools/smoke-core.sh "$FBNEO_SOURCE/src/burner/libretro/fbneo_libretro.so"`.
This uses an ephemeral synthetic board probe, not the original game ROM. An
optional second argument is a compatible local ZIP to smoke test too. For the
separate two-core protocol test, see [Link setup](docs/LINK_PLAY.md#host-test).

**Signing:** By default the scripts use a local test key under `.cache/signing/`
(never checked in) and the ID `io.github.ankushui18.fbaarcade.test`. Keep the
matching key for upgrades. Use `ARCADE_RECOVERY=1 ./tools/build-apk.sh` (also set
`ARCADE_LINK=1` for Link beta) to build a separate install with the ID
`io.github.ankushui18.fbaarcade.recovery` and its own local test key. This does
not migrate your old app's private data. `APK_KEYSTORE` and `APK_KEY_ALIAS` use
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
