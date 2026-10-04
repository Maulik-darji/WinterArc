package com.app.winterarc.domain.engine;

import androidx.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Week-column model for the contribution grid. */
public final class Heatmap {

    /** Visual intensity of one cell, ordered from least to most effort. */
    public enum CellLevel {
        /** Before the goal started or after it ended. */
        OUTSIDE,
        FUTURE,
        /** No activity: unscheduled, missed, or today not yet logged. */
        EMPTY,
        /** Planned rest or paused; drawn with a marker rather than intensity. */
        REST,
        PARTIAL,
        COMPLETED,
        EXCEEDED;

        public static CellLevel from(DayKind kind) {
            switch (kind) {
                case BEFORE_START:
                case AFTER_END:
                    return OUTSIDE;
                case FUTURE:
                    return FUTURE;
                case PAUSED:
                case SKIPPED:
                    return REST;
                case PARTIAL:
                    return PARTIAL;
                case COMPLETED:
                    return COMPLETED;
                case EXCEEDED:
                    return EXCEEDED;
                default:
                    return EMPTY;
            }
        }
    }

    public record Day(LocalDate date, DayKind kind, CellLevel level, boolean isToday) {}

    /** One column; {@code days} always has 7 entries starting on the locale's first weekday. */
    public record Week(LocalDate weekStart, List<Day> days, @Nullable LocalDate monthStart) {}

    public final List<Week> weeks;
    public final DayOfWeek firstDayOfWeek;

    private Heatmap(List<Week> weeks, DayOfWeek firstDayOfWeek) {
        this.weeks = Collections.unmodifiableList(weeks);
        this.firstDayOfWeek = firstDayOfWeek;
    }

    /**
     * Builds {@code weekCount} columns ending with the week containing {@code today}. Days after
     * today in the last column are {@link CellLevel#FUTURE}.
     */
    public static Heatmap build(LocalDate today, int weekCount, DayOfWeek firstDayOfWeek, GoalTimeline timeline) {
        if (weekCount <= 0) throw new IllegalArgumentException("weekCount must be positive");
        LocalDate lastWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek));
        LocalDate firstWeekStart = lastWeekStart.minusWeeks(weekCount - 1L);

        List<Week> weeks = new ArrayList<>(weekCount);
        int previousMonth = -1;
        for (int w = 0; w < weekCount; w++) {
            LocalDate weekStart = firstWeekStart.plusWeeks(w);
            List<Day> days = new ArrayList<>(7);
            LocalDate firstOfMonth = null;
            for (int i = 0; i < 7; i++) {
                LocalDate date = weekStart.plusDays(i);
                DayKind kind = timeline.classify(date, today);
                days.add(new Day(date, kind, CellLevel.from(kind), date.equals(today)));
                if (date.getDayOfMonth() == 1) firstOfMonth = date;
            }
            // A month label belongs to the column containing the 1st (or the very first column).
            LocalDate monthStart = null;
            if (w == 0) {
                monthStart = firstOfMonth != null ? firstOfMonth : weekStart;
            } else if (firstOfMonth != null && firstOfMonth.getMonthValue() != previousMonth) {
                monthStart = firstOfMonth;
            }
            if (monthStart != null) previousMonth = monthStart.getMonthValue();
            weeks.add(new Week(weekStart, days, monthStart));
        }
        return new Heatmap(weeks, firstDayOfWeek);
    }

    /** Number of week columns needed to cover the goal since {@code start}, clamped. */
    public static int weeksSince(LocalDate start, LocalDate today, int minimum, int maximum) {
        long days = Math.max(0, ChronoUnit.DAYS.between(start, today));
        int weeks = (int) (days / 7) + 2;
        return Math.max(minimum, Math.min(maximum, weeks));
    }
}
