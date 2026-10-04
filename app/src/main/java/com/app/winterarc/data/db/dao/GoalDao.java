package com.app.winterarc.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.app.winterarc.data.db.entity.GoalEntity;

import java.util.List;

@Dao
public interface GoalDao {
    @Query("SELECT * FROM goals WHERE status IN (:statuses) ORDER BY created_at ASC, id ASC")
    LiveData<List<GoalEntity>> observeByStatus(List<String> statuses);

    @Query("SELECT * FROM goals WHERE status IN (:statuses) ORDER BY created_at ASC, id ASC")
    List<GoalEntity> getByStatus(List<String> statuses);

    @Query("SELECT * FROM goals WHERE id = :id")
    LiveData<GoalEntity> observe(long id);

    @Query("SELECT * FROM goals WHERE id = :id")
    GoalEntity get(long id);

    @Insert
    long insert(GoalEntity goal);

    @Update
    void update(GoalEntity goal);

    @Query("DELETE FROM goals WHERE id = :id")
    void delete(long id);

    @Query("DELETE FROM goals")
    void deleteAll();
}
