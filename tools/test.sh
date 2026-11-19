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
app="$main/java/io/github/ankushui18/fbaoffline"
"$javac" -encoding UTF-8 --release 8 -d "$work" "$app"/{RomStore,GameImport,LooseNes,ConsoleStore,ConsoleSystem,Joypad,PadBindings,AutoFire,PadLayout,PadInput,PadProfile,StateStore,QuickResumeStore,LocalFiles,BackupStore,BackupSettings,IpsPatch,PatchStore,LegacyIps,UserCheats,RomNotes,LegacySkinFiles,LatestFrame}.java \
  "$root/tests/RomStoreTest.java" "$root/tests/LooseNesTest.java" "$root/tests/ConsoleStoreTest.java" "$root/tests/PadTest.java" "$root/tests/StateStoreTest.java" "$root/tests/BackupStoreTest.java" \
  "$root/tests/QuickResumeTest.java" "$root/tests/GameFilesTest.java" "$root/tests/ParityFilesTest.java" "$root/tests/LatestFrameTest.java"
"$java" -cp "$work" io.github.ankushui18.fbaoffline.RomStoreTest
"$java" -cp "$work" io.github.ankushui18.fbaoffline.ConsoleStoreTest "$main/assets/console-sets.tsv"
"$java" -cp "$work" io.github.ankushui18.fbaoffline.LooseNesTest "$main/assets/console-sets.tsv"
"$java" -cp "$work" io.github.ankushui18.fbaoffline.PadTest
"$java" -cp "$work" io.github.ankushui18.fbaoffline.StateStoreTest
"$java" -cp "$work" io.github.ankushui18.fbaoffline.BackupStoreTest
"$java" -cp "$work" io.github.ankushui18.fbaoffline.QuickResumeTest
"$java" -cp "$work" io.github.ankushui18.fbaoffline.GameFilesTest
"$java" -cp "$work" io.github.ankushui18.fbaoffline.ParityFilesTest
"$java" -cp "$work" io.github.ankushui18.fbaoffline.LatestFrameTest
"$javac" -encoding UTF-8 --release 8 -d "$work" \
  "$root/android/src/link/java/io/github/ankushui18/fbaoffline/LinkWire.java" "$root/tests/LinkWireTest.java"
"$java" -cp "$work" io.github.ankushui18.fbaoffline.LinkWireTest
python3 "$root/tests/test_catalog.py"
python3 "$root/tools/make-console-index.py" "$core" "$work/console-sets.tsv"
cmp "$main/assets/console-sets.tsv" "$work/console-sets.tsv"
"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -I "$header" -I "$main/cpp" \
  "$main/cpp/engine.cpp" "$root/tests/EngineTest.cpp" -o "$work/engine-test"
"$work/engine-test"

hqx="$root/android/third_party/hqx"
for c in init hq2x hq3x hq4x; do "${CC:-cc}" -std=c11 -O2 -ffp-contract=off -c "$hqx/$c.c" -o "$work/$c.o"; done
"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -pthread -I "$main/cpp" -I "$hqx" \
  "$main/cpp/frame_filter.cpp" "$root/tests/FilterTest.cpp" "$work/"{hq2x,hq3x,hq4x,init}.o -o "$work/filter-test"
"$work/filter-test"
