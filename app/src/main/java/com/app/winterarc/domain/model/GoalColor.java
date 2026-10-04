package com.app.winterarc.domain.model;

public enum GoalColor implements StableKeys.Keyed {
    EMERALD("emerald"),
    TEAL("teal"),
    VIOLET("violet"),
    AMBER("amber"),
    SKY("sky"),
    ROSE("rose");

    private final String key;

    GoalColor(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
