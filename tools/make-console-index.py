#!/usr/bin/env python3
"""Generate ROM-free metadata for the pinned FBNeo console and computer DATs.

Columns: prefix, set, role (game/BIOS/prefix-required game), year, title,
parent, other dependencies, and CSV size:CRC signatures excluding BIOS chips
from game rows. Read DAT bytes by pinned Git object, not local working files.
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PIN = "2da48555c13ce5d011edf2d6ec829b7393c070af"
SYSTEMS = {
    "a26": "Atari 2600 Games", "astro": "Bally Astrocade Games", "chf": "Fairchild Channel F Games",
    "cv": "ColecoVision", "fds": "FDS Games", "gba": "GBA Games", "gg": "Game Gear",
    "md": "Megadrive", "msx": "MSX 1 Games", "nes": "NES Games", "ngp": "NeoGeo Pocket Games",
    "pce": "PC-Engine", "s32x": "Sega 32X", "sg1k": "Sega SG-1000", "sgx": "SuprGrafx",
    "sms": "Master System", "snes": "SNES Games", "spec": "ZX Spectrum Games", "tg": "TurboGrafx16",
}
# These DAT entries are firmware for disc systems, not playable cartridge sets.
DISC_FIRMWARE = {"md": {"segacd"}, "pce": {"scdsys"}}
SET_NAME = re.compile(r"[a-z0-9_][a-z0-9_-]{0,79}")


def read_dat(core, system):
    name = f"dats/FinalBurn Neo (ClrMame Pro XML, {system} only).dat"
    return ET.fromstring(subprocess.check_output(["git", "-C", str(core), "show", f"{PIN}:{name}"])).findall("game")


def signature(rom):
    size, crc = int(rom.get("size", "0")), rom.get("crc", "")
    if not (re.fullmatch(r"[a-f0-9]{8}", crc) and 0 < size <= 64 * 1024 * 1024):
        raise ValueError(f"Invalid signature for {rom.get('name')}")
    return f"{size}:{crc}"


def build(core):
    rows = []
    arcade = {g.attrib["name"]: {signature(r) for r in g.findall("rom")
                               if 0 < int(r.get("size", "0")) <= 64 * 1024 * 1024
                               and re.fullmatch(r"[a-f0-9]{8}", r.get("crc", ""))}
              for g in read_dat(core, "Arcade")}
    for prefix, dat in sorted(SYSTEMS.items()):
        games = {g.attrib["name"]: g for g in read_dat(core, dat)}
        firmware = {name: {signature(r) for r in g.findall("rom")} for name, g in games.items()
                    if g.get("isbios") == "yes" or name in DISC_FIRMWARE.get(prefix, set())}
        for name, game in sorted(games.items()):
            if not SET_NAME.fullmatch(name) or f"{prefix}_{name}" in arcade or f"bios_{prefix}_{name}" in arcade:
                raise ValueError(f"Invalid or conflicting console ID: {prefix}_{name}")
            driver = game.find("driver")
            bios = name in firmware
            if not bios and (driver is None or driver.get("status") != "good"):
                continue
            members = set()
            parent = game.get("cloneof", "")
            dependencies = {game.get("romof")} - {None, name, parent}
            for rom in game.findall("rom"):
                sig = signature(rom)
                # Merged BIOS members can also appear on clones whose romof names their parent.
                providers = {short for short, chips in firmware.items() if sig in chips} if not bios and rom.get("merge") else set()
                if providers:
                    dependencies.update(providers - {parent})
                else:
                    members.add(sig)
            if not members:  # No independent game/media members in this entry.
                continue
            for dep in dependencies | ({parent} if parent else set()):
                if dep not in games:
                    raise ValueError(f"Unknown dependency {dep} for {prefix}_{name}")
            year = " ".join(game.findtext("year", "").split()) or "????"
            title = " ".join(game.findtext("description", name).split())
            # If a bare ZIP could be a real arcade set with the same bytes, require the explicit console prefix.
            role = "B" if bios else ("P" if members & arcade.get(name, set()) else "G")
            rows.append("\t".join((prefix, name, role, year, title, parent,
                                   ",".join(sorted(dependencies)), ",".join(sorted(members)))) + "\n")
    return "".join(rows).encode("utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: make-console-index.py FBNEO_SOURCE console-sets.tsv")
    Path(sys.argv[2]).write_bytes(build(Path(sys.argv[1])))
