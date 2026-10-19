package com.example.goaltracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Home-screen widget: goal name, days left and number of lists. */
public class GoalWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        SharedPreferences prefs = context.getSharedPreferences(Store.WIDGET_PREFS, Context.MODE_PRIVATE);
        String name = prefs.getString("goal_name", "");
        String deadline = prefs.getString("goal_deadline", "");
        int lists = prefs.getInt("goal_lists", 0);

        Long days = daysLeft(deadline);
        String daysText;
        String labelText;
        if (days == null) { daysText = "–"; labelText = ""; }
        else if (days < 0) { daysText = "Passed"; labelText = ""; }
        else { daysText = String.valueOf(days); labelText = days == 1 ? "day left" : "days left"; }

        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pending = launch == null ? null : PendingIntent.getActivity(
                context, 0, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.goal_widget);
            v.setTextViewText(R.id.w_name, name.isEmpty() ? "No goal yet" : name);
            v.setTextViewText(R.id.w_days, daysText);
            v.setTextViewText(R.id.w_label, labelText);
            v.setTextViewText(R.id.w_lists, lists == 1 ? "1 list" : lists + " lists");
            if (pending != null) v.setOnClickPendingIntent(R.id.w_root, pending);
            manager.updateAppWidget(id, v);
        }
    }

    /** Whole days from today to the deadline (null if no deadline). */
    private static Long daysLeft(String deadline) {
        LocalDate target = Util.parse(deadline);
        if (target == null) return null;
        return ChronoUnit.DAYS.between(LocalDate.now(), target);
    }

    /** Re-draws every placed widget (called whenever the app saves). */
    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, GoalWidgetProvider.class));
        if (ids != null && ids.length > 0) new GoalWidgetProvider().onUpdate(c, m, ids);
    }
}
