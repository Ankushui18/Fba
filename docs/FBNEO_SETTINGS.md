# FBA Arcade 0.5.9 — FBNeo settings

This is the first implementation step after the [0.5.8 compatibility audit](COMPATIBILITY_0.5.8.md), not full FBA4droid, desktop FinalBurn Alpha, or FBNeo/RetroArch parity. The app uses the same FBNeo pin and ROM checks. NES, GBA and the other [console routes](CONSOLE_SUPPORT.md) do not gain or lose cartridge formats in this build.

## Use the settings

Start a regular solo game and open **Pause → FBNeo settings**. A changed row is marked **Next launch**. **Save**, exit, then tap the game in the library. **Restart** inside Pause resets the active game, not its boot settings. **Defaults** in the panel is pending until you save. If a saved setting prevents launch, **Game row ⋮ → Reset FBNeo settings** restores that game's defaults without deleting data.

| Section | Setting | When it matters |
| --- | --- | --- |
| Machine | **CPU clock** | Sets the emulated CPU factor on drivers that implement it. It is not fast-forward or a CPU setting for the phone. A change can affect gameplay or stability. |
| Machine | **Save high scores** | Shown for drivers with high-score support. Requires [the exact upstream `hiscore.dat`](../README.md#high-score-setup) and a matching table. Off by default. Use **Exit game** for the driver to write scores. |
| Video | **Force 60 Hz** | For games whose source refresh rate is above 50 Hz. Can change pacing; it does not convert 50 Hz PAL games. |
| Video | **32-bit color when available** | Uses the wider pixel format only where the core supports it. Off by default, as in 0.5.8. |
| Video | **Core frameskip** | Fixed skip at the core; audio and game steps continue. No auto/buffer-driven core frameskip. Presentation frameskip in **Display** is separate and counts only frames supplied by the core. |
| Audio | **Sample rate, sample interpolation, FM interpolation, low-pass** | PCM rate request is 44.1 or 48 kHz; the actual rate can differ slightly at the native refresh rate. Interpolation affects only the relevant sound chips. |
| Input | **Opposing directions (SOCD)** | Sets direction priority where the driver uses it, e.g. when two opposite keyboard/gamepad inputs arrive together. Not analog input. |

**Cheats and DIP switches** stay in their existing in-session panels. **Link** and **IPS variants** do not use solo FBNeo profiles. The alternate NES launch path still permits only its isolated overrides; there is no general CRC bypass.

### States and backup

An unchanged profile keeps the old state fingerprint and format. A custom profile is tied to the state alongside the ROM/IPS revision and core pin. To load an earlier state, reselect the same boot settings used when saving it, or reset to Defaults if saved with stock settings. A mismatch is rejected without deleting the state. If you exit with changed settings pending, the old Quick Resume is not overwritten; start normally to apply the new ones.

Manual state ZIP backups carry portable per-game profiles. They don't copy the high score DAT or score files, battery saves, ROMs, BIOS or Quick Resume. Import the DAT separately on the destination device if needed.

## Verified

- **Runtime on Linux, pinned patched core:** GBA switched from **59.72 Hz** with about 48 kHz PCM to **60 Hz / 44.1 kHz**, produced the expected PCM count, saved/loaded and returned to stock timing on reopen. Synthetic Genesis confirmed an increased CPU work count and neutral vs last-input direction priority, then restored defaults.
- Synthetic Gridlee verified **32-bit colors**, **4 new draws in 12 steps** with fixed core frameskip and continuous PCM, and different waveform levels with the low-pass filter. A subsequent Link boot got stock settings. Cheat/DIP writes, reset, P2 input and multi-chip IPS isolation still worked.
- Synthetic 1942 with the pinned high score DAT wrote a `.hi`, reloaded its score into RAM and reapplied it after reset. It ignored truncated and oversized score files, did not save without the DAT, and did not load solo scores in Link. No original game or firmware was used for this test.
- **16 host suites** and the four Linux smoke scripts passed, including the NES routing/bank-switch tests, exact local Ninja alternate (optional), GBA/SNES/MD/SMS/FDS and two independent Link peers. A mixed 0.5.8/0.5.9 frontend Link check against the same patched host core passed. A 0.5.8 native Gridlee snapshot loaded under default settings with matching six-frame AV replay on the host. These are fixture results, not a per-game audit.
- **APK:** Preview package/signer continuity, v2/v3 signatures, ARM64/16 KiB ELF alignment and payload checks passed. No phone or tablet was used. Follow [Android acceptance](ANDROID_SMOKE.md) for the actual picker, app update, UI, audio and Wi-Fi/Bluetooth checks.

To repeat the native checks, build the [pinned Linux core with both patches](LINK_PLAY.md#host-test) and run `tools/smoke-{core,console,hiscore,link}.sh` with its `.so` as the argument. The fixture suite needs the corresponding drivers compiled in. `tools/test.sh` also checks the boot allowlist, offline DAT validation, profile-aware states, Quick Resume and backup settings.

## Still open

- Neo Geo BIOS selection and memory-card workflows, vector resolution, and other core options that need driver-specific validation.
- Real analog and specialty inputs; CD media, BIOS and disc changes; console Link and broader import formats.
- Legacy state/cache migration and desktop-only functions such as input recording, fast-forward controls and cheat search. These have distinct compatibility requirements; adding a settings row does not verify them.

Existing ads, tracking and online updater behavior are intentionally not matched. The project remains free for personal, non-commercial play; see [licenses and source](../LICENSE.md).
