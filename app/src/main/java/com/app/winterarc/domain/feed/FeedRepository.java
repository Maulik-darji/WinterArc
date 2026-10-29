package com.app.winterarc.domain.feed;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * Feed posts, likes and threaded comments. Every write takes the acting user id and enforces
 * authorship here, so edit/delete rules hold regardless of what the UI shows.
 */
public interface FeedRepository {
    LiveData<List<FeedPost>> observeTimeline(String viewerId);

    LiveData<FeedPost> observePost(String postId, String viewerId);

    LiveData<List<PostComment>> observeComments(String postId);

    /** Asks for the latest shared posts (the website's too); no-op when everything is local. */
    default void refresh() {}

    /** Live updates while a feed screen is visible (reference counted); no-op when local. */
    default void startLive() {}

    default void stopLive() {}

    @WorkerThread
    FeedResult createPost(String actorId, String body);

    @WorkerThread
    FeedResult editPost(String actorId, String postId, String body);

    @WorkerThread
    FeedResult deletePost(String actorId, String postId);

    @WorkerThread
    FeedResult toggleLike(String actorId, String postId);

    /** @param parentCommentId null for a top-level comment, else the comment being replied to */
    @WorkerThread
    FeedResult addComment(String actorId, String postId, @Nullable String parentCommentId, String body);

    @WorkerThread
    FeedResult editComment(String actorId, String commentId, String body);

    @WorkerThread
    FeedResult deleteComment(String actorId, String commentId);
}
