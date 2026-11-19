#!/usr/bin/env bash
# Optional native smoke test using the UNIX build of the same Libretro commit.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lib=$(realpath -- "${1:?Usage: tools/smoke-core.sh FBNEO_HOST_LIB.so [LOCAL_ROM.zip]}")
core=${FBNEO_SOURCE:-$root/.cache/FBNeo-libretro}
header="$core/src/burner/libretro/libretro-common/include"
cpp="$root/android/src/main/cpp"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/roms" "$work/local"
python3 "$root/tests/make_core_fixture.py" "$core/dats/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat" \
  "$work/roms/gridlee.zip" "$work/local/fbneo/cheats/gridlee.ini"
app="$root/android/src/main/java/io/github/ankushui18/fbaoffline"
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 8 -encoding UTF-8 -d "$work" "$app/"{LocalFiles,RomStore,PatchStore,IpsPatch,LegacyIps,StateStore,UserCheats}.java "$root/tests/GameFilesTest.java"
read -r chip palette < <(python3 - "$core/dats/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat" <<'PYCHIP'
import sys, xml.etree.ElementTree as ET
roms = ET.parse(sys.argv[1]).findall('./game[@name="gridlee"]/rom')
print(roms[0].get('name'), roms[10].get('name'))
PYCHIP
)
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -Dprobe.chip="$chip" -Dprobe.palette="$palette" -cp "$work" io.github.ankushui18.fbaoffline.GameFilesTest \
    "$work/roms/gridlee.zip" "$work/local/fbneo/patched/gridlee.zip" "$work/patches" \
    "$root/android/src/main/assets/ips-targets.zip" "$work/local/fbneo/cheats/gridlee.ini"
python3 - "$work/roms" <<'PY'
from pathlib import Path
import sys
from zipfile import ZipFile
out = Path(sys.argv[1])
for name in ('no_such_fbneo_romset', '1942'):
    with ZipFile(out / (name + '.zip'), 'w') as z:
        z.writestr('placeholder.bin', b'Not a ROM; failure-path test only.\n')
PY
optional=()
if [[ $# -gt 1 ]]; then optional=("$(realpath -- "$2")"); fi
"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -I "$header" -I "$cpp" \
  "$cpp/engine.cpp" "$root/tests/CoreSmoke.cpp" "$lib" "-Wl,-rpath,$(dirname -- "$lib")" -o "$work/core-smoke"
"$work/core-smoke" "$work/local" "$work/roms/no_such_fbneo_romset.zip" "$work/roms/1942.zip" "$work/roms/gridlee.zip" "${optional[@]}"
