# Licenses, source and attribution

**FBA Arcade Preview is free for personal, non-commercial play. Not for sale.**
It is an independent Android frontend, not an official release from Team
FBNeo, Libretro or FBA4droid. This project does not own the upstream emulator
code, game ROMs or original third-party assets. Their rights remain with their
respective authors and rights holders.

## FinalBurn Neo (FBNeo)

The APK uses the FinalBurn Neo Libretro port. Credit goes to [Team FBNeo and
contributors](https://github.com/finalburnneo/FBNeo), Dave (original Final Burn),
MAME contributors and the other authors named in the [full FBNeo license and
credits](LICENSES/FBNeo-LICENSE.txt). The local license file is an unmodified copy
of [`src/license.txt` at the bundled revision](https://github.com/libretro/FBNeo/blob/2da48555c13ce5d011edf2d6ec829b7393c070af/src/license.txt).

For the FBNeo-based build, do not sell, lease, rent, or otherwise seek monetary
profit from it, or ask for donations to support work on this FBNeo-based
project. If redistributing under the applicable terms, retain the full license
and copyright notices verbatim, provide or link to the corresponding source,
and publish any changes you make to the FBNeo source. Do not distribute FBNeo
with ROM images unless you have the legal right to distribute them. This is a
summary; the full upstream text and each component's own license control.

### Corresponding source for Preview 0.5.8

| Component | Exact source |
| --- | --- |
| FBNeo 1.0.0.03 (Libretro port) | [Upstream snapshot `2da48555`](https://github.com/libretro/FBNeo/tree/2da48555c13ce5d011edf2d6ec829b7393c070af) |
| Changes to FBNeo in this APK | [Published `fbneo-session.patch`](https://github.com/Ankushui18/Fba/blob/795f9695b65336d84d47838c306f9be7640d84c5/android/patches/fbneo-session.patch): deterministic startup for Link and safe driver switching |
| Android frontend, build scripts and bundled HQx source | [FBA Arcade build revision `795f969`](https://github.com/Ankushui18/Fba/tree/795f9695b65336d84d47838c306f9be7640d84c5) and [build instructions](README.md#build-and-verify) |

The build scripts fetch the pinned core and apply the patch to a separate
checkout. The APK includes `assets/CORE-SOURCE.txt`, the full
`assets/FBNeo-LICENSE.txt`, `assets/FBNeo-SESSION.patch` and the other bundled
notices under `assets/licenses/`. Use the source revision and patch associated
with each older APK rather than assuming it was built from the current tree.

## Other components and third-party assets

- **FBNeo dependencies:** MAME-derived code and other components included by
  FBNeo have their own notices in the upstream tree and APK. The FBNeo summary
  above is not a replacement for those notices.
- **HQx:** Maxim Stepin and Cameron Zemek's HQx is a separate library under
  LGPL-2.1-or-later. See the [license](android/third_party/hqx/COPYING),
  [authors](android/third_party/hqx/AUTHORS) and [source and replacement
  instructions](android/third_party/hqx/README.md). The FBNeo restrictions
  don't change the terms of HQx itself.
- **Legacy art and data:** Some artwork, controller skins, thumbnails,
  metadata and cheat definitions come from the supplied FBA4droid assets. The
  [asset provenance notes](docs/PRIVACY_AND_CORE.md#what-fba-arcade-reuses) identify
  what is retained. Attribution does not grant permission to relicense or
  redistribute those assets. Verify the applicable rights before redistributing;
  remove or replace assets whose distribution rights you cannot confirm.

This notice does not add a blanket license for every file in the repository or
claim an affiliation or endorsement. The current FBA Arcade APK does not bundle
game ROM images or original BIOS archives. Provide only game and BIOS files you
have the right to use, and do not attach them to public bug reports.
