package com.app.winterarc.testing;

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

public final class TestGoals {
    private TestGoals() {}

    public static Goal consistency(Amount target, WeeklySchedule schedule, LocalDate start) {
        return base(start).trackingMode(TrackingMode.CONSISTENCY).activityType(ActivityType.WALKING)
                .currentTarget(target).schedule(schedule).build();
    }

    public static Goal progression(Amount start, Amount fin, Amount hop, LocalDate startDate) {
        return base(startDate).trackingMode(TrackingMode.PROGRESSION).activityType(ActivityType.RUNNING)
                .currentTarget(start).plan(new ProgressionPlan(start, fin, hop)).build();
    }

    public static Amount km(double value) {
        return Amount.parse(String.valueOf(value));
    }

    private static Goal.Builder base(LocalDate start) {
        return new Goal.Builder()
                .uuid("uuid-" + start)
                .title("Test goal")
                .unit(GoalUnit.KILOMETERS)
                .schedule(WeeklySchedule.EVERY_DAY)
                .startDate(start)
                .reminder(ReminderConfig.DEFAULT)
                .color(GoalColor.EMERALD)
                .icon(GoalIcon.RUN)
                .status(GoalStatus.ACTIVE)
                .createdAt(Instant.EPOCH)
                .updatedAt(Instant.EPOCH);
    }
}
