package com.example.goaltracker;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** Feeds the widget's scrollable goal list. Android handles the touch scrolling natively. */
public class GoalWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private List<GoalWidgetProvider.Item> items = new ArrayList<>();

        Factory(Context c) { ctx = c; }

        @Override public void onCreate() { items = GoalWidgetProvider.loadItems(ctx); }

        /** Called on notifyAppWidgetViewDataChanged: reload from the store. */
        @Override public void onDataSetChanged() { items = GoalWidgetProvider.loadItems(ctx); }

        @Override public void onDestroy() { items = new ArrayList<>(); }

        @Override public int getCount() { return items.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_row);
            if (position < 0 || position >= items.size()) return v;
            GoalWidgetProvider.Item it = items.get(position);

            String sub = it.completed ? "Completed \u2713"
                    : it.days != null && it.days < 0 ? "100% \u00b7 Expired"
                    : it.days == null ? "No deadline" : it.percent + "% complete";
            String remaining = it.days == null ? "\u2013" : String.valueOf(Math.max(0, it.days));
            String total = it.days == null ? "\u2013" : String.valueOf(it.totalDays);

            v.setTextViewText(R.id.w_name, it.name);
            v.setTextViewText(R.id.w_days, remaining + " / " + total);
            v.setTextViewText(R.id.w_pct, sub);

            int mode = Pal.mode(ctx);
            if (mode != Pal.MODE_SYSTEM) {      // forced Light/Dark; System uses the resource colours
                Pal t = mode == Pal.MODE_DARK ? Pal.DARK : Pal.LIGHT;
                v.setTextColor(R.id.w_name, t.ink);
                v.setTextColor(R.id.w_days, t.ink);
                v.setTextColor(R.id.w_pct, t.mute);
                v.setInt(R.id.w_vsep, "setBackgroundColor", t.line);
                v.setInt(R.id.w_divider, "setBackgroundColor", t.line);
            }

            Intent fill = new Intent();
            fill.putExtra(GoalWidgetProvider.EXTRA_GOAL_ID, it.id);
            v.setOnClickFillInIntent(R.id.w_row, fill);
            return v;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) {
            return position < items.size() ? items.get(position).id.hashCode() : position;
        }
        @Override public boolean hasStableIds() { return true; }
    }
}
