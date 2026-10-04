package com.app.winterarc.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.app.winterarc.data.db.entity.PausePeriodEntity;

import java.util.List;

@Dao
public interface PausePeriodDao {
    @Query("SELECT * FROM pause_periods WHERE goal_id = :goalId ORDER BY start_date ASC")
    LiveData<List<PausePeriodEntity>> observeForGoal(long goalId);

    @Query("SELECT * FROM pause_periods WHERE goal_id = :goalId ORDER BY start_date ASC")
    List<PausePeriodEntity> getForGoal(long goalId);

    @Query("SELECT * FROM pause_periods ORDER BY start_date ASC")
    LiveData<List<PausePeriodEntity>> observeAll();

    @Query("SELECT * FROM pause_periods WHERE goal_id = :goalId AND end_date IS NULL LIMIT 1")
    PausePeriodEntity getOpen(long goalId);

    @Insert
    long insert(PausePeriodEntity pause);

    @Update
    void update(PausePeriodEntity pause);

    @Query("DELETE FROM pause_periods WHERE id = :id")
    void delete(long id);
}
