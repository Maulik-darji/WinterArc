package com.app.winterarc.data.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.time.LocalDate;

/*
 * Storage rules for every entity:
 * - Quantities are Long thousandths of a unit ("milli"), never floating point or formatted text.
 * - Calendar days are LocalDate (stored as epoch day); timestamps are Instant (epoch millis).
 * - Enums are stored by their stable string key.
 * - Syncable rows carry a uuid for future cloud synchronisation.
 */
@Entity(tableName = "goals", indices = {@Index(value = "uuid", unique = true), @Index("status")})
public class GoalEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String uuid = "";
    @NonNull public String title = "";
    @Nullable public String description;
    @NonNull @ColumnInfo(name = "activity_type") public String activityType = "";
    @NonNull @ColumnInfo(name = "tracking_mode") public String trackingMode = "";
    @NonNull public String unit = "";
    @Nullable @ColumnInfo(name = "custom_unit_label") public String customUnitLabel;
    @ColumnInfo(name = "current_target_milli") public long currentTargetMilli;
    @Nullable @ColumnInfo(name = "start_target_milli") public Long startTargetMilli;
    @Nullable @ColumnInfo(name = "final_target_milli") public Long finalTargetMilli;
    @Nullable @ColumnInfo(name = "hop_milli") public Long hopMilli;
    @ColumnInfo(name = "schedule_mask") public int scheduleMask;
    @NonNull @ColumnInfo(name = "start_date") public LocalDate startDate = LocalDate.MIN;
    @Nullable @ColumnInfo(name = "end_date") public LocalDate endDate;
    @ColumnInfo(name = "reminder_enabled") public boolean reminderEnabled;
    @ColumnInfo(name = "reminder_minute_of_day") public int reminderMinuteOfDay;
    @NonNull public String color = "";
    @NonNull public String icon = "";
    @NonNull public String status = "";
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
    @NonNull @ColumnInfo(name = "updated_at") public Instant updatedAt = Instant.EPOCH;
}
