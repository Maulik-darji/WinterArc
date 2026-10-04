package com.app.winterarc.domain.usecase;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.AnalyticsEvent;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.domain.engine.ProgressionEngine;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.CelebrationState;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.domain.model.ProgressionPlan;
import com.app.winterarc.domain.model.TrackingMode;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.repository.MilestoneRepository;
import com.app.winterarc.domain.repository.ReminderScheduler;
import com.app.winterarc.domain.repository.TransactionRunner;

import javax.inject.Inject;

/**
 * Resolves a reached final milestone. Every option keeps the goal's full history; nothing is reset
 * or discarded.
 */
public class MilestoneUseCase {

    private final GoalRepository goals;
    private final MilestoneRepository milestones;
    private final TransactionRunner transactions;
    private final ReminderScheduler reminders;
    private final TimeProvider time;
    private final Analytics analytics;

    @Inject
    public MilestoneUseCase(GoalRepository goals, MilestoneRepository milestones, TransactionRunner transactions,
                            ReminderScheduler reminders, TimeProvider time, Analytics analytics) {
        this.goals = goals;
        this.milestones = milestones;
        this.transactions = transactions;
        this.reminders = reminders;
        this.time = time;
        this.analytics = analytics;
    }

    /** Converts the goal to consistency mode with the milestone as its recurring target. */
    @WorkerThread
    public boolean maintain(long goalId) {
        boolean ok = transactions.run(() -> {
            Goal goal = goals.getGoal(goalId);
            if (goal == null || goal.plan() == null) return false;
            goals.updateGoal(goal.toBuilder()
                    .trackingMode(TrackingMode.CONSISTENCY)
                    .plan(null)
                    .currentTarget(goal.plan().finalTarget())
                    .updatedAt(time.now())
                    .build());
            resolve(goalId, CelebrationState.MAINTAINED);
            return true;
        });
        if (ok) analytics.track(AnalyticsEvent.of(AnalyticsEvent.PROGRESSION_CONVERTED_TO_CONSISTENCY));
        return ok;
    }

    /** Keeps progressing from the reached milestone toward {@code newFinal}, optionally with a new hop. */
    @WorkerThread
    public boolean continueProgressing(long goalId, Amount newFinal, @Nullable Amount hop) {
        boolean ok = transactions.run(() -> {
            Goal goal = goals.getGoal(goalId);
            if (goal == null || goal.plan() == null) return false;
            ProgressionPlan plan = goal.plan();
            if (!newFinal.greaterThan(plan.finalTarget())) return false;
            Amount step = hop != null && hop.isPositive() ? hop : plan.hop();
            ProgressionEngine.ContinuedPlan continued =
                    ProgressionEngine.continueBeyond(plan.finalTarget(), newFinal, step);
            goals.updateGoal(goal.toBuilder()
                    .plan(continued.plan())
                    .currentTarget(continued.nextTarget())
                    .updatedAt(time.now())
                    .build());
            resolve(goalId, CelebrationState.CONTINUED);
            return true;
        });
        if (ok) analytics.track(AnalyticsEvent.of(AnalyticsEvent.PROGRESSION_CONTINUED));
        return ok;
    }

    /** Marks the goal complete; it moves to the archive with all history preserved. */
    @WorkerThread
    public boolean markComplete(long goalId) {
        boolean ok = transactions.run(() -> {
            Goal goal = goals.getGoal(goalId);
            if (goal == null) return false;
            goals.updateGoal(goal.toBuilder()
                    .status(GoalStatus.COMPLETED)
                    .endDate(time.today())
                    .updatedAt(time.now())
                    .build());
            resolve(goalId, CelebrationState.COMPLETED);
            return true;
        });
        if (ok) {
            reminders.cancel(goalId);
            analytics.track(AnalyticsEvent.of(AnalyticsEvent.GOAL_ARCHIVED));
        }
        return ok;
    }

    private void resolve(long goalId, CelebrationState state) {
        Milestone pending = milestones.getPending(goalId);
        if (pending != null) milestones.update(pending.withState(state));
    }
}
