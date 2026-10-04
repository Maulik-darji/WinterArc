package com.app.winterarc.domain.model;

public enum ActivityType implements StableKeys.Keyed {
    WALKING("walking", GoalUnit.KILOMETERS, GoalIcon.WALK, GoalColor.EMERALD, true),
    RUNNING("running", GoalUnit.KILOMETERS, GoalIcon.RUN, GoalColor.TEAL, true),
    READING("reading", GoalUnit.PAGES, GoalIcon.BOOK, GoalColor.AMBER, false),
    MEDITATION("meditation", GoalUnit.MINUTES, GoalIcon.MINDFUL, GoalColor.VIOLET, false),
    CODING("coding", GoalUnit.SESSIONS, GoalIcon.CODE, GoalColor.SKY, false),
    CUSTOM("custom", GoalUnit.SESSIONS, GoalIcon.STAR, GoalColor.EMERALD, false);

    private final String key;
    private final GoalUnit defaultUnit;
    private final GoalIcon defaultIcon;
    private final GoalColor defaultColor;
    private final boolean endurance;

    ActivityType(String key, GoalUnit unit, GoalIcon icon, GoalColor color, boolean endurance) {
        this.key = key;
        this.defaultUnit = unit;
        this.defaultIcon = icon;
        this.defaultColor = color;
        this.endurance = endurance;
    }

    @Override
    public String key() {
        return key;
    }

    public GoalUnit defaultUnit() {
        return defaultUnit;
    }

    public GoalIcon defaultIcon() {
        return defaultIcon;
    }

    public GoalColor defaultColor() {
        return defaultColor;
    }

    /** Walking and running get first-class treatment: milestone picker and safety guidance. */
    public boolean isEndurance() {
        return endurance;
    }
}
