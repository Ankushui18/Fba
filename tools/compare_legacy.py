#!/usr/bin/env python3
"""Compare legacy ELF definitions and ROM-set names, not lines of source."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from zipfile import ZipFile


def git(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo), *args], text=True)


def elf32_value(data, address):
    if data[:6] != b"\x7fELF\x01\x01":
        raise ValueError("expected 32-bit little-endian ELF")
    phoff = struct.unpack_from("<I", data, 28)[0]
    size, count = struct.unpack_from("<HH", data, 42)
    for i in range(count):
        kind, offset, vaddr, _, filesz = struct.unpack_from("<IIIII", data, phoff + i * size)
        if kind == 1 and vaddr <= address <= vaddr + filesz - 4:
            return struct.unpack_from("<I", data, offset + address - vaddr)[0]
    raise ValueError("version symbol is not in a file-backed ELF segment")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("fbneo", type=Path, help="finalburnneo/FBNeo checkout")
    parser.add_argument("libretro", type=Path, help="libretro/FBNeo checkout")
    args = parser.parse_args()
    sources = git(args.fbneo, "grep", "-h", "-e", "struct BurnDriver", "HEAD", "--", "src/burn/drv")
    upstream_drivers = set(re.findall(r"\bstruct\s+BurnDriver\w*\s+(BurnDrv\w+)\s*=", sources))
    dat = args.libretro / "dats/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat"
    arcade = {node.attrib["name"] for node in ET.parse(dat).findall("game")}
    result = {
        "legacy_apk_sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        "finalburnneo_commit": git(args.fbneo, "rev-parse", "HEAD").strip(),
        "libretro_commit": git(args.libretro, "rev-parse", "HEAD").strip(),
        "upstream_driver_definitions": len(upstream_drivers),
        "libretro_arcade_dat_names": len(arcade),
        "legacy_cores": {},
    }
    with ZipFile(args.apk) as apk, tempfile.TemporaryDirectory() as tmp:
        listed = {node.text for node in ET.fromstring(apk.read("assets/drvrom.xml")).findall("./rom/name") if node.text}
        result["legacy_rom_names"] = len(listed)
        result["legacy_rom_names_in_arcade_dat"] = len(listed & arcade)
        for name in ("libfba.so", "libfbas.so"):
            data = apk.read("lib/armeabi-v7a/" + name)
            path = Path(tmp) / name
            path.write_bytes(data)
            rows = [line.split() for line in subprocess.check_output(
                ["nm", "-D", "-P", "--defined-only", str(path)], text=True).splitlines()]
            drivers = {r[0] for r in rows if re.fullmatch(r"BurnDrv\w+", r[0]) and r[1] in "BDGRSV"}
            ver = elf32_value(data, int(next(r[2] for r in rows if r[0] == "nBurnVer"), 16))
            result["legacy_cores"][name] = {
                "bytes": len(data),
                "sha256": hashlib.sha256(data).hexdigest(),
                "reported_core_version": f"{ver >> 20:x}.{ver >> 16 & 15:x}.{ver >> 8 & 255:02x}.{ver & 255:02x}",
                "exported_driver_symbols": len(drivers),
                "symbols_matching_upstream_definitions": len(drivers & upstream_drivers),
            }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
