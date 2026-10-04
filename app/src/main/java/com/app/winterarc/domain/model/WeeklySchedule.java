package com.app.winterarc.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The days of the week a goal is active on, stored as a 7-bit mask (bit 0 = Monday). */
public record WeeklySchedule(int mask) implements java.io.Serializable {

    public static final int ALL_MASK = 0b111_1111;
    public static final WeeklySchedule EVERY_DAY = new WeeklySchedule(ALL_MASK);

    public WeeklySchedule {
        if (mask < 0 || mask > ALL_MASK) throw new IllegalArgumentException("Invalid schedule mask " + mask);
    }

    public static WeeklySchedule of(DayOfWeek... days) {
        int mask = 0;
        for (DayOfWeek day : days) mask |= bit(day);
        return new WeeklySchedule(mask);
    }

    public boolean contains(DayOfWeek day) {
        return (mask & bit(day)) != 0;
    }

    public boolean isScheduled(LocalDate date) {
        return contains(date.getDayOfWeek());
    }

    public WeeklySchedule with(DayOfWeek day, boolean enabled) {
        return new WeeklySchedule(enabled ? (mask | bit(day)) : (mask & ~bit(day)));
    }

    public List<DayOfWeek> days() {
        List<DayOfWeek> result = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) if (contains(day)) result.add(day);
        return result;
    }

    public int dayCount() {
        return Integer.bitCount(mask);
    }

    public boolean isEmpty() {
        return mask == 0;
    }

    public boolean isEveryDay() {
        return mask == ALL_MASK;
    }

    private static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }
}
