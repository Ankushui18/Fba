#!/usr/bin/env bash
# Drive two independent copies of the Link core with the Android link wire protocol.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lib=$(realpath -- "${1:?Pass a host build of the FBNeo Link core}")
core=${FBNEO_SOURCE:-$root/.cache/FBNeo-libretro}
main="$root/android/src/main"
header="$core/src/burner/libretro/libretro-common/include"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/host" "$work/join"
python3 "$root/tests/make_core_fixture.py" "$core/dats/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat" \
  "$work/gridlee.zip" "$work/unused-cheats/gridlee.ini"
"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -I "$header" -I "$main/cpp" \
  "$main/cpp/engine.cpp" "$root/tests/CoreLinkProbe.cpp" "$lib" "-Wl,-rpath,$(dirname -- "$lib")" -o "$work/peer"
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 8 -encoding UTF-8 -d "$work" \
  "$root/android/src/link/java/io/github/ankushui18/fbaoffline/LinkWire.java" "$root/tests/LinkWireTest.java"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$work" io.github.ankushui18.fbaoffline.LinkWireTest \
  "$work/peer" "$work/gridlee.zip" "$work/host" "$work/join"
