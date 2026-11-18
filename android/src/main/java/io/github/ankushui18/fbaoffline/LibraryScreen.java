package io.github.ankushui18.fbaoffline;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.Formatter;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static io.github.ankushui18.fbaoffline.ArcadeUi.*;

/** Legacy-style toolbar and library tabs with local search and recycled ROM rows. */
final class LibraryScreen extends LinearLayout {
    interface Actions {
        void importZips();
        void settings();
        void play(File file);
        void options(View anchor, File file);
    }

    private final ArcadeUi ui;
    private final Actions actions;
    private final LinearLayout toolbar;
    private final FrameLayout body;
    private final View[] tabs = new View[2], indicators = new View[2];
    private final ListView list;
    private final TextView section, count, notice;
    private final EditText search;
    private final Button add;
    private final PadPreview preview;
    private final ArchiveRows rows = new ArchiveRows();
    private List<File> files = Collections.emptyList(), recent = Collections.emptyList();
    private boolean recentTab, importing;

    LibraryScreen(Context context, PadSkin skin, Actions actions) {
        super(context);
        ui = new ArcadeUi(context);
        this.actions = actions;
        setOrientation(VERTICAL);
        setBackgroundColor(PAGE);
        setFocusableInTouchMode(true);

        toolbar = ui.column();
        toolbar.setBackground(ui.gradient(NAVY, 0xff1975c7, 0));
        addView(toolbar, new LayoutParams(-1, -2));
        FrameLayout barWidth = ui.widthLimit(740);
        toolbar.addView(barWidth, centeredWidth());
        LinearLayout navigation = ui.column();
        navigation.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        barWidth.addView(navigation, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout bar = ui.row();
        bar.setPadding(0, ui.dp(11), 0, ui.dp(11));
        ImageView logo = ui.art(R.drawable.legacy_ui_launcher);
        logo.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        logo.setBackground(ui.surface(0x30ffffff, 0x30ffffff, 13));
        bar.addView(logo, new LayoutParams(ui.dp(48), ui.dp(48)));
        LinearLayout branding = ui.column();
        TextView title = ui.text("FBA Offline", 23, WHITE, true);
        singleLine(title);
        branding.addView(title);
        ui.space(branding, 4);
        TextView sub = ui.eyebrow("ARCADE LIBRARY", 0xffdaf0ff);
        singleLine(sub);
        branding.addView(sub);
        LayoutParams brandSpace = new LayoutParams(0, -2, 1);
        brandSpace.setMarginStart(ui.dp(11));
        brandSpace.setMarginEnd(ui.dp(9));
        bar.addView(branding, brandSpace);
        ImageButton settings = ui.icon(R.drawable.legacy_ui_menu_setting, "Settings", 0x26ffffff, WHITE, 13, 12);
        settings.setOnClickListener(v -> actions.settings());
        bar.addView(settings, new LayoutParams(ui.dp(48), ui.dp(48)));
        navigation.addView(bar, new LayoutParams(-1, -2));
        LinearLayout tabBar = ui.row();
        addTab(tabBar, 0, "Library", R.drawable.legacy_ui_games);
        addTab(tabBar, 1, "Recent", R.drawable.legacy_ui_menu_list);
        navigation.addView(tabBar, new LayoutParams(-1, -2));

        body = ui.widthLimit(740);
        LayoutParams bodyParams = new LayoutParams(-1, 0, 1);
        bodyParams.gravity = Gravity.CENTER_HORIZONTAL;
        addView(body, bodyParams);
        list = new ListView(context);
        list.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(14));
        list.setClipToPadding(false);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(ui.dp(8));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setItemsCanFocus(true);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = ui.column();
        LinearLayout feature = ui.row();
        feature.setPadding(ui.dp(16), ui.dp(17), ui.dp(12), ui.dp(17));
        feature.setBackground(ui.gradient(0xff132e4d, 0xff1a507e, 19));
        LinearLayout copy = ui.column();
        copy.addView(ui.eyebrow("●  OFFLINE MODE", 0xffa3dcff));
        ui.space(copy, 6);
        copy.addView(ui.text("Local arcade", 26, WHITE, true));
        ui.space(copy, 5);
        copy.addView(ui.text("Play your own FBNeo ROM sets.", 14, 0xffd5e9fa, false));
        ui.space(copy, 13);
        add = ui.button("＋  Import ZIP", BLUE, WHITE, 11);
        add.setMinimumHeight(ui.dp(48));
        add.setOnClickListener(v -> actions.importZips());
        copy.addView(add, new LayoutParams(-2, -2));
        feature.addView(copy, new LayoutParams(0, -2, 1));
        preview = new PadPreview(context, skin, true);
        feature.addView(preview, new LayoutParams(ui.dp(110), ui.dp(104)));
        header.addView(feature, new LayoutParams(-1, -2));
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            boolean show = r - l >= ui.dp(268) && context.getResources().getConfiguration().fontScale <= 1.35f;
            int visibility = show ? VISIBLE : GONE;
            if (preview.getVisibility() != visibility) preview.setVisibility(visibility);
        });

        notice = ui.text("", 13, NAVY, true);
        notice.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        notice.setBackground(ui.surface(LIGHT_BLUE, BORDER, 11));
        notice.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        LayoutParams noticeSpace = new LayoutParams(-1, -2);
        noticeSpace.topMargin = ui.dp(12);
        header.addView(notice, noticeSpace);
        notice.setVisibility(GONE);

        ui.space(header, 18);
        LinearLayout sectionRow = ui.row();
        section = ui.text("Imported ROMs", 20, INK, true);
        if (Build.VERSION.SDK_INT >= 28) section.setAccessibilityHeading(true);
        sectionRow.addView(section, new LayoutParams(0, -2, 1));
        count = ui.text("0 ZIPs", 12, NAVY, true);
        count.setPadding(ui.dp(9), ui.dp(5), ui.dp(9), ui.dp(5));
        count.setBackground(ui.surface(LIGHT_BLUE, BORDER, 9));
        sectionRow.addView(count);
        header.addView(sectionRow, new LayoutParams(-1, -2));

        search = new EditText(context);
        search.setSingleLine();
        search.setTextSize(15);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setHint("Find a ROM by filename");
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.setBackground(ui.surface(WHITE, BORDER, 11));
        search.setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9));
        search.setMinimumHeight(ui.dp(48));
        search.setCompoundDrawablePadding(ui.dp(9));
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshRows(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            hideSearchKeyboard();
            return true;
        });
        LayoutParams querySpace = new LayoutParams(-1, -2);
        querySpace.topMargin = ui.dp(9);
        header.addView(search, querySpace);
        search.setVisibility(GONE);
        TextView foot = ui.text("Private storage  ·  FinalBurn Neo 1.0.0.03", 12, MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, ui.dp(8), 0, ui.dp(8));
        list.addHeaderView(header, null, false);
        list.addFooterView(foot, null, false);
        list.setAdapter(rows);
        updateTabs();
    }

    void setInsets(int left, int top, int right, int bottom) {
        setPadding(0, 0, 0, bottom);
        toolbar.setPadding(left, top, right, 0);
        body.setPadding(left, 0, right, 0);
    }

    void show(List<File> files, List<File> recent, boolean importing, String note) {
        this.files = files;
        this.recent = recent;
        this.importing = importing;
        add.setText(importing ? "Importing…" : "＋  Import ZIP");
        add.setEnabled(!importing);
        notice.setText(note);
        notice.setVisibility(note != null ? VISIBLE : GONE);
        refreshRows();
    }

    void showSkin(PadSkin skin) { preview.setSkin(skin); }

    private LayoutParams centeredWidth() {
        LayoutParams p = new LayoutParams(-1, -2);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        return p;
    }

    private void addTab(LinearLayout bar, int index, String name, int icon) {
        LinearLayout tab = ui.column();
        tab.setBackground(ui.tappable(Color.TRANSPARENT, Color.TRANSPARENT, 10, true));
        tab.setFocusable(true);
        if (Build.VERSION.SDK_INT >= 28) tab.setScreenReaderFocusable(true);
        tab.setOnClickListener(v -> {
            hideSearchKeyboard();
            recentTab = index == 1;
            updateTabs();
            refreshRows();
            list.setSelection(0);
        });
        LinearLayout inner = ui.row();
        inner.setGravity(Gravity.CENTER);
        inner.setMinimumHeight(ui.dp(43));
        inner.setPadding(ui.dp(3), ui.dp(7), ui.dp(3), ui.dp(5));
        ImageView image = ui.art(icon);
        image.setColorFilter(0xffd8ecff);
        inner.addView(image, new LayoutParams(ui.dp(19), ui.dp(19)));
        LayoutParams labelSpace = new LayoutParams(-2, -2);
        labelSpace.setMarginStart(ui.dp(7));
        inner.addView(ui.text(name, 14, WHITE, true), labelSpace);
        tab.addView(inner, new LayoutParams(-1, -2));
        View line = new View(getContext());
        line.setBackgroundColor(0xff9adaff);
        tab.addView(line, new LayoutParams(-1, ui.dp(3)));
        bar.addView(tab, new LayoutParams(0, -2, 1));
        tabs[index] = tab;
        indicators[index] = line;
    }

    private void updateTabs() {
        int selected = recentTab ? 1 : 0;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setSelected(i == selected);
            indicators[i].setVisibility(i == selected ? VISIBLE : INVISIBLE);
            int fill = i == selected ? 0x1bffffff : Color.TRANSPARENT;
            tabs[i].setBackground(ui.tappable(fill, fill, 10, true));
        }
    }

    private void refreshRows() {
        List<File> all = recentTab ? recent : files;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<File> shown = new ArrayList<>(all.size());
        for (File file : all) if (file.getName().contains(query)) shown.add(file);
        rows.shown = shown;
        rows.emptyTitle = all.isEmpty() ? (recentTab ? "Nothing played yet" : "No ROMs imported") : "No matching ROMs";
        rows.emptyDetail = all.isEmpty() ? (recentTab ? "Games you launch will appear here." : "Use Import ZIP and keep each ROM’s original set name.")
                : "Try another part of the filename.";
        section.setText(recentTab ? "Recently played" : "Imported ROMs");
        count.setText(shown.size() + (shown.size() == 1 ? " ZIP" : " ZIPs"));
        search.setVisibility(all.isEmpty() ? GONE : VISIBLE);
        rows.notifyDataSetChanged();
    }

    void hideSearchKeyboard() {
        InputMethodManager keyboard = getContext().getSystemService(InputMethodManager.class);
        if (keyboard != null && getWindowToken() != null) keyboard.hideSoftInputFromWindow(getWindowToken(), 0);
        search.clearFocus();
        requestFocus();
    }

    private View emptyRow(String title, String detail) {
        LinearLayout panel = ui.column();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(ui.dp(17), ui.dp(20), ui.dp(17), ui.dp(20));
        panel.setBackground(ui.surface(WHITE, BORDER, 16));
        ImageView image = ui.art(R.drawable.legacy_ui_games);
        image.setBackground(ui.surface(LIGHT_BLUE, BORDER, 14));
        image.setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9));
        panel.addView(image, new LayoutParams(ui.dp(52), ui.dp(52)));
        ui.space(panel, 10);
        TextView head = ui.text(title, 17, INK, true);
        head.setGravity(Gravity.CENTER);
        panel.addView(head);
        ui.space(panel, 5);
        TextView body = ui.text(detail, 14, MUTED, false);
        body.setGravity(Gravity.CENTER);
        panel.addView(body);
        panel.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        return panel;
    }

    private final class ArchiveRows extends BaseAdapter {
        List<File> shown = Collections.emptyList();
        String emptyTitle = "No ROMs imported", emptyDetail = "Use Import ZIP to get started.";
        @Override public int getCount() { return Math.max(1, shown.size()); }
        @Override public Object getItem(int i) { return shown.isEmpty() ? null : shown.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return shown.isEmpty() ? 1 : 0; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int i) { return !shown.isEmpty() && !importing; }
        @Override public View getView(int i, View old, ViewGroup parent) {
            if (shown.isEmpty()) return emptyRow(emptyTitle, emptyDetail);
            ArchiveRow row = old instanceof ArchiveRow ? (ArchiveRow) old : new ArchiveRow();
            row.bind(shown.get(i));
            return row;
        }
    }

    private final class ArchiveRow extends LinearLayout {
        final LinearLayout play;
        final ImageButton options;
        final TextView name, info;

        ArchiveRow() {
            super(LibraryScreen.this.getContext());
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(ui.dp(4), ui.dp(4), ui.dp(5), ui.dp(4));
            setBackground(ui.surface(WHITE, BORDER, 14));
            setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            play = ui.row();
            play.setBackground(ui.tappable(WHITE, WHITE, 11, false));
            play.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
            play.setMinimumHeight(ui.dp(65));
            play.setFocusable(true);
            if (Build.VERSION.SDK_INT >= 28) play.setScreenReaderFocusable(true);
            addView(play, new LayoutParams(0, -2, 1));
            ImageView zip = ui.art(R.drawable.legacy_ui_file_icon);
            zip.setPadding(ui.dp(7), ui.dp(7), ui.dp(7), ui.dp(7));
            zip.setBackground(ui.surface(LIGHT_BLUE, BORDER, 10));
            play.addView(zip, new LayoutParams(ui.dp(43), ui.dp(43)));
            LinearLayout text = ui.column();
            name = ui.text("", 16, INK, true);
            singleLine(name);
            info = ui.text("", 12, MUTED, false);
            singleLine(info);
            text.addView(name);
            ui.space(text, 4);
            text.addView(info);
            LayoutParams center = new LayoutParams(0, -2, 1);
            center.setMarginStart(ui.dp(10));
            center.setMarginEnd(ui.dp(8));
            play.addView(text, center);
            ImageView arrow = ui.art(R.drawable.legacy_ui_start);
            arrow.setPadding(ui.dp(7), ui.dp(7), ui.dp(7), ui.dp(7));
            arrow.setBackground(ui.surface(BLUE, BLUE, 11));
            play.addView(arrow, new LayoutParams(ui.dp(36), ui.dp(36)));
            options = ui.icon(R.drawable.legacy_ui_menu_gameinfo, "ROM options", Color.TRANSPARENT, MUTED, 11, 13);
            addView(options, new LayoutParams(ui.dp(48), ui.dp(48)));
        }

        void bind(File file) {
            String filename = file.getName();
            name.setText(filename.substring(0, filename.length() - 4));
            info.setText("ROM ZIP · " + Formatter.formatShortFileSize(getContext(), file.length()));
            play.setContentDescription("Play " + filename);
            play.setOnClickListener(v -> { hideSearchKeyboard(); actions.play(file); });
            play.setEnabled(!importing);
            options.setContentDescription("Options for " + filename);
            options.setOnClickListener(v -> actions.options(v, file));
            options.setEnabled(!importing);
        }
    }
}
