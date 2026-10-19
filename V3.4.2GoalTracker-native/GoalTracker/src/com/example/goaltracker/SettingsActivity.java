package com.example.goaltracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

/** Settings screen. Currently: Appearance (System default / Light / Dark). */
public class SettingsActivity extends Activity {

    private Pal p;
    private int appliedMode;
    private Store store;

    private static final int REQ_FOLDER = 101, REQ_EXPORT = 102, REQ_IMPORT = 103;

    @Override
    protected void onCreate(Bundle b) {
        setTheme(Pal.themeRes(this));
        super.onCreate(b);
        appliedMode = Pal.mode(this);
        p = Pal.of(this);
        store = Store.get(this);
        Pal.applyBars(this, p);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(p.bg);
        FrameLayout holder = new FrameLayout(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(24));
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(640));
        holder.addView(content, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL));
        scroll.addView(holder, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        // Header: back + title
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(p.ink);
        back.setContentDescription("Back");
        back.setScaleType(ImageView.ScaleType.CENTER);
        TypedValue tvv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tvv, true);
        back.setBackgroundResource(tvv.resourceId);
        back.setOnClickListener(v -> finish());
        head.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Settings", 26, p.ink, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = dp(6);
        head.addView(title, tl);
        content.addView(head);

        // Appearance section
        TextView sec = text("Appearance", 14, p.mute, true);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = dp(20);
        sl.bottomMargin = dp(8);
        sl.leftMargin = dp(6);
        content.addView(sec, sl);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.card);
        bg.setCornerRadius(dp(16));
        bg.setStroke(Math.max(1, dp(1)), p.line);
        card.setBackground(bg);
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));

        addOption(card, Pal.MODE_LIGHT, "Light", "White background, dark text", false);
        addOption(card, Pal.MODE_DARK, "Dark", "Black background, light text", true);
        addOption(card, Pal.MODE_SYSTEM, "System default", "Follow the device's setting", true);

        // Data & storage
        TextView sec2 = text("Data & storage", 14, p.mute, true);
        LinearLayout.LayoutParams sl2 = new LinearLayout.LayoutParams(-2, -2);
        sl2.topMargin = dp(24);
        sl2.bottomMargin = dp(8);
        sl2.leftMargin = dp(6);
        content.addView(sec2, sl2);

        LinearLayout card2 = new LinearLayout(this);
        card2.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg2 = new GradientDrawable();
        bg2.setColor(p.card);
        bg2.setCornerRadius(dp(16));
        bg2.setStroke(Math.max(1, dp(1)), p.line);
        card2.setBackground(bg2);
        content.addView(card2, new LinearLayout.LayoutParams(-1, -2));

        addAction(card2, "Storage folder", LocalFolder.status(this), false, this::chooseFolder);
        if (LocalFolder.isSet(this)) {
            addAction(card2, "Sync now", "Write the latest data to the folder", true, this::syncNow);
            addAction(card2, "Stop syncing to folder", "Files already in the folder are kept", true, this::stopSync);
        }
        addAction(card2, "Export data", "Save a backup file to a place you choose", true, this::exportData);
        addAction(card2, "Import data", "Restore from a backup file", true, this::importData);

        TextView note = text("Your data is always saved inside the app too, with automatic backups. "
                + "The storage folder, if set, is updated after every change.", 12, p.mute, false);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.topMargin = dp(8);
        nl.leftMargin = dp(6);
        nl.rightMargin = dp(6);
        content.addView(note, nl);
    }

    // ───────────── Data & storage ─────────────

    private void addAction(LinearLayout card, String label, String sub, boolean divider, Runnable r) {
        if (divider) {
            View d = new View(this);
            d.setBackgroundColor(p.line);
            card.addView(d, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        TypedValue tvv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tvv, true)) {
            row.setBackgroundResource(tvv.resourceId);
        }
        row.addView(text(label, 16, p.ink, true));
        row.addView(text(sub, 13, p.mute, false));
        row.setOnClickListener(v -> r.run());
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try { startActivityForResult(i, REQ_FOLDER); }
        catch (Exception e) { toast("No folder picker is available on this device"); }
    }

    private void syncNow() {
        String json = store.snapshotJson();
        if (json == null) { toast("Could not read the data"); return; }
        LocalFolder.queue(this, json);
        toast("Syncing…");
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing()) recreate();
        }, 1200);
    }

    private void stopSync() {
        new AlertDialog.Builder(this)
                .setMessage("Stop syncing to the storage folder? Files already in the folder are kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Stop syncing", (d, w) -> { LocalFolder.clear(this); recreate(); })
                .show();
    }

    private void exportData() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "goal-tracker-backup-" + java.time.LocalDate.now() + ".json");
        try { startActivityForResult(i, REQ_EXPORT); }
        catch (Exception e) { toast("No file picker is available on this device"); }
    }

    private void importData() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        try { startActivityForResult(i, REQ_IMPORT); }
        catch (Exception e) { toast("No file picker is available on this device"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_FOLDER) onFolderPicked(uri);
        else if (req == REQ_EXPORT) doExport(uri);
        else if (req == REQ_IMPORT) doImport(uri);
    }

    // Export: write, then read the file back and check it before reporting success.
    private void doExport(Uri uri) {
        final String json = store.snapshotJson();
        if (json == null) { toast("Could not read the data to export"); return; }
        final int count = store.goals.size();
        new Thread(() -> {
            String error = null;
            try {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new java.io.IOException("Cannot open the file");
                    out.write(json.getBytes("UTF-8"));
                    out.flush();
                }
                String back;
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    back = in == null ? null : LocalFolder.readAll(in);
                }
                Store.Parsed chk = Store.parseDocument(back);
                if (chk == null || chk.goals.size() != count) error = "The exported file could not be verified";
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }
            final String err = error;
            runOnUiThread(() -> toast(err == null ? "Exported " + count + (count == 1 ? " goal" : " goals")
                    : "Export failed: " + err));
        }).start();
    }

    // Import: read and validate first; nothing changes until the user confirms.
    private void doImport(Uri uri) {
        new Thread(() -> {
            Store.Parsed parsed = null;
            String error = null;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("Cannot open the file");
                parsed = Store.parseDocument(LocalFolder.readAll(in));
                if (parsed == null) error = "That file is not a Goal Tracker backup";
                else if (parsed.goals.isEmpty()) error = "That backup contains no goals";
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }
            final Store.Parsed fp = parsed;
            final String err = error;
            runOnUiThread(() -> { if (err != null) toast("Import failed: " + err); else confirmImport(fp); });
        }).start();
    }

    private void confirmImport(Store.Parsed in) {
        int tasks = 0;
        for (Store.Goal g : in.goals) tasks += g.allTasks().size();
        String what = in.goals.size() + (in.goals.size() == 1 ? " goal" : " goals") + " and "
                + tasks + (tasks == 1 ? " task" : " tasks");
        new AlertDialog.Builder(this)
                .setTitle("Import data")
                .setMessage("The file contains " + what + ".\n\nReplace: use the file's data instead of the current data ("
                        + store.goals.size() + " goals now).\nMerge: add only goals that are not already in the app.\n\n"
                        + "A safety copy of your current data is saved first.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Merge", (d, w) -> finishImport(in, true))
                .setPositiveButton("Replace", (d, w) -> finishImport(in, false))
                .show();
    }

    private void finishImport(Store.Parsed in, boolean merge) {
        try {
            int[] r = store.importDocument(in, merge);
            toast(merge ? "Added " + r[0] + (r[0] == 1 ? " goal" : " goals")
                    + (r[1] > 0 ? ", skipped " + r[1] + " already present" : "")
                    : "Imported " + r[0] + (r[0] == 1 ? " goal" : " goals"));
        } catch (RuntimeException e) {
            toast("Import failed: " + e.getMessage());
        }
    }

    // Folder: inspect first, so existing data in the folder is never overwritten without asking.
    private void onFolderPicked(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            toast("Could not get access to that folder");
            return;
        }
        new Thread(() -> {
            LocalFolder.Found f = null;
            String error = null;
            try { f = LocalFolder.inspect(this, uri); }
            catch (Exception e) { error = String.valueOf(e.getMessage()); }
            final LocalFolder.Found ff = f;
            final String err = error;
            runOnUiThread(() -> {
                if (err != null || ff == null) {
                    toast("Could not use that folder: " + err);
                    if (!uri.toString().equals(prefsTree())) LocalFolder.release(this, uri);
                } else {
                    afterInspect(uri, ff);
                }
            });
        }).start();
    }

    private String prefsTree() {
        return getSharedPreferences("goal_tracker_settings", MODE_PRIVATE).getString("folder_tree_uri", "");
    }

    private void afterInspect(Uri uri, LocalFolder.Found f) {
        boolean hasContent = !f.existing.trim().isEmpty();
        Store.Parsed existing = hasContent ? Store.parseDocument(f.existing) : null;
        if (!hasContent || (existing != null && existing.goals.isEmpty())) {
            linkFolder(uri, f);
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Data already in this folder");
        if (existing == null) {
            b.setMessage("The file " + LocalFolder.FILE_NAME + " in this folder is not readable Goal Tracker data. "
                    + "Overwrite it with the data currently in the app?")
                    .setPositiveButton("Overwrite", (d, w) -> linkFolder(uri, f));
        } else {
            b.setMessage("This folder already contains Goal Tracker data (" + existing.goals.size()
                    + (existing.goals.size() == 1 ? " goal" : " goals") + ").\n\nUse it, or overwrite it with the data "
                    + "currently in the app? (A safety copy of the current data is saved if you use the folder's data.)")
                    .setPositiveButton("Use folder data", (d, w) -> {
                        store.importDocument(existing, false);
                        linkFolder(uri, f);
                    })
                    .setNeutralButton("Overwrite", (d, w) -> linkFolder(uri, f));
        }
        b.setNegativeButton("Cancel", (d, w) -> {
            if (!uri.toString().equals(prefsTree())) LocalFolder.release(this, uri);
        }).setCancelable(false).show();
    }

    private void linkFolder(Uri uri, LocalFolder.Found f) {
        LocalFolder.commit(this, uri, f);
        String json = store.snapshotJson();
        if (json != null) LocalFolder.queue(this, json);
        toast("Storage folder set: " + f.label);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing()) recreate();
        }, 800);
    }

    private void addOption(LinearLayout card, int mode, String label, String sub, boolean divider) {
        if (divider) {
            View d = new View(this);
            d.setBackgroundColor(p.line);
            card.addView(d, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        TypedValue tvv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tvv, true)) {
            row.setBackgroundResource(tvv.resourceId);
        }

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(label, 16, p.ink, true));
        labels.addView(text(sub, 13, p.mute, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        RadioButton rb = new RadioButton(this);
        rb.setChecked(appliedMode == mode);
        rb.setClickable(false);
        rb.setFocusable(false);
        rb.setButtonTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{p.acc, p.mute}));
        row.addView(rb, new LinearLayout.LayoutParams(-2, -2));

        row.setOnClickListener(v -> choose(mode));
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Saves the choice, refreshes the widget so it matches, and re-draws this screen. */
    private void choose(int mode) {
        if (mode == appliedMode) return;
        Pal.setMode(this, mode);
        GoalWidgetProvider.refresh(this);
        recreate();
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(float v) { return Util.dp(this, v); }
}
