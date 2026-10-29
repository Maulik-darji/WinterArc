package com.app.winterarc.data.social.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FeedDao {
    String POST_ROW = "SELECT p.id, p.author_user_id, p.body, p.created_at, p.edited_at, "
            + "(SELECT COUNT(*) FROM feed_post_likes l WHERE l.post_id = p.id) AS like_count, "
            + "EXISTS(SELECT 1 FROM feed_post_likes l WHERE l.post_id = p.id AND l.user_id = :viewerId) AS liked_by_me, "
            + "(SELECT COUNT(*) FROM post_comments c WHERE c.post_id = p.id AND c.deleted = 0) AS reply_count "
            + "FROM feed_posts p ";

    @Query(POST_ROW + "ORDER BY p.created_at DESC, p.id DESC")
    LiveData<List<FeedPostRow>> observeTimeline(String viewerId);

    @Query(POST_ROW + "WHERE p.id = :postId")
    LiveData<FeedPostRow> observePost(String postId, String viewerId);

    @Query("SELECT * FROM feed_posts WHERE id = :postId")
    FeedPostEntity getPost(String postId);

    @Insert
    void insertPost(FeedPostEntity post);

    @Update
    void updatePost(FeedPostEntity post);

    @Query("DELETE FROM feed_posts WHERE id = :postId")
    void deletePost(String postId);

    @Query("SELECT COUNT(*) FROM feed_post_likes WHERE post_id = :postId AND user_id = :userId")
    int likeExists(String postId, String userId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertLike(FeedPostLikeEntity like);

    @Query("DELETE FROM feed_post_likes WHERE post_id = :postId AND user_id = :userId")
    void deleteLike(String postId, String userId);

    @Query("SELECT * FROM post_comments WHERE post_id = :postId ORDER BY created_at ASC, id ASC")
    LiveData<List<PostCommentEntity>> observeComments(String postId);

    @Query("SELECT * FROM post_comments WHERE id = :commentId")
    PostCommentEntity getComment(String commentId);

    @Query("SELECT COUNT(*) FROM post_comments WHERE parent_comment_id = :commentId")
    int childCount(String commentId);

    @Insert
    void insertComment(PostCommentEntity comment);

    @Update
    void updateComment(PostCommentEntity comment);

    @Query("DELETE FROM post_comments WHERE id = :commentId")
    void deleteComment(String commentId);

    // ---- cloud snapshot (CloudSync)

    /** Likes and comments go with their posts (foreign-key cascade). */
    @Query("DELETE FROM feed_posts")
    void clearFeed();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPosts(List<FeedPostEntity> posts);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertLikes(List<FeedPostLikeEntity> likes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertComments(List<PostCommentEntity> comments);
}
