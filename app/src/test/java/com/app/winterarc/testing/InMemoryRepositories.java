package com.app.winterarc.testing;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.winterarc.domain.model.CelebrationState;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.domain.model.PausePeriod;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.repository.MilestoneRepository;
import com.app.winterarc.domain.repository.ProgressRepository;
import com.app.winterarc.domain.repository.ReminderScheduler;
import com.app.winterarc.domain.repository.TransactionRunner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Simple synchronous in-memory implementations for use-case tests. LiveData is not used here. */
public final class InMemoryRepositories {
    private InMemoryRepositories() {}

    public static final class Goals implements GoalRepository {
        public final Map<Long, Goal> rows = new LinkedHashMap<>();
        private long nextId = 1;

        @Override public LiveData<List<Goal>> observeGoals(Set<GoalStatus> statuses) { return new MutableLiveData<>(getGoals(statuses)); }
        @Override public LiveData<Goal> observeGoal(long id) { return new MutableLiveData<>(rows.get(id)); }
        @Override public Goal getGoal(long id) { return rows.get(id); }

        @Override
        public List<Goal> getGoals(Set<GoalStatus> statuses) {
            List<Goal> out = new ArrayList<>();
            for (Goal g : rows.values()) if (statuses.contains(g.status())) out.add(g);
            return out;
        }

        @Override
        public long insertGoal(Goal goal) {
            long id = nextId++;
            rows.put(id, goal.toBuilder().id(id).build());
            return id;
        }

        @Override public void updateGoal(Goal goal) { rows.put(goal.id(), goal); }
        @Override public void deleteGoal(long id) { rows.remove(id); }
    }

    public static final class Progress implements ProgressRepository {
        public final Map<Long, ProgressEntry> entries = new LinkedHashMap<>();
        public final Map<Long, PausePeriod> pauses = new LinkedHashMap<>();
        private long nextId = 1;

        @Override public LiveData<List<ProgressEntry>> observeEntries(long goalId) { return new MutableLiveData<>(getEntries(goalId)); }
        @Override public LiveData<List<ProgressEntry>> observeEntriesSince(LocalDate date) { return new MutableLiveData<>(new ArrayList<>(entries.values())); }

        @Override
        public List<ProgressEntry> getEntries(long goalId) {
            List<ProgressEntry> out = new ArrayList<>();
            for (ProgressEntry e : entries.values()) if (e.goalId() == goalId) out.add(e);
            out.sort(Comparator.comparing(ProgressEntry::date));
            return out;
        }

        @Override
        public ProgressEntry getEntry(long goalId, LocalDate date) {
            for (ProgressEntry e : entries.values()) if (e.goalId() == goalId && e.date().equals(date)) return e;
            return null;
        }

        @Override
        public long upsertEntry(ProgressEntry entry) {
            long id = entry.id() == 0 ? nextId++ : entry.id();
            entries.put(id, entry.withId(id));
            return id;
        }

        @Override public void deleteEntry(long id) { entries.remove(id); }
        @Override public LiveData<List<PausePeriod>> observePauses(long goalId) { return new MutableLiveData<>(getPauses(goalId)); }
        @Override public LiveData<List<PausePeriod>> observeAllPauses() { return new MutableLiveData<>(new ArrayList<>(pauses.values())); }

        @Override
        public List<PausePeriod> getPauses(long goalId) {
            List<PausePeriod> out = new ArrayList<>();
            for (PausePeriod p : pauses.values()) if (p.goalId() == goalId) out.add(p);
            return out;
        }

        @Override
        public PausePeriod getOpenPause(long goalId) {
            for (PausePeriod p : pauses.values()) if (p.goalId() == goalId && p.endDate() == null) return p;
            return null;
        }

        @Override
        public long insertPause(PausePeriod pause) {
            long id = nextId++;
            pauses.put(id, new PausePeriod(id, pause.goalId(), pause.startDate(), pause.endDate()));
            return id;
        }

        @Override public void updatePause(PausePeriod pause) { pauses.put(pause.id(), pause); }
        @Override public void deletePause(long id) { pauses.remove(id); }
    }

    public static final class Milestones implements MilestoneRepository {
        public final Map<Long, Milestone> rows = new LinkedHashMap<>();
        private long nextId = 1;

        @Override public LiveData<List<Milestone>> observeMilestones(long goalId) { return new MutableLiveData<>(forGoal(goalId)); }
        @Override public LiveData<List<Milestone>> observePending() { return new MutableLiveData<>(new ArrayList<>()); }

        public List<Milestone> forGoal(long goalId) {
            List<Milestone> out = new ArrayList<>();
            for (Milestone m : rows.values()) if (m.goalId() == goalId) out.add(m);
            return out;
        }

        @Override
        public Milestone getPending(long goalId) {
            for (Milestone m : rows.values()) {
                if (m.goalId() == goalId && m.celebrationState() == CelebrationState.PENDING) return m;
            }
            return null;
        }

        @Override
        public Milestone getForDate(long goalId, LocalDate date) {
            for (Milestone m : rows.values()) if (m.goalId() == goalId && m.achievedDate().equals(date)) return m;
            return null;
        }

        @Override
        public long insert(Milestone m) {
            long id = nextId++;
            rows.put(id, new Milestone(id, m.goalId(), m.targetValue(), m.achievedDate(), m.celebrationState(), m.createdAt()));
            return id;
        }

        @Override public void update(Milestone m) { rows.put(m.id(), m); }
        @Override public void delete(long id) { rows.remove(id); }
    }

    public static final class DirectTransactions implements TransactionRunner {
        @Override public <T> T run(Supplier<T> block) { return block.get(); }
    }

    public static final class RecordingReminders implements ReminderScheduler {
        public final List<Long> scheduled = new ArrayList<>();
        public final List<Long> cancelled = new ArrayList<>();

        @Override public void schedule(Goal goal) { scheduled.add(goal.id()); }
        @Override public void cancel(long goalId) { cancelled.add(goalId); }
        @Override public void rescheduleAll() { }
    }
}
