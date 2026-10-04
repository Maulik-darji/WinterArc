package com.app.winterarc.data.db;

import androidx.room.TypeConverter;

import java.time.Instant;
import java.time.LocalDate;

public final class Converters {
    @TypeConverter
    public static Long fromLocalDate(LocalDate date) {
        return date == null ? null : date.toEpochDay();
    }

    @TypeConverter
    public static LocalDate toLocalDate(Long epochDay) {
        return epochDay == null ? null : LocalDate.ofEpochDay(epochDay);
    }

    @TypeConverter
    public static Long fromInstant(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    @TypeConverter
    public static Instant toInstant(Long millis) {
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }
}
