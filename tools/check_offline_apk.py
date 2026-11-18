#!/usr/bin/env python3
"""Fail-closed packaging/privacy checks for FBA Offline (not an antivirus)."""
import re
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
from zipfile import ZIP_STORED, ZipFile
from zlib import crc32

from import_legacy_art import ART


ART_PATHS = {f"res/drawable-nodpi-v4/{name}" for name in ART}
APP = "io.github.ankushui18.fbaoffline"
NATIVE = re.compile(r"lib/(arm64-v8a|x86_64)/lib(retro|offlinefb)\.so\Z")
ASSETS = {
    "assets/CORE-SOURCE.txt", "assets/FBNeo-LICENSE.txt",
    *{"assets/licenses/" + name + ".txt" for name in ("dr_libs", "gba", "libchdr", "lzma", "snes", "ugui")},
}
MARKERS = (
    b"baidu.com", b"qq.com", b"cn/waps", b"com/tencent/bugly",
    b"Landroid/webkit/", b"Ldalvik/system/DexClassLoader;", b"Ldalvik/system/InMemoryDexClassLoader;",
    b"Ljava/net/", b"Ljavax/net/ssl/", b"Landroid/app/DownloadManager;",
    b"REQUEST_INSTALL_PACKAGES", b"android.intent.action.INSTALL_PACKAGE",
)
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
        allowed = PLATFORM_LIBS | ({"libretro.so"} if lib == "offlinefb" else set())
        require(deps <= allowed and "(TEXTREL)" not in dyn, f"unexpected native dependency/relocations: {label}: {deps}")
        if lib == "offlinefb":
            require("libretro.so" in deps, f"JNI frontend isn't linked to built core: {label}")
    require(not any(x in data.lower() for x in MARKERS[:4]), f"old tracker marker in {label}")


def inspect_manifest(aapt2, apk):
    tree = subprocess.check_output([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk], text=True)
    tags = re.findall(r"^\s*E: (\S+)", tree, re.MULTILINE)
    require(tags == ["manifest", "uses-sdk", "application", "activity", "intent-filter", "action", "category"],
            f"unexpected manifest elements (including any permissions/components): {tags}")
    for attr in ("allowBackup", "debuggable", "extractNativeLibs", "usesCleartextTraffic"):
        require(re.search(rf":{attr}\([^)]*\)=(?:false|\(type 0x12\)0x0)(?:\s|$)", tree),
                f"manifest must explicitly set android:{attr}=false")
    require("android.intent.action.MAIN" in tree and "android.intent.category.LAUNCHER" in tree and
            "MainActivity" in tree, "missing expected launcher")
    require(re.search(r":exported\([^)]*\)=(?:true|\(type 0x12\)0xffffffff)(?:\s|$)", tree),
            "the launcher must be explicitly exported")
    badging = subprocess.check_output([aapt2, "dump", "badging", apk], text=True)
    require(f"package: name='{APP}'" in badging, "unexpected application ID")
    require(re.search(r"^(?:sdkVersion|minSdkVersion):'26'$", badging, re.M) and
            "targetSdkVersion:'37'" in badging, "unexpected effective SDK levels")


def inspect_dex(dex):
    require(dex.startswith(b"dex\n") and len(dex) >= 112, "invalid DEX header")
    string_count, strings, type_count, types = struct.unpack_from("<IIII", dex, 56)
    class_count, classes = struct.unpack_from("<II", dex, 96)
    require(strings + 4 * string_count <= len(dex) and types + 4 * type_count <= len(dex) and
            0 < class_count <= 96 and classes + 32 * class_count <= len(dex), "invalid or unexpected DEX layout")
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


def main(aapt2, apk):
    inspect_manifest(aapt2, apk)
    with ZipFile(apk) as z:
        require(z.testzip() is None, "archive CRC error")
        entries = set(z.namelist())
        require(len(entries) == len(z.infolist()), "duplicate archive entries")
        mandatory = {"AndroidManifest.xml", "classes.dex", "resources.arsc", "res/drawable/ic_search.xml"} | ASSETS | ART_PATHS
        require(mandatory <= entries, f"missing files: {mandatory - entries}")
        libraries = {}
        for info in z.infolist():
            name = info.filename
            if name in ART_PATHS:
                require(info.file_size <= 256 * 1024, f"overlong PNG: {name}")
                inspect_png(z.read(info), name)
            if name in mandatory:
                continue
            match = NATIVE.fullmatch(name)
            require(match is not None, f"non-allowlisted payload: {name}")
            abi, lib = match.groups()
            require(info.compress_type == ZIP_STORED, f"native library must be stored: {name}")
            inspect_elf(z.read(info), name, abi, lib)
            libraries.setdefault(abi, set()).add(lib)
        require(libraries and all(libs == {"retro", "offlinefb"} for libs in libraries.values()), "incomplete native libraries")
        inspect_dex(z.read("classes.dex"))
    print("Offline APK checks passed: zero permissions, launcher only, approved payload, and 16 KiB ELF alignment.")
    print("Native ABIs: " + ", ".join(sorted(libraries)))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: check_offline_apk.py AAPT2 FILE.apk")
    main(*sys.argv[1:])
