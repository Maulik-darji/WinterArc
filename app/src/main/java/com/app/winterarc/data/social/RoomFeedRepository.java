package com.app.winterarc.data.social;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.data.social.db.FeedDao;
import com.app.winterarc.data.social.db.FeedPostEntity;
import com.app.winterarc.data.social.db.FeedPostLikeEntity;
import com.app.winterarc.data.social.db.FeedPostRow;
import com.app.winterarc.data.social.db.PostCommentEntity;
import com.app.winterarc.data.social.db.SocialDatabase;
import com.app.winterarc.domain.feed.FeedPost;
import com.app.winterarc.domain.feed.FeedRepository;
import com.app.winterarc.domain.feed.FeedResult;
import com.app.winterarc.domain.feed.FeedRules;
import com.app.winterarc.domain.feed.PostComment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Feed storage on Room. Writes run in transactions and enforce authorship. */
@Singleton
public class RoomFeedRepository implements FeedRepository {
    private final SocialDatabase database;
    private final FeedDao dao;
    private final TimeProvider time;

    @Inject
    public RoomFeedRepository(SocialDatabase database, FeedDao dao, TimeProvider time) {
        this.database = database;
        this.dao = dao;
        this.time = time;
    }

    @Override
    public LiveData<List<FeedPost>> observeTimeline(String viewerId) {
        return Transformations.map(dao.observeTimeline(viewerId), rows -> {
            List<FeedPost> out = new ArrayList<>(rows.size());
            for (FeedPostRow r : rows) out.add(post(r));
            return out;
        });
    }

    @Override
    public LiveData<FeedPost> observePost(String postId, String viewerId) {
        return Transformations.map(dao.observePost(postId, viewerId), r -> r == null ? null : post(r));
    }

    @Override
    public LiveData<List<PostComment>> observeComments(String postId) {
        return Transformations.map(dao.observeComments(postId), rows -> {
            List<PostComment> out = new ArrayList<>(rows.size());
            for (PostCommentEntity e : rows) out.add(comment(e));
            return out;
        });
    }

    @Override
    public FeedResult createPost(String actorId, String body) {
        String clean = FeedRules.clean(body, FeedRules.POST_MAX);
        if (clean == null || actorId == null || actorId.isBlank()) return FeedResult.INVALID_INPUT;
        FeedPostEntity e = new FeedPostEntity();
        e.id = UUID.randomUUID().toString();
        e.authorUserId = actorId;
        e.body = clean;
        e.createdAt = time.now();
        dao.insertPost(e);
        return FeedResult.SUCCESS;
    }

    @Override
    public FeedResult editPost(String actorId, String postId, String body) {
        String clean = FeedRules.clean(body, FeedRules.POST_MAX);
        if (clean == null) return FeedResult.INVALID_INPUT;
        return database.runInTransaction(() -> {
            FeedPostEntity e = dao.getPost(postId);
            if (e == null) return FeedResult.NOT_FOUND;
            if (!e.authorUserId.equals(actorId)) return FeedResult.PERMISSION_DENIED;
            if (e.body.equals(clean)) return FeedResult.SUCCESS;
            e.body = clean;
            e.editedAt = time.now();
            dao.updatePost(e);
            return FeedResult.SUCCESS;
        });
    }

    @Override
    public FeedResult deletePost(String actorId, String postId) {
        return database.runInTransaction(() -> {
            FeedPostEntity e = dao.getPost(postId);
            if (e == null) return FeedResult.NOT_FOUND;
            if (!e.authorUserId.equals(actorId)) return FeedResult.PERMISSION_DENIED;
            dao.deletePost(postId); // likes and comments cascade
            return FeedResult.SUCCESS;
        });
    }

    @Override
    public FeedResult toggleLike(String actorId, String postId) {
        return database.runInTransaction(() -> {
            if (dao.getPost(postId) == null) return FeedResult.NOT_FOUND;
            if (dao.likeExists(postId, actorId) > 0) {
                dao.deleteLike(postId, actorId);
            } else {
                FeedPostLikeEntity like = new FeedPostLikeEntity();
                like.postId = postId;
                like.userId = actorId;
                like.createdAt = time.now();
                dao.insertLike(like);
            }
            return FeedResult.SUCCESS;
        });
    }

    @Override
    public FeedResult addComment(String actorId, String postId, @Nullable String parentCommentId, String body) {
        String clean = FeedRules.clean(body, FeedRules.COMMENT_MAX);
        if (clean == null || actorId == null || actorId.isBlank()) return FeedResult.INVALID_INPUT;
        return database.runInTransaction(() -> {
            if (dao.getPost(postId) == null) return FeedResult.NOT_FOUND;
            if (parentCommentId != null) {
                PostCommentEntity parent = dao.getComment(parentCommentId);
                if (parent == null || !parent.postId.equals(postId)) return FeedResult.NOT_FOUND;
                if (parent.deleted) return FeedResult.INVALID_INPUT; // can't reply to a deleted comment
            }
            PostCommentEntity e = new PostCommentEntity();
            e.id = UUID.randomUUID().toString();
            e.postId = postId;
            e.parentCommentId = parentCommentId;
            e.authorUserId = actorId;
            e.body = clean;
            e.createdAt = time.now();
            dao.insertComment(e);
            return FeedResult.SUCCESS;
        });
    }

    @Override
    public FeedResult editComment(String actorId, String commentId, String body) {
        String clean = FeedRules.clean(body, FeedRules.COMMENT_MAX);
        if (clean == null) return FeedResult.INVALID_INPUT;
        return database.runInTransaction(() -> {
            PostCommentEntity e = dao.getComment(commentId);
            if (e == null || e.deleted) return FeedResult.NOT_FOUND;
            if (!e.authorUserId.equals(actorId)) return FeedResult.PERMISSION_DENIED;
            if (e.body.equals(clean)) return FeedResult.SUCCESS;
            e.body = clean;
            e.editedAt = time.now();
            dao.updateComment(e);
            return FeedResult.SUCCESS;
        });
    }

    /**
     * Deletes the actor's own comment. With replies it becomes a tombstone so the thread stays
     * readable; without replies it is removed, along with any tombstone ancestors left childless.
     */
    @Override
    public FeedResult deleteComment(String actorId, String commentId) {
        return database.runInTransaction(() -> {
            PostCommentEntity e = dao.getComment(commentId);
            if (e == null || e.deleted) return FeedResult.NOT_FOUND;
            if (!e.authorUserId.equals(actorId)) return FeedResult.PERMISSION_DENIED;
            if (dao.childCount(commentId) > 0) {
                e.deleted = true;
                e.body = "";
                dao.updateComment(e);
                return FeedResult.SUCCESS;
            }
            String parentId = e.parentCommentId;
            dao.deleteComment(commentId);
            while (parentId != null) {
                PostCommentEntity parent = dao.getComment(parentId);
                if (parent == null || !parent.deleted || dao.childCount(parentId) > 0) break;
                parentId = parent.parentCommentId;
                dao.deleteComment(parent.id);
            }
            return FeedResult.SUCCESS;
        });
    }

    private static FeedPost post(FeedPostRow r) {
        return new FeedPost(r.id, r.authorUserId, r.body, r.createdAt, r.editedAt, r.likeCount, r.likedByMe, r.replyCount);
    }

    private static PostComment comment(PostCommentEntity e) {
        return new PostComment(e.id, e.postId, e.parentCommentId, e.authorUserId, e.body, e.createdAt, e.editedAt, e.deleted);
    }
}
