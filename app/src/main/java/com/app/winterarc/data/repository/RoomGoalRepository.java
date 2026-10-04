package com.app.winterarc.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.app.winterarc.data.db.Mappers;
import com.app.winterarc.data.db.dao.GoalDao;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.repository.GoalRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RoomGoalRepository implements GoalRepository {
    private final GoalDao dao;

    @Inject
    public RoomGoalRepository(GoalDao dao) {
        this.dao = dao;
    }

    @Override
    public LiveData<List<Goal>> observeGoals(Set<GoalStatus> statuses) {
        return Transformations.map(dao.observeByStatus(keys(statuses)), list -> Mappers.mapList(list, Mappers::toDomain));
    }

    @Override
    public LiveData<Goal> observeGoal(long id) {
        return Transformations.map(dao.observe(id), Mappers::toDomain);
    }

    @Override
    public Goal getGoal(long id) {
        return Mappers.toDomain(dao.get(id));
    }

    @Override
    public List<Goal> getGoals(Set<GoalStatus> statuses) {
        return Mappers.mapList(dao.getByStatus(keys(statuses)), Mappers::toDomain);
    }

    @Override
    public long insertGoal(Goal goal) {
        return dao.insert(Mappers.toEntity(goal));
    }

    @Override
    public void updateGoal(Goal goal) {
        dao.update(Mappers.toEntity(goal));
    }

    @Override
    public void deleteGoal(long id) {
        dao.delete(id);
    }

    private static List<String> keys(Set<GoalStatus> statuses) {
        List<String> keys = new ArrayList<>();
        for (GoalStatus s : statuses) keys.add(s.key());
        return keys;
    }
}
