package com.app.winterarc.data.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.data.remote.SupabaseConfig;
import com.app.winterarc.data.social.RoomFeedRepository;
import com.app.winterarc.data.social.RoomSocialRepository;
import com.app.winterarc.data.social.db.SocialDatabase;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.app.winterarc.domain.feed.FeedResult;
import com.app.winterarc.domain.social.SocialResult;
import com.app.winterarc.testing.FakeTimeProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** The app and the website share one Supabase: writes go there first, reads come from its snapshot. */
@RunWith(AndroidJUnit4.class)
@Config(application = Application.class)
public class SyncingFeedRepositoryTest {
    private static final String ME = "11111111-1111-1111-1111-111111111111";
    private static final String WEB_USER = "22222222-2222-2222-2222-222222222222";

    private MockWebServer server;
    private SocialDatabase db;
    private FakeAuth auth;
    private CloudSync cloud;
    private SyncingFeedRepository repo;
    private RoomSocialRepository social;
    private final List<String> writes = new ArrayList<>();

    // What the fake Supabase currently returns for each table.
    private String posts = "[]";
    private String comments = "[]";
    private String profiles = "[]";
    private String follows = "[]";
    private int writeStatus = 201;
    private String writeBody = "[{}]";

    static final class FakeAuth implements AuthRepository {
        boolean cloudAccount = true;
        AuthUser user = new AuthUser(ME, "me@example.com", "Me", true);
        @Override public LiveData<AuthUser> currentUser() { return new MutableLiveData<>(user); }
        @Nullable @Override public AuthUser getCurrentUser() { return user; }
        @Override public boolean isAvailable() { return true; }
        @Nullable @Override public String googleServerClientId() { return null; }
        @Override public void signUpWithEmail(String n, String e, String p, Callback c) {}
        @Override public void signInWithEmail(String e, String p, Callback c) {}
        @Override public void signInWithGoogle(@Nullable String t, Callback c) {}
        @Override public void sendPasswordReset(String e, DoneCallback c) {}
        @Override public void signOut() {}
        @Override public boolean isCloudAccount() { return cloudAccount; }
        @Override public void getAccessToken(TokenCallback callback) { callback.onToken("token-1"); }
    }

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (!"GET".equals(request.getMethod())) {
                    if (path.startsWith("/storage/")) {
                        writes.add("UPLOAD " + path + " type=" + request.getHeader("Content-Type") + " bytes=" + request.getBodySize()
                                + " auth=" + request.getHeader("Authorization"));
                        return json("{\"Key\":\"profile-media" + path.substring(path.lastIndexOf('/')) + "\"}");
                    }
                    writes.add(request.getMethod() + " " + path.replaceAll("\\?.*", "") + " " + request.getBody().readUtf8()
                            + " auth=" + request.getHeader("Authorization"));
                    return new MockResponse().setResponseCode(writeStatus).setBody(writeBody);
                }
                if (path.startsWith("/rest/v1/feed_posts")) return json(posts);
                if (path.startsWith("/rest/v1/post_comments")) return json(comments);
                if (path.startsWith("/rest/v1/profiles")) return json(profiles);
                if (path.startsWith("/rest/v1/follows")) return json(follows);
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SocialDatabase.class)
                .allowMainThreadQueries().build();
        auth = new FakeAuth();
        SocialCloudApi api = new Retrofit.Builder().baseUrl(server.url("/")).client(new OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create()).build().create(SocialCloudApi.class);
        SupabaseConfig config = new SupabaseConfig(server.url("/").toString(), "publishable", true);
        cloud = new CloudSync(config, api, auth, db, db.feedDao(), db.socialDao(), AppExecutors.direct());
        FakeTimeProvider clock = new FakeTimeProvider(LocalDate.of(2026, 10, 29));
        RoomFeedRepository local = new RoomFeedRepository(db, db.feedDao(), clock);
        repo = new SyncingFeedRepository(local, cloud, db.feedDao(), clock);
        social = new RoomSocialRepository(db, db.socialDao(), cloud);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        server.shutdown();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private int count(String sql) {
        try (Cursor c = db.query(sql, null)) {
            c.moveToFirst();
            return c.getInt(0);
        }
    }

    private String first(String sql) {
        try (Cursor c = db.query(sql, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    private void websiteHasOnePostWithReplyAndLike() {
        posts = "[{\"id\":\"p-web\",\"author_user_id\":\"" + WEB_USER + "\",\"body\":\"Posted on the website\","
                + "\"created_at\":\"2026-10-29T10:00:00.123456+00:00\",\"edited_at\":null,"
                + "\"feed_post_likes\":[{\"post_id\":\"p-web\",\"user_id\":\"" + ME + "\",\"created_at\":\"2026-10-29T10:01:00+00:00\"}]}]";
        comments = "[{\"id\":\"c-1\",\"post_id\":\"p-web\",\"parent_comment_id\":null,\"author_user_id\":\"" + ME + "\","
                + "\"body\":\"Replied from the app\",\"created_at\":\"2026-10-29T10:02:00+00:00\",\"edited_at\":null,\"deleted\":false},"
                + "{\"id\":\"c-orphan\",\"post_id\":\"not-kept\",\"parent_comment_id\":null,\"author_user_id\":\"x\","
                + "\"body\":\"ignored\",\"created_at\":\"2026-10-29T10:02:00+00:00\",\"edited_at\":null,\"deleted\":false}]";
        profiles = "[{\"user_id\":\"" + WEB_USER + "\",\"display_name\":\"Web Person\",\"handle\":\"@webperson\",\"bio\":\"hi\"}]";
        follows = "[{\"follower_user_id\":\"" + ME + "\",\"followed_user_id\":\"" + WEB_USER + "\",\"created_at\":\"2026-10-29T09:00:00Z\"}]";
    }

    @Test
    public void refreshBringsWebsitePostsRepliesLikesProfilesAndFollows() {
        websiteHasOnePostWithReplyAndLike();
        assertTrue(cloud.refresh());

        assertEquals("p-web", first("SELECT id FROM feed_posts"));
        assertEquals(1, count("SELECT COUNT(*) FROM feed_posts"));
        assertEquals(1, count("SELECT COUNT(*) FROM feed_post_likes WHERE user_id = '" + ME + "'"));
        assertEquals("only comments of kept posts", 1, count("SELECT COUNT(*) FROM post_comments"));
        assertEquals("Web Person", first("SELECT display_name FROM social_users WHERE user_id = '" + WEB_USER + "'"));
        assertEquals(1, count("SELECT COUNT(*) FROM follows"));

        // The website deletes it: the next refresh removes it here too, with its likes and replies.
        posts = "[]";
        assertTrue(cloud.refresh());
        assertEquals(0, count("SELECT COUNT(*) FROM feed_posts"));
        assertEquals(0, count("SELECT COUNT(*) FROM post_comments"));
    }

    @Test
    public void postingFromTheAppGoesToSupabaseWithTheUsersToken() {
        writes.clear();
        assertEquals(FeedResult.SUCCESS, repo.createPost(ME, "  Hello from Android  "));
        assertEquals(1, writes.size());
        String write = writes.get(0);
        assertTrue(write, write.startsWith("POST /rest/v1/feed_posts"));
        assertTrue(write, write.contains("\"body\":\"Hello from Android\""));
        assertTrue(write, write.contains("auth=Bearer token-1"));
        assertEquals(FeedResult.INVALID_INPUT, repo.createPost(ME, "   "));
        assertEquals("invalid input never reaches the server", 1, writes.size());
    }

    @Test
    public void onlyAuthorsMayEditAndServerRefusalsAreReported() {
        websiteHasOnePostWithReplyAndLike();
        cloud.refresh();
        writes.clear();
        assertEquals(FeedResult.PERMISSION_DENIED, repo.editPost(ME, "p-web", "mine now"));
        assertEquals(FeedResult.PERMISSION_DENIED, repo.deletePost(ME, "p-web"));
        assertTrue("checked locally before calling the server", writes.isEmpty());

        // Row-level security filtered the row out: empty result.
        writeBody = "[]";
        writeStatus = 200;
        assertEquals(FeedResult.PERMISSION_DENIED, repo.editComment(ME, "c-1", "edited"));
        assertEquals(1, writes.size());
        // And a server error is reported, not swallowed.
        writeStatus = 503;
        writeBody = "{}";
        assertEquals(FeedResult.UNAVAILABLE, repo.editComment(ME, "c-1", "edited again"));
    }

    @Test
    public void likeTogglesAgainstTheServerState() {
        websiteHasOnePostWithReplyAndLike();
        cloud.refresh();
        writes.clear();
        writeStatus = 200;
        assertEquals(FeedResult.SUCCESS, repo.toggleLike(ME, "p-web"));
        assertTrue(writes.get(0), writes.get(0).startsWith("DELETE /rest/v1/feed_post_likes"));
    }

    @Test
    public void replyGoesToTheServerWithItsParent() {
        websiteHasOnePostWithReplyAndLike();
        cloud.refresh();
        writes.clear();
        assertEquals(FeedResult.SUCCESS, repo.addComment(ME, "p-web", "c-1", "Thread reply"));
        assertTrue(writes.get(0), writes.get(0).contains("\"parent_comment_id\":\"c-1\""));
    }

    @Test
    public void offlineWritesFailClearlyAndKeepTheLastSnapshot() throws Exception {
        websiteHasOnePostWithReplyAndLike();
        cloud.refresh();
        server.shutdown();
        assertEquals(FeedResult.UNAVAILABLE, repo.createPost(ME, "No signal"));
        assertFalse(cloud.refresh());
        assertEquals(1, count("SELECT COUNT(*) FROM feed_posts"));
    }

    @Test
    public void localTestAccountsNeverTouchTheCloud() {
        auth.cloudAccount = false;
        writes.clear();
        assertEquals(FeedResult.SUCCESS, repo.createPost(ME, "Stays on this phone"));
        assertTrue(writes.isEmpty());
        assertEquals(1, count("SELECT COUNT(*) FROM feed_posts"));
    }

    @Test
    public void profileAndFollowsSyncWithTheWebsite() {
        websiteHasOnePostWithReplyAndLike();
        writes.clear();
        social.ensureProfile(ME, "Me", "@me");
        assertTrue(writes.get(0), writes.get(0).startsWith("POST /rest/v1/profiles"));
        assertTrue(writes.get(0), writes.get(0).contains("\"handle\":\"@me\""));
        assertNotNull("the website's people arrive with the refresh", first("SELECT user_id FROM social_users WHERE user_id = '" + WEB_USER + "'"));

        writes.clear();
        writeStatus = 200;
        assertEquals(SocialResult.SUCCESS, social.toggleFollow(ME, WEB_USER));
        assertTrue(writes.get(0), writes.get(0).startsWith("DELETE /rest/v1/follows"));
    }

    @Test
    public void confirmedPostShowsAtOnceEvenBeforeTheRefreshLands() {
        // The server accepts the post, but the refresh that follows returns an error (slow network):
        // the post is still on screen immediately, with the same id the server stored.
        posts = "not json";
        assertEquals(FeedResult.SUCCESS, repo.createPost(ME, "Instant"));
        assertEquals("Instant", first("SELECT body FROM feed_posts"));
        String sentId = writes.get(0).replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        assertEquals(sentId, first("SELECT id FROM feed_posts"));
    }

    @Test
    public void profilePhotoUploadsToTheUsersFolderAndIsSharedWithTheWebsite() {
        social.ensureProfile(ME, "Me", "@me");
        writes.clear();
        writeStatus = 200;
        assertEquals(SocialResult.SUCCESS, social.setProfileImage(ME, "avatar", new byte[]{1, 2, 3}));
        String upload = writes.get(0);
        assertTrue(upload, upload.startsWith("UPLOAD /storage/v1/object/profile-media/" + ME + "/avatar-"));
        assertTrue(upload, upload.contains("type=image/jpeg") && upload.contains("bytes=3") && upload.contains("auth=Bearer token-1"));
        String patch = writes.get(1);
        assertTrue(patch, patch.startsWith("PATCH /rest/v1/profiles"));
        assertTrue(patch, patch.contains("/storage/v1/object/public/profile-media/" + ME + "/avatar-"));

        // Removing sets the column to null; nothing is uploaded.
        writes.clear();
        assertEquals(SocialResult.SUCCESS, social.setProfileImage(ME, "banner", null));
        assertEquals(1, writes.size());
        assertTrue(writes.get(0), writes.get(0).contains("\"banner_url\":null"));
        assertEquals(SocialResult.INVALID_INPUT, social.setProfileImage(ME, "cover", null));
    }

    @Test
    public void parsesSupabaseTimestamps() {
        assertEquals(java.time.Instant.parse("2026-10-29T10:00:00.123456Z"),
                CloudSync.time("2026-10-29T10:00:00.123456+00:00", null));
        assertNull(CloudSync.time(null, null));
    }
}
