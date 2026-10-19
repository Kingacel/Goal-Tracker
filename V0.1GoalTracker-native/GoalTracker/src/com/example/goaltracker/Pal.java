package com.example.goaltracker;

import android.content.Context;
import android.content.res.Configuration;

/** Colour palette (same values as the Flutter app's light/dark palettes). */
final class Pal {
    final boolean dark;
    final int bg, card, ink, mute, line, acc, bad, ok, soft;

    private Pal(boolean dark, int bg, int card, int ink, int mute, int line,
                int acc, int bad, int ok, int soft) {
        this.dark = dark; this.bg = bg; this.card = card; this.ink = ink; this.mute = mute;
        this.line = line; this.acc = acc; this.bad = bad; this.ok = ok; this.soft = soft;
    }

    static final Pal LIGHT = new Pal(false, 0xFFE6F3FD, 0xFFFFFFFF, 0xFF0F2335, 0xFF52697A,
            0xFFCDE2F2, 0xFF0284C7, 0xFFC2410C, 0xFF15803D, 0xFFD9ECFA);
    static final Pal DARK = new Pal(true, 0xFF0C1A26, 0xFF132634, 0xFFE6F2FB, 0xFF8BA7BA,
            0xFF24415A, 0xFF38BDF8, 0xFFFB923C, 0xFF4ADE80, 0xFF17354A);

    static Pal of(Context c) {
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? DARK : LIGHT;
    }
}
