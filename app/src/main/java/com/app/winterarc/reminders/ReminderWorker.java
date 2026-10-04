package com.app.winterarc.reminders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.repository.PreferencesRepository;
import com.app.winterarc.domain.repository.ProgressRepository;
import com.app.winterarc.domain.repository.ReminderScheduler;

import java.time.LocalDate;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

/**
 * Fires one goal reminder, then schedules the next one. Nothing is shown when the day is not
 * scheduled, the goal is paused or closed, or today's target is already complete.
 */
@HiltWorker
public class ReminderWorker extends Worker {
    public static final String KEY_GOAL_ID = "goal_id";

    private final GoalRepository goals;
    private final ProgressRepository progress;
    private final PreferencesRepository preferences;
    private final ReminderScheduler scheduler;
    private final TimeProvider time;

    @AssistedInject
    public ReminderWorker(@Assisted @NonNull Context context, @Assisted @NonNull WorkerParameters params,
                          GoalRepository goals, ProgressRepository progress, PreferencesRepository preferences,
                          ReminderScheduler scheduler, TimeProvider time) {
        super(context, params);
        this.goals = goals;
        this.progress = progress;
        this.preferences = preferences;
        this.scheduler = scheduler;
        this.time = time;
    }

    @NonNull
    @Override
    public Result doWork() {
        long goalId = getInputData().getLong(KEY_GOAL_ID, -1);
        Goal goal = goals.getGoal(goalId);
        if (goal == null) return Result.success();
        if (shouldNotify(goal)) {
            Context ctx = getApplicationContext();
            String target = Formats.amountWithUnit(ctx, goal.currentTarget(), goal.unit(), goal.customUnitLabel());
            Notifications.showReminder(ctx, goalId, goal.title(),
                    ctx.getString(R.string.notification_reminder_text, target));
        }
        scheduler.schedule(goal); // next occurrence; a no-op if reminders are now off
        return Result.success();
    }

    private boolean shouldNotify(Goal goal) {
        if (!preferences.get().remindersEnabled() || !goal.reminder().enabled()) return false;
        if (goal.status() != GoalStatus.ACTIVE) return false;
        LocalDate today = time.today();
        if (!goal.schedule().isScheduled(today) || today.isBefore(goal.startDate())) return false;
        ProgressEntry entry = progress.getEntry(goal.id(), today);
        return entry == null || (entry.status() != EntryStatus.COMPLETED && entry.status() != EntryStatus.SKIPPED);
    }
}
