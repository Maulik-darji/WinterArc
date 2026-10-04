package com.app.winterarc.domain.engine;

import androidx.annotation.Nullable;

import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.GoalDraft;
import com.app.winterarc.domain.model.TrackingMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gentle, non-diagnostic plan review for walking and running. A NOTICE informs; CONFIRM asks the
 * user to acknowledge an unusually aggressive plan. It never prevents saving.
 */
public final class SafetyAdvisor {

    private SafetyAdvisor() {}

    public enum Reason {
        /** Large jump between consecutive targets. */
        LARGE_HOP,
        /** Half-marathon-scale distances or more. */
        LONG_DISTANCE,
        /** Ultra-scale distances. */
        VERY_LONG_DISTANCE,
        /** Progressive running every day with no rest days. */
        NO_REST_DAYS
    }

    public enum Level { NONE, NOTICE, CONFIRM }

    public record Assessment(Level level, List<Reason> reasons) {
        public static final Assessment NONE = new Assessment(Level.NONE, Collections.emptyList());
    }

    public static Assessment assess(GoalDraft draft) {
        boolean progression = draft.trackingMode == TrackingMode.PROGRESSION;
        return assess(draft.activityType, draft.trackingMode == TrackingMode.PROGRESSION,
                draft.unit.toKilometers(draft.initialTarget()),
                progression ? draft.unit.toKilometers(draft.finalTarget) : null,
                progression ? draft.unit.toKilometers(draft.hop) : null,
                draft.schedule.isEveryDay());
    }

    /** All distances in kilometres; a null start means the unit is not a distance. */
    public static Assessment assess(ActivityType activity, boolean progression, @Nullable Amount startKm,
                                    @Nullable Amount finalKm, @Nullable Amount hopKm, boolean everyDay) {
        if (!activity.isEndurance() || startKm == null) return Assessment.NONE;
        Amount peakKm = finalKm != null ? finalKm : startKm;
        // Walking tolerates roughly twice the distance of running at a comparable effort.
        int factor = activity == ActivityType.WALKING ? 2 : 1;

        List<Reason> confirm = new ArrayList<>();
        List<Reason> notice = new ArrayList<>();
        if (progression && hopKm != null) {
            if (hopKm.greaterThan(Amount.whole(5L * factor))) {
                confirm.add(Reason.LARGE_HOP);
            } else if (hopKm.greaterThan(Amount.whole(2L * factor))) {
                notice.add(Reason.LARGE_HOP);
            }
        }
        if (peakKm.greaterThan(Amount.whole(50L * factor)) || startKm.greaterThan(Amount.whole(30L * factor))) {
            confirm.add(Reason.VERY_LONG_DISTANCE);
        } else if (peakKm.atLeast(new Amount(21_100L * factor))) {
            notice.add(Reason.LONG_DISTANCE);
        }
        if (activity == ActivityType.RUNNING && progression && everyDay) {
            notice.add(Reason.NO_REST_DAYS);
        }

        if (!confirm.isEmpty()) {
            List<Reason> all = new ArrayList<>(confirm);
            all.addAll(notice);
            return new Assessment(Level.CONFIRM, all);
        }
        if (!notice.isEmpty()) return new Assessment(Level.NOTICE, notice);
        return Assessment.NONE;
    }
}
