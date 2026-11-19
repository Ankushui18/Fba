#!/usr/bin/env python3
"""Fail-closed packaging/privacy checks for FBA Arcade (not an antivirus)."""
import re
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
from zipfile import ZIP_STORED, ZipFile
from zlib import crc32

from import_legacy_art import ART
from link_manifest import LINK_PERMISSIONS


ART_PATHS = {f"res/drawable-nodpi-v4/{name}" for name in ART}
LIBRARY_ICONS = {f"res/drawable/ic_library_{name}.xml" for name in ("add", "clear", "more", "pad", "play", "settings")}
APP = "io.github.ankushui18.fbaoffline"  # Java/JNI namespace, independent of installed application ID
PACKAGES = {
    APP: ("", "FBA Arcade"),
    "io.github.ankushui18.fbaarcade.test": ("", "FBA Arcade"),
    "io.github.ankushui18.fbaarcade.recovery": ("-recovery", "FBA Arcade Recovery"),
    "io.github.ankushui18.fbaarcade.recovery.preview": ("-recovery-preview", "FBA Arcade Recovery Preview"),
}
CATALOG_ASSETS = {"assets/game-art.zip", "assets/game-catalog.tsv", "assets/game-cheats.zip", "assets/ips-targets.zip"}
NES_INDEX_ASSET = "assets/nes-sets.tsv"
CONSOLE_INDEX_ASSET = "assets/console-sets.tsv"
ROOT = Path(__file__).resolve().parents[1]
CATALOG_DIR = ROOT / "android/src/main"
FBNEO_LICENSE = ROOT / "LICENSES/FBNeo-LICENSE.txt"
NATIVE = re.compile(r"lib/(arm64-v8a|x86_64)/lib(retro|offlinefb|hqx)\.so\Z")
ASSETS = {
    "assets/CORE-SOURCE.txt", "assets/FBNeo-LICENSE.txt", *CATALOG_ASSETS,
    *{"assets/licenses/" + name + ".txt" for name in ("dr_libs", "gba", "hqx", "hqx-authors", "libchdr", "lzma", "snes", "ugui")},
}
MARKERS = (
    b"baidu.com", b"qq.com", b"cn/waps", b"com/tencent/bugly",
    b"Landroid/webkit/", b"Ldalvik/system/DexClassLoader;", b"Ldalvik/system/InMemoryDexClassLoader;",
    b"Ljavax/net/ssl/", b"Landroid/app/DownloadManager;",
    b"REQUEST_INSTALL_PACKAGES", b"android.intent.action.INSTALL_PACKAGE",
)
LINK_JAVA_NETWORK = {f"Ljava/net/{name};".encode() for name in (
    "InetAddress", "Inet4Address", "InterfaceAddress", "NetworkInterface", "InetSocketAddress", "SocketAddress",
    "Socket", "ServerSocket", "SocketException", "SocketTimeoutException", "UnknownHostException",
)}
NETWORK_OR_EXEC = {
    "socket", "socketpair", "connect", "bind", "listen", "accept", "accept4",
    "send", "sendto", "sendmsg", "recv", "recvfrom", "recvmsg", "getaddrinfo", "gethostbyname",
    "dlopen", "android_dlopen_ext", "popen", "system", "execve", "execv", "execl", "execvp", "posix_spawn",
}
PLATFORM_LIBS = {"libc.so", "libm.so", "libdl.so", "libjnigraphics.so", "liblog.so", "libz.so", "libandroid.so"}


def require(ok, message):
    if not ok:
        raise SystemExit(f"APK verification failed: {message}")


def inspect_elf(data, label, abi, lib):
    require(data[:6] == b"\x7fELF\x02\x01", f"{label} is not a little-endian 64-bit ELF")
    expected = {"arm64-v8a": 183, "x86_64": 62}[abi]
    require(struct.unpack_from("<HH", data, 16) == (3, expected), f"unexpected architecture/type: {label}")
    table = struct.unpack_from("<Q", data, 32)[0]
    row, count = struct.unpack_from("<HH", data, 54)
    require(row == 56 and table + row * count <= len(data), f"invalid program header table: {label}")
    loads = 0
    for i in range(count):
        kind, flags, offset, vaddr, _, _, _, align = struct.unpack_from("<IIQQQQQQ", data, table + row * i)
        if kind == 1:  # PT_LOAD
            loads += 1
            require(align >= 16384 and align & (align - 1) == 0 and (vaddr - offset) % align == 0,
                    f"ELF segment not 16 KiB aligned: {label}")
            require(flags & 3 != 3, f"writable and executable segment: {label}")
        if kind == 0x6474E551:  # PT_GNU_STACK
            require(not flags & 1, f"executable stack: {label}")
    require(loads > 0, f"no loadable ELF segments: {label}")
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / f"lib{lib}.so"
        path.write_bytes(data)
        syms = subprocess.check_output(["readelf", "-W", "--dyn-syms", str(path)], text=True)
        imports = {m.split("@")[0] for m in re.findall(r"\bUND\s+(\S+)\s", syms)}
        require(not (imports & NETWORK_OR_EXEC), f"unexpected native imports: {imports & NETWORK_OR_EXEC} in {label}")
        dyn = subprocess.check_output(["readelf", "-W", "-d", str(path)], text=True)
        deps = set(re.findall(r"\(NEEDED\).*?\[(.*?)\]", dyn))
        allowed = PLATFORM_LIBS | ({"libretro.so", "libhqx.so"} if lib == "offlinefb" else set())
        require(deps <= allowed and "(TEXTREL)" not in dyn, f"unexpected native dependency/relocations: {label}: {deps}")
        if lib == "offlinefb":
            require({"libretro.so", "libhqx.so"} <= deps, f"JNI frontend has missing core or filter linkage: {label}")
    require(not any(x in data.lower() for x in MARKERS[:4]), f"old tracker marker in {label}")


def inspect_manifest(aapt2, apk, link):
    tree = subprocess.check_output([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk], text=True)
    tags = re.findall(r"^\s*E: (\S+)", tree, re.MULTILINE)
    permission_tags = ["uses-permission"] * len(LINK_PERMISSIONS) if link else []
    require(tags == ["manifest", "uses-sdk", *permission_tags, "application", "activity", "intent-filter", "action", "category"],
            f"unexpected manifest elements (including permissions/components): {tags}")
    if link:
        nodes = re.findall(r"E: uses-permission[^\n]*\n((?:\s*A: [^\n]*\n)+)", tree)
        found = {}
        for node in nodes:
            name = re.search(r':name\([^)]*\)="([^"]+)"', node)
            max_sdk = re.search(r':maxSdkVersion\([^)]*\)=([^\s]+)', node)
            require(name is not None and name[1] not in found, "duplicate or invalid permission")
            found[name[1]] = int(max_sdk[1], 0) if max_sdk else None
        require(found == LINK_PERMISSIONS, f"unexpected Link permissions: {found}")
    for attr in ("allowBackup", "debuggable", "extractNativeLibs", "usesCleartextTraffic"):
        require(re.search(rf":{attr}\([^)]*\)=(?:false|\(type 0x12\)0x0)(?:\s|$)", tree),
                f"manifest must explicitly set android:{attr}=false")
    require("android.intent.action.MAIN" in tree and "android.intent.category.LAUNCHER" in tree and
            "MainActivity" in tree, "missing expected launcher")
    require(re.search(r":exported\([^)]*\)=(?:true|\(type 0x12\)0xffffffff)(?:\s|$)", tree),
            "the launcher must be explicitly exported")
    badging = subprocess.check_output([aapt2, "dump", "badging", apk], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.M)
    require(package and package[1] in PACKAGES, "unexpected application ID or version metadata")
    version_code = int(package[2])
    require(version_code < 8 or link, "current APKs must include local multiplayer")
    # Older pinned downloads still have their original labels. The app IDs are unchanged.
    suffix, label = PACKAGES[package[1]] if version_code < 7 else ("", "FBA Arcade")
    released_versions = {7: "0.5.2", 8: "0.5.3", 9: "0.5.4", 10: "0.5.5", 11: "0.5.6", 12: "0.5.7", 13: "0.5.8"}
    version = re.escape(released_versions[version_code]) if version_code in released_versions else r"\d+\.\d+\.\d+"
    expected_version = version + re.escape(suffix + ("-link" if link else ""))
    require(re.fullmatch(expected_version, package[3]), "wrong APK version or variant")
    require(f"application-label:'{label}'" in badging, "unexpected launcher label")
    require(re.search(r"^(?:sdkVersion|minSdkVersion):'26'$", badging, re.M) and
            "targetSdkVersion:'37'" in badging, "unexpected effective SDK levels")
    return version_code


def inspect_dex(dex, link):
    require(dex.startswith(b"dex\n") and len(dex) >= 112, "invalid DEX header")
    string_count, strings, type_count, types = struct.unpack_from("<IIII", dex, 56)
    class_count, classes = struct.unpack_from("<II", dex, 96)
    require(strings + 4 * string_count <= len(dex) and types + 4 * type_count <= len(dex) and
            0 < class_count <= 512 and classes + 32 * class_count <= len(dex), "invalid or unexpected DEX layout")
    prefix = b"L" + APP.replace(".", "/").encode() + b"/"
    for i in range(class_count):
        class_idx, = struct.unpack_from("<I", dex, classes + 32 * i)
        require(class_idx < type_count, "invalid class type")
        name_idx, = struct.unpack_from("<I", dex, types + 4 * class_idx)
        require(name_idx < string_count, "invalid class name")
        offset, = struct.unpack_from("<I", dex, strings + 4 * name_idx)
        # Each MUTF-8 string is preceded by a ULEB128 character count.
        for _ in range(5):
            require(offset < len(dex), "invalid class name offset")
            byte = dex[offset]
            offset += 1
            if byte < 128:
                break
        require(dex[offset:offset + len(prefix)] == prefix, "unexpected bundled Java class namespace")
    network = set(re.findall(rb"Ljava/net/[A-Za-z0-9$/_]+;", dex))
    require(network <= (LINK_JAVA_NETWORK if link else set()), f"unexpected Java networking APIs: {network}")
    if link:
        require(b"Ljava/net/Socket;" in network and prefix + b"LinkWire;" in dex, "missing local link implementation")
    else:
        require(b"Landroid/bluetooth/" not in dex and b"Ljava/net/" not in dex, "wireless code in offline APK")
    require(not any(marker.lower() in dex.lower() for marker in MARKERS), "network/install/tracker/dynamic DEX marker")


def inspect_png(data, name):
    # Legacy art is flattened to RGBA; don't permit metadata, animation, or a trailing payload.
    require(45 <= len(data) <= 256 * 1024 and data[:8] == b"\x89PNG\r\n\x1a\n", f"invalid art: {name}")
    size, kind, width, height, format_bytes = struct.unpack_from(">I4sII5s", data, 8)
    require(size == 13 and kind == b"IHDR" and 0 < width <= 1024 and 0 < height <= 1024 and
            format_bytes == b"\x08\x06\x00\x00\x00", f"art must be a static RGBA PNG: {name}")
    pos = 8
    chunks = 0
    while pos + 12 <= len(data):
        size, kind = struct.unpack_from(">I4s", data, pos)
        end = pos + size + 12
        require(end <= len(data), f"truncated PNG chunk: {name}")
        require(crc32(data[pos + 4:end - 4]) == struct.unpack_from(">I", data, end - 4)[0], f"bad PNG checksum: {name}")
        if kind == b"IEND":
            require(size == 0 and chunks >= 2 and end == len(data), f"unexpected PNG end: {name}")
            return
        require(kind == (b"IHDR" if chunks == 0 else b"IDAT"), f"unexpected PNG chunk {kind!r}: {name}")
        chunks += 1
        pos = end
    require(False, f"missing PNG end: {name}")


def inspect_console_drivers(payload, index, abi):
    rows = [line.split(b"\t", 2)[:2] for line in index.splitlines()]
    expected = {prefix + b"_" + short for prefix, short in rows}
    prefixes = b"|".join(sorted({prefix for prefix, _ in rows}))
    symbols = re.compile(rb"\x00((?:" + prefixes + rb")_[a-z0-9_-]{1,80})(?=\x00)")
    missing = expected - {match[1] for match in symbols.finditer(payload)}
    require(not missing, f"console index names drivers missing in {abi}: {sorted(missing)[:6]}")


def main(aapt2, apk, link=False):
    version_code = inspect_manifest(aapt2, apk, link)
    with ZipFile(apk) as z:
        require(z.testzip() is None, "archive CRC error")
        entries = set(z.namelist())
        require(len(entries) == len(z.infolist()), "duplicate archive entries")
        mandatory = {"AndroidManifest.xml", "classes.dex", "resources.arsc", "res/drawable/ic_search.xml"} | ASSETS | ART_PATHS
        if version_code >= 9:
            mandatory |= LIBRARY_ICONS
        if version_code >= 11:
            mandatory.add(CONSOLE_INDEX_ASSET)
        elif version_code == 10:
            mandatory.add(NES_INDEX_ASSET)
        if link:
            patch, source = ("assets/FBNeo-SESSION.patch", "fbneo-session.patch") if version_code >= 11 else (
                "assets/FBNeo-LINK.patch", "fbneo-lockstep.patch")
            mandatory.add(patch)
            require(patch in entries and z.read(patch) == (CATALOG_DIR.parent.parent / "patches" / source).read_bytes(),
                    "FBNeo patch differs from source")
        require(mandatory <= entries, f"missing files: {mandatory - entries}")
        require(z.read("assets/FBNeo-LICENSE.txt") == FBNEO_LICENSE.read_bytes(),
                "FBNeo license must be the full, verbatim text for the pinned core")
        libraries = {}
        for info in z.infolist():
            name = info.filename
            if name in ART_PATHS:
                require(info.file_size <= 256 * 1024, f"overlong PNG: {name}")
                inspect_png(z.read(info), name)
            if name in CATALOG_ASSETS or name in (NES_INDEX_ASSET, CONSOLE_INDEX_ASSET):
                expected = CATALOG_DIR / name
                require(info.file_size == expected.stat().st_size and z.read(info) == expected.read_bytes(),
                        f"catalog bundle differs from source: {name}")
            if name in mandatory:
                continue
            match = NATIVE.fullmatch(name)
            require(match is not None, f"non-allowlisted payload: {name}")
            abi, lib = match.groups()
            require(info.compress_type == ZIP_STORED, f"native library must be stored: {name}")
            payload = z.read(info)
            inspect_elf(payload, name, abi, lib)
            if lib == "retro" and version_code >= 11:
                inspect_console_drivers(payload, z.read(CONSOLE_INDEX_ASSET), abi)
            libraries.setdefault(abi, set()).add(lib)
        require(libraries and all(libs == {"retro", "offlinefb", "hqx"} for libs in libraries.values()), "incomplete native libraries")
        inspect_dex(z.read("classes.dex"), link)
    scope = "local link permission/API allowlist" if link else "zero permissions"
    print(f"APK checks passed: {scope}, launcher only, approved payload, verbatim FBNeo license, and 16 KiB ELF alignment.")
    print("Native ABIs: " + ", ".join(sorted(libraries)))


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4) or (len(sys.argv) == 4 and sys.argv[3] != "--link"):
        raise SystemExit("Usage: check_offline_apk.py AAPT2 FILE.apk [--link]")
    main(*sys.argv[1:3], link=len(sys.argv) == 4)
