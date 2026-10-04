package com.app.winterarc.core.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Single source of "now". Days are resolved in the device's current zone at call time, so
 * travelling or DST transitions never shift already-saved LocalDates.
 */
public interface TimeProvider {
    Instant now();

    ZoneId zone();

    default LocalDate today() {
        return now().atZone(zone()).toLocalDate();
    }

    default ZonedDateTime zonedNow() {
        return now().atZone(zone());
    }
}
