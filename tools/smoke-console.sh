#!/usr/bin/env bash
# Production import and launch path plus pinned host core. Uses no commercial data by default.
set -euo pipefail
ulimit -c 0
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lib=$(realpath -- "${1:?Usage: smoke-console.sh FBNEO_HOST_LIB.so [LOCAL_VERIFIED_NINJA.nes]}")
core=${FBNEO_SOURCE:-$root/.cache/FBNeo-libretro}
main="$root/android/src/main"
app="$main/java/io/github/ankushui18/fbaoffline"
index="$main/assets/console-sets.tsv"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
python3 "$root/tests/make_console_fixture.py" "$index" "$work/raw"
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 8 -encoding UTF-8 -d "$work" \
  "$app/"{LocalFiles,RomStore,ConsoleStore,ConsoleSystem}.java "$root/tests/ConsoleRouteProbe.java"
optional=()
if [[ $# -gt 1 ]]; then optional=("$(realpath -- "$2")"); fi
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$work" io.github.ankushui18.fbaoffline.ConsoleRouteProbe \
  "$index" "$work" "${optional[@]}"
"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -I "$core/src/burner/libretro/libretro-common/include" -I "$main/cpp" \
  "$main/cpp/engine.cpp" "$root/tests/CoreConsoleProbe.cpp" "$lib" -Wl,-rpath,"$(dirname -- "$lib")" -o "$work/console-probe"
"$work/console-probe" "$work/launches.tsv"
