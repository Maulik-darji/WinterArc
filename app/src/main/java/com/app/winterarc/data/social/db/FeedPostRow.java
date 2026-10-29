package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;

import java.time.Instant;

/** A post with its counts and the viewer's like state, computed in SQL. */
public class FeedPostRow {
    @NonNull public String id = "";
    @NonNull @ColumnInfo(name = "author_user_id") public String authorUserId = "";
    @NonNull public String body = "";
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
    @Nullable @ColumnInfo(name = "edited_at") public Instant editedAt;
    @ColumnInfo(name = "like_count") public int likeCount;
    @ColumnInfo(name = "liked_by_me") public boolean likedByMe;
    @ColumnInfo(name = "reply_count") public int replyCount;
}
