package com.app.winterarc.reminders;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.repository.PreferencesRepository;
import com.app.winterarc.domain.repository.ReminderScheduler;

import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * One unique one-shot job per goal, re-armed after each run. A one-shot computed from the local
 * wall-clock time stays correct across DST changes, unlike a fixed 24-hour periodic job.
 */
@Singleton
public class WorkManagerReminderScheduler implements ReminderScheduler {
    private final Context context;
    private final TimeProvider time;
    private final PreferencesRepository preferences;
    private final Provider<GoalRepository> goals;

    @Inject
    public WorkManagerReminderScheduler(@ApplicationContext Context context, TimeProvider time,
                                        PreferencesRepository preferences, Provider<GoalRepository> goals) {
        this.context = context;
        this.time = time;
        this.preferences = preferences;
        this.goals = goals;
    }

    static String workName(long goalId) {
        return "goal-reminder-" + goalId;
    }

    @Override
    public void schedule(Goal goal) {
        if (!goal.reminder().enabled() || goal.status() != GoalStatus.ACTIVE || !preferences.get().remindersEnabled()) {
            cancel(goal.id());
            return;
        }
        Duration delay = ReminderTimes.delayUntilNext(time.zonedNow(), goal.reminder().time());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReminderWorker.class)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(new Data.Builder().putLong(ReminderWorker.KEY_GOAL_ID, goal.id()).build())
                .addTag("reminder")
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(workName(goal.id()), ExistingWorkPolicy.REPLACE, request);
    }

    @Override
    public void cancel(long goalId) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(goalId));
    }

    @Override
    public void rescheduleAll() {
        for (Goal goal : goals.get().getGoals(EnumSet.allOf(GoalStatus.class))) schedule(goal);
    }
}
