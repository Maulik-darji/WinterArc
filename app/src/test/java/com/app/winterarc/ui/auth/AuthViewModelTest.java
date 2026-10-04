package com.app.winterarc.ui.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Parcelable;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.app.winterarc.core.analytics.AnalyticsEvent;
import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.app.winterarc.domain.auth.Country;
import com.app.winterarc.domain.auth.PhoneNumber;
import com.app.winterarc.testing.FakeTimeProvider;

import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AuthViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    /** Records requests and lets each test decide how the provider responds. */
    static final class ScriptedAuth implements AuthRepository {
        final List<String> sentTo = new ArrayList<>();
        final List<Parcelable> tokens = new ArrayList<>();
        SendCallback pendingSend;
        VerifyCallback pendingVerify;
        String lastCode;

        @Override public LiveData<AuthUser> currentUser() { return new MutableLiveData<>(); }
        @Override public AuthUser getCurrentUser() { return null; }
        @Override public boolean isAvailable() { return true; }

        @Override
        public void sendCode(Activity activity, String e164, Parcelable resendToken, SendCallback callback) {
            sentTo.add(e164);
            tokens.add(resendToken);
            pendingSend = callback;
        }

        @Override
        public void verifyCode(String verificationId, String code, VerifyCallback callback) {
            lastCode = code;
            pendingVerify = callback;
        }

        @Override public void signOut() { }
    }

    private final ScriptedAuth auth = new ScriptedAuth();
    private final FakeTimeProvider time = new FakeTimeProvider(LocalDate.of(2026, 10, 2));
    private final List<AnalyticsEvent> events = new ArrayList<>();

    private AuthViewModel create(SavedStateHandle handle) {
        AuthViewModel vm = new AuthViewModel(handle, auth, time, events::add);
        vm.initCountry("IN");
        return vm;
    }

    @Test
    public void invalidNumberIsRejectedWithoutContactingProvider() {
        AuthViewModel vm = create(new SavedStateHandle());
        vm.setInput("12345");
        assertFalse(vm.sendCode(null));
        assertEquals(PhoneNumber.Problem.TOO_SHORT, vm.state().getValue().phoneProblem());
        assertTrue(auth.sentTo.isEmpty());
    }

    @Test
    public void happyPathSendsCodeThenSignsIn() {
        AuthViewModel vm = create(new SavedStateHandle());
        vm.setInput("98765 43210");
        assertTrue(vm.sendCode(null));
        assertEquals("+919876543210", auth.sentTo.get(0));
        assertTrue(vm.state().getValue().sending());

        auth.pendingSend.onCodeSent("vid-1", null);
        assertNotNull(vm.codeSent().getValue().consume());
        assertFalse(vm.state().getValue().sending());
        assertEquals(30, vm.resendInSeconds());

        vm.verify("123456");
        assertEquals("123456", auth.lastCode);
        assertTrue(vm.state().getValue().verifying());
        auth.pendingVerify.onSuccess(new AuthUser("uid", "+919876543210"));
        assertEquals("uid", vm.signedIn().getValue().consume().uid());
        assertFalse(vm.hasPendingCode());
        assertTrue(events.stream().anyMatch(e -> e.name().equals(AnalyticsEvent.SIGNUP_COMPLETED)));
    }

    @Test
    public void wrongCodeShowsErrorAndAllowsRetry() {
        AuthViewModel vm = create(new SavedStateHandle());
        vm.setInput("9876543210");
        vm.sendCode(null);
        auth.pendingSend.onCodeSent("vid-1", null);
        vm.verify("000000");
        auth.pendingVerify.onError(AuthError.INVALID_CODE);
        assertEquals(AuthError.INVALID_CODE, vm.state().getValue().error());
        assertFalse(vm.state().getValue().verifying());
        vm.verify("123456");
        auth.pendingVerify.onSuccess(new AuthUser("uid", "+919876543210"));
        assertNotNull(vm.signedIn().getValue());
    }

    @Test
    public void malformedCodeNeverReachesProvider() {
        AuthViewModel vm = create(new SavedStateHandle());
        vm.setInput("9876543210");
        vm.sendCode(null);
        auth.pendingSend.onCodeSent("vid-1", null);
        vm.verify("12a4");
        assertNull(auth.lastCode);
        assertEquals(AuthError.INVALID_CODE, vm.state().getValue().error());
    }

    @Test
    public void resendWaitsForCooldownAndUsesToken() {
        AuthViewModel vm = create(new SavedStateHandle());
        vm.setInput("9876543210");
        vm.sendCode(null);
        android.os.Bundle token = new android.os.Bundle();
        auth.pendingSend.onCodeSent("vid-1", token);

        vm.resend(null);
        assertEquals(1, auth.sentTo.size()); // still cooling down
        advanceSeconds(31);
        assertEquals(0, vm.resendInSeconds());
        vm.resend(null);
        assertEquals(2, auth.sentTo.size());
        assertEquals(token, auth.tokens.get(1));
    }

    @Test
    public void autoVerificationSignsInWithoutCode() {
        AuthViewModel vm = create(new SavedStateHandle());
        vm.setInput("9876543210");
        vm.sendCode(null);
        auth.pendingSend.onAutoVerified(new AuthUser("uid", "+919876543210"));
        assertNotNull(vm.signedIn().getValue().consume());
    }

    @Test
    public void sendErrorsAreSurfacedAndNumberIsNotLogged() {
        AuthViewModel vm = create(new SavedStateHandle());
        vm.setInput("9876543210");
        vm.sendCode(null);
        auth.pendingSend.onError(AuthError.TOO_MANY_REQUESTS);
        assertEquals(AuthError.TOO_MANY_REQUESTS, vm.state().getValue().error());
        for (AnalyticsEvent e : events) {
            for (String v : e.properties().values()) assertFalse(v.contains("9876"));
        }
    }

    @Test
    public void verificationSurvivesProcessDeath() {
        SavedStateHandle handle = new SavedStateHandle();
        AuthViewModel vm = create(handle);
        vm.setCountry(Country.forIso("GB"));
        vm.setInput("07700 900123");
        vm.sendCode(null);
        auth.pendingSend.onCodeSent("vid-9", null);

        SavedStateHandle restored = new SavedStateHandle();
        for (String key : handle.keys()) restored.set(key, handle.get(key));
        AuthViewModel again = create(restored);
        assertTrue(again.hasPendingCode());
        assertEquals("+447700900123", again.pendingNumber().e164());
        assertEquals("GB", again.state().getValue().country().iso());
        again.verify("123456");
        assertEquals("123456", auth.lastCode);
    }

    private void advanceSeconds(int seconds) {
        // FakeTimeProvider moves in days; jump via a fresh zoned time instead.
        time.setNow(time.zonedNow().plusSeconds(seconds));
    }
}
