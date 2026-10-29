package com.app.winterarc.domain.feed;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a flat list of comments into a display-ready thread: pre-order (each comment followed by
 * its replies, oldest first), with indentation depth and the flags needed to draw thread lines.
 *
 * <p>Depth is capped at {@link FeedRules#MAX_VISUAL_DEPTH}: deeper replies are attached, in order,
 * under their ancestor at the cap and carry {@code replyingTo} so the conversation stays readable on
 * a phone. Leaf tombstones (deleted, no replies) are dropped.
 */
public final class CommentThreads {
    private CommentThreads() {}

    /**
     * @param depth        0 for a top-level comment
     * @param continues    for each indent column c (0..depth-1): whether the line in that column
     *                     carries on below this row (the path through column c has later siblings)
     * @param hasChildren  draw a line down from this comment's avatar
     * @param replyingTo   author id of the real parent when the reply was flattened by the cap
     */
    public record Node(PostComment comment, int depth, boolean[] continues, boolean hasChildren,
                       @Nullable String replyingTo) {}

    public static List<Node> build(List<PostComment> comments) {
        Map<String, PostComment> byId = new HashMap<>();
        for (PostComment c : comments) byId.put(c.id(), c);

        // Real tree (orphans whose parent is gone become top level).
        Map<String, List<PostComment>> children = new LinkedHashMap<>();
        List<PostComment> roots = new ArrayList<>();
        for (PostComment c : comments) {
            String parent = c.parentId();
            if (parent == null || !byId.containsKey(parent)) roots.add(c);
            else children.computeIfAbsent(parent, k -> new ArrayList<>()).add(c);
        }
        Comparator<PostComment> chronological = Comparator.comparing(PostComment::createdAt).thenComparing(PostComment::id);
        roots.sort(chronological);
        for (List<PostComment> list : children.values()) list.sort(chronological);

        // Visual tree: anything below the cap is re-attached to its ancestor at the cap.
        Map<String, List<PostComment>> visual = new HashMap<>();
        Map<String, String> replyingTo = new HashMap<>();
        for (PostComment root : roots) attach(root, 0, null, children, visual, replyingTo);

        List<Node> out = new ArrayList<>();
        List<PostComment> visibleRoots = prune(roots, visual);
        for (int i = 0; i < visibleRoots.size(); i++) {
            emit(visibleRoots.get(i), 0, new boolean[0], i < visibleRoots.size() - 1, visual, replyingTo, out);
        }
        return out;
    }

    private static void attach(PostComment node, int depth, @Nullable PostComment capAncestor,
                               Map<String, List<PostComment>> real, Map<String, List<PostComment>> visual,
                               Map<String, String> replyingTo) {
        List<PostComment> kids = real.getOrDefault(node.id(), Collections.emptyList());
        for (PostComment kid : kids) {
            if (depth + 1 <= FeedRules.MAX_VISUAL_DEPTH) {
                visual.computeIfAbsent(node.id(), k -> new ArrayList<>()).add(kid);
                PostComment cap = depth + 1 == FeedRules.MAX_VISUAL_DEPTH ? node : null;
                attach(kid, depth + 1, cap, real, visual, replyingTo);
            } else {
                // Flatten: show under the ancestor at the cap, after its earlier replies.
                visual.computeIfAbsent(capAncestor.id(), k -> new ArrayList<>()).add(kid);
                replyingTo.put(kid.id(), node.authorId());
                attach(kid, depth + 1, capAncestor, real, visual, replyingTo);
            }
        }
    }

    /** Drops deleted comments that no longer have any visible replies. */
    private static List<PostComment> prune(List<PostComment> siblings, Map<String, List<PostComment>> visual) {
        List<PostComment> kept = new ArrayList<>();
        for (PostComment c : siblings) {
            List<PostComment> kids = prune(visual.getOrDefault(c.id(), Collections.emptyList()), visual);
            visual.put(c.id(), kids);
            if (!c.deleted() || !kids.isEmpty()) kept.add(c);
        }
        return kept;
    }

    private static void emit(PostComment c, int depth, boolean[] parentContinues, boolean hasNextSibling,
                             Map<String, List<PostComment>> visual, Map<String, String> replyingTo, List<Node> out) {
        boolean[] continues = new boolean[depth];
        System.arraycopy(parentContinues, 0, continues, 0, Math.min(parentContinues.length, depth));
        if (depth > 0) continues[depth - 1] = hasNextSibling;
        List<PostComment> kids = visual.getOrDefault(c.id(), Collections.emptyList());
        out.add(new Node(c, depth, continues, !kids.isEmpty(), replyingTo.get(c.id())));
        for (int i = 0; i < kids.size(); i++) {
            emit(kids.get(i), Math.min(depth + 1, FeedRules.MAX_VISUAL_DEPTH), continues, i < kids.size() - 1,
                    visual, replyingTo, out);
        }
    }
}
