package com.app.winterarc;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.domain.repository.ContentRepository;
import com.app.winterarc.domain.repository.PreferencesRepository;
import com.app.winterarc.domain.repository.ReminderScheduler;
import com.app.winterarc.reminders.Notifications;
import com.app.winterarc.ui.common.ThemeController;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class WinterArcApplication extends Application implements Configuration.Provider {

    @Inject HiltWorkerFactory workerFactory;
    @Inject AppExecutors executors;
    @Inject ContentRepository content;
    @Inject ReminderScheduler reminders;
    @Inject PreferencesRepository preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeController.apply(preferences.get().themeMode());
        Notifications.createChannels(this);
        executors.io().execute(() -> {
            content.ensureSeeded();
            // Re-arm reminders after updates, restores, or time-zone changes.
            reminders.rescheduleAll();
        });
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().setWorkerFactory(workerFactory).build();
    }
}
