#!/usr/bin/env python3
"""Regenerate the allowlisted, pixel-only images from the supplied legacy APK.

Pillow is only needed to regenerate these checked-in drawables, not to build.
"""
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import sys

ROOT = Path(__file__).resolve().parent.parent
ART_DIR = ROOT / 'android/src/main/res/drawable-nodpi'
LEGACY_SHA256 = '9099a973c4bab3c8634583e5d3630f8f0642d7d3c993e698e6ea64120f36f775'

# App artwork, not dependency icons, web controls, QR images or updater art.
_UI_PNGS = '''border btn_check_off btn_check_off_holo_light btn_check_on
btn_check_on_selected check_off check_on collapsed expanded file_icon games image_check
menu_cheat menu_dipswitch menu_fold_ori menu_fold_sel menu_gameinfo menu_keyconfig
menu_list menu_loadstate menu_more menu_quit menu_restart menu_savestate menu_screenshot
menu_setting menu_softinput menu_unfold_ori menu_unfold_sel move no_screenshot
no_screenshot_cn start tab_middle tab_middle_f updir'''.split()
_UI_PNGS += [f'button_{key}{suffix}' for key in '1 2 3 4 a b l1 r1 x y'.split() for suffix in ('', '_gray')]
ART = {f'legacy_ui_{name}.png': f'res/drawable/{name}.png' for name in _UI_PNGS}
for name in ('list_selector_background_disabled', 'list_selector_background_focus',
             'list_selector_background_longpress', 'list_selector_background_pressed',
             'menu_item_bg_sel', 'top_bar_bg'):
    ART[f'legacy_ui_{name}.png'] = f'res/drawable/{name}.9.png'
ART.update({
    'legacy_ui_dir.png': 'res/drawable/dir.jpg',
    'legacy_ui_launcher.png': 'res/mipmap-xxxhdpi-v4/ic_launcher_fba.png',
    'legacy_ui_launcher_round.png': 'res/mipmap-xxxhdpi-v4/ic_launcher_fba_round.png',
    'legacy_ui_launcher_background.png': 'res/mipmap-xxxhdpi-v4/ic_launcher_background.png',
    'legacy_ui_launcher_foreground.png': 'res/mipmap-xxxhdpi-v4/ic_launcher_foreground.png',
    'legacy_ui_arrow.png': 'res/drawable-xxxhdpi-v4/ic_arrow_down_24dp.png',
    'legacy_ui_info.png': 'res/drawable-hdpi-v4/menu_info.png',
    'legacy_ui_banner.png': 'res/drawable-hdpi-v4/banner.jpg',
    'legacy_ui_blank_state.png': 'res/drawable-nodpi-v4/blank_state.png',
    'legacy_ui_no_pic.png': 'res/drawable-nodpi-v4/no_pic.png',
    'legacy_ui_ex_doc.png': 'res/mipmap-hdpi-v4/ex_doc.png',
    'legacy_ui_ex_folder.png': 'res/mipmap-hdpi-v4/ex_folder.png',
    'legacy_ui_sorting.png': 'res/mipmap-hdpi-v4/sorting.png',
    'legacy_ui_path_sep.png': 'res/mipmap-xxhdpi-v4/path_sep.png',
})

_skin_images = ['ANALOG', 'ANALOG_2', 'icon1', 'icon2', 'BUTTON_DPAD',
                *('BUTTON_DPAD_' + direction for direction in ('UP', 'DOWN', 'LEFT', 'RIGHT')),
                *('BUTTON_PLAYER' + player for player in ('', '2', '3', '4'))]
_skin_images += [f'BUTTON_{key}{suffix}' for key in '1 2 3 4 A B COIN L1 R1 START X Y'.split() for suffix in ('', '_P')]
for label, original in (('classic', 'skin_default'), ('chrome', 'skin_1')):
    names = _skin_images + (['BUTTON_PAUSE', 'BUTTON_PAUSE_P'] if label == 'classic' else [])
    for name in names:
        ART[f'legacy_{label}_{name.lower()}.png'] = f'assets/{original}/{name}.png'


def main():
    from PIL import Image

    path = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'fba4droid_18644.apk'
    if sha256(path.read_bytes()).hexdigest() != LEGACY_SHA256:
        raise SystemExit('Unexpected legacy APK SHA-256; the art allowlist is specific to the supplied archive.')
    ART_DIR.mkdir(parents=True, exist_ok=True)
    with ZipFile(path) as z:
        for filename, entry in sorted(ART.items()):
            if z.getinfo(entry).file_size > 256 * 1024:
                raise SystemExit(f'Unexpected size: {entry}')
            with Image.open(BytesIO(z.read(entry)), formats=('PNG', 'JPEG')) as image:
                if not (0 < image.width <= 1024 and 0 < image.height <= 1024) or getattr(image, 'n_frames', 1) != 1:
                    raise SystemExit(f'Unexpected image: {entry}')
                # Re-encode only pixels; no original ZIP or binary XML entries and no image metadata.
                rgba = Image.frombytes('RGBA', image.size, image.convert('RGBA').tobytes())
                rgba.save(ART_DIR / filename, format='PNG', optimize=True)
    print(f'Imported {len(ART)} static legacy drawables into {ART_DIR.relative_to(ROOT)}.')


if __name__ == '__main__':
    main()
