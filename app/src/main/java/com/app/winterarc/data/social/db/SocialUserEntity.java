package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "social_users")
public class SocialUserEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "user_id") public String userId = "";
    @NonNull @ColumnInfo(name = "display_name") public String displayName = "";
    @NonNull public String handle = "";
    @NonNull public String bio = "";
    @NonNull public String initial = "";
    @Nullable @ColumnInfo(name = "avatar_url") public String avatarUrl;
    @Nullable @ColumnInfo(name = "banner_url") public String bannerUrl;
}
