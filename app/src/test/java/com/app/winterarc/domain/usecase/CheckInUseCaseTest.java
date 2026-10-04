package com.app.winterarc.domain.usecase;

import static com.app.winterarc.testing.TestGoals.km;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.AnalyticsEvent;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.CelebrationState;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.model.SkipReason;
import com.app.winterarc.domain.model.WeeklySchedule;
import com.app.winterarc.testing.FakeTimeProvider;
import com.app.winterarc.testing.InMemoryRepositories;
import com.app.winterarc.testing.TestGoals;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CheckInUseCaseTest {
    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    private final InMemoryRepositories.Goals goals = new InMemoryRepositories.Goals();
    private final InMemoryRepositories.Progress progress = new InMemoryRepositories.Progress();
    private final InMemoryRepositories.Milestones milestones = new InMemoryRepositories.Milestones();
    private final FakeTimeProvider time = new FakeTimeProvider(START);
    private final List<AnalyticsEvent> events = new ArrayList<>();
    private CheckInUseCase checkIn;

    @Before
    public void setUp() {
        Analytics analytics = events::add;
        checkIn = new CheckInUseCase(goals, progress, milestones, new InMemoryRepositories.DirectTransactions(), time, analytics);
    }

    private long progression(double start, double fin, double hop) {
        return goals.insertGoal(TestGoals.progression(km(start), km(fin), km(hop), START));
    }

    private Amount target(long id) {
        return goals.getGoal(id).currentTarget();
    }

    @Test
    public void completionAdvancesProgressionByOneHop() {
        long id = progression(1, 10, 1);
        CheckInUseCase.Result r = checkIn.complete(id, time.today());
        assertEquals(EntryStatus.COMPLETED, r.entry().status());
        assertEquals(km(1), r.entry().scheduledTarget());
        assertEquals(Integer.valueOf(1), r.entry().progressionLevel());
        assertEquals(km(2), target(id));
    }

    @Test
    public void missedDaysNeverAdvanceTheTarget() {
        long id = progression(1, 10, 1);
        checkIn.complete(id, time.today());
        time.advanceDays(5); // five calendar days pass with no check-in
        assertEquals(km(2), target(id));
    }

    @Test
    public void completingTwiceOnSameDayAdvancesOnlyOnce() {
        long id = progression(1, 10, 1);
        checkIn.complete(id, time.today());
        checkIn.logValue(id, time.today(), km(3), null);
        assertEquals(km(2), target(id));
        assertEquals(1, progress.entries.size());
    }

    @Test
    public void partialProgressDoesNotAdvance() {
        long id = progression(2, 10, 2);
        CheckInUseCase.Result r = checkIn.logValue(id, time.today(), km(1.5), "windy");
        assertEquals(EntryStatus.PARTIAL, r.entry().status());
        assertEquals("windy", r.entry().note());
        assertEquals(km(2), target(id));
    }

    @Test
    public void partialThenTopUpToTargetCompletesAndAdvances() {
        long id = progression(2, 10, 2);
        checkIn.logValue(id, time.today(), km(1), null);
        checkIn.logValue(id, time.today(), km(2), null);
        assertEquals(km(4), target(id));
    }

    @Test
    public void skipDoesNotAdvanceAndRecordsReason() {
        long id = progression(1, 10, 1);
        CheckInUseCase.Result r = checkIn.skip(id, time.today(), SkipReason.REST, null);
        assertEquals(EntryStatus.SKIPPED, r.entry().status());
        assertEquals(SkipReason.REST, r.entry().skipReason());
        assertEquals(km(1), target(id));
    }

    @Test
    public void exceedingTheTargetStillAdvancesOneHop() {
        long id = progression(1, 10, 1);
        CheckInUseCase.Result r = checkIn.logValue(id, time.today(), km(4), null);
        assertTrue(r.entry().isExceeded());
        assertEquals(km(2), target(id));
    }

    @Test
    public void undoRevertsAdvancement() {
        long id = progression(1, 10, 1);
        checkIn.complete(id, time.today());
        checkIn.undo(id, time.today());
        assertEquals(km(1), target(id));
        assertTrue(progress.entries.isEmpty());
    }

    @Test
    public void undoKeepsNote() {
        long id = progression(1, 10, 1);
        checkIn.logValue(id, time.today(), km(1), "felt great");
        CheckInUseCase.Result r = checkIn.undo(id, time.today());
        assertEquals(EntryStatus.NOTE_ONLY, r.entry().status());
        assertEquals("felt great", r.entry().note());
        assertEquals(km(1), target(id));
    }

    @Test
    public void loweringACompletedDayToPartialRevertsAdvancement() {
        long id = progression(1, 10, 1);
        checkIn.complete(id, time.today());
        checkIn.logValue(id, time.today(), km(0.5), null);
        assertEquals(km(1), target(id));
        assertFalse(progress.getEntry(id, time.today()).advancedProgression());
    }

    @Test
    public void fullJourneyReachesMilestoneWithoutExceedingFinal() {
        long id = progression(1, 10, 2); // 1,3,5,7,9,10
        for (int i = 0; i < 5; i++) {
            CheckInUseCase.Result r = checkIn.complete(id, time.today());
            assertFalse(r.milestoneReached());
            time.advanceDays(2);
        }
        assertEquals(km(10), target(id));
        CheckInUseCase.Result last = checkIn.complete(id, time.today());
        assertTrue(last.milestoneReached());
        assertEquals(km(10), target(id));
        assertEquals(CelebrationState.PENDING, milestones.getPending(id).celebrationState());
        assertTrue(events.stream().anyMatch(e -> e.name().equals(AnalyticsEvent.PROGRESSION_MILESTONE_REACHED)));
    }

    @Test
    public void undoingTheMilestoneDayRemovesThePendingMilestone() {
        long id = progression(9, 10, 1);
        checkIn.complete(id, time.today());
        time.advanceDays(1);
        assertTrue(checkIn.complete(id, time.today()).milestoneReached());
        checkIn.undo(id, time.today());
        assertNull(milestones.getPending(id));
        assertEquals(km(10), target(id));
    }

    @Test
    public void consistencyGoalsKeepTheirTarget() {
        long id = goals.insertGoal(TestGoals.consistency(Amount.whole(5), WeeklySchedule.EVERY_DAY, START));
        checkIn.logValue(id, time.today(), Amount.whole(7), null);
        assertEquals(Amount.whole(5), target(id));
        ProgressEntry e = progress.getEntry(id, time.today());
        assertNull(e.progressionLevel());
        assertTrue(e.isExceeded());
    }

    @Test
    public void decimalTargetCompletesOnExactValue() {
        long id = goals.insertGoal(TestGoals.consistency(km(2.3), WeeklySchedule.EVERY_DAY, START));
        CheckInUseCase.Result r = checkIn.logValue(id, time.today(), km(2.3), null);
        assertEquals(EntryStatus.COMPLETED, r.entry().status());
        r = checkIn.logValue(id, time.today(), km(2.2), null);
        assertEquals(EntryStatus.PARTIAL, r.entry().status());
    }

    @Test
    public void closedGoalsRejectCheckIns() {
        long id = progression(1, 10, 1);
        goals.updateGoal(goals.getGoal(id).toBuilder().status(GoalStatus.ARCHIVED).build());
        assertEquals(CheckInUseCase.Status.GOAL_NOT_OPEN, checkIn.complete(id, time.today()).status());
    }

    @Test
    public void noteOnlyEntryDoesNotCountAsActivity() {
        long id = progression(1, 10, 1);
        CheckInUseCase.Result r = checkIn.saveNote(id, time.today(), "Planning a long run");
        assertEquals(EntryStatus.NOTE_ONLY, r.entry().status());
        assertEquals(km(1), target(id));
        // Completing afterwards keeps the note and advances.
        checkIn.complete(id, time.today());
        assertEquals("Planning a long run", progress.getEntry(id, time.today()).note());
        assertEquals(km(2), target(id));
    }

    @Test
    public void analyticsNeverContainFreeText() {
        long id = progression(1, 10, 1);
        checkIn.logValue(id, time.today(), km(5), "private note about my day");
        for (AnalyticsEvent e : events) {
            for (String v : e.properties().values()) assertFalse(v.contains("private"));
        }
    }

    @Test
    public void missingGoalIsReported() {
        assertEquals(CheckInUseCase.Status.GOAL_NOT_FOUND, checkIn.complete(99, time.today()).status());
    }

    @SuppressWarnings("unused")
    private Goal goal(long id) {
        return goals.getGoal(id);
    }
}
