package com.app.winterarc.domain.auth;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

/**
 * Account contract: Google sign-in or email + password. Implemented by Supabase Auth; debug builds
 * without Supabase settings use a local test provider. Callbacks arrive on the main thread.
 */
public interface AuthRepository {

    interface Callback {
        void onSuccess(AuthUser user);

        void onError(AuthError error);
    }

    interface DoneCallback {
        void onDone();

        void onError(AuthError error);
    }

    interface TokenCallback {
        void onToken(String accessToken);

        void onError(Throwable error);
    }

    LiveData<AuthUser> currentUser();

    @Nullable
    AuthUser getCurrentUser();

    /** False when no provider is configured; the UI explains instead of failing silently. */
    boolean isAvailable();

    /**
     * The OAuth web client id used to request a Google ID token, or null when Google sign-in is
     * not configured. Test providers return {@link #TEST_GOOGLE_CLIENT} and skip the system picker.
     */
    @Nullable
    String googleServerClientId();

    String TEST_GOOGLE_CLIENT = "test";

    @MainThread
    void signUpWithEmail(String name, String email, String password, Callback callback);

    @MainThread
    void signInWithEmail(String email, String password, Callback callback);

    /** Exchanges a Google ID token (null for test providers) for an app session. */
    @MainThread
    void signInWithGoogle(@Nullable String idToken, Callback callback);

    @MainThread
    void sendPasswordReset(String email, DoneCallback callback);

    void signOut();

    /**
     * A valid Supabase access token (refreshed when close to expiry) for authenticated data calls.
     * Callbacks may arrive on any thread. Providers without a cloud session report an error.
     */
    default void getAccessToken(TokenCallback callback) {
        callback.onError(new IllegalStateException("No cloud session: sign in with a Supabase account"));
    }

    /** True when the account lives in Supabase, so social data can sync to the cloud. */
    default boolean isCloudAccount() {
        return false;
    }

    /** Debug test provider only: a hint shown on the sign-in screens. Real providers return null. */
    @Nullable
    default String testModeHint() {
        return null;
    }
}
