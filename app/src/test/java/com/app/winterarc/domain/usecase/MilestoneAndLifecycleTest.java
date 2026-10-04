package com.app.winterarc.domain.usecase;

import static com.app.winterarc.testing.TestGoals.km;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.domain.engine.DayKind;
import com.app.winterarc.domain.engine.GoalTimeline;
import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.CelebrationState;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalDraft;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.domain.model.TrackingMode;
import com.app.winterarc.testing.FakeTimeProvider;
import com.app.winterarc.testing.InMemoryRepositories;
import com.app.winterarc.testing.TestGoals;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;

public class MilestoneAndLifecycleTest {
    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    private final InMemoryRepositories.Goals goals = new InMemoryRepositories.Goals();
    private final InMemoryRepositories.Progress progress = new InMemoryRepositories.Progress();
    private final InMemoryRepositories.Milestones milestones = new InMemoryRepositories.Milestones();
    private final InMemoryRepositories.RecordingReminders reminders = new InMemoryRepositories.RecordingReminders();
    private final FakeTimeProvider time = new FakeTimeProvider(START);
    private CheckInUseCase checkIn;
    private MilestoneUseCase milestone;
    private GoalUseCases lifecycle;

    @Before
    public void setUp() {
        Analytics analytics = Analytics.NO_OP;
        InMemoryRepositories.DirectTransactions tx = new InMemoryRepositories.DirectTransactions();
        checkIn = new CheckInUseCase(goals, progress, milestones, tx, time, analytics);
        milestone = new MilestoneUseCase(goals, milestones, tx, reminders, time, analytics);
        lifecycle = new GoalUseCases(goals, progress, tx, reminders, time, analytics);
    }

    private long reachMilestone() {
        long id = goals.insertGoal(TestGoals.progression(km(8), km(10), km(2), START));
        checkIn.complete(id, time.today());
        time.advanceDays(1);
        assertTrue(checkIn.complete(id, time.today()).milestoneReached());
        return id;
    }

    @Test
    public void maintainConvertsToConsistencyAtFinalTarget() {
        long id = reachMilestone();
        assertTrue(milestone.maintain(id));
        Goal g = goals.getGoal(id);
        assertEquals(TrackingMode.CONSISTENCY, g.trackingMode());
        assertNull(g.plan());
        assertEquals(km(10), g.currentTarget());
        assertEquals(2, progress.getEntries(id).size()); // history kept
        Milestone m = milestones.forGoal(id).get(0);
        assertEquals(CelebrationState.MAINTAINED, m.celebrationState());
    }

    @Test
    public void continueProgressingSetsNewFinalAndNextStep() {
        long id = reachMilestone();
        assertTrue(milestone.continueProgressing(id, km(15), km(1)));
        Goal g = goals.getGoal(id);
        assertEquals(TrackingMode.PROGRESSION, g.trackingMode());
        assertEquals(km(15), g.plan().finalTarget());
        assertEquals(km(11), g.currentTarget());
        assertEquals(CelebrationState.CONTINUED, milestones.forGoal(id).get(0).celebrationState());
    }

    @Test
    public void continueKeepsOldHopWhenNoneGiven() {
        long id = reachMilestone();
        assertTrue(milestone.continueProgressing(id, km(20), null));
        assertEquals(km(12), goals.getGoal(id).currentTarget());
    }

    @Test
    public void continueRejectsNonIncreasingFinal() {
        long id = reachMilestone();
        assertFalse(milestone.continueProgressing(id, km(10), km(1)));
        assertNotNull(milestones.getPending(id));
    }

    @Test
    public void markCompleteArchivesWithHistory() {
        long id = reachMilestone();
        assertTrue(milestone.markComplete(id));
        Goal g = goals.getGoal(id);
        assertEquals(GoalStatus.COMPLETED, g.status());
        assertEquals(time.today(), g.endDate());
        assertEquals(2, progress.getEntries(id).size());
        assertTrue(reminders.cancelled.contains(id));
    }

    @Test
    public void pausedDaysAreNotMissedAndResumeKeepsTarget() {
        long id = goals.insertGoal(TestGoals.progression(km(1), km(10), km(1), START));
        checkIn.complete(id, time.today());
        time.advanceDays(1);
        lifecycle.pause(id);
        assertEquals(GoalStatus.PAUSED, goals.getGoal(id).status());
        time.advanceDays(4);
        lifecycle.resume(id);
        Goal g = goals.getGoal(id);
        assertEquals(GoalStatus.ACTIVE, g.status());
        assertEquals(km(2), g.currentTarget());
        GoalTimeline t = GoalTimeline.of(g, progress.getEntries(id), progress.getPauses(id));
        for (int i = 1; i <= 4; i++) {
            assertEquals(DayKind.PAUSED, t.classify(START.plusDays(i), time.today()));
        }
        assertEquals(DayKind.TODAY_PENDING, t.classify(time.today(), time.today()));
    }

    @Test
    public void pauseAndResumeSameDayLeavesNoPausePeriod() {
        long id = goals.insertGoal(TestGoals.progression(km(1), km(10), km(1), START));
        lifecycle.pause(id);
        lifecycle.resume(id);
        assertTrue(progress.pauses.isEmpty());
    }

    @Test
    public void restoringArchivedGoalCoversGapWithPause() {
        long id = goals.insertGoal(TestGoals.progression(km(1), km(10), km(1), START));
        lifecycle.archive(id);
        time.advanceDays(10);
        lifecycle.restore(id);
        Goal g = goals.getGoal(id);
        assertEquals(GoalStatus.ACTIVE, g.status());
        assertNull(g.endDate());
        GoalTimeline t = GoalTimeline.of(g, progress.getEntries(id), progress.getPauses(id));
        assertEquals(DayKind.PAUSED, t.classify(START.plusDays(5), time.today()));
    }

    @Test
    public void editingPlanPreservesHistoryAndCurrentProgress() {
        long id = goals.insertGoal(TestGoals.progression(km(1), km(10), km(1), START));
        for (int i = 0; i < 4; i++) {
            checkIn.complete(id, time.today());
            time.advanceDays(1);
        }
        assertEquals(km(5), goals.getGoal(id).currentTarget());
        GoalDraft draft = GoalDraft.fromGoal(goals.getGoal(id));
        draft.title = "Longer plan";
        draft.finalTarget = km(21.1);
        draft.hop = km(0.5);
        assertTrue(lifecycle.update(id, draft).isSaved());
        Goal g = goals.getGoal(id);
        assertEquals(km(5), g.currentTarget());
        assertEquals(km(21.1), g.plan().finalTarget());
        // Historical entries keep their original targets.
        assertEquals(km(1), progress.getEntries(id).get(0).scheduledTarget());
        assertEquals(km(4), progress.getEntries(id).get(3).scheduledTarget());
    }

    @Test
    public void createValidatesAndSchedulesReminder() {
        GoalDraft draft = GoalDraft.forActivity(ActivityType.WALKING, START);
        assertFalse(lifecycle.create(draft).isSaved()); // no title
        draft.title = "Daily walking";
        GoalUseCases.SaveResult r = lifecycle.create(draft);
        assertTrue(r.isSaved());
        assertTrue(reminders.scheduled.contains(r.goalId()));
        assertEquals(new ArrayList<>(goals.rows.keySet()).get(0).longValue(), r.goalId());
    }
}
