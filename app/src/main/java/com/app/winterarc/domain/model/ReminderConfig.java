package com.app.winterarc.domain.model;

import java.time.LocalTime;

public record ReminderConfig(boolean enabled, LocalTime time) implements java.io.Serializable {
    public static final ReminderConfig DEFAULT = new ReminderConfig(false, LocalTime.of(7, 0));

    public ReminderConfig withEnabled(boolean value) {
        return new ReminderConfig(value, time);
    }

    public ReminderConfig withTime(LocalTime value) {
        return new ReminderConfig(enabled, value);
    }
}
