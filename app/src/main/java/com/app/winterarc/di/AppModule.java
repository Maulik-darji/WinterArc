package com.app.winterarc.di;

import android.content.Context;

import androidx.room.Room;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.core.time.SystemTimeProvider;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.data.db.WinterArcDatabase;
import com.app.winterarc.data.db.dao.GoalDao;
import com.app.winterarc.data.db.dao.MilestoneDao;
import com.app.winterarc.data.db.dao.MotivationalContentDao;
import com.app.winterarc.data.db.dao.PausePeriodDao;
import com.app.winterarc.data.db.dao.ProgressEntryDao;
import com.app.winterarc.data.social.db.SocialDao;
import com.app.winterarc.data.social.db.SocialDatabase;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class AppModule {
    private AppModule() {}

    @Provides
    @Singleton
    static WinterArcDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, WinterArcDatabase.class, WinterArcDatabase.NAME)
                .addMigrations(WinterArcDatabase.MIGRATIONS)
                .build();
    }

    @Provides static GoalDao goalDao(WinterArcDatabase db) { return db.goalDao(); }
    @Provides static ProgressEntryDao progressEntryDao(WinterArcDatabase db) { return db.progressEntryDao(); }
    @Provides static MilestoneDao milestoneDao(WinterArcDatabase db) { return db.milestoneDao(); }
    @Provides static PausePeriodDao pausePeriodDao(WinterArcDatabase db) { return db.pausePeriodDao(); }
    @Provides static MotivationalContentDao contentDao(WinterArcDatabase db) { return db.contentDao(); }

    @Provides
    @Singleton
    static SocialDatabase provideSocialDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, SocialDatabase.class, SocialDatabase.NAME).build();
    }

    @Provides static SocialDao socialDao(SocialDatabase db) { return db.socialDao(); }

    @Provides
    @Singleton
    static TimeProvider timeProvider() {
        return new SystemTimeProvider();
    }

    @Provides
    @Singleton
    static AppExecutors executors() {
        return AppExecutors.create();
    }
}
