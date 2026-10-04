package com.app.winterarc.ui.common;

import com.app.winterarc.domain.engine.GoalTimeline;
import com.app.winterarc.domain.engine.Heatmap;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.model.WeeklySchedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic sample data for illustrations (onboarding) and Android Studio layout previews
 * (views call this when {@code isInEditMode()}). Never written to the database.
 */
public final class PreviewData {
    private PreviewData() {}

    /** A believable history whose density increases over time, like a habit taking hold. */
    public static Heatmap heatmap(LocalDate today, int weeks, DayOfWeek firstDay, long seed) {
        Random random = new Random(seed);
        Map<LocalDate, ProgressEntry> entries = new HashMap<>();
        LocalDate start = today.minusWeeks(weeks);
        long span = weeks * 7L;
        for (int i = 0; i < span; i++) {
            LocalDate d = start.plusDays(i);
            if (!d.isBefore(today)) break;
            double progress = i / (double) span;
            double r = random.nextDouble();
            EntryStatus status = null;
            long actual = 0;
            if (r < 0.35 + progress * 0.55) {
                status = EntryStatus.COMPLETED;
                actual = random.nextDouble() < 0.25 ? 7 : 5;
            } else if (r < 0.48 + progress * 0.45) {
                status = EntryStatus.PARTIAL;
                actual = 2;
            }
            if (status != null) {
                entries.put(d, new ProgressEntry(i, "preview", 0, d, Amount.whole(5), Amount.whole(actual), status,
                        null, null, null, false, Instant.EPOCH, Instant.EPOCH));
            }
        }
        GoalTimeline timeline = new GoalTimeline(start, null, WeeklySchedule.EVERY_DAY, entries, new ArrayList<>());
        return Heatmap.build(today, weeks, firstDay, timeline);
    }
}
