package com.app.winterarc.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.app.winterarc.domain.auth.Credentials;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Debug-only stand-in for Supabase Auth (src/debug; never in release), used when no
 * Supabase is configured (or while instrumented tests run). Accounts live in this app's private storage with hashed
 * passwords; "Continue with Google" signs into a fixed test account without the system picker.
 */
@Singleton
public class LocalTestAuthRepository implements AuthRepository {
    private static final long LATENCY_MS = 500;
    private static final String PREFS = "local_test_auth";

    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<AuthUser> current = new MutableLiveData<>();

    @Inject
    public LocalTestAuthRepository(@ApplicationContext Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        current.setValue(getCurrentUser());
    }

    @Override public LiveData<AuthUser> currentUser() { return current; }
    @Override public boolean isAvailable() { return true; }
    @Override public String googleServerClientId() { return TEST_GOOGLE_CLIENT; }
    @Override public String testModeHint() { return "Test mode: accounts are stored on this device only."; }

    @Nullable
    @Override
    public AuthUser getCurrentUser() {
        String uid = prefs.getString("session.uid", null);
        if (uid == null) return null;
        return new AuthUser(uid, prefs.getString("session.email", null), prefs.getString("session.name", null), true);
    }

    @Override
    public void signUpWithEmail(String name, String email, String password, Callback callback) {
        String e = Credentials.normalizeEmail(email);
        later(() -> {
            if (prefs.contains(key(e, "hash"))) {
                callback.onError(AuthError.EMAIL_IN_USE);
                return;
            }
            String uid = "local-" + UUID.randomUUID();
            prefs.edit().putString(key(e, "hash"), hash(e, password)).putString(key(e, "uid"), uid)
                    .putString(key(e, "name"), name.trim()).apply();
            start(uid, e, name.trim(), callback);
        });
    }

    @Override
    public void signInWithEmail(String email, String password, Callback callback) {
        String e = Credentials.normalizeEmail(email);
        later(() -> {
            String stored = prefs.getString(key(e, "hash"), null);
            if (stored == null || !stored.equals(hash(e, password))) {
                callback.onError(AuthError.WRONG_CREDENTIALS);
                return;
            }
            start(prefs.getString(key(e, "uid"), e), e, prefs.getString(key(e, "name"), null), callback);
        });
    }

    @Override
    public void signInWithGoogle(@Nullable String idToken, Callback callback) {
        later(() -> start("local-google-test", "test.user@gmail.com", "Test Google account", callback));
    }

    @Override
    public void sendPasswordReset(String email, DoneCallback callback) {
        later(callback::onDone);
    }

    @Override
    public void signOut() {
        prefs.edit().remove("session.uid").remove("session.email").remove("session.name").apply();
        current.setValue(null);
    }

    private void start(String uid, String email, @Nullable String name, Callback callback) {
        prefs.edit().putString("session.uid", uid).putString("session.email", email).putString("session.name", name).apply();
        AuthUser user = getCurrentUser();
        current.setValue(user);
        callback.onSuccess(user);
    }

    private void later(Runnable r) {
        handler.postDelayed(r, LATENCY_MS);
    }

    private static String key(String email, String field) {
        return "account." + email + "." + field;
    }

    private static String hash(String email, String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((email + ":" + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
