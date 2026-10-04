package com.app.winterarc.domain.usecase;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.AnalyticsEvent;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.domain.engine.ProgressionEngine;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.CelebrationState;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.model.ProgressionPlan;
import com.app.winterarc.domain.model.SkipReason;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.repository.MilestoneRepository;
import com.app.winterarc.domain.repository.ProgressRepository;
import com.app.winterarc.domain.repository.TransactionRunner;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import javax.inject.Inject;

/**
 * Daily check-in rules.
 * <ul>
 *   <li>One entry per goal per day; logging again the same day replaces that day's value.</li>
 *   <li>A progression target advances at most once per day, and only when the logged value meets
 *       the target. A missed day, a partial log or a skip never advances it.</li>
 *   <li>Lowering or undoing a completed day reverts its advancement and any milestone it
 *       created.</li>
 * </ul>
 */
public class CheckInUseCase {

    public enum Status { SAVED, GOAL_NOT_FOUND, GOAL_NOT_OPEN }

    /**
     * @param entry      the day's entry after the operation, or null if it was removed
     * @param nextTarget the goal's target for its next attempt
     */
    public record Result(Status status, @Nullable ProgressEntry entry, boolean milestoneReached,
                         @Nullable Amount nextTarget) {
        static Result of(Status status) {
            return new Result(status, null, false, null);
        }
    }

    private final GoalRepository goals;
    private final ProgressRepository progress;
    private final MilestoneRepository milestones;
    private final TransactionRunner transactions;
    private final TimeProvider time;
    private final Analytics analytics;

    @Inject
    public CheckInUseCase(GoalRepository goals, ProgressRepository progress, MilestoneRepository milestones,
                          TransactionRunner transactions, TimeProvider time, Analytics analytics) {
        this.goals = goals;
        this.progress = progress;
        this.milestones = milestones;
        this.transactions = transactions;
        this.time = time;
        this.analytics = analytics;
    }

    /** Marks the day's target as done (keeping a higher value if one was already logged). */
    @WorkerThread
    public Result complete(long goalId, LocalDate date) {
        Goal goal = goals.getGoal(goalId);
        if (goal == null) return Result.of(Status.GOAL_NOT_FOUND);
        ProgressEntry existing = progress.getEntry(goalId, date);
        Amount target = targetFor(goal, existing);
        Amount actual = existing != null ? Amount.max(target, existing.actualValue()) : target;
        return record(goalId, date, actual, null, null);
    }

    /**
     * Logs the day's total value. Meeting the target completes the day; less is partial progress.
     * A null note keeps any existing note. Logging zero with no note undoes the day.
     */
    @WorkerThread
    public Result logValue(long goalId, LocalDate date, Amount actual, @Nullable String note) {
        if (actual.isZero() && (note == null || note.isBlank())) return undo(goalId, date);
        return record(goalId, date, actual, note, null);
    }

    @WorkerThread
    public Result skip(long goalId, LocalDate date, SkipReason reason, @Nullable String note) {
        return record(goalId, date, Amount.ZERO, note, reason);
    }

    /** Saves a note without changing the day's progress. A blank note clears it. */
    @WorkerThread
    public Result saveNote(long goalId, LocalDate date, String note) {
        return transactions.run(() -> {
            Goal goal = goals.getGoal(goalId);
            if (goal == null) return Result.of(Status.GOAL_NOT_FOUND);
            ProgressEntry existing = progress.getEntry(goalId, date);
            String trimmed = trimToNull(note);
            Instant now = time.now();
            ProgressEntry saved;
            if (existing != null && existing.status() == EntryStatus.NOTE_ONLY && trimmed == null) {
                progress.deleteEntry(existing.id());
                saved = null;
            } else if (existing != null) {
                saved = existing.withNote(trimmed, now);
                progress.upsertEntry(saved);
            } else if (trimmed == null) {
                saved = null;
            } else {
                saved = newEntry(goal, date, goal.currentTarget(), Amount.ZERO, EntryStatus.NOTE_ONLY, null, trimmed);
                saved = saved.withId(progress.upsertEntry(saved));
            }
            return new Result(Status.SAVED, saved, false, goal.currentTarget());
        });
    }

    /** Reverts the day's check-in. A note, if present, is kept. */
    @WorkerThread
    public Result undo(long goalId, LocalDate date) {
        return transactions.run(() -> {
            Goal goal = goals.getGoal(goalId);
            if (goal == null) return Result.of(Status.GOAL_NOT_FOUND);
            ProgressEntry existing = progress.getEntry(goalId, date);
            if (existing == null) return new Result(Status.SAVED, null, false, goal.currentTarget());
            if (existing.advancedProgression()) goal = revertAdvancement(goal, existing);
            ProgressEntry kept = null;
            if (existing.note() != null && !existing.note().isBlank()) {
                kept = existing.withProgress(existing.scheduledTarget(), Amount.ZERO, EntryStatus.NOTE_ONLY,
                        null, existing.note(), existing.progressionLevel(), false, time.now());
                progress.upsertEntry(kept);
            } else {
                progress.deleteEntry(existing.id());
            }
            return new Result(Status.SAVED, kept, false, goal.currentTarget());
        });
    }

    private Result record(long goalId, LocalDate date, Amount actual, @Nullable String note,
                          @Nullable SkipReason skipReason) {
        Result result = transactions.run(() -> {
            Goal goal = goals.getGoal(goalId);
            if (goal == null) return Result.of(Status.GOAL_NOT_FOUND);
            if (!goal.status().isOpen()) return Result.of(Status.GOAL_NOT_OPEN);
            ProgressEntry existing = progress.getEntry(goalId, date);
            Amount target = targetFor(goal, existing);

            EntryStatus status;
            if (skipReason != null) {
                status = EntryStatus.SKIPPED;
            } else if (actual.isPositive() && actual.atLeast(target)) {
                status = EntryStatus.COMPLETED;
            } else if (actual.isPositive()) {
                status = EntryStatus.PARTIAL;
            } else {
                status = EntryStatus.NOTE_ONLY;
            }

            boolean advanced = existing != null && existing.advancedProgression();
            boolean milestoneReached = false;
            ProgressionPlan plan = goal.plan();
            if (status == EntryStatus.COMPLETED && !advanced && goal.isProgression()
                    && target.equals(goal.currentTarget())) {
                ProgressionEngine.CompletionOutcome outcome = ProgressionEngine.onCompleted(target, plan);
                goal = goal.toBuilder().currentTarget(outcome.nextTarget()).updatedAt(time.now()).build();
                goals.updateGoal(goal);
                advanced = true;
                if (outcome.milestoneReached() && milestones.getPending(goalId) == null) {
                    milestones.insert(new Milestone(0, goalId, plan.finalTarget(), date,
                            CelebrationState.PENDING, time.now()));
                    milestoneReached = true;
                }
            } else if (status != EntryStatus.COMPLETED && advanced) {
                goal = revertAdvancement(goal, existing);
                advanced = false;
            }

            Integer level = plan != null && ProgressionEngine.isValid(plan) ? ProgressionEngine.level(target, plan) : null;
            String newNote = trimToNull(note);
            if (newNote == null && existing != null) newNote = existing.note();
            ProgressEntry base = existing != null ? existing : newEntry(goal, date, target, actual, status, skipReason, newNote);
            ProgressEntry entry = base.withProgress(target, actual, status, skipReason, newNote, level, advanced, time.now());
            long id = progress.upsertEntry(entry);
            return new Result(Status.SAVED, entry.withId(existing != null ? existing.id() : id),
                    milestoneReached, goal.currentTarget());
        });
        if (result.status() == Status.SAVED) trackCheckIn(goalId, result);
        return result;
    }

    /** The target that applies to an existing entry's day, otherwise the goal's current target. */
    private static Amount targetFor(Goal goal, @Nullable ProgressEntry existing) {
        if (existing != null && existing.status() != EntryStatus.NOTE_ONLY) return existing.scheduledTarget();
        return goal.currentTarget();
    }

    private Goal revertAdvancement(Goal goal, ProgressEntry entry) {
        Milestone milestone = milestones.getForDate(goal.id(), entry.date());
        if (milestone != null && milestone.celebrationState() == CelebrationState.PENDING) {
            milestones.delete(milestone.id());
        }
        if (!goal.isProgression()) return goal;
        Goal reverted = goal.toBuilder()
                .currentTarget(ProgressionEngine.rebase(entry.scheduledTarget(), goal.plan()))
                .updatedAt(time.now())
                .build();
        goals.updateGoal(reverted);
        return reverted;
    }

    private ProgressEntry newEntry(Goal goal, LocalDate date, Amount target, Amount actual, EntryStatus status,
                                   @Nullable SkipReason reason, @Nullable String note) {
        Instant now = time.now();
        ProgressionPlan plan = goal.plan();
        Integer level = plan != null && ProgressionEngine.isValid(plan) ? ProgressionEngine.level(target, plan) : null;
        return new ProgressEntry(0, UUID.randomUUID().toString(), goal.id(), date, target, actual, status,
                reason, note, level, false, now, now);
    }

    private void trackCheckIn(long goalId, Result result) {
        ProgressEntry entry = result.entry();
        if (entry == null) return;
        Goal goal = goals.getGoal(goalId);
        if (goal == null) return;
        if (entry.status() == EntryStatus.COMPLETED) {
            analytics.track(AnalyticsEvent.targetCompleted(goal.trackingMode(), entry.isExceeded()));
        } else if (entry.status() == EntryStatus.PARTIAL) {
            analytics.track(AnalyticsEvent.of(AnalyticsEvent.PARTIAL_PROGRESS_LOGGED));
        }
        if (result.milestoneReached()) {
            analytics.track(AnalyticsEvent.of(AnalyticsEvent.PROGRESSION_MILESTONE_REACHED));
        }
    }

    @Nullable
    private static String trimToNull(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
