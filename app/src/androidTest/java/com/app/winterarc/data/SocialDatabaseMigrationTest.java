package com.app.winterarc.data;

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.app.winterarc.data.social.db.SocialDatabase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** v1 → v2 adds the feed tables; existing social data must survive and the schema must validate. */
@RunWith(AndroidJUnit4.class)
public class SocialDatabaseMigrationTest {
    private static final String DB = "social-migration-test";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), SocialDatabase.class);

    @Test
    public void migrate1To2KeepsUsersAndAddsFeedTables() throws Exception {
        SupportSQLiteDatabase v1 = helper.createDatabase(DB, 1);
        try (Cursor columns = v1.query("PRAGMA table_info(social_users)")) {
            ContentValues user = new ContentValues();
            while (columns.moveToNext()) {
                String name = columns.getString(columns.getColumnIndexOrThrow("name"));
                String type = columns.getString(columns.getColumnIndexOrThrow("type"));
                if (type.equalsIgnoreCase("INTEGER")) user.put(name, 0L); else user.put(name, name.equals("user_id") ? "u1" : "x");
            }
            v1.insert("social_users", SQLiteDatabase.CONFLICT_ABORT, user);
        }
        v1.close();

        SupportSQLiteDatabase v2 = helper.runMigrationsAndValidate(DB, 3, true,
                SocialDatabase.MIGRATION_1_2, SocialDatabase.MIGRATION_2_3);
        try (Cursor c = v2.query("SELECT avatar_url, banner_url FROM social_users WHERE user_id = 'u1'")) {
            c.moveToFirst();
            assertEquals("v3 adds empty photo and banner columns", null, c.getString(0));
        }
        try (Cursor c = v2.query("SELECT COUNT(*) FROM social_users WHERE user_id = 'u1'")) {
            c.moveToFirst();
            assertEquals(1, c.getInt(0));
        }
        v2.execSQL("PRAGMA foreign_keys = ON");
        v2.execSQL("INSERT INTO feed_posts (id, author_user_id, body, created_at) VALUES ('p1', 'u1', 'hi', 0)");
        v2.execSQL("INSERT INTO post_comments (id, post_id, author_user_id, body, created_at, deleted) VALUES ('c1', 'p1', 'u1', 'yo', 0, 0)");
        v2.execSQL("DELETE FROM feed_posts WHERE id = 'p1'");
        try (Cursor c = v2.query("SELECT COUNT(*) FROM post_comments")) {
            c.moveToFirst();
            assertEquals("comments cascade with their post", 0, c.getInt(0));
        }
        v2.close();
    }
}
