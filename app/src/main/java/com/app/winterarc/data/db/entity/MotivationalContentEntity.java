package com.app.winterarc.data.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "motivational_content")
public class MotivationalContentEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull @ColumnInfo(name = "content_type") public String contentType = "";
    @Nullable @ColumnInfo(name = "activity_type") public String activityType;
    @Nullable @ColumnInfo(name = "min_target_milli") public Long minTargetMilli;
    @Nullable @ColumnInfo(name = "max_target_milli") public Long maxTargetMilli;
    @NonNull public String message = "";
    @Nullable public String attribution;
    @Nullable @ColumnInfo(name = "source_name") public String sourceName;
    @Nullable @ColumnInfo(name = "source_url") public String sourceUrl;
    @NonNull public String locale = "en";
    @ColumnInfo(name = "content_version") public int contentVersion;
}
