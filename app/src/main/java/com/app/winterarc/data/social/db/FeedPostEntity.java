package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;

/** A public post on the Feed (not tied to a community). */
@Entity(tableName = "feed_posts", indices = {@Index("author_user_id"), @Index("created_at")})
public class FeedPostEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull @ColumnInfo(name = "author_user_id") public String authorUserId = "";
    @NonNull public String body = "";
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
    @Nullable @ColumnInfo(name = "edited_at") public Instant editedAt;
}
