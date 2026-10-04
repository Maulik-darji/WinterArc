package com.app.winterarc.ui.common;

import android.content.Context;
import android.text.format.DateFormat;

import androidx.annotation.StringRes;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.domain.engine.GoalValidator;
import com.app.winterarc.domain.engine.SafetyAdvisor;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.GoalUnit;
import com.app.winterarc.domain.model.ProgressionPlan;
import com.app.winterarc.domain.model.ReminderConfig;
import com.app.winterarc.domain.model.WeeklySchedule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Turns domain values into user-facing, localised text. */
public final class DomainText {
    private DomainText() {}

    @StringRes
    public static int error(GoalValidator.Error error) {
        switch (error) {
            case TITLE_BLANK: return R.string.error_title_blank;
            case TITLE_TOO_LONG: return R.string.error_title_too_long;
            case DESCRIPTION_TOO_LONG: return R.string.error_description_too_long;
            case CUSTOM_UNIT_BLANK: return R.string.error_custom_unit_blank;
            case TARGET_NOT_POSITIVE: return R.string.error_target_positive;
            case TARGET_TOO_LARGE:
            case FINAL_TOO_LARGE: return R.string.error_target_too_large;
            case START_NOT_POSITIVE: return R.string.error_start_positive;
            case FINAL_NOT_GREATER_THAN_START: return R.string.error_final_greater;
            case HOP_NOT_POSITIVE: return R.string.error_hop_positive;
            case TOO_MANY_STEPS: return R.string.error_too_many_steps;
            case DECIMALS_NOT_SUPPORTED: return R.string.error_decimals;
            default: return R.string.error_no_days;
        }
    }

    public static String errors(Context context, Set<GoalValidator.Error> errors) {
        StringBuilder sb = new StringBuilder();
        for (GoalValidator.Error e : errors) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(context.getString(error(e)));
        }
        return sb.toString();
    }

    public static String safetyReasons(Context context, SafetyAdvisor.Assessment assessment) {
        StringBuilder sb = new StringBuilder();
        for (SafetyAdvisor.Reason r : assessment.reasons()) {
            int res;
            switch (r) {
                case LARGE_HOP: res = R.string.safety_large_hop; break;
                case LONG_DISTANCE: res = R.string.safety_long_distance; break;
                case VERY_LONG_DISTANCE: res = R.string.safety_very_long_distance; break;
                default: res = R.string.safety_no_rest_days; break;
            }
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(context.getString(res));
        }
        return sb.toString();
    }

    public static String amount(Context context, Amount amount, GoalUnit unit, String customLabel) {
        return Formats.amountWithUnit(context, amount, unit, customLabel);
    }

    /** "1 km → 2 km → 3 km → … → 10 km", eliding the middle of long plans. */
    public static String steps(Context context, List<Amount> steps, GoalUnit unit, String customLabel) {
        List<String> parts = new ArrayList<>();
        if (steps.size() <= 5) {
            for (Amount a : steps) parts.add(Formats.amountWithUnit(context, a, unit, customLabel));
        } else {
            for (int i = 0; i < 3; i++) parts.add(Formats.amountWithUnit(context, steps.get(i), unit, customLabel));
            parts.add("…");
            parts.add(Formats.amountWithUnit(context, steps.get(steps.size() - 1), unit, customLabel));
        }
        return String.join(" → ", parts);
    }

    public static String plan(Context context, ProgressionPlan plan, GoalUnit unit, String customLabel) {
        return context.getString(R.string.editor_review_plan_value,
                Formats.amountWithUnit(context, plan.startTarget(), unit, customLabel),
                Formats.amountWithUnit(context, plan.finalTarget(), unit, customLabel),
                Formats.amountWithUnit(context, plan.hop(), unit, customLabel));
    }

    /** "Every day", "Weekdays", or "Mon, Wed, Fri" in locale order. */
    public static String schedule(Context context, WeeklySchedule schedule) {
        if (schedule.isEveryDay()) return context.getString(R.string.editor_days_every_day);
        if (schedule.equals(WeeklySchedule.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))) {
            return context.getString(R.string.editor_days_weekdays);
        }
        Locale locale = Formats.locale(context);
        List<String> names = new ArrayList<>();
        for (DayOfWeek d : orderedDays(locale)) {
            if (schedule.contains(d)) names.add(d.getDisplayName(TextStyle.SHORT, locale));
        }
        return String.join(", ", names);
    }

    /** Days of the week starting from the locale's first day. */
    public static List<DayOfWeek> orderedDays(Locale locale) {
        DayOfWeek first = WeekFields.of(locale).getFirstDayOfWeek();
        List<DayOfWeek> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) days.add(first.plus(i));
        return days;
    }

    public static String time(Context context, LocalTime time) {
        String pattern = DateFormat.is24HourFormat(context) ? "HH:mm" : "h:mm a";
        return DateTimeFormatter.ofPattern(pattern, Formats.locale(context)).format(time);
    }

    public static String reminder(Context context, ReminderConfig reminder) {
        return reminder.enabled() ? time(context, reminder.time()) : context.getString(R.string.editor_reminder_off);
    }
}
