#!/usr/bin/env python3
"""Build the exact chip/size/CRC index for the IPS picker (metadata only)."""
import argparse
import hashlib
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

from prepare_game_catalog import ARCADE_DAT, DAT_SHA256, ASSETS, SET, write

NAME = re.compile(r'[a-zA-Z0-9_][a-zA-Z0-9 ._()+\-]{0,139}\Z')
CRC = re.compile(r'[a-f0-9]{8}\Z')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('core', type=Path)
    args = parser.parse_args()
    data = (args.core / 'dats' / ARCADE_DAT).read_bytes()
    if hashlib.sha256(data).hexdigest() != DAT_SHA256:
        raise SystemExit('Use the pinned FBNeo arcade DAT.')
    games = ET.fromstring(data).findall('game')
    count = 0
    with ZipFile(ASSETS / 'ips-targets.zip', 'w') as index:
        for g in sorted(games, key=lambda g: g.attrib['name']):
            name = g.attrib['name']
            if not SET.fullmatch(name):
                continue
            rows = set()
            for r in g.findall('rom'):
                chip = r.attrib.get('name', '')
                length = int(r.attrib.get('size', '0'))
                crc = r.attrib.get('crc', '')
                if NAME.fullmatch(chip) and CRC.fullmatch(crc) and crc != '00000000' and 0 < length <= 64 * 1024 * 1024:
                    rows.add(f'{chip}\t{length}\t{crc}\n')
            if rows:
                write(index, name + '.tsv', ''.join(sorted(rows)).encode('ascii'))
                count += 1
    print(f'{count} exact-set chip lists in ips-targets.zip.')


if __name__ == '__main__':
    main()
