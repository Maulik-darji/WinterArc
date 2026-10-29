package com.app.winterarc.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.app.winterarc.domain.repository.PreferencesRepository;
import com.app.winterarc.domain.repository.ReminderScheduler;
import com.app.winterarc.domain.repository.ThemeMode;
import com.app.winterarc.domain.repository.UserPreferences;
import com.app.winterarc.ui.common.Event;
import com.app.winterarc.ui.common.ThemeController;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class SettingsViewModel extends ViewModel {
    private final PreferencesRepository preferences;
    private final ReminderScheduler reminders;
    private final AppExecutors executors;
    private final AuthRepository auth;

    @Inject
    public SettingsViewModel(PreferencesRepository preferences, ReminderScheduler reminders,
                             AppExecutors executors, AuthRepository auth) {
        this.auth = auth;
        this.preferences = preferences;
        this.reminders = reminders;
        this.executors = executors;
    }

    public LiveData<UserPreferences> preferences() {
        return preferences.observe();
    }

    public LiveData<AuthUser> account() {
        return auth.currentUser();
    }

    public void signOut() {
        auth.signOut();
    }


    public void setTheme(ThemeMode mode) {
        if (preferences.get().themeMode() == mode) return;
        preferences.setThemeMode(mode);
        ThemeController.apply(mode);
    }

    public void setHaptics(boolean enabled) {
        preferences.setHapticsEnabled(enabled);
    }

    public void setReminders(boolean enabled) {
        if (preferences.get().remindersEnabled() == enabled) return;
        preferences.setRemindersEnabled(enabled);
        executors.io().execute(reminders::rescheduleAll);
    }

    public void replayOnboarding() {
        preferences.setOnboardingCompleted(false);
    }


}
