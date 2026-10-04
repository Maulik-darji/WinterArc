package com.app.winterarc.domain.model;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Mutable user input for creating or editing a goal, before validation. Serializable so the
 * creation wizard survives process death via SavedStateHandle.
 */
public final class GoalDraft implements Serializable {
    private static final long serialVersionUID = 1L;

    public String title = "";
    public String description = "";
    public ActivityType activityType = ActivityType.WALKING;
    public TrackingMode trackingMode = TrackingMode.CONSISTENCY;
    public GoalUnit unit = GoalUnit.KILOMETERS;
    public String customUnitLabel = "";
    /** Recurring target for consistency goals. */
    public Amount consistencyTarget = Amount.whole(5);
    public Amount startTarget = Amount.whole(1);
    public Amount finalTarget = Amount.whole(10);
    public Amount hop = Amount.whole(1);
    public WeeklySchedule schedule = WeeklySchedule.EVERY_DAY;
    public LocalDate startDate;
    public ReminderConfig reminder = ReminderConfig.DEFAULT;
    public GoalColor color = GoalColor.EMERALD;
    public GoalIcon icon = GoalIcon.WALK;

    @Nullable
    public ProgressionPlan plan() {
        if (trackingMode != TrackingMode.PROGRESSION) return null;
        return new ProgressionPlan(startTarget, finalTarget, hop);
    }

    /** The target the user will face first. */
    public Amount initialTarget() {
        return trackingMode == TrackingMode.PROGRESSION ? startTarget : consistencyTarget;
    }

    public GoalDraft copy() {
        GoalDraft d = new GoalDraft();
        d.title = title;
        d.description = description;
        d.activityType = activityType;
        d.trackingMode = trackingMode;
        d.unit = unit;
        d.customUnitLabel = customUnitLabel;
        d.consistencyTarget = consistencyTarget;
        d.startTarget = startTarget;
        d.finalTarget = finalTarget;
        d.hop = hop;
        d.schedule = schedule;
        d.startDate = startDate;
        d.reminder = reminder;
        d.color = color;
        d.icon = icon;
        return d;
    }

    /** Resets activity-dependent fields to sensible defaults for {@code activity}. */
    public void applyActivityDefaults(ActivityType activity) {
        activityType = activity;
        unit = activity.defaultUnit();
        icon = activity.defaultIcon();
        color = activity.defaultColor();
        switch (activity) {
            case WALKING:
                set(5, 2, 10, 1);
                break;
            case RUNNING:
                set(3, 1, 10, 1);
                break;
            case READING:
                set(20, 10, 40, 5);
                break;
            case MEDITATION:
                set(10, 5, 30, 5);
                break;
            default:
                set(1, 1, 5, 1);
                break;
        }
    }

    private void set(int consistency, int start, int fin, int step) {
        consistencyTarget = Amount.whole(consistency);
        startTarget = Amount.whole(start);
        finalTarget = Amount.whole(fin);
        hop = Amount.whole(step);
    }

    public static GoalDraft forActivity(ActivityType activity, LocalDate today) {
        GoalDraft d = new GoalDraft();
        d.applyActivityDefaults(activity);
        d.startDate = today;
        return d;
    }

    public static GoalDraft fromGoal(Goal goal) {
        GoalDraft d = new GoalDraft();
        d.title = goal.title();
        d.description = goal.description() == null ? "" : goal.description();
        d.activityType = goal.activityType();
        d.trackingMode = goal.trackingMode();
        d.unit = goal.unit();
        d.customUnitLabel = goal.customUnitLabel() == null ? "" : goal.customUnitLabel();
        d.consistencyTarget = goal.currentTarget();
        ProgressionPlan plan = goal.plan();
        d.startTarget = plan != null ? plan.startTarget() : goal.currentTarget();
        d.finalTarget = plan != null ? plan.finalTarget() : goal.currentTarget().plus(Amount.whole(5));
        d.hop = plan != null ? plan.hop() : Amount.whole(1);
        d.schedule = goal.schedule();
        d.startDate = goal.startDate();
        d.reminder = goal.reminder();
        d.color = goal.color();
        d.icon = goal.icon();
        return d;
    }
}
