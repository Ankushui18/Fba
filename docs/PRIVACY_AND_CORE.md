# Legacy APK comparison and offline build audit

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
  v0.2.0 reuses only vetted static artwork from the original.

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

The new APK compiles **FBNeo 1.0.0.03** from the upstream-linked Libretro port at
[`2da48555`](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af)
(2026-11-18). It links a new JNI bridge and Android frontend. No compiled
code or emulator frontend source from the old APK is used. The main project
and the Libretro port are pinned separately: the former is the comparison
reference, the latter has the Android build and libretro frontend ABI.
Reproduce the comparison:

```sh
# With the two clean checkouts at the above commits:
python3 tools/compare_legacy.py fba4droid_18644.apk /path/to/FBNeo /path/to/FBNeo-libretro
```

## Artwork in v0.2.0

The redesigned app reuses 152 original images: 76 images from the complete
`skin_default` and `skin_1` sets, and 76 from app-specific drawable and mipmap
resources. The displayed controllers use the original normal and pressed
frames, not generic shapes. The new UI code handles layout, button mapping,
input and settings; recent filenames and settings stay in app-private data.
There is no legacy game icon archive, updater UI, QR link, WebView, or netplay
code. `tools/import_legacy_art.py` records each image's source path, verifies
the legacy APK hash, and writes only the decoded RGBA pixels to PNG. The APK
checker requires the explicitly listed filenames and validates the PNG headers
and chunks. The two v0.2.0 native libraries match those of the published
v0.1.0 APK byte for byte.

## New build checks and remaining work

- The signed ARM64 test APK passes APK v2/v3 signature verification, zero
  manifest permissions/only launcher, archive allowlist and CRC, legacy tracker
  marker checks, native dependency/import checks, **16 KiB alignment** for
  both in-APK library offsets and native ELF segments, non-executable stacks,
  and no writable executable ELF segments. These checks are enforced by
  `tools/verify-apk.sh` / `tools/check_offline_apk.py`; no AV verdict is claimed.
- The shell build uses tools available inside a network-restricted sandbox:
  Android SDK 37.0/Build Tools 36.1.0 from pinned
  [`whynusn/android-sdk-bundle@3dd1d84`](https://github.com/whynusn/android-sdk-bundle/tree/3dd1d84b2a76870a5995107613af83f6114450ae)
  (bundle checksums verified), Temurin 17.0.20.1 (digest matches the upstream
  Temurin release), and NDK 23.0.7599858 from the pinned
  [Chromium mirror](https://github.com/chromium-full-mirror/android_ndk/tree/310956bd122ec2b96049f8d7398de6b717f3452e).
  The linker is explicitly configured for 16 KiB. Rebuild with official SDK
  packages and a current NDK and release key before public distribution.
- Host tests cover ZIP import and rejection, layout across screen sizes,
  simultaneous directional and action inputs, and the native frontend with
  a mock libretro core. The pinned core also passed repeated unknown-set and
  missing-ROM load checks in its Linux build (`tools/smoke-core.sh`). No
  physical Android device is connected. On a device, test the in-place update,
  both skins and pressed states, rotate during play, pause/resume, and check
  touch and gamepad input with a known-good ROM. Repeat on a 16 KiB device.

| Signed ARM64 test build | APK SHA-256 |
|---|---|
| v0.2.0 (versionCode 2) | `2e6b5d7ff292602e3574d0cdc5220cdfe8ef87be0f847243c89b1f90c4b5aa06` |
| Published v0.1.0 (versionCode 1) | `8d13b5ff5b563da0bf0af7415cb9520914622470fd978f3652ef45b02f16c930` |

Both have test certificate SHA-256
`ce12cfdadf51d70c95bb24825b58d1af017acf8d23618ee74aaf781aa0d37cd9`
and the same application ID. They do not have the legacy app's signing key.
