package com.app.winterarc.domain.engine;

import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.ProgressEntry;

import java.time.LocalDate;

/**
 * Streaks count consecutive completed days. Missed or partial scheduled days end a streak; rest
 * (skipped) days, pauses, unscheduled days and an unlogged "today" are neutral, so nobody is
 * penalised for planned recovery or for checking in later in the day.
 */
public final class StatsCalculator {

    private StatsCalculator() {}

    public record StreakSummary(int current, int longest) {}

    /**
     * @param completedDays  days whose target was met or exceeded, including unscheduled bonus days
     * @param completionRate completed scheduled days / scheduled days that counted (rest and paused
     *                       days excluded); 0..1
     * @param totalValue     sum of logged values for completed and partial days
     */
    public record GoalStats(int currentStreak, int longestStreak, int completedDays, int exceededDays,
                            int partialDays, int skippedDays, int missedDays, float completionRate,
                            Amount totalValue) {
        public static final GoalStats EMPTY = new GoalStats(0, 0, 0, 0, 0, 0, 0, 0f, Amount.ZERO);
    }

    public static StreakSummary streaks(LocalDate today, GoalTimeline timeline) {
        LocalDate first = timeline.firstTrackedDay();
        if (first.isAfter(today)) return new StreakSummary(0, 0);
        int longest = 0;
        int running = 0;
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            DayKind kind = timeline.classify(d, today);
            if (kind.isCompletion()) {
                running++;
                longest = Math.max(longest, running);
            } else if (kind.breaksStreak()) {
                running = 0;
            }
        }
        // Neutral days don't reset `running`, so it is the streak still alive today.
        return new StreakSummary(running, longest);
    }

    public static GoalStats stats(LocalDate today, GoalTimeline timeline) {
        LocalDate first = timeline.firstTrackedDay();
        if (first.isAfter(today)) return GoalStats.EMPTY;

        int completed = 0, exceeded = 0, partial = 0, skipped = 0, missed = 0;
        int scheduledCompleted = 0, scheduledCounted = 0;
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            DayKind kind = timeline.classify(d, today);
            boolean scheduled = timeline.schedule.isScheduled(d);
            switch (kind) {
                case EXCEEDED:
                    exceeded++;
                    // fall through: an exceeded day is also a completed day
                case COMPLETED:
                    completed++;
                    if (scheduled) {
                        scheduledCompleted++;
                        scheduledCounted++;
                    }
                    break;
                case PARTIAL:
                    partial++;
                    if (scheduled) scheduledCounted++;
                    break;
                case MISSED:
                    missed++;
                    scheduledCounted++;
                    break;
                case SKIPPED:
                    skipped++;
                    break;
                default:
                    break;
            }
        }

        Amount total = Amount.ZERO;
        for (ProgressEntry e : timeline.entriesByDate.values()) {
            if (e.status() == EntryStatus.COMPLETED || e.status() == EntryStatus.PARTIAL) {
                total = total.plus(e.actualValue());
            }
        }
        StreakSummary s = streaks(today, timeline);
        float rate = scheduledCounted == 0 ? 0f : scheduledCompleted / (float) scheduledCounted;
        return new GoalStats(s.current(), s.longest(), completed, exceeded, partial, skipped, missed, rate, total);
    }
}
