package com.app.winterarc.domain.repository;

import androidx.annotation.WorkerThread;

import com.app.winterarc.domain.model.Goal;

/** Schedules or cancels per-goal reminders. Implemented with WorkManager. */
public interface ReminderScheduler {
    void schedule(Goal goal);

    void cancel(long goalId);

    @WorkerThread
    void rescheduleAll();
}
