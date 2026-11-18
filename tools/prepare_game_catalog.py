#!/usr/bin/env python3
"""Produce data-only thumbnail, metadata and FBNeo cheat indexes from pinned inputs."""
import argparse
import csv
import hashlib
import io
import re
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

from import_legacy_art import LEGACY_SHA256

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'android/src/main/assets'
ARCADE_DAT = 'FinalBurn Neo (ClrMame Pro XML, Arcade only).dat'
DAT_SHA256 = 'ca3551b1eb94d53f27ed2398a6e9012ae626429d0a3259f6a63fb75f51a57989'
# Group numeric genre codes in the supplied legacy XML into usable library filters.
GENRES = {
    '0': 'Other', '1': 'Shooters', '2': 'Action', '3': 'Fighting', '4': 'System files',
    '5': 'Casual & puzzle', '6': 'Casual & puzzle', '7': 'Casual & puzzle', '8': 'Action',
    '9': 'Other', '10': 'Casual & puzzle', '11': 'Action', '12': 'Casual & puzzle',
    '13': 'Casual & puzzle', '14': 'Sports', '15': 'Sports', '16': 'Other',
    '17': 'Casual & puzzle', '18': 'Driving', '19': 'Shooters',
}
SET = re.compile(r'[a-z0-9_][a-z0-9_-]{0,89}\Z')
CHEAT = re.compile(r'cheat\s+"([^"\r\n]*)"(?:\s+advanced)?\s*\{?\s*(?://.*)?\Z', re.I)
OPTION = re.compile(r'(\d{1,3})\s+"([^"\r\n]+)"\s*(,.*)?\Z')
INTEGER = re.compile(r'(?:[0-9]{1,10}|0[xX][0-9a-fA-F]{1,8})\Z')
MAX_GROUPS, MAX_OPTIONS, MAX_CHOICES = 160, 400, 96


def label(text, length=220):
    return ' '.join(''.join(c if not unicodedata.category(c).startswith('C') else ' ' for c in text or '').split())[:length]


def cheat_label(text):
    return label(text).encode('utf-8')[:110].decode('utf-8', errors='ignore').replace('"', "'")


def num(text):
    text = text.strip()
    if not INTEGER.fullmatch(text):
        raise ValueError('not a bounded number')
    return int(text, 16 if text.lower().startswith('0x') else 10)


def clean_cheat(data):
    """Allow only named, disabled-by-default arcade cheats containing byte triples."""
    try:
        text = data.decode('utf-8-sig')
    except UnicodeDecodeError:
        text = data.decode('gb18030', errors='replace')
    groups, current = [], None
    for orig in text.splitlines():
        line = orig.strip()
        if not line or line == '}' or line.startswith('//'):
            continue
        start = CHEAT.fullmatch(line)
        if start:
            current = [cheat_label(start[1]), 0, [], True]
            groups.append(current)
            continue
        if current is None:
            continue
        typ = re.fullmatch(r'type\s+([012])(?:\s*//.*)?', line, re.I)
        if typ:
            current[1] = int(typ[1])
            continue
        if re.fullmatch(r'default\s+0(?:\s*//.*)?', line, re.I):
            continue
        opt = OPTION.fullmatch(line.split('//', 1)[0].rstrip())
        if not opt:
            current[3] = False
            continue
        index = int(opt[1])
        kind = cheat_label(opt[2])
        try:
            args = [num(x) for x in opt[3][1:].split(',')] if opt[3] else []
            if not kind or index >= MAX_CHOICES or index != len(current[2]):
                raise ValueError('noncontiguous choices')
            if index == 0 and args or index > 0 and (not args or len(args) % 3 or len(args) > 3 * 64):
                raise ValueError('not a byte-triple option')
            triples = list(zip(args[0::3], args[1::3], args[2::3]))
            if any(cpu > 7 or addr == 0 or addr > 0x7fffffff or value > 255 for cpu, addr, value in triples):
                raise ValueError('byte triple out of range')
        except ValueError:
            current[3] = False
            continue
        values = ''.join(f', {cpu}, 0x{addr:X}, 0x{byte:02X}' for cpu, addr, byte in triples)
        current[2].append(f'{index} "{kind}"{values}')
    out = []
    count = 0
    for title, typ, options, valid in groups:
        if not valid or not title or len(options) < 2:
            continue
        if len(out) >= MAX_GROUPS or count + len(options) > MAX_OPTIONS:
            break
        out.append(f'cheat "{title}"\ntype {typ}\ndefault 0\n' + '\n'.join(options))
        count += len(options)
    return ('\n\n'.join(out) + '\n').encode('utf-8') if out else b''


def image_pack(original):
    from PIL import Image
    with ZipFile(io.BytesIO(original.read('assets/gameicon.zip'))) as source, \
            ZipFile(ASSETS / 'game-art.zip', 'w', ZIP_DEFLATED, compresslevel=9) as out:
        images = 0
        for name in sorted(set(source.namelist())):
            stem = name[:-4] if name.endswith('.ico') else ''
            if not SET.fullmatch(stem) or source.getinfo(name).file_size > 65536:
                continue
            with Image.open(io.BytesIO(source.read(name)), formats=('ICO',)) as icon:
                if not 0 < icon.width <= 128 or not 0 < icon.height <= 128:
                    continue
                rgba = Image.frombytes('RGBA', icon.size, icon.convert('RGBA').tobytes())
                buf = io.BytesIO()
                rgba.save(buf, 'PNG', optimize=True)
                write(out, stem + '.png', buf.getvalue())
                images += 1
        return images


def write(archive, name, data):
    from zipfile import ZipInfo
    archive.writestr(ZipInfo(name), data, compress_type=ZIP_DEFLATED, compresslevel=9)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('core', type=Path)
    parser.add_argument('--apk', type=Path, default=ROOT / 'fba4droid_18644.apk')
    args = parser.parse_args()
    if hashlib.sha256(args.apk.read_bytes()).hexdigest() != LEGACY_SHA256:
        raise SystemExit('Unexpected legacy APK SHA-256.')
    ASSETS.mkdir(parents=True, exist_ok=True)
    dat_file = args.core / 'dats' / ARCADE_DAT
    if hashlib.sha256(dat_file.read_bytes()).hexdigest() != DAT_SHA256:
        raise SystemExit('Use the FBNeo arcade DAT at the pinned core revision.')
    dat = ET.parse(dat_file)
    with ZipFile(args.apk) as original:
        original_sets = {r.findtext('name'): r for r in ET.fromstring(original.read('assets/drvrom.xml')).findall('rom')}
        boards = {name: label(r.findtext('board'), 72) for name, r in original_sets.items()}
        genres = {name: GENRES.get(r.findtext('catogary'), '') for name, r in original_sets.items()}
        cn = dict(row for row in csv.reader(original.read('assets/listcn.txt').decode('gb18030', errors='replace').splitlines()) if len(row) == 2)
        games = {name: [name, name, label(cn.get(name)), board, '', '', '', '', genres.get(name, '')]
                 for name, board in boards.items() if name and SET.fullmatch(name)}
        arcade = set()
        for g in dat.getroot().findall('game'):
            name = g.attrib['name']
            if not SET.fullmatch(name):
                continue
            arcade.add(name)
            source = g.attrib.get('sourcefile', '').split('/')[0]
            board = boards.get(name) or {'cps1':'CPS1', 'cps2':'CPS2', 'cps3':'CPS-3', 'neogeo':'Neo Geo', 'pgm':'PolyGameMaster'}.get(source, '')
            games[name] = [name, label(g.findtext('description')) or name, label(cn.get(name)), board,
                           label(g.findtext('year'), 18), label(g.findtext('manufacturer'), 120),
                           label(g.attrib.get('cloneof'), 90), label(g.attrib.get('romof'), 90), genres.get(name, '')]
        with (ASSETS / 'game-catalog.tsv').open('w', encoding='utf-8', newline='\n') as out:
            for row in sorted(games.values()):
                out.write('\t'.join(row) + '\n')
        images = image_pack(original)
        with ZipFile(io.BytesIO(original.read('assets/cheat_en.zip'))) as en, \
                ZipFile(io.BytesIO(original.read('assets/cheat.zip'))) as cn, \
                ZipFile(ASSETS / 'game-cheats.zip', 'w', ZIP_DEFLATED, compresslevel=9) as out:
            cheats = 0
            for name in sorted(arcade):
                entry = name + '.ini'
                for source in (en, cn):
                    try:
                        if source.getinfo(entry).file_size > 512 * 1024:
                            continue
                        data = clean_cheat(source.read(entry))
                    except KeyError:
                        continue
                    if data and len(data) <= 256 * 1024:
                        write(out, entry, data)
                        cheats += 1
                        break
    print(f'{len(games)} game records, {images} thumbnails, {cheats} exact-set cheat definitions.')


if __name__ == '__main__':
    main()
