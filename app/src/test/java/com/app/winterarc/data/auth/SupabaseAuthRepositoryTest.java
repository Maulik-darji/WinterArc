package com.app.winterarc.data.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.core.analytics.CrashReporter;
import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Talks to a fake Supabase Auth server: no network, no real accounts. */
public class SupabaseAuthRepositoryTest {
    private static final String SESSION = "{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-1\","
            + "\"expires_in\":3600,\"expires_at\":%d,\"token_type\":\"bearer\",\"user\":{\"id\":\"u-1\","
            + "\"email\":\"maya@example.com\",\"email_confirmed_at\":\"2026-10-27T10:00:00Z\","
            + "\"user_metadata\":{\"full_name\":\"Maya Rao\"}}}";

    private MockWebServer server;
    private MemoryStorage storage;
    private AtomicLong now;
    private SupabaseAuthRepository repo;

    static final class MemoryStorage implements SessionStorage {
        String value;
        @Override public String read() { return value; }
        @Override public void write(String v) { value = v; }
        @Override public void clear() { value = null; }
    }

    private SupabaseAuthRepository create(String googleClient) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(new OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return new SupabaseAuthRepository(retrofit.create(SupabaseAuthApi.class), storage,
                new AppExecutors(Runnable::run, Runnable::run), CrashReporter.NO_OP, googleClient, now::get);
    }

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        storage = new MemoryStorage();
        now = new AtomicLong(1_000_000);
        repo = create("web-client.apps.googleusercontent.com");
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private String session(long expiresAt) {
        return String.format(SESSION, expiresAt);
    }

    private static final class Result {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<AuthUser> user = new AtomicReference<>();
        final AtomicReference<AuthError> error = new AtomicReference<>();
    }

    private Result signIn() throws Exception {
        Result r = new Result();
        repo.signInWithEmail(" Maya@Example.com ", "winter2026", callback(r));
        assertTrue(r.done.await(5, TimeUnit.SECONDS));
        return r;
    }

    private static AuthRepository.Callback callback(Result r) {
        return new AuthRepository.Callback() {
            @Override public void onSuccess(AuthUser user) { r.user.set(user); r.done.countDown(); }
            @Override public void onError(AuthError error) { r.error.set(error); r.done.countDown(); }
        };
    }

    private Result signUp() throws Exception {
        Result r = new Result();
        repo.signUpWithEmail("Maya Rao", "maya@example.com", "winter2026", callback(r));
        assertTrue(r.done.await(5, TimeUnit.SECONDS));
        return r;
    }

    private static MockResponse error(int status, String body) {
        return new MockResponse().setResponseCode(status).setBody(body);
    }

    @Test
    public void signInStoresSessionAndSendsNormalizedEmail() throws Exception {
        server.enqueue(new MockResponse().setBody(session(now.get() + 3600)));
        Result r = signIn();
        assertNull(r.error.get());
        assertEquals("u-1", r.user.get().uid());
        assertEquals("Maya Rao", r.user.get().displayName());
        assertTrue(r.user.get().emailVerified());
        assertEquals("u-1", repo.getCurrentUser().uid());

        RecordedRequest request = server.takeRequest();
        assertEquals("/auth/v1/token?grant_type=password", request.getPath());
        JsonObject body = JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("maya@example.com", body.get("email").getAsString());
        assertNotNull(storage.value);

        // A fresh repository (app restart) restores the session without any network call.
        SupabaseAuthRepository restarted = create(null);
        assertEquals("maya@example.com", restarted.getCurrentUser().email());
        assertNull(restarted.googleServerClientId());
    }

    @Test
    public void wrongPasswordAndUnconfirmedEmailAreMapped() throws Exception {
        server.enqueue(error(400, "{\"code\":400,\"error_code\":\"invalid_credentials\",\"msg\":\"Invalid login credentials\"}"));
        assertEquals(AuthError.WRONG_CREDENTIALS, signIn().error.get());
        server.enqueue(error(400, "{\"code\":400,\"error_code\":\"email_not_confirmed\",\"msg\":\"Email not confirmed\"}"));
        assertEquals(AuthError.EMAIL_NOT_CONFIRMED, signIn().error.get());
        server.enqueue(error(429, "{\"code\":429,\"error_code\":\"over_request_rate_limit\",\"msg\":\"Too many\"}"));
        assertEquals(AuthError.TOO_MANY_REQUESTS, signIn().error.get());
        assertNull(repo.getCurrentUser());
    }

    @Test
    public void signUpWithSessionSignsIn() throws Exception {
        server.enqueue(new MockResponse().setBody(session(now.get() + 3600)));
        Result r = signUp();
        assertEquals("u-1", r.user.get().uid());
        RecordedRequest request = server.takeRequest();
        assertEquals("/auth/v1/signup", request.getPath());
        JsonObject body = JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("Maya Rao", body.getAsJsonObject("data").get("full_name").getAsString());
    }

    @Test
    public void signUpNeedingConfirmationAndDuplicateEmails() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"id\":\"u-2\",\"email\":\"maya@example.com\",\"identities\":[{\"id\":\"x\"}]}"));
        assertEquals(AuthError.CONFIRM_EMAIL, signUp().error.get());
        assertNull(repo.getCurrentUser());
        // Supabase hides existing accounts by returning a user with no identities.
        server.enqueue(new MockResponse().setBody("{\"id\":\"u-3\",\"email\":\"maya@example.com\",\"identities\":[]}"));
        assertEquals(AuthError.EMAIL_IN_USE, signUp().error.get());
        server.enqueue(error(422, "{\"code\":422,\"error_code\":\"weak_password\",\"msg\":\"Password is too weak\"}"));
        assertEquals(AuthError.WEAK_PASSWORD, signUp().error.get());
    }

    @Test
    public void googleIdTokenIsExchanged() throws Exception {
        server.enqueue(new MockResponse().setBody(session(now.get() + 3600)));
        Result r = new Result();
        repo.signInWithGoogle("google-id-token", callback(r));
        assertTrue(r.done.await(5, TimeUnit.SECONDS));
        assertEquals("u-1", r.user.get().uid());
        RecordedRequest request = server.takeRequest();
        assertEquals("/auth/v1/token?grant_type=id_token", request.getPath());
        JsonObject body = JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("google", body.get("provider").getAsString());
        assertEquals("google-id-token", body.get("id_token").getAsString());

        Result missing = new Result();
        repo.signInWithGoogle(null, callback(missing));
        assertEquals(AuthError.GOOGLE_NO_ACCOUNT, missing.error.get());
    }

    @Test
    public void tokenIsReusedUntilNearExpiryThenRefreshed() throws Exception {
        server.enqueue(new MockResponse().setBody(session(now.get() + 3600)));
        signIn();
        assertEquals(1, server.getRequestCount());

        String first = token();
        assertEquals("access-1", first);
        assertEquals("no extra request while valid", 1, server.getRequestCount());

        now.addAndGet(3600 - 30); // inside the refresh margin
        server.enqueue(new MockResponse().setBody(session(now.get() + 3600).replace("access-1", "access-2").replace("refresh-1", "refresh-2")));
        assertEquals("access-2", token());
        server.takeRequest();
        RecordedRequest refresh = server.takeRequest();
        assertEquals("/auth/v1/token?grant_type=refresh_token", refresh.getPath());
        assertTrue(refresh.getBody().readUtf8().contains("refresh-1"));
        assertTrue(storage.value.contains("refresh-2"));
    }

    @Test
    public void revokedRefreshTokenEndsTheSession() throws Exception {
        server.enqueue(new MockResponse().setBody(session(now.get() + 10)));
        signIn();
        server.enqueue(error(400, "{\"error_code\":\"refresh_token_not_found\",\"msg\":\"Invalid Refresh Token\"}"));
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        repo.getAccessToken(new AuthRepository.TokenCallback() {
            @Override public void onToken(String t) { done.countDown(); }
            @Override public void onError(Throwable e) { failure.set(e); done.countDown(); }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNotNull(failure.get());
        assertNull(repo.getCurrentUser());
        assertNull(storage.value);
    }

    @Test
    public void signOutClearsSessionAndTokenRequestsFail() throws Exception {
        server.enqueue(new MockResponse().setBody(session(now.get() + 3600)));
        signIn();
        server.enqueue(new MockResponse().setResponseCode(204));
        repo.signOut();
        assertNull(repo.getCurrentUser());
        assertNull(storage.value);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        repo.getAccessToken(new AuthRepository.TokenCallback() {
            @Override public void onToken(String t) { done.countDown(); }
            @Override public void onError(Throwable e) { failure.set(e); done.countDown(); }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNotNull(failure.get());
    }

    @Test
    public void offlineIsReportedAsNetwork() throws Exception {
        server.shutdown();
        assertEquals(AuthError.NETWORK, signIn().error.get());
    }

    private String token() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> token = new AtomicReference<>();
        repo.getAccessToken(new AuthRepository.TokenCallback() {
            @Override public void onToken(String t) { token.set(t); done.countDown(); }
            @Override public void onError(Throwable e) { done.countDown(); }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        return token.get();
    }
}
