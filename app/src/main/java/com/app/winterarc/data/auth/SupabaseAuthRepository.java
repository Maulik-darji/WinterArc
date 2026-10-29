package com.app.winterarc.data.auth;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.core.analytics.CrashReporter;
import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.app.winterarc.domain.auth.Credentials;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Supabase Auth: email + password and Google (ID token exchange). The session is stored encrypted
 * and refreshed shortly before the access token expires; the same access token authorises
 * Supabase data calls, where row-level security keys on its {@code sub} (the user's id).
 */
public final class SupabaseAuthRepository implements AuthRepository {
    /** Refresh when fewer than this many seconds remain. */
    private static final long REFRESH_MARGIN_SECONDS = 60;

    private record Session(String accessToken, String refreshToken, long expiresAt, AuthUser user) {}

    private final SupabaseAuthApi api;
    private final SessionStorage storage;
    private final AppExecutors executors;
    private final CrashReporter crashReporter;
    @Nullable private final String googleWebClientId;
    private final LongSupplier nowSeconds;
    private final MutableLiveData<AuthUser> current;
    private final List<TokenCallback> refreshWaiters = new ArrayList<>();

    @Nullable private Session session;
    private boolean refreshing;

    public SupabaseAuthRepository(SupabaseAuthApi api, SessionStorage storage, AppExecutors executors,
                                  CrashReporter crashReporter, @Nullable String googleWebClientId,
                                  LongSupplier nowSeconds) {
        this.api = api;
        this.storage = storage;
        this.executors = executors;
        this.crashReporter = crashReporter;
        this.googleWebClientId = googleWebClientId == null || googleWebClientId.isBlank() ? null : googleWebClientId.trim();
        this.nowSeconds = nowSeconds;
        this.session = decode(storage.read());
        this.current = new MutableLiveData<>(session == null ? null : session.user());
    }

    @Override public LiveData<AuthUser> currentUser() { return current; }
    @Nullable @Override public synchronized AuthUser getCurrentUser() { return session == null ? null : session.user(); }
    @Override public boolean isAvailable() { return true; }
    @Override public boolean isCloudAccount() { return true; }
    @Nullable @Override public String googleServerClientId() { return googleWebClientId; }

    @Override
    public void signUpWithEmail(String name, String email, String password, Callback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", Credentials.normalizeEmail(email));
        body.addProperty("password", password);
        JsonObject data = new JsonObject();
        data.addProperty("full_name", name);
        body.add("data", data);
        api.signUp(body).enqueue(new Callback0(callback, false) {
            @Override
            void onBody(JsonObject json) {
                Session created = parseSession(json);
                if (created != null) {
                    adopt(created);
                    deliver(callback, created.user());
                } else if (isObfuscatedDuplicate(json)) {
                    fail(callback, AuthError.EMAIL_IN_USE);
                } else {
                    // The account exists but waits for the emailed confirmation link.
                    fail(callback, AuthError.CONFIRM_EMAIL);
                }
            }
        });
    }

    @Override
    public void signInWithEmail(String email, String password, Callback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", Credentials.normalizeEmail(email));
        body.addProperty("password", password);
        exchange("password", body, callback, false);
    }

    @Override
    public void signInWithGoogle(@Nullable String idToken, Callback callback) {
        if (idToken == null || idToken.isBlank()) {
            executors.main().execute(() -> callback.onError(AuthError.GOOGLE_NO_ACCOUNT));
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("provider", "google");
        body.addProperty("id_token", idToken);
        exchange("id_token", body, callback, true);
    }

    @Override
    public void sendPasswordReset(String email, DoneCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", Credentials.normalizeEmail(email));
        api.recover(body).enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) executors.main().execute(callback::onDone);
                else {
                    AuthError error = mapError(response, false);
                    executors.main().execute(() -> callback.onError(error));
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                AuthError error = mapFailure(t);
                executors.main().execute(() -> callback.onError(error));
            }
        });
    }

    @Override
    public void signOut() {
        Session old;
        synchronized (this) {
            old = session;
            session = null;
        }
        storage.clear();
        current.postValue(null);
        if (old != null) {
            // Best effort: revoke the refresh token on the server; the device is signed out either way.
            api.logout("Bearer " + old.accessToken()).enqueue(new retrofit2.Callback<>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        }
    }

    @Override
    public void getAccessToken(TokenCallback callback) {
        Session s;
        synchronized (this) {
            s = session;
            if (s == null) {
                callback.onError(new IllegalStateException("A signed-in user is required for cloud sync"));
                return;
            }
            if (s.expiresAt() - nowSeconds.getAsLong() > REFRESH_MARGIN_SECONDS) {
                callback.onToken(s.accessToken());
                return;
            }
            refreshWaiters.add(callback);
            if (refreshing) return;
            refreshing = true;
        }
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", s.refreshToken());
        api.token("refresh_token", body).enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                Session renewed = response.isSuccessful() && response.body() != null ? parseSession(response.body()) : null;
                if (renewed != null) {
                    adopt(renewed);
                    finishRefresh(renewed.accessToken(), null);
                    return;
                }
                // 400/401: the refresh token was revoked or expired, so the session is over.
                if (response.code() == 400 || response.code() == 401 || response.code() == 403) {
                    signOutLocal();
                }
                finishRefresh(null, new IllegalStateException("Session refresh failed (HTTP " + response.code() + ")"));
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                finishRefresh(null, t); // offline: keep the session, try again later
            }
        });
    }

    // ---- internals

    private void exchange(String grantType, JsonObject body, Callback callback, boolean google) {
        api.token(grantType, body).enqueue(new Callback0(callback, google) {
            @Override
            void onBody(JsonObject json) {
                Session created = parseSession(json);
                if (created == null) {
                    fail(callback, AuthError.UNKNOWN);
                    return;
                }
                adopt(created);
                deliver(callback, created.user());
            }
        });
    }

    private void finishRefresh(@Nullable String token, @Nullable Throwable error) {
        List<TokenCallback> waiting;
        synchronized (this) {
            waiting = new ArrayList<>(refreshWaiters);
            refreshWaiters.clear();
            refreshing = false;
        }
        for (TokenCallback w : waiting) {
            if (token != null) w.onToken(token); else w.onError(error);
        }
    }

    private void adopt(Session next) {
        synchronized (this) { session = next; }
        storage.write(encode(next));
        current.postValue(next.user());
    }

    private void signOutLocal() {
        synchronized (this) { session = null; }
        storage.clear();
        current.postValue(null);
    }

    private void deliver(Callback callback, AuthUser user) {
        executors.main().execute(() -> callback.onSuccess(user));
    }

    private void fail(Callback callback, AuthError error) {
        executors.main().execute(() -> callback.onError(error));
    }

    /** Shared response handling for sign-up and token exchanges. */
    private abstract class Callback0 implements retrofit2.Callback<JsonObject> {
        private final Callback callback;
        private final boolean google;

        Callback0(Callback callback, boolean google) {
            this.callback = callback;
            this.google = google;
        }

        abstract void onBody(JsonObject json);

        @Override
        public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
            if (response.isSuccessful() && response.body() != null) onBody(response.body());
            else fail(callback, mapError(response, google));
        }

        @Override
        public void onFailure(Call<JsonObject> call, Throwable t) {
            fail(callback, mapFailure(t));
        }
    }

    private AuthError mapFailure(Throwable t) {
        if (t instanceof IOException) return AuthError.NETWORK;
        crashReporter.recordNonFatal(t, "auth");
        return AuthError.UNKNOWN;
    }

    /** Maps a Supabase Auth error body. Wrong password and unknown email stay one message on purpose. */
    AuthError mapError(Response<?> response, boolean google) {
        String code = null;
        String message = "";
        try {
            if (response.errorBody() != null) {
                JsonObject e = JsonParser.parseString(response.errorBody().string()).getAsJsonObject();
                code = firstString(e, "error_code", "code");
                message = firstString(e, "msg", "error_description", "message");
                if (message == null) message = "";
            }
        } catch (IOException | RuntimeException ignored) {
            // Not JSON; fall through to the status code.
        }
        String text = message.toLowerCase(Locale.ROOT);
        if (code != null) {
            switch (code) {
                case "user_already_exists":
                case "email_exists":
                    return AuthError.EMAIL_IN_USE;
                case "weak_password":
                    return AuthError.WEAK_PASSWORD;
                case "invalid_credentials":
                    return AuthError.WRONG_CREDENTIALS;
                case "email_not_confirmed":
                    return AuthError.EMAIL_NOT_CONFIRMED;
                case "over_request_rate_limit":
                case "over_email_send_rate_limit":
                    return AuthError.TOO_MANY_REQUESTS;
                case "email_address_invalid":
                    return AuthError.INVALID_EMAIL;
                case "user_banned":
                    return AuthError.ACCOUNT_DISABLED;
                case "provider_disabled":
                case "signup_disabled":
                    return AuthError.NOT_CONFIGURED;
                default:
                    break;
            }
        }
        if (response.code() == 429) return AuthError.TOO_MANY_REQUESTS;
        if (google) {
            if (text.contains("provider")) return AuthError.NOT_CONFIGURED;
            if (response.code() == 400 || response.code() == 401 || response.code() == 422) return AuthError.GOOGLE_NO_ACCOUNT;
        }
        if (text.contains("invalid login credentials")) return AuthError.WRONG_CREDENTIALS;
        if (text.contains("email") && text.contains("invalid")) return AuthError.INVALID_EMAIL;
        if (text.contains("password") && (text.contains("weak") || text.contains("at least"))) return AuthError.WEAK_PASSWORD;
        crashReporter.recordNonFatal(new IllegalStateException("Auth HTTP " + response.code() + " " + code), "auth");
        return AuthError.UNKNOWN;
    }

    // ---- JSON <-> session

    /** Supabase hides whether an email is registered by returning a user with no identities. */
    private static boolean isObfuscatedDuplicate(JsonObject json) {
        JsonObject user = json.has("user") && json.get("user").isJsonObject() ? json.getAsJsonObject("user") : json;
        return user.has("identities") && user.get("identities").isJsonArray() && user.getAsJsonArray("identities").isEmpty();
    }

    @Nullable
    private Session parseSession(JsonObject json) {
        String access = string(json, "access_token");
        String refresh = string(json, "refresh_token");
        JsonElement userElement = json.get("user");
        if (access == null || refresh == null || userElement == null || !userElement.isJsonObject()) return null;
        AuthUser user = parseUser(userElement.getAsJsonObject());
        if (user == null) return null;
        long expiresAt;
        if (json.has("expires_at") && json.get("expires_at").isJsonPrimitive()) expiresAt = json.get("expires_at").getAsLong();
        else expiresAt = nowSeconds.getAsLong() + (json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600);
        return new Session(access, refresh, expiresAt, user);
    }

    @Nullable
    private static AuthUser parseUser(JsonObject u) {
        String id = string(u, "id");
        if (id == null) return null;
        String name = null;
        JsonElement meta = u.get("user_metadata");
        if (meta != null && meta.isJsonObject()) name = firstString(meta.getAsJsonObject(), "full_name", "name");
        boolean verified = u.has("email_confirmed_at") && !u.get("email_confirmed_at").isJsonNull();
        return new AuthUser(id, string(u, "email"), name, verified);
    }

    private static String encode(Session s) {
        JsonObject o = new JsonObject();
        o.addProperty("access_token", s.accessToken());
        o.addProperty("refresh_token", s.refreshToken());
        o.addProperty("expires_at", s.expiresAt());
        JsonObject u = new JsonObject();
        u.addProperty("id", s.user().uid());
        if (s.user().email() != null) u.addProperty("email", s.user().email());
        if (s.user().displayName() != null) {
            JsonObject meta = new JsonObject();
            meta.addProperty("full_name", s.user().displayName());
            u.add("user_metadata", meta);
        }
        if (s.user().emailVerified()) u.addProperty("email_confirmed_at", "verified");
        o.add("user", u);
        return o.toString();
    }

    @Nullable
    private Session decode(@Nullable String stored) {
        if (stored == null) return null;
        try {
            return parseSession(JsonParser.parseString(stored).getAsJsonObject());
        } catch (RuntimeException e) {
            storage.clear();
            return null;
        }
    }

    @Nullable
    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    @Nullable
    private static String firstString(JsonObject o, String... keys) {
        for (String key : keys) {
            String v = string(o, key);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
