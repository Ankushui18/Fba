# FBA Arcade 0.5.0 Recovery — Android acceptance checks

Both signed ARM64 APKs passed build and host verification. No Android device
was connected. These checks verify Android UI/SAF, input, audio, and hardware
connectivity on a phone or tablet. Use a legally obtained ROM set for the
pinned FBNeo revision, including its BIOS/parent dependencies.

1. **Install/update.** Use Offline first. For the signed parity test APKs, install
   0.5.0 **over** 0.4.0 Recovery; compare the ID and cert before testing a
   different build. Check that the library, an existing control layout, save
   slots, IPS variants and cheats survive. Don't uninstall the old app. Offline
   should request no permissions; the Android file picker is not a permission
   prompt for all storage. Original FBA Arcade/FBA4droid installs stay separate.
2. **Import and browse.** Use **Import ZIPs** and **Files → Browse folder**.
   Select a directory with ZIPs and a nested folder. Navigate down/up; choose
   one ZIP or import the listed ZIPs. Reopen the app and browse without picking
   again. Change the remembered folder, or use **Forget** and verify copied
   games and saves remain. Try revoking access/moving the selected folder.
   Check thumbnails/list/grid/search/filters/favorites/recent. An invalid ZIP
   and an old `.fba` should not launch; no ROMs come preinstalled.
3. **Notes and skins.** In **Game info → Local note**, import a `.htm` note with
   line breaks, Chinese characters, an image and a link; verify only text remains.
   Restart, replace the note, then remove it and check other games' notes are
   unchanged. In library **Settings → Virtual controls → Controller skin** import
   a visible folder with legacy PNG filenames and optional `icon1.png`/`icon2.png`.
   Check the preview, both stick modes, a pressed image and fallback for a missing
   frame in play and the drag editor. Check per-game skin selection, removal
   without affecting the original files, and rejection of an invalid PNG.
4. **Controllers.** Press Coin and Start; hold a diagonal and two buttons.
   Drag and resize controls, adjust joystick and button touch expansion separately,
   and verify both portrait and landscape. An old shared margin starts both values
   at the same percentage. Try Auto on a game with fewer than six buttons and try
   Compact. Verify Cancel leaves a layout unchanged, full-screen portrait preview
   agrees with play and buttons release on background/focus loss. Configure an A–F
   combination and a Combo 1–4 mask, bind EXT1–4 in Physical keys, and test touch
   and hardware. Quick taps should register; holding a selected repeat button
   should start autofire. Try input mirroring and separate controller ports.
5. **Display.** Compare fit, fill, original 1×, ratios and integer scale with a
   horizontal and vertical game. 1× stays at the raw pixel size when it fits.
   Compare sharp and HQ2x/3x/4x at a larger size. HQx is pixel smoothing, not a
   core resolution increase; oversized frames fall back to sharp. Change
   orientation, audio, FPS and frameskip. Enable GPU canvas, exit and re-open.
   Check pause/resume and split-screen. Restore per-game defaults without
   losing ROMs, notes or states.
6. **Cheats/DIPs and slots.** Apply a bundled cheat and cabinet choice and check
   their effect. Save to slot 1, advance, load and restart. Use the row's
   **Save states** to rename, launch from and delete slots. Capture a screenshot,
   check the library cover, and export PNG. Import a matching `.ini` or cheat ZIP
   with top-level `<set>.ini`. Options should initially be off; restore built-in
   cheats afterwards.
7. **IPS isolation.** Import a same-size, one-chip `.ips` for the exact ROM
   revision, then browse a simple `.dat` plus two matching `.ips` files (see
   [format](FBA4DROID_PARITY.md#legacy-ips-subset)). Confirm a visible effect from
   both chips, then save a slot and preview in the variant. Use **Apply selected**
   to combine installed variants, confirm the listed order and test its own
   preview/slots. The base ZIP, regular battery storage and states should be
   unchanged. Missing IPS files, out-of-bounds writes and wrong chip CRCs should
   fail without changing existing variants.
8. **Link beta only, two phones.** Update both to the same 0.5.0 Link APK. See
   [setup](LINK_PLAY.md). Test Wi-Fi P1/P2 and paired Bluetooth P1/P2. Check
   separate inputs, paired pause/resume and safe exit. Try a mixed 0.4.0/0.5.0
   pair, wrong room code, different ZIP revision, permission denial, cancellation
   and a network loss or app background. Check normal solo data is unchanged.
9. **If possible:** repeat on a 16 KiB page-size phone.

Report phone model/Android version, APK flavor, ROM set name (not ROM contents),
and the exact step that failed. There is no automatic error reporting.
