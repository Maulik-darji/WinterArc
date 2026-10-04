package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;

import java.time.Instant;

public class PostRow {
    @NonNull public String id = "";
    @NonNull @ColumnInfo(name = "community_id") public String communityId = "";
    @NonNull @ColumnInfo(name = "author_user_id") public String authorUserId = "";
    @NonNull public String body = "";
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
    @ColumnInfo(name = "like_count") public int likeCount;
    @ColumnInfo(name = "liked_by_me") public boolean likedByMe;
}
