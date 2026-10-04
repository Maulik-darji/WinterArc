package com.app.winterarc.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.app.winterarc.data.db.Mappers;
import com.app.winterarc.data.db.dao.MilestoneDao;
import com.app.winterarc.domain.model.CelebrationState;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.domain.repository.MilestoneRepository;

import java.time.LocalDate;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RoomMilestoneRepository implements MilestoneRepository {
    private final MilestoneDao dao;

    @Inject
    public RoomMilestoneRepository(MilestoneDao dao) {
        this.dao = dao;
    }

    @Override
    public LiveData<List<Milestone>> observeMilestones(long goalId) {
        return Transformations.map(dao.observeForGoal(goalId), l -> Mappers.mapList(l, Mappers::toDomain));
    }

    @Override
    public LiveData<List<Milestone>> observePending() {
        return Transformations.map(dao.observeByState(CelebrationState.PENDING.key()),
                l -> Mappers.mapList(l, Mappers::toDomain));
    }

    @Override
    public Milestone getPending(long goalId) {
        return Mappers.toDomain(dao.getByState(goalId, CelebrationState.PENDING.key()));
    }

    @Override
    public Milestone getForDate(long goalId, LocalDate date) {
        return Mappers.toDomain(dao.getForDate(goalId, date));
    }

    @Override
    public long insert(Milestone milestone) {
        return dao.insert(Mappers.toEntity(milestone));
    }

    @Override
    public void update(Milestone milestone) {
        dao.update(Mappers.toEntity(milestone));
    }

    @Override
    public void delete(long id) {
        dao.delete(id);
    }
}
