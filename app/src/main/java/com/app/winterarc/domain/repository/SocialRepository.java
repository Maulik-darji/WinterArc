package com.app.winterarc.domain.repository;

import androidx.lifecycle.LiveData;

import com.app.winterarc.domain.social.Community;
import com.app.winterarc.domain.social.CommunityMember;
import com.app.winterarc.domain.social.CommunityMessage;
import com.app.winterarc.domain.social.CommunityPermissions;
import com.app.winterarc.domain.social.CommunityPost;
import com.app.winterarc.domain.social.Follow;
import com.app.winterarc.domain.social.MemberRole;
import com.app.winterarc.domain.social.SocialResult;
import com.app.winterarc.domain.social.SocialUser;

import java.util.List;

public interface SocialRepository {
    LiveData<List<SocialUser>> observeUsers();
    LiveData<List<Community>> observeCommunities();
    LiveData<List<CommunityMember>> observeMembers();
    LiveData<List<CommunityPost>> observePosts();
    LiveData<List<Follow>> observeFollows();
    LiveData<List<CommunityMessage>> observeMessages(String communityId);

    /** Creates the signed-in user's profile if missing. No sample people or communities are added. */
    void ensureProfile(String currentUserId, String displayName, String handle);
    SocialResult updateProfile(String actorUserId, String displayName, String bio);

    /**
     * Sets ({@code jpeg} non-null) or removes the user's profile photo ({@code "avatar"}) or banner
     * ({@code "banner"}). With a cloud account the image is uploaded and shared with the website.
     */
    SocialResult setProfileImage(String actorUserId, String kind, @androidx.annotation.Nullable byte[] jpeg);
    SocialResult toggleFollow(String actorUserId, String targetUserId);
    SocialResult joinCommunity(String actorUserId, String communityId);
    SocialResult leaveCommunity(String actorUserId, String communityId);
    SocialResult createCommunity(String actorUserId, String name, String description);
    SocialResult createPost(String actorUserId, String communityId, String body);
    SocialResult toggleLike(String actorUserId, String postId);
    SocialResult sendMessage(String actorUserId, String communityId, String body);
    SocialResult updatePermissions(String actorUserId, String communityId, CommunityPermissions permissions);
    SocialResult setRole(String actorUserId, String communityId, String targetUserId, MemberRole role);
}
