# FBA Arcade 0.4.0 — Android acceptance checks

Both signed ARM64 APKs passed build and host verification. No Android device
was connected. These checks verify Android UI/SAF, input, audio, and hardware
connectivity on a phone or tablet. Use a legally obtained ROM set for the
pinned FBNeo revision, including its BIOS/parent dependencies.

1. **Install/update.** Install the regular build. The Recovery APK should
   appear as **FBA Arcade Recovery**, without replacing or accessing any older
   app's data. If testing an original 0.4.0 APK with the original v0.3.0 signer,
   update **FBA Arcade** without uninstalling and check that its games,
   preferences and saves remain. The regular build should request no permissions.
2. **Import and browse.** Use both **Import ZIPs** and **Files → Choose folder**.
   Check thumbnails, game info, list/grid, search, filters, favorite and Recent.
   Attempt an invalid ZIP and a legacy `.fba` to see actionable errors. Check a
   valid set lacking artwork still launches; no ROMs should come preinstalled.
3. **Controllers.** Press Coin and Start; hold a diagonal and two buttons.
   Try both circular skins, drag controls into the game area and resize them.
   Save and test their hit positions. Rotate and edit the other orientation.
   Try Auto on a game with fewer than six buttons and try Compact. Confirm
   buttons don't stick after backgrounding, Cancel leaves a layout unchanged,
   and full-screen portrait preview agrees with play. Try physical key
   rebinding, Player selection, mirroring, combos and held-button turbo.
4. **Display.** Change aspect, orientation, FPS, audio and frameskip. Compare
   sharp and HQ2x/3x/4x on a compatible small frame. HQx is pixel smoothing,
   not an increase in the core's resolution; oversized frames fall back to
   sharp. Enable GPU canvas, exit and re-open a game. Check display/pause and
   resume after orientation and split-screen changes. Check per-game override
   isolation, then restore defaults without losing library data or states.
5. **Cheats/DIPs and slots.** Apply a visible bundled cheat and cabinet choice.
   Check the actual game effect, disable the cheat, and restart. Save to slot
   1, advance, then load. Exit and use **Save states** on the row to rename the
   slot, launch from it, and delete a different slot with confirmation. Capture
   a screenshot, verify the library cover, and export PNG. Import your own
   matching `.ini` or top-level `<set>.ini` cheat ZIP. Verify options are off on
   launch, work when enabled, and the bundled list returns when restored.
6. **IPS isolation.** Use a trusted, same-size IPS for a named chip in the
   exact FBNeo ZIP. From the row, import it, select the patch, and launch.
   Confirm the effect and save a slot and preview in the variant. Relaunch the
   regular row and verify the effect and variant slot are absent. Test an IPS
   for an out-of-bounds offset and rejection of a changed chip revision.
7. **Link beta only, two phones.** Update both to the Link APK; see
   [setup](LINK_PLAY.md). Test Wi-Fi P1/P2 and then paired Bluetooth P1/P2.
   Check separate inputs on both screens, paired pause/resume and safe exit.
   Try a wrong room code, different ZIP revision, denying a relevant permission,
   cancelling while waiting, and losing the network or backgrounding one phone.
   Check solo play still works and solo data and save slots are unchanged.
8. **If possible:** repeat installation and launch on a 16 KiB page-size phone.

Report phone model/Android version, APK flavor, ROM set name (not ROM contents),
and the exact step that failed. There is no automatic error reporting.
