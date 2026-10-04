package com.app.winterarc.domain.model;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A user goal.
 *
 * @param uuid          stable cross-device identifier, reserved for future cloud sync
 * @param currentTarget the target for the next attempt. For consistency goals this is the
 *                      recurring target; for progression goals it only increases after a
 *                      successful completion
 * @param plan          progression plan, null for consistency goals
 * @param endDate       last tracked day once completed/archived; later days are never "missed"
 */
public record Goal(
        long id,
        String uuid,
        String title,
        @Nullable String description,
        ActivityType activityType,
        TrackingMode trackingMode,
        GoalUnit unit,
        @Nullable String customUnitLabel,
        Amount currentTarget,
        @Nullable ProgressionPlan plan,
        WeeklySchedule schedule,
        LocalDate startDate,
        @Nullable LocalDate endDate,
        ReminderConfig reminder,
        GoalColor color,
        GoalIcon icon,
        GoalStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isProgression() {
        return trackingMode == TrackingMode.PROGRESSION && plan != null;
    }

    /** True when the current target already equals the final milestone. */
    public boolean isAtFinalTarget() {
        return plan != null && currentTarget.atLeast(plan.finalTarget());
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Copy-with builder, since records are immutable. */
    public static final class Builder {
        private long id;
        private String uuid;
        private String title;
        private String description;
        private ActivityType activityType;
        private TrackingMode trackingMode;
        private GoalUnit unit;
        private String customUnitLabel;
        private Amount currentTarget;
        private ProgressionPlan plan;
        private WeeklySchedule schedule;
        private LocalDate startDate;
        private LocalDate endDate;
        private ReminderConfig reminder;
        private GoalColor color;
        private GoalIcon icon;
        private GoalStatus status;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder() {
            reminder = ReminderConfig.DEFAULT;
            status = GoalStatus.ACTIVE;
            schedule = WeeklySchedule.EVERY_DAY;
        }

        private Builder(Goal g) {
            id = g.id;
            uuid = g.uuid;
            title = g.title;
            description = g.description;
            activityType = g.activityType;
            trackingMode = g.trackingMode;
            unit = g.unit;
            customUnitLabel = g.customUnitLabel;
            currentTarget = g.currentTarget;
            plan = g.plan;
            schedule = g.schedule;
            startDate = g.startDate;
            endDate = g.endDate;
            reminder = g.reminder;
            color = g.color;
            icon = g.icon;
            status = g.status;
            createdAt = g.createdAt;
            updatedAt = g.updatedAt;
        }

        public Builder id(long v) { id = v; return this; }
        public Builder uuid(String v) { uuid = v; return this; }
        public Builder title(String v) { title = v; return this; }
        public Builder description(@Nullable String v) { description = v; return this; }
        public Builder activityType(ActivityType v) { activityType = v; return this; }
        public Builder trackingMode(TrackingMode v) { trackingMode = v; return this; }
        public Builder unit(GoalUnit v) { unit = v; return this; }
        public Builder customUnitLabel(@Nullable String v) { customUnitLabel = v; return this; }
        public Builder currentTarget(Amount v) { currentTarget = v; return this; }
        public Builder plan(@Nullable ProgressionPlan v) { plan = v; return this; }
        public Builder schedule(WeeklySchedule v) { schedule = v; return this; }
        public Builder startDate(LocalDate v) { startDate = v; return this; }
        public Builder endDate(@Nullable LocalDate v) { endDate = v; return this; }
        public Builder reminder(ReminderConfig v) { reminder = v; return this; }
        public Builder color(GoalColor v) { color = v; return this; }
        public Builder icon(GoalIcon v) { icon = v; return this; }
        public Builder status(GoalStatus v) { status = v; return this; }
        public Builder createdAt(Instant v) { createdAt = v; return this; }
        public Builder updatedAt(Instant v) { updatedAt = v; return this; }

        public Goal build() {
            return new Goal(id, uuid, title, description, activityType, trackingMode, unit, customUnitLabel,
                    currentTarget, plan, schedule, startDate, endDate, reminder, color, icon, status,
                    createdAt, updatedAt);
        }
    }
}
