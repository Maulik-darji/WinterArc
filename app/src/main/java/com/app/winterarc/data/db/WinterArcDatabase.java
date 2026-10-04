package com.app.winterarc.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;

import com.app.winterarc.data.db.dao.GoalDao;
import com.app.winterarc.data.db.dao.MilestoneDao;
import com.app.winterarc.data.db.dao.MotivationalContentDao;
import com.app.winterarc.data.db.dao.PausePeriodDao;
import com.app.winterarc.data.db.dao.ProgressEntryDao;
import com.app.winterarc.data.db.entity.GoalEntity;
import com.app.winterarc.data.db.entity.MilestoneEntity;
import com.app.winterarc.data.db.entity.MotivationalContentEntity;
import com.app.winterarc.data.db.entity.PausePeriodEntity;
import com.app.winterarc.data.db.entity.ProgressEntryEntity;

@Database(
        entities = {
                GoalEntity.class,
                ProgressEntryEntity.class,
                MilestoneEntity.class,
                PausePeriodEntity.class,
                MotivationalContentEntity.class
        },
        version = WinterArcDatabase.VERSION,
        exportSchema = true)
@TypeConverters(Converters.class)
public abstract class WinterArcDatabase extends RoomDatabase {
    public static final int VERSION = 1;
    public static final String NAME = "winterarc.db";

    /**
     * Explicit migrations. The schema JSON for every version is exported to app/schemas and must
     * be committed. Add each new Migration(n, n + 1) here plus a test in DatabaseMigrationTest.
     * Destructive fallback is intentionally never enabled, so user history is never wiped.
     */
    public static final Migration[] MIGRATIONS = new Migration[0];

    public abstract GoalDao goalDao();

    public abstract ProgressEntryDao progressEntryDao();

    public abstract MilestoneDao milestoneDao();

    public abstract PausePeriodDao pausePeriodDao();

    public abstract MotivationalContentDao contentDao();
}
