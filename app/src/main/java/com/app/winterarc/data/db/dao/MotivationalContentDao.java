package com.app.winterarc.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.app.winterarc.data.db.entity.MotivationalContentEntity;

import java.util.List;

@Dao
public abstract class MotivationalContentDao {
    @Query("SELECT * FROM motivational_content ORDER BY id ASC")
    public abstract List<MotivationalContentEntity> getAll();

    @Query("SELECT MAX(content_version) FROM motivational_content")
    public abstract Integer storedVersion();

    @Query("DELETE FROM motivational_content")
    abstract void clear();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insertAll(List<MotivationalContentEntity> items);

    /** Atomically swaps the whole content set (bundled seed today, backend refresh later). */
    @Transaction
    public void replaceAll(List<MotivationalContentEntity> items) {
        clear();
        insertAll(items);
    }
}
