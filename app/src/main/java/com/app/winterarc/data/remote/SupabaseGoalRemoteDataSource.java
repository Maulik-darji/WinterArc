package com.app.winterarc.data.remote;

import androidx.annotation.Nullable;

import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.model.Goal;

import java.time.Instant;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Authenticated Supabase operations. Room remains the UI-facing source of truth. */
@Singleton
public final class SupabaseGoalRemoteDataSource {
    public interface ResultCallback<T> {
        void onSuccess(T value);
        void onError(Throwable error);
    }

    private final SupabaseConfig config;
    private final SupabaseApi api;
    private final AuthRepository auth;

    @Inject
    public SupabaseGoalRemoteDataSource(SupabaseConfig config, SupabaseApi api, AuthRepository auth) {
        this.config = config;
        this.api = api;
        this.auth = auth;
    }

    public boolean isConfigured() {
        return config.configured();
    }

    public void getGoals(@Nullable Instant changedAfter, ResultCallback<List<CloudGoal>> callback) {
        withToken(callback, bearer -> api.getGoals(bearer,
                changedAfter == null ? null : "gt." + changedAfter, "updated_at.asc")
                .enqueue(retrofitCallback(callback)));
    }

    public void upsertGoal(Goal goal, ResultCallback<CloudGoal> callback) {
        withToken(callback, bearer -> api.upsertGoal(bearer, "uuid",
                CloudGoalMapper.fromDomain(goal)).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<CloudGoal>> call, Response<List<CloudGoal>> response) {
                List<CloudGoal> body = response.body();
                if (response.isSuccessful() && body != null && !body.isEmpty()) {
                    callback.onSuccess(body.get(0));
                } else {
                    callback.onError(httpError(response));
                }
            }

            @Override
            public void onFailure(Call<List<CloudGoal>> call, Throwable error) {
                callback.onError(error);
            }
        }));
    }

    private <T> void withToken(ResultCallback<T> callback, AuthorizedCall call) {
        if (!config.configured()) {
            callback.onError(new IllegalStateException(
                    "Supabase is not configured; set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY"));
            return;
        }
        auth.getAccessToken(new AuthRepository.TokenCallback() {
            @Override public void onToken(String token) { call.run("Bearer " + token); }
            @Override public void onError(Throwable error) { callback.onError(error); }
        });
    }

    private static Callback<List<CloudGoal>> retrofitCallback(
            ResultCallback<List<CloudGoal>> callback) {
        return new Callback<>() {
            @Override
            public void onResponse(Call<List<CloudGoal>> call, Response<List<CloudGoal>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(httpError(response));
                }
            }

            @Override
            public void onFailure(Call<List<CloudGoal>> call, Throwable error) {
                callback.onError(error);
            }
        };
    }

    private static IllegalStateException httpError(Response<?> response) {
        return new IllegalStateException("Supabase request failed with HTTP " + response.code());
    }

    private interface AuthorizedCall {
        void run(String bearer);
    }
}
