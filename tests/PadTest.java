package io.github.ankushui18.fbaoffline;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static io.github.ankushui18.fbaoffline.Joypad.*;
import static io.github.ankushui18.fbaoffline.PadLayout.*;

public final class PadTest {
    public static void main(String[] args) {
        int[][] sizes = {{240, 320}, {320, 650}, {360, 800}, {480, 854}, {640, 960}, {800, 1280},
                {320, 200}, {600, 240}, {800, 360}, {900, 420}, {1280, 720}, {900, 850}, {320, 320}};
        for (int[] dp : sizes) {
            for (float density : new float[] {1, 1.5f, 2, 2.625f, 4}) {
                int w = Math.round(dp[0] * density), h = Math.round(dp[1] * density);
                checkLayout(w, h, density);
                PadLayout hidden = new PadLayout(w, h, density, false);
                expect(hidden.dockTop == h && hidden.video.bottom() == h, "hidden controls leave all video space");
                expect(hidden.controls[MENU].w > 0 && allPadTargetsEmpty(hidden), "hidden controls leave the pause button only");
            }
        }
        expect(new PadLayout(0, 0, 2, true).video.h == 0, "empty measure is allowed");
        expect(new PadLayout(200, 0, 2, true).video.h == 0, "zero-height measure is allowed");
        expect(new PadLayout(0, 200, 2, true).video.w == 0, "zero-width measure is allowed");
        testPointers(new PadLayout(360, 760, 1, true));
        testPointers(new PadLayout(760, 360, 1, true));
        System.out.println("Controller layout and multitouch tests passed.");
    }

    private static void checkLayout(int w, int h, float density) {
        PadLayout layout = new PadLayout(w, h, density, true);
        expect(layout.overlay == (w > h), "orientation");
        expect(layout.video.w == w && layout.video.h > 0 && layout.video.bottom() <= h, "usable video rectangle");
        expect(layout.video.h >= h / 4, "reserve at least a quarter for video in a small window");
        if (layout.overlay) {
            expect(layout.video.y == 0 && layout.video.h == h, "landscape uses full play area");
        } else {
            expect(!overlap(layout.title, layout.controls[MENU]), "title clear of pause");
            expect(layout.video.y >= layout.controls[MENU].bottom() && layout.video.bottom() == layout.dockTop,
                    "portrait keeps video between title and controls");
        }
        Set<Integer> bits = new HashSet<>();
        for (int i = 0; i < layout.controls.length; i++) {
            PadLayout.Box key = layout.controls[i];
            expect(key.x >= 0 && key.y >= 0 && key.right() <= w && key.bottom() <= h, "in-bounds touch target");
            expect(key.w > 0 && key.h > 0, "non-empty touch target");
            for (int j = 0; j < i; j++) expect(!overlap(key, layout.controls[j]), "touch targets do not overlap");
            if (i == DPAD || i == MENU) continue;
            expect(layout.keyAt(centerX(key), centerY(key)) == i, "hit target matches drawing");
            expect(bits.add(PadInput.KEY_BITS[i]), "distinct joypad bit per key");
            expect(layout.overlay || key.y >= layout.dockTop, "portrait buttons don't cover video");
        }
        expect(bits.equals(new HashSet<>(Arrays.asList(A, B, X, Y, L, R, Joypad.COIN, Joypad.START))), "all eight legacy inputs");
        if (w >= Math.round(320 * density) && h >= Math.round((layout.overlay ? 260 : 360) * density)) {
            expect(layout.controls[FIRST_ACTION].w >= Math.round(46 * density), "comfortable controls on typical phones");
        }
    }

    private static void testPointers(PadLayout layout) {
        PadInput input = new PadInput();
        PadLayout.Box dpad = layout.controls[DPAD];
        PadLayout.Box a = layout.controls[FIRST_ACTION], b = layout.controls[FIRST_ACTION + 1];
        PadLayout.Box coin = layout.controls[PadLayout.COIN];
        int[] ids = {18, 2, 6};
        float[] xs = {dpad.x + 1, centerX(a), centerX(b)};
        float[] ys = {dpad.y + 1, centerY(a), centerY(b)};
        expect(input.update(layout, 3, -1, ids, xs, ys) == (UP | LEFT | B | A), "diagonal plus two buttons");
        expect(input.update(layout, 3, 1, ids, xs, ys) == (UP | LEFT | A), "lifting one finger leaves the others");
        // The second pointer is gone; ordering of the remaining contacts can change.
        ids[1] = ids[2];
        xs[1] = xs[2];
        ys[1] = ys[2];
        xs[0] = centerX(a); // D-pad thumb moves outside its square without becoming a face button.
        ys[0] = dpad.bottom() + 10;
        expect(input.update(layout, 2, -1, ids, xs, ys) == (DOWN | RIGHT | A), "D-pad tracks its original pointer");
        expect(input.update(layout, 2, 0, ids, xs, ys) == A, "lifting the D-pad clears directions");
        xs[0] = centerX(dpad);
        ys[0] = centerY(dpad);
        xs[1] = centerX(coin);
        ys[1] = centerY(coin);
        expect(input.update(layout, 2, -1, ids, xs, ys) == Joypad.COIN, "dead zone plus Coin");
        input.clear();
        xs[0] = -10;
        expect(input.update(layout, 1, -1, ids, xs, ys) == 0, "don't latch D-pad after cancellation");
        expect(input.update(layout, 0, -1, ids, xs, ys) == 0, "all contacts released");
    }

    private static float centerX(PadLayout.Box box) { return box.x + box.w / 2f; }
    private static float centerY(PadLayout.Box box) { return box.y + box.h / 2f; }
    private static boolean overlap(PadLayout.Box a, PadLayout.Box b) {
        return a.x < b.right() && a.right() > b.x && a.y < b.bottom() && a.bottom() > b.y;
    }
    private static boolean allPadTargetsEmpty(PadLayout layout) {
        for (int i = 0; i < MENU; i++) if (layout.controls[i].w != 0 || layout.controls[i].h != 0) return false;
        return true;
    }
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
