package com.app.winterarc.domain.engine;

/** What a single calendar day means for a goal. Derived, never stored. */
public enum DayKind {
    BEFORE_START,
    AFTER_END,
    FUTURE,
    /** Today, scheduled, nothing logged yet. Never counts as missed. */
    TODAY_PENDING,
    NOT_SCHEDULED,
    PAUSED,
    /** A scheduled past day with no progress. */
    MISSED,
    SKIPPED,
    PARTIAL,
    COMPLETED,
    EXCEEDED;

    public boolean isCompletion() {
        return this == COMPLETED || this == EXCEEDED;
    }

    /** Days that end a streak. Rest, pauses, unscheduled days and today are neutral. */
    public boolean breaksStreak() {
        return this == MISSED || this == PARTIAL;
    }
}
