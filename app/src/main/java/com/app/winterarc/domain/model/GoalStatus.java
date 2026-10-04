package com.app.winterarc.domain.model;

public enum GoalStatus implements StableKeys.Keyed {
    ACTIVE("active"),
    PAUSED("paused"),
    COMPLETED("completed"),
    ARCHIVED("archived");

    private final String key;

    GoalStatus(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }

    /** Open goals still accept check-ins. */
    public boolean isOpen() {
        return this == ACTIVE || this == PAUSED;
    }
}
