# FBA Offline

An independent, offline Android arcade frontend for FinalBurn Neo. This is a new
app, not a modification or a signed update of `com.joyemu.fbaapp`.

> `fba4droid_18644.apk` and `fba4droid_18644.zip` are the **unchanged legacy
> inputs** for comparison. They are not the privacy-first build and should not
> be installed as one. The ZIP is packed APK content, not source code.

## Install and play

- [Download the signed v0.1.0 ARM64 test APK](artifacts/fba-offline-0.1.0-arm64-v8a-test.apk)
  from this branch. The minimum is Android 8 (API 26) and the target is
  Android 17 (API 37).
- Install **FBA Offline** as a separate app. Its app ID is
  `io.github.ankushui18.fbaoffline`; its signing key, settings and saves are not
  the original app's. There is no legacy save-state migration.
- In **Import ZIP**, pick arcade ROM sets you legally have. Keep the original
  FBNeo set names, including any required parent or BIOS ZIPs. Pick files from
  on-device storage. No ROM/BIOS archives are bundled or downloaded. An old
  FB Alpha set with the same name may have incompatible file hashes.
- The library lists local ZIPs; **Play** starts a set, and **Coin** / **Start**
  work with touch controls or an Android gamepad. A keyboard can use arrows,
  Z/X/A/S/Q/W, Space and Enter. Audio, single-player digital controls, and
  core-managed NVRAM are supported. This first build does not offer imported
  cheats, old save states, online play, or a frontend for disc images.

## Privacy and scope

The app has **no Android permissions**, including no `INTERNET` or wide storage
access. The system document picker grants access only to selected files; the
app copies archives to private, non-backed-up storage. It does not send files
or identifiers, use a WebView, or contain the old Baidu links, AppConnect,
Tencent Bugly, or updater code. ZIPs are bounded and checked before being
handed to FBNeo; use only ROM sets you trust. This is not a guarantee against
all malware or all defects in native ROM parsing.

See [the privacy and FBNeo comparison](docs/PRIVACY_AND_CORE.md).

## Build from source

On Linux x86-64, install a JDK (17+), GNU `make`, `git`, `readelf` (binutils),
Python 3, and the official Android SDK with Platform 37 and Build Tools 36.1.0.
Use an official NDK r28 or newer for production builds (recommended: r28c or
newer). Export the *absolute* install paths:

```sh
export JAVA_HOME=/path/to/jdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
APK_OUTPUT="$PWD/build/fba-offline-local-test.apk" ./tools/build-apk.sh
./tools/test.sh
./tools/verify-apk.sh build/fba-offline-local-test.apk
```

`tools/build-apk.sh` uses the Android NDK, `aapt2`, `javac`, D8, `zipalign` and
`apksigner` directly: no Gradle, Maven app dependencies, or old APK inputs.
`tools/build-core.sh` fetches and verifies a pinned, unchanged checkout of the
[FBNeo Libretro port](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af).
Set `FBNEO_SOURCE` to an existing checkout at this exact commit to build without
fetching. `ANDROID_PLATFORM` and `ANDROID_BUILD_TOOLS` can override SDK tool
paths, `JOBS` controls the native compile, and `ANDROID_ABIS=x86_64` is the
separate build option for an x86-64 emulator. Only the ARM64 APK is provided.
The build fails if the output has permissions, unrecognized payloads, native
network/loader imports on the denylist, or incorrect 16 KiB library alignment.

**Signing:** The default artifacts use a locally generated test key under
`.cache/signing/`, deliberately excluded from version control. To sign a
release with your own key, set `APK_KEYSTORE` and `APK_KEY_ALIAS` locally;
`apksigner` prompts you for the password in your own terminal. Keep the same
release key for future updates. A release signed with a different key cannot
replace the test install in place. Back up any data you need before removing a
test install; uninstalling deletes its private library and saves.

## Licensing and future updates

This app is not an official release by Team FBNeo. FBNeo has a non-commercial
license. Its full license and component notices are included in the APK.
If you redistribute a build, make the matching frontend/build source and any
core modifications public, preserve the notices, and don't bundle ROM images
without permission. Do not sell or monetize the build under FBNeo's license.
The old archives are not inputs to the build and do not confer rights to their
code or bundled data.

For future core refreshes, update the source pin in `tools/build-core.sh` and
`android/CORE-SOURCE.txt` together, review the diff, rebuild, re-run the checks,
and test known good sets, required BIOS files, touch, gamepad and audio on
physical devices. Increase `versionCode` before distributing an update. The
app does not update itself or fetch cores in the background.
