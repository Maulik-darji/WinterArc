package com.app.winterarc.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.app.winterarc.data.db.Mappers;
import com.app.winterarc.data.db.dao.PausePeriodDao;
import com.app.winterarc.data.db.dao.ProgressEntryDao;
import com.app.winterarc.domain.model.PausePeriod;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.repository.ProgressRepository;

import java.time.LocalDate;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RoomProgressRepository implements ProgressRepository {
    private final ProgressEntryDao entries;
    private final PausePeriodDao pauses;

    @Inject
    public RoomProgressRepository(ProgressEntryDao entries, PausePeriodDao pauses) {
        this.entries = entries;
        this.pauses = pauses;
    }

    @Override
    public LiveData<List<ProgressEntry>> observeEntries(long goalId) {
        return Transformations.map(entries.observeForGoal(goalId), l -> Mappers.mapList(l, Mappers::toDomain));
    }

    @Override
    public LiveData<List<ProgressEntry>> observeEntriesSince(LocalDate date) {
        return Transformations.map(entries.observeSince(date), l -> Mappers.mapList(l, Mappers::toDomain));
    }

    @Override
    public List<ProgressEntry> getEntries(long goalId) {
        return Mappers.mapList(entries.getForGoal(goalId), Mappers::toDomain);
    }

    @Override
    public ProgressEntry getEntry(long goalId, LocalDate date) {
        return Mappers.toDomain(entries.get(goalId, date));
    }

    @Override
    public long upsertEntry(ProgressEntry entry) {
        return entries.upsert(Mappers.toEntity(entry));
    }

    @Override
    public void deleteEntry(long id) {
        entries.delete(id);
    }

    @Override
    public LiveData<List<PausePeriod>> observePauses(long goalId) {
        return Transformations.map(pauses.observeForGoal(goalId), l -> Mappers.mapList(l, Mappers::toDomain));
    }

    @Override
    public LiveData<List<PausePeriod>> observeAllPauses() {
        return Transformations.map(pauses.observeAll(), l -> Mappers.mapList(l, Mappers::toDomain));
    }

    @Override
    public List<PausePeriod> getPauses(long goalId) {
        return Mappers.mapList(pauses.getForGoal(goalId), Mappers::toDomain);
    }

    @Override
    public PausePeriod getOpenPause(long goalId) {
        return Mappers.toDomain(pauses.getOpen(goalId));
    }

    @Override
    public long insertPause(PausePeriod pause) {
        return pauses.insert(Mappers.toEntity(pause));
    }

    @Override
    public void updatePause(PausePeriod pause) {
        pauses.update(Mappers.toEntity(pause));
    }

    @Override
    public void deletePause(long id) {
        pauses.delete(id);
    }
}
