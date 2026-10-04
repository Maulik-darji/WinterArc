package com.app.winterarc.di;

import com.app.winterarc.data.content.BundledContentRepository;
import com.app.winterarc.data.prefs.SharedPreferencesRepository;
import com.app.winterarc.data.repository.RoomGoalRepository;
import com.app.winterarc.data.repository.RoomMilestoneRepository;
import com.app.winterarc.data.repository.RoomProgressRepository;
import com.app.winterarc.data.repository.RoomTransactionRunner;
import com.app.winterarc.data.social.RoomSocialRepository;
import com.app.winterarc.domain.repository.ContentRepository;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.repository.MilestoneRepository;
import com.app.winterarc.domain.repository.PreferencesRepository;
import com.app.winterarc.domain.repository.ProgressRepository;
import com.app.winterarc.domain.repository.ReminderScheduler;
import com.app.winterarc.domain.repository.TransactionRunner;
import com.app.winterarc.domain.repository.SocialRepository;
import com.app.winterarc.reminders.WorkManagerReminderScheduler;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/** Interface bindings. Swap these for cloud-backed implementations when sync is added. */
@Module
@InstallIn(SingletonComponent.class)
public abstract class BindingsModule {
    @Binds abstract GoalRepository goals(RoomGoalRepository impl);
    @Binds abstract ProgressRepository progress(RoomProgressRepository impl);
    @Binds abstract MilestoneRepository milestones(RoomMilestoneRepository impl);
    @Binds abstract ContentRepository content(BundledContentRepository impl);
    @Binds abstract TransactionRunner transactions(RoomTransactionRunner impl);
    @Binds abstract PreferencesRepository preferences(SharedPreferencesRepository impl);
    @Binds abstract ReminderScheduler reminders(WorkManagerReminderScheduler impl);
    @Binds abstract SocialRepository social(RoomSocialRepository impl);
}
