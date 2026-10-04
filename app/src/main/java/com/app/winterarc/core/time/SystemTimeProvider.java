package com.app.winterarc.core.time;

import java.time.Instant;
import java.time.ZoneId;

public final class SystemTimeProvider implements TimeProvider {
    @Override
    public Instant now() {
        return Instant.now();
    }

    /** Read on every call so a time-zone change is picked up without a restart. */
    @Override
    public ZoneId zone() {
        return ZoneId.systemDefault();
    }
}
