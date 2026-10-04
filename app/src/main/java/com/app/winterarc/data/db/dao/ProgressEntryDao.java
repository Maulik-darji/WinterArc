package com.app.winterarc.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.app.winterarc.data.db.entity.ProgressEntryEntity;

import java.time.LocalDate;
import java.util.List;

@Dao
public interface ProgressEntryDao {
    @Query("SELECT * FROM progress_entries WHERE goal_id = :goalId ORDER BY date ASC")
    LiveData<List<ProgressEntryEntity>> observeForGoal(long goalId);

    @Query("SELECT * FROM progress_entries WHERE goal_id = :goalId ORDER BY date ASC")
    List<ProgressEntryEntity> getForGoal(long goalId);

    @Query("SELECT * FROM progress_entries WHERE date >= :since ORDER BY date ASC")
    LiveData<List<ProgressEntryEntity>> observeSince(LocalDate since);

    @Query("SELECT * FROM progress_entries WHERE goal_id = :goalId AND date = :date LIMIT 1")
    ProgressEntryEntity get(long goalId, LocalDate date);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(ProgressEntryEntity entry);

    @Insert
    void insertAll(List<ProgressEntryEntity> entries);

    @Query("DELETE FROM progress_entries WHERE id = :id")
    void delete(long id);
}
