package com.app.winterarc.domain.repository;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;

import java.util.List;
import java.util.Set;

/*
 * Repository contracts. Screens and use cases depend only on these interfaces, so a future
 * cloud-backed implementation (auth + sync) can replace the offline Room implementations
 * without touching any screen. Blocking methods must be called off the main thread.
 */
public interface GoalRepository {
    LiveData<List<Goal>> observeGoals(Set<GoalStatus> statuses);

    LiveData<Goal> observeGoal(long id);

    @WorkerThread @Nullable
    Goal getGoal(long id);

    @WorkerThread
    List<Goal> getGoals(Set<GoalStatus> statuses);

    @WorkerThread
    long insertGoal(Goal goal);

    @WorkerThread
    void updateGoal(Goal goal);

    /** Permanently deletes a goal and, via cascade, all of its history. */
    @WorkerThread
    void deleteGoal(long id);
}
