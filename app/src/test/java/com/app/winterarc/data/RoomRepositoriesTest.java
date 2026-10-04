package com.app.winterarc.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.app.winterarc.data.db.WinterArcDatabase;
import com.app.winterarc.data.repository.RoomGoalRepository;
import com.app.winterarc.data.repository.RoomMilestoneRepository;
import com.app.winterarc.data.repository.RoomProgressRepository;
import com.app.winterarc.data.repository.RoomTransactionRunner;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.usecase.CheckInUseCase;
import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.testing.FakeTimeProvider;
import com.app.winterarc.testing.TestGoals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;

/** Real Room (in-memory, on Robolectric) behind the repository interfaces and use cases. */
@RunWith(AndroidJUnit4.class)
@Config(application = Application.class)
public class RoomRepositoriesTest {
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private WinterArcDatabase db;
    private RoomGoalRepository goals;
    private RoomProgressRepository progress;
    private RoomMilestoneRepository milestones;
    private final FakeTimeProvider time = new FakeTimeProvider(START);

    @Before
    public void setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WinterArcDatabase.class)
                .allowMainThreadQueries()
                .build();
        goals = new RoomGoalRepository(db.goalDao());
        progress = new RoomProgressRepository(db.progressEntryDao(), db.pausePeriodDao());
        milestones = new RoomMilestoneRepository(db.milestoneDao());
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void goalRoundTripsEveryField() {
        Goal goal = TestGoals.progression(TestGoals.km(2.5), TestGoals.km(21.1), TestGoals.km(0.5), START)
                .toBuilder()
                .description("desc")
                .reminder(new com.app.winterarc.domain.model.ReminderConfig(true, LocalTime.of(6, 45)))
                .build();
        long id = goals.insertGoal(goal);
        Goal loaded = goals.getGoal(id);
        assertEquals(goal.toBuilder().id(id).build(), loaded);
        assertEquals(new Amount(21_100), loaded.plan().finalTarget());
    }

    @Test
    public void checkInPersistsAndAdvancesThroughRealDatabase() {
        long id = goals.insertGoal(TestGoals.progression(TestGoals.km(1), TestGoals.km(3), TestGoals.km(1), START));
        CheckInUseCase checkIn = new CheckInUseCase(goals, progress, milestones, new RoomTransactionRunner(db), time,
                Analytics.NO_OP);
        checkIn.complete(id, START);
        checkIn.complete(id, START.plusDays(1));
        assertEquals(TestGoals.km(3), goals.getGoal(id).currentTarget());
        CheckInUseCase.Result r = checkIn.complete(id, START.plusDays(2));
        assertTrue(r.milestoneReached());
        assertEquals(3, progress.getEntries(id).size());
        assertEquals(EntryStatus.COMPLETED, progress.getEntry(id, START).status());
        assertEquals(TestGoals.km(1), progress.getEntry(id, START).scheduledTarget());
        assertEquals(id, milestones.getPending(id).goalId());
    }

    @Test
    public void upsertKeepsOneEntryPerDay() {
        long id = goals.insertGoal(TestGoals.consistency(Amount.whole(5), com.app.winterarc.domain.model.WeeklySchedule.EVERY_DAY, START));
        CheckInUseCase checkIn = new CheckInUseCase(goals, progress, milestones, new RoomTransactionRunner(db), time,
                Analytics.NO_OP);
        checkIn.logValue(id, START, Amount.whole(2), null);
        checkIn.logValue(id, START, Amount.whole(6), "more");
        assertEquals(1, progress.getEntries(id).size());
        ProgressEntry e = progress.getEntry(id, START);
        assertEquals(Amount.whole(6), e.actualValue());
        assertTrue(e.isExceeded());
    }

    @Test
    public void deletingGoalCascadesToHistory() {
        long id = goals.insertGoal(TestGoals.consistency(Amount.whole(5), com.app.winterarc.domain.model.WeeklySchedule.EVERY_DAY, START));
        new CheckInUseCase(goals, progress, milestones, new RoomTransactionRunner(db), time, Analytics.NO_OP)
                .complete(id, START);
        goals.deleteGoal(id);
        assertNull(goals.getGoal(id));
        assertTrue(progress.getEntries(id).isEmpty());
    }

    @Test
    public void statusFilterWorks() {
        long a = goals.insertGoal(TestGoals.consistency(Amount.whole(5), com.app.winterarc.domain.model.WeeklySchedule.EVERY_DAY, START));
        long b = goals.insertGoal(TestGoals.consistency(Amount.whole(5), com.app.winterarc.domain.model.WeeklySchedule.EVERY_DAY, START)
                .toBuilder().uuid("other").status(GoalStatus.ARCHIVED).build());
        assertEquals(a, goals.getGoals(EnumSet.of(GoalStatus.ACTIVE)).get(0).id());
        assertEquals(b, goals.getGoals(EnumSet.of(GoalStatus.ARCHIVED)).get(0).id());
    }
}
