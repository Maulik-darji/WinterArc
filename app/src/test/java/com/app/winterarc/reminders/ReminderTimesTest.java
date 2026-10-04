package com.app.winterarc.reminders;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class ReminderTimesTest {
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Test
    public void laterTodayWhenTimeNotPassed() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 2, 6, 0, 0, 0, LONDON);
        assertEquals(ZonedDateTime.of(2026, 10, 2, 7, 0, 0, 0, LONDON), ReminderTimes.nextOccurrence(now, LocalTime.of(7, 0)));
    }

    @Test
    public void tomorrowWhenTimePassed() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 2, 7, 0, 0, 0, LONDON);
        assertEquals(ZonedDateTime.of(2026, 10, 3, 7, 0, 0, 0, LONDON), ReminderTimes.nextOccurrence(now, LocalTime.of(7, 0)));
    }

    @Test
    public void dstEndKeepsWallClockTime() {
        // Clocks go back on 25 Oct 2026 in the UK: that day has 25 hours.
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 24, 8, 0, 0, 0, LONDON);
        ZonedDateTime next = ReminderTimes.nextOccurrence(now, LocalTime.of(7, 0));
        assertEquals(LocalDateTime.of(2026, 10, 25, 7, 0), next.toLocalDateTime());
        assertEquals(Duration.ofHours(24), ReminderTimes.delayUntilNext(now, LocalTime.of(7, 0)));
    }

    @Test
    public void dstGapResolvesToValidTime() {
        // 01:30 does not exist on 29 Mar 2026 in the UK; java.time shifts it forward.
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, LONDON);
        ZonedDateTime next = ReminderTimes.nextOccurrence(now, LocalTime.of(1, 30));
        assertEquals(LocalDateTime.of(2026, 3, 29, 2, 30), next.toLocalDateTime());
    }
}
