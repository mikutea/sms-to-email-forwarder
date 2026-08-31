package com.server.smsforwarder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public final class LocalDayWindowTest {
    @Test
    public void containingUsesTheRequestedLocalCalendarDay() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar instant = calendar(zone, 2026, Calendar.AUGUST, 31, 16, 42);

        LocalDayWindow window = LocalDayWindow.containing(instant.getTimeInMillis(), zone);

        assertEquals(
                calendar(zone, 2026, Calendar.AUGUST, 31, 0, 0).getTimeInMillis(),
                window.startInclusive);
        assertEquals(
                calendar(zone, 2026, Calendar.SEPTEMBER, 1, 0, 0).getTimeInMillis(),
                window.endExclusive);
    }

    @Test
    public void containingFollowsDaylightSavingBoundaries() {
        TimeZone zone = TimeZone.getTimeZone("America/New_York");
        Calendar instant = calendar(zone, 2026, Calendar.MARCH, 8, 12, 0);

        LocalDayWindow window = LocalDayWindow.containing(instant.getTimeInMillis(), zone);

        assertEquals(23L * 60L * 60L * 1000L, window.endExclusive - window.startInclusive);
    }

    private static Calendar calendar(
            TimeZone zone, int year, int month, int day, int hour, int minute) {
        Calendar value = Calendar.getInstance(zone);
        value.clear();
        value.set(year, month, day, hour, minute, 0);
        return value;
    }
}
