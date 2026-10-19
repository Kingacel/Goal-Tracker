package com.example.goaltracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Popup menu opened by the widget's "•••" button. Home-screen widgets cannot display popups
 * themselves, so the button opens this small transparent activity, drawn like a dropdown menu
 * in the selected theme and placed next to the button when the launcher reports its bounds.
 */
public class WidgetMenuActivity extends Activity {

    private static final int MENU_W_DP = 230;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Pal p = Pal.of(this);
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        final int sw = dm.widthPixels, sh = dm.heightPixels;

        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> close());          // tap outside the menu dismisses it

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.card);
        bg.setCornerRadius(dp(14));
        bg.setStroke(Math.max(1, dp(1)), p.line);
        card.setBackground(bg);
        card.setElevation(dp(10));
        card.setClickable(true);
        card.setPadding(0, dp(6), 0, dp(6));

        addItem(card, p, "Open Goal Tracker", v -> {
            startActivity(mainIntent());
            close();
        });
        addItem(card, p, "Add new goal", v -> {
            startActivity(mainIntent().putExtra(GoalWidgetProvider.EXTRA_ADD_GOAL, true));
            close();
        });
        addItem(card, p, "Settings", v -> {
            startActivity(new Intent(this, SettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            close();
        });
        addItem(card, p, "Refresh widget", v -> {
            GoalWidgetProvider.refresh(this);
            close();
        });

        // Place the menu under the "•••" button if the launcher told us where it is.
        int w = Math.min(dp(MENU_W_DP), sw - dp(16));
        card.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int h = card.getMeasuredHeight();
        Rect r = getIntent() == null ? null : getIntent().getSourceBounds();
        int left, top;
        if (r != null && !r.isEmpty()) {
            left = r.right - w;
            top = r.bottom + dp(4);
            if (top + h > sh - dp(16)) top = Math.max(dp(16), r.top - h - dp(4));  // not enough room below
        } else {
            left = sw - w - dp(16);
            top = dp(96);
        }
        left = Math.max(dp(8), Math.min(left, sw - w - dp(8)));
        top = Math.max(dp(8), Math.min(top, sh - h - dp(8)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        lp.leftMargin = left;
        lp.topMargin = top;
        root.addView(card, lp);
        setContentView(root);
    }

    private Intent mainIntent() {
        return new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private void addItem(LinearLayout card, Pal p, String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(p.ink);
        t.setTextSize(15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(52));                       // comfortable touch target
        t.setPadding(dp(18), dp(8), dp(18), dp(8));
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            t.setBackgroundResource(tv.resourceId);
        }
        t.setOnClickListener(l);
        card.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void close() {
        finish();
        overridePendingTransition(0, 0);
    }

    private int dp(float v) { return Util.dp(this, v); }
}
