package com.app.winterarc.data.auth;

import android.app.Activity;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.winterarc.core.analytics.CrashReporter;
import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

/** Firebase Phone Auth. SMS sending, rate limiting and abuse protection are handled by Firebase. */
public class FirebasePhoneAuthRepository implements AuthRepository {
    private static final long TIMEOUT_SECONDS = 60;

    private final FirebaseAuth auth;
    private final CrashReporter crashReporter;
    private final MutableLiveData<AuthUser> current = new MutableLiveData<>();

    public FirebasePhoneAuthRepository(FirebaseAuth auth, CrashReporter crashReporter) {
        this.auth = auth;
        this.crashReporter = crashReporter;
        // Send the SMS in the user's language.
        auth.useAppLanguage();
        current.setValue(map(auth.getCurrentUser()));
        auth.addAuthStateListener(a -> current.postValue(map(a.getCurrentUser())));
    }

    @Override
    public LiveData<AuthUser> currentUser() {
        return current;
    }

    @Nullable
    @Override
    public AuthUser getCurrentUser() {
        return map(auth.getCurrentUser());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void sendCode(Activity activity, String e164, @Nullable Parcelable resendToken, SendCallback callback) {
        PhoneAuthOptions.Builder options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(e164)
                .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        // Instant verification or SMS auto-retrieval: sign in without user input.
                        signIn(credential, new VerifyCallback() {
                            @Override
                            public void onSuccess(AuthUser user) {
                                callback.onAutoVerified(user);
                            }

                            @Override
                            public void onError(AuthError error) {
                                callback.onError(error);
                            }
                        });
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        callback.onError(mapError(e));
                    }

                    @Override
                    public void onCodeSent(@NonNull String verificationId,
                                           @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        callback.onCodeSent(verificationId, token);
                    }
                });
        if (resendToken instanceof PhoneAuthProvider.ForceResendingToken) {
            options.setForceResendingToken((PhoneAuthProvider.ForceResendingToken) resendToken);
        }
        PhoneAuthProvider.verifyPhoneNumber(options.build());
    }

    @Override
    public void verifyCode(String verificationId, String code, VerifyCallback callback) {
        signIn(PhoneAuthProvider.getCredential(verificationId, code), callback);
    }

    private void signIn(PhoneAuthCredential credential, VerifyCallback callback) {
        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                AuthUser user = map(task.getResult().getUser());
                if (user != null) {
                    callback.onSuccess(user);
                    return;
                }
            }
            callback.onError(mapError(task.getException()));
        });
    }

    @Override
    public void signOut() {
        auth.signOut();
    }

    @Nullable
    private static AuthUser map(@Nullable FirebaseUser user) {
        if (user == null) return null;
        return new AuthUser(user.getUid(), user.getPhoneNumber());
    }

    AuthError mapError(@Nullable Exception e) {
        if (e instanceof FirebaseTooManyRequestsException) return AuthError.TOO_MANY_REQUESTS;
        if (e instanceof FirebaseNetworkException) return AuthError.NETWORK;
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            switch (code) {
                case "ERROR_INVALID_VERIFICATION_CODE":
                    return AuthError.INVALID_CODE;
                case "ERROR_SESSION_EXPIRED":
                    return AuthError.CODE_EXPIRED;
                case "ERROR_INVALID_PHONE_NUMBER":
                    return AuthError.INVALID_PHONE;
                case "ERROR_QUOTA_EXCEEDED":
                    return AuthError.QUOTA_EXCEEDED;
                default:
                    break;
            }
            if (e instanceof FirebaseAuthInvalidCredentialsException) return AuthError.INVALID_CODE;
        }
        if (e != null) crashReporter.recordNonFatal(e, "phone auth");
        return AuthError.UNKNOWN;
    }
}
