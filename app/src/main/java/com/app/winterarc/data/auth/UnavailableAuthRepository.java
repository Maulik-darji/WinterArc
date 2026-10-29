package com.app.winterarc.data.auth;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;

/** Used by release builds that have no Supabase config. Never pretends to sign in. */
public class UnavailableAuthRepository implements AuthRepository {
    private final MutableLiveData<AuthUser> current = new MutableLiveData<>(null);

    @Override public LiveData<AuthUser> currentUser() { return current; }
    @Nullable @Override public AuthUser getCurrentUser() { return null; }
    @Override public boolean isAvailable() { return false; }
    @Nullable @Override public String googleServerClientId() { return null; }

    @Override public void signUpWithEmail(String name, String email, String password, Callback cb) { cb.onError(AuthError.NOT_CONFIGURED); }
    @Override public void signInWithEmail(String email, String password, Callback cb) { cb.onError(AuthError.NOT_CONFIGURED); }
    @Override public void signInWithGoogle(@Nullable String idToken, Callback cb) { cb.onError(AuthError.NOT_CONFIGURED); }
    @Override public void sendPasswordReset(String email, DoneCallback cb) { cb.onError(AuthError.NOT_CONFIGURED); }
    @Override public void signOut() { }
}
