package io.github.ankushui18.fbaoffline;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static io.github.ankushui18.fbaoffline.ArcadeUi.*;

/** The game-first FBA library: blue tab strip, image rows, search, boards, sorting and file tools. */
final class LibraryScreen extends LinearLayout {
    interface Actions {
        void importZips();
        void browseFolder();
        void settings();
        void play(File file);
        void options(View anchor, File file);
    }

    private static final String[] TABS = {"Games", "Favorites", "Recent", "Files"};
    private static final String[] SORTS = {"Game title A–Z", "ROM filename A–Z", "Release year · newest", "Recently added", "Board system"};
    private final ArcadeUi ui;
    private final Actions actions;
    private final GameCatalog catalog;
    private final SharedPreferences prefs;
    private final LinearLayout toolbar, header, libraryTools, filesPanel;
    private final FrameLayout body;
    private final View[] tabs = new View[TABS.length], indicators = new View[TABS.length];
    private final ListView list;
    private final TextView section, count, notice, boardChip;
    private final EditText search;
    private final Button add, folder, sortButton, viewButton;
    private final Rows rows = new Rows();
    private List<File> files = Collections.emptyList(), recent = Collections.emptyList();
    private Set<String> favorites = Collections.emptySet();
    private int tab, sort;
    private String board = "", genre = "";
    private boolean importing, tiles, chinese;

    LibraryScreen(Context context, GameCatalog catalog, SharedPreferences prefs, Actions actions) {
        super(context);
        ui = new ArcadeUi(context);
        this.catalog = catalog;
        this.prefs = prefs;
        this.actions = actions;
        sort = Math.max(0, Math.min(SORTS.length - 1, prefs.getInt("sorting", 0)));
        tiles = prefs.getBoolean("tiles", false);
        chinese = prefs.getBoolean("chinese_names", false);
        setOrientation(VERTICAL);
        setBackgroundColor(PAGE);
        setFocusableInTouchMode(true);
        toolbar = ui.column();
        toolbar.setBackground(ui.gradient(0xff1465ad, 0xff2089d6, 0));
        addView(toolbar, new LayoutParams(-1, -2));
        FrameLayout band = ui.widthLimit(860);
        LayoutParams center = new LayoutParams(-1, -2);
        center.gravity = Gravity.CENTER_HORIZONTAL;
        toolbar.addView(band, center);
        LinearLayout nav = ui.column();
        nav.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        band.addView(nav, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout bar = ui.row();
        bar.setPadding(ui.dp(4), ui.dp(9), ui.dp(2), ui.dp(7));
        ImageView logo = ui.art(R.drawable.legacy_ui_launcher);
        bar.addView(logo, new LayoutParams(ui.dp(43), ui.dp(43)));
        LinearLayout branding = ui.column();
        branding.addView(ui.text("FBA Arcade", 23, WHITE, true));
        ui.space(branding, 3);
        branding.addView(ui.eyebrow("FINALBURN NEO", 0xffd9efff));
        LayoutParams brand = new LayoutParams(0, -2, 1);
        brand.setMarginStart(ui.dp(9));
        bar.addView(branding, brand);
        ImageButton open = ui.icon(R.drawable.legacy_ui_ex_folder, "Add ROMs", 0x24ffffff, WHITE, 11, 12);
        open.setOnClickListener(v -> actions.importZips());
        bar.addView(open, new LayoutParams(ui.dp(46), ui.dp(46)));
        ImageButton settings = ui.icon(R.drawable.legacy_ui_menu_setting, "Settings", 0x24ffffff, WHITE, 11, 11);
        settings.setOnClickListener(v -> actions.settings());
        LayoutParams iconSpace = new LayoutParams(ui.dp(46), ui.dp(46));
        iconSpace.setMarginStart(ui.dp(6));
        bar.addView(settings, iconSpace);
        nav.addView(bar);
        LinearLayout tabsRow = ui.row();
        for (int i = 0; i < TABS.length; i++) addTab(tabsRow, i);
        nav.addView(tabsRow);

        body = ui.widthLimit(860);
        LayoutParams content = new LayoutParams(-1, 0, 1);
        content.gravity = Gravity.CENTER_HORIZONTAL;
        addView(body, content);
        list = new ListView(context);
        list.setPadding(ui.dp(13), ui.dp(13), ui.dp(13), ui.dp(12));
        list.setClipToPadding(false);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(ui.dp(7));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setItemsCanFocus(true);
        list.setVerticalScrollBarEnabled(false);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));

        header = ui.column();
        notice = ui.text("", 13, NAVY, true);
        notice.setBackground(ui.surface(LIGHT_BLUE, BORDER, 10));
        notice.setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9));
        notice.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        LayoutParams noteSpace = new LayoutParams(-1, -2);
        noteSpace.bottomMargin = ui.dp(8);
        header.addView(notice, noteSpace);
        notice.setVisibility(GONE);
        libraryTools = ui.column();
        header.addView(libraryTools);
        LinearLayout heading = ui.row();
        section = ui.text("All games", 20, INK, true);
        if (Build.VERSION.SDK_INT >= 28) section.setAccessibilityHeading(true);
        heading.addView(section, new LayoutParams(0, -2, 1));
        count = ui.text("0", 12, NAVY, true);
        chip(count);
        heading.addView(count);
        libraryTools.addView(heading);
        ui.space(libraryTools, 9);
        search = new EditText(context);
        search.setSingleLine();
        search.setTextSize(15);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setHint("Search title or ROM name");
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.setBackground(ui.surface(WHITE, BORDER, 11));
        search.setPadding(ui.dp(12), ui.dp(7), ui.dp(12), ui.dp(7));
        search.setMinimumHeight(ui.dp(46));
        search.setCompoundDrawablePadding(ui.dp(8));
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, act, event) -> { if (act != EditorInfo.IME_ACTION_DONE) return false; hideKeyboard(); return true; });
        libraryTools.addView(search, new LayoutParams(-1, -2));
        ui.space(libraryTools, 7);
        LinearLayout controls = ui.row();
        sortButton = ui.button("Sorting ▾", LIGHT_BLUE, NAVY, 9);
        sortButton.setContentDescription("Sorting and game title language");
        sortButton.setOnClickListener(v -> showSorting());
        controls.addView(sortButton, new LayoutParams(-2, ui.dp(42)));
        boardChip = ui.text("Filter ▾", 13, NAVY, true);
        boardChip.setGravity(Gravity.CENTER);
        boardChip.setPadding(ui.dp(6), ui.dp(4), ui.dp(6), ui.dp(4));
        singleLine(boardChip);
        boardChip.setBackground(ui.tappable(WHITE, BORDER, 9, false));
        boardChip.setOnClickListener(v -> showFilters());
        boardChip.setFocusable(true);
        LayoutParams boardSpace = new LayoutParams(0, ui.dp(42), 1);
        boardSpace.setMargins(ui.dp(6), 0, ui.dp(6), 0);
        controls.addView(boardChip, boardSpace);
        viewButton = ui.button(tiles ? "List" : "Grid", WHITE, NAVY, 9);
        viewButton.setOnClickListener(v -> {
            tiles = !tiles;
            prefs.edit().putBoolean("tiles", tiles).apply();
            viewButton.setText(tiles ? "List" : "Grid");
            refresh();
        });
        controls.addView(viewButton, new LayoutParams(-2, ui.dp(42)));
        libraryTools.addView(controls);
        ui.space(libraryTools, 2);

        filesPanel = ui.column();
        filesPanel.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(14));
        filesPanel.setBackground(ui.surface(WHITE, BORDER, 14));
        filesPanel.addView(ui.eyebrow("ROM STORAGE", BLUE));
        ui.space(filesPanel, 5);
        filesPanel.addView(ui.text("Add games from a device folder", 20, INK, true));
        ui.space(filesPanel, 6);
        filesPanel.addView(ui.text("Pick ZIPs or browse a remembered device folder. Keep the original FBNeo set names and include required parent or BIOS ZIPs.", 14, MUTED, false));
        LinearLayout importOptions = ui.row();
        add = ui.button("Import ZIPs", BLUE, WHITE, 10);
        add.setOnClickListener(v -> actions.importZips());
        folder = ui.button("Browse folder", LIGHT_BLUE, NAVY, 10);
        folder.setOnClickListener(v -> actions.browseFolder());
        importOptions.addView(add, new LayoutParams(0, ui.dp(48), 1));
        LayoutParams fp = new LayoutParams(0, ui.dp(48), 1);
        fp.setMarginStart(ui.dp(7));
        importOptions.addView(folder, fp);
        ui.space(filesPanel, 11);
        filesPanel.addView(importOptions);
        header.addView(filesPanel, new LayoutParams(-1, -2));

        list.addHeaderView(header, null, false);
        list.setAdapter(rows);
        switchTab(0);
    }

    private void chip(TextView text) {
        text.setPadding(ui.dp(9), ui.dp(5), ui.dp(9), ui.dp(5));
        text.setBackground(ui.surface(LIGHT_BLUE, BORDER, 9));
    }

    private void addTab(LinearLayout strip, int index) {
        LinearLayout tab = ui.column();
        TextView label = ui.text(TABS[index], 14, WHITE, true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(ui.dp(1), ui.dp(5), ui.dp(1), ui.dp(5));
        label.setMinHeight(ui.dp(43));
        tab.addView(label, new LayoutParams(-1, -2));
        View underline = new View(getContext());
        underline.setBackgroundColor(0xffbdeaff);
        tab.addView(underline, new LayoutParams(-1, ui.dp(3)));
        tab.setFocusable(true);
        tab.setContentDescription(TABS[index]);
        tab.setOnClickListener(v -> switchTab(index));
        strip.addView(tab, new LayoutParams(0, -2, 1));
        tabs[index] = tab; indicators[index] = underline;
    }

    void setInsets(int left, int top, int right, int bottom) {
        setPadding(0, 0, 0, bottom);
        toolbar.setPadding(left, top, right, 0);
        body.setPadding(left, 0, right, 0);
    }

    void show(List<File> files, List<File> recent, Set<String> favorites, boolean importing, String note) {
        this.files = files; this.recent = recent; this.favorites = favorites; this.importing = importing;
        notice.setText(note);
        notice.setVisibility(note == null ? GONE : VISIBLE);
        add.setEnabled(!importing); folder.setEnabled(!importing);
        refresh();
    }

    void refreshCatalog() { refresh(); }
    boolean chineseNames() { return chinese; }

    private void switchTab(int selected) {
        hideKeyboard();
        tab = selected;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setSelected(i == tab);
            int fill = i == tab ? 0x20ffffff : Color.TRANSPARENT;
            tabs[i].setBackground(ui.tappable(fill, fill, 8, true));
            indicators[i].setVisibility(i == tab ? VISIBLE : INVISIBLE);
        }
        refresh();
        list.setSelection(0);
    }

    private void showSorting() {
        hideKeyboard();
        new AlertDialog.Builder(getContext()).setTitle("Sorting")
                .setSingleChoiceItems(SORTS, sort, (d, which) -> {
                    sort = which;
                    prefs.edit().putInt("sorting", sort).apply();
                    refresh(); d.dismiss();
                })
                .setNeutralButton(chinese ? "English titles" : "Chinese titles", (d, w) -> {
                    chinese = !chinese;
                    prefs.edit().putBoolean("chinese_names", chinese).apply();
                    refresh();
                }).setNegativeButton("Close", null).show();
    }

    private void showFilters() {
        hideKeyboard();
        String[] labels = {"Board · " + (board.isEmpty() ? "All" : board),
                "Category · " + (genre.isEmpty() ? "All" : genre), "Clear filters"};
        new AlertDialog.Builder(getContext()).setTitle("Filter games").setItems(labels, (dialog, which) -> {
            if (which == 2) { board = genre = ""; refresh(); }
            else showFilter(which == 0);
        }).setNegativeButton("Close", null).show();
    }

    private void showFilter(boolean byBoard) {
        TreeSet<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (File f : files) {
            GameCatalog.Game game = catalog.game(f);
            String value = byBoard ? game.board : game.genre;
            if (!value.isEmpty()) values.add(value);
        }
        List<String> menu = new ArrayList<>();
        menu.add(byBoard ? "All boards" : "All categories"); menu.addAll(values);
        new AlertDialog.Builder(getContext()).setTitle(byBoard ? "Board system" : "Game category")
                .setSingleChoiceItems(menu.toArray(new String[0]), Math.max(0, menu.indexOf(byBoard ? board : genre)), (d, i) -> {
                    String picked = i == 0 ? "" : menu.get(i);
                    if (byBoard) board = picked; else genre = picked;
                    refresh(); d.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void refresh() {
        boolean onFiles = tab == 3;
        filesPanel.setVisibility(onFiles ? VISIBLE : GONE);
        libraryTools.setVisibility(onFiles ? GONE : VISIBLE);
        section.setText(tab == 0 ? "All games" : tab == 1 ? "Favorite games" : "Recently played");
        int filters = (board.isEmpty() ? 0 : 1) + (genre.isEmpty() ? 0 : 1);
        boardChip.setText(filters == 0 ? "Filter ▾" : "Filter · " + filters + " ▾");
        String query = onFiles ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<File> shown = new ArrayList<>();
        for (File file : tab == 2 ? recent : files) {
            if (tab == 1 && !favorites.contains(file.getName())) continue;
            GameCatalog.Game game = catalog.game(file);
            if (!onFiles && ((!board.isEmpty() && !board.equalsIgnoreCase(game.board)) || (!genre.isEmpty() && !genre.equals(game.genre)))) continue;
            if (query.isEmpty() || file.getName().contains(query) || game.title(chinese).toLowerCase(Locale.ROOT).contains(query)
                    || game.english.toLowerCase(Locale.ROOT).contains(query)) shown.add(file);
        }
        if (tab == 0 || tab == 1) {
            Collator collator = Collator.getInstance(chinese ? Locale.CHINA : Locale.ENGLISH);
            Comparator<File> title = (a, b) -> collator.compare(catalog.game(a).title(chinese), catalog.game(b).title(chinese));
            Comparator<File> bySet = Comparator.comparing(File::getName);
            switch (sort) {
                case 1: shown.sort(bySet); break;
                case 2: shown.sort(Comparator.<File, String>comparing(f -> catalog.game(f).year).reversed().thenComparing(title)); break;
                case 3: shown.sort(Comparator.comparingLong(File::lastModified).reversed().thenComparing(bySet)); break;
                case 4: shown.sort(Comparator.<File, String>comparing(f -> catalog.game(f).board).thenComparing(title)); break;
                default: shown.sort(title.thenComparing(bySet));
            }
        }
        rows.shown = shown;
        count.setText(shown.size() + (shown.size() == 1 ? " game" : " games"));
        rows.notifyDataSetChanged();
    }

    void hideKeyboard() {
        InputMethodManager keyboard = getContext().getSystemService(InputMethodManager.class);
        if (keyboard != null && getWindowToken() != null) keyboard.hideSoftInputFromWindow(getWindowToken(), 0);
        search.clearFocus();
        requestFocus();
    }

    private View empty() {
        LinearLayout panel = ui.column();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(ui.dp(16), ui.dp(24), ui.dp(16), ui.dp(22));
        panel.setBackground(ui.surface(WHITE, BORDER, 14));
        ImageView image = ui.art(files.isEmpty() ? R.drawable.legacy_ui_blank_state : R.drawable.legacy_ui_games);
        panel.addView(image, new LayoutParams(ui.dp(71), ui.dp(48)));
        ui.space(panel, 10);
        String title = files.isEmpty() ? "No games found" : tab == 1 && favorites.isEmpty() ? "No favorites yet"
                : tab == 2 && recent.isEmpty() ? "Nothing played yet" : "No matching games";
        panel.addView(ui.text(title, 18, INK, true));
        ui.space(panel, 6);
        String detail = files.isEmpty() ? "Select a ROM ZIP or choose a folder from the Files tab."
                : tab == 1 && favorites.isEmpty() ? "Open a game's menu to add it to Favorites."
                : tab == 2 && recent.isEmpty() ? "Launch a game to see it here." : "Try a different search or clear the filters.";
        TextView tip = ui.text(detail, 14, MUTED, false);
        tip.setGravity(Gravity.CENTER);
        panel.addView(tip);
        if (files.isEmpty()) {
            Button go = ui.button("＋ Import ZIPs", BLUE, WHITE, 10);
            go.setEnabled(!importing);
            go.setOnClickListener(v -> actions.importZips());
            ui.space(panel, 10);
            panel.addView(go, new LayoutParams(-2, ui.dp(48)));
        }
        panel.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        return panel;
    }

    private final class Rows extends BaseAdapter {
        List<File> shown = Collections.emptyList();
        boolean grid() { return tiles && tab != 3; }
        @Override public int getCount() { return shown.isEmpty() ? tab == 3 ? 0 : 1 : grid() ? (shown.size() + 1) / 2 : shown.size(); }
        @Override public Object getItem(int position) { return shown.isEmpty() ? null : shown.get(grid() ? position * 2 : position); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 3; }
        @Override public int getItemViewType(int position) { return shown.isEmpty() ? 2 : grid() ? 1 : 0; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int i) { return !shown.isEmpty() && !importing; }
        @Override public View getView(int i, View reused, ViewGroup parent) {
            if (shown.isEmpty()) return empty();
            if (grid()) {
                Pair pair = reused instanceof Pair ? (Pair) reused : new Pair();
                pair.first.bind(shown.get(2 * i));
                boolean hasRight = 2 * i + 1 < shown.size();
                pair.second.setVisibility(hasRight ? VISIBLE : INVISIBLE);
                if (hasRight) pair.second.bind(shown.get(2 * i + 1));
                return pair;
            }
            Card card = reused instanceof Card ? (Card) reused : new Card(false);
            card.bind(shown.get(i));
            return card;
        }
    }

    private final class Pair extends LinearLayout {
        final Card first = new Card(true), second = new Card(true);
        Pair() {
            super(LibraryScreen.this.getContext());
            setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            addView(first, new LayoutParams(0, -1, 1));
            View gutter = new View(getContext());
            addView(gutter, new LayoutParams(ui.dp(8), 1));
            addView(second, new LayoutParams(0, -1, 1));
        }
    }

    private final class Card extends LinearLayout {
        final LinearLayout play, labels;
        final TextView name, info;
        final ImageView art;
        final ImageButton options;
        final boolean grid;
        Card(boolean grid) {
            super(LibraryScreen.this.getContext());
            this.grid = grid;
            setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            setGravity(Gravity.CENTER_VERTICAL);
            setBackground(ui.surface(WHITE, BORDER, 12));
            if (grid) setOrientation(VERTICAL);
            setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
            play = grid ? ui.column() : ui.row();
            play.setGravity(grid ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
            play.setBackground(ui.tappable(WHITE, WHITE, 9, false));
            play.setPadding(ui.dp(6), ui.dp(6), ui.dp(5), ui.dp(6));
            play.setFocusable(true);
            addView(play, grid ? new LayoutParams(-1, -2) : new LayoutParams(0, -2, 1));
            art = ui.art(R.drawable.legacy_ui_games);
            art.setPadding(ui.dp(grid ? 6 : 4), ui.dp(3), ui.dp(grid ? 6 : 4), ui.dp(3));
            art.setBackground(ui.surface(0xffe9f2fb, BORDER, 9));
            play.addView(art, grid ? new LayoutParams(-1, ui.dp(91)) : new LayoutParams(ui.dp(66), ui.dp(57)));
            labels = ui.column();
            name = ui.text("", grid ? 14 : 16, INK, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            info = ui.text("", 12, MUTED, false);
            singleLine(info);
            labels.addView(name);
            ui.space(labels, 4);
            labels.addView(info);
            LayoutParams tp = grid ? new LayoutParams(-1, -2) : new LayoutParams(0, -2, 1);
            if (grid) tp.topMargin = ui.dp(7); else tp.setMarginStart(ui.dp(9));
            play.addView(labels, tp);
            options = ui.icon(R.drawable.legacy_ui_menu_gameinfo, "Game options", Color.TRANSPARENT, NAVY, 9, 12);
            LayoutParams op = new LayoutParams(ui.dp(45), ui.dp(44));
            if (grid) op.gravity = Gravity.END;
            addView(options, op);
        }
        void bind(File file) {
            GameCatalog.Game game = catalog.game(file);
            catalog.showArt(art, game);
            boolean favorite = favorites.contains(file.getName());
            name.setText((favorite ? "★ " : "") + game.title(chinese));
            info.setText(grid && !game.board.isEmpty() ? game.board : game.subtitle());
            play.setContentDescription("Play " + game.title(chinese));
            play.setEnabled(!importing);
            play.setOnClickListener(v -> { hideKeyboard(); actions.play(file); });
            play.setOnLongClickListener(v -> { actions.options(options, file); return true; });
            options.setContentDescription("Options for " + game.title(chinese));
            options.setEnabled(!importing);
            options.setOnClickListener(v -> actions.options(options, file));
        }
    }
}
