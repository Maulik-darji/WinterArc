package com.app.winterarc.domain.model;

public enum GoalIcon implements StableKeys.Keyed {
    WALK("walk"),
    RUN("run"),
    HIKE("hike"),
    BIKE("bike"),
    SWIM("swim"),
    BOOK("book"),
    MINDFUL("mindful"),
    CODE("code"),
    STRENGTH("strength"),
    MUSIC("music"),
    WRITE("write"),
    STAR("star");

    private final String key;

    GoalIcon(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
