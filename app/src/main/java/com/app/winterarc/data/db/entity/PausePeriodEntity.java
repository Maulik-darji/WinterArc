package com.app.winterarc.data.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.LocalDate;

@Entity(
        tableName = "pause_periods",
        foreignKeys = @ForeignKey(entity = GoalEntity.class, parentColumns = "id", childColumns = "goal_id",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("goal_id"))
public class PausePeriodEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @ColumnInfo(name = "goal_id") public long goalId;
    @NonNull @ColumnInfo(name = "start_date") public LocalDate startDate = LocalDate.MIN;
    @Nullable @ColumnInfo(name = "end_date") public LocalDate endDate;
}
