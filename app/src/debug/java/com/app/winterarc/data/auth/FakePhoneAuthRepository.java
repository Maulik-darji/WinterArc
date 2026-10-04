package com.app.winterarc.data.auth;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Debug-only stand-in for an SMS provider (lives in src/debug; never in release). No SMS is sent:
 * the code is always {@link #TEST_CODE}. Numbers ending in "0000" fail to send, which makes the
 * error state easy to demo. Used only when no google-services.json is configured.
 */
@Singleton
public class FakePhoneAuthRepository implements AuthRepository {
    public static final String TEST_CODE = "123456";
    private static final long LATENCY_MS = 700;
    private static final String PREFS = "fake_auth";

    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<AuthUser> current = new MutableLiveData<>();

    @Inject
    public FakePhoneAuthRepository(@ApplicationContext Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        current.setValue(getCurrentUser());
    }

    @Override public LiveData<AuthUser> currentUser() { return current; }
    @Override public boolean isAvailable() { return true; }

    @Nullable
    @Override
    public AuthUser getCurrentUser() {
        String uid = prefs.getString("uid", null);
        String phone = prefs.getString("phone", null);
        return uid == null ? null : new AuthUser(uid, phone);
    }

    @Override
    public void sendCode(Activity activity, String e164, @Nullable Parcelable resendToken, SendCallback callback) {
        handler.postDelayed(() -> {
            if (e164.endsWith("0000")) {
                callback.onError(AuthError.TOO_MANY_REQUESTS);
            } else {
                // The "verification id" carries the number so verifyCode can sign that number in.
                Bundle token = new Bundle();
                token.putString("phone", e164);
                callback.onCodeSent("fake:" + e164, token);
            }
        }, LATENCY_MS);
    }

    @Override
    public void verifyCode(String verificationId, String code, VerifyCallback callback) {
        handler.postDelayed(() -> {
            if (!TEST_CODE.equals(code)) {
                callback.onError(AuthError.INVALID_CODE);
                return;
            }
            String phone = verificationId.startsWith("fake:") ? verificationId.substring(5) : verificationId;
            AuthUser user = new AuthUser("fake-" + UUID.nameUUIDFromBytes(phone.getBytes()), phone);
            prefs.edit().putString("uid", user.uid()).putString("phone", user.phoneE164()).apply();
            current.setValue(user);
            callback.onSuccess(user);
        }, LATENCY_MS);
    }

    @Override
    public String testCodeHint() {
        return TEST_CODE;
    }

    @Override
    public void signOut() {
        prefs.edit().clear().apply();
        current.setValue(null);
    }
}
