package com.app.winterarc.data.auth;

import android.app.Activity;
import android.os.Parcelable;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;

/** Used by release builds that have no SMS provider configured. Never pretends to sign in. */
public class UnavailableAuthRepository implements AuthRepository {
    private final MutableLiveData<AuthUser> current = new MutableLiveData<>(null);

    @Override public LiveData<AuthUser> currentUser() { return current; }
    @Nullable @Override public AuthUser getCurrentUser() { return null; }
    @Override public boolean isAvailable() { return false; }

    @Override
    public void sendCode(Activity activity, String e164, @Nullable Parcelable resendToken, SendCallback callback) {
        callback.onError(AuthError.NOT_CONFIGURED);
    }

    @Override
    public void verifyCode(String verificationId, String code, VerifyCallback callback) {
        callback.onError(AuthError.NOT_CONFIGURED);
    }

    @Override public void signOut() { }
}
