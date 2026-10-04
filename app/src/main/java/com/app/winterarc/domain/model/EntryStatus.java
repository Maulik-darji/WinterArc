package com.app.winterarc.domain.model;

/** Status of a saved day. A scheduled past day with no entry is "missed" (derived, not stored). */
public enum EntryStatus implements StableKeys.Keyed {
    COMPLETED("completed"),
    PARTIAL("partial"),
    SKIPPED("skipped"),
    /** A note was saved but no progress has been logged. Counts as no activity. */
    NOTE_ONLY("note_only");

    private final String key;

    EntryStatus(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
