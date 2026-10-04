package com.app.winterarc.domain.model;

public enum ContentType implements StableKeys.Keyed {
    ENCOURAGEMENT("encouragement"),
    MILESTONE("milestone"),
    FACT("fact"),
    QUOTE("quote"),
    SAFETY("safety");

    private final String key;

    ContentType(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
