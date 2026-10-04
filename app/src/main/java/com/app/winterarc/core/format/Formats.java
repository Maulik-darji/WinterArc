package com.app.winterarc.core.format;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;

import com.app.winterarc.R;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.GoalUnit;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Locale-aware display formatting. Formatted strings are never stored. */
public final class Formats {
    private Formats() {}

    public static Locale locale(Context context) {
        return context.getResources().getConfiguration().getLocales().get(0);
    }

    /** "5", "5.5", "21.1" using the locale's decimal separator; at most two decimals. */
    public static String amount(Context context, Amount amount) {
        return amount(locale(context), amount);
    }

    public static String amount(Locale locale, Amount amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        nf.setGroupingUsed(true);
        return nf.format(amount.toBigDecimal());
    }

    /** Two-digit picker style ("05") for whole numbers below 100. */
    public static String twoDigits(Context context, long value) {
        return String.format(locale(context), "%02d", value);
    }

    public static String amountWithUnit(Context context, Amount amount, GoalUnit unit, @Nullable String customLabel) {
        String number = amount(context, amount);
        int quantity = amount.isWhole() && amount.wholePart() <= Integer.MAX_VALUE ? (int) amount.wholePart() : 2;
        switch (unit) {
            case KILOMETERS:
                return context.getString(R.string.unit_km_value, number);
            case MILES:
                return context.getString(R.string.unit_mi_value, number);
            case CUSTOM:
                return context.getString(R.string.unit_custom_value, number,
                        customLabel == null ? "" : customLabel);
            default:
                return context.getResources().getQuantityString(pluralFor(unit), quantity, number);
        }
    }

    /** Short unit label for pickers, e.g. "km", "min", "pages". */
    public static String unitLabel(Context context, GoalUnit unit, @Nullable String customLabel) {
        switch (unit) {
            case KILOMETERS: return context.getString(R.string.unit_km);
            case MILES: return context.getString(R.string.unit_mi);
            case MINUTES: return context.getString(R.string.unit_min);
            case PAGES: return context.getString(R.string.unit_pages);
            case REPETITIONS: return context.getString(R.string.unit_reps);
            case SESSIONS: return context.getString(R.string.unit_sessions);
            default: return customLabel == null || customLabel.isBlank()
                    ? context.getString(R.string.unit_custom) : customLabel;
        }
    }

    public static String unitName(Context context, GoalUnit unit) {
        switch (unit) {
            case KILOMETERS: return context.getString(R.string.unit_name_km);
            case MILES: return context.getString(R.string.unit_name_mi);
            case MINUTES: return context.getString(R.string.unit_name_min);
            case PAGES: return context.getString(R.string.unit_name_pages);
            case REPETITIONS: return context.getString(R.string.unit_name_reps);
            case SESSIONS: return context.getString(R.string.unit_name_sessions);
            default: return context.getString(R.string.unit_name_custom);
        }
    }

    @PluralsRes
    private static int pluralFor(GoalUnit unit) {
        switch (unit) {
            case MINUTES: return R.plurals.unit_min_value;
            case PAGES: return R.plurals.unit_pages_value;
            case REPETITIONS: return R.plurals.unit_reps_value;
            default: return R.plurals.unit_sessions_value;
        }
    }

    public static String percent(Context context, float fraction) {
        NumberFormat nf = NumberFormat.getPercentInstance(locale(context));
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.max(0f, Math.min(1f, fraction)));
    }

    public static String date(Context context, LocalDate date) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale(context)).format(date);
    }

    public static String longDate(Context context, LocalDate date) {
        return DateTimeFormatter.ofPattern("EEEE, d MMMM", locale(context)).format(date);
    }
}
