package com.app.winterarc.di;

import android.content.Context;

import com.app.winterarc.core.analytics.CrashReporter;
import com.app.winterarc.data.auth.FirebasePhoneAuthRepository;
import com.app.winterarc.domain.auth.AuthRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class AuthModule {
    public static final String FALLBACK = "fallbackAuth";

    private AuthModule() {}

    /**
     * Firebase when the app is configured (google-services.json present); otherwise the
     * build-type fallback: a fake OTP provider in debug, an explanatory "unavailable" in release.
     */
    @Provides
    @Singleton
    static AuthRepository auth(@ApplicationContext Context context, CrashReporter crashReporter,
                               @Named(FALLBACK) javax.inject.Provider<AuthRepository> fallback) {
        if (!FirebaseApp.getApps(context).isEmpty()) {
            return new FirebasePhoneAuthRepository(FirebaseAuth.getInstance(), crashReporter);
        }
        return fallback.get();
    }
}
