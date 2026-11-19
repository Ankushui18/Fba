#!/usr/bin/env python3
"""Build the metadata-only NES import index from the pinned FBNeo DAT."""
import hashlib
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

DAT_SHA256 = "18fafb4b3b04319d0053ee436ed01bb3ae1bff85a01e11523160fc2bdeb89a46"


def build(dat):
    data = dat.read_bytes()
    if hashlib.sha256(data).hexdigest() != DAT_SHA256:
        raise SystemExit("Use the NES DAT from the pinned FBNeo commit, not a different revision.")
    rows = {}
    for game in ET.fromstring(data).findall("game"):
        roms = game.findall("rom")
        driver = game.find("driver")
        if len(roms) != 1 or driver is None or driver.get("status") != "good":
            continue
        rom = roms[0]
        if not rom.get("name", "").lower().endswith(".nes"):
            continue
        name = game.get("name", "")
        crc, size = rom.get("crc", ""), int(rom.get("size", "0"))
        if (not re.fullmatch(r"[a-z0-9_][a-z0-9_-]{0,85}", name) or name in rows
                or not re.fullmatch(r"[0-9a-f]{8}", crc) or not 16 <= size <= 10 * 1024 * 1024):
            raise SystemExit(f"Unexpected NES entry: {name}")
        title = " ".join(game.findtext("description", name).split())
        year = " ".join(game.findtext("year", "").split()) or "????"
        rows[name] = f"{name}\t{size}\t{crc}\t{year}\t{title}\n"
    return "".join(rows[name] for name in sorted(rows)).encode("utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: make-nes-index.py FBNEO_NES.dat nes-sets.tsv")
    Path(sys.argv[2]).write_bytes(build(Path(sys.argv[1])))
