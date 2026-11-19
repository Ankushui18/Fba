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


def loose_cart(mapper, prg, chr_, *, nes20=False, battery=False, pal=False, nonce=0):
    """Test program waits for Start, sets the backdrop, and plays a pulse tone."""
    data = bytearray(16 + prg * 16384 + chr_ * 8192)
    data[:4] = b"NES\x1a"
    data[4:8] = bytes((prg, chr_, ((mapper & 15) << 4) | (2 if battery else 0),
                      (mapper & 0xf0) | (8 if nes20 else 0)))
    if nes20:
        data[10] = 0x70 if battery else 0  # 8 KiB NVRAM if present
        data[11] = 0 if chr_ else 7       # 8 KiB CHR RAM
        data[12] = int(pal)
    start = 16 + prg * 16384 - 8192  # $e000 in the last mapped PRG bank
    code = bytearray.fromhex(
        "78 D8 A2 FF 9A A9 00 8D 00 20 8D 01 20 "  # reset, leave rendering off
        "2C 02 20 10 FB 2C 02 20 10 FB "           # wait two vblanks
        "A9 01 8D 16 40 A9 00 8D 16 40 "            # latch both controllers
        "AD 16 40 AD 16 40 AD 16 40 AD 16 40 29 01 F0 E6 "  # wait for P1 Start (4th bit)
        "A9 5A 8D 00 60 "                           # marker for battery tests
        "A9 3F 8D 06 20 A9 00 8D 06 20 A9 21 8D 07 20 "      # palette $3f00
        "A9 00 8D 06 20 8D 06 20 "                 # return PPU address to pattern table
        "A9 01 8D 15 40 A9 7F 8D 00 40 A9 70 8D 02 40 A9 00 8D 03 40"  # pulse channel
    )
    loop = 0xe000 + len(code)
    code += bytes((0x4c, loop & 0xff, loop >> 8))
    data[start:start+len(code)] = code
    data[start+0x1f00] = nonce
    for vector in (0x1ffa, 0x1ffc, 0x1ffe):
        pack_into("<H", data, start + vector, 0xe000)
    return data


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

    # No DAT CRC adjustment for these ordinary iNES/NES 2.0 cartridges.
    (output / "Homebrew.nes").write_bytes(loose_cart(0, 2, 1, battery=True))
    (output / "Headerless filename").write_bytes(loose_cart(0, 1, 1, battery=True, nonce=2))
    (output / "MMC1.NES").write_bytes(loose_cart(1, 8, 16, nes20=True, battery=True))
    (output / "UxROM.nes").write_bytes(loose_cart(2, 8, 0))
    (output / "CNROM.nes").write_bytes(loose_cart(3, 2, 4))
    (output / "MMC3 PAL.nes").write_bytes(loose_cart(4, 16, 16, nes20=True, pal=True))
    (output / "AxROM.nes").write_bytes(loose_cart(7, 2, 0))
    (output / "GxROM.nes").write_bytes(loose_cart(66, 2, 4))

    # Correct iNES signature, length and alternate CRC, but NOT the verified SHA-256.
    (output / "Unverified NES collision.nes").write_bytes(fit_checksum(loose_cart(1, 8, 16), 0xed1d282b))


if __name__ == "__main__":
    make(*map(Path, sys.argv[1:]))
