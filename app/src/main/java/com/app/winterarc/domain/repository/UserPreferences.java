package com.app.winterarc.domain.repository;

public record UserPreferences(
        boolean onboardingCompleted,
        boolean remindersEnabled,
        ThemeMode themeMode,
        boolean hapticsEnabled,
        boolean notificationPermissionAsked) {

    public static final UserPreferences DEFAULT =
            new UserPreferences(false, true, ThemeMode.SYSTEM, true, false);
}
