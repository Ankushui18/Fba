#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
core=${FBNEO_SOURCE:-$root/.cache/FBNeo-libretro}
main="$root/android/src/main"
header="$core/src/burner/libretro/libretro-common/include"
if [[ ! -f $header/libretro.h ]]; then
  echo "Fetch the pinned FBNeo source with tools/build-core.sh first or set FBNEO_SOURCE." >&2
  exit 1
fi
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$javac" --release 8 -d "$work" "$main/java/io/github/ankushui18/fbaoffline/RomStore.java" "$root/tests/RomStoreTest.java"
"$java" -cp "$work" io.github.ankushui18.fbaoffline.RomStoreTest
"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -I "$header" -I "$main/cpp" \
  "$main/cpp/engine.cpp" "$root/tests/EngineTest.cpp" -o "$work/engine-test"
"$work/engine-test"
