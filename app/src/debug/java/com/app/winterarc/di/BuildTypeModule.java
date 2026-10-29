package com.app.winterarc.di;

import android.util.Log;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.CrashReporter;
import com.app.winterarc.data.auth.LocalTestAuthRepository;
import com.app.winterarc.domain.auth.AuthRepository;

import javax.inject.Named;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/** Debug bindings: analytics and errors go to Logcat. */
@Module
@InstallIn(SingletonComponent.class)
public abstract class BuildTypeModule {
    private static final String TAG = "WinterArc";

    @Provides
    static Analytics analytics() {
        return event -> Log.d(TAG, "analytics " + event.name() + " " + event.properties());
    }

    @Provides
    static CrashReporter crashReporter() {
        return new CrashReporter() {
            @Override
            public void recordNonFatal(Throwable throwable, String context) {
                Log.w(TAG, "non-fatal (" + context + ")", throwable);
            }

            @Override
            public void log(String message) {
                Log.d(TAG, message);
            }
        };
    }

    /** Local test accounts when Supabase is not configured (or in instrumented tests). */
    @Binds
    @Named(AuthModule.FALLBACK)
    abstract AuthRepository fallbackAuth(LocalTestAuthRepository impl);
}
