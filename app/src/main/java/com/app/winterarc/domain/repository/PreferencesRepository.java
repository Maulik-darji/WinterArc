package com.app.winterarc.domain.repository;

import androidx.lifecycle.LiveData;

public interface PreferencesRepository {
    LiveData<UserPreferences> observe();

    /** Synchronous read; preferences are small and cached in memory. */
    UserPreferences get();

    void setOnboardingCompleted(boolean completed);

    void setRemindersEnabled(boolean enabled);

    void setThemeMode(ThemeMode mode);

    void setHapticsEnabled(boolean enabled);

    void setNotificationPermissionAsked();
}
