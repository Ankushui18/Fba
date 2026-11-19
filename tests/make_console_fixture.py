#!/usr/bin/env python3
"""Create test-only cartridges and a test-only FDS BIOS from tiny hardware loops.

The approved DAT CRCs select real FBNeo drivers. None of the original game/BIOS
code or bytes are used. Like the arcade smoke fixture, only unused padding is
adjusted to match these checksums. Never package these as game assets.
"""
import sys
from pathlib import Path
from struct import pack_into
from zipfile import ZipFile

from make_core_fixture import fit_checksum


def make(index, output):
    sets = {(r[0], r[1]): r[-1] for line in index.read_text(encoding="utf-8").splitlines()
            if (r := line.split("\t"))}

    def rom(system, name):
        size, crc = sets[system, name].split(":")
        return bytearray(int(size)), int(crc, 16)

    output.mkdir(parents=True, exist_ok=True)
    data, crc = rom("nes", "smb")
    data[:16] = b"NES\x1a\x02\x01" + bytes(10)
    data[16:21] = bytes.fromhex("78 D8 4C 02 80")  # 6502: SEI, CLD, loop at $8002
    for vector in (0x7ffa, 0x7ffc, 0x7ffe):
        pack_into("<H", data, 16 + vector, 0x8000)
    (output / "Cartridge.nes").write_bytes(fit_checksum(data, crc))

    data, crc = rom("snes", "supermarioworld")
    code = bytes.fromhex("78 18 FB E2 20 A9 8F 8D 00 21 9C 21 21 A9 1F 8D 22 21 9C 22 21 A9 0F 8D 00 21 80 FE")
    data[:len(code)] = code  # 65816: set CGRAM backdrop and loop
    data[0x7fc0:0x7fd5] = b"FBA CONSOLE PROBE    "
    data[0x7fd5:0x7fdb] = bytes([0x20, 0, 9, 0, 1, 0])  # LoROM, 512 KiB, NTSC
    pack_into("<HH", data, 0x7fdc, 0xedcb, 0x1234)
    pack_into("<H", data, 0x7ffc, 0x8000)
    (output / "Headered cartridge.smc").write_bytes(bytes(512) + fit_checksum(data, crc))

    data, crc = rom("md", "sonic")
    pack_into(">II", data, 0, 0x00ffff00, 0x00000200)
    data[0x100:0x110] = b"SEGA MEGA DRIVE "
    data[0x1f0:0x1f3] = b"U  "
    data[0x200:0x202] = bytes.fromhex("60 FE")  # 68000: branch to itself
    (output / "Genesis cartridge.bin").write_bytes(fit_checksum(data, crc))

    data, crc = rom("sms", "alexkidd")
    data[:4] = bytes.fromhex("F3 76 18 FD")  # Z80: DI, HALT, loop
    (output / "Master System cartridge.sms").write_bytes(fit_checksum(data, crc))

    data, crc = rom("gba", "2048")
    pack_into("<I", data, 0, 0xeafffffe)  # ARM: branch to itself
    data[0xa0:0xad] = b"FBA PROBE   A"
    data[0xb2] = 0x96
    data[0xbd] = (-0x19 - sum(data[0xa0:0xbd])) & 255
    (output / "Handheld cartridge.gba").write_bytes(fit_checksum(data, crc))

    data, crc = rom("fds", "19neunzehn")
    for side in range(len(data) // 65500):
        offset = side * 65500
        data[offset:offset+15] = b"\x01*NINTENDO-HVC*"
        data[offset+56:offset+58] = b"\x02\x00"  # no user program files
    (output / "Famicom disk.fds").write_bytes(fit_checksum(data, crc))
    data, crc = rom("fds", "fdsbios")
    data[:5] = bytes.fromhex("78 D8 4C 02 E0")
    for vector in (0x1ffa, 0x1ffc, 0x1ffe):
        pack_into("<H", data, vector, 0xe000)
    with ZipFile(output / "fdsbios.zip", "w") as out:
        out.writestr("fdsbios.nes", fit_checksum(data, crc))

    # Correct iNES signature, length and alternate CRC, but NOT the verified SHA-256.
    fake = bytearray(262160)
    fake[:16] = b"NES\x1a\x08\x10\x10" + bytes(9)
    (output / "Unverified NES collision.nes").write_bytes(fit_checksum(fake, 0xed1d282b))


if __name__ == "__main__":
    make(*map(Path, sys.argv[1:]))
