package com.app.winterarc.data.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.app.winterarc.data.social.db.FeedDao;
import com.app.winterarc.data.social.db.FeedPostRow;
import com.app.winterarc.data.social.db.PostCommentEntity;
import com.app.winterarc.data.social.db.SocialDatabase;
import com.app.winterarc.domain.feed.FeedResult;
import com.app.winterarc.testing.FakeTimeProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.LocalDate;

@RunWith(AndroidJUnit4.class)
@Config(application = Application.class)
public class RoomFeedRepositoryTest {
    private static final String ME = "me";
    private static final String OTHER = "other";
    private SocialDatabase db;
    private FeedDao dao;
    private RoomFeedRepository repo;

    @Before
    public void setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SocialDatabase.class)
                .allowMainThreadQueries().build();
        dao = db.feedDao();
        repo = new RoomFeedRepository(db, dao, new FakeTimeProvider(LocalDate.of(2026, 10, 24)));
    }

    @After
    public void tearDown() {
        db.close();
    }

    private String onlyPostId() {
        return db.query("SELECT id FROM feed_posts", null).getCount() == 1 ? first("SELECT id FROM feed_posts") : null;
    }

    private String first(String sql) {
        android.database.Cursor c = db.query(sql, null);
        c.moveToFirst();
        String v = c.getString(0);
        c.close();
        return v;
    }

    private String commentBy(String body) {
        return first("SELECT id FROM post_comments WHERE body = '" + body + "'");
    }

    @Test
    public void createValidatesAndStoresPost() {
        assertEquals(FeedResult.INVALID_INPUT, repo.createPost(ME, "   "));
        assertEquals(FeedResult.INVALID_INPUT, repo.createPost(ME, "x".repeat(281)));
        assertEquals(FeedResult.SUCCESS, repo.createPost(ME, "  Ran 5 km before work  "));
        assertEquals("Ran 5 km before work", first("SELECT body FROM feed_posts"));
    }

    @Test
    public void onlyAuthorCanEditOrDeletePost() {
        repo.createPost(ME, "First");
        String id = onlyPostId();
        assertEquals(FeedResult.PERMISSION_DENIED, repo.editPost(OTHER, id, "hijack"));
        assertEquals(FeedResult.PERMISSION_DENIED, repo.deletePost(OTHER, id));
        assertEquals(FeedResult.SUCCESS, repo.editPost(ME, id, "First, edited"));
        assertNotNull(dao.getPost(id).editedAt);
        assertEquals(FeedResult.SUCCESS, repo.deletePost(ME, id));
        assertNull(dao.getPost(id));
    }

    @Test
    public void likesToggleAndCountsAreComputed() {
        repo.createPost(ME, "Hello");
        String id = onlyPostId();
        repo.toggleLike(OTHER, id);
        repo.toggleLike(ME, id);
        repo.addComment(OTHER, id, null, "Nice");
        FeedPostRow row = latest(id, ME);
        assertEquals(2, row.likeCount);
        assertTrue(row.likedByMe);
        assertEquals(1, row.replyCount);
        repo.toggleLike(ME, id);
        assertFalse(latest(id, ME).likedByMe);
    }

    private FeedPostRow latest(String id, String viewer) {
        android.database.Cursor c = db.query("SELECT (SELECT COUNT(*) FROM feed_post_likes WHERE post_id='" + id + "'), "
                + "EXISTS(SELECT 1 FROM feed_post_likes WHERE post_id='" + id + "' AND user_id='" + viewer + "'), "
                + "(SELECT COUNT(*) FROM post_comments WHERE post_id='" + id + "' AND deleted=0)", null);
        c.moveToFirst();
        FeedPostRow r = new FeedPostRow();
        r.likeCount = c.getInt(0);
        r.likedByMe = c.getInt(1) == 1;
        r.replyCount = c.getInt(2);
        c.close();
        return r;
    }

    @Test
    public void threadedRepliesAndCommentPermissions() {
        repo.createPost(ME, "Post");
        String post = onlyPostId();
        assertEquals(FeedResult.SUCCESS, repo.addComment(OTHER, post, null, "Top"));
        String top = commentBy("Top");
        assertEquals(FeedResult.SUCCESS, repo.addComment(ME, post, top, "Reply"));
        assertEquals(top, dao.getComment(commentBy("Reply")).parentCommentId);
        assertEquals(FeedResult.NOT_FOUND, repo.addComment(ME, post, "missing", "x"));

        // Only the comment's author may edit or delete it (the post author can't either).
        assertEquals(FeedResult.PERMISSION_DENIED, repo.editComment(ME, top, "edit"));
        assertEquals(FeedResult.PERMISSION_DENIED, repo.deleteComment(ME, top));
        assertEquals(FeedResult.SUCCESS, repo.editComment(OTHER, top, "Top (edited)"));
        assertNotNull(dao.getComment(top).editedAt);
    }

    @Test
    public void deletingCommentWithRepliesLeavesTombstoneThenCleansUp() {
        repo.createPost(ME, "Post");
        String post = onlyPostId();
        repo.addComment(OTHER, post, null, "Parent");
        String parent = commentBy("Parent");
        repo.addComment(ME, post, parent, "Child");
        String child = commentBy("Child");

        assertEquals(FeedResult.SUCCESS, repo.deleteComment(OTHER, parent));
        PostCommentEntity tomb = dao.getComment(parent);
        assertTrue(tomb.deleted);
        assertEquals("", tomb.body);
        assertEquals(FeedResult.INVALID_INPUT, repo.addComment(ME, post, parent, "reply to deleted"));
        assertEquals(FeedResult.NOT_FOUND, repo.editComment(OTHER, parent, "revive"));

        // Removing the last reply also removes the now-empty tombstone.
        assertEquals(FeedResult.SUCCESS, repo.deleteComment(ME, child));
        assertNull(dao.getComment(child));
        assertNull(dao.getComment(parent));
    }

    @Test
    public void deletingPostCascadesToCommentsAndLikes() {
        repo.createPost(ME, "Post");
        String post = onlyPostId();
        repo.addComment(OTHER, post, null, "C");
        repo.toggleLike(OTHER, post);
        repo.deletePost(ME, post);
        assertEquals(0, db.query("SELECT * FROM post_comments", null).getCount());
        assertEquals(0, db.query("SELECT * FROM feed_post_likes", null).getCount());
    }
}
