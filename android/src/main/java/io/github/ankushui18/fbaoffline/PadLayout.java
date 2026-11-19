package io.github.ankushui18.fbaoffline;

/** Geometry shared by rendered controls and hit testing. Sizes are in local pixels. */
final class PadLayout {
    static final int CUSTOM = 0, AUTO = 1, COMPACT = 2;
    static final int DPAD = 0, FIRST_ACTION = 1, COIN = 7, START = 8, COMBO = 9, KEY_END = 13,
            PLAYER = 13, MENU = 14, COUNT = 15;
    static final String[] NAMES = {"Direction", "A", "B", "C", "D", "E", "F", "Coin", "Start",
            "Combo 1", "Combo 2", "Combo 3", "Combo 4", "Player", "Pause"};
    final Box[] controls = new Box[COUNT];
    final int[] keyBits = new int[KEY_END];
    final float opacity, deadZone, buttonMargin, stickMargin;
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
        this(width, height, density, showControls, null, null, false, false);
    }

    PadLayout(int width, int height, float density, boolean showControls, PadProfile profile,
              int[] combos, boolean showPlayer, boolean editor) {
        this(width, height, density, showControls, profile, combos, showPlayer, editor, CUSTOM, 0, false);
    }

    PadLayout(int width, int height, float density, boolean showControls, PadProfile profile,
              int[] combos, boolean showPlayer, boolean editor, int mode, int descriptor, boolean fullScreen) {
        this(width, height, density, showControls, profile, showPlayer, editor, mode, descriptor, fullScreen,
                combos == null ? PadBindings.DEFAULT : new PadBindings(null, combos));
    }

    PadLayout(int width, int height, float density, boolean showControls, PadProfile profile,
              boolean showPlayer, boolean editor, int mode, int descriptor, boolean fullScreen, PadBindings bindings) {
        overlay = width > height || fullScreen;
        boolean compact = mode == COMPACT && !editor;
        boolean auto = mode == AUTO && !editor && descriptor != 0;
        opacity = profile == null ? .9f : profile.opacity / 100f;
        deadZone = profile == null ? .27f : profile.deadZone / 100f;
        buttonMargin = profile == null ? 0 : profile.buttonMargin / 100f;
        stickMargin = profile == null ? 0 : profile.stickMargin / 100f;
        for (int i = FIRST_ACTION; i < COIN; i++) {
            int mapped = bindings.face(i - FIRST_ACTION);
            keyBits[i] = auto ? mapped & descriptor : mapped;
        }
        keyBits[COIN] = Joypad.COIN; keyBits[START] = Joypad.START;
        for (int i = 0; i < PadBindings.EXTRAS; i++) keyBits[COMBO + i] = bindings.extra(i);
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
            int face = overlay ? Math.min(px(64, density), Math.min((int) (height * .185f), (usable - dpad - 4 * gap) / (compact ? 3 : 2)))
                    : Math.min(px(60, density), Math.min((int) (height * .135f), (usable - dpad - 4 * gap) / 3));
            if (compact) {
                dpad = dpad * 4 / 5;
                face = face * 4 / 5;
            }
            int[] order = overlay && !compact ? new int[] {4, 5, 2, 3, 0, 1} : new int[] {2, 3, 4, 0, 1, 5};
            int count = 0;
            for (int index : order) if (!auto || (descriptor & keyBits[FIRST_ACTION + index]) != 0) order[count++] = index;
            int columns = Math.max(1, Math.min(count, overlay && !compact ? 2 : 3));
            int rows = (count + columns - 1) / columns;
            int actionHeight = rows * face + Math.max(0, rows - 1) * gap;
            int actionWidth = columns * face + (columns - 1) * gap;
            int bottom = height - edge;
            int mainTop = bottom - Math.max(dpad, actionHeight);
            controls[DPAD] = new Box(left, bottom - dpad, dpad, dpad);
            for (int i = 0; i < count; i++) {
                controls[FIRST_ACTION + order[i]] = new Box(right - actionWidth + (i % columns) * (face + gap),
                        bottom - actionHeight + (i / columns) * (face + gap), face, face);
            }
            int pillH = Math.min(px(48, density), height / (overlay ? 6 : 8));
            int pillW = Math.min(px(88, density), (usable - menuSize - 3 * gap) / (overlay ? 2 : 3));
            int pillX = overlay ? edge : (width - 2 * pillW - gap) / 2;
            int pillY = overlay ? edge : mainTop - gap - pillH;
            controls[COIN] = new Box(pillX, pillY, pillW, pillH);
            controls[START] = new Box(pillX + pillW + gap, pillY, pillW, pillH);
            if (!overlay) top = pillY - edge / 2;
            int comboSize = Math.max(0, Math.min(face, px(46, density)));
            int extraX = (width - 4 * comboSize - 3 * gap) / 2;
            int extraY = overlay ? bottom - comboSize : Math.max(0, top - comboSize - gap);
            for (int i = 0; i < 4; i++) {
                if (auto) keyBits[COMBO + i] &= descriptor;
                if (editor || keyBits[COMBO + i] != 0) {
                    controls[COMBO + i] = new Box(extraX + i * (comboSize + gap), extraY, comboSize, comboSize);
                }
            }
            if (showPlayer || editor) {
                int playerW = Math.max(0, Math.min(px(66, density), overlay
                        ? controls[MENU].x - controls[START].right() - 2 * gap : pillX - edge - gap));
                controls[PLAYER] = new Box(overlay ? controls[START].right() + gap : left, pillY, playerW, pillH);
            }
        }
        dockTop = top;
        int videoTop = overlay ? 0 : controls[MENU].bottom() + edge / 3;
        video = new Box(0, videoTop, width, top - videoTop);
        if (profile != null) for (int i = 0; i < COUNT; i++) {
            Box box = controls[i];
            if ((!editor && profile.hidden[i] && i != MENU) || box.w == 0 || box.h == 0) {
                controls[i] = new Box(0, 0, 0, 0);
                continue;
            }
            float resize = profile.scale[i] * profile.overall / 100f;
            int w = Math.min(width, Math.max(1, Math.round(box.w * resize)));
            int h = Math.min(height, Math.max(1, Math.round(box.h * resize)));
            boolean fixed = compact || auto;
            float cx = fixed || profile.x[i] < 0 ? box.x + box.w / 2f : profile.x[i] * width;
            float cy = fixed || profile.y[i] < 0 ? box.y + box.h / 2f : profile.y[i] * height;
            if (i == MENU) { w = Math.max(w, Math.min(width, px(36, density))); h = Math.max(h, Math.min(height, px(36, density))); }
            int x = Math.max(0, Math.min(Math.round(cx - w / 2f), width - w));
            int y = Math.max(0, Math.min(Math.round(cy - h / 2f), height - h));
            controls[i] = new Box(x, y, w, h);
        }
    }

    private static int px(int dp, float density) { return Math.round(dp * density); }

    int keyAt(float x, float y) {
        if (controls[MENU].contains(x, y) || controls[PLAYER].contains(x, y)) return -1;
        for (int i = KEY_END - 1; i >= FIRST_ACTION; i--) if (controls[i].contains(x, y)) return i;
        if (buttonMargin <= 0 || controls[DPAD].contains(x, y)) return -1;
        int nearest = -1;
        float distance = Float.POSITIVE_INFINITY;
        for (int i = KEY_END - 1; i >= FIRST_ACTION; i--) {
            Box box = controls[i];
            if (!expanded(box, x, y, buttonMargin)) continue;
            float dx = x - box.x - box.w / 2f, dy = y - box.y - box.h / 2f;
            float score = dx * dx + dy * dy;
            if (score < distance) { nearest = i; distance = score; }
        }
        return nearest;
    }

    boolean directionAt(float x, float y) {
        return !controls[MENU].contains(x, y) && !controls[PLAYER].contains(x, y) && expanded(controls[DPAD], x, y, stickMargin);
    }

    private static boolean expanded(Box box, float x, float y, float margin) {
        if (box.w == 0 || box.h == 0) return false;
        float extra = Math.min(box.w, box.h) * margin;
        return x >= box.x - extra && x < box.right() + extra && y >= box.y - extra && y < box.bottom() + extra;
    }
}
