package com.app.winterarc.data.cloud;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.data.cloud.SocialCloudApi.RemoteComment;
import com.app.winterarc.data.cloud.SocialCloudApi.RemoteFollow;
import com.app.winterarc.data.cloud.SocialCloudApi.RemoteLike;
import com.app.winterarc.data.cloud.SocialCloudApi.RemotePost;
import com.app.winterarc.data.cloud.SocialCloudApi.RemoteProfile;
import com.app.winterarc.data.remote.SupabaseConfig;
import com.app.winterarc.data.social.db.FeedDao;
import com.app.winterarc.data.social.db.FeedPostEntity;
import com.app.winterarc.data.social.db.FeedPostLikeEntity;
import com.app.winterarc.data.social.db.FollowEntity;
import com.app.winterarc.data.social.db.PostCommentEntity;
import com.app.winterarc.data.social.db.SocialDao;
import com.app.winterarc.data.social.db.SocialDatabase;
import com.app.winterarc.data.social.db.SocialUserEntity;
import com.app.winterarc.domain.auth.AuthRepository;
import com.google.gson.JsonArray;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Keeps the app's social data in step with Supabase, which the website shares.
 *
 * <p>The server is the source of truth. Writes go to Supabase first (row-level security decides
 * who may change what); after every write, and whenever a social screen asks, the latest snapshot
 * of posts, likes, comments, profiles and follows is pulled and replaces the local copy in one Room
 * transaction, so screens update once. If the cloud can't be reached, the last snapshot stays and
 * writes report {@link Outcome#UNAVAILABLE} instead of silently diverging.
 *
 * <p>Only active for real (Supabase) accounts with Supabase configured. The debug test account and
 * builds without Supabase keep everything local, exactly as before.
 */
@Singleton
public class CloudSync {
    /** The newest posts kept on the device. */
    static final int POST_LIMIT = 200;
    static final int PEOPLE_LIMIT = 1000;

    public enum Outcome { SUCCESS, NOT_ALLOWED, NOT_FOUND, INVALID, UNAVAILABLE }

    private final SupabaseConfig config;
    private final SocialCloudApi api;
    private final AuthRepository auth;
    private final SocialDatabase database;
    private final FeedDao feedDao;
    private final SocialDao socialDao;
    private final AppExecutors executors;
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicInteger liveScreens = new java.util.concurrent.atomic.AtomicInteger();
    @Nullable private RealtimeClient realtime;

    @Inject
    public CloudSync(SupabaseConfig config, SocialCloudApi api, AuthRepository auth, SocialDatabase database,
                     FeedDao feedDao, SocialDao socialDao, AppExecutors executors) {
        this.config = config;
        this.api = api;
        this.auth = auth;
        this.database = database;
        this.feedDao = feedDao;
        this.socialDao = socialDao;
        this.executors = executors;
    }

    /** True when social data should live in the cloud (signed in with a Supabase account). */
    public boolean isActive() {
        return config.configured() && auth.isCloudAccount() && auth.getCurrentUser() != null;
    }

    /**
     * Live updates while a social screen is visible: Supabase Realtime pings on every change (from
     * anyone, on any platform) and the snapshot is pulled straight away. Calls are reference
     * counted, so overlapping screens keep one connection.
     */
    public void startLive() {
        if (!isActive()) return;
        if (liveScreens.incrementAndGet() != 1) return;
        synchronized (this) {
            if (realtime == null) {
                realtime = new RealtimeClient(config.url(), config.publishableKey(), this::liveToken, this::refreshAsync);
            }
            realtime.start();
        }
    }

    public void stopLive() {
        if (liveScreens.get() == 0 || liveScreens.decrementAndGet() != 0) return;
        synchronized (this) {
            if (realtime != null) realtime.stop();
        }
    }

    @Nullable
    private String liveToken() {
        String bearer = bearer();
        return bearer == null ? null : bearer.substring("Bearer ".length());
    }

    /** Pulls the latest snapshot in the background; repeated calls while one is queued collapse. */
    public void refreshAsync() {
        if (!isActive() || !refreshQueued.compareAndSet(false, true)) return;
        executors.network().execute(() -> {
            refreshQueued.set(false);
            refresh();
        });
    }

    /** Pulls the latest snapshot now. Returns false (and keeps the local copy) when offline. */
    @WorkerThread
    public synchronized boolean refresh() {
        if (!isActive()) return false;
        String bearer = bearer();
        try {
            List<RemotePost> posts = body(api.posts(bearer, SocialCloudApi.POST_SELECT, "created_at.desc", POST_LIMIT));
            List<RemoteComment> comments = body(api.comments(bearer, "*", null, "created_at.asc"));
            // "*": works before and after the profile-images migration (missing columns stay null).
            List<RemoteProfile> profiles = body(api.profiles(bearer, "*", PEOPLE_LIMIT));
            List<RemoteFollow> follows = body(api.follows(bearer, "*", PEOPLE_LIMIT));
            apply(posts, comments, profiles, follows);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @VisibleForTesting
    void apply(List<RemotePost> posts, List<RemoteComment> comments, List<RemoteProfile> profiles,
               List<RemoteFollow> follows) {
        List<FeedPostEntity> postRows = new ArrayList<>();
        List<FeedPostLikeEntity> likeRows = new ArrayList<>();
        Set<String> postIds = new HashSet<>();
        for (RemotePost p : posts) {
            if (p.id == null || p.authorUserId == null) continue;
            FeedPostEntity row = new FeedPostEntity();
            row.id = p.id;
            row.authorUserId = p.authorUserId;
            row.body = p.body == null ? "" : p.body;
            row.createdAt = time(p.createdAt, Instant.EPOCH);
            row.editedAt = time(p.editedAt, null);
            postRows.add(row);
            postIds.add(p.id);
            if (p.likes == null) continue;
            for (RemoteLike l : p.likes) {
                FeedPostLikeEntity like = new FeedPostLikeEntity();
                like.postId = p.id;
                like.userId = l.userId == null ? "" : l.userId;
                like.createdAt = time(l.createdAt, Instant.EPOCH);
                likeRows.add(like);
            }
        }
        List<PostCommentEntity> commentRows = new ArrayList<>();
        for (RemoteComment c : comments) {
            if (c.id == null || !postIds.contains(c.postId)) continue; // only comments of posts we keep
            PostCommentEntity row = new PostCommentEntity();
            row.id = c.id;
            row.postId = c.postId;
            row.parentCommentId = c.parentCommentId;
            row.authorUserId = c.authorUserId == null ? "" : c.authorUserId;
            row.body = c.body == null ? "" : c.body;
            row.createdAt = time(c.createdAt, Instant.EPOCH);
            row.editedAt = time(c.editedAt, null);
            row.deleted = c.deleted;
            commentRows.add(row);
        }
        List<SocialUserEntity> userRows = new ArrayList<>();
        for (RemoteProfile p : profiles) {
            if (p.userId == null || p.displayName == null || p.displayName.isBlank()) continue;
            SocialUserEntity row = new SocialUserEntity();
            row.userId = p.userId;
            row.displayName = p.displayName.trim();
            row.handle = p.handle == null ? "" : p.handle;
            row.bio = p.bio == null ? "" : p.bio;
            row.initial = row.displayName.substring(0, row.displayName.offsetByCodePoints(0, 1)).toUpperCase(Locale.getDefault());
            row.avatarUrl = blankToNull(p.avatarUrl);
            row.bannerUrl = blankToNull(p.bannerUrl);
            userRows.add(row);
        }
        List<FollowEntity> followRows = new ArrayList<>();
        for (RemoteFollow f : follows) {
            if (f.followerUserId == null || f.followedUserId == null) continue;
            FollowEntity row = new FollowEntity();
            row.followerUserId = f.followerUserId;
            row.followedUserId = f.followedUserId;
            row.createdAt = time(f.createdAt, Instant.EPOCH);
            followRows.add(row);
        }
        database.runInTransaction(() -> {
            feedDao.clearFeed();
            feedDao.insertPosts(postRows);
            feedDao.insertLikes(likeRows);
            feedDao.insertComments(commentRows);
            socialDao.upsertUsers(userRows);
            socialDao.clearFollows();
            socialDao.insertFollows(followRows);
        });
    }

    // ---- writes

    /**
     * Runs a write against Supabase. An empty result means RLS filtered the row (the caller is not
     * the author, or it is already gone). On success the local copy is refreshed.
     */
    @WorkerThread
    public Outcome write(Write call) {
        if (!isActive()) return Outcome.NOT_ALLOWED;
        String bearer = bearer();
        if (bearer == null) return Outcome.NOT_ALLOWED;
        try {
            Response<JsonArray> response = call.make(bearer).execute();
            if (!response.isSuccessful()) return httpOutcome(response.code());
            JsonArray rows = response.body();
            if (rows != null && rows.isEmpty()) return Outcome.NOT_ALLOWED;
            refreshAsync(); // reconcile with the server (ids, timestamps, others' changes)
            return Outcome.SUCCESS;
        } catch (IOException e) {
            return Outcome.UNAVAILABLE;
        }
    }

    /** Creates the signed-in user's profile in the cloud if it doesn't exist yet. */
    @WorkerThread
    public Outcome ensureProfile(com.google.gson.JsonObject profile) {
        if (!isActive()) return Outcome.NOT_ALLOWED;
        String bearer = bearer();
        if (bearer == null) return Outcome.NOT_ALLOWED;
        try {
            Response<Void> response = api.ensureProfile(bearer, "user_id", profile).execute();
            if (!response.isSuccessful()) return httpOutcome(response.code());
            refresh();
            return Outcome.SUCCESS;
        } catch (IOException e) {
            return Outcome.UNAVAILABLE;
        }
    }

    /**
     * Uploads a JPEG to "profile-media/<user id>/<kind>-<time>.jpg" and returns its public URL, or
     * null when the upload failed (offline, not signed in, rejected).
     */
    @Nullable
    @WorkerThread
    public String uploadProfileImage(String userId, String kind, byte[] jpeg) {
        if (!isActive()) return null;
        String bearer = bearer();
        if (bearer == null) return null;
        String path = userId + "/" + kind + "-" + System.currentTimeMillis() + ".jpg";
        okhttp3.RequestBody body = okhttp3.RequestBody.create(jpeg, okhttp3.MediaType.get("image/jpeg"));
        try {
            Response<com.google.gson.JsonObject> response = api.uploadProfileMedia(bearer, path, body).execute();
            if (!response.isSuccessful()) return null;
            return config.url() + "storage/v1/object/public/profile-media/" + path;
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private static String blankToNull(@Nullable String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public SocialCloudApi api() {
        return api;
    }

    public interface Write {
        Call<JsonArray> make(String bearer);
    }

    private static Outcome httpOutcome(int code) {
        if (code == 401 || code == 403) return Outcome.NOT_ALLOWED;
        if (code == 404) return Outcome.NOT_FOUND;
        if (code == 409 || code == 400 || code == 422) return Outcome.INVALID;
        return Outcome.UNAVAILABLE;
    }

    private static <T> T body(Call<T> call) throws IOException {
        Response<T> response = call.execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException("HTTP " + response.code());
        }
        return response.body();
    }

    /** A valid access token, waiting briefly for a refresh if needed (never on the main thread). */
    @Nullable
    private String bearer() {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> token = new AtomicReference<>();
        auth.getAccessToken(new AuthRepository.TokenCallback() {
            @Override public void onToken(String accessToken) { token.set(accessToken); done.countDown(); }
            @Override public void onError(Throwable error) { done.countDown(); }
        });
        try {
            done.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return token.get() == null ? null : "Bearer " + token.get();
    }

    @Nullable
    static Instant time(@Nullable String value, @Nullable Instant fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            try {
                return Instant.parse(value);
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
    }
}
