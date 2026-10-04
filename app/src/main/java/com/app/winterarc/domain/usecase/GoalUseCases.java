package com.app.winterarc.domain.usecase;

import androidx.annotation.WorkerThread;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.AnalyticsEvent;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.domain.engine.GoalValidator;
import com.app.winterarc.domain.engine.ProgressionEngine;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalDraft;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.PausePeriod;
import com.app.winterarc.domain.model.ProgressionPlan;
import com.app.winterarc.domain.model.TrackingMode;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.repository.ProgressRepository;
import com.app.winterarc.domain.repository.ReminderScheduler;
import com.app.winterarc.domain.repository.TransactionRunner;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

/** Creating, editing and changing the lifecycle state of goals. */
public class GoalUseCases {

    /** Either a saved goal id, or the validation errors that prevented saving. */
    public record SaveResult(long goalId, Set<GoalValidator.Error> errors) {
        public boolean isSaved() {
            return goalId > 0 && errors.isEmpty();
        }

        static SaveResult saved(long id) {
            return new SaveResult(id, Collections.emptySet());
        }

        static SaveResult invalid(Set<GoalValidator.Error> errors) {
            return new SaveResult(0, errors);
        }
    }

    private final GoalRepository goals;
    private final ProgressRepository progress;
    private final TransactionRunner transactions;
    private final ReminderScheduler reminders;
    private final TimeProvider time;
    private final Analytics analytics;

    @Inject
    public GoalUseCases(GoalRepository goals, ProgressRepository progress, TransactionRunner transactions,
                        ReminderScheduler reminders, TimeProvider time, Analytics analytics) {
        this.goals = goals;
        this.progress = progress;
        this.transactions = transactions;
        this.reminders = reminders;
        this.time = time;
        this.analytics = analytics;
    }

    @WorkerThread
    public SaveResult create(GoalDraft draft) {
        Set<GoalValidator.Error> errors = GoalValidator.validate(draft);
        if (!errors.isEmpty()) return SaveResult.invalid(errors);
        Instant now = time.now();
        Goal goal = new Goal.Builder()
                .uuid(UUID.randomUUID().toString())
                .title(draft.title.trim())
                .description(emptyToNull(draft.description))
                .activityType(draft.activityType)
                .trackingMode(draft.trackingMode)
                .unit(draft.unit)
                .customUnitLabel(emptyToNull(draft.customUnitLabel))
                .currentTarget(draft.initialTarget())
                .plan(draft.plan())
                .schedule(draft.schedule)
                .startDate(draft.startDate != null ? draft.startDate : time.today())
                .reminder(draft.reminder)
                .color(draft.color)
                .icon(draft.icon)
                .status(GoalStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        long id = goals.insertGoal(goal);
        reminders.schedule(goal.toBuilder().id(id).build());
        analytics.track(AnalyticsEvent.goalCreated(draft.activityType, draft.trackingMode, draft.schedule.dayCount()));
        if (draft.reminder.enabled()) analytics.track(AnalyticsEvent.of(AnalyticsEvent.REMINDER_ENABLED));
        return SaveResult.saved(id);
    }

    /**
     * Applies edits. History is never rewritten: entries keep the target that applied on their
     * day, and the current progression target is preserved (clamped into the new plan).
     */
    @WorkerThread
    public SaveResult update(long goalId, GoalDraft draft) {
        Set<GoalValidator.Error> errors = GoalValidator.validate(draft);
        if (!errors.isEmpty()) return SaveResult.invalid(errors);
        Goal existing = goals.getGoal(goalId);
        if (existing == null) return SaveResult.invalid(Collections.emptySet());

        Amount newTarget;
        ProgressionPlan plan = draft.plan();
        if (draft.trackingMode == TrackingMode.CONSISTENCY) {
            newTarget = draft.consistencyTarget;
        } else if (existing.trackingMode() == TrackingMode.PROGRESSION) {
            newTarget = ProgressionEngine.rebase(existing.currentTarget(), plan);
        } else {
            newTarget = plan.startTarget();
        }
        Goal updated = existing.toBuilder()
                .title(draft.title.trim())
                .description(emptyToNull(draft.description))
                .activityType(draft.activityType)
                .trackingMode(draft.trackingMode)
                .unit(draft.unit)
                .customUnitLabel(emptyToNull(draft.customUnitLabel))
                .currentTarget(newTarget)
                .plan(plan)
                .schedule(draft.schedule)
                .reminder(draft.reminder)
                .color(draft.color)
                .icon(draft.icon)
                .updatedAt(time.now())
                .build();
        goals.updateGoal(updated);
        if (updated.reminder().enabled() && !existing.reminder().enabled()) {
            analytics.track(AnalyticsEvent.of(AnalyticsEvent.REMINDER_ENABLED));
        }
        reminders.schedule(updated);
        return SaveResult.saved(goalId);
    }

    /** Pauses tracking from today. Paused days never count as missed. */
    @WorkerThread
    public void pause(long goalId) {
        Goal paused = transactions.run(() -> {
            Goal goal = goals.getGoal(goalId);
            if (goal == null || goal.status() != GoalStatus.ACTIVE) return null;
            if (progress.getOpenPause(goalId) == null) {
                progress.insertPause(new PausePeriod(0, goalId, time.today(), null));
            }
            Goal g = goal.toBuilder().status(GoalStatus.PAUSED).updatedAt(time.now()).build();
            goals.updateGoal(g);
            return g;
        });
        if (paused == null) return;
        reminders.cancel(goalId);
        analytics.track(AnalyticsEvent.of(AnalyticsEvent.GOAL_PAUSED));
    }

    /** Resumes a paused goal from today, keeping the same target. */
    @WorkerThread
    public void resume(long goalId) {
        Goal resumed = transactions.run(() -> {
            Goal goal = goals.getGoal(goalId);
            if (goal == null || goal.status() != GoalStatus.PAUSED) return null;
            closeOpenPause(goalId);
            Goal g = goal.toBuilder().status(GoalStatus.ACTIVE).updatedAt(time.now()).build();
            goals.updateGoal(g);
            return g;
        });
        if (resumed != null) reminders.schedule(resumed);
    }

    @WorkerThread
    public void archive(long goalId) {
        Boolean done = transactions.run(() -> {
            Goal goal = goals.getGoal(goalId);
            if (goal == null) return false;
            closeOpenPause(goalId);
            LocalDate end = goal.endDate() != null ? goal.endDate() : time.today();
            goals.updateGoal(goal.toBuilder().status(GoalStatus.ARCHIVED).endDate(end).updatedAt(time.now()).build());
            return true;
        });
        if (!Boolean.TRUE.equals(done)) return;
        reminders.cancel(goalId);
        analytics.track(AnalyticsEvent.of(AnalyticsEvent.GOAL_ARCHIVED));
    }

    /**
     * Brings an archived or completed goal back. Days between its end and today are recorded as a
     * pause so they never appear as missed.
     */
    @WorkerThread
    public void restore(long goalId) {
        Goal restored = transactions.run(() -> {
            Goal goal = goals.getGoal(goalId);
            if (goal == null || goal.status().isOpen()) return null;
            LocalDate today = time.today();
            LocalDate ended = goal.endDate();
            if (ended != null && ended.plusDays(1).isBefore(today)) {
                progress.insertPause(new PausePeriod(0, goalId, ended.plusDays(1), today.minusDays(1)));
            }
            Goal g = goal.toBuilder().status(GoalStatus.ACTIVE).endDate(null).updatedAt(time.now()).build();
            goals.updateGoal(g);
            return g;
        });
        if (restored != null) reminders.schedule(restored);
    }

    @WorkerThread
    public void delete(long goalId) {
        reminders.cancel(goalId);
        goals.deleteGoal(goalId);
    }

    private void closeOpenPause(long goalId) {
        PausePeriod open = progress.getOpenPause(goalId);
        if (open == null) return;
        LocalDate yesterday = time.today().minusDays(1);
        if (open.startDate().isAfter(yesterday)) {
            // Paused and resumed on the same day: nothing to record.
            progress.deletePause(open.id());
        } else {
            progress.updatePause(open.withEndDate(yesterday));
        }
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
