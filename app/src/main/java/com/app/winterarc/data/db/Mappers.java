package com.app.winterarc.data.db;

import com.app.winterarc.data.db.entity.GoalEntity;
import com.app.winterarc.data.db.entity.MilestoneEntity;
import com.app.winterarc.data.db.entity.MotivationalContentEntity;
import com.app.winterarc.data.db.entity.PausePeriodEntity;
import com.app.winterarc.data.db.entity.ProgressEntryEntity;
import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.CelebrationState;
import com.app.winterarc.domain.model.ContentType;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalColor;
import com.app.winterarc.domain.model.GoalIcon;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.GoalUnit;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.domain.model.MotivationalContent;
import com.app.winterarc.domain.model.PausePeriod;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.model.ProgressionPlan;
import com.app.winterarc.domain.model.ReminderConfig;
import com.app.winterarc.domain.model.SkipReason;
import com.app.winterarc.domain.model.TrackingMode;
import com.app.winterarc.domain.model.WeeklySchedule;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.app.winterarc.domain.model.StableKeys.fromKey;

/** Pure entity <-> domain mapping. Unknown stored keys fall back to safe defaults. */
public final class Mappers {
    private Mappers() {}

    public static <A, B> List<B> mapList(List<A> in, Function<A, B> fn) {
        List<B> out = new ArrayList<>(in == null ? 0 : in.size());
        if (in != null) for (A a : in) out.add(fn.apply(a));
        return out;
    }

    public static Goal toDomain(GoalEntity e) {
        if (e == null) return null;
        TrackingMode mode = fromKey(TrackingMode.class, e.trackingMode, TrackingMode.CONSISTENCY);
        ProgressionPlan plan = null;
        if (mode == TrackingMode.PROGRESSION && e.startTargetMilli != null && e.finalTargetMilli != null
                && e.hopMilli != null) {
            plan = new ProgressionPlan(new Amount(e.startTargetMilli), new Amount(e.finalTargetMilli),
                    new Amount(e.hopMilli));
        }
        return new Goal(e.id, e.uuid, e.title, e.description,
                fromKey(ActivityType.class, e.activityType, ActivityType.CUSTOM),
                plan == null ? TrackingMode.CONSISTENCY : mode,
                fromKey(GoalUnit.class, e.unit, GoalUnit.SESSIONS),
                e.customUnitLabel,
                new Amount(e.currentTargetMilli),
                plan,
                new WeeklySchedule(e.scheduleMask & WeeklySchedule.ALL_MASK),
                e.startDate,
                e.endDate,
                new ReminderConfig(e.reminderEnabled, LocalTime.ofSecondOfDay(
                        Math.floorMod(e.reminderMinuteOfDay, 24 * 60) * 60L)),
                fromKey(GoalColor.class, e.color, GoalColor.EMERALD),
                fromKey(GoalIcon.class, e.icon, GoalIcon.STAR),
                fromKey(GoalStatus.class, e.status, GoalStatus.ACTIVE),
                e.createdAt,
                e.updatedAt);
    }

    public static GoalEntity toEntity(Goal g) {
        GoalEntity e = new GoalEntity();
        e.id = g.id();
        e.uuid = g.uuid();
        e.title = g.title();
        e.description = g.description();
        e.activityType = g.activityType().key();
        e.trackingMode = g.trackingMode().key();
        e.unit = g.unit().key();
        e.customUnitLabel = g.customUnitLabel();
        e.currentTargetMilli = g.currentTarget().milli();
        if (g.plan() != null) {
            e.startTargetMilli = g.plan().startTarget().milli();
            e.finalTargetMilli = g.plan().finalTarget().milli();
            e.hopMilli = g.plan().hop().milli();
        }
        e.scheduleMask = g.schedule().mask();
        e.startDate = g.startDate();
        e.endDate = g.endDate();
        e.reminderEnabled = g.reminder().enabled();
        e.reminderMinuteOfDay = g.reminder().time().getHour() * 60 + g.reminder().time().getMinute();
        e.color = g.color().key();
        e.icon = g.icon().key();
        e.status = g.status().key();
        e.createdAt = g.createdAt();
        e.updatedAt = g.updatedAt();
        return e;
    }

    public static ProgressEntry toDomain(ProgressEntryEntity e) {
        if (e == null) return null;
        return new ProgressEntry(e.id, e.uuid, e.goalId, e.date,
                new Amount(e.scheduledTargetMilli), new Amount(e.actualMilli),
                fromKey(EntryStatus.class, e.status, EntryStatus.NOTE_ONLY),
                e.skipReason == null ? null : fromKey(SkipReason.class, e.skipReason, SkipReason.OTHER),
                e.note, e.progressionLevel, e.advancedProgression, e.createdAt, e.updatedAt);
    }

    public static ProgressEntryEntity toEntity(ProgressEntry p) {
        ProgressEntryEntity e = new ProgressEntryEntity();
        e.id = p.id();
        e.uuid = p.uuid();
        e.goalId = p.goalId();
        e.date = p.date();
        e.scheduledTargetMilli = p.scheduledTarget().milli();
        e.actualMilli = p.actualValue().milli();
        e.status = p.status().key();
        e.skipReason = p.skipReason() == null ? null : p.skipReason().key();
        e.note = p.note();
        e.progressionLevel = p.progressionLevel();
        e.advancedProgression = p.advancedProgression();
        e.createdAt = p.createdAt();
        e.updatedAt = p.updatedAt();
        return e;
    }

    public static Milestone toDomain(MilestoneEntity e) {
        if (e == null) return null;
        return new Milestone(e.id, e.goalId, new Amount(e.targetMilli), e.achievedDate,
                fromKey(CelebrationState.class, e.celebrationState, CelebrationState.PENDING), e.createdAt);
    }

    public static MilestoneEntity toEntity(Milestone m) {
        MilestoneEntity e = new MilestoneEntity();
        e.id = m.id();
        e.goalId = m.goalId();
        e.targetMilli = m.targetValue().milli();
        e.achievedDate = m.achievedDate();
        e.celebrationState = m.celebrationState().key();
        e.createdAt = m.createdAt();
        return e;
    }

    public static PausePeriod toDomain(PausePeriodEntity e) {
        if (e == null) return null;
        return new PausePeriod(e.id, e.goalId, e.startDate, e.endDate);
    }

    public static PausePeriodEntity toEntity(PausePeriod p) {
        PausePeriodEntity e = new PausePeriodEntity();
        e.id = p.id();
        e.goalId = p.goalId();
        e.startDate = p.startDate();
        e.endDate = p.endDate();
        return e;
    }

    public static MotivationalContent toDomain(MotivationalContentEntity e) {
        return new MotivationalContent(e.id,
                fromKey(ContentType.class, e.contentType, ContentType.ENCOURAGEMENT),
                e.activityType == null ? null : fromKey(ActivityType.class, e.activityType, null),
                e.minTargetMilli == null ? null : new Amount(e.minTargetMilli),
                e.maxTargetMilli == null ? null : new Amount(e.maxTargetMilli),
                e.message, e.attribution, e.sourceName, e.sourceUrl, e.locale);
    }
}
