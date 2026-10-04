package com.app.winterarc.data;

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.app.winterarc.data.db.WinterArcDatabase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Validates the exported schema and that every registered migration produces it. When version 2
 * is added, create the database at 1, insert rows, then call runMigrationsAndValidate(2).
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {
    private static final String DB = "migration-test";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), WinterArcDatabase.class);

    @Test
    public void exportedSchemaMatchesEntitiesAndDataSurvivesReopen() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(DB, 1);
        ContentValues goal = new ContentValues();
        goal.put("uuid", "u1");
        goal.put("title", "Walk");
        goal.put("activity_type", "walking");
        goal.put("tracking_mode", "consistency");
        goal.put("unit", "km");
        goal.put("current_target_milli", 5000L);
        goal.put("schedule_mask", 127);
        goal.put("start_date", 20_000L);
        goal.put("reminder_enabled", 0);
        goal.put("reminder_minute_of_day", 420);
        goal.put("color", "emerald");
        goal.put("icon", "walk");
        goal.put("status", "active");
        goal.put("created_at", 0L);
        goal.put("updated_at", 0L);
        db.insert("goals", SQLiteDatabase.CONFLICT_ABORT, goal);
        db.close();

        // Open with Room using the production migrations; Room validates the schema.
        WinterArcDatabase room = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        WinterArcDatabase.class, DB)
                .addMigrations(WinterArcDatabase.MIGRATIONS)
                .build();
        try (Cursor c = room.getOpenHelper().getReadableDatabase().query("SELECT title FROM goals")) {
            c.moveToFirst();
            assertEquals("Walk", c.getString(0));
        }
        room.close();
    }
}
