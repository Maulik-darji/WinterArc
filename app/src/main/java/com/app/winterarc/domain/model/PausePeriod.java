package com.app.winterarc.domain.model;

import androidx.annotation.Nullable;

import java.time.LocalDate;

/** A period during which the goal was paused. Paused days never count as missed. */
public record PausePeriod(long id, long goalId, LocalDate startDate, @Nullable LocalDate endDate) {

    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }

    public PausePeriod withEndDate(@Nullable LocalDate value) {
        return new PausePeriod(id, goalId, startDate, value);
    }
}
