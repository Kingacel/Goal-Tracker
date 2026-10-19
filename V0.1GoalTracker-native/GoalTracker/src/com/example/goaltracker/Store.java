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
        String id, title, deadline, created;
        final List<TaskList> lists = new ArrayList<>();

        Goal(String id, String title, String deadline, String created) {
            this.id = id; this.title = title; this.deadline = deadline; this.created = created;
        }

        static Goal fromJson(JSONObject j) {
            Goal g = new Goal(
                    j.optString("id", Util.newId()),
                    j.optString("title", ""),
                    j.optString("deadline", ""),
                    j.optString("created", Util.todayIso()));
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
                    .put("created", created).put("lists", a);
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
    private static final String KEY = "goal-tracker-v1";
    static final String WIDGET_PREFS = "goal_widget";

    private static Store instance;

    static synchronized Store get(Context c) {
        if (instance == null) {
            instance = new Store(c.getApplicationContext());
            instance.load();
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

    private void load() {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, null);
        if (raw != null) {
            try {
                JSONObject j = new JSONObject(raw);
                JSONArray a = j.optJSONArray("goals");
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.optJSONObject(i);
                        if (o != null) goals.add(Goal.fromJson(o));
                    }
                }
                activeId = j.isNull("active") ? null : j.optString("active", null);
                return;
            } catch (JSONException e) {
                goals.clear(); // fall through to seed data
            }
        }
        seed();
        persist();
    }

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

    private void persist() {
        try {
            JSONArray a = new JSONArray();
            for (Goal g : goals) a.put(g.toJson());
            JSONObject root = new JSONObject().put("goals", a).put("active", activeId == null ? JSONObject.NULL : activeId);
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply();
        } catch (JSONException ignored) { }
        syncWidget();
    }

    /** Pushes the active goal's name, deadline and list count to the home-screen widget. */
    private void syncWidget() {
        try {
            Goal g = active();
            ctx.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
                    .putString("goal_name", g == null ? "" : (g.title.isEmpty() ? "Untitled" : g.title))
                    .putString("goal_deadline", g == null ? "" : g.deadline)
                    .putInt("goal_lists", g == null ? 0 : g.lists.size())
                    .apply();
            GoalWidgetProvider.refresh(ctx);
        } catch (Exception ignored) { }
    }

    /** Save and rebuild the UI. */
    void changed() {
        persist();
        if (listener != null) listener.run();
    }

    /** Save only (for text fields that already show the latest text). */
    void saveQuietly() { persist(); }

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
