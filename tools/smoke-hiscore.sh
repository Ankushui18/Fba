#!/usr/bin/env bash
# Exercise arcade high scores using the pinned Linux core with both Android patches.
set -euo pipefail
ulimit -c 0
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lib=$(realpath -- "${1:?Usage: tools/smoke-hiscore.sh FBNEO_PATCHED_HOST_LIB.so}")
core=${FBNEO_SOURCE:-$root/.cache/FBNeo-libretro}
header="$core/src/burner/libretro/libretro-common/include"
cpp="$root/android/src/main/cpp"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/system/fbneo" "$work/save" "$work/no-dat"
cp "$core/metadata/hiscore.dat" "$work/system/fbneo/hiscore.dat"
python3 "$root/tests/make_hiscore_fixture.py" "$core/dats/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat" "$work/1942.zip"
"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -I "$header" -I "$cpp" \
  "$cpp/engine.cpp" "$root/tests/CoreHiscoreProbe.cpp" "$lib" "-Wl,-rpath,$(dirname -- "$lib")" -o "$work/probe"
"$work/probe" "$work/1942.zip" "$work/system" "$work/save" "$work/no-dat"
