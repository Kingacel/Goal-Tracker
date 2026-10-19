package com.example.goaltracker;

import android.content.Context;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/** Date and misc helpers (ports of the Dart helpers in the Flutter app). */
final class Util {
    private Util() {}

    static final String[] MONTHS_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    static final String[] MONTHS_LONG = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};

    private static final Random RNG = new Random();

    static String todayIso() { return LocalDate.now().toString(); }

    static String todayPlus(int days) { return LocalDate.now().plusDays(days).toString(); }

    /** Parses yyyy-MM-dd; returns null for empty or invalid input. */
    static LocalDate parse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Epoch millis of 23:59:59 local time on the given date. */
    static long endOfDayMillis(String s) {
        LocalDate d = parse(s);
        if (d == null) return Long.MAX_VALUE;
        return d.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static String fmtShort(String s) {
        LocalDate d = parse(s);
        return d == null ? "No date"
                : MONTHS_SHORT[d.getMonthValue() - 1] + " " + d.getDayOfMonth() + ", " + d.getYear();
    }

    static String fmtLong(String s) {
        LocalDate d = parse(s);
        return d == null ? "Not set"
                : MONTHS_LONG[d.getMonthValue() - 1] + " " + d.getDayOfMonth() + ", " + d.getYear();
    }

    /** Whole days between creation and deadline (never negative). */
    static long totalDays(String created, String deadline) {
        LocalDate a = parse(created), b = parse(deadline);
        if (a == null || b == null) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(a, b));
    }

    static String newId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) sb.append(Character.forDigit(RNG.nextInt(36), 36));
        return sb.toString();
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
