package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;

@Entity(tableName = "community_posts", indices = {@Index("community_id"), @Index("author_user_id")})
public class CommunityPostEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull @ColumnInfo(name = "community_id") public String communityId = "";
    @NonNull @ColumnInfo(name = "author_user_id") public String authorUserId = "";
    @NonNull public String body = "";
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
}
