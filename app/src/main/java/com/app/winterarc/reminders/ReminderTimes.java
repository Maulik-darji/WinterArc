package com.app.winterarc.reminders;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** DST-safe reminder time math. */
public final class ReminderTimes {
    private ReminderTimes() {}

    /**
     * The next occurrence of {@code time} strictly after {@code now}, in now's zone. Times that
     * fall into a DST gap resolve to the shifted wall-clock time chosen by java.time.
     */
    public static ZonedDateTime nextOccurrence(ZonedDateTime now, LocalTime time) {
        ZonedDateTime candidate = ZonedDateTime.of(now.toLocalDate(), time, now.getZone());
        if (!candidate.isAfter(now)) {
            candidate = ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.getZone());
        }
        return candidate;
    }

    public static Duration delayUntilNext(ZonedDateTime now, LocalTime time) {
        return Duration.between(now, nextOccurrence(now, time));
    }
}
