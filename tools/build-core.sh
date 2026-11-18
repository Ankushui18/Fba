#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
core=${FBNEO_SOURCE:-$root/.cache/FBNeo-libretro}
ndk=${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an installed Android NDK}
core_url=https://github.com/libretro/FBNeo.git
core_rev=2da48555c13ce5d011edf2d6ec829b7393c070af
abis=${ANDROID_ABIS:-arm64-v8a}
min_sdk=26
jobs=${JOBS:-2}

read -r -a abi_list <<< "$abis"
if [[ ${#abi_list[@]} -eq 0 ]]; then echo 'No ABI selected' >&2; exit 1; fi
for abi in "${abi_list[@]}"; do
  case $abi in arm64-v8a|x86_64) ;; *) echo "Unsupported ABI: $abi" >&2; exit 1 ;; esac
done

if [[ ! -d $core/.git ]]; then
  mkdir -p -- "$core"
  git -C "$core" init -q
  git -C "$core" fetch --depth=1 "$core_url" "$core_rev"
  git -C "$core" checkout -q --detach FETCH_HEAD
fi
core=$(cd -- "$core" && pwd)
[[ $(git -C "$core" rev-parse HEAD) == "$core_rev" && -z $(git -C "$core" status --porcelain) ]] || {
  echo "Use an unchanged checkout of $core_url at $core_rev (new directory via FBNEO_SOURCE)." >&2; exit 1;
}

out="$root/build/core"
if [[ ${ARCADE_LINK:-0} == 1 ]]; then
  copy="$root/.cache/FBNeo-link"
  "$root/tools/prepare-link-core.sh" "$core" "$copy"
  core="$copy"
  out="$root/build/core-link"
fi
port="$core/src/burner/libretro"
"$ndk/ndk-build" -C "$port" -j"$jobs" \
  "NDK_PROJECT_PATH=$port" "APP_BUILD_SCRIPT=$port/jni/Android.mk" \
  "NDK_APPLICATION_MK=$port/jni/Application.mk" "APP_ABI=$abis" \
  "APP_PLATFORM=android-$min_sdk" NDK_DEBUG=0 APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true \
  "NDK_OUT=$out/obj" "NDK_LIBS_OUT=$out/lib"
