package com.app.winterarc.di;

import android.util.Log;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.CrashReporter;
import com.app.winterarc.data.auth.FakePhoneAuthRepository;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.demo.DebugDemoTools;
import com.app.winterarc.demo.DemoTools;

import javax.inject.Named;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/** Debug bindings: analytics and errors go to Logcat, and demo tools are available. */
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

    @Binds
    abstract DemoTools demoTools(DebugDemoTools impl);

    /** Fake OTP provider (code 123456) when Firebase is not configured. */
    @Binds
    @Named(AuthModule.FALLBACK)
    abstract AuthRepository fallbackAuth(FakePhoneAuthRepository impl);
}
