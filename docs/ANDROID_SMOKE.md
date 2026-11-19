# FBA Arcade 0.5.5 — Android acceptance checks

The signed ARM64 APK passed build and host verification. No Android device
was connected. These checks verify Android UI/SAF, input, audio, and hardware
connectivity on a phone or tablet. Use a legally obtained ROM set for the
pinned FBNeo revision, including its BIOS/parent dependencies.

1. **Update in place.** Install the [0.5.5 APK](../README.md#install-and-play)
   over 0.5.4, 0.5.3, 0.5.2 or 0.5.1 Recovery Preview. Do not uninstall first. Check that
   ROMs, saved folder access, Favorites, list/grid, Quick Resume and a manual
   save still work. Any original Recovery or FBA4droid install should be
   untouched. Start a solo game without a network connection or a Bluetooth/Local
   Network runtime grant.
2. **First screen and import.** On a fresh install, look for **Add games** with
   no empty library tabs or search field. Tap it, cancel the Android picker and
   check the welcome screen is still usable. Add a valid game ZIP and tap its
   row to play. When games are present, **+ Add** opens the same picker. Import
   while Favorites or a search filter is active: the completed import should
   show **All games**, not the old filtered list. Check search, its clear button,
   empty Favorites and Recent, and grid/layout controls under **Filter & sort**.
   Game row **⋮** should still open saves, Link and the other game tools. Check
   portrait, landscape, a narrow window and large text, including whether the
   welcome card and library controls scroll out of the way.
3. **Browse folders.** Use **Browse folder** on the welcome screen or
   **Settings → Browse ROM folder**. Select a directory with ZIPs and a nested
   folder. Navigate down/up and import one or all listed ZIPs. Reopen and browse
   without picking again. Change or **Forget** the remembered folder and verify
   that copied games and saves remain; try revoking the grant or moving the
   folder. An invalid ZIP and an old `.fba` should not launch; no ROMs are bundled.
   For **NES**, pick a matching `.nes` (e.g. the World `smb` revision) directly
   from **Add games** and the folder picker. Confirm the game's title and
   **NES / Famicom** label, then use Start and the D-pad and check manual save/load
   and Quick Resume. Re-add the same `.nes` and check the slot still loads.
   Try the correct `smb.zip` or `nes_smb.zip`, a different-revision `.nes`, and
   a file merely renamed `.nes`. In a name collision (e.g. NES and arcade
   `1942.zip`), both matching sets should be listed separately. Check that the
   arcade game's controls, saves and Link access did not change.
4. **Notes and skins.** In **Game info → Local note**, import a `.htm` note with
   line breaks, Chinese characters, an image and a link; verify only text remains.
   Restart, replace the note, then remove it and check other games' notes are
   unchanged. In library **Settings → Virtual controls → Controller skin** import
   a visible folder with legacy PNG filenames and optional `icon1.png`/`icon2.png`.
   Check the preview, both stick modes, a pressed image and fallback for a missing
   frame in play and the drag editor. Check per-game skin selection, removal
   without affecting the original files, and rejection of an invalid PNG.
5. **Controllers.** Press Coin and Start; hold a diagonal and two buttons.
   Drag and resize controls, adjust joystick and button touch expansion separately,
   and verify both portrait and landscape. An old shared margin starts both values
   at the same percentage. Try Auto on a game with fewer than six buttons and try
   Compact. Verify Cancel leaves a layout unchanged, full-screen portrait preview
   agrees with play and buttons release on background/focus loss. Configure an A–F
   combination and a Combo 1–4 mask, bind EXT1–4 in Physical keys, and test touch
   and hardware. Quick taps should register; holding a selected repeat button
   should start autofire. Try input mirroring and separate controller ports.
6. **Display and filter timing.** In Fit mode with sound and the FPS counter on
   and frameskip off, play the same scene with Sharp, Bilinear, HQ2x, HQ3x and
   HQ4x. Note both **game / video** FPS, smooth scrolling, audio crackle and
   input response. On a phone that can't draw every HQx frame, video FPS can
   drop while game FPS and sound stay steady. Compare with 0.5.0 if possible
   (test the older install first; don't downgrade by uninstalling). Try manual
   and Auto frameskip: they affect video, not emulation speed. Repeat with GPU
   canvas enabled after exiting and reopening the game. Switch filters while
   paused, rotate/split the screen, background/resume, and exit one game then
   immediately open another. Check fit, fill, integer scale and both fixed
   ratios in horizontal and vertical games. **Original size (1×)** bypasses
   filters. Screenshots/slot previews should still be unfiltered. Restore
   per-game defaults without losing ROMs, notes or states.
7. **Cheats/DIPs and slots.** Apply a bundled cheat and cabinet choice and check
   their effect. Save to slot 1, advance, load and restart. Use the row's
   **Save states** to rename, launch from and delete slots. Capture a screenshot,
   check the library cover, and export PNG. Import a matching `.ini` or cheat ZIP
   with top-level `<set>.ini`. Options should initially be off; restore built-in
   cheats afterwards.
8. **IPS isolation.** Import a same-size, one-chip `.ips` for the exact ROM
   revision, then browse a simple `.dat` plus two matching `.ips` files (see
   [format](FBA4DROID_PARITY.md#legacy-ips-subset)). Confirm a visible effect from
   both chips, then save a slot and preview in the variant. Use **Apply selected**
   to combine installed variants, confirm the listed order and test its own
   preview/slots. The base ZIP, regular battery storage and states should be
   unchanged. Missing IPS files, out-of-bounds writes and wrong chip CRCs should
   fail without changing existing variants.
9. **Quick Resume.** With the option off (default), exit a solo game and verify
   there is no Continue button. Turn it on under **Settings → Quick Resume**.
   Make a manual save, advance, and use **Pause → Exit game**. Wait for the
   saved confirmation, close and reopen FBA Arcade, and tap **Continue**. Check
   that it resumes from exit, not the earlier manual slot. Tap a normal game row
   to verify it still starts normally. Press Home or force-stop from an active
   game without choosing Exit; the previous auto-save should remain. Repeat with
   an IPS variant; remove/re-import the same variant and try a different ROM
   revision under the same name. An incompatible save must not start the wrong
   game. Turn off Quick Resume to hide Continue, then turn it back on to see the
   same checkpoint, and use **Clear auto-save**. The original manual slots must
   still work. If a driver cannot save, check that you can stay paused or exit
   without losing the previous checkpoint.
10. **Backup & restore.** Name a base-game slot and an IPS slot, set a non-default
    HQx filter, sort/grid mode and custom pad layout, then exit to the library.
    Use **Settings → Backup & restore → Export ZIP** to save in Downloads. On a
    second device, restore that ZIP before importing the same ROMs and IPS
    variant. Check the slot names, progress and settings after reopening the app.
    Repeat restore with one occupied slot and one missing slot; the occupied
    slot (including its name/preview) must survive and the missing one must be
    added. The destination's ROM copies and folder grant must remain intact.
    The Quick Resume checkpoint and its toggle must remain local. Cancel a
    picker/confirmation and try a plain ROM ZIP and a truncated copy of the backup;
    none should change the current slots or settings.
11. **Local multiplayer, two phones.** Install the same 0.5.5 APK on both.
    See [Link setup](LINK_PLAY.md). Test Wi-Fi P1/P2 and paired Bluetooth P1/P2.
    Check separate inputs, paired pause/resume and safe exit. Try different
    filters on the two phones. Check a mixed 0.5.4/0.5.5 pair (same protocol), a
    rejected 0.4.0 peer, wrong room code, different ZIP revision, permission
    denial, cancellation and network loss or app background. Check that the solo
    Quick Resume checkpoint and other solo data stay unchanged after a Link match.
12. **If possible:** repeat on a 16 KiB page-size phone.

Report phone model/Android version, APK flavor, ROM set name (not ROM contents),
active filter, both FPS values, and whether audio stutters along with video.
There is no automatic error reporting.
