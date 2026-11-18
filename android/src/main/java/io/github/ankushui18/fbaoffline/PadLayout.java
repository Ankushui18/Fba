package io.github.ankushui18.fbaoffline;

/** Geometry shared by rendered controls and hit testing. Sizes are in local pixels. */
final class PadLayout {
    static final int DPAD = 0, FIRST_ACTION = 1, COIN = 7, START = 8, MENU = 9;
    final Box[] controls = new Box[10];
    final Box video, title;
    final int dockTop;
    final boolean overlay;

    static final class Box {
        final int x, y, w, h;
        Box(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = Math.max(0, w);
            this.h = Math.max(0, h);
        }
        int right() { return x + w; }
        int bottom() { return y + h; }
        boolean contains(float px, float py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    PadLayout(int width, int height, float density, boolean showControls) {
        overlay = width > height;
        int edge = Math.min(px(14, density), Math.min(width, height) / 16);
        int menuSize = Math.min(px(48, density), Math.min(width, height) / 5);
        controls[MENU] = new Box(width - edge - menuSize, edge, menuSize, menuSize);
        title = overlay ? new Box(0, 0, 0, 0) : new Box(edge + edge / 2, edge,
                controls[MENU].x - 3 * edge, menuSize);
        for (int i = 0; i < MENU; i++) controls[i] = new Box(0, 0, 0, 0);
        int top = height;
        if (showControls && width > 0 && height > 0) {
            int usable = Math.min(width - 2 * edge, px(548, density));
            int left = overlay ? edge : (width - usable) / 2;
            int right = overlay ? width - edge : left + usable;
            int gap = Math.min(px(6, density), Math.min(usable, height) / 60);
            int dpad = overlay ? Math.min(px(160, density), Math.min((int) (height * .49f), usable / 2))
                    : Math.min(px(156, density), Math.min((int) (height * .25f), (int) (usable * .43f)));
            int face = overlay ? Math.min(px(64, density), Math.min((int) (height * .185f), (usable - dpad - 4 * gap) / 2))
                    : Math.min(px(60, density), Math.min((int) (height * .135f), (usable - dpad - 4 * gap) / 3));
            int actionHeight = overlay ? 3 * face + 2 * gap : 2 * face + gap;
            int actionWidth = overlay ? 2 * face + gap : 3 * face + 2 * gap;
            int bottom = height - edge;
            int mainTop = bottom - Math.max(dpad, actionHeight);
            controls[DPAD] = new Box(left, bottom - dpad, dpad, dpad);
            int[] slots = overlay ? new int[] {4, 5, 2, 3, 0, 1} : new int[] {2, 3, 4, 0, 1, 5};
            int columns = overlay ? 2 : 3;
            for (int i = 0; i < slots.length; i++) {
                controls[FIRST_ACTION + slots[i]] = new Box(right - actionWidth + (i % columns) * (face + gap),
                        bottom - actionHeight + (i / columns) * (face + gap), face, face);
            }
            int pillH = Math.min(px(48, density), height / (overlay ? 6 : 8));
            int pillW = Math.min(px(88, density), (usable - menuSize - 3 * gap) / (overlay ? 2 : 3));
            int pillX = overlay ? edge : (width - 2 * pillW - gap) / 2;
            int pillY = overlay ? edge : mainTop - gap - pillH;
            controls[COIN] = new Box(pillX, pillY, pillW, pillH);
            controls[START] = new Box(pillX + pillW + gap, pillY, pillW, pillH);
            if (!overlay) top = pillY - edge / 2;
        }
        dockTop = top;
        int videoTop = overlay ? 0 : controls[MENU].bottom() + edge / 3;
        video = new Box(0, videoTop, width, top - videoTop);
    }

    private static int px(int dp, float density) { return Math.round(dp * density); }

    int keyAt(float x, float y) {
        for (int i = FIRST_ACTION; i < MENU; i++) if (controls[i].contains(x, y)) return i;
        return -1;
    }
}
