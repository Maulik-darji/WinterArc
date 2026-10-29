package com.app.winterarc.data.cloud;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.app.winterarc.data.social.RoomFeedRepository;
import com.app.winterarc.data.social.db.FeedDao;
import com.app.winterarc.data.social.db.FeedPostEntity;
import com.app.winterarc.data.social.db.PostCommentEntity;
import com.app.winterarc.domain.feed.FeedPost;
import com.app.winterarc.domain.feed.FeedRepository;
import com.app.winterarc.domain.feed.FeedResult;
import com.app.winterarc.domain.feed.FeedRules;
import com.app.winterarc.domain.feed.PostComment;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The Feed for real accounts: writes go to Supabase (shared with the website) and screens read the
 * local snapshot that {@link CloudSync} keeps current. Without a cloud account everything stays in
 * {@link RoomFeedRepository}, as before. Authorship is checked here for clear errors and enforced
 * again by row-level security on the server.
 */
@Singleton
public class SyncingFeedRepository implements FeedRepository {
    private final RoomFeedRepository local;
    private final CloudSync cloud;
    private final FeedDao dao;

    private final com.app.winterarc.core.time.TimeProvider time;

    @Inject
    public SyncingFeedRepository(RoomFeedRepository local, CloudSync cloud, FeedDao dao,
                                 com.app.winterarc.core.time.TimeProvider time) {
        this.local = local;
        this.cloud = cloud;
        this.dao = dao;
        this.time = time;
    }

    @Override
    public void startLive() {
        cloud.startLive();
    }

    @Override
    public void stopLive() {
        cloud.stopLive();
    }

    @Override
    public LiveData<List<FeedPost>> observeTimeline(String viewerId) {
        cloud.refreshAsync();
        return local.observeTimeline(viewerId);
    }

    @Override
    public LiveData<FeedPost> observePost(String postId, String viewerId) {
        cloud.refreshAsync();
        return local.observePost(postId, viewerId);
    }

    @Override
    public LiveData<List<PostComment>> observeComments(String postId) {
        return local.observeComments(postId);
    }

    /** Pulls the latest posts and replies from the cloud (no-op without a cloud account). */
    @Override
    public void refresh() {
        cloud.refreshAsync();
    }

    @Override
    public FeedResult createPost(String actorId, String body) {
        if (!cloud.isActive()) return local.createPost(actorId, body);
        String text = FeedRules.clean(body, FeedRules.POST_MAX);
        if (text == null || actorId == null || actorId.isBlank()) return FeedResult.INVALID_INPUT;
        String id = UUID.randomUUID().toString();
        JsonObject post = new JsonObject();
        post.addProperty("id", id);
        post.addProperty("body", text);
        FeedResult r = result(cloud.write(bearer -> cloud.api().createPost(bearer, post)));
        if (r == FeedResult.SUCCESS) {
            // Show it right away; the background refresh then swaps in the server's timestamps.
            FeedPostEntity row = new FeedPostEntity();
            row.id = id;
            row.authorUserId = actorId;
            row.body = text;
            row.createdAt = time.now();
            dao.insertPosts(java.util.Collections.singletonList(row));
        }
        return r;
    }

    @Override
    public FeedResult editPost(String actorId, String postId, String body) {
        if (!cloud.isActive()) return local.editPost(actorId, postId, body);
        String text = FeedRules.clean(body, FeedRules.POST_MAX);
        if (text == null) return FeedResult.INVALID_INPUT;
        FeedResult owner = checkPostOwner(actorId, postId);
        if (owner != null) return owner;
        JsonObject changes = new JsonObject();
        changes.addProperty("body", text);
        return mirror(result(cloud.write(bearer -> cloud.api().updatePost(bearer, "eq." + postId, changes))),
                () -> local.editPost(actorId, postId, text));
    }

    @Override
    public FeedResult deletePost(String actorId, String postId) {
        if (!cloud.isActive()) return local.deletePost(actorId, postId);
        FeedResult owner = checkPostOwner(actorId, postId);
        if (owner != null) return owner;
        return mirror(result(cloud.write(bearer -> cloud.api().deletePost(bearer, "eq." + postId))),
                () -> local.deletePost(actorId, postId));
    }

    @Override
    public FeedResult toggleLike(String actorId, String postId) {
        if (!cloud.isActive()) return local.toggleLike(actorId, postId);
        if (dao.getPost(postId) == null) return FeedResult.NOT_FOUND;
        if (dao.likeExists(postId, actorId) > 0) {
            return mirror(result(cloud.write(bearer -> cloud.api().unlike(bearer, "eq." + postId, "eq." + actorId))),
                    () -> local.toggleLike(actorId, postId));
        }
        JsonObject like = new JsonObject();
        like.addProperty("post_id", postId);
        return mirror(result(cloud.write(bearer -> cloud.api().like(bearer, like))),
                () -> local.toggleLike(actorId, postId));
    }

    @Override
    public FeedResult addComment(String actorId, String postId, @Nullable String parentCommentId, String body) {
        if (!cloud.isActive()) return local.addComment(actorId, postId, parentCommentId, body);
        String text = FeedRules.clean(body, FeedRules.COMMENT_MAX);
        if (text == null || actorId == null || actorId.isBlank()) return FeedResult.INVALID_INPUT;
        if (dao.getPost(postId) == null) return FeedResult.NOT_FOUND;
        if (parentCommentId != null) {
            PostCommentEntity parent = dao.getComment(parentCommentId);
            if (parent == null || !parent.postId.equals(postId)) return FeedResult.NOT_FOUND;
            if (parent.deleted) return FeedResult.INVALID_INPUT;
        }
        String id = UUID.randomUUID().toString();
        JsonObject comment = new JsonObject();
        comment.addProperty("id", id);
        comment.addProperty("post_id", postId);
        if (parentCommentId != null) comment.addProperty("parent_comment_id", parentCommentId);
        comment.addProperty("body", text);
        FeedResult r = result(cloud.write(bearer -> cloud.api().createComment(bearer, comment)));
        if (r == FeedResult.SUCCESS) {
            PostCommentEntity row = new PostCommentEntity();
            row.id = id;
            row.postId = postId;
            row.parentCommentId = parentCommentId;
            row.authorUserId = actorId;
            row.body = text;
            row.createdAt = time.now();
            dao.insertComments(java.util.Collections.singletonList(row));
        }
        return r;
    }

    @Override
    public FeedResult editComment(String actorId, String commentId, String body) {
        if (!cloud.isActive()) return local.editComment(actorId, commentId, body);
        String text = FeedRules.clean(body, FeedRules.COMMENT_MAX);
        if (text == null) return FeedResult.INVALID_INPUT;
        FeedResult owner = checkCommentOwner(actorId, commentId);
        if (owner != null) return owner;
        JsonObject changes = new JsonObject();
        changes.addProperty("body", text);
        return mirror(result(cloud.write(bearer -> cloud.api().updateComment(bearer, "eq." + commentId, changes))),
                () -> local.editComment(actorId, commentId, text));
    }

    /**
     * Same rules as on the device: with replies the comment becomes a "deleted" placeholder;
     * without replies it is removed, along with placeholder ancestors left with no replies.
     */
    @Override
    public FeedResult deleteComment(String actorId, String commentId) {
        if (!cloud.isActive()) return local.deleteComment(actorId, commentId);
        FeedResult owner = checkCommentOwner(actorId, commentId);
        if (owner != null) return owner;
        PostCommentEntity comment = dao.getComment(commentId);
        if (dao.childCount(commentId) > 0) {
            JsonObject changes = new JsonObject();
            changes.addProperty("deleted", true);
            changes.addProperty("body", "");
            return mirror(result(cloud.write(bearer -> cloud.api().updateComment(bearer, "eq." + commentId, changes))),
                    () -> local.deleteComment(actorId, commentId));
        }
        FeedResult removed = result(cloud.write(bearer -> cloud.api().deleteComment(bearer, "eq." + commentId)));
        if (removed != FeedResult.SUCCESS) return removed;
        local.deleteComment(actorId, commentId); // instant on screen; the refresh reconciles
        // Clean up placeholders that no longer have replies. Only their authors may remove them, so
        // this is best effort: anything left is simply hidden by the thread builder.
        String parentId = comment.parentCommentId;
        while (parentId != null) {
            PostCommentEntity parent = dao.getComment(parentId);
            if (parent == null || !parent.deleted || dao.childCount(parentId) > 0 || !parent.authorUserId.equals(actorId)) break;
            String id = parent.id;
            parentId = parent.parentCommentId;
            if (cloud.write(bearer -> cloud.api().deleteComment(bearer, "eq." + id)) != CloudSync.Outcome.SUCCESS) break;
        }
        return FeedResult.SUCCESS;
    }

    /** After the server accepted a change, apply the same change locally so the screen updates at once. */
    private static FeedResult mirror(FeedResult result, Runnable applyLocally) {
        if (result == FeedResult.SUCCESS) applyLocally.run();
        return result;
    }

    @Nullable
    private FeedResult checkPostOwner(String actorId, String postId) {
        FeedPostEntity post = dao.getPost(postId);
        if (post == null) return FeedResult.NOT_FOUND;
        return post.authorUserId.equals(actorId) ? null : FeedResult.PERMISSION_DENIED;
    }

    @Nullable
    private FeedResult checkCommentOwner(String actorId, String commentId) {
        PostCommentEntity comment = dao.getComment(commentId);
        if (comment == null || comment.deleted) return FeedResult.NOT_FOUND;
        return comment.authorUserId.equals(actorId) ? null : FeedResult.PERMISSION_DENIED;
    }

    private static FeedResult result(CloudSync.Outcome outcome) {
        switch (outcome) {
            case SUCCESS: return FeedResult.SUCCESS;
            case NOT_ALLOWED: return FeedResult.PERMISSION_DENIED;
            case NOT_FOUND: return FeedResult.NOT_FOUND;
            case INVALID: return FeedResult.INVALID_INPUT;
            default: return FeedResult.UNAVAILABLE;
        }
    }
}
