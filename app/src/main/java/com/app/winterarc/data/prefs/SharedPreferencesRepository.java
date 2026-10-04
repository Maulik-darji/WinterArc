package com.app.winterarc.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.winterarc.domain.repository.PreferencesRepository;
import com.app.winterarc.domain.repository.ThemeMode;
import com.app.winterarc.domain.repository.UserPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Small user preferences. Jetpack DataStore is designed for Kotlin coroutines; in this Java app
 * SharedPreferences (written with apply(), observed through LiveData) is the idiomatic choice.
 */
@Singleton
public class SharedPreferencesRepository implements PreferencesRepository {
    private static final String FILE = "winterarc_prefs";
    private static final String KEY_ONBOARDING = "onboarding_completed";
    private static final String KEY_REMINDERS = "reminders_enabled";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_HAPTICS = "haptics_enabled";
    private static final String KEY_NOTIF_ASKED = "notification_permission_asked";

    private final SharedPreferences prefs;
    private final MutableLiveData<UserPreferences> live;

    @Inject
    public SharedPreferencesRepository(@ApplicationContext Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        live = new MutableLiveData<>(read());
    }

    @Override
    public LiveData<UserPreferences> observe() {
        return live;
    }

    @Override
    public UserPreferences get() {
        return read();
    }

    @Override
    public void setOnboardingCompleted(boolean completed) {
        prefs.edit().putBoolean(KEY_ONBOARDING, completed).apply();
        publish();
    }

    @Override
    public void setRemindersEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMINDERS, enabled).apply();
        publish();
    }

    @Override
    public void setThemeMode(ThemeMode mode) {
        prefs.edit().putString(KEY_THEME, mode.name()).apply();
        publish();
    }

    @Override
    public void setHapticsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply();
        publish();
    }

    @Override
    public void setNotificationPermissionAsked() {
        prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply();
        publish();
    }

    private void publish() {
        live.postValue(read());
    }

    private UserPreferences read() {
        UserPreferences d = UserPreferences.DEFAULT;
        ThemeMode theme;
        try {
            theme = ThemeMode.valueOf(prefs.getString(KEY_THEME, d.themeMode().name()));
        } catch (IllegalArgumentException e) {
            theme = ThemeMode.SYSTEM;
        }
        return new UserPreferences(
                prefs.getBoolean(KEY_ONBOARDING, d.onboardingCompleted()),
                prefs.getBoolean(KEY_REMINDERS, d.remindersEnabled()),
                theme,
                prefs.getBoolean(KEY_HAPTICS, d.hapticsEnabled()),
                prefs.getBoolean(KEY_NOTIF_ASKED, d.notificationPermissionAsked()));
    }
}
