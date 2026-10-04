package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;

@Entity(tableName = "communities", indices = @Index("owner_user_id"))
public class CommunityEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String name = "";
    @NonNull public String description = "";
    @NonNull @ColumnInfo(name = "owner_user_id") public String ownerUserId = "";
    @ColumnInfo(name = "members_can_post") public boolean membersCanPost;
    @ColumnInfo(name = "members_can_chat") public boolean membersCanChat;
    @ColumnInfo(name = "members_can_invite") public boolean membersCanInvite;
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
}
