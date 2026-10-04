package com.app.winterarc.domain.engine;

import static org.junit.Assert.assertEquals;

import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.PausePeriod;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.model.SkipReason;
import com.app.winterarc.domain.model.WeeklySchedule;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsCalculatorTest {
    // 2026-09-28 is a Monday.
    private static final LocalDate MON = LocalDate.of(2026, 9, 28);
    private static final Amount TARGET = Amount.whole(5);

    private final Map<LocalDate, ProgressEntry> entries = new HashMap<>();
    private final List<PausePeriod> pauses = new ArrayList<>();

    private void log(LocalDate date, EntryStatus status, Amount actual) {
        entries.put(date, new ProgressEntry(entries.size() + 1, "u", 1, date, TARGET, actual, status,
                status == EntryStatus.SKIPPED ? SkipReason.REST : null, null, null, false, Instant.EPOCH, Instant.EPOCH));
    }

    private void done(LocalDate date) {
        log(date, EntryStatus.COMPLETED, TARGET);
    }

    private GoalTimeline timeline(WeeklySchedule schedule, LocalDate start) {
        return new GoalTimeline(start, null, schedule, entries, pauses);
    }

    @Test
    public void consecutiveCompletionsFormAStreak() {
        for (int i = 0; i < 5; i++) done(MON.plusDays(i));
        StatsCalculator.StreakSummary s = StatsCalculator.streaks(MON.plusDays(4), timeline(WeeklySchedule.EVERY_DAY, MON));
        assertEquals(5, s.current());
        assertEquals(5, s.longest());
    }

    @Test
    public void unloggedTodayDoesNotBreakStreak() {
        for (int i = 0; i < 3; i++) done(MON.plusDays(i));
        StatsCalculator.StreakSummary s = StatsCalculator.streaks(MON.plusDays(3), timeline(WeeklySchedule.EVERY_DAY, MON));
        assertEquals(3, s.current());
    }

    @Test
    public void missedScheduledDayResetsCurrentButKeepsLongest() {
        for (int i = 0; i < 4; i++) done(MON.plusDays(i));
        // day 4 missed
        done(MON.plusDays(5));
        done(MON.plusDays(6));
        StatsCalculator.StreakSummary s = StatsCalculator.streaks(MON.plusDays(6), timeline(WeeklySchedule.EVERY_DAY, MON));
        assertEquals(2, s.current());
        assertEquals(4, s.longest());
    }

    @Test
    public void unscheduledDaysAreNeutral() {
        WeeklySchedule mwf = WeeklySchedule.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        done(MON);
        done(MON.plusDays(2));
        done(MON.plusDays(4));
        done(MON.plusDays(7)); // next Monday
        StatsCalculator.StreakSummary s = StatsCalculator.streaks(MON.plusDays(8), timeline(mwf, MON));
        assertEquals(4, s.current());
    }

    @Test
    public void restDaysAndPausesAreNeutral() {
        done(MON);
        log(MON.plusDays(1), EntryStatus.SKIPPED, Amount.ZERO);
        done(MON.plusDays(2));
        pauses.add(new PausePeriod(1, 1, MON.plusDays(3), MON.plusDays(5)));
        done(MON.plusDays(6));
        StatsCalculator.GoalStats stats = StatsCalculator.stats(MON.plusDays(6), timeline(WeeklySchedule.EVERY_DAY, MON));
        assertEquals(3, stats.currentStreak());
        assertEquals(1, stats.skippedDays());
        assertEquals(0, stats.missedDays());
        assertEquals(1f, stats.completionRate(), 0.0001f);
    }

    @Test
    public void partialDayEndsStreak() {
        done(MON);
        done(MON.plusDays(1));
        log(MON.plusDays(2), EntryStatus.PARTIAL, Amount.whole(2));
        done(MON.plusDays(3));
        StatsCalculator.StreakSummary s = StatsCalculator.streaks(MON.plusDays(3), timeline(WeeklySchedule.EVERY_DAY, MON));
        assertEquals(1, s.current());
        assertEquals(2, s.longest());
    }

    @Test
    public void statsTotalsAndRate() {
        done(MON);                                                   // completed
        log(MON.plusDays(1), EntryStatus.COMPLETED, Amount.whole(7)); // exceeded
        log(MON.plusDays(2), EntryStatus.PARTIAL, new Amount(2_500)); // partial
        // MON+3 missed
        StatsCalculator.GoalStats s = StatsCalculator.stats(MON.plusDays(4), timeline(WeeklySchedule.EVERY_DAY, MON));
        assertEquals(2, s.completedDays());
        assertEquals(1, s.exceededDays());
        assertEquals(1, s.partialDays());
        assertEquals(1, s.missedDays());
        assertEquals(new Amount(14_500), s.totalValue());
        // 2 completed of 4 counted scheduled days (today pending is excluded)
        assertEquals(0.5f, s.completionRate(), 0.0001f);
    }

    @Test
    public void emptyHistoryHasZeroStats() {
        StatsCalculator.GoalStats s = StatsCalculator.stats(MON, timeline(WeeklySchedule.EVERY_DAY, MON));
        assertEquals(0, s.currentStreak());
        assertEquals(0, s.completedDays());
        assertEquals(0f, s.completionRate(), 0f);
    }

    @Test
    public void classifierMapsEveryState() {
        WeeklySchedule weekdays = WeeklySchedule.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        done(MON);
        log(MON.plusDays(1), EntryStatus.COMPLETED, Amount.whole(6));
        log(MON.plusDays(2), EntryStatus.PARTIAL, Amount.whole(1));
        GoalTimeline t = timeline(weekdays, MON);
        LocalDate today = MON.plusDays(4); // Friday
        assertEquals(DayKind.BEFORE_START, t.classify(MON.minusDays(1), today));
        assertEquals(DayKind.COMPLETED, t.classify(MON, today));
        assertEquals(DayKind.EXCEEDED, t.classify(MON.plusDays(1), today));
        assertEquals(DayKind.PARTIAL, t.classify(MON.plusDays(2), today));
        assertEquals(DayKind.MISSED, t.classify(MON.plusDays(3), today));
        assertEquals(DayKind.TODAY_PENDING, t.classify(today, today));
        assertEquals(DayKind.FUTURE, t.classify(today.plusDays(1), today));
        assertEquals(DayKind.NOT_SCHEDULED, t.classify(MON.minusDays(1).plusDays(7), MON.plusDays(10)));
    }
}
