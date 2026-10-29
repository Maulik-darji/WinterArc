package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import java.time.Instant;

@Entity(tableName = "feed_post_likes", primaryKeys = {"post_id", "user_id"},
        foreignKeys = @ForeignKey(entity = FeedPostEntity.class, parentColumns = "id", childColumns = "post_id",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("post_id"))
public class FeedPostLikeEntity {
    @NonNull @ColumnInfo(name = "post_id") public String postId = "";
    @NonNull @ColumnInfo(name = "user_id") public String userId = "";
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
}
