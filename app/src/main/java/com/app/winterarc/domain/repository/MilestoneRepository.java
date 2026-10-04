package com.app.winterarc.domain.repository;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.app.winterarc.domain.model.Milestone;

import java.time.LocalDate;
import java.util.List;

public interface MilestoneRepository {
    LiveData<List<Milestone>> observeMilestones(long goalId);

    LiveData<List<Milestone>> observePending();

    @WorkerThread @Nullable
    Milestone getPending(long goalId);

    @WorkerThread @Nullable
    Milestone getForDate(long goalId, LocalDate date);

    @WorkerThread
    long insert(Milestone milestone);

    @WorkerThread
    void update(Milestone milestone);

    @WorkerThread
    void delete(long id);
}
