#!/usr/bin/env python3
"""Generate a test-only Gridlee hardware probe, not the original game's ROM data.

A tiny hand-written 6809 loop mirrors inputs and DIP bits to pixels. All other
bytes are synthetic. Match the pinned driver's archive checksums by filling an
unused four-byte field in each synthetic image. This fixture is written only
to the smoke test's temporary directory and is never packaged with the app.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile
from zlib import crc32


def fit_checksum(data, target):
    at = len(data) - 16  # outside our program; do not touch the reset vector
    baseline = crc32(data)
    basis = {}
    for bit in range(32):
        byte, mask = at + bit // 8, 1 << (bit % 8)
        data[byte] ^= mask
        residue = crc32(data) ^ baseline
        data[byte] ^= mask
        guess = 1 << bit
        while residue:
            lead = residue.bit_length() - 1
            if lead not in basis:
                basis[lead] = residue, guess
                break
            delta, other = basis[lead]
            residue ^= delta
            guess ^= other
    assert len(basis) == 32
    residue, patch = baseline ^ target, 0
    while residue:
        delta, guess = basis[residue.bit_length() - 1]
        residue ^= delta
        patch ^= guess
    data[at:at + 4] = (int.from_bytes(data[at:at + 4], 'little') ^ patch).to_bytes(4, 'little')
    assert crc32(data) == target
    return data


def fixture(dat, archive, ini):
    driver = ET.parse(dat).find('./game[@name="gridlee"]')
    entries = driver.findall('rom')
    assert len(entries) == 13
    code = bytes.fromhex('''
        1A50 10CE07FF
        8622 B70800
        8658 B79838
        8610 B79839
        7F9380 B69502 B70801 B69600 B70802 20EF
    ''')
    with ZipFile(archive, 'w', ZIP_DEFLATED) as out:
        for index, rom in enumerate(entries):
            data = bytearray(int(rom.attrib['size']))
            if index == 0:
                data[:len(code)] = code
            elif index == 5:
                data[-2:] = b'\xa0\x00'  # 6809 reset vector
            elif index == 10:
                data[:] = bytes(i % 16 for i in range(len(data)))  # red ramp
            out.writestr(rom.attrib['name'], fit_checksum(data, int(rom.attrib['crc'], 16)))
    ini.parent.mkdir(parents=True, exist_ok=True)
    ini.write_text('cheat "Probe Color"\ntype 0\ndefault 0\n0 "Disabled"\n1 "Enabled", 0, 0x800, 0x55\n', encoding='utf-8')


if __name__ == '__main__':
    fixture(*(Path(arg) for arg in sys.argv[1:]))
