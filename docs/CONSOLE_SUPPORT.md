# Console and computer games

FBA Arcade uses the [FinalBurn Neo Libretro port at `2da48555`](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af). This is the core in the APK, not a list copied from another FBNeo build. Its [system selectors](https://github.com/libretro/FBNeo/blob/2da48555c13ce5d011edf2d6ec829b7393c070af/src/burner/libretro/README.md#emulating-consoles-and-computers) and [DATs](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af/dats) define the import index. The APK audit checks that the indexed driver names exist in its native core.

## Add a game

Use **Add games** (or **+ Add** in the library). Select a `.nes` file, another matching console file or an FBNeo set ZIP. If the file is missing in Android’s Recent view, open Downloads or the folder where you saved it. You don't need to make system folders, rename a loose ROM, or pick a core. A recognized game is shown with its system label. A correct FBNeo ZIP can use its bare set name or the system-prefixed name in the table.

| System | Common loose files | ZIP prefix | BIOS notes |
| --- | --- | --- | --- |
| NES / Famicom | `.nes`, `.unf`, `.unif` | `nes_` | None for ordinary cartridges |
| Famicom Disk System | `.fds` | `fds_` | Add `fdsbios.zip` with your game |
| SNES / Super Famicom | `.sfc`, `.smc` | `snes_` | Some games need a DSP or Seta chip ZIP, e.g. `dsp1.zip` |
| Game Boy Advance | `.gba`, `.srl` | `gba_` | `gba.zip` is optional; the core has a replacement |
| Sega Genesis / Mega Drive | `.md`, `.gen`, `.bin` | `md_` | Ordinary cartridges don't need BIOS |
| Sega 32X | `.32x` | `s32x_` | Cartridge sets |
| Sega Master System | `.sms` | `sms_` | Depends on the set |
| Sega Game Gear | `.gg` | `gg_` | Cartridge sets |
| Sega SG-1000 | `.sg`, `.sg1000` | `sg1k_` | Cartridge sets |
| PC Engine | `.pce` | `pce_` | HuCard games, not CD images |
| TurboGrafx-16 | `.pce`, `.tg16` | `tg_` | HuCard games, not CD images |
| SuperGrafx | `.pce`, `.sgx` | `sgx_` | Cartridge sets |
| Neo Geo Pocket / Color | `.ngp`, `.ngc` | `ngp_` | `ngp.zip` |
| Atari 2600 | `.a26`, `.bin` | `a26_` | Cartridge sets |
| Bally Astrocade | `.astro`, `.bin` | `astro_` | `astrocde.zip` |
| Fairchild Channel F | `.chf`, `.bin`, `.rom` | `chf_` | `channelf.zip` |
| ColecoVision | `.col`, `.cv`, `.rom` | `cv_` | `coleco.zip` |
| MSX1 | `.rom`, `.cas`, `.mx1` | `msx_` | `msx.zip`; USB/Bluetooth keyboard for games that need keys |
| ZX Spectrum | `.tap`, `.tzx`, `.z80` | `spec_` | `spectrum.zip`, `spec128.zip` or `spec1282a.zip`; USB/Bluetooth keyboard as needed |

Shared `.bin` and `.rom` suffixes are identified by content: NES magic for iNES cartridges, or size and CRC for other sets. Import the matching FBNeo ZIP for sets with more than one game file. If several drivers use identical bytes, the system-prefixed ZIP disambiguates. If a set needs a BIOS or parent, add the matching ZIP in **Add games**. Console BIOS ZIPs are dependencies, not playable rows. Owners supply their own copies; this app does not distribute games or original BIOS archives.

### If a selected file doesn't show up

After **Add games** or **Browse folder**, any skipped file appears in **Import results** with its filename (when provided by Android) and reason. The report also names the existing entry when the file maps to a game already listed. A valid game in a mixed selection still appears in **All games**, even if other files were rejected. BIOS files are counted separately. If you report an import problem, share the filename and the on-screen reason, not the ROM itself.

### Direct NES files

Select the extracted `.nes` directly; for supported layouts you don't need to rename it, make a special ZIP, or add a BIOS. A known FBNeo match still takes priority, keeping the old library and save identity. Add games can also recognize the iNES signature when the Android provider omits the filename or reports a generic `.bin` / `.rom` suffix.

The direct path accepts standard home NES/Famicom iNES and NES 2.0 layouts for mappers **0, 1, 2, 3, 4, 7, 9, 10, 11, 34, 66, 71, 87 and 94**. Version 0.5.8 adds MMC2, MMC4, Color Dreams, BNROM/NINA-001, Camerica, mapper 87 and UN1ROM. The declared program/graphics sizes and RAM layout must still be supported. NES 2.0 submapper 0 is supported for these boards, plus the CHR-ROM NINA-001 (34.1) and CHR-RAM BNROM (34.2) layouts. This does not enable other mapper variants, extended sizes, or specialty controllers. Some boards/dumps need FBNeo's per-game quirks; a matching set ZIP remains an option. UNIF and FDS continue to use matching sets.

If a `.nes` doesn't match a known set as-is, the importer can remove up to 512 trailing bytes of uniform `00` or `FF` filler beyond the declared image. It does this only on a private copy and checks again for an exact match. No declared ROM bytes or source files are edited, and truncated files, longer or non-uniform tails, and NES 2.0 files declaring extra ROMs do not gain a padding exception.

FBNeo's normal loader expects a named set archive. For a non-DAT `.nes`, the app keeps the cartridge in a private archive identified by SHA-256, and generates a one-cartridge RomData descriptor at launch. FBNeo's existing NES mapper code still verifies its size and CRC. The descriptor isn't supplied by the user and is removed with the session. Separate ROM identities keep battery data and states from mixing; re-adding the same cartridge bytes under a different filename or with supported padding keeps the save identity.

### Which dumps work?

Other systems and the normal FBNeo ZIP path still require the pinned set revision, even if a different dump or hack works in another emulator. Examples of exact matching sets:

| Game | Bare / prefixed ZIP | Loose bytes | CRC-32 |
| --- | --- | ---: | --- |
| Super Mario Bros. (World), NES | `smb.zip` / `nes_smb.zip` | 40,976 (includes header) | `3337EC46` |
| Super Mario World (USA), SNES | `supermarioworld.zip` / `snes_supermarioworld.zip` | 524,288 | `B19ED489` |
| Sonic the Hedgehog (Euro, USA), Genesis | `sonic.zip` / `md_sonic.zip` | 524,288 | `F9394E97` |

If a SNES file has an extra 512-byte copier header, the app only omits it from its **private copy** if the remaining bytes match a known SNES dump. Existing library ZIPs, including the NES names from 0.5.5, retain their save identity. Re-add an older bare console ZIP if 0.5.5 treated it as arcade.

One verified NES alternate dump, **Ninja Gaiden (USA)** (`ED1D282B`), imports as a distinct local variant. Its SHA-256 is checked at import and launch. Only that session uses the existing FBNeo patched-ROM path and its own battery/save identity; the ROM bytes and ordinary CRC checks are unchanged.

### Scope and testing

- Arcade ZIPs and arcade Link still use the existing flow. Console play is solo, with digital touch/gamepad controls, manual states and optional Quick Resume where the FBNeo driver supports them. MSX1 and Spectrum can also receive physical keyboard keys. No full touch keyboard, lightgun, mouse, or specialty controller support is claimed. Some titles may need input hardware not provided by the frontend.
- The core also includes the **Neo Geo CD, PC Engine CD / TurboGrafx-CD, and Sega CD / Mega-CD** drivers. **The app does not import CD images yet**: those need separate disc-image routing (including companion tracks when applicable), firmware and lifecycle tests. DAT cartridge support does not imply Game Boy / Game Boy Color, N64, or arbitrary games for other systems.
- `tools/smoke-console.sh` checks the production importer and session paths against the pinned, patched Linux core. Synthetic NES, SNES, Genesis, SMS, GBA and FDS fixtures verify video, PCM, digital inputs and save/load, including missing/added FDS BIOS and switching systems in one process. The direct NES fixtures cover all fourteen enabled mappers, the two mapper 34 layouts and both indexed and unlisted cartridges with padding. New board tests verify actual PRG and/or CHR bank switches before a Start response; the suite also covers reset, battery writes, PAL timing and rejection of mismatched CRCs. An optional local-file run verified the specific Ninja Gaiden alternate dump. These are host tests, not phone gameplay tests or a test of every dump in the DAT.
