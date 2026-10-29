package com.app.winterarc.ui.auth;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.AnalyticsEvent;
import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.app.winterarc.domain.auth.Credentials;
import com.app.winterarc.ui.common.Event;

import java.util.Collections;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * State shared by the welcome, sign-up and log-in screens (activity-scoped). Form text lives in the
 * EditTexts (restored by the framework); this holds progress, errors and one-shot results.
 */
@HiltViewModel
public class AuthViewModel extends ViewModel {

    /** {@code problems} are inline field errors; {@code error} is a provider failure. */
    public record State(boolean busy, Set<Credentials.Problem> problems, @Nullable AuthError error,
                        boolean available, boolean googleAvailable, @Nullable String testHint) {}

    private final AuthRepository auth;
    private final Analytics analytics;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<Event<AuthUser>> signedIn = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> resetSent = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> confirmEmail = new MutableLiveData<>();

    private boolean busy;
    private Set<Credentials.Problem> problems = Collections.emptySet();
    @Nullable private AuthError error;

    @Inject
    public AuthViewModel(AuthRepository auth, Analytics analytics) {
        this.auth = auth;
        this.analytics = analytics;
        analytics.track(AnalyticsEvent.of(AnalyticsEvent.SIGNUP_STARTED));
        publish();
    }

    public LiveData<State> state() { return state; }
    public LiveData<Event<AuthUser>> signedIn() { return signedIn; }
    public LiveData<Event<String>> resetSent() { return resetSent; }

    /** Sign-up succeeded but the account waits for the emailed confirmation link (value: the email). */
    public LiveData<Event<String>> confirmEmail() { return confirmEmail; }

    @Nullable
    public String googleServerClientId() {
        return auth.googleServerClientId();
    }

    /** Clears errors when the user switches screens or edits a field. */
    public void clearErrors() {
        if (problems.isEmpty() && error == null) return;
        problems = Collections.emptySet();
        error = null;
        publish();
    }

    public boolean signUp(String name, String email, String password, String confirm) {
        if (busy) return false;
        problems = Credentials.validateSignUp(name, email, password, confirm);
        error = null;
        if (!problems.isEmpty()) {
            publish();
            return false;
        }
        start();
        String normalized = Credentials.normalizeEmail(email);
        auth.signUpWithEmail(name.trim(), email, password, callback("email_signup", normalized));
        return true;
    }

    public boolean logIn(String email, String password) {
        if (busy) return false;
        problems = Credentials.validateLogIn(email, password);
        error = null;
        if (!problems.isEmpty()) {
            publish();
            return false;
        }
        start();
        auth.signInWithEmail(email, password, callback("email_login", null));
        return true;
    }

    /** Called with the ID token from the Google account picker (null for the test provider). */
    public void googleToken(@Nullable String idToken) {
        if (busy) return;
        start();
        auth.signInWithGoogle(idToken, callback("google", null));
    }

    /** The Google picker failed or was dismissed before an app session started. */
    public void googleFailed(AuthError reason) {
        busy = false;
        // Dismissing the picker is a choice, not an error worth shouting about.
        error = reason == AuthError.GOOGLE_CANCELLED ? null : reason;
        publish();
    }

    public boolean sendReset(String email) {
        if (busy) return false;
        problems = Credentials.validateEmail(email);
        error = null;
        if (!problems.isEmpty()) {
            publish();
            return false;
        }
        start();
        String normalized = Credentials.normalizeEmail(email);
        auth.sendPasswordReset(normalized, new AuthRepository.DoneCallback() {
            @Override
            public void onDone() {
                busy = false;
                publish();
                resetSent.setValue(new Event<>(normalized));
            }

            @Override
            public void onError(AuthError e) {
                fail(e);
            }
        });
        return true;
    }

    private void start() {
        busy = true;
        publish();
    }

    private AuthRepository.Callback callback(String method, @Nullable String confirmationEmail) {
        return new AuthRepository.Callback() {
            @Override
            public void onSuccess(AuthUser user) {
                busy = false;
                analytics.track(new AnalyticsEvent(AnalyticsEvent.SIGNUP_COMPLETED, java.util.Map.of("method", method)));
                publish();
                signedIn.setValue(new Event<>(user));
            }

            @Override
            public void onError(AuthError e) {
                if (e == AuthError.CONFIRM_EMAIL && confirmationEmail != null) {
                    busy = false;
                    analytics.track(new AnalyticsEvent(AnalyticsEvent.SIGNUP_COMPLETED, java.util.Map.of("method", method)));
                    publish();
                    confirmEmail.setValue(new Event<>(confirmationEmail));
                    return;
                }
                fail(e);
            }
        };
    }

    private void fail(AuthError e) {
        busy = false;
        error = e;
        analytics.track(AnalyticsEvent.authFailed(e.name().toLowerCase(java.util.Locale.ROOT)));
        publish();
    }

    private void publish() {
        state.setValue(new State(busy, problems, error, auth.isAvailable(), auth.googleServerClientId() != null,
                auth.testModeHint()));
    }
}
