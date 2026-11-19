package io.github.ankushui18.fbaoffline;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static io.github.ankushui18.fbaoffline.ArcadeUi.*;

final class GamePanels {
    private GamePanels() {}

    static void options(Activity activity, String title, String[][] data, boolean cheats,
                        Consumer<Map<String, Integer>> apply) {
        List<String[]> options = new ArrayList<>();
        if (data != null) for (String[] row : data) {
            if (row.length <= 6) continue;
            if (cheats ? row[1].equals("cheat") : row[1].equals("dipswitch") || row[1].equals("machine")) options.add(row);
        }
        if (options.isEmpty()) {
            new AlertDialog.Builder(activity).setTitle(title).setMessage(cheats
                    ? "No compatible cheats loaded for this set. To import an INI or cheat ZIP, use Cheat files in the game’s library row menu."
                    : "This game doesn't expose DIP switch choices to the frontend.")
                    .setPositiveButton("Close", null).show();
            return;
        }
        ArcadeUi ui = new ArcadeUi(activity);
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(3));
        TextView summary = ui.text(cheats ? "Choose an option, then Apply and Resume. Restart clears active cheats."
                : "Cabinet settings provided by FBNeo. Some changes may need Restart.", 13, MUTED, false);
        body.addView(summary);
        ui.space(body, 7);
        EditText search = new EditText(activity);
        search.setHint(cheats ? "Find a cheat" : "Find a switch");
        search.setTextSize(14);
        search.setSingleLine(true);
        body.addView(search, new LinearLayout.LayoutParams(-1, -2));
        ListView list = new ListView(activity);
        list.setDividerHeight(ui.dp(1));
        list.setItemsCanFocus(false);
        int height = Math.min(ui.dp(342), Math.max(ui.dp(80), activity.getResources().getDisplayMetrics().heightPixels / 2 - ui.dp(58)));
        body.addView(list, new LinearLayout.LayoutParams(-1, height));
        Map<String, Integer> pending = new LinkedHashMap<>();
        List<String[]> visible = new ArrayList<>(options);
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public String[] getItem(int i) { return visible.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View old, ViewGroup parent) {
                String[] row = getItem(i);
                LinearLayout cell = ui.column();
                cell.setGravity(Gravity.CENTER_VERTICAL);
                cell.setPadding(ui.dp(7), ui.dp(8), ui.dp(7), ui.dp(8));
                int selected = Math.min(row.length - 7, pending.getOrDefault(row[0], Integer.parseInt(row[4])));
                cell.addView(ui.text(label(row[2]), 14, INK, true));
                ui.space(cell, 4);
                cell.addView(ui.text(row[6 + selected], 12, BLUE, false));
                return cell;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> {
            String[] row = visible.get(i);
            int selected = pending.getOrDefault(row[0], Integer.parseInt(row[4]));
            new AlertDialog.Builder(activity).setTitle(label(row[2])).setSingleChoiceItems(Arrays.copyOfRange(row, 6, row.length), selected, (d, at) -> {
                pending.put(row[0], at); adapter.notifyDataSetChanged(); d.dismiss();
            }).setNegativeButton("Cancel", null).show();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int c, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                visible.clear();
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                for (String[] row : options) if (label(row[2]).toLowerCase(Locale.ROOT).contains(query)) visible.add(row);
                adapter.notifyDataSetChanged();
            }
        });
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title).setView(body)
                .setPositiveButton("Apply", (d, w) -> { if (!pending.isEmpty()) apply.accept(pending); })
                .setNegativeButton("Cancel", null).setNeutralButton("Defaults", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            for (String[] row : options) pending.put(row[0], Integer.parseInt(row[5]));
            adapter.notifyDataSetChanged();
        });
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private static String label(String raw) { return raw.replaceFirst("^\\[[^]]+\\]\\s*", "").trim(); }

    static void slots(Activity activity, StateStore state, boolean saving, IntConsumer chosen) {
        slots(activity, saving ? "Save state" : "Load state", state, saving, chosen);
    }

    static ImageView preview(Activity activity, File file) {
        ImageView image = new ArcadeUi(activity).art(R.drawable.legacy_ui_no_screenshot);
        if (file.isFile()) {
            BitmapFactory.Options scale = new BitmapFactory.Options();
            scale.inSampleSize = 4;
            Bitmap thumb = BitmapFactory.decodeFile(file.getAbsolutePath(), scale);
            if (thumb != null) image.setImageBitmap(thumb);
        }
        return image;
    }

    static void slots(Activity activity, String title, StateStore state, boolean saving, IntConsumer chosen) {
        ArcadeUi ui = new ArcadeUi(activity);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(7));
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title)
                .setView(scroll).setNegativeButton("Cancel", null).create();
        for (int i = 1; i <= StateStore.SLOTS; i++) {
            final int slot = i;
            File file = state.slot(i);
            boolean exists = file.isFile();
            LinearLayout cell = ui.row();
            cell.setMinimumHeight(ui.dp(62));
            cell.setPadding(ui.dp(6), ui.dp(4), ui.dp(9), ui.dp(4));
            cell.setBackground(ui.tappable(exists ? LIGHT_BLUE : WHITE, BORDER, 9, false));
            ImageView image = preview(activity, state.preview(i));
            cell.addView(image, new LinearLayout.LayoutParams(ui.dp(66), ui.dp(47)));
            LinearLayout labels = ui.column();
            labels.addView(ui.text(state.label(i), 15, INK, true));
            ui.space(labels, 4);
            labels.addView(ui.text(exists ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(file.lastModified()))
                    : "Empty", 12, MUTED, false));
            LinearLayout.LayoutParams text = new LinearLayout.LayoutParams(0, -2, 1);
            text.setMarginStart(ui.dp(11));
            cell.addView(labels, text);
            if (saving || exists) {
                cell.setFocusable(true);
                cell.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (!saving || !exists) chosen.accept(slot);
                    else new AlertDialog.Builder(activity).setTitle("Replace slot " + slot + "?")
                            .setMessage("The previous state in this slot will be overwritten.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Replace", (d, w) -> chosen.accept(slot)).show();
                });
            }
            body.addView(cell, new LinearLayout.LayoutParams(-1, -2));
            ui.space(body, 6);
        }
        dialog.show();
    }
}
