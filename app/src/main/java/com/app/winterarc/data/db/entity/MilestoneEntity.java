package com.app.winterarc.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.time.LocalDate;

@Entity(
        tableName = "milestones",
        foreignKeys = @ForeignKey(entity = GoalEntity.class, parentColumns = "id", childColumns = "goal_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("goal_id"), @Index("celebration_state")})
public class MilestoneEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @ColumnInfo(name = "goal_id") public long goalId;
    @ColumnInfo(name = "target_milli") public long targetMilli;
    @NonNull @ColumnInfo(name = "achieved_date") public LocalDate achievedDate = LocalDate.MIN;
    @NonNull @ColumnInfo(name = "celebration_state") public String celebrationState = "";
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
}
