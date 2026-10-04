package com.app.winterarc.domain.model;

public enum SkipReason implements StableKeys.Keyed {
    REST("rest"),
    UNWELL("unwell"),
    WEATHER("weather"),
    BUSY("busy"),
    OTHER("other");

    private final String key;

    SkipReason(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
