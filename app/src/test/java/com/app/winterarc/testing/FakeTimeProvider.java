package com.app.winterarc.testing;

import com.app.winterarc.core.time.TimeProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class FakeTimeProvider implements TimeProvider {
    private ZonedDateTime now;

    public FakeTimeProvider(LocalDate today) {
        this(ZonedDateTime.of(today, LocalTime.NOON, ZoneId.of("Europe/London")));
    }

    public FakeTimeProvider(ZonedDateTime now) {
        this.now = now;
    }

    public void setToday(LocalDate date) {
        now = ZonedDateTime.of(date, now.toLocalTime(), now.getZone());
    }

    public void setNow(ZonedDateTime value) {
        now = value;
    }

    public void advanceDays(int days) {
        now = now.plusDays(days);
    }

    @Override
    public Instant now() {
        return now.toInstant();
    }

    @Override
    public ZoneId zone() {
        return now.getZone();
    }
}
