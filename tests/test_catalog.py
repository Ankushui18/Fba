#!/usr/bin/env python3
"""Catalog contract independent of Android and Pillow; the generator needs Pillow."""
import sys
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from check_offline_apk import inspect_png
from prepare_game_catalog import SET, clean_cheat
from prepare_rom_targets import NAME, CRC

assets = ROOT / 'android/src/main/assets'
rows = {}
for line in (assets / 'game-catalog.tsv').read_text(encoding='utf-8').splitlines():
    row = line.split('\t')
    assert len(row) == 9 and row[1] and SET.fullmatch(row[0]) and row[0] not in rows
    rows[row[0]] = row
assert len(rows) == 10095
assert rows['1942'][8] == 'Shooters' and rows['sf2'][8] == 'Fighting'
assert rows['mslug'][2] and rows['mslug'][4] == '1996'

with ZipFile(assets / 'game-art.zip') as art:
    names = art.namelist()
    assert len(names) == len(set(names)) == 3847
    for entry in art.infolist():
        name = entry.filename
        assert name.endswith('.png') and SET.fullmatch(name[:-4]) and entry.file_size <= 128 * 1024
        inspect_png(art.read(entry), name)
    assert 'mslug.png' in names and 'sf2.png' in names

with ZipFile(assets / 'game-cheats.zip') as cheats:
    entries = cheats.namelist()
    assert len(entries) == len(set(entries)) == 3131
    for entry in cheats.infolist():
        assert entry.filename.endswith('.ini') and entry.filename[:-4] in rows
        assert 0 < entry.file_size <= 256 * 1024
        data = cheats.read(entry)
        assert clean_cheat(data) == data, f'unsanitized cheat: {entry.filename}'
    assert b'Infinite Time' in cheats.read('mslug.ini')
    assert 'sf2.ini' in entries and 'kof97.ini' in entries

valid = b'cheat "Test"\ntype 0\ndefault 0\n0 "Off"\n1 "On", 0, 0x10, 0x11\n'
assert clean_cheat(valid) == valid
assert not clean_cheat(valid.replace(b'default 0', b'default 1'))
assert not clean_cheat(valid.replace(b'0 "Off"', b'0 "Off", 0, 0x10, 0x11'))
assert not clean_cheat(valid.replace(b'0, 0x10', b'8, 0x10'))
assert not clean_cheat(valid.replace(b'0x11', b'0x111'))
assert not clean_cheat(valid.replace(b'0x10', b'0x00'))
assert not clean_cheat(valid.replace(b'1 "On"', b'2 "On"'))
assert b'include' not in clean_cheat(b'include "./another-file.ini"\n' + valid)
with ZipFile(assets / 'ips-targets.zip') as chips:
    names = chips.namelist()
    assert len(names) == len(set(names)) == 8545
    for entry in chips.infolist():
        assert entry.filename.endswith('.tsv') and entry.filename[:-4] in rows
        records = chips.read(entry).decode('ascii').splitlines()
        assert 0 < len(records) <= 2048 and len(records) == len(set(records))
        for record in records:
            filename, size, crc = record.split('\t')
            assert NAME.fullmatch(filename) and 0 < int(size) <= 64 * 1024 * 1024 and CRC.fullmatch(crc) and int(crc, 16)
print('Game metadata, PNG thumbnails, bounded cheat grammar and exact ROM chip index passed.')
