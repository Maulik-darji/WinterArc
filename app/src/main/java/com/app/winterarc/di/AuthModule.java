package com.app.winterarc.di;

import android.content.Context;

import com.app.winterarc.BuildConfig;
import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.core.analytics.CrashReporter;
import com.app.winterarc.data.auth.KeystoreSessionStorage;
import com.app.winterarc.data.auth.SupabaseAuthApi;
import com.app.winterarc.data.auth.SupabaseAuthRepository;
import com.app.winterarc.data.remote.SupabaseConfig;
import com.app.winterarc.domain.auth.AuthRepository;

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
     * Supabase Auth when SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY are set; otherwise the
     * build-type fallback: a local test provider in debug, an explanatory "unavailable" in release.
     * Instrumented-test builds always use the local provider so tests never create real accounts.
     */
    @Provides
    @Singleton
    static AuthRepository auth(@ApplicationContext Context context, SupabaseConfig config, SupabaseAuthApi api,
                               AppExecutors executors, CrashReporter crashReporter,
                               @Named(FALLBACK) javax.inject.Provider<AuthRepository> fallback) {
        if (config.configured() && BuildConfig.USE_SUPABASE_AUTH) {
            return new SupabaseAuthRepository(api, new KeystoreSessionStorage(context), executors, crashReporter,
                    BuildConfig.GOOGLE_WEB_CLIENT_ID, () -> System.currentTimeMillis() / 1000);
        }
        return fallback.get();
    }
}
