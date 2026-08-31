package com.server.smsforwarder;

import java.util.Calendar;
import java.util.TimeZone;

final class LocalDayWindow {
    private static final long BOUNDARY_SEARCH_RADIUS_MS = 36L * 60L * 60L * 1000L;
    private static final long BOUNDARY_SEARCH_STEP_MS = 15L * 60L * 1000L;
    final long startInclusive;
    final long endExclusive;

    private LocalDayWindow(long startInclusive, long endExclusive) {
        this.startInclusive = startInclusive;
        this.endExclusive = endExclusive;
    }

    static LocalDayWindow containing(long instant, TimeZone timeZone) {
        Calendar day = Calendar.getInstance(timeZone);
        day.setTimeInMillis(instant);
        int year = day.get(Calendar.YEAR);
        int month = day.get(Calendar.MONTH);
        int dayOfMonth = day.get(Calendar.DAY_OF_MONTH);

        Calendar nextDay = (Calendar) day.clone();
        nextDay.set(Calendar.HOUR_OF_DAY, 12);
        nextDay.set(Calendar.MINUTE, 0);
        nextDay.set(Calendar.SECOND, 0);
        nextDay.set(Calendar.MILLISECOND, 0);
        nextDay.add(Calendar.DAY_OF_MONTH, 1);

        long start = firstInstantOfDate(year, month, dayOfMonth, timeZone);
        long end = firstInstantOfDate(
                nextDay.get(Calendar.YEAR),
                nextDay.get(Calendar.MONTH),
                nextDay.get(Calendar.DAY_OF_MONTH),
                timeZone);
        return new LocalDayWindow(start, end);
    }

    private static long firstInstantOfDate(
            int year, int month, int dayOfMonth, TimeZone timeZone) {
        Calendar normalizedMidnight = Calendar.getInstance(timeZone);
        normalizedMidnight.clear();
        normalizedMidnight.set(year, month, dayOfMonth, 0, 0, 0);
        long normalized = normalizedMidnight.getTimeInMillis();
        long searchStart = normalized - BOUNDARY_SEARCH_RADIUS_MS;
        long searchEnd = normalized + BOUNDARY_SEARCH_RADIUS_MS;
        long previous = searchStart;
        if (hasDate(previous, year, month, dayOfMonth, timeZone)) {
            return previous;
        }
        for (long candidate = searchStart + BOUNDARY_SEARCH_STEP_MS;
                candidate <= searchEnd;
                candidate += BOUNDARY_SEARCH_STEP_MS) {
            if (hasDate(candidate, year, month, dayOfMonth, timeZone)) {
                return firstMatchingInstant(
                        previous, candidate, year, month, dayOfMonth, timeZone);
            }
            previous = candidate;
        }
        // Some civil dates are skipped entirely when a region crosses the date line. Calendar's
        // lenient normalized value is then the first representable instant after the skipped date.
        return normalized;
    }

    private static long firstMatchingInstant(
            long nonMatching,
            long matching,
            int year,
            int month,
            int dayOfMonth,
            TimeZone timeZone) {
        long low = nonMatching;
        long high = matching;
        while (high - low > 1L) {
            long middle = low + (high - low) / 2L;
            if (hasDate(middle, year, month, dayOfMonth, timeZone)) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return high;
    }

    private static boolean hasDate(
            long instant, int year, int month, int dayOfMonth, TimeZone timeZone) {
        Calendar local = Calendar.getInstance(timeZone);
        local.setTimeInMillis(instant);
        return local.get(Calendar.YEAR) == year
                && local.get(Calendar.MONTH) == month
                && local.get(Calendar.DAY_OF_MONTH) == dayOfMonth;
    }
}
