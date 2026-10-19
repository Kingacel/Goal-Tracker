package com.example.goaltracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Data models + auto-saving store (port of TrackerStore). Everything persists on every change. */
final class Store {

    // ───────────── Models ─────────────

    static final class Task {
        String id, name, desc, start, due, priority;
        int progress;
        boolean done;

        Task(String id, String name, String desc, String start, String due,
             String priority, int progress, boolean done) {
            this.id = id; this.name = name; this.desc = desc; this.start = start;
            this.due = due; this.priority = priority; this.progress = progress; this.done = done;
        }

        static Task fromJson(JSONObject j) {
            return new Task(
                    j.optString("id", Util.newId()),
                    j.optString("name", ""),
                    j.optString("desc", ""),
                    j.optString("start", ""),
                    j.optString("due", ""),
                    j.optString("priority", "medium"),
                    j.optInt("progress", 0),
                    j.optBoolean("done", false));
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("desc", desc)
                    .put("start", start).put("due", due).put("priority", priority)
                    .put("progress", progress).put("done", done);
        }

        int effectiveProgress() { return done ? 100 : progress; }

        boolean overdue() {
            return !done && !due.isEmpty() && Util.endOfDayMillis(due) < System.currentTimeMillis();
        }
    }

    static final class TaskList {
        String id, name;
        final List<Task> tasks = new ArrayList<>();

        TaskList(String id, String name) { this.id = id; this.name = name; }

        static TaskList fromJson(JSONObject j) {
            TaskList l = new TaskList(j.optString("id", Util.newId()), j.optString("name", ""));
            JSONArray a = j.optJSONArray("tasks");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null) l.tasks.add(Task.fromJson(o));
                }
            }
            return l;
        }

        JSONObject toJson() throws JSONException {
            JSONArray a = new JSONArray();
            for (Task t : tasks) a.put(t.toJson());
            return new JSONObject().put("id", id).put("name", name).put("tasks", a);
        }
    }

    static final class Goal {
        /** created = fixed start date (JSON key kept for compatibility); total = stored duration in days. */
        String id, title, deadline, created;
        int total;
        /**
         * In-memory only (never saved). Set when the user picks a start date that lies after the
         * current deadline: the old, valid start/deadline pair stays stored until a valid deadline
         * is chosen, so an invalid range can never be saved.
         */
        String pendingStart;
        final List<TaskList> lists = new ArrayList<>();

        Goal(String id, String title, String deadline, String created) {
            this.id = id; this.title = title; this.deadline = deadline; this.created = created;
            recalcTotal();
        }

        /** The start date shown in the UI (a pending start while a new deadline is awaited). */
        String effectiveStart() { return pendingStart != null ? pendingStart : created; }

        /** True while the user must still choose a deadline that fits the newly picked start date. */
        boolean awaitingDeadline() { return pendingStart != null; }

        /** Deadline to display: empty while a new deadline is awaited. */
        String shownDeadline() { return pendingStart != null ? "" : deadline; }

        /** Only called when the start date or deadline is set/changed (or missing in legacy data). */
        void recalcTotal() { total = (int) Util.totalDays(created, deadline); }

        Long remaining() { return Util.daysRemaining(deadline); }
        boolean expired() { Long r = remaining(); return r != null && r < 0; }
        boolean completed() { return !allTasks().isEmpty() && percent() == 100; }
        int pctElapsed() { return Util.percentElapsed(remaining(), total); }

        /** "12 / 20 — 60%" */
        String summary() {
            Long r = remaining();
            if (r == null || pendingStart != null) return "– / –";
            return Math.max(0, r) + " / " + total + " \u2014 " + pctElapsed() + "%";
        }

        static Goal fromJson(JSONObject j) {
            Goal g = new Goal(
                    j.optString("id", Util.newId()),
                    j.optString("title", ""),
                    j.optString("deadline", ""),
                    Util.parse(j.optString("created", "")) != null ? j.optString("created") : Util.todayIso());
            if (j.has("total") && j.optInt("total", -1) >= 0) g.total = j.optInt("total");
            JSONArray a = j.optJSONArray("lists");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null) g.lists.add(TaskList.fromJson(o));
                }
            }
            return g;
        }

        JSONObject toJson() throws JSONException {
            JSONArray a = new JSONArray();
            for (TaskList l : lists) a.put(l.toJson());
            return new JSONObject().put("id", id).put("title", title).put("deadline", deadline)
                    .put("created", created).put("total", total).put("lists", a);
        }

        List<Task> allTasks() {
            List<Task> out = new ArrayList<>();
            for (TaskList l : lists) out.addAll(l.tasks);
            return out;
        }

        int percent() {
            List<Task> t = allTasks();
            if (t.isEmpty()) return 0;
            int sum = 0;
            for (Task x : t) sum += x.effectiveProgress();
            return (int) Math.round(sum / (double) t.size());
        }
    }

    // ───────────── Store ─────────────

    private static final String PREFS = "goal_tracker";
    private static final String KEY = "goal-tracker-v1";   // same key: existing data stays in place
    private static final int SCHEMA = 2;                    // 1 = no stored total, 2 = stored total
    private static final String BACKUP_DIR = "backup";

    private static Store instance;

    static synchronized Store get(Context c) {
        if (instance == null) {
            instance = new Store(c.getApplicationContext());
            instance.load();
            LocalFolder.queue(c, instance.buildJson());   // re-sync the storage folder on app start (no-op if none)
        }
        return instance;
    }

    private final Context ctx;
    final List<Goal> goals = new ArrayList<>();
    String activeId;
    /** Called after changed(); the UI re-renders. */
    Runnable listener;

    private Store(Context c) { ctx = c; }

    Goal active() {
        for (Goal g : goals) if (g.id.equals(activeId)) return g;
        return goals.isEmpty() ? null : goals.get(0);
    }

    // ───────────── Load / migrate / recover ─────────────

    /** A parsed backup document. Nothing is applied to the app until importDocument(). */
    static final class Parsed {
        final List<Goal> goals;
        final String active;
        Parsed(List<Goal> goals, String active) { this.goals = goals; this.active = active; }
    }

    /** Parses a saved/exported document without touching any app state. Null if it is not valid. */
    static Parsed parseDocument(String raw) {
        if (raw == null) return null;
        try {
            JSONObject j = new JSONObject(raw);
            JSONArray a = j.optJSONArray("goals");
            if (a == null) return null;
            List<Goal> parsed = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) parsed.add(Goal.fromJson(o));
            }
            return new Parsed(parsed, j.isNull("active") ? null : j.optString("active", null));
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    /** Parses a saved document into goals. Returns false (goals untouched) if it is not valid. */
    private boolean parseInto(String raw) {
        Parsed p = parseDocument(raw);
        if (p == null) return false;
        goals.clear();
        goals.addAll(p.goals);
        activeId = p.active;
        return true;
    }

    private void load() {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, null);
        if (raw != null && parseInto(raw)) {
            int version = 1;
            try { version = new JSONObject(raw).optInt("v", 1); } catch (JSONException ignored) { }
            if (version < SCHEMA) {
                writeFile("pre-migration-v" + version + ".json", raw); // safety copy before migrating
                persist();                                              // rewrites in the new format
            } else {
                backup(raw);
            }
            return;
        }
        if (raw != null) writeFile("corrupt-" + System.currentTimeMillis() + ".json", raw);
        // Primary storage missing or unreadable: recover from the newest valid backup.
        if (restoreFromBackupInternal()) {
            persist();
            return;
        }
        seed();
        persist();
    }

    /** Replaces the current data with the latest valid backup. Returns false if none is usable. */
    boolean restoreFromBackup() {
        if (!restoreFromBackupInternal()) return false;
        changed();
        return true;
    }

    private boolean restoreFromBackupInternal() {
        for (String name : new String[]{"latest.json", "last-nonempty.json"}) {
            if (parseInto(readFile(name))) return true;
        }
        return false;
    }

    boolean hasBackup() {
        return readFile("latest.json") != null || readFile("last-nonempty.json") != null;
    }

    // ───────────── Backup files (atomic writes) ─────────────

    private java.io.File dir() {
        java.io.File d = new java.io.File(ctx.getFilesDir(), BACKUP_DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private String readFile(String name) {
        java.io.File f = new java.io.File(dir(), name);
        if (!f.isFile()) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) { int n = in.read(b, off, b.length - off); if (n < 0) break; off += n; }
            return new String(b, 0, off, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /** Writes to a temp file then renames, so a crash never leaves a half-written file. */
    private void writeFile(String name, String text) {
        try {
            java.io.File tmp = new java.io.File(dir(), name + ".tmp");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                out.write(text.getBytes("UTF-8"));
                out.getFD().sync();
            }
            java.io.File dst = new java.io.File(dir(), name);
            if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst); }
        } catch (Exception ignored) { }
    }

    /** Only complete, parseable data is backed up; an empty list never replaces the last non-empty backup. */
    private void backup(String json) {
        try {
            JSONObject j = new JSONObject(json);
            JSONArray a = j.optJSONArray("goals");
            if (a == null) return;
            writeFile("latest.json", json);
            if (a.length() > 0) writeFile("last-nonempty.json", json);
        } catch (JSONException ignored) { }
    }

    // ───────────── Persist ─────────────

    private Task seedTask(String name, String desc, int off, String pri, int prog, boolean done) {
        return new Task(Util.newId(), name, desc, Util.todayPlus(0), Util.todayPlus(off), pri, prog, done);
    }

    private void seed() {
        Goal g = new Goal(Util.newId(), "Build an Android App", Util.todayPlus(85), Util.todayPlus(0));
        TaskList design = new TaskList(Util.newId(), "Design");
        design.tasks.add(seedTask("Sketch main screens", "Wireframes for the home and detail screens", 7, "high", 100, true));
        design.tasks.add(seedTask("Pick colors and fonts", "", 14, "medium", 40, false));
        TaskList build = new TaskList(Util.newId(), "Build");
        build.tasks.add(seedTask("Set up project", "Install tools, create repo", 10, "high", 60, false));
        build.tasks.add(seedTask("Build the home screen", "", 30, "medium", 0, false));
        g.lists.add(design);
        g.lists.add(build);
        goals.add(g);
        activeId = g.id;
    }

    /** The full saved document as JSON (null if it cannot be built). */
    private String buildJson() {
        try {
            JSONArray a = new JSONArray();
            for (Goal g : goals) a.put(g.toJson());
            JSONObject root = new JSONObject().put("v", SCHEMA).put("goals", a)
                    .put("active", activeId == null ? JSONObject.NULL : activeId);
            return root.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /** Current data as a JSON document, for Export and the local-folder sync. */
    String snapshotJson() { return buildJson(); }

    private void persist() {
        try {
            String json = buildJson();
            // Verify the document round-trips before it replaces anything.
            if (json != null && new JSONObject(json).optJSONArray("goals") != null) {
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).commit();
                backup(json);
                LocalFolder.queue(ctx, json);   // keeps the chosen storage folder up to date (no-op if none)
            }
        } catch (JSONException ignored) { }
        GoalWidgetProvider.refresh(ctx);   // widget reads this same store, so it is never out of sync
    }

    // ───────────── Import ─────────────

    /**
     * Applies an imported document. replace: the file's goals replace the current ones.
     * merge: only goals whose id is not already present are added (so nothing is duplicated).
     * A safety copy of the current data is always written first. Returns {added, skipped}.
     */
    int[] importDocument(Parsed in, boolean merge) {
        String current = buildJson();
        if (current != null) {
            writeFile("pre-import-" + System.currentTimeMillis() + ".json", current);
            pruneSafetyCopies();
        }
        java.util.Set<String> existing = new java.util.HashSet<>();
        if (merge) for (Goal g : goals) existing.add(g.id);
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Goal> toAdd = new ArrayList<>();
        int skipped = 0;
        for (Goal g : in.goals) {
            if (merge && existing.contains(g.id)) { skipped++; continue; }
            if (!seen.add(g.id)) { g.id = Util.newId(); seen.add(g.id); }   // duplicate id inside the file
            java.time.LocalDate s = Util.parse(g.created), d = Util.parse(g.deadline);
            if (s != null && d != null && d.isBefore(s)) { g.created = g.deadline; g.recalcTotal(); }
            toAdd.add(g);
        }
        if (!merge) goals.clear();
        goals.addAll(toAdd);
        if (!merge) {
            boolean ok = false;
            for (Goal g : goals) if (g.id.equals(in.active)) ok = true;
            activeId = ok ? in.active : (goals.isEmpty() ? null : goals.get(0).id);
        } else if (activeId == null && !goals.isEmpty()) {
            activeId = goals.get(0).id;
        }
        changed();
        return new int[]{toAdd.size(), skipped};
    }

    /** Keeps only the 5 newest pre-import safety copies. */
    private void pruneSafetyCopies() {
        java.io.File[] fs = dir().listFiles((d, n) -> n.startsWith("pre-import-") && n.endsWith(".json"));
        if (fs == null || fs.length <= 5) return;
        java.util.Arrays.sort(fs, (x, y) -> x.getName().compareTo(y.getName()));
        for (int i = 0; i < fs.length - 5; i++) fs[i].delete();
    }

    /** Save and rebuild the UI. */
    void changed() {
        persist();
        if (listener != null) listener.run();
    }

    /** Save only (for text fields that already show the latest text). */
    void saveQuietly() { persist(); }

    // ───────────── Dates ─────────────
    // The only places the total duration is recalculated. A deadline must be on or after the
    // start date; an invalid range is never written to storage.

    static final String MSG_INVALID_DEADLINE =
            "Invalid deadline. The deadline cannot be earlier than the start date.";

    /** True if both dates parse and the deadline is on or after the start date. */
    static boolean validRange(String start, String deadline) {
        java.time.LocalDate s = Util.parse(start), d = Util.parse(deadline);
        return s != null && d != null && !d.isBefore(s);
    }

    /** Returns false (and changes nothing) if the deadline is earlier than the start date. */
    boolean setDeadline(Goal g, String iso) {
        if (Util.parse(iso) == null) return false;
        if (!validRange(g.effectiveStart(), iso)) return false;
        if (g.pendingStart != null) {          // commit the pending start together with its new deadline
            g.created = g.pendingStart;
            g.pendingStart = null;
        }
        g.deadline = iso;
        g.recalcTotal();
        changed();
        return true;
    }

    /**
     * Returns true if the start date was saved. Returns false if the existing deadline is now
     * earlier than the new start: the new start is held in memory only, and the caller must tell
     * the user to pick a valid deadline (setDeadline commits both).
     */
    boolean setStart(Goal g, String iso) {
        java.time.LocalDate s = Util.parse(iso), d = Util.parse(g.deadline);
        if (s == null) return false;
        if (d != null && s.isAfter(d)) {
            g.pendingStart = iso;
            changed();
            return false;
        }
        g.pendingStart = null;
        g.created = iso;
        g.recalcTotal();
        changed();
        return true;
    }

    // Goals
    void selectGoal(String id) { activeId = id; changed(); }

    void addGoal() {
        Goal g = new Goal(Util.newId(), "New goal", Util.todayPlus(30), Util.todayIso());
        g.lists.add(new TaskList(Util.newId(), "To do"));
        goals.add(g);
        activeId = g.id;
        changed();
    }

    void deleteGoal(Goal g) {
        goals.remove(g);
        activeId = goals.isEmpty() ? null : goals.get(0).id;
        changed();
    }

    // Lists
    void addList(Goal g) { g.lists.add(new TaskList(Util.newId(), "New list")); changed(); }

    void deleteList(Goal g, TaskList l) { g.lists.remove(l); changed(); }

    // Tasks
    void upsertTask(TaskList l, Task data) {
        int idx = -1;
        for (int i = 0; i < l.tasks.size(); i++) if (l.tasks.get(i).id.equals(data.id)) { idx = i; break; }
        if (idx >= 0) l.tasks.set(idx, data); else l.tasks.add(data);
        changed();
    }

    void deleteTask(TaskList l, Task t) { l.tasks.remove(t); changed(); }

    void toggleTask(Task t, boolean v) {
        t.done = v;
        t.progress = v ? 100 : 0;
        changed();
    }

    void moveTask(TaskList l, int i, int delta) {
        int j = i + delta;
        if (j < 0 || j >= l.tasks.size()) return;
        Task t = l.tasks.remove(i);
        l.tasks.add(j, t);
        changed();
    }
}
