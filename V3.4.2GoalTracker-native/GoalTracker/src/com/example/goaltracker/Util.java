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

    /** Whole calendar days from today to the deadline (negative once passed); null if no valid deadline.
     *  Uses date-only values, so the result does not depend on the time zone or time of day. */
    static Long daysRemaining(String deadline) {
        LocalDate d = parse(deadline);
        return d == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), d);
    }

    /**
     * Elapsed percentage = ((total - remaining) / total) x 100, rounded and clamped to 0..100.
     * 0% on the start date, 100% once remaining reaches 0 (and stays 100% after the deadline).
     * Rounding never shows 100% while days are still remaining (capped at 99%).
     * Missing deadline -> 0. Zero/invalid total -> 100% if the deadline is reached, else 0%.
     */
    static int percentElapsed(Long remaining, long total) {
        if (remaining == null) return 0;
        if (remaining <= 0) return 100;
        if (total <= 0) return 0;
        long elapsed = Math.max(0, Math.min(total, total - remaining));
        return (int) Math.min(99, Math.round(elapsed * 100.0 / total));
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
