package com.app.winterarc.domain.model;

public enum CelebrationState implements StableKeys.Keyed {
    /** Achieved; the user has not yet chosen what to do next. */
    PENDING("pending"),
    MAINTAINED("maintained"),
    CONTINUED("continued"),
    COMPLETED("completed");

    private final String key;

    CelebrationState(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
