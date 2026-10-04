package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;

@Entity(tableName = "community_messages", indices = {@Index("community_id"), @Index("sender_user_id")})
public class CommunityMessageEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull @ColumnInfo(name = "community_id") public String communityId = "";
    @NonNull @ColumnInfo(name = "sender_user_id") public String senderUserId = "";
    @NonNull public String body = "";
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
}
