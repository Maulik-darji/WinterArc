package com.app.winterarc.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.app.winterarc.data.db.entity.MilestoneEntity;

import java.time.LocalDate;
import java.util.List;

@Dao
public interface MilestoneDao {
    @Query("SELECT * FROM milestones WHERE goal_id = :goalId ORDER BY achieved_date ASC, id ASC")
    LiveData<List<MilestoneEntity>> observeForGoal(long goalId);

    @Query("SELECT * FROM milestones WHERE celebration_state = :state")
    LiveData<List<MilestoneEntity>> observeByState(String state);

    @Query("SELECT * FROM milestones WHERE goal_id = :goalId AND celebration_state = :state ORDER BY id DESC LIMIT 1")
    MilestoneEntity getByState(long goalId, String state);

    @Query("SELECT * FROM milestones WHERE goal_id = :goalId AND achieved_date = :date ORDER BY id DESC LIMIT 1")
    MilestoneEntity getForDate(long goalId, LocalDate date);

    @Insert
    long insert(MilestoneEntity milestone);

    @Update
    void update(MilestoneEntity milestone);

    @Query("DELETE FROM milestones WHERE id = :id")
    void delete(long id);
}
