package io.github.ankushui18.fbaoffline;

import android.app.Activity;
import java.io.File;
import java.util.function.Consumer;

final class LinkMenu {
    static final boolean AVAILABLE = false;
    LinkMenu(Activity activity, GameCatalog catalog) {}
    void show(File rom, Consumer<GameLink> launch) {}
    void result(int request, String[] permissions, int[] grants) {}
}
