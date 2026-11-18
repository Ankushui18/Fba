#!/usr/bin/env python3
"""Build-time overlay for the opt-in local multiplayer APK, no network components."""
import sys
from pathlib import Path

LINK_PERMISSIONS = {
    'android.permission.INTERNET': None,
    'android.permission.ACCESS_LOCAL_NETWORK': None,
    'android.permission.BLUETOOTH': 30,
    'android.permission.BLUETOOTH_CONNECT': None,
}


def manifest_with_link(text):
    anchor = '    <application\n'
    if text.count(anchor) != 1 or '<uses-permission' in text:
        raise ValueError('Expected the permission-free base manifest')
    lines = [f'    <uses-permission android:name="{name}"' + (f' android:maxSdkVersion="{sdk}"' if sdk else '') + ' />\n'
             for name, sdk in LINK_PERMISSIONS.items()]
    return text.replace(anchor, ''.join(lines) + anchor)


if __name__ == '__main__':
    src, dst = map(Path, sys.argv[1:])
    dst.write_text(manifest_with_link(src.read_text(encoding='utf-8')), encoding='utf-8')
