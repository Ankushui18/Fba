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
import android.widget.ProgressBar;
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

/** The library starts with one import action, then shows games and optional Continue. */
final class LibraryScreen extends LinearLayout {
    interface Actions {
        void importZips();
        void browseFolder();
        void settings();
        void play(File file);
        void continueGame(QuickResumeStore.Entry entry);
        void options(View anchor, File file);
    }

    private static final int ALL = 0, FAVORITES = 1, RECENT = 2;
    private static final String[] TABS = {"All games", "Favorites", "Recent"};
    private static final String[] SORTS = {"Game title A–Z", "ROM filename A–Z", "Release year · newest", "Recently added", "Board system"};
    private final ArcadeUi ui;
    private final Actions actions;
    private final GameCatalog catalog;
    private final SharedPreferences prefs;
    private final LinearLayout toolbar, noticeRow, continueCard, libraryTools;
    private final FrameLayout body, welcome;
    private final Button[] tabs = new Button[TABS.length];
    private final ListView list;
    private final TextView count, notice, resumeTitle;
    private final ProgressBar progress;
    private final EditText search;
    private final ImageButton settings, clearSearch;
    private final Button topAdd, add, folder, options;
    private final Rows rows = new Rows();
    private QuickResumeStore.Entry checkpoint;
    private List<File> files = Collections.emptyList(), recent = Collections.emptyList();
    private Set<String> favorites = Collections.emptySet();
    private int tab = ALL, sort;
    private String board = "", genre = "";
    private boolean importing, tiles, chinese;

    LibraryScreen(Context context, GameCatalog catalog, SharedPreferences prefs, Actions actions) {
        super(context);
        ui = new ArcadeUi(context);
        this.catalog = catalog;
        this.prefs = prefs;
        this.actions = actions;
        reloadSettings();
        setOrientation(VERTICAL);
        setBackgroundColor(PAGE);
        setFocusableInTouchMode(true);

        toolbar = ui.column();
        addView(toolbar, new LayoutParams(-1, -2));
        FrameLayout band = ui.widthLimit(860);
        addCentered(toolbar, band);
        LinearLayout bar = ui.row();
        bar.setPaddingRelative(ui.dp(16), ui.dp(8), ui.dp(12), ui.dp(8));
        band.addView(bar, new FrameLayout.LayoutParams(-1, -2));
        ImageView mark = badge(R.drawable.ic_library_pad, BLUE, WHITE, 7, 11);
        bar.addView(mark, new LayoutParams(ui.dp(35), ui.dp(35)));
        TextView branding = ui.text("FBA Arcade", 21, INK, true);
        branding.setMaxLines(2);
        branding.setEllipsize(TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= 28) branding.setAccessibilityHeading(true);
        LayoutParams brand = new LayoutParams(0, -2, 1);
        brand.setMarginStart(ui.dp(9));
        bar.addView(branding, brand);
        topAdd = ui.button("Add", LIGHT_BLUE, NAVY, 12);
        topAdd.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_library_add, 0, 0, 0);
        topAdd.setCompoundDrawablePadding(ui.dp(4));
        topAdd.setContentDescription("Add games");
        topAdd.setTooltipText("Add games");
        topAdd.setMinimumHeight(ui.dp(48));
        topAdd.setOnClickListener(v -> importGames());
        LayoutParams addSpace = new LayoutParams(-2, -2);
        addSpace.setMarginStart(ui.dp(6));
        bar.addView(topAdd, addSpace);
        settings = ui.icon(R.drawable.ic_library_settings, "Settings", Color.TRANSPARENT, INK, 12, 12);
        settings.setOnClickListener(v -> { if (!importing) { hideKeyboard(); actions.settings(); } });
        bar.addView(settings, new LayoutParams(ui.dp(48), ui.dp(48)));

        body = ui.widthLimit(860);
        LayoutParams content = new LayoutParams(-1, 0, 1);
        content.gravity = Gravity.CENTER_HORIZONTAL;
        addView(body, content);
        list = new ListView(context);
        list.setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(18));
        list.setClipToPadding(false);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(ui.dp(8));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setItemsCanFocus(true);
        list.setVerticalScrollBarEnabled(false);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));

        // Everything below the compact app bar scrolls, including onboarding in landscape.
        LinearLayout header = ui.column();
        noticeRow = ui.row();
        noticeRow.setBackground(ui.surface(WHITE, BORDER, 12));
        noticeRow.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        LayoutParams progressSpace = new LayoutParams(ui.dp(18), ui.dp(18));
        progressSpace.setMarginEnd(ui.dp(10));
        progress.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        noticeRow.addView(progress, progressSpace);
        notice = ui.text("", 14, INK, false);
        notice.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        noticeRow.addView(notice, new LayoutParams(0, -2, 1));
        addWithGap(header, noticeRow, 12);

        continueCard = ui.row();
        continueCard.setBackground(ui.tappable(LIGHT_BLUE, 0xffcadff6, 16, false));
        continueCard.setPadding(ui.dp(12), ui.dp(11), ui.dp(12), ui.dp(11));
        continueCard.setFocusable(true);
        continueCard.addView(badge(R.drawable.ic_library_play, BLUE, WHITE, 8, 11), new LayoutParams(ui.dp(38), ui.dp(38)));
        LinearLayout resumeLabels = ui.column();
        resumeLabels.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        resumeLabels.addView(ui.text("Continue", 16, NAVY, true));
        ui.space(resumeLabels, 4);
        resumeTitle = ui.text("", 14, MUTED, false);
        resumeTitle.setMaxLines(2);
        resumeTitle.setEllipsize(TextUtils.TruncateAt.END);
        resumeLabels.addView(resumeTitle);
        LayoutParams resumeSpace = new LayoutParams(0, -2, 1);
        resumeSpace.setMarginStart(ui.dp(11));
        continueCard.addView(resumeLabels, resumeSpace);
        continueCard.setOnClickListener(v -> {
            if (!importing && checkpoint != null) { hideKeyboard(); actions.continueGame(checkpoint); }
        });
        addWithGap(header, continueCard, 12);

        welcome = ui.widthLimit(480);
        LinearLayout panel = ui.column();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackground(ui.surface(WHITE, BORDER, 20));
        panel.setPadding(ui.dp(22), ui.dp(26), ui.dp(22), ui.dp(18));
        welcome.addView(panel, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(badge(R.drawable.ic_library_pad, LIGHT_BLUE, BLUE, 13, 17), new LayoutParams(ui.dp(57), ui.dp(57)));
        ui.space(panel, 15);
        TextView intro = ui.text("Add your first game", 23, INK, true);
        intro.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 28) intro.setAccessibilityHeading(true);
        panel.addView(intro);
        ui.space(panel, 8);
        TextView tip = ui.text("Choose game ZIPs or console ROMs. Games aren't included.", 15, MUTED, false);
        tip.setGravity(Gravity.CENTER);
        tip.setLineSpacing(ui.dp(3), 1f);
        panel.addView(tip);
        ui.space(panel, 20);
        add = ui.button("Add games", BLUE, WHITE, 12);
        add.setMinimumHeight(ui.dp(50));
        add.setOnClickListener(v -> importGames());
        addWithGap(panel, add, 3);
        folder = ui.button("Browse folder", Color.TRANSPARENT, NAVY, 12);
        folder.setMinimumHeight(ui.dp(48));
        folder.setOnClickListener(v -> {
            if (!importing) { hideKeyboard(); actions.browseFolder(); }
        });
        panel.addView(folder, new LayoutParams(-1, -2));
        LayoutParams welcomeSpace = new LayoutParams(-1, -2);
        welcomeSpace.gravity = Gravity.CENTER_HORIZONTAL;
        welcomeSpace.topMargin = ui.dp(16);
        header.addView(welcome, welcomeSpace);

        libraryTools = ui.column();
        header.addView(libraryTools);
        LinearLayout searchRow = ui.row();
        FrameLayout searchBox = new FrameLayout(context);
        searchBox.setBackground(ui.surface(WHITE, BORDER, 12));
        search = new EditText(context);
        search.setSingleLine();
        search.setTextSize(15);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setHint("Search games");
        search.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setMinimumHeight(ui.dp(48));
        search.setCompoundDrawablePadding(ui.dp(8));
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        searchBox.addView(search, new FrameLayout.LayoutParams(-1, -2));
        clearSearch = ui.icon(R.drawable.ic_library_clear, "Clear search", Color.TRANSPARENT, MUTED, 12, 13);
        clearSearch.setOnClickListener(v -> { if (!importing) { search.setText(""); hideKeyboard(); } });
        searchBox.addView(clearSearch, new FrameLayout.LayoutParams(ui.dp(48), ui.dp(48), Gravity.END | Gravity.CENTER_VERTICAL));
        searchRow.addView(searchBox, new LayoutParams(0, -2, 1));
        options = ui.button("Filter & sort", WHITE, NAVY, 12);
        options.setMinimumHeight(ui.dp(48));
        options.setOnClickListener(v -> showOptions());
        LayoutParams optionSpace = new LayoutParams(-2, -2);
        optionSpace.setMarginStart(ui.dp(8));
        searchRow.addView(options, optionSpace);
        addWithGap(libraryTools, searchRow, 10);

        LinearLayout tabStrip = ui.row();
        tabStrip.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        tabStrip.setBackground(ui.surface(WHITE, BORDER, 13));
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            Button button = ui.button(TABS[i], Color.TRANSPARENT, MUTED, 10);
            button.setTextSize(13);
            button.setPadding(ui.dp(4), ui.dp(5), ui.dp(4), ui.dp(5));
            button.setMinimumHeight(ui.dp(48));
            button.setOnClickListener(v -> switchTab(index));
            tabs[i] = button;
            tabStrip.addView(button, new LayoutParams(0, -1, 1));
        }
        addWithGap(libraryTools, tabStrip, 10);
        count = ui.text("", 13, MUTED, false);
        count.setPaddingRelative(ui.dp(2), 0, 0, ui.dp(1));
        libraryTools.addView(count);

        list.addHeaderView(header, null, false);
        list.setAdapter(rows);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, act, event) -> {
            if (act != EditorInfo.IME_ACTION_SEARCH) return false;
            hideKeyboard();
            return true;
        });
        show(files, recent, favorites, null, false, null);
    }

    private void addCentered(LinearLayout parent, View view) {
        LayoutParams lp = new LayoutParams(-1, -2);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        parent.addView(view, lp);
    }

    private void addWithGap(LinearLayout parent, View view, int gap) {
        LayoutParams lp = new LayoutParams(-1, -2);
        lp.bottomMargin = ui.dp(gap);
        parent.addView(view, lp);
    }

    private ImageView badge(int image, int fill, int tint, int padding, int radius) {
        ImageView art = ui.art(image);
        art.setColorFilter(tint);
        art.setBackground(ui.surface(fill, fill, radius));
        int space = ui.dp(padding);
        art.setPadding(space, space, space, space);
        return art;
    }

    private void importGames() {
        if (!importing) { hideKeyboard(); actions.importZips(); }
    }

    void setInsets(int left, int top, int right, int bottom) {
        setPadding(0, 0, 0, bottom);
        toolbar.setPadding(left, top, right, 0);
        body.setPadding(left, 0, right, 0);
    }

    void show(List<File> files, List<File> recent, Set<String> favorites, QuickResumeStore.Entry checkpoint, boolean importing, String note) {
        this.files = files;
        this.recent = recent;
        this.favorites = favorites;
        this.importing = importing;
        this.checkpoint = checkpoint;
        notice.setText(note);
        noticeRow.setVisibility(note == null ? GONE : VISIBLE);
        progress.setVisibility(importing ? VISIBLE : GONE);
        for (View action : new View[] {topAdd, add, folder, settings, search, clearSearch, options, continueCard}) action.setEnabled(!importing);
        if (files.isEmpty()) revealGames(); else refresh();
        if (note != null) scrollToTop();
    }

    void reloadSettings() {
        sort = Math.max(0, Math.min(SORTS.length - 1, prefs.getInt("sorting", 0)));
        tiles = prefs.getBoolean("tiles", false);
        chinese = prefs.getBoolean("chinese_names", false);
    }

    void refreshCatalog() { refresh(); }
    boolean chineseNames() { return chinese; }
    void scrollToTop() { list.setSelection(0); }

    /** A successful import always reveals the games, not a previous filter or Favorites tab. */
    void revealGames() {
        tab = ALL;
        clearCriteria();
        scrollToTop();
    }

    private void clearCriteria() {
        board = genre = "";
        if (search.length() > 0) search.setText(""); // The text watcher refreshes the rows.
        else refresh();
    }

    private void switchTab(int selected) {
        if (importing) return;
        hideKeyboard();
        tab = selected;
        refresh();
        scrollToTop();
    }

    private void showOptions() {
        if (importing) return;
        hideKeyboard();
        String[] labels = {"Sort by · " + SORTS[sort], "Board · " + (board.isEmpty() ? "All" : board),
                "Category · " + (genre.isEmpty() ? "All" : genre), tiles ? "View as list" : "View as grid",
                chinese ? "Use English game titles" : "Use Chinese game titles", "Clear filters"};
        new AlertDialog.Builder(getContext()).setTitle("Library options").setItems(labels, (d, i) -> {
            switch (i) {
                case 0: showSorting(); break;
                case 1: showFilter(true); break;
                case 2: showFilter(false); break;
                case 3:
                    tiles = !tiles;
                    prefs.edit().putBoolean("tiles", tiles).apply();
                    refresh();
                    scrollToTop();
                    break;
                case 4:
                    chinese = !chinese;
                    prefs.edit().putBoolean("chinese_names", chinese).apply();
                    refresh();
                    break;
                case 5: board = genre = ""; refresh(); break;
                default: break;
            }
        }).setNegativeButton("Close", null).show();
    }

    private void showSorting() {
        new AlertDialog.Builder(getContext()).setTitle("Sort games")
                .setSingleChoiceItems(SORTS, sort, (d, which) -> {
                    sort = which;
                    prefs.edit().putInt("sorting", sort).apply();
                    refresh();
                    d.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showFilter(boolean byBoard) {
        TreeSet<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (File f : files) {
            GameCatalog.Game game = catalog.game(f);
            String value = byBoard ? game.board : game.genre;
            if (!value.isEmpty()) values.add(value);
        }
        List<String> menu = new ArrayList<>();
        menu.add(byBoard ? "All boards" : "All categories");
        menu.addAll(values);
        new AlertDialog.Builder(getContext()).setTitle(byBoard ? "Board system" : "Game category")
                .setSingleChoiceItems(menu.toArray(new String[0]), Math.max(0, menu.indexOf(byBoard ? board : genre)), (d, i) -> {
                    String picked = i == 0 ? "" : menu.get(i);
                    if (byBoard) board = picked; else genre = picked;
                    refresh();
                    d.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void refresh() {
        boolean hasGames = !files.isEmpty();
        topAdd.setVisibility(hasGames ? VISIBLE : GONE);
        welcome.setVisibility(hasGames ? GONE : VISIBLE);
        libraryTools.setVisibility(hasGames ? VISIBLE : GONE);
        continueCard.setVisibility(checkpoint == null ? GONE : VISIBLE);
        if (checkpoint != null) {
            String title = catalog.game(new File(checkpoint.rom)).title(chinese) + (checkpoint.variant == null ? "" : " · IPS");
            resumeTitle.setText(title);
            continueCard.setContentDescription("Continue " + title);
        }
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == tab;
            int fill = selected ? LIGHT_BLUE : Color.TRANSPARENT;
            tabs[i].setSelected(selected);
            tabs[i].setEnabled(!importing);
            tabs[i].setTextColor(selected ? NAVY : MUTED);
            tabs[i].setBackground(ui.tappable(fill, fill, 10, false));
        }
        int filters = (board.isEmpty() ? 0 : 1) + (genre.isEmpty() ? 0 : 1);
        options.setText(filters == 0 ? "Filter & sort" : filters + (filters == 1 ? " filter" : " filters"));
        options.setContentDescription(filters == 0 ? "Filter & sort" : "Filter & sort, " + filters + " active " + (filters == 1 ? "filter" : "filters"));
        options.setBackground(ui.tappable(filters == 0 ? WHITE : LIGHT_BLUE, BORDER, 12, false));
        boolean canClear = search.length() > 0;
        clearSearch.setVisibility(canClear ? VISIBLE : GONE);
        search.setPaddingRelative(ui.dp(12), ui.dp(9), ui.dp(canClear ? 48 : 12), ui.dp(9));
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<File> shown = new ArrayList<>();
        int candidates = 0;
        for (File file : tab == RECENT ? recent : files) {
            if (tab == FAVORITES && !favorites.contains(file.getName())) continue;
            candidates++;
            GameCatalog.Game game = catalog.game(file);
            if ((!board.isEmpty() && !board.equalsIgnoreCase(game.board)) || (!genre.isEmpty() && !genre.equalsIgnoreCase(game.genre))) continue;
            if (query.isEmpty() || file.getName().contains(query) || game.english.toLowerCase(Locale.ROOT).contains(query)
                    || game.chinese.toLowerCase(Locale.ROOT).contains(query)) shown.add(file);
        }
        if (tab != RECENT && shown.size() > 1) {
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
        rows.emptyShelf = candidates == 0;
        count.setText(shown.size() + (shown.size() == 1 ? " game" : " games"));
        count.setVisibility(shown.isEmpty() ? GONE : VISIBLE);
        rows.notifyDataSetChanged();
    }

    void hideKeyboard() {
        InputMethodManager keyboard = getContext().getSystemService(InputMethodManager.class);
        if (keyboard != null && getWindowToken() != null) keyboard.hideSoftInputFromWindow(getWindowToken(), 0);
        search.clearFocus();
        requestFocus();
    }

    private View noResults() {
        LinearLayout panel = ui.column();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(ui.dp(16), ui.dp(21), ui.dp(16), ui.dp(12));
        panel.setBackground(ui.surface(WHITE, BORDER, 16));
        boolean noFavorites = rows.emptyShelf && tab == FAVORITES, noRecent = rows.emptyShelf && tab == RECENT;
        String title = noFavorites ? "No favorites yet" : noRecent ? "No recently played games" : "No matching games";
        TextView heading = ui.text(title, 17, INK, true);
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading);
        ui.space(panel, 7);
        TextView tip = ui.text(noFavorites ? "Use a game's ⋮ menu to add it to Favorites."
                : noRecent ? "Play a game and it will appear here." : "Try a different search or clear your filters.", 14, MUTED, false);
        tip.setGravity(Gravity.CENTER);
        panel.addView(tip);
        boolean emptyShelf = noFavorites || noRecent;
        Button recover = ui.button(emptyShelf ? "Show all games" : "Clear search & filters", Color.TRANSPARENT, NAVY, 12);
        recover.setMinimumHeight(ui.dp(48));
        recover.setEnabled(!importing);
        recover.setOnClickListener(v -> {
            if (importing) return;
            hideKeyboard();
            if (emptyShelf) revealGames(); else { clearCriteria(); scrollToTop(); }
        });
        ui.space(panel, 4);
        panel.addView(recover);
        panel.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        return panel;
    }

    private final class Rows extends BaseAdapter {
        List<File> shown = Collections.emptyList();
        boolean emptyShelf;
        @Override public int getCount() {
            if (shown.isEmpty()) return files.isEmpty() ? 0 : 1;
            return tiles ? (shown.size() + 1) / 2 : shown.size();
        }
        @Override public Object getItem(int position) { return shown.isEmpty() ? null : shown.get(tiles ? position * 2 : position); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 3; }
        @Override public int getItemViewType(int position) { return shown.isEmpty() ? 2 : tiles ? 1 : 0; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int i) { return !shown.isEmpty() && !importing; }
        @Override public View getView(int i, View reused, ViewGroup parent) {
            if (shown.isEmpty()) return noResults();
            if (tiles) {
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
            addView(new View(getContext()), new LayoutParams(ui.dp(8), 1));
            addView(second, new LayoutParams(0, -1, 1));
        }
    }

    private final class Card extends LinearLayout {
        final LinearLayout play;
        final TextView name, info;
        final ImageView art;
        final ImageButton menu;
        Card(boolean grid) {
            super(LibraryScreen.this.getContext());
            setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            setGravity(Gravity.CENTER_VERTICAL);
            setBackground(ui.surface(WHITE, BORDER, 14));
            if (grid) setOrientation(VERTICAL);
            setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
            play = grid ? ui.column() : ui.row();
            play.setBackground(ui.tappable(WHITE, WHITE, 11, false));
            play.setPaddingRelative(ui.dp(7), ui.dp(7), ui.dp(5), ui.dp(7));
            play.setFocusable(true);
            addView(play, grid ? new LayoutParams(-1, -2) : new LayoutParams(0, -2, 1));
            art = ui.art(R.drawable.ic_library_pad);
            art.setPadding(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(3));
            art.setBackground(ui.surface(LIGHT_BLUE, LIGHT_BLUE, 10));
            // FIT_CENTER keeps bundled icons and larger user screenshots in proportion.
            play.addView(art, grid ? new LayoutParams(-1, ui.dp(91)) : new LayoutParams(ui.dp(58), ui.dp(54)));
            LinearLayout labels = ui.column();
            labels.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            name = ui.text("", grid ? 15 : 16, INK, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            info = ui.text("", 13, MUTED, false);
            singleLine(info);
            labels.addView(name);
            ui.space(labels, 4);
            labels.addView(info);
            LayoutParams textSpace = grid ? new LayoutParams(-1, -2) : new LayoutParams(0, -2, 1);
            if (grid) textSpace.topMargin = ui.dp(8); else textSpace.setMarginStart(ui.dp(11));
            play.addView(labels, textSpace);
            menu = ui.icon(R.drawable.ic_library_more, "Game options", Color.TRANSPARENT, MUTED, 12, 12);
            LayoutParams menuSpace = new LayoutParams(ui.dp(48), ui.dp(48));
            if (grid) menuSpace.gravity = Gravity.END;
            addView(menu, menuSpace);
        }

        void bind(File file) {
            GameCatalog.Game game = catalog.game(file);
            catalog.showArt(art, game);
            boolean favorite = favorites.contains(file.getName());
            String title = game.title(chinese);
            name.setText((favorite ? "★ " : "") + title);
            info.setText(game.subtitle());
            play.setContentDescription("Play " + title + (favorite ? ", favorite" : ""));
            play.setEnabled(!importing);
            play.setOnClickListener(v -> { if (!importing) { hideKeyboard(); actions.play(file); } });
            play.setOnLongClickListener(v -> { openGameOptions(file); return true; });
            menu.setContentDescription("Options for " + title);
            menu.setEnabled(!importing);
            menu.setOnClickListener(v -> openGameOptions(file));
        }

        private void openGameOptions(File game) {
            if (!importing) { hideKeyboard(); actions.options(menu, game); }
        }
    }
}
