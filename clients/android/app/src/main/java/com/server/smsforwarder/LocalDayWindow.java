package com.server.smsforwarder;

import java.util.Calendar;
import java.util.TimeZone;

final class LocalDayWindow {
    final long startInclusive;
    final long endExclusive;

    private LocalDayWindow(long startInclusive, long endExclusive) {
        this.startInclusive = startInclusive;
        this.endExclusive = endExclusive;
    }

    static LocalDayWindow containing(long instant, TimeZone timeZone) {
        Calendar day = Calendar.getInstance(timeZone);
        day.setTimeInMillis(instant);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        long start = day.getTimeInMillis();
        day.add(Calendar.DAY_OF_MONTH, 1);
        return new LocalDayWindow(start, day.getTimeInMillis());
    }
}
