package com.app.winterarc.domain.engine;

import androidx.annotation.Nullable;

import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.PausePeriod;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.model.WeeklySchedule;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything needed to classify the days of one goal. */
public final class GoalTimeline {
    public final LocalDate startDate;
    @Nullable public final LocalDate endDate;
    public final WeeklySchedule schedule;
    public final Map<LocalDate, ProgressEntry> entriesByDate;
    public final List<PausePeriod> pauses;

    public GoalTimeline(LocalDate startDate, @Nullable LocalDate endDate, WeeklySchedule schedule,
                        Map<LocalDate, ProgressEntry> entriesByDate, List<PausePeriod> pauses) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.schedule = schedule;
        this.entriesByDate = Collections.unmodifiableMap(entriesByDate);
        this.pauses = Collections.unmodifiableList(pauses);
    }

    public static GoalTimeline of(Goal goal, List<ProgressEntry> entries, List<PausePeriod> pauses) {
        Map<LocalDate, ProgressEntry> map = new HashMap<>();
        for (ProgressEntry e : entries) map.put(e.date(), e);
        return new GoalTimeline(goal.startDate(), goal.endDate(), goal.schedule(), map, pauses);
    }

    /** Classifies {@code date} relative to {@code today}. */
    public DayKind classify(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) return DayKind.FUTURE;
        ProgressEntry entry = entriesByDate.get(date);
        // A logged entry always wins, so history stays visible even if the plan changed later.
        if (entry != null && entry.status() != EntryStatus.NOTE_ONLY) {
            switch (entry.status()) {
                case COMPLETED:
                    return entry.actualValue().greaterThan(entry.scheduledTarget())
                            ? DayKind.EXCEEDED : DayKind.COMPLETED;
                case PARTIAL:
                    return DayKind.PARTIAL;
                case SKIPPED:
                    return DayKind.SKIPPED;
                default:
                    break;
            }
        }
        if (date.isBefore(startDate)) return DayKind.BEFORE_START;
        if (endDate != null && date.isAfter(endDate)) return DayKind.AFTER_END;
        for (PausePeriod pause : pauses) {
            if (pause.contains(date)) return DayKind.PAUSED;
        }
        if (!schedule.isScheduled(date)) return DayKind.NOT_SCHEDULED;
        if (date.equals(today)) return DayKind.TODAY_PENDING;
        return DayKind.MISSED;
    }

    /** First day worth evaluating: the start date, or an earlier entry if one exists. */
    public LocalDate firstTrackedDay() {
        LocalDate first = startDate;
        for (LocalDate d : entriesByDate.keySet()) {
            if (d.isBefore(first)) first = d;
        }
        return first;
    }
}
