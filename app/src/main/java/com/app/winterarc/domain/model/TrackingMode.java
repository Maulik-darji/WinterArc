package com.app.winterarc.domain.model;

public enum TrackingMode implements StableKeys.Keyed {
    /** Repeat the same target on every scheduled day. */
    CONSISTENCY("consistency"),
    /** Move from a starting target toward a final milestone, one completed step at a time. */
    PROGRESSION("progression");

    private final String key;

    TrackingMode(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
