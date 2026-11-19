#!/usr/bin/env python3
"""A test-only 1942 board program that initializes the pinned DAT's score ranges.

Uses synthetic data and the same unused CRC-padding technique as the Gridlee
fixture. The tests never need the original game, and no fixture is packaged.
"""
import sys
import xml.etree.ElementTree as ET
from zipfile import ZIP_DEFLATED, ZipFile

from make_core_fixture import fit_checksum


def fixture(dat, archive):
    chips = ET.parse(dat).findall('./game[@name="1942"]/rom')
    assert chips[0].attrib['name'] == 'srb-03.m3' and chips[5].attrib['name'] == 'sr-01.c11'
    program = bytes.fromhex('''
        F3 31 F0EF
        3E2A 3201E8
        AF 3200E8 328FE9 3240E0 3247E0
        3E1E 32C0E9
        3E01 3228E0
        76
    ''')  # Score byte at $e801, then the four ranges' readiness sentinels.
    with ZipFile(archive, 'w', ZIP_DEFLATED) as out:
        for index, chip in enumerate(chips):
            data = bytearray(int(chip.attrib['size']))
            if index == 0:
                data[:len(program)] = program
            elif index == 5:
                data[:2] = b'\xf3\x76'  # Idle the sound CPU.
            out.writestr(chip.attrib['name'], fit_checksum(data, int(chip.attrib['crc'], 16)))


if __name__ == '__main__':
    fixture(*sys.argv[1:])
