package com.app.winterarc.data.social.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SocialDao {
    @Query("SELECT * FROM social_users ORDER BY display_name")
    LiveData<List<SocialUserEntity>> observeUsers();

    @Query("SELECT * FROM social_users WHERE user_id = :userId")
    SocialUserEntity getUser(String userId);

    @Query("SELECT COUNT(*) FROM social_users")
    int userCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertUser(SocialUserEntity user);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertUsers(java.util.List<SocialUserEntity> users);

    @Query("DELETE FROM follows")
    void clearFollows();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertFollows(java.util.List<FollowEntity> follows);

    @Query("SELECT * FROM communities ORDER BY created_at DESC")
    LiveData<List<CommunityEntity>> observeCommunities();

    @Query("SELECT * FROM communities WHERE id = :communityId")
    CommunityEntity getCommunity(String communityId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCommunity(CommunityEntity community);

    @Query("SELECT * FROM community_members ORDER BY joined_at")
    LiveData<List<CommunityMemberEntity>> observeMembers();

    @Query("SELECT * FROM community_members WHERE community_id = :communityId AND user_id = :userId")
    CommunityMemberEntity getMember(String communityId, String userId);

    @Query("SELECT COUNT(*) FROM community_members WHERE community_id = :communityId")
    int memberCount(String communityId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertMember(CommunityMemberEntity member);

    @Query("DELETE FROM community_members WHERE community_id = :communityId AND user_id = :userId")
    void deleteMember(String communityId, String userId);

    @Query("SELECT p.id, p.community_id, p.author_user_id, p.body, p.created_at, " +
            "COUNT(l.user_id) AS like_count, " +
            "MAX(CASE WHEN l.user_id = :userId THEN 1 ELSE 0 END) AS liked_by_me " +
            "FROM community_posts p LEFT JOIN post_likes l ON p.id = l.post_id " +
            "GROUP BY p.id ORDER BY p.created_at DESC")
    LiveData<List<PostRow>> observePosts(String userId);

    @Query("SELECT * FROM community_posts WHERE id = :postId")
    CommunityPostEntity getPost(String postId);

    @Query("SELECT COUNT(*) FROM community_posts WHERE community_id = :communityId")
    int postCount(String communityId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertPost(CommunityPostEntity post);

    @Query("SELECT COUNT(*) FROM post_likes WHERE post_id = :postId AND user_id = :userId")
    int hasLike(String postId, String userId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertLike(PostLikeEntity like);

    @Query("DELETE FROM post_likes WHERE post_id = :postId AND user_id = :userId")
    void deleteLike(String postId, String userId);

    @Query("SELECT * FROM follows ORDER BY created_at DESC")
    LiveData<List<FollowEntity>> observeFollows();

    @Query("SELECT COUNT(*) FROM follows WHERE follower_user_id = :actorId AND followed_user_id = :targetId")
    int hasFollow(String actorId, String targetId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertFollow(FollowEntity follow);

    @Query("DELETE FROM follows WHERE follower_user_id = :actorId AND followed_user_id = :targetId")
    void deleteFollow(String actorId, String targetId);

    @Query("SELECT * FROM community_messages WHERE community_id = :communityId ORDER BY created_at ASC")
    LiveData<List<CommunityMessageEntity>> observeMessages(String communityId);

    @Query("SELECT COUNT(*) FROM community_messages WHERE community_id = :communityId")
    int messageCount(String communityId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertMessage(CommunityMessageEntity message);

    // ---- Removal of the sample data earlier builds seeded (people, communities, posts, chat). ----
    @Query("DELETE FROM post_likes WHERE post_id IN (:postIds) OR user_id IN (:userIds)")
    void purgeLikes(java.util.List<String> postIds, java.util.List<String> userIds);

    @Query("DELETE FROM community_posts WHERE id IN (:postIds) OR community_id IN (:communityIds) OR author_user_id IN (:userIds)")
    void purgePosts(java.util.List<String> postIds, java.util.List<String> communityIds, java.util.List<String> userIds);

    @Query("DELETE FROM community_messages WHERE community_id IN (:communityIds) OR sender_user_id IN (:userIds)")
    void purgeMessages(java.util.List<String> communityIds, java.util.List<String> userIds);

    @Query("DELETE FROM community_members WHERE community_id IN (:communityIds) OR user_id IN (:userIds)")
    void purgeMembers(java.util.List<String> communityIds, java.util.List<String> userIds);

    @Query("DELETE FROM follows WHERE follower_user_id IN (:userIds) OR followed_user_id IN (:userIds)")
    void purgeFollows(java.util.List<String> userIds);

    @Query("DELETE FROM communities WHERE id IN (:communityIds)")
    void purgeCommunities(java.util.List<String> communityIds);

    @Query("DELETE FROM social_users WHERE user_id IN (:userIds)")
    void purgeUsers(java.util.List<String> userIds);
}
