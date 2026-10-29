package com.app.winterarc.domain.feed;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CommentThreadsTest {
    private int clock;

    private PostComment c(String id, String parent, String author) {
        return new PostComment(id, "p", parent, author, "body " + id, Instant.ofEpochSecond(clock++), null, false);
    }

    private PostComment deleted(String id, String parent) {
        return new PostComment(id, "p", parent, "x", "", Instant.ofEpochSecond(clock++), null, true);
    }

    private static List<String> ids(List<CommentThreads.Node> nodes) {
        List<String> out = new ArrayList<>();
        for (CommentThreads.Node n : nodes) out.add(n.comment().id() + "@" + n.depth());
        return out;
    }

    @Test
    public void preOrderOldestFirstWithDepths() {
        List<PostComment> all = List.of(c("a", null, "u1"), c("b", null, "u2"), c("a1", "a", "u2"),
                c("a2", "a", "u3"), c("a1x", "a1", "u1"));
        List<CommentThreads.Node> nodes = CommentThreads.build(all);
        assertEquals(List.of("a@0", "a1@1", "a1x@2", "a2@1", "b@0"), ids(nodes));
        assertTrue(nodes.get(0).hasChildren());
        assertFalse(nodes.get(4).hasChildren());
    }

    @Test
    public void threadLineFlagsTrackLaterSiblings() {
        List<PostComment> all = List.of(c("a", null, "u"), c("a1", "a", "u"), c("a1x", "a1", "u"), c("a2", "a", "u"));
        List<CommentThreads.Node> nodes = CommentThreads.build(all);
        // a1 has a later sibling (a2): its column-0 line continues.
        assertArrayEquals(new boolean[]{true}, nodes.get(1).continues());
        // a1x: column 0 continues (a1 has sibling a2); column 1 ends (a1x is a1's last child).
        assertArrayEquals(new boolean[]{true, false}, nodes.get(2).continues());
        // a2 is a's last child.
        assertArrayEquals(new boolean[]{false}, nodes.get(3).continues());
    }

    @Test
    public void deepRepliesAreFlattenedAtTheCapWithReplyingTo() {
        List<PostComment> all = List.of(c("d0", null, "u0"), c("d1", "d0", "u1"), c("d2", "d1", "u2"),
                c("d3", "d2", "u3"), c("d4", "d3", "u4"), c("d5", "d4", "u5"));
        List<CommentThreads.Node> nodes = CommentThreads.build(all);
        assertEquals(List.of("d0@0", "d1@1", "d2@2", "d3@3", "d4@3", "d5@3"), ids(nodes));
        assertNull(nodes.get(3).replyingTo());
        assertEquals("u3", nodes.get(4).replyingTo());
        assertEquals("u4", nodes.get(5).replyingTo());
    }

    @Test
    public void deletedCommentKeptOnlyWhileItHasReplies() {
        List<PostComment> all = List.of(deleted("gone", null), c("reply", "gone", "u"), deleted("leaf", null));
        List<CommentThreads.Node> nodes = CommentThreads.build(all);
        assertEquals(List.of("gone@0", "reply@1"), ids(nodes));
        assertTrue(nodes.get(0).comment().deleted());
    }

    @Test
    public void orphanBecomesTopLevel() {
        assertEquals(List.of("o@0"), ids(CommentThreads.build(List.of(c("o", "missing", "u")))));
    }

    @Test
    public void rulesCleanAndCount() {
        assertNull(FeedRules.clean("   ", FeedRules.POST_MAX));
        assertNull(FeedRules.clean("x".repeat(281), FeedRules.POST_MAX));
        assertEquals("hi\n\nthere", FeedRules.clean("  hi\n\n\n\nthere ", FeedRules.POST_MAX));
        assertEquals(278, FeedRules.remaining(" hi ", FeedRules.POST_MAX));
        // Emoji count as one character each.
        assertEquals(279, FeedRules.remaining("🏃", FeedRules.POST_MAX));
    }
}
