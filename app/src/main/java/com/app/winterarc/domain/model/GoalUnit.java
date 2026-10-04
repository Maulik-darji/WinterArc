package com.app.winterarc.domain.model;

import androidx.annotation.Nullable;

public enum GoalUnit implements StableKeys.Keyed {
    KILOMETERS("km", true, true),
    MILES("mi", true, true),
    MINUTES("min", false, false),
    PAGES("pages", false, false),
    REPETITIONS("reps", false, false),
    SESSIONS("sessions", false, false),
    CUSTOM("custom", true, false);

    private final String key;
    private final boolean supportsDecimal;
    private final boolean distance;

    GoalUnit(String key, boolean supportsDecimal, boolean distance) {
        this.key = key;
        this.supportsDecimal = supportsDecimal;
        this.distance = distance;
    }

    @Override
    public String key() {
        return key;
    }

    public boolean supportsDecimal() {
        return supportsDecimal;
    }

    public boolean isDistance() {
        return distance;
    }

    /** Converts an amount of this unit to kilometres, or null when the unit is not a distance. */
    @Nullable
    public Amount toKilometers(@Nullable Amount amount) {
        if (amount == null) return null;
        switch (this) {
            case KILOMETERS:
                return amount;
            case MILES:
                return new Amount(amount.milli() * 1_609_344L / 1_000_000L);
            default:
                return null;
        }
    }
}
