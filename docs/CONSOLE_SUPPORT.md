# Console and computer games

FBA Arcade uses the [FinalBurn Neo Libretro port at `2da48555`](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af). This is the core in the APK, not a list copied from another FBNeo build. Its [system selectors](https://github.com/libretro/FBNeo/blob/2da48555c13ce5d011edf2d6ec829b7393c070af/src/burner/libretro/README.md#emulating-consoles-and-computers) and [DATs](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af/dats) define the import index. The APK audit checks that the indexed driver names exist in its native core.

## Add a game

Use **Add games** (or **+ Add** in the library). Select matching console files or FBNeo ZIPs exactly as you would select arcade ZIPs. You don't need to make system folders, rename a loose ROM, or pick a core. A recognized game is shown with its system label. A correct FBNeo ZIP can use its bare set name or the system-prefixed name in the table.

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

Shared `.bin` and `.rom` suffixes are identified by size and CRC, not their name. Import the matching FBNeo ZIP for sets with more than one game file. If several drivers use identical bytes, the system-prefixed ZIP disambiguates. If a set needs a BIOS or parent, add the matching ZIP in **Add games**. Console BIOS ZIPs are dependencies, not playable rows. Owners supply their own copies; this app does not distribute games or original BIOS archives.

### Which dumps work?

Most files must match the **pinned FBNeo** revision, even if a different dump or hack works in another emulator. Examples:

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
- `tools/smoke-console.sh` checks the production importer and session paths against the pinned, patched Linux core. Synthetic NES, SNES, Genesis, SMS, GBA and FDS fixtures verify video, PCM, digital inputs and save/load, including missing/added FDS BIOS and switching systems in one process. An optional local-file run verified the specific Ninja Gaiden alternate dump. These are host tests, not phone gameplay tests or a test of every dump in the DAT.
