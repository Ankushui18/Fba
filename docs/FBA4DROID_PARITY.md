# FBA4droid 1.77 parity check

For the baseline and later work, see the [0.5.8 compatibility audit](COMPATIBILITY_0.5.8.md)
and [0.5.9 FBNeo settings](FBNEO_SETTINGS.md). This page preserves the older
comparison and the supported legacy IPS format.

This compares the supplied `fba4droid_18644.apk` (SHA-256
`9099a973c4bab3c8634583e5d3630f8f0642d7d3c993e698e6ea64120f36f775`,
`com.joyemu.fbaapp` 1.77/20200306) with FBA Arcade Recovery 0.4.0 and 0.5.0.
The old APK was inspected, not installed. It is a local audit input, not a
project download. Its help is older than the APK, so help alone isn't proof
that a legacy file or behavior works on an Android device. For third-party
rights and the Preview's source, see [licenses and attribution](../LICENSE.md).

| Feature | Evidence in the supplied APK | Recovery 0.4.0 | Recovery 0.5.0 |
|---|---|---|---|
| Local library and metadata | Games and Files tabs, `drvrom.xml`, `listcn.txt`, `gameicon.zip` | Present: library art, lists/grid, search, board/category/sorting, Favorites and Recent | Preserved. Uses FBNeo set/CRC requirements. |
| Remember a ROM folder and browse subfolders | English Help default `/sdcard/fba`, Files tab and `LAST_DIR` DEX preference | Partial: select a folder once and copy top-level ZIPs | **Added:** revisit the granted folder, browse up/down within it, import a ZIP or listed ZIPs, change/forget access. Plays private copies, not arbitrary shared paths. |
| Imported controller skin | Help `.skin`, DEX `PREF_CUSTOM_SKIN_PATH`/`TYPE` and sprite filenames | Missing: four built-in skins only | **Added:** select a skin's PNG folder, optional `icon1.png`/`icon2.png` previews, D-pad or round stick, built-in fallbacks. Does not import old layout INIs. |
| Per-game local notes | Chinese Help `.rominfo/<set>.htm`, game detail/info UI | Missing: catalog and artwork only | **Added:** import `.htm`, `.html` or `.txt` under Game info. Stores visible text only; no embedded browser, images, active links, or scripts. |
| Extra physical keys for combos | DEX `KEY_EXT1_VAL`–`KEY_EXT4_VAL` | Missing: physical mappings stop at the 12 base controls | **Added:** physical EXT1–4 use the corresponding virtual combo's mask, per-game or global. |
| Face-button combos and held autofire | `game_setting_combikey` has rows for face buttons and extras; Chinese Help describes hold-to-repeat | Partial: combos 1–4, turbo on a shared wave | **Added:** A–F can also send combinations. Selected face bits register a press immediately and repeat after about ¼ second, independently per player. Timing isn't a claim of exact Alpha behavior. |
| Separate joystick/button hit expansion | `analogExpandzoneNEW` and `buttonExpandzoneNEW` in `res/xml/prefs.xml` | Partial: one shared value | **Added:** separate slider values per orientation. Old shared values seed both without moving controls. |
| Touch editing, opacity, presets and local players | Defaults in `assets/pad_{analog,digital}.ini`; touch layout and player options | Present: portrait/landscape drag/resize/hide, Custom/Auto/Compact, local digital P1–P4 | Preserved. Auto uses the current game's digital descriptors; this is not true analog, lightgun, or trackball input. |
| DIP switches, cheats, states and snapshots | Game menu, details, `.cheats` Help, native BurnState functions | Present: DIP/cheat menus, compatible custom cheat INI/ZIP, six save slots, thumbnail/screenshot | Preserved with the same core and state format. This is not import of old Alpha states. |
| IPS folder browse, preview and Apply Selected | `game_detail` IPS tree/preview and Apply Selected; `.ips` Help; both Alpha libraries have active patch lists | Partial: one `.ips` on one chip per variant, separate preview/saves | **Added subset:** choose a folder and simple `.dat` map for multiple `.ips` files; select saved profiles to apply together. The result is a separate playable variant with its own preview/state slots/battery storage. No resize, IPS32, selection `.ini` migration, cross-set chip targeting, or claim of all old packs working. |
| Screen sizing | `game_setting_screen` and `screenMode`: Origin Size, Auto Stretch, ratios 4:3/3:4 and Full | Partial: fit/fill/ratios/integer scale, no fixed 1× | **Added:** centered raw 1× pixels when they fit, sharp downscale in a smaller window. Fit and Fill remain separate. |
| Filters and renderer/input variants | `libfbawrapper.so`: `scaleXbrz`, `setGLQuality`; settings `renderQuality`, `autoMultiThread`, `analogPretty` | Partial: HQ2x/3x/4x, sharp/bilinear, GPU canvas, render frameskip and stick dead zone | **Still partial.** No xBRZ (a compatible license grant wasn't established), old GL quality/thread policy, or exact analog appearance/response options. |
| Legacy `.romdata` board overrides | Chinese Help, `readRomdata`/CPS1/CPS2/NeoGeo handlers in the old ELF | Missing | **Still missing.** The newer FBNeo core's internals are not proof it accepts old board data. Needs legal samples and validation before enabling. |
| E680 `.fba` and FinalBurn Alpha states | Help lists PGM/NeoGeo/CPS2 E680 cache format; `FS1 ` / `FB1 ` and state functions in the old ELF | Missing | **Still missing.** No validated samples for cache format or state conversion; same filename does not mean same ROM/core state. |
| Link over Wi-Fi / paired Bluetooth | Host/join UI and legacy transport code | Partial: opt-in Link beta with a new protocol | **Still partial:** Link beta requires matching Link APK and ROM/dependency bytes on both phones. Not interoperable with FBA4droid. Actual Android Wi-Fi and Bluetooth testing pending. |
| Ads, updater, analytics | Original manifest and SDK/DEX paths | Intentionally excluded | Still excluded. Offline needs no Android permissions. |

## Legacy IPS subset

The original `FbaEmu` scans `.ips/<set>` for `.dat` files. `libfba.so`'s
`LoadIpsActivePatches` reads `.ips/<set>.ini` for activation; the apply path
reads whitespace-separated ROM chip and patch names before the first `[section]`
and appends `.ips` to a patch stem. `libfbas.so` also exports the active patch
list/apply APIs. This evidence comes from the supplied binaries, not the newer
FBNeo IPS reader. No original working pack was supplied to test end to end.

In **Game row ⋮ → IPS patches → Add patches → Browse legacy IPS .dat folder**,
select the set's directory containing its `.dat` and `.ips` files (or its parent
with a child named for the set). Pick one definition. For example, given actual
matching chip names and files in that folder:

```text
# example.dat
chip1.bin translation
chip2.bin color.ips
[en_US]
Translation and palette adjustment
```

Each chip must exist in the selected ZIP at the pinned FBNeo size and CRC. An
optional `CRC(12345678)` token is checked as a further constraint, not required.
Only local patch basenames or a prefix matching that same set are allowed.
Description sections are ignored. Patch files are read as bounded classic IPS;
no raw `.dat` goes to the native core. For **Apply selected**, profiles run in
the listed order. Later writes win at an overlapping offset. A variant holds at
most 8 IPS files (8 MiB/file, 16 MiB together), and at most 128 MiB of distinct
patched chips. Split-set chips absent from the ZIP are not patched by this tool.
The source files and normal save slots are unchanged.

## Repeat the audit / tests

Useful inputs: `assets/about.htm` and `assets/about_en.htm` (UTF-16), `res/xml/prefs.xml`,
`game_detail` and `game_setting_combikey` layouts, `classes.dex`, and the
`armeabi-v7a` ELF files. Use `aapt2 dump resources` and `aapt2 dump xmltree`,
`dexdump -d`, `readelf --dyn-syms`, `strings`, or `llvm-objdump -d` on extracted
copies. The local excerpts/dumps in `build/parity-audit/` are not build inputs
or tracked assets. For the wider engine and asset audit see
[privacy and core provenance](PRIVACY_AND_CORE.md).

- `tools/test.sh` covers saved layout migration, hit tests, combos and repeat,
  bounded skin file names and note bytes, IPS order and CRC/overflow failures,
  state isolation, and regressions in import, cheat, engine, filters and Link.
- `tools/smoke-core.sh` uses a synthetic Gridlee program to verify changes to
  both program and palette chips from one `.dat` pack in the pinned Linux core.
  The unchanged launch, P2 input, PCM, DIP, cheat, save/load and reset are tested.
- `tools/smoke-link.sh` verifies two independent patched Linux cores in lockstep.
  Signature/manifest/16 KiB checks are separate from the device tests in
  [Android acceptance](ANDROID_SMOKE.md). SAF, Android bitmaps and input/audio
  on a physical phone are not established by host tests.
