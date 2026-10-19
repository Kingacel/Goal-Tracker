package com.example.goaltracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** Colour palette plus the saved appearance setting (System / Light / Dark). */
final class Pal {
    final boolean dark;
    final int bg, card, ink, mute, line, acc, onAcc, bad, ok, soft;

    private Pal(boolean dark, int bg, int card, int ink, int mute, int line,
                int acc, int onAcc, int bad, int ok, int soft) {
        this.dark = dark; this.bg = bg; this.card = card; this.ink = ink; this.mute = mute;
        this.line = line; this.acc = acc; this.onAcc = onAcc; this.bad = bad; this.ok = ok; this.soft = soft;
    }

    /** Light: white background, black text (pure black-and-white palette). */
    static final Pal LIGHT = new Pal(false, 0xFFFFFFFF, 0xFFF5F5F5, 0xFF000000, 0xFF6B6B6B,
            0xFFD4D4D4, 0xFF000000, 0xFFFFFFFF, 0xFF000000, 0xFF000000, 0xFFE8E8E8);
    /** Dark: black background, white text. */
    static final Pal DARK = new Pal(true, 0xFF000000, 0xFF111111, 0xFFFFFFFF, 0xFFA0A0A0,
            0xFF2E2E2E, 0xFFFFFFFF, 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF1C1C1C);

    // ───────────── Appearance setting (own preferences file; goal data is never touched) ─────────────

    static final int MODE_SYSTEM = 0, MODE_LIGHT = 1, MODE_DARK = 2;
    private static final String PREFS = "goal_tracker_settings";
    private static final String KEY_MODE = "theme_mode";

    /** Saved appearance; defaults to "follow the system" so existing users see no change. */
    static int mode(Context c) {
        try {
            int m = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt(KEY_MODE, MODE_SYSTEM);
            return m == MODE_LIGHT || m == MODE_DARK ? m : MODE_SYSTEM;
        } catch (Exception e) {
            return MODE_SYSTEM;
        }
    }

    static void setMode(Context c, int mode) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_MODE, mode).commit();
    }

    static boolean systemIsDark(Context c) {
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    static boolean isDark(Context c) {
        int m = mode(c);
        return m == MODE_DARK || (m == MODE_SYSTEM && systemIsDark(c));
    }

    static Pal of(Context c) { return isDark(c) ? DARK : LIGHT; }

    /** Activity theme matching the saved appearance (also themes dialogs and date pickers). */
    static int themeRes(Context c) {
        int m = mode(c);
        if (m == MODE_LIGHT) return R.style.AppTheme_Light;
        if (m == MODE_DARK) return R.style.AppTheme_Dark;
        return R.style.AppTheme;
    }

    /** Light/dark status-bar icons and bar colours for an activity window. */
    static void applyBars(android.app.Activity a, Pal p) {
        android.view.Window w = a.getWindow();
        w.setStatusBarColor(p.bg);
        w.setNavigationBarColor(p.bg);
        android.view.View d = w.getDecorView();
        int f = d.getSystemUiVisibility();
        int light = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        d.setSystemUiVisibility(p.dark ? (f & ~light) : (f | light));
    }
}
