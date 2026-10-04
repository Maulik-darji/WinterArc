package com.app.winterarc.ui.common;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.app.winterarc.R;
import com.app.winterarc.domain.engine.DayKind;
import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.GoalColor;
import com.app.winterarc.domain.model.GoalIcon;
import com.app.winterarc.domain.model.SkipReason;
import com.app.winterarc.domain.model.TrackingMode;

/** Maps domain enums to resources. Kept in one place so every screen renders goals consistently. */
public final class GoalVisuals {
    private GoalVisuals() {}

    @ColorInt
    public static int color(Context context, GoalColor color) {
        int res;
        switch (color) {
            case TEAL: res = R.color.goal_teal; break;
            case VIOLET: res = R.color.goal_violet; break;
            case AMBER: res = R.color.goal_amber; break;
            case SKY: res = R.color.goal_sky; break;
            case ROSE: res = R.color.goal_rose; break;
            default: res = R.color.goal_emerald; break;
        }
        return ContextCompat.getColor(context, res);
    }

    /** A soft tint of the goal color for icon badges and containers. */
    @ColorInt
    public static int container(Context context, GoalColor color) {
        int surface = ContextCompat.getColor(context, R.color.wa_surface);
        return ColorUtils.blendARGB(surface, color(context, color), 0.16f);
    }

    @DrawableRes
    public static int icon(GoalIcon icon) {
        switch (icon) {
            case WALK: return R.drawable.ic_goal_walk;
            case RUN: return R.drawable.ic_goal_run;
            case HIKE: return R.drawable.ic_goal_hike;
            case BIKE: return R.drawable.ic_goal_bike;
            case SWIM: return R.drawable.ic_goal_swim;
            case BOOK: return R.drawable.ic_goal_book;
            case MINDFUL: return R.drawable.ic_goal_mindful;
            case CODE: return R.drawable.ic_goal_code;
            case STRENGTH: return R.drawable.ic_goal_strength;
            case MUSIC: return R.drawable.ic_goal_music;
            case WRITE: return R.drawable.ic_goal_write;
            default: return R.drawable.ic_goal_star;
        }
    }

    @StringRes
    public static int activityName(ActivityType type) {
        switch (type) {
            case WALKING: return R.string.activity_walking;
            case RUNNING: return R.string.activity_running;
            case READING: return R.string.activity_reading;
            case MEDITATION: return R.string.activity_meditation;
            case CODING: return R.string.activity_coding;
            default: return R.string.activity_custom;
        }
    }

    @StringRes
    public static int modeName(TrackingMode mode) {
        return mode == TrackingMode.PROGRESSION ? R.string.mode_progression : R.string.mode_consistency;
    }

    @DrawableRes
    public static int modeIcon(TrackingMode mode) {
        return mode == TrackingMode.PROGRESSION ? R.drawable.ic_trending_up : R.drawable.ic_repeat;
    }

    @StringRes
    public static int skipReason(SkipReason reason) {
        switch (reason) {
            case UNWELL: return R.string.skip_reason_unwell;
            case WEATHER: return R.string.skip_reason_weather;
            case BUSY: return R.string.skip_reason_busy;
            case OTHER: return R.string.skip_reason_other;
            default: return R.string.skip_reason_rest;
        }
    }

    /** Spoken/visible description of a day; never relies on color alone. */
    @StringRes
    public static int dayKind(DayKind kind) {
        switch (kind) {
            case COMPLETED: return R.string.day_kind_completed;
            case EXCEEDED: return R.string.day_kind_exceeded;
            case PARTIAL: return R.string.day_kind_partial;
            case MISSED: return R.string.day_kind_missed;
            case SKIPPED: return R.string.day_kind_skipped;
            case PAUSED: return R.string.day_kind_paused;
            case NOT_SCHEDULED: return R.string.day_kind_not_scheduled;
            case TODAY_PENDING: return R.string.day_kind_today;
            case FUTURE: return R.string.day_kind_future;
            default: return R.string.day_kind_outside;
        }
    }
}
