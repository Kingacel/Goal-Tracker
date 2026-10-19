package com.example.goaltracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.app.AlarmManager;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Home-screen widget: a vertically scrollable list of all goals (days left / total days and
 * percent complete), plus a "+" button (new goal) and a "View all goals" button.
 */
public class GoalWidgetProvider extends AppWidgetProvider {

    static final String EXTRA_ADD_GOAL = "widget_add_goal";
    static final String EXTRA_GOAL_ID = "widget_goal_id";

    /** One goal row in the scrolling list. */
    static final class Item {
        String id, name;
        int percent;      // ((total - remaining) / total) x 100, 0..100
        Long days;        // days remaining (null = no deadline, negative = passed)
        long totalDays;   // stored duration (start -> deadline)
        boolean completed;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        scheduleMidnight(context);

        PendingIntent openApp = activity(context, 0, null, false);
        PendingIntent addGoal = activity(context, 1, null, true);
        // Template for the list rows; each row adds its goal id through a fill-in intent.
        Intent tpl = new Intent(context, MainActivity.class);
        tpl.setAction(Intent.ACTION_MAIN);
        tpl.addCategory(Intent.CATEGORY_LAUNCHER);
        tpl.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent rowTemplate = PendingIntent.getActivity(context, 2, tpl,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // "•••" opens WidgetMenuActivity (a widget cannot show a real popup menu itself).
        // Mutable so the launcher can add the button's screen bounds, used to place the menu.
        Intent mi = new Intent(context, WidgetMenuActivity.class);
        mi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        PendingIntent menu = PendingIntent.getActivity(context, 3, mi,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.goal_widget);

            // Scrollable list of ALL goals, served by GoalWidgetService.
            Intent svc = new Intent(context, GoalWidgetService.class);
            svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            svc.setData(android.net.Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
            v.setRemoteAdapter(R.id.w_list, svc);
            v.setEmptyView(R.id.w_list, R.id.w_empty);
            v.setPendingIntentTemplate(R.id.w_list, rowTemplate);

            v.setOnClickPendingIntent(R.id.w_empty, addGoal);
            v.setOnClickPendingIntent(R.id.w_root, openApp);
            v.setOnClickPendingIntent(R.id.w_add, addGoal);
            v.setOnClickPendingIntent(R.id.w_more, menu);
            applyTheme(context, v);
            manager.updateAppWidget(id, v);
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.w_list);
    }

    /**
     * Applies the saved appearance. In "System" mode nothing is overridden: the layout's colour
     * resources already switch between day and night. Light/Dark force fixed colours.
     */
    static void applyTheme(Context c, RemoteViews v) {
        int mode = Pal.mode(c);
        if (mode == Pal.MODE_SYSTEM) return;
        boolean dark = mode == Pal.MODE_DARK;
        Pal t = dark ? Pal.DARK : Pal.LIGHT;
        v.setInt(R.id.w_root, "setBackgroundResource",
                dark ? R.drawable.widget_bg_dark : R.drawable.widget_bg_light);
        v.setTextColor(R.id.w_add, t.ink);
        v.setTextColor(R.id.w_more, t.ink);
        v.setTextColor(R.id.w_empty, t.ink);
    }

    /** Builds a PendingIntent that opens the app, optionally selecting a goal or adding a new one. */
    private static PendingIntent activity(Context c, int requestCode, String goalId, boolean add) {
        Intent i = new Intent(c, MainActivity.class);
        i.setAction(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (goalId != null) i.putExtra(EXTRA_GOAL_ID, goalId);
        if (add) i.putExtra(EXTRA_ADD_GOAL, true);
        return PendingIntent.getActivity(c, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Reads the live Store (single source of truth) and orders goals by soonest deadline. */
    static List<Item> loadItems(Context c) {
        // The widget service reads on a binder thread while the app may be editing; if the store
        // changes mid-read, retry instead of crashing.
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return buildItems(c);
            } catch (RuntimeException e) {
                // concurrent modification: try again
            }
        }
        return new ArrayList<>();
    }

    private static List<Item> buildItems(Context c) {
        List<Item> out = new ArrayList<>();
        for (Store.Goal g : new ArrayList<>(Store.get(c).goals)) {
            Item it = new Item();
            it.id = g.id;
            it.name = g.title.isEmpty() ? "Untitled" : g.title;
            it.days = g.remaining();
            it.totalDays = g.total;
            it.percent = g.pctElapsed();
            it.completed = g.completed();
            out.add(it);
        }
        // Active first (soonest deadline on top), then completed/expired, then goals without a deadline.
        Collections.sort(out, (x, y) -> Long.compare(rank(x), rank(y)));
        return out;
    }

    private static long rank(Item it) {
        if (it.days == null) return Long.MAX_VALUE;
        if (it.completed) return 2_000_000L;
        if (it.days < 0) return 1_000_000L - it.days;
        return it.days;
    }

    private static final String ACTION_MIDNIGHT = "com.example.goaltracker.WIDGET_MIDNIGHT";

    /** Re-draws the widget when the date, time or time zone changes, so day counts stay current. */
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String a = intent == null ? null : intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a) || ACTION_MIDNIGHT.equals(a)
                || Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            refresh(context);
        }
    }

    @Override public void onEnabled(Context c) { scheduleMidnight(c); }

    @Override public void onDisabled(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(midnightIntent(c));
    }

    private static PendingIntent midnightIntent(Context c) {
        Intent i = new Intent(c, GoalWidgetProvider.class).setAction(ACTION_MIDNIGHT);
        return PendingIntent.getBroadcast(c, 99, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Wakes the widget just after local midnight (inexact alarm, no special permission needed). */
    private static void scheduleMidnight(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 5000;
        am.setAndAllowWhileIdle(AlarmManager.RTC, at, midnightIntent(c));
    }

    /** Re-draws every placed widget (called whenever the app saves or the date changes). */
    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, GoalWidgetProvider.class));
        if (ids != null && ids.length > 0) {
            new GoalWidgetProvider().onUpdate(c, m, ids);
        }
    }
}
