package com.app.winterarc.domain.auth;

import android.app.Activity;
import android.os.Parcelable;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

/**
 * Phone-number sign-in contract. Implemented by Firebase Phone Auth; debug builds without a
 * Firebase config use a fake. Callbacks are delivered on the main thread.
 */
public interface AuthRepository {

    interface SendCallback {
        /** An SMS was sent. Keep the id (and token, for resending) to verify the code. */
        void onCodeSent(String verificationId, @Nullable Parcelable resendToken);

        /** The provider verified the number without user input (instant or SMS auto-retrieval). */
        void onAutoVerified(AuthUser user);

        void onError(AuthError error);
    }

    interface VerifyCallback {
        void onSuccess(AuthUser user);

        void onError(AuthError error);
    }

    LiveData<AuthUser> currentUser();

    @Nullable
    AuthUser getCurrentUser();

    /** False when no provider is configured; the UI then explains instead of failing silently. */
    boolean isAvailable();

    /**
     * Sends a one-time code. The activity is only used by the provider for its app-verification
     * fallback (reCAPTCHA) and is not retained beyond the request.
     */
    @MainThread
    void sendCode(Activity activity, String e164, @Nullable Parcelable resendToken, SendCallback callback);

    @MainThread
    void verifyCode(String verificationId, String code, VerifyCallback callback);

    void signOut();

    /** Debug fake only: the fixed code to show as a hint. Real providers return null. */
    @Nullable
    default String testCodeHint() {
        return null;
    }
}
