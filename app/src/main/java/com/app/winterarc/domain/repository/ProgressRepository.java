package com.app.winterarc.domain.repository;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.app.winterarc.domain.model.PausePeriod;
import com.app.winterarc.domain.model.ProgressEntry;

import java.time.LocalDate;
import java.util.List;

public interface ProgressRepository {
    LiveData<List<ProgressEntry>> observeEntries(long goalId);

    LiveData<List<ProgressEntry>> observeEntriesSince(LocalDate date);

    @WorkerThread
    List<ProgressEntry> getEntries(long goalId);

    @WorkerThread @Nullable
    ProgressEntry getEntry(long goalId, LocalDate date);

    /** Inserts or replaces by id; returns the row id. */
    @WorkerThread
    long upsertEntry(ProgressEntry entry);

    @WorkerThread
    void deleteEntry(long id);

    LiveData<List<PausePeriod>> observePauses(long goalId);

    LiveData<List<PausePeriod>> observeAllPauses();

    @WorkerThread
    List<PausePeriod> getPauses(long goalId);

    @WorkerThread @Nullable
    PausePeriod getOpenPause(long goalId);

    @WorkerThread
    long insertPause(PausePeriod pause);

    @WorkerThread
    void updatePause(PausePeriod pause);

    @WorkerThread
    void deletePause(long id);
}
