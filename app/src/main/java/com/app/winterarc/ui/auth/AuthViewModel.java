package com.app.winterarc.ui.auth;

import android.app.Activity;
import android.os.Parcelable;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.AnalyticsEvent;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.app.winterarc.domain.auth.Country;
import com.app.winterarc.domain.auth.PhoneNumber;
import com.app.winterarc.ui.common.Event;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Phone sign-up state shared by the phone and code screens (activity-scoped). Everything needed
 * to resume — number, verification id, resend token and cooldown — lives in SavedStateHandle,
 * so an SMS-app switch that kills the process doesn't lose the flow.
 */
@HiltViewModel
public class AuthViewModel extends ViewModel {
    public static final int CODE_LENGTH = 6;
    public static final long RESEND_COOLDOWN_MS = 30_000;

    private static final String KEY_COUNTRY = "country";
    private static final String KEY_INPUT = "input";
    private static final String KEY_E164 = "e164";
    private static final String KEY_VERIFICATION_ID = "verification_id";
    private static final String KEY_RESEND_TOKEN = "resend_token";
    private static final String KEY_RESEND_AT = "resend_at";

    public record State(Country country, String input, @Nullable PhoneNumber phone,
                        @Nullable PhoneNumber.Problem phoneProblem, boolean sending, boolean verifying,
                        @Nullable AuthError error, int resendInSeconds, boolean available) {}

    private final SavedStateHandle saved;
    private final AuthRepository auth;
    private final TimeProvider time;
    private final Analytics analytics;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> codeSent = new MutableLiveData<>();
    private final MutableLiveData<Event<AuthUser>> signedIn = new MutableLiveData<>();

    @Nullable private PhoneNumber.Problem phoneProblem;
    private boolean sending;
    private boolean verifying;
    @Nullable private AuthError error;
    private boolean startedTracked;

    @Inject
    public AuthViewModel(SavedStateHandle saved, AuthRepository auth, TimeProvider time, Analytics analytics) {
        this.saved = saved;
        this.auth = auth;
        this.time = time;
        this.analytics = analytics;
        publish();
    }

    public LiveData<State> state() { return state; }
    public LiveData<Event<Boolean>> codeSent() { return codeSent; }
    public LiveData<Event<AuthUser>> signedIn() { return signedIn; }

    /** Sets the default region once (from SIM/network or locale); user choices are kept. */
    public void initCountry(@Nullable String iso) {
        if (saved.get(KEY_COUNTRY) == null) {
            saved.set(KEY_COUNTRY, Country.forIso(iso).iso());
            publish();
        }
        if (!startedTracked) {
            startedTracked = true;
            analytics.track(AnalyticsEvent.of(AnalyticsEvent.SIGNUP_STARTED));
        }
    }

    public void setCountry(Country country) {
        saved.set(KEY_COUNTRY, country.iso());
        phoneProblem = null;
        error = null;
        publish();
    }

    public void setInput(String text) {
        if (text.equals(saved.get(KEY_INPUT))) return;
        saved.set(KEY_INPUT, text);
        phoneProblem = null;
        error = null;
        publish();
    }

    // ---- Send / resend ----------------------------------------------------------------------

    /** Validates the number and requests a code. Returns false if the number is invalid. */
    public boolean sendCode(Activity activity) {
        if (sending) return true;
        PhoneNumber.Result result = PhoneNumber.parse(country(), input());
        if (!result.isValid()) {
            phoneProblem = result.problem();
            publish();
            return false;
        }
        PhoneNumber number = result.number();
        boolean sameNumber = number.e164().equals(saved.get(KEY_E164));
        // Re-entering the same number during the cooldown goes straight to the code screen.
        if (sameNumber && saved.get(KEY_VERIFICATION_ID) != null && resendInSeconds() > 0) {
            codeSent.setValue(new Event<>(true));
            return true;
        }
        saved.set(KEY_E164, number.e164());
        if (!sameNumber) {
            saved.set(KEY_VERIFICATION_ID, null);
            saved.set(KEY_RESEND_TOKEN, null);
        }
        request(activity, number.e164(), sameNumber ? saved.get(KEY_RESEND_TOKEN) : null, true);
        return true;
    }

    public void resend(Activity activity) {
        String e164 = saved.get(KEY_E164);
        if (e164 == null || sending || resendInSeconds() > 0) return;
        request(activity, e164, saved.get(KEY_RESEND_TOKEN), false);
    }

    private void request(Activity activity, String e164, @Nullable Parcelable token, boolean navigate) {
        sending = true;
        error = null;
        publish();
        analytics.track(AnalyticsEvent.of(AnalyticsEvent.OTP_REQUESTED));
        auth.sendCode(activity, e164, token, new AuthRepository.SendCallback() {
            @Override
            public void onCodeSent(String verificationId, @Nullable Parcelable resendToken) {
                sending = false;
                saved.set(KEY_VERIFICATION_ID, verificationId);
                saved.set(KEY_RESEND_TOKEN, resendToken);
                saved.set(KEY_RESEND_AT, time.now().toEpochMilli() + RESEND_COOLDOWN_MS);
                publish();
                if (navigate) codeSent.setValue(new Event<>(true));
            }

            @Override
            public void onAutoVerified(AuthUser user) {
                sending = false;
                complete(user);
            }

            @Override
            public void onError(AuthError e) {
                sending = false;
                fail(e);
            }
        });
    }

    // ---- Verify -----------------------------------------------------------------------------

    public void verify(String code) {
        String id = saved.get(KEY_VERIFICATION_ID);
        if (verifying || id == null) return;
        if (code == null || !code.matches("\\d{" + CODE_LENGTH + "}")) {
            fail(AuthError.INVALID_CODE);
            return;
        }
        verifying = true;
        error = null;
        publish();
        auth.verifyCode(id, code, new AuthRepository.VerifyCallback() {
            @Override
            public void onSuccess(AuthUser user) {
                verifying = false;
                complete(user);
            }

            @Override
            public void onError(AuthError e) {
                verifying = false;
                fail(e);
            }
        });
    }

    /** Back to the number screen; the code request is kept in case the number doesn't change. */
    public void clearError() {
        error = null;
        publish();
    }

    private void complete(AuthUser user) {
        analytics.track(AnalyticsEvent.of(AnalyticsEvent.SIGNUP_COMPLETED));
        saved.set(KEY_VERIFICATION_ID, null);
        saved.set(KEY_RESEND_TOKEN, null);
        publish();
        signedIn.setValue(new Event<>(user));
    }

    private void fail(AuthError e) {
        error = e;
        analytics.track(AnalyticsEvent.otpFailed(e.name().toLowerCase(java.util.Locale.ROOT)));
        publish();
    }

    // ---- State ------------------------------------------------------------------------------

    /** Called every second by the code screen to refresh the resend countdown. */
    public void tick() {
        publish();
    }

    public int resendInSeconds() {
        Long at = saved.get(KEY_RESEND_AT);
        if (at == null) return 0;
        long remaining = at - time.now().toEpochMilli();
        return remaining <= 0 ? 0 : (int) Math.ceil(remaining / 1000.0);
    }

    @Nullable
    public PhoneNumber pendingNumber() {
        String e164 = saved.get(KEY_E164);
        if (e164 == null) return null;
        Country c = country();
        return e164.startsWith(c.prefix()) ? new PhoneNumber(c, e164.substring(c.prefix().length()), e164) : null;
    }

    public boolean hasPendingCode() {
        return saved.get(KEY_VERIFICATION_ID) != null;
    }

    private Country country() {
        return Country.forIso(saved.get(KEY_COUNTRY));
    }

    private String input() {
        String s = saved.get(KEY_INPUT);
        return s == null ? "" : s;
    }

    private void publish() {
        state.setValue(new State(country(), input(), pendingNumber(), phoneProblem, sending, verifying, error,
                resendInSeconds(), auth.isAvailable()));
    }
}
