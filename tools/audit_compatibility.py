#!/usr/bin/env python3
"""Inventory the APK's indexed routes and core option keys without running a ROM."""
import argparse
from collections import Counter, defaultdict
from hashlib import sha256
import json
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile

from check_offline_apk import inspect_console_drivers


def git(core, *args):
    return subprocess.check_output(["git", "-C", str(core), *args], text=True)


def file_sha256(path):
    digest = sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="FBA Arcade ARM64 APK")
    parser.add_argument("core", type=Path, help="Git checkout of the FBNeo Libretro revision recorded in the APK")
    parser.add_argument("--legacy-apk", type=Path, help="optional local FBA4droid APK (not included)")
    parser.add_argument("--aapt2", default="aapt2", help="aapt2 executable for the optional legacy settings inventory")
    args = parser.parse_args()

    rev = git(args.core, "rev-parse", "HEAD").strip()
    option_source = git(args.core, "show", "HEAD:src/burner/libretro/retro_common.cpp")
    options = sorted(set(re.findall(
        r'struct retro_core_option_v2_definition var_fbneo_\w+\s*=\s*\{\s*"(fbneo-[a-z0-9-]+)"', option_source)))
    subsystem_source = git(args.core, "show", "HEAD:src/burner/libretro/libretro.cpp")
    modes = [{"label": label, "selector": tag, "media": media} for label, tag, media in re.findall(
        r'\{\s*"([^"\n]+)",\s*"([a-z0-9]+)",\s*subsystem_(rom|iso),', subsystem_source)]
    if not options or not modes:
        parser.error("couldn't find the FBNeo Libretro option or subsystem definitions")

    with ZipFile(args.apk) as apk:
        source = apk.read("assets/CORE-SOURCE.txt")
        if f"https://github.com/libretro/FBNeo/tree/{rev}".encode() not in source:
            parser.error("core checkout doesn't match the source revision named by this APK")
        index = apk.read("assets/console-sets.tsv")
        lib = apk.read("lib/arm64-v8a/libretro.so")
        inspect_console_drivers(lib, index, "arm64-v8a")
        counts = defaultdict(Counter)
        routes = set()
        for line in index.decode("utf-8").splitlines():
            prefix, name, role, *_ = line.split("\t")
            if (prefix, name) in routes or role not in ("B", "G"):
                parser.error("duplicate or unrecognized console index row")
            routes.add((prefix, name))
            counts[prefix]["firmware" if role == "B" else "game"] += 1
        in_binary = [key for key in options if b"\0" + key.encode("ascii") + b"\0" in lib]
        cd_drivers = [name for name in ("neocdz", "pce_scdsys", "md_segacd")
                      if b"\0" + name.encode("ascii") + b"\0" in lib]

    audit = {
        "apk_sha256": file_sha256(args.apk),
        "libretro_revision_from_apk_notice": rev,
        "indexed_console_routes": dict(sorted(counts.items())),
        "indexed_game_count": sum(c["game"] for c in counts.values()),
        "indexed_firmware_count": sum(c["firmware"] for c in counts.values()),
        "upstream_subsystem_definitions": modes,
        "compiled_cd_driver_names": cd_drivers,
        "fixed_option_keys": {"found_in_apk_core": in_binary, "source_only": sorted(set(options) - set(in_binary))},
    }
    if args.legacy_apk:
        tree = subprocess.check_output([args.aapt2, "dump", "xmltree", "--file", "res/xml/prefs.xml",
                                        str(args.legacy_apk)], text=True)
        keys = list(dict.fromkeys(re.findall(r'android:key\(0x[0-9a-f]+\)="([^"]+)"', tree)))
        if not keys:
            parser.error("couldn't read keys in the legacy preference XML")
        audit["legacy_apk_sha256"] = file_sha256(args.legacy_apk)
        audit["legacy_global_preference_keys"] = [key for key in keys if key != "options"]  # root screen ID
    print(json.dumps(audit, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
