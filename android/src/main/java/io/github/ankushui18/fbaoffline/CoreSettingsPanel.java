package io.github.ankushui18.fbaoffline;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.github.ankushui18.fbaoffline.ArcadeUi.*;

/** Settings supplied at driver boot, separate from the live DIP and cheat panels. */
final class CoreSettingsPanel {
    private static final List<String> ORDER = Arrays.asList("Machine", "Video", "Audio", "Input");
    private CoreSettingsPanel() {}

    static void show(Activity activity, String[][] data, String saved, boolean hiscoreData, Consumer<String> apply) {
        List<Row> rows = new ArrayList<>();
        if (data != null) for (String[] row : data) rows.add(new Row(row));
        rows.sort(Comparator.comparingInt(row -> ORDER.indexOf(row.section)));
        if (rows.isEmpty()) {
            new AlertDialog.Builder(activity).setTitle("FBNeo settings")
                    .setMessage("This driver doesn't expose supported boot settings.").setPositiveButton("Close", null).show();
            return;
        }
        Map<String, String> pending = CoreProfile.decode(saved);
        ArcadeUi ui = new ArcadeUi(activity);
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(16), ui.dp(7), ui.dp(16), ui.dp(4));
        body.addView(ui.text("For this game. Exit and start normally to apply. Save states require the same settings used when saved.", 13, MUTED, false));
        ui.space(body, 7);
        ListView list = new ListView(activity);
        int height = Math.min(ui.dp(380), Math.max(ui.dp(80), activity.getResources().getDisplayMetrics().heightPixels / 2 - ui.dp(56)));
        body.addView(list, new LinearLayout.LayoutParams(-1, height));
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Row getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View old, ViewGroup parent) {
                Row row = getItem(i);
                LinearLayout cell = ui.column();
                cell.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
                if (i == 0 || !row.section.equals(getItem(i - 1).section)) {
                    cell.addView(ui.text(row.section, 12, MUTED, true));
                    ui.space(cell, 4);
                }
                cell.addView(ui.text(row.title, 14, INK, true));
                int choice = row.choice(pending);
                String suffix = choice == row.active ? (choice == row.baseline ? " · Default" : "") : " · Next launch";
                cell.addView(ui.text(row.labels[choice] + suffix, 12, BLUE, false));
                return cell;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, v, i, id) -> select(activity, rows.get(i), pending, hiscoreData, adapter::notifyDataSetChanged));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("FBNeo settings").setView(body)
                .setPositiveButton("Save", (d, w) -> {
                    String profile = CoreProfile.encode(pending);
                    if (!profile.equals(saved)) apply.accept(profile);
                }).setNeutralButton("Defaults", null).setNegativeButton("Cancel", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { pending.clear(); adapter.notifyDataSetChanged(); });
    }

    private static void select(Activity activity, Row row, Map<String, String> pending, boolean hiscoreData, Runnable changed) {
        if (row.key.equals(CoreProfile.HISCORES) && !hiscoreData) {
            new AlertDialog.Builder(activity).setTitle(row.title)
                    .setMessage("In the library, open Settings → High score data and import the verified hiscore.dat before enabling this setting.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] labels = row.labels.clone();
        labels[row.baseline] += " · Default";
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle(row.title)
                .setSingleChoiceItems(labels, row.choice(pending), (d, i) -> {
                    if (i == row.baseline) pending.remove(row.key); else pending.put(row.key, row.values[i]);
                    changed.run();
                    d.dismiss();
                }).setNegativeButton("Cancel", null);
        if (!row.info.isEmpty()) dialog.setNeutralButton("Details", (d, w) -> new AlertDialog.Builder(activity)
                .setTitle(row.title).setMessage(row.info)
                .setPositiveButton("Back", (info, which) -> select(activity, row, pending, hiscoreData, changed)).show());
        dialog.show();
    }

    private static final class Row {
        final String key, section, title, info;
        final String[] labels, values;
        final int active, baseline;

        Row(String[] data) {
            key = data[0]; section = data[1]; info = data[3];
            switch (key) {
                case CoreProfile.HISCORES: title = "Save high scores"; break;
                case "fbneo-fixed-frameskip": title = "Core frameskip"; break;
                case "fbneo-samplerate": title = "Audio sample rate"; break;
                case "fbneo-socd": title = "Opposing directions (SOCD)"; break;
                default: title = data[2];
            }
            active = Integer.parseInt(data[4]); baseline = Integer.parseInt(data[5]);
            int split = 6 + (data.length - 6) / 2;
            labels = Arrays.copyOfRange(data, 6, split);
            values = Arrays.copyOfRange(data, split, data.length);
        }

        int choice(Map<String, String> pending) {
            String next = pending.get(key);
            for (int i = 0; next != null && i < values.length; i++) if (values[i].equals(next)) return i;
            return baseline;
        }
    }
}
