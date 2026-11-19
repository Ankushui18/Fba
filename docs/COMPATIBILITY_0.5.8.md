# FBA Arcade 0.5.8 compatibility audit

**Verdict: 0.5.8 is not a 100% match for FBA4droid 1.77, FinalBurn Alpha Win32, or even the full feature set of its own FBNeo core.** Common arcade play and settings are available. NES, GBA and SNES have real Linux core launches, with the format limits below. An indexed ROM set is a candidate for import, not a tested game or a promise that it will play on a phone.

## What was compared

| Baseline | Evidence |
| --- | --- |
| **FBA Arcade Recovery Preview `0.5.8-link` (13)** | The [signed ARM64 APK and corresponding source](https://github.com/Ankushui18/Fba/tree/795f9695b65336d84d47838c306f9be7640d84c5). SHA-256 `7eff351f2b6b933681c14e6cc7b2277224cc789d7172656d7ad50a8834b96218`. Requires Android 8+ (API 26). The APK checksum, v2/v3 signatures, 16 KiB alignment, asset bytes and core name strings passed checks. |
| **Supplied FBA4droid 1.77 (20200306)** | `com.joyemu.fbaapp`, `armeabi-v7a`, min API 14, SHA-256 `9099a973c4bab3c8634583e5d3630f8f0642d7d3c993e698e6ea64120f36f775`. Inspected settings XML, menus/layouts, DEX, help and ARM libraries. Not installed. Resource strings and help alone are not proof that a feature runs. |
| **FinalBurn Alpha 0.2.97.44 for Windows** | Archived [official source at `41694815`](https://github.com/barry65536/FBAlpha/tree/416948158d88ba17f87813419ddc3c1209ef68c0): `src/burn/version.h`, `src/burner/win32/` and `fbahelpfilesrc/`. This is the desktop baseline, not the Android APK or the later Neo fork. Some menus are driver/build dependent. |
| **FinalBurn Neo Libretro 1.0.0.03** | [Exact `2da48555` pin](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af), its DATs, `retro_common.cpp`, `libretro.cpp`, `retro_input.cpp` and the [packaged session patch](../android/patches/fbneo-session.patch). The Android app implements its own Libretro frontend, not the Win32 or RetroArch frontend. |

**Present** means an app path exists; **partial** means there is a counterpart but not the same behavior, formats or controls. The tables do not imply phone testing or identical emulation accuracy. No side-by-side Android run was possible.

## FBA4droid 1.77 → Android 0.5.8

### All 21 global preference keys in `res/xml/prefs.xml`

| Old option | 0.5.8 equivalent and difference |
| --- | --- |
| `enableAudio` — Enable Audio | **Present:** Display & audio → Sound. |
| `renderMode` — GPU Render | **Partial:** GPU canvas (next launch), not the legacy GL renderer. |
| `renderQuality` — High Render Quality | **Partial:** Sharp, bilinear and HQx controls; no separate legacy GL quality toggle. |
| `filterMode` — None, xBRZ, HQx | **Partial:** Sharp, bilinear, HQ2x/3x/4x; **no xBRZ**. |
| `autoMultiThread` — Multi-threading | **Missing:** no old thread-policy setting. 0.5.8 has its own presentation worker. |
| `screenOrient` — Auto, both portrait and landscape directions | **Present:** Follow device, Portrait, Reverse portrait, Landscape, Reverse landscape. |
| `screenMode` — Screen mode | **Present, different layout:** player viewport sizing and full-screen portrait. Not identical system-bar or layout behavior. |
| `enableVPad` — Virtual pad | **Present:** Controls → Touch controls. |
| `vpad_style_dialg` — Virtual pad style | **Partial:** four built-in skins, plus supported legacy PNG folders with previews and fallbacks. No legacy layout INI import. |
| `vpadPlayer` — Show player button | **Present:** show selector or choose touch player 1–4 during non-Link play. |
| `vpadAutoLayout` — Auto layout | **Present:** Custom, Auto (game buttons), Compact (fixed). Uses available digital descriptors. |
| `PREF_VPAD_ANIMA` — Pad animation | **Present:** Button animation switch. |
| `analogPretty` — Smooth stick effect | **Missing:** no separate legacy stick-animation/smoothing option. The pad animation switch is not this control. |
| `analogDeadzoneNEW` — Stick dead zone | **Present:** pad editor dead-zone slider, for digital directions. Not true analog input. |
| `analogExpandzoneNEW` — Stick expand zone | **Present:** separate stick touch-area slider. |
| `buttonExpandzoneNEW` — Button expand zone | **Present:** separate button touch-area slider. |
| `enableVibrator` — Touch vibration | **Present:** touch Haptic feedback (Android system behavior applies). Not emulated controller rumble. |
| `enableOneController` — One controller for multiple players | **Present:** Mirror to all local players. Not applied in Link. |
| `key_map_config` — Physical mapping | **Present:** Android key remapping, including EXT1–4; no import of old bindings. |
| `PREF_IPS_DETAIL2` — Show IPS detail info | **Missing as a toggle:** variants have patch names and image previews, but legacy `.dat` description blocks are not rendered. |
| `key_reset_config` — Reset All Configure | **Partial:** resets app/game settings while retaining ROMs, saves, library data and the remembered ROM folder. Not a migration or reset of the old APK. |

### Game and library menus

| Area | 0.5.8 result |
| --- | --- |
| Browse, search, board/category, English/Chinese names, Favorites, Recent, game info | **Present, different storage:** list/grid, filters, cover customization and a remembered Android folder. Browse inside the granted tree; play private copies, not arbitrary paths. Old app database/preferences, shared-file rename/delete workflows and installed data do not migrate. |
| Resume, Close, Restart, Snap, FPS | **Present:** pause/resume, Exit game, restart, unfiltered PNG capture and export, separate game/video FPS. A snapshot is not video or input recording. |
| Save / Load and game state list | **Partial:** six named slots, previews, delete and launch from a slot. State headers bind the core and game/variant. **Old FBA4droid/Alpha states are not supported**. The app's Backup ZIP is for this app's slots and settings, not old states, ROMs or battery saves. |
| Screen menu and per-game/default settings | **Present:** Fit, Fill, Original 1×, integer, 4:3/3:4; Off/1–4/Auto render skip; global and per-game overrides. Render skip drops presentations, not emulation steps. |
| Virtual layout and key menu | **Present:** independent portrait/landscape position, size, hiding and opacity; A–F and Combo 1–4 masks, physical EXT1–4, hold-autofire and adjustable rate. Turbo timing, stick feel and gestures are not certified identical. |
| Cheats and DIP switches | **Partial:** driver-published choices and bounded supported `.ini`/ZIP imports; can paste compatible INI. Some changes need restart; active cheats start off. Not every legacy `.cheats` entry or a complete cheat-search tool. |
| IPS tree, preview and Apply Selected | **Partial:** same-size, same-set chip patches, simple legacy `.dat` mapping, order-preserving combined variants and separate saves. No IPS32, resizing, arbitrary original `.ini` activation lists, description blocks, or full pack parity. Console IPS is not exposed. See [format limits](FBA4DROID_PARITY.md#legacy-ips-subset). |
| Local notes and skins | **Partial:** import visible text from `.rominfo` HTML/TXT and compatible PNG artwork. No HTML images, scripts, active links, or automatic scanning of old hidden directories. |
| Legacy `.fba` cache / `.romdata` board overrides | **Missing:** reject E680 `.fba` and do not import user board descriptors. The generic NES path generates its own private descriptor; it does not enable that legacy format. |
| Wi-Fi / Bluetooth netplay | **Partial:** opt-in P1/P2 **arcade Link** for identical ROM/dependency bytes and compatible Link builds, over private IPv4 or paired Bluetooth. No interoperability with 1.77 or Kaillera, no console Link. Link has no save/load, IPS, cheat or DIP changes. Linux loopback passed; Android Wi-Fi/Bluetooth untested. |
| “More”, external resources, feedback, updates, ads and old SDK services | **Intentionally not reproduced**, including the updater and analytics. Normal play is offline. See [privacy and source](PRIVACY_AND_CORE.md). |
| Preview additions | Optional one-game **Quick Resume** at explicit solo exit, non-overwriting manual-slot Backup ZIP restore, and per-file **Import results** with new/already-listed/BIOS/rejected counts. These are not Alpha compatibility modes. |

### ROM and core coverage are not the same as UI parity

- The 1.77 APK embeds `libfba.so` reporting **0.2.97.44** and `libfbas.so` reporting **0.2.97.08**. The current APK embeds the later **FBNeo** port, not either library or a selectable Alpha core. Statistically, 7,385 of 8,372 and 382 of 802 respective exported `BurnDrv*` symbols match source definitions at the FBNeo pin. That is name overlap, not matching code, states or ROM bytes.
- **3,361 of 4,900 unique legacy catalog names** appear in the pinned **8,556-name arcade DAT**. This is *only* a short-name comparison. The legacy catalog has no chip CRCs; different names, console sets and clones can affect the count, and even matching names may require different ROM bytes. It is not a playable percentage.
- The archived Alpha driver tree contains console families including SNES, Mega Drive, Sega 8-bit, ColecoVision, MSX, PC Engine and Spectrum, but not the pinned FBNeo NES/GBA drivers. The supplied FBA4droid DEX and two ELF inventories also do not establish an NES or GBA route. **0.5.8's NES and GBA paths come from FBNeo, not from copying FBA4droid.**

## Desktop FinalBurn Alpha → Android 0.5.8

Desktop menu evidence: [Game](https://github.com/barry65536/FBAlpha/blob/416948158d88ba17f87813419ddc3c1209ef68c0/fbahelpfilesrc/menu_game.htm), [Video](https://github.com/barry65536/FBAlpha/blob/416948158d88ba17f87813419ddc3c1209ef68c0/fbahelpfilesrc/menu_video.htm), [Audio](https://github.com/barry65536/FBAlpha/blob/416948158d88ba17f87813419ddc3c1209ef68c0/fbahelpfilesrc/menu_audio.htm), [Input](https://github.com/barry65536/FBAlpha/blob/416948158d88ba17f87813419ddc3c1209ef68c0/fbahelpfilesrc/menu_input.htm), [Misc](https://github.com/barry65536/FBAlpha/blob/416948158d88ba17f87813419ddc3c1209ef68c0/fbahelpfilesrc/menu_misc.htm), plus the [archive loader](https://github.com/barry65536/FBAlpha/blob/416948158d88ba17f87813419ddc3c1209ef68c0/src/burner/zipfn.cpp) and Win32 source.

| Desktop options | Available / missing in 0.5.8 |
| --- | --- |
| Load/exit, Recent, ROM paths, support paths, `.zip`/`.7z` | Load, exit, Recents and a granted folder with private copies. **No `.7z` import**, multiple desktop ROM search paths or general support-directory configuration. Not the Alpha ROM revisions. Library search and filters do not replace the desktop parent/clone tree, working-status filters or pre-launch ROM/sample audit. |
| Pause, state files, slots, last-save undo, restore-full-state, `.fs` and `.fr` associations | Pause and app-specific slots only. No Alpha file load, last-save undo, full-state switch or desktop associations. No fast-forward gameplay shortcut; render skip does not speed up emulation. |
| Replay input, record input, AVI output/size, audio logging | **Missing** input replay/recording, AVI and WAV/audio logging. PNG screenshots are present. |
| Kaillera; Neo Geo multi-slot; Neo Geo CDZ select/start disc | **Missing** all three. Link is a different network protocol; Neo Geo arcade cartridges are not CDZ. |
| Create/select/insert/eject memory card; high score saving option | **Missing** desktop memory-card management and configurable FBNeo card mode; no `hiscore.dat` import or high-score toggle. A game's own battery-backed/NVRAM data is a different feature. |
| CPU speed and reset; input plugin, autofire rate, map individual game inputs, DIPs | Digital remap, rate and DIPs are available. **No** CPU clock override, desktop input plugin selector or arbitrary cabinet-input mapping. True analog axes, rotary/trackball/mouse and lightgun devices are not forwarded. |
| Blitter/effects, window/fullscreen mode, scaling, orientation | Android scaler, GPU canvas, five orientations, original size, render skip and HQx. **No** DirectX blitter/plugins, monitor/refresh/resolution/bit-depth control, gamma, CRT/RGB/scanline or phosphor presets, triple buffer/VSync/DWM switches, projection or motion blur menus. Limited rotation choices via the core are not those desktop modes. |
| Sound plugin, buffer/sample rate, interpolation, filter, reverb | Sound on/off with Android audio; **no** plugin/buffer/resampling control, FM/sample interpolation sliders, low-pass or reverb menus. |
| Enable cheats, cheat search/value comparisons/dump, palette viewer, Shot Factory and debugger | Compatible `.ini` cheats, snapshot and previews only. **No** Nebula/Kawaks `.dat` cheat import, memory search/dump, layer inspection or palette/debugger tools. |
| Desktop IPS manager with patch categories, languages, descriptions and preview | Variant list, preview and simple `.dat` subset only; no desktop manager or per-language descriptions. |
| DAT generation/command-line tasks, UI/gamelist languages, skins/icons, `.ini` save/reload | Indexed DAT metadata is bundled, not generated in-app. English/Chinese **titles**, app/virtual-pad presentation and app-specific settings backup. No desktop CLI, localization packs, UI skin engine, DAT export or desktop `.ini` settings/input-presets interchange. |
| Auto pause, keyboard/Num Lock, thread priority, timer, alpha-blend/gear-shift indicators, support folders | App lifecycle and file handling are automatic. These separate desktop switches are not exposed. |

## Pinned FBNeo Libretro → Android option coverage

The [upstream definitions](https://github.com/libretro/FBNeo/blob/2da48555c13ce5d011edf2d6ec829b7393c070af/src/burner/libretro/retro_common.cpp) have **50 fixed option keys**; **37** of those key strings are found in the packaged `libretro.so`. This is a static inventory, not a denominator for overall parity. The source also generates options per driver, and some fixed choices need a particular driver or frontend callback.

[`captureOptions`](../android/src/main/cpp/engine.cpp) accepts visible `dipswitch`/`cheat` entries plus **only two fixed keys**: `fbneo-vertical-mode` and `fbneo-diagnostic-input`, under **Pause → DIP switches** when the core publishes them. The options are for the active session, not a global core-settings panel. Categorized DIP choices may include a driver BIOS setting; that does not expose the separate `fbneo-neogeo-mode` selector.

| Core option group | 0.5.8 behavior |
| --- | --- |
| Vertical mode, service/diagnostic shortcut; dynamic DIP and cheat choices | Exposed when published/visible; DIP/cheat behavior tested on a host arcade fixture. |
| `fbneo-allow-patched-romsets` | Internally scoped for validated IPS sessions and the one verified NES alternate. **Not** an unrestricted user-facing CRC bypass. |
| CPU speed, force 60 Hz, resolution, 32-bit depth, fixed and audio-buffer frameskip/threshold | **Not exposed**. Audio-buffer controls also depend on an environment callback this frontend does not provide. App render skip and filter are separate. |
| Audio sample rate, sample/FM interpolation, low-pass, high scores | **Not exposed**. No user `hiscore.dat` or separate audio-sample-pack import. Hidden options are not necessarily disabled; the core can keep its defaults. |
| Neo Geo MVS/AES/UniBIOS mode, memory card mode, 16 Neo Geo debug DIP bits | **Not exposed** as those options. Ordinary driver DIPs and matching cartridge BIOS dependencies are separate. |
| Analog speed, SOCD (opposing directions), rumble, gun crosshair | **Not exposed**. Thumbstick motion is reduced to digital directions. `engine.cpp` polls digital joypad plus keyboard only; touch haptics do not implement core rumble. No complete controller-device selection, specialty keypad or touch keyboard. |
| Dynamic PGM2 card options; core RomData and IPS selection | Not forwarded as general option groups. App-managed IPS and internally generated NES descriptors are narrower paths. |
| Cyclone, 4 layer and 8 sprite debug keys | **Not found** in this ARM64 core binary. These 13 source definitions are conditional. |
| Rewind, run-ahead/preemptive frames, RetroArch recording/netplay | **No frontend implementation**. Save states and Quick Resume do not implement those features. |
| CD subsystems/disc swapping | `neocdz`, `pce_scdsys`, `md_segacd` name strings exist, but no Android disc route, track staging, swap or BIOS workflow for CD. See below. |

## Console formats and launch evidence

The signed APK has **21,539 game set rows and 20 BIOS rows across 19 non-arcade systems**. Counts include revisions/clones. The verifier checked that each indexed driver **name string** exists in `libretro.so`; it did not run that set. The pinned source defines 22 console/computer subsystem selectors (including three CD families). The app implements the following 19 game import routes. **Host** = at least one representative run through the production import/staging path and patched Linux core. **Index** = route + index + native string checked, but not launched in this audit.

| System | Common loose extensions | Game sets | BIOS/dependency notes | Launch evidence |
| --- | --- | ---: | --- | --- |
| NES / Famicom | `.nes`, matching `.unf`/`.unif` | 2,965 | No BIOS for ordinary carts | **Host**, including 14 direct mappers and one verified alternate |
| Famicom Disk System | `.fds` | 207 | Matching `fdsbios.zip` | **Host**; without BIOS correctly fails |
| Game Boy Advance | `.gba`, `.srl` | 3,285 | `gba.zip` optional; core replacement tested | **Host** |
| SNES / Super Famicom | `.sfc`, `.smc` | 2,709 | Some sets need DSP/Seta firmware such as `dsp1.zip` | **Host**; specialty chip variants not all tested |
| Genesis / Mega Drive | `.md`, `.gen`, `.bin` | 3,004 | Ordinary carts do not need a BIOS | **Host** |
| Master System | `.sms` | 897 | Depends on set | **Host** |
| Game Gear | `.gg` | 638 | Cartridge sets | **Index** |
| Sega SG-1000 | `.sg`, `.sg1000` | 259 | Cartridge sets | **Index** |
| Sega 32X | `.32x` | 59 | Cartridge sets | **Index** |
| PC Engine | `.pce` | 373 | HuCard, not CD | **Index** |
| TurboGrafx-16 | `.pce`, `.tg16` | 100 | HuCard, not CD | **Index** |
| SuperGrafx | `.pce`, `.sgx` | 10 | Cartridge sets | **Index** |
| Neo Geo Pocket / Color | `.ngp`, `.ngc`, `.npg` | 205 | `ngp.zip` | **Index** |
| Atari 2600 | `.a26`, `.bin` | 505 | Cartridge sets | **Index** |
| Bally Astrocade | `.astro`, `.bin` | 100 | `astrocde.zip` | **Index** |
| Fairchild Channel F | `.chf`, `.bin`, `.rom` | 49 | `channelf.zip` | **Index** |
| ColecoVision | `.col`, `.cv`, `.rom` | 678 | `coleco.zip`; no dedicated keypad mapping | **Index** |
| MSX1 | `.rom`, `.cas`, `.mx1` | 1,838 | `msx.zip`; physical keyboard for games needing keys | **Index** |
| ZX Spectrum | `.tap`, `.tzx`, `.z80` | 3,658 | Correct `spectrum.zip`, `spec128.zip` or `spec1282a.zip`; physical keyboard as needed | **Index** |

### What to add in 0.5.8

- **Arcade:** use exact FBNeo set ZIPs, with the matching parent and BIOS (e.g. `neogeo.zip` when required). CPS/PGM/Neo Geo coverage does not mean every old Alpha ZIP works. Copying a ZIP to the library can pass structural checks while the core refuses to launch it. Do not rename or repack a wrong revision and expect it to work.
- **Ordinary NES:** use the extracted `.nes` in **Add games**. Exact indexed matches come first. For other home iNES/NES 2.0 carts, the direct path allows mappers **0, 1, 2, 3, 4, 7, 9, 10, 11, 34, 66, 71, 87, 94**, subject to PRG/CHR/RAM layout rules. NES 2.0 submapper 0 plus mapper 34.1 (CHR ROM) and 34.2 (CHR RAM) are supported; no extended sizes, misc ROMs or specialty controllers on this path. Mapper 13 or 30 is not enabled for generic loose play. A matching indexed set, where available, is a different path. Version 0.5.8 can trim up to 512 bytes of uniform `00` or `FF` *after* the declared image in a private copy; the original is never edited. UNIF/FDS still require an indexed match.
- **GBA, SNES and the other indexed systems:** use a recognized single-file dump (actual size + CRC, not simply the extension) or a matching FBNeo ZIP with the correct set name and complete required data. Shared `.bin`/`.rom` can route by content. For a same-name collision, use a system prefix such as `gba_`, `snes_` or `md_` with the **correct** set name. Multi-file sets generally need their set ZIP. Some SNES copier headers are skipped in the private copy only if the remaining bytes match. Not a generic loader for every `.gba`/`.sfc` hack or dump.
- **Unavailable:** Game Boy/GBC (`.gb`/`.gbc`) are not GBA here. No GB/GBC, N64, DS, PlayStation, or broad MAME compatibility claim. **No Neo Geo CD, PC Engine CD/TurboGrafx-CD or Sega CD/Mega-CD**: no CUE/ISO/CHD/CCD/M3U or corresponding CD ZIP route. `.7z`, `.rar`, old `.fba` and arbitrary `.romdata` are not playable imports. For a general download archive containing legal loose files, extract the correct ROM first; a ROM-set ZIP follows its own naming/CRC rules.
- **Import diagnostics:** both **Add games** and the folder browser give **Import results** on skipped/mixed selections. Open Downloads or the actual folder if Android's **Recent** view hides the file. A detected iNES signature can handle a missing/generic filename in the file picker; this is not a universal format sniffer. Add games takes up to 64 files per batch, the folder UI lists up to 256 entries per folder, and console scanning caps loose files at 64 MiB (plus the eligible SNES header) and set ZIPs at 80 MiB. Generic NES has a tighter layout/size validator. For details and matching examples see [Console support](CONSOLE_SUPPORT.md).

Console games run outside the two-device Link mode; local digital ports are usable only where the driver maps them. No console cable/link emulation, complete touch keyboard or specialty input suite is verified. No ROMs or original BIOS archives are supplied.

## Test results and next priorities

- **Passed again:** 15 host suites, APK checksum/signature/package checks, **26 successful Linux console launches** (including repeats and the optional exact local Ninja Gaiden alternate), expected wrong-data/missing-BIOS failures, and Linux arcade and two-process Link smoke. Console checks include video, PCM, mapped controls, state round trips and system switches. Selected fixtures also exercise bank switches, PAL, battery and reset. Arcade coverage includes P2, DIP/cheat changes and multi-chip IPS isolation on a synthetic board fixture. These are not 26 distinct console games, runs of the ARM64 APK, or full playthroughs.
- **Not verified:** fresh install/update, file-picker/provider behavior, controller/keyboard routing on a phone, audio/display performance, 16 KiB device runtime, real Bluetooth/Wi-Fi Link, and every set/BIOS/revision. Follow [Android acceptance](ANDROID_SMOKE.md). A useful first run: direct NES, known GBA, SNES, FDS BIOS fail/pass, Neo Geo cartridge, physical controller and two-device arcade Link. Share the phone/Android version and **Import results** reason if a legally held file is skipped, not the ROM.
- **Recommended order if expanding parity:** first run the device checklist. Then expose a tested, per-driver subset of the core settings (Neo Geo mode/card, high scores with local data, CPU/timing/audio), respecting state compatibility and Link determinism. Add native analog and special inputs, then CD-with-BIOS/track staging. Separately prioritize any legacy migration samples, input replay/recording and desktop debug tools that are actually needed. Do not enable a global CRC bypass or reintroduce trackers to match old menus.

## Reproduce the inventory

Run from the repository root with Python 3, a Git checkout of the FBNeo pin (`.cache/FBNeo-libretro` by default), and the Android build tools for APK verification. The optional legacy input is local only; omit those arguments if you don't have the APK.

```sh
apk=artifacts/fba-arcade-0.5.8-preview-link-arm64-v8a-test.apk
core=.cache/FBNeo-libretro
python3 tools/audit_compatibility.py "$apk" "$core"
# Optional: inventory the legacy preference keys and compare core/catalog names.
python3 tools/audit_compatibility.py "$apk" "$core" \
  --legacy-apk fba4droid_18644.apk --aapt2 /path/to/build-tools/aapt2
python3 tools/compare_legacy.py fba4droid_18644.apk "$core" "$core"
tools/verify-apk.sh "$apk"
tools/test.sh
```

For runtime checks, pass a compatible patched Linux core with the tested drivers compiled in to `tools/smoke-console.sh`, `tools/smoke-core.sh` and `tools/smoke-link.sh`. They use synthetic data by default. The 26-launch run included the optional local NES input; it is not distributed by the project. For the previous 0.4.0/0.5.0 comparison see [historical FBA4droid parity](FBA4DROID_PARITY.md).

**Free for personal, non-commercial play. Not for sale.** This is an independent frontend. FinalBurn Alpha/Neo and third-party code are not ours; see [licenses and corresponding source](../LICENSE.md).
