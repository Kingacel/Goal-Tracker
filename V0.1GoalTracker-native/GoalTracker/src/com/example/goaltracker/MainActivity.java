package com.example.goaltracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.goaltracker.Store.Goal;
import com.example.goaltracker.Store.Task;
import com.example.goaltracker.Store.TaskList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class MainActivity extends Activity {

    private Store store;
    private Pal p;
    private LinearLayout content;
    private ScrollView scroll;
    private int pageWidth;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable clockUpdater;          // refreshes the live countdown
    private TextView activeTabLabel;        // live-updated while typing the goal title

    private final Runnable renderer = this::render;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (clockUpdater != null) clockUpdater.run();
            handler.postDelayed(this, 1000);
        }
    };

    // ───────────────────────── Lifecycle ─────────────────────────

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Pal.of(this);
        store = Store.get(this);

        Window w = getWindow();
        w.setStatusBarColor(p.bg);
        w.setNavigationBarColor(p.bg);
        if (!p.dark) {
            w.getDecorView().setSystemUiVisibility(w.getDecorView().getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        pageWidth = Math.min(getResources().getDisplayMetrics().widthPixels, dp(900));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(p.bg);
        FrameLayout holder = new FrameLayout(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(24));
        holder.addView(content, new FrameLayout.LayoutParams(pageWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        scroll.addView(holder, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        store.listener = renderer;
        render();
    }

    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(ticker); handler.post(ticker); }

    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (store.listener == renderer) store.listener = null;
    }

    // ───────────────────────── Small UI helpers ─────────────────────────

    private int dp(float v) { return Util.dp(this, v); }

    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    private LinearLayout.LayoutParams lpm(int w, int h, float l, float t, float r, float bt) {
        LinearLayout.LayoutParams x = new LinearLayout.LayoutParams(w, h);
        x.setMargins(dp(l), dp(t), dp(r), dp(bt));
        return x;
    }

    private LinearLayout vbox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }

    private LinearLayout hbox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }

    private TextView tv(CharSequence s, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (weight >= 700) t.setTypeface(Typeface.DEFAULT_BOLD);
        else if (weight >= 500) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private GradientDrawable box(int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private Drawable ripple(Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33808080), content, box(Color.WHITE, radiusDp, 0));
    }

    private TextView button(String label, int fill, int stroke, int textColor, float radiusDp) {
        TextView t = tv(label, 14, textColor, 600);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(40));
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setBackground(ripple(box(fill, radiusDp, stroke), radiusDp));
        return t;
    }

    private ImageButton iconBtn(int res, int color, int sizeDp, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(color);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = dp((sizeDp - 18) / 2f);
        b.setPadding(pad, pad, pad, pad);
        TypedValue tvv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tvv, true);
        b.setBackground(getResources().getDrawable(tvv.resourceId, getTheme()));
        if (l == null) { b.setEnabled(false); b.setAlpha(0.35f); } else b.setOnClickListener(l);
        b.setLayoutParams(lp(dp(sizeDp), dp(sizeDp)));
        return b;
    }

    /** Text field with a coloured underline when focused (Flutter _InlineField). */
    private EditText inlineField(String initial, String hint, float sp, int weight, int maxLen, Consumer<String> onChange) {
        EditText e = new EditText(this);
        e.setText(initial);
        e.setHint(hint);
        e.setHintTextColor(p.mute);
        e.setTextColor(p.ink);
        e.setTextSize(sp);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        if (weight >= 700) e.setTypeface(Typeface.DEFAULT_BOLD);
        e.setBackground(null);
        e.setPadding(0, dp(6), 0, dp(6));
        e.setOnFocusChangeListener((v, focus) -> e.setBackground(focus ? underline(p.line) : null));
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable s) { onChange.accept(s.toString()); }
        });
        return e;
    }

    private Drawable underline(int color) {
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(color);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{bar});
        ld.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        ld.setLayerHeight(0, dp(2));
        return ld;
    }

    private void pickDate(String current, Consumer<String> cb) {
        LocalDate d = Util.parse(current);
        if (d == null) d = LocalDate.now();
        new DatePickerDialog(this, (view, y, m, day) -> cb.accept(LocalDate.of(y, m + 1, day).toString()),
                d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    private void confirmDelete(String message, Runnable onYes) {
        AlertDialog d = new AlertDialog.Builder(this)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dlg, which) -> onYes.run())
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(p.bad);
    }

    // ───────────────────────── Page ─────────────────────────

    private void render() {
        clockUpdater = null;
        activeTabLabel = null;
        content.removeAllViews();

        Goal g = store.active();
        content.addView(buildTabs(g));

        if (g == null) {
            LinearLayout empty = vbox();
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(8), dp(36), dp(8), dp(24));
            empty.addView(tv("No goals yet", 21, p.ink, 800));
            TextView m = tv("Add your first goal to start tracking a deadline.", 14, p.mute, 400);
            m.setGravity(Gravity.CENTER);
            empty.addView(m);
            content.addView(empty);
            return;
        }

        content.addView(buildHero(g), lpm(-1, -2, 0, 12, 0, 0));

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(22), 0, dp(10));
        TextView title = tv("Task lists", 21, p.ink, 800);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView add = button("+ New list", p.acc, 0, Color.WHITE, 10);
        add.setOnClickListener(v -> store.addList(g));
        head.addView(add);
        content.addView(head);

        if (g.lists.isEmpty()) {
            TextView m = tv("No lists yet. Create one to start adding tasks.", 14, p.mute, 400);
            m.setGravity(Gravity.CENTER);
            m.setPadding(dp(8), dp(24), dp(8), dp(24));
            content.addView(m);
        } else {
            for (TaskList l : g.lists) content.addView(buildListCard(g, l), lpm(-1, -2, 0, 0, 0, 14));
        }
    }

    // ───────────────────────── Goal tabs ─────────────────────────

    private View buildTabs(Goal active) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (Goal g : store.goals) {
            boolean sel = active != null && g.id.equals(active.id);
            TextView chip = tv(g.title.isEmpty() ? "Untitled" : g.title, 14, sel ? Color.WHITE : p.ink, 500);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setMaxWidth(dp(200));
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(36));
            chip.setPadding(dp(16), dp(6), dp(16), dp(6));
            chip.setBackground(ripple(box(sel ? p.acc : p.card, 20, sel ? p.acc : p.line), 20));
            chip.setOnClickListener(v -> store.selectGoal(g.id));
            if (sel) activeTabLabel = chip;
            row.addView(chip, lpm(-2, -2, 0, 0, 8, 0));
        }
        TextView add = tv("+ New goal", 14, p.mute, 500);
        add.setGravity(Gravity.CENTER);
        add.setMinHeight(dp(36));
        add.setPadding(dp(16), dp(6), dp(16), dp(6));
        add.setBackground(ripple(box(p.bg, 20, (p.mute & 0x00FFFFFF) | 0x78000000), 20));
        add.setOnClickListener(v -> store.addGoal());
        row.addView(add);
        hs.addView(row);
        return hs;
    }

    // ───────────────────────── Hero card ─────────────────────────

    private View buildHero(Goal g) {
        List<Task> tasks = g.allTasks();
        int done = 0;
        for (Task t : tasks) if (t.done) done++;
        int pct = g.percent();

        LinearLayout card = vbox();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(box(p.card, 20, p.line));

        EditText title = inlineField(g.title, "Goal or project name", 26, 800, 100, v -> {
            g.title = v;
            if (activeTabLabel != null) activeTabLabel.setText(v.isEmpty() ? "Untitled" : v);
            store.saveQuietly();
        });
        card.addView(title, lp(-1, -2));

        FlowLayout row = new FlowLayout(this, dp(10), dp(4));
        row.addView(tv("Deadline", 14, p.mute, 400));
        TextView dateBtn = button(g.deadline.isEmpty() ? "Set date" : Util.fmtShort(g.deadline), 0, p.line, p.ink, 10);
        dateBtn.setCompoundDrawablesWithIntrinsicBounds(tinted(R.drawable.ic_event, p.ink, 18), null, null, null);
        dateBtn.setCompoundDrawablePadding(dp(8));
        dateBtn.setOnClickListener(v -> pickDate(g.deadline, s -> { g.deadline = s; store.changed(); }));
        row.addView(dateBtn);
        TextView del = button("Delete goal", 0, 0, p.bad, 10);
        del.setOnClickListener(v -> confirmDelete("Delete this goal and all its lists?", () -> store.deleteGoal(g)));
        row.addView(del);
        card.addView(row, lpm(-1, -2, 0, 8, 0, 0));

        card.addView(buildStats(g), lpm(-1, -2, 0, 12, 0, 0));

        LinearLayout pr = hbox();
        pr.addView(tv("Progress", 14, p.ink, 600), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView ptxt = tv(pct + "% · " + done + "/" + tasks.size() + " tasks done", 14, p.ink, 600);
        ptxt.setGravity(Gravity.END);
        pr.addView(ptxt, lp(-2, -2));
        card.addView(pr, lpm(-1, -2, 0, 18, 0, 6));
        card.addView(new BarView(this, p.line, p.acc, pct / 100f), lp(-1, dp(14)));
        return card;
    }

    private Drawable tinted(int res, int color, int sizeDp) {
        Drawable d = getResources().getDrawable(res, getTheme()).mutate();
        d.setTint(color);
        d.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        return d;
    }

    private LinearLayout statCell(String label, View child) {
        LinearLayout c = vbox();
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(box(p.soft, 14, 0));
        c.addView(tv(label, 13, p.mute, 400));
        c.addView(child, lpm(-1, -2, 0, 2, 0, 0));
        return c;
    }

    private View buildStats(Goal g) {
        final TextView big = tv("", 30, p.acc, 800);
        big.setLineSpacing(0, 1.0f);
        final LinearLayout clockBox = vbox();

        Runnable fill = () -> fillClock(g, big, clockBox);
        fill.run();
        clockUpdater = fill;

        TextView deadlineTv = tv(Util.fmtLong(g.deadline), 16, p.ink, 700);
        TextView totalTv = tv(g.deadline.isEmpty() ? "–" : Util.totalDays(g.created, g.deadline) + " days", 16, p.ink, 700);

        LinearLayout daysCell = statCell("Days remaining", big);
        LinearLayout deadlineCell = statCell("Deadline date", deadlineTv);
        LinearLayout totalCell = statCell("Total days (from " + Util.fmtShort(g.created) + ")", totalTv);
        LinearLayout clockCell = statCell("Time left", clockBox);

        final int gap = dp(12);
        boolean wide = (pageWidth - dp(14 * 2 + 18 * 2 + 2)) >= dp(640);
        LinearLayout col = vbox();
        if (wide) {
            LinearLayout r = hbox();
            for (View c : new View[]{daysCell, deadlineCell, totalCell, clockCell}) {
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -2, 1f);
                if (c != clockCell) l.rightMargin = gap;
                r.addView(c, l);
            }
            col.addView(r, lp(-1, -2));
        } else {
            LinearLayout r1 = hbox();
            LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1f);
            a.rightMargin = gap;
            r1.addView(daysCell, a);
            r1.addView(deadlineCell, new LinearLayout.LayoutParams(0, -2, 1f));
            col.addView(r1, lp(-1, -2));

            LinearLayout r2 = hbox();
            LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, -2, 1f);
            c1.rightMargin = gap;
            r2.addView(totalCell, c1);
            r2.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            col.addView(r2, lpm(-1, -2, 0, 12, 0, 0));

            col.addView(clockCell, lpm(-1, -2, 0, 12, 0, 0));
        }
        return col;
    }

    /** Fills the big "N days left" text and the d/h/m/s cells. Called every second. */
    private void fillClock(Goal g, TextView big, LinearLayout box) {
        box.removeAllViews();
        if (g.deadline.isEmpty()) {
            big.setText("–");
            big.setTextSize(30);
            big.setTextColor(p.acc);
            box.addView(tv("Set a deadline", 14, p.mute, 400));
            return;
        }
        long ms = Util.endOfDayMillis(g.deadline) - System.currentTimeMillis();
        if (ms <= 0) {
            big.setText("Passed");
            big.setTextSize(30);
            big.setTextColor(p.bad);
            box.addView(tv("Deadline reached", 13, p.bad, 400));
            return;
        }
        long d = ms / 86400000L, h = (ms / 3600000L) % 24, m = (ms / 60000L) % 60, s = (ms / 1000L) % 60;
        String text = d + (d == 1 ? " day" : " days") + " left";
        big.setText(text);
        big.setTextSize(text.length() > 8 ? 22 : 30);
        big.setTextColor(p.acc);

        LinearLayout row = hbox();
        long[] vals = {d, h, m, s};
        String[] units = {"d", "h", "m", "s"};
        for (int i = 0; i < 4; i++) {
            LinearLayout cell = vbox();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(6), 0, dp(6));
            cell.setBackground(box(p.card, 10, 0));
            TextView v = tv(String.format("%02d", vals[i]), 19, p.ink, 700);
            v.setFontFeatureSettings("tnum");
            cell.addView(v);
            cell.addView(tv(units[i], 11, p.mute, 400));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -2, 1f);
            l.rightMargin = dp(6);
            row.addView(cell, l);
        }
        box.addView(row, lp(-1, -2));
    }

    // ───────────────────────── Lists & tasks ─────────────────────────

    private View buildListCard(Goal g, TaskList l) {
        int n = l.tasks.size(), doneCount = 0;
        for (Task t : l.tasks) if (t.done) doneCount++;

        LinearLayout card = vbox();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(box(p.card, 16, p.line));

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        EditText name = inlineField(l.name, "List name", 17, 700, 60, v -> { l.name = v; store.saveQuietly(); });
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(tv(doneCount + "/" + n, 13, p.mute, 400), lpm(-2, -2, 8, 0, 6, 0));
        TextView addTask = button("+ Task", 0, p.line, p.ink, 10);
        addTask.setMinHeight(dp(36));
        addTask.setPadding(dp(12), dp(4), dp(12), dp(4));
        addTask.setOnClickListener(v -> openTaskForm(l, null));
        head.addView(addTask);
        head.addView(iconBtn(R.drawable.ic_delete, p.mute, 40,
                v -> confirmDelete("Delete this list and its tasks?", () -> store.deleteList(g, l))));
        card.addView(head, lp(-1, -2));

        if (n == 0) {
            TextView m = tv("No tasks yet. Tap “+ Task” to add one.", 14, p.mute, 400);
            m.setPadding(dp(12), dp(12), dp(12), dp(12));
            card.addView(m);
        } else {
            for (int i = 0; i < n; i++) card.addView(buildTile(l, l.tasks.get(i), i, n));
        }
        return card;
    }

    private View buildTile(TaskList l, Task t, int index, int count) {
        LinearLayout wrap = vbox();
        View divider = new View(this);
        divider.setBackgroundColor(p.line);
        wrap.addView(divider, lp(-1, Math.max(1, dp(1))));

        LinearLayout row = hbox();
        row.setPadding(0, dp(8), 0, dp(8));
        row.setGravity(Gravity.TOP);

        CheckBox cb = new CheckBox(this);
        cb.setChecked(t.done);
        cb.setButtonTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{p.acc, p.mute}));
        cb.setOnCheckedChangeListener((btn, checked) -> store.toggleTask(t, checked));
        row.addView(cb, lp(-2, -2));

        LinearLayout mid = vbox();
        mid.setPadding(0, dp(10), dp(4), 0);
        TextView name = tv(t.name, 16, t.done ? p.mute : p.ink, 600);
        if (t.done) name.setPaintFlags(name.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        mid.addView(name);
        if (!t.desc.isEmpty()) mid.addView(tv(t.desc, 14, p.mute, 400));

        int pr = t.effectiveProgress();
        int[] pill = pillColors(t.priority);
        FlowLayout meta = new FlowLayout(this, dp(10), dp(4));
        TextView pillTv = tv(t.priority, 12.5f, pill[1], 600);
        pillTv.setPadding(dp(9), dp(1), dp(9), dp(1));
        pillTv.setBackground(box(pill[0], 99, 0));
        meta.addView(pillTv);

        List<String> dates = new ArrayList<>();
        if (!t.start.isEmpty()) dates.add(Util.fmtShort(t.start));
        boolean overdue = t.overdue();
        if (!t.due.isEmpty()) dates.add(Util.fmtShort(t.due) + (overdue ? " (overdue)" : ""));
        if (!dates.isEmpty()) meta.addView(tv(TextUtils.join(" → ", dates), 13, overdue ? p.bad : p.mute, 400));

        LinearLayout prog = hbox();
        prog.setGravity(Gravity.CENTER_VERTICAL);
        prog.addView(new BarView(this, p.line, p.acc, pr / 100f), lp(dp(70), dp(6)));
        prog.addView(tv(pr + "%", 13, p.mute, 400), lpm(-2, -2, 6, 0, 0, 0));
        meta.addView(prog);
        mid.addView(meta, lpm(-1, -2, 0, 4, 0, 0));
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout icons = vbox();
        LinearLayout r1 = hbox(), r2 = hbox();
        r1.addView(iconBtn(R.drawable.ic_up, p.mute, 34, index == 0 ? null : v -> store.moveTask(l, index, -1)));
        r1.addView(iconBtn(R.drawable.ic_edit, p.mute, 34, v -> openTaskForm(l, t)));
        r2.addView(iconBtn(R.drawable.ic_down, p.mute, 34, index == count - 1 ? null : v -> store.moveTask(l, index, 1)));
        r2.addView(iconBtn(R.drawable.ic_delete, p.mute, 34, v -> store.deleteTask(l, t)));
        icons.addView(r1);
        icons.addView(r2);
        row.addView(icons, lp(-2, -2));

        wrap.addView(row, lp(-1, -2));
        return wrap;
    }

    private int[] pillColors(String priority) {
        switch (priority) {
            case "high": return new int[]{0xFFFDE2D3, 0xFF9A3412};
            case "low": return new int[]{0xFFD9F2E3, 0xFF166534};
            default: return new int[]{0xFFFDF0C4, 0xFF7A5B00};
        }
    }

    // ───────────────────────── Task form (bottom sheet) ─────────────────────────

    /** Segmented selector (Flutter SegmentedButton). */
    private final class Segmented {
        final LinearLayout row = hbox();
        final TextView[] items;
        int sel;
        final IntConsumer cb;

        Segmented(String[] labels, int sel, IntConsumer cb) {
            this.sel = sel;
            this.cb = cb;
            items = new TextView[labels.length];
            row.setBackground(box(0, 22, p.line));
            row.setPadding(dp(3), dp(3), dp(3), dp(3));
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                TextView t = tv(labels[i], 14, p.ink, 500);
                t.setGravity(Gravity.CENTER);
                t.setMinHeight(dp(38));
                t.setOnClickListener(v -> select(idx, true));
                items[i] = t;
                row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            refresh();
        }

        void select(int i, boolean notify) {
            sel = i;
            refresh();
            if (notify) cb.accept(i);
        }

        void refresh() {
            for (int i = 0; i < items.length; i++) {
                boolean on = i == sel;
                items[i].setBackground(on ? box(p.soft, 19, 0) : null);
                items[i].setTextColor(on ? p.ink : p.mute);
                items[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
        }
    }

    private EditText formField(String hint, String initial, int maxLen, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(p.mute);
        e.setText(initial);
        e.setTextColor(p.ink);
        e.setTextSize(16);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | (lines > 1 ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        e.setMinLines(lines);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setBackground(box(0, 10, p.mute));
        return e;
    }

    private void openTaskForm(TaskList list, Task initial) {
        final boolean editing = initial != null;
        final String[] start = {initial != null ? initial.start : Util.todayIso()};
        final String[] due = {initial != null ? initial.due : ""};
        final String[] priority = {initial != null ? initial.priority : "medium"};
        final boolean[] done = {initial != null && initial.done};
        final int[] progress = {initial != null ? initial.effectiveProgress() : 0};

        Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView sv = new ScrollView(this);
        LinearLayout form = vbox();
        form.setPadding(dp(20), dp(20), dp(20), dp(20));
        sv.addView(form);

        form.addView(tv(editing ? "Edit task" : "Add task", 22, p.ink, 800));

        EditText name = formField("Task name", initial != null ? initial.name : "", 120, 1);
        form.addView(name, lpm(-1, -2, 0, 14, 0, 0));
        EditText desc = formField("Description", initial != null ? initial.desc : "", 400, 2);
        form.addView(desc, lpm(-1, -2, 0, 10, 0, 0));

        // Dates
        LinearLayout dates = hbox();
        TextView startTv = tv("", 15, p.ink, 400);
        TextView dueTv = tv("", 15, p.ink, 400);
        ImageView dueIcon = new ImageView(this);
        Runnable refreshDates = () -> {
            startTv.setText(Util.fmtShort(start[0]));
            if (due[0].isEmpty()) { dueTv.setText("None"); dueTv.setTextColor(p.mute); }
            else { dueTv.setText(Util.fmtShort(due[0])); dueTv.setTextColor(p.ink); }
            dueIcon.setImageResource(due[0].isEmpty() ? R.drawable.ic_event : R.drawable.ic_close);
            dueIcon.setColorFilter(p.mute);
        };
        refreshDates.run();

        LinearLayout startBox = dateBox("Start date", startTv, null);
        startBox.setOnClickListener(v -> pickDate(start[0], s -> { start[0] = s; refreshDates.run(); }));
        LinearLayout dueBox = dateBox("Due date", dueTv, dueIcon);
        dueBox.setOnClickListener(v -> pickDate(due[0], s -> { due[0] = s; refreshDates.run(); }));
        dueIcon.setOnClickListener(v -> {
            if (!due[0].isEmpty()) { due[0] = ""; refreshDates.run(); } else dueBox.performClick();
        });
        dates.addView(startBox, lpm(0, -2, 0, 0, 5, 0));
        ((LinearLayout.LayoutParams) startBox.getLayoutParams()).weight = 1f;
        dates.addView(dueBox, lpm(0, -2, 5, 0, 0, 0));
        ((LinearLayout.LayoutParams) dueBox.getLayoutParams()).weight = 1f;
        form.addView(dates, lpm(-1, -2, 0, 14, 0, 0));

        // Priority
        form.addView(tv("Priority", 14, p.ink, 600), lpm(-2, -2, 0, 16, 0, 6));
        String[] prKeys = {"high", "medium", "low"};
        int prSel = "high".equals(priority[0]) ? 0 : "low".equals(priority[0]) ? 2 : 1;
        Segmented prSeg = new Segmented(new String[]{"High", "Medium", "Low"}, prSel, i -> priority[0] = prKeys[i]);
        form.addView(prSeg.row, lp(-1, -2));

        // Status + progress (linked)
        form.addView(tv("Status", 14, p.ink, 600), lpm(-2, -2, 0, 14, 0, 6));
        TextView progLabel = tv("", 14, p.ink, 600);
        SeekBar seek = new SeekBar(this);
        seek.setMax(20);
        seek.setProgressTintList(ColorStateList.valueOf(p.acc));
        seek.setThumbTintList(ColorStateList.valueOf(p.acc));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(p.line));
        final Segmented[] statusSeg = new Segmented[1];
        statusSeg[0] = new Segmented(new String[]{"Incomplete", "Completed"}, done[0] ? 1 : 0, i -> {
            done[0] = i == 1;
            if (done[0]) { progress[0] = 100; seek.setProgress(20); progLabel.setText("Progress: 100%"); }
        });
        form.addView(statusSeg[0].row, lp(-1, -2));

        progLabel.setText("Progress: " + progress[0] + "%");
        form.addView(progLabel, lpm(-2, -2, 0, 14, 0, 0));
        seek.setProgress(progress[0] / 5);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                progress[0] = v * 5;
                progLabel.setText("Progress: " + progress[0] + "%");
                if (fromUser) {
                    done[0] = progress[0] >= 100;
                    statusSeg[0].select(done[0] ? 1 : 0, false);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        form.addView(seek, lpm(-1, -2, 0, 4, 0, 0));

        // Buttons
        LinearLayout btns = hbox();
        btns.setGravity(Gravity.END);
        TextView cancel = button("Cancel", 0, 0, p.acc, 20);
        cancel.setOnClickListener(v -> dlg.dismiss());
        TextView save = button("Save task", p.acc, 0, Color.WHITE, 20);
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { name.setError("Please enter a name"); return; }
            Task t = new Task(initial != null ? initial.id : Util.newId(), n,
                    desc.getText().toString().trim(), start[0], due[0], priority[0],
                    done[0] ? 100 : progress[0], done[0]);
            dlg.dismiss();
            store.upsertTask(list, t);
        });
        btns.addView(cancel);
        btns.addView(save, lpm(-2, -2, 8, 0, 0, 0));
        form.addView(btns, lpm(-1, -2, 0, 12, 0, 0));

        dlg.setContentView(sv);
        Window w = dlg.getWindow();
        if (w != null) {
            float r = dp(28);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(p.card);
            bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            w.setBackgroundDrawable(bg);
            w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels, dp(640)), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.getDecorView().setPadding(0, 0, 0, 0);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | (editing ? WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    : WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE));
        }
        dlg.show();
        if (!editing) name.requestFocus();
    }

    private LinearLayout dateBox(String label, TextView value, ImageView icon) {
        LinearLayout box = vbox();
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.setBackground(box(0, 10, p.mute));
        box.addView(tv(label, 12, p.mute, 400));
        LinearLayout r = hbox();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(value, new LinearLayout.LayoutParams(0, -2, 1f));
        if (icon == null) {
            icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_event);
            icon.setColorFilter(p.mute);
        }
        r.addView(icon, lp(dp(22), dp(22)));
        box.addView(r);
        return box;
    }
}
