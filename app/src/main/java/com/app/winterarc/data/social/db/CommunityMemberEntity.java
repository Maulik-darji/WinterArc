package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

import java.time.Instant;

@Entity(tableName = "community_members", primaryKeys = {"community_id", "user_id"},
        indices = {@Index("community_id"), @Index("user_id")})
public class CommunityMemberEntity {
    @NonNull @ColumnInfo(name = "community_id") public String communityId = "";
    @NonNull @ColumnInfo(name = "user_id") public String userId = "";
    @NonNull public String role = "member";
    @NonNull @ColumnInfo(name = "joined_at") public Instant joinedAt = Instant.EPOCH;
}
