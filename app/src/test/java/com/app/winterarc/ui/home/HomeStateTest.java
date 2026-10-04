package com.app.winterarc.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.app.winterarc.domain.engine.DayKind;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.CelebrationState;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.model.WeeklySchedule;
import com.app.winterarc.testing.TestGoals;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public class HomeStateTest {
    private static final LocalDate FRI = LocalDate.of(2026, 10, 2);

    private static Goal goal(long id, WeeklySchedule schedule, GoalStatus status) {
        return TestGoals.consistency(Amount.whole(5), schedule, FRI.minusDays(30)).toBuilder()
                .id(id).uuid("g" + id).status(status).build();
    }

    @Test
    public void groupsTodayRestAndPausedAndCountsRemaining() {
        Goal pending = goal(1, WeeklySchedule.EVERY_DAY, GoalStatus.ACTIVE);
        Goal done = goal(2, WeeklySchedule.EVERY_DAY, GoalStatus.ACTIVE);
        Goal rest = goal(3, WeeklySchedule.of(DayOfWeek.MONDAY), GoalStatus.ACTIVE);
        Goal paused = goal(4, WeeklySchedule.EVERY_DAY, GoalStatus.PAUSED);
        ProgressEntry doneToday = new ProgressEntry(1, "e", 2, FRI, Amount.whole(5), Amount.whole(5),
                EntryStatus.COMPLETED, null, null, null, false, Instant.EPOCH, Instant.EPOCH);
        Milestone milestone = new Milestone(1, 1, Amount.whole(5), FRI, CelebrationState.PENDING, Instant.EPOCH);

        HomeViewModel.HomeState s = HomeViewModel.build(FRI, LocalTime.of(8, 0), DayOfWeek.MONDAY,
                List.of(paused, rest, done, pending), List.of(doneToday), Collections.emptyList(), List.of(milestone));

        assertEquals(HomeViewModel.Greeting.MORNING, s.greeting());
        assertEquals(2, s.scheduledToday()); // paused goals are not scheduled today
        assertEquals(1, s.remainingToday());
        // Order: pending today first, then done today, then rest, then paused.
        assertEquals(1, s.cards().get(0).goal().id());
        assertEquals(2, s.cards().get(1).goal().id());
        assertEquals(HomeViewModel.Section.REST, s.cards().get(2).section());
        assertEquals(HomeViewModel.Section.PAUSED, s.cards().get(3).section());
        assertEquals(DayKind.COMPLETED, s.cards().get(1).todayKind());
        assertTrue(s.cards().get(0).milestonePending());
        assertEquals(HomeViewModel.PREVIEW_WEEKS, s.cards().get(0).preview().weeks.size());
    }
}
