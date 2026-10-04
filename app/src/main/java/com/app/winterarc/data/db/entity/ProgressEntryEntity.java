package com.app.winterarc.data.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.time.LocalDate;

@Entity(
        tableName = "progress_entries",
        foreignKeys = @ForeignKey(entity = GoalEntity.class, parentColumns = "id", childColumns = "goal_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(value = {"goal_id", "date"}, unique = true),
                @Index("date"),
                @Index(value = "uuid", unique = true)
        })
public class ProgressEntryEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String uuid = "";
    @ColumnInfo(name = "goal_id") public long goalId;
    @NonNull public LocalDate date = LocalDate.MIN;
    @ColumnInfo(name = "scheduled_target_milli") public long scheduledTargetMilli;
    @ColumnInfo(name = "actual_milli") public long actualMilli;
    @NonNull public String status = "";
    @Nullable @ColumnInfo(name = "skip_reason") public String skipReason;
    @Nullable public String note;
    @Nullable @ColumnInfo(name = "progression_level") public Integer progressionLevel;
    @ColumnInfo(name = "advanced_progression") public boolean advancedProgression;
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
    @NonNull @ColumnInfo(name = "updated_at") public Instant updatedAt = Instant.EPOCH;
}
