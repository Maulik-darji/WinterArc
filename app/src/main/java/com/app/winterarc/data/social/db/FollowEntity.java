package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

import java.time.Instant;

@Entity(tableName = "follows", primaryKeys = {"follower_user_id", "followed_user_id"},
        indices = {@Index("follower_user_id"), @Index("followed_user_id")})
public class FollowEntity {
    @NonNull @ColumnInfo(name = "follower_user_id") public String followerUserId = "";
    @NonNull @ColumnInfo(name = "followed_user_id") public String followedUserId = "";
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
}
