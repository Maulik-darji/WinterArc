package com.app.winterarc.di;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.CrashReporter;
import com.app.winterarc.data.auth.UnavailableAuthRepository;
import com.app.winterarc.demo.DemoTools;
import com.app.winterarc.domain.auth.AuthRepository;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/**
 * Release bindings: analytics and crash reporting are no-ops until a provider is deliberately
 * configured, and demo tools are unavailable.
 */
@Module
@InstallIn(SingletonComponent.class)
public final class BuildTypeModule {
    private BuildTypeModule() {}

    @Provides
    static Analytics analytics() {
        return Analytics.NO_OP;
    }

    @Provides
    static CrashReporter crashReporter() {
        return CrashReporter.NO_OP;
    }

    @Provides
    static DemoTools demoTools() {
        return DemoTools.UNAVAILABLE;
    }

    /** Release never fakes sign-in: without Firebase config, sign-up explains it is unavailable. */
    @Provides
    @Named(AuthModule.FALLBACK)
    static AuthRepository fallbackAuth() {
        return new UnavailableAuthRepository();
    }
}
