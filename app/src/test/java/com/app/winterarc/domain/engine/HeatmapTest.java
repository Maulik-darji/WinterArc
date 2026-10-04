package com.app.winterarc.domain.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.model.WeeklySchedule;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class HeatmapTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2); // Friday

    private static ProgressEntry entry(LocalDate d, EntryStatus status, long actual) {
        return new ProgressEntry(1, "u", 1, d, Amount.whole(5), Amount.whole(actual), status, null, null, null,
                false, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    public void buildsYearOfWeeksEndingThisWeek() {
        Map<LocalDate, ProgressEntry> map = new HashMap<>();
        map.put(TODAY.minusDays(1), entry(TODAY.minusDays(1), EntryStatus.COMPLETED, 5));
        map.put(TODAY.minusDays(2), entry(TODAY.minusDays(2), EntryStatus.COMPLETED, 8));
        map.put(TODAY.minusDays(3), entry(TODAY.minusDays(3), EntryStatus.PARTIAL, 2));
        GoalTimeline t = new GoalTimeline(TODAY.minusDays(200), null, WeeklySchedule.EVERY_DAY, map, new ArrayList<>());

        Heatmap h = Heatmap.build(TODAY, 53, DayOfWeek.MONDAY, t);
        assertEquals(53, h.weeks.size());
        Heatmap.Week last = h.weeks.get(52);
        assertEquals(DayOfWeek.MONDAY, last.weekStart().getDayOfWeek());
        // Friday is index 4; Sat/Sun are future.
        assertTrue(last.days().get(4).isToday());
        assertEquals(Heatmap.CellLevel.FUTURE, last.days().get(5).level());
        assertEquals(Heatmap.CellLevel.COMPLETED, last.days().get(3).level());
        assertEquals(Heatmap.CellLevel.EXCEEDED, last.days().get(2).level());
        assertEquals(Heatmap.CellLevel.PARTIAL, last.days().get(1).level());
        assertEquals(Heatmap.CellLevel.OUTSIDE, h.weeks.get(0).days().get(0).level());
        assertNotNull(h.weeks.get(0).monthStart());
    }

    @Test
    public void sundayFirstLocalesStartColumnsOnSunday() {
        GoalTimeline t = new GoalTimeline(TODAY, null, WeeklySchedule.EVERY_DAY, new HashMap<>(), new ArrayList<>());
        Heatmap h = Heatmap.build(TODAY, 4, DayOfWeek.SUNDAY, t);
        for (Heatmap.Week w : h.weeks) assertEquals(DayOfWeek.SUNDAY, w.weekStart().getDayOfWeek());
    }

    @Test
    public void monthLabelsAppearOncePerMonth() {
        GoalTimeline t = new GoalTimeline(TODAY.minusYears(1), null, WeeklySchedule.EVERY_DAY, new HashMap<>(), new ArrayList<>());
        Heatmap h = Heatmap.build(TODAY, 53, DayOfWeek.MONDAY, t);
        int labels = 0;
        for (Heatmap.Week w : h.weeks) if (w.monthStart() != null) labels++;
        assertTrue("labels=" + labels, labels >= 12 && labels <= 14);
    }
}
