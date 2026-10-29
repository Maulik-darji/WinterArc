package com.app.winterarc.di;

import com.app.winterarc.BuildConfig;
import com.app.winterarc.data.auth.SupabaseAuthApi;
import com.app.winterarc.data.remote.SupabaseApi;
import com.app.winterarc.data.remote.SupabaseConfig;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Module
@InstallIn(SingletonComponent.class)
public final class SupabaseModule {
    private SupabaseModule() {}

    @Provides
    @Singleton
    static SupabaseConfig config() {
        return new SupabaseConfig(BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_PUBLISHABLE_KEY, BuildConfig.SUPABASE_CONFIGURED);
    }

    @Provides
    @Singleton
    static Retrofit retrofit(SupabaseConfig config) {
        OkHttpClient http = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("apikey", config.publishableKey())
                        .header("Accept", "application/json")
                        .build()))
                .build();
        return new Retrofit.Builder()
                .baseUrl(config.retrofitBaseUrl())
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    @Provides
    @Singleton
    static SupabaseApi api(Retrofit retrofit) {
        return retrofit.create(SupabaseApi.class);
    }

    @Provides
    @Singleton
    static com.app.winterarc.data.cloud.SocialCloudApi socialCloudApi(Retrofit retrofit) {
        return retrofit.create(com.app.winterarc.data.cloud.SocialCloudApi.class);
    }

    @Provides
    @Singleton
    static SupabaseAuthApi authApi(Retrofit retrofit) {
        return retrofit.create(SupabaseAuthApi.class);
    }
}
