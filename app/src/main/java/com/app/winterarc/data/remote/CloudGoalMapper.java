package com.app.winterarc.data.remote;

import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalColor;
import com.app.winterarc.domain.model.GoalIcon;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.GoalUnit;
import com.app.winterarc.domain.model.ProgressionPlan;
import com.app.winterarc.domain.model.ReminderConfig;
import com.app.winterarc.domain.model.TrackingMode;
import com.app.winterarc.domain.model.WeeklySchedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static com.app.winterarc.domain.model.StableKeys.fromKey;

/** Pure mapping between the domain model and Supabase JSON. */
public final class CloudGoalMapper {
    private CloudGoalMapper() {}

    public static CloudGoal fromDomain(Goal goal) {
        CloudGoal cloud = new CloudGoal();
        cloud.uuid = goal.uuid();
        cloud.title = goal.title();
        cloud.description = goal.description();
        cloud.activityType = goal.activityType().key();
        cloud.trackingMode = goal.trackingMode().key();
        cloud.unit = goal.unit().key();
        cloud.customUnitLabel = goal.customUnitLabel();
        cloud.currentTargetMilli = goal.currentTarget().milli();
        if (goal.plan() != null) {
            cloud.startTargetMilli = goal.plan().startTarget().milli();
            cloud.finalTargetMilli = goal.plan().finalTarget().milli();
            cloud.hopMilli = goal.plan().hop().milli();
        }
        cloud.scheduleMask = goal.schedule().mask();
        cloud.startDate = goal.startDate().toString();
        cloud.endDate = goal.endDate() == null ? null : goal.endDate().toString();
        cloud.reminderEnabled = goal.reminder().enabled();
        cloud.reminderMinuteOfDay = goal.reminder().time().getHour() * 60
                + goal.reminder().time().getMinute();
        cloud.color = goal.color().key();
        cloud.icon = goal.icon().key();
        cloud.status = goal.status().key();
        cloud.createdAt = goal.createdAt().toString();
        cloud.updatedAt = goal.updatedAt().toString();
        return cloud;
    }

    /** The caller supplies the existing Room id, or zero when inserting a pulled goal. */
    public static Goal toDomain(CloudGoal cloud, long localId) {
        TrackingMode mode = fromKey(TrackingMode.class, cloud.trackingMode, TrackingMode.CONSISTENCY);
        ProgressionPlan plan = null;
        if (mode == TrackingMode.PROGRESSION && cloud.startTargetMilli != null
                && cloud.finalTargetMilli != null && cloud.hopMilli != null) {
            plan = new ProgressionPlan(new Amount(cloud.startTargetMilli),
                    new Amount(cloud.finalTargetMilli), new Amount(cloud.hopMilli));
        }
        return new Goal(localId, cloud.uuid, cloud.title, cloud.description,
                fromKey(ActivityType.class, cloud.activityType, ActivityType.CUSTOM),
                plan == null ? TrackingMode.CONSISTENCY : mode,
                fromKey(GoalUnit.class, cloud.unit, GoalUnit.SESSIONS), cloud.customUnitLabel,
                new Amount(cloud.currentTargetMilli), plan,
                new WeeklySchedule(cloud.scheduleMask & WeeklySchedule.ALL_MASK),
                LocalDate.parse(cloud.startDate),
                cloud.endDate == null ? null : LocalDate.parse(cloud.endDate),
                new ReminderConfig(cloud.reminderEnabled,
                        LocalTime.ofSecondOfDay(Math.floorMod(cloud.reminderMinuteOfDay, 1440) * 60L)),
                fromKey(GoalColor.class, cloud.color, GoalColor.EMERALD),
                fromKey(GoalIcon.class, cloud.icon, GoalIcon.STAR),
                fromKey(GoalStatus.class, cloud.status, GoalStatus.ACTIVE),
                Instant.parse(cloud.createdAt), Instant.parse(cloud.updatedAt));
    }
}
