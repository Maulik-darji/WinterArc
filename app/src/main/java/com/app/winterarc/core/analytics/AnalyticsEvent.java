package com.app.winterarc.core.analytics;

import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.TrackingMode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AnalyticsEvent(String name, Map<String, String> properties) {

    public static final String ONBOARDING_COMPLETED = "onboarding_completed";
    public static final String GOAL_CREATION_STARTED = "goal_creation_started";
    public static final String GOAL_CREATED = "goal_created";
    public static final String GOAL_MODE_SELECTED = "goal_mode_selected";
    public static final String REMINDER_ENABLED = "reminder_enabled";
    public static final String DAILY_TARGET_COMPLETED = "daily_target_completed";
    public static final String PARTIAL_PROGRESS_LOGGED = "partial_progress_logged";
    public static final String GOAL_PAUSED = "goal_paused";
    public static final String PROGRESSION_MILESTONE_REACHED = "progression_milestone_reached";
    public static final String PROGRESSION_CONVERTED_TO_CONSISTENCY = "progression_converted_to_consistency";
    public static final String PROGRESSION_CONTINUED = "progression_continued";
    public static final String GOAL_ARCHIVED = "goal_archived";
    public static final String SIGNUP_STARTED = "signup_started";
    public static final String OTP_REQUESTED = "otp_requested";
    public static final String OTP_FAILED = "otp_failed";
    public static final String SIGNUP_COMPLETED = "signup_completed";

    public static AnalyticsEvent of(String name) {
        return new AnalyticsEvent(name, Collections.emptyMap());
    }

    /** Failure reason only (an enum), never the phone number. */
    public static AnalyticsEvent otpFailed(String reason) {
        return new AnalyticsEvent(OTP_FAILED, Map.of("reason", reason));
    }

    public static AnalyticsEvent modeSelected(TrackingMode mode) {
        return new AnalyticsEvent(GOAL_MODE_SELECTED, Map.of("mode", mode.key()));
    }

    public static AnalyticsEvent goalCreated(ActivityType activity, TrackingMode mode, int activeDays) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("activity", activity.key());
        p.put("mode", mode.key());
        p.put("active_days", String.valueOf(activeDays));
        return new AnalyticsEvent(GOAL_CREATED, Collections.unmodifiableMap(p));
    }

    public static AnalyticsEvent targetCompleted(TrackingMode mode, boolean exceeded) {
        return new AnalyticsEvent(DAILY_TARGET_COMPLETED,
                Map.of("mode", mode.key(), "exceeded", String.valueOf(exceeded)));
    }
}
