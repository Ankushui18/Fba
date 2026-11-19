#!/usr/bin/env bash
# Build with Android SDK/NDK, JDK and Python; no Gradle or Maven dependencies.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"

if [[ ${ARCADE_LINK:-1} != 1 ]]; then
  echo 'The standalone Offline build is retired. Unset ARCADE_LINK to build FBA Arcade with local multiplayer.' >&2
  exit 1
fi

export JAVA_HOME=${JAVA_HOME:?Set JAVA_HOME to a JDK (17 or newer)}
export PATH="$JAVA_HOME/bin:$PATH"
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Set ANDROID_SDK_ROOT to the Android SDK}}
ndk=${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an installed Android NDK}
bt=${ANDROID_BUILD_TOOLS:-36.1.0}
platform=${ANDROID_PLATFORM:-android-37.0}
bsdk="$sdk/build-tools/$bt"
jar="$sdk/platforms/$platform/android.jar"
if [[ ! -f $jar && $platform == android-37.0 ]]; then jar="$sdk/platforms/android-37/android.jar"; fi
cbin="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
core=${FBNEO_SOURCE:-$root/.cache/FBNeo-libretro}
abis=${ANDROID_ABIS:-arm64-v8a}
min_sdk=26
target_sdk=37
version=0.5.3
version_code=8
artifact_suffix=
java_package=io.github.ankushui18.fbaoffline
recovery=${ARCADE_RECOVERY:-0}
test_package=io.github.ankushui18.fbaarcade.test
test_key=arcade-test
test_cn='FBA Arcade Test Build'
expected_cert=
case $recovery in
  0) ;;
  1)
    [[ -z ${APK_KEYSTORE:-} ]] || { echo 'ARCADE_RECOVERY=1 uses its own local test key.' >&2; exit 1; }
    artifact_suffix=-recovery
    test_package=io.github.ankushui18.fbaarcade.recovery
    test_key=arcade-recovery-test
    expected_cert=9a536eff83d3a9892a458927edcbb4e7209ccab339094f726c676d2ec197e1a3
    ;;
  preview)
    [[ -z ${APK_KEYSTORE:-} ]] || { echo 'Recovery Preview uses its own local test key.' >&2; exit 1; }
    artifact_suffix=-preview
    test_package=io.github.ankushui18.fbaarcade.recovery.preview
    test_key=arcade-recovery-preview-test
    expected_cert=d8a45e30010cfbee84fef2227b3e0c98e585046658c0a0582df0845d8fd0d396
    ;;
  *) echo 'ARCADE_RECOVERY must be 0, 1, or preview.' >&2; exit 1 ;;
esac
if [[ -n $expected_cert ]]; then
  upgrade_key="$root/.cache/signing/$test_key.jks"
  [[ -f $upgrade_key ]] || {
    echo "The $test_key upgrade key is missing. A new key cannot update this app." >&2; exit 1
  }
  fingerprint=$("$JAVA_HOME/bin/keytool" -exportcert -keystore "$upgrade_key" -alias "$test_key" -storepass "$test_key" | sha256sum)
  [[ ${fingerprint%% *} == "$expected_cert" ]] || {
    echo "The signing certificate does not match the installed $test_package build." >&2; exit 1
  }
fi
# Preserve the existing signed Link package/version and artifact names.
artifact_version="$version$artifact_suffix-link"
manifest_package=()
if [[ -z ${APK_KEYSTORE:-} ]]; then
  manifest_package=(--rename-manifest-package "$test_package")
fi
main="$root/android/src/main"
link="$root/android/src/link"
hqx="$root/android/third_party/hqx"
core_out="$root/build/core-link"

for required in "$jar" "$bsdk/aapt2" "$bsdk/d8" "$bsdk/apksigner" "$bsdk/zipalign" "$JAVA_HOME/bin/javac"; do
  [[ -f $required ]] || { echo "Missing build tool or platform: $required" >&2; exit 1; }
done

"$root/tools/build-core.sh"
read -r -a abi_list <<< "$abis"
work=$(mktemp -d "$root/build/apk.XXXXXX")
trap 'rm -rf -- "$work"' EXIT
stage="$work/stage"
mkdir -p "$stage/lib" "$work/classes" "$work/dex" "$work/generated" "$work/assets/licenses" "$root/artifacts"

for abi in "${abi_list[@]}"; do
  case $abi in
    arm64-v8a) triple=aarch64-linux-android ;;
    x86_64) triple=x86_64-linux-android ;;
    *) echo "Unsupported ABI: $abi" >&2; exit 1 ;;
  esac
  dir="$stage/lib/$abi"
  mkdir -p "$dir"
  install -m 0644 "$core_out/lib/$abi/libretro.so" "$dir/libretro.so"
  "$cbin/$triple$min_sdk-clang" -std=c11 -O2 -ffp-contract=off -shared -fPIC \
    -Wl,--no-undefined,-z,relro,-z,now,-z,max-page-size=16384,-soname,libhqx.so \
    -Wl,--version-script="$hqx/link.exports" "$hqx/"{hq2x.c,hq3x.c,hq4x.c,init.c} -o "$dir/libhqx.so"
  "$cbin/$triple$min_sdk-clang++" -std=c++17 -O2 -Wall -Wextra -Werror -shared -fPIC \
    -fvisibility=hidden -fstack-protector-strong -Wl,--no-undefined,-z,relro,-z,now,-z,max-page-size=16384 \
    -Wl,-soname,libofflinefb.so -static-libstdc++ \
    -I "$core/src/burner/libretro/libretro-common/include" -I "$hqx" "$main/cpp/"{frontend,engine,frame_filter}.cpp \
    -L "$dir" -lretro -lhqx -ljnigraphics -o "$dir/libofflinefb.so"
  "$cbin/llvm-strip" --strip-unneeded "$dir/"{libofflinefb.so,libhqx.so}
done

cp "$core/src/license.txt" "$work/assets/FBNeo-LICENSE.txt"
cp "$core/src/burner/libretro/deps/ugui/LICENSE.md" "$work/assets/licenses/ugui.txt"
cp "$core/src/dep/libs/libchdr/LICENSE.txt" "$work/assets/licenses/libchdr.txt"
cp "$core/src/dep/libs/lzma/LICENSE" "$work/assets/licenses/lzma.txt"
cp "$core/src/dep/libs/dr_libs/LICENSE.txt" "$work/assets/licenses/dr_libs.txt"
cp "$core/src/burn/drv/gba/license.txt" "$work/assets/licenses/gba.txt"
cp "$core/src/burn/drv/snes/license.txt" "$work/assets/licenses/snes.txt"
cp "$root/android/CORE-SOURCE.txt" "$work/assets/CORE-SOURCE.txt"
cp "$hqx/COPYING" "$work/assets/licenses/hqx.txt"
cp "$hqx/AUTHORS" "$work/assets/licenses/hqx-authors.txt"
cp "$main/assets/"{game-art.zip,game-catalog.tsv,game-cheats.zip,ips-targets.zip} "$work/assets/"

manifest="$work/AndroidManifest.xml"
python3 "$root/tools/link_manifest.py" "$main/AndroidManifest.xml" "$manifest"
cp "$root/android/patches/fbneo-lockstep.patch" "$work/assets/FBNeo-LINK.patch"
printf '\nThis Link build applies FBNeo-LINK.patch to initialize the existing deterministic mode before driver startup.\n' >> "$work/assets/CORE-SOURCE.txt"
# Keep the checked-in legacy PNG pixels/chunks unchanged for the APK allowlist.
"$bsdk/aapt2" compile --no-crunch --dir "$main/res" -o "$work/res.zip"
"$bsdk/aapt2" link -I "$jar" --java "$work/generated" --manifest "$manifest" -A "$work/assets" \
  --custom-package "$java_package" "${manifest_package[@]}" \
  --min-sdk-version "$min_sdk" --target-sdk-version "$target_sdk" \
  --version-code "$version_code" --version-name "$version-link" -o "$work/unsigned.apk" "$work/res.zip"
mapfile -d '' sources < <(find "$main/java" "$link/java" "$work/generated" -name '*.java' -print0 | sort -z)
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 8 -classpath "$jar" -d "$work/classes" "${sources[@]}"
(cd "$work/classes" && "$JAVA_HOME/bin/jar" cf "$work/frontend.jar" .)
"$bsdk/d8" --release --lib "$jar" --min-api "$min_sdk" --output "$work/dex" "$work/frontend.jar"
cp "$work/dex/classes.dex" "$stage/"
# Uncompressed native libraries are mapped directly by Android. Keep zipalign
# after adding all entries and before signing.
python3 - "$work/unsigned.apk" "$stage" <<'PY'
import sys
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile
apk, stage = Path(sys.argv[1]), Path(sys.argv[2])
with ZipFile(apk, 'a') as z:
    z.write(stage / 'classes.dex', 'classes.dex', compress_type=ZIP_DEFLATED)
    for lib in sorted(stage.glob('lib/*/*.so')):
        z.write(lib, lib.relative_to(stage).as_posix(), compress_type=ZIP_STORED)
PY
"$bsdk/zipalign" -P 16 -f 4 "$work/unsigned.apk" "$work/aligned.apk"

if [[ -n ${APK_KEYSTORE:-} ]]; then
  ks=$APK_KEYSTORE
  key=${APK_KEY_ALIAS:?Set APK_KEY_ALIAS for your signing key}
  password=()    # apksigner reads the password on this terminal, not from source or chat.
  flavor=release
else
  ks="$root/.cache/signing/$test_key.jks"
  key=$test_key
  password=(--ks-pass "pass:$test_key" --key-pass "pass:$test_key")
  flavor=test
  if [[ ! -e $ks ]]; then
    [[ $recovery == 0 ]] || { echo 'Upgrade key is missing.' >&2; exit 1; }
    mkdir -p "$(dirname -- "$ks")"
    (umask 077; "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$ks" -storetype PKCS12 \
      -storepass "$test_key" -keypass "$test_key" -alias "$key" -keyalg RSA -keysize 3072 \
      -validity 3650 -dname "CN=$test_cn")
  fi
fi
arch=${abis// /-}
apk="${APK_OUTPUT:-$root/artifacts/fba-arcade-$artifact_version-$arch-$flavor.apk}"
"$bsdk/apksigner" sign --ks "$ks" --ks-key-alias "$key" "${password[@]}" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --out "$work/signed.apk" "$work/aligned.apk"
"$root/tools/verify-apk.sh" "$work/signed.apk"
install -m 0644 "$work/signed.apk" "$apk"
sha256sum "$apk"
