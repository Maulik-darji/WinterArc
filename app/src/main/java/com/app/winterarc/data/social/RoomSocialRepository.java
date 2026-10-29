package com.app.winterarc.data.social;

import androidx.lifecycle.LiveData;
import com.app.winterarc.data.cloud.CloudSync;
import com.google.gson.JsonObject;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.app.winterarc.data.social.db.CommunityEntity;
import com.app.winterarc.data.social.db.CommunityMemberEntity;
import com.app.winterarc.data.social.db.CommunityMessageEntity;
import com.app.winterarc.data.social.db.CommunityPostEntity;
import com.app.winterarc.data.social.db.FollowEntity;
import com.app.winterarc.data.social.db.PostLikeEntity;
import com.app.winterarc.data.social.db.PostRow;
import com.app.winterarc.data.social.db.SocialDao;
import com.app.winterarc.data.social.db.SocialDatabase;
import com.app.winterarc.data.social.db.SocialUserEntity;
import com.app.winterarc.domain.repository.SocialRepository;
import com.app.winterarc.domain.social.Community;
import com.app.winterarc.domain.social.CommunityAction;
import com.app.winterarc.domain.social.CommunityMember;
import com.app.winterarc.domain.social.CommunityMessage;
import com.app.winterarc.domain.social.CommunityPermissions;
import com.app.winterarc.domain.social.CommunityPost;
import com.app.winterarc.domain.social.Follow;
import com.app.winterarc.domain.social.MemberRole;
import com.app.winterarc.domain.social.SocialPolicy;
import com.app.winterarc.domain.social.SocialResult;
import com.app.winterarc.domain.social.SocialUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RoomSocialRepository implements SocialRepository {
    private final SocialDatabase database;
    private final SocialDao dao;
    private final MutableLiveData<String> currentUserId = new MutableLiveData<>("");

    /** Syncs profiles and follows with the website for cloud accounts; null keeps them local (tests). */
    @androidx.annotation.Nullable private final CloudSync cloud;
    /** Where images are kept for local (non-cloud) accounts. */
    @androidx.annotation.Nullable private final java.io.File mediaDir;

    public RoomSocialRepository(SocialDatabase database, SocialDao dao) {
        this(database, dao, null, (java.io.File) null);
    }

    public RoomSocialRepository(SocialDatabase database, SocialDao dao, @androidx.annotation.Nullable CloudSync cloud) {
        this(database, dao, cloud, (java.io.File) null);
    }

    @Inject
    public RoomSocialRepository(SocialDatabase database, SocialDao dao, @androidx.annotation.Nullable CloudSync cloud,
                                @dagger.hilt.android.qualifiers.ApplicationContext android.content.Context context) {
        this(database, dao, cloud, new java.io.File(context.getFilesDir(), "profile-media"));
    }

    private RoomSocialRepository(SocialDatabase database, SocialDao dao, @androidx.annotation.Nullable CloudSync cloud,
                                 @androidx.annotation.Nullable java.io.File mediaDir) {
        this.database = database;
        this.dao = dao;
        this.cloud = cloud;
        this.mediaDir = mediaDir;
        if (mediaDir != null && !mediaDir.exists()) mediaDir.mkdirs();
    }

    private boolean cloudActive() {
        return cloud != null && cloud.isActive();
    }

    private static SocialResult socialResult(CloudSync.Outcome outcome) {
        switch (outcome) {
            case SUCCESS: return SocialResult.SUCCESS;
            case NOT_ALLOWED: return SocialResult.PERMISSION_DENIED;
            case NOT_FOUND: return SocialResult.NOT_FOUND;
            case INVALID: return SocialResult.INVALID_INPUT;
            default: return SocialResult.UNAVAILABLE;
        }
    }

    @Override public LiveData<List<SocialUser>> observeUsers() {
        return Transformations.map(dao.observeUsers(), rows -> map(rows, RoomSocialRepository::user));
    }

    @Override public LiveData<List<Community>> observeCommunities() {
        return Transformations.map(dao.observeCommunities(), rows -> map(rows, RoomSocialRepository::community));
    }

    @Override public LiveData<List<CommunityMember>> observeMembers() {
        return Transformations.map(dao.observeMembers(), rows -> map(rows, RoomSocialRepository::member));
    }

    @Override public LiveData<List<CommunityPost>> observePosts() {
        return Transformations.switchMap(currentUserId,
                id -> Transformations.map(dao.observePosts(id), rows -> map(rows, RoomSocialRepository::post)));
    }

    @Override public LiveData<List<Follow>> observeFollows() {
        return Transformations.map(dao.observeFollows(), rows -> map(rows, RoomSocialRepository::follow));
    }

    @Override public LiveData<List<CommunityMessage>> observeMessages(String communityId) {
        return Transformations.map(dao.observeMessages(communityId), rows -> map(rows, RoomSocialRepository::message));
    }

    @Override
    public void ensureProfile(String userId, String displayName, String handle) {
        if (userId == null || userId.isBlank()) return;
        currentUserId.postValue(userId);
        database.runInTransaction(() -> {
            purgeLegacySampleData();
            if (dao.getUser(userId) == null) dao.upsertUser(newUser(userId, displayName, handle, ""));
        });
        if (cloudActive()) {
            // Creates the shared profile if this account has none yet; one made on the website is
            // kept, and the refresh that follows brings it (name, @handle, bio) to this device.
            JsonObject profile = new JsonObject();
            profile.addProperty("user_id", userId);
            profile.addProperty("display_name", clean(displayName, 50));
            profile.addProperty("handle", handle);
            profile.addProperty("bio", "");
            cloud.ensureProfile(profile);
        }
    }

    @Override
    public SocialResult updateProfile(String actorUserId, String displayName, String bio) {
        SocialUserEntity user = dao.getUser(actorUserId);
        String clean = clean(displayName, 50);
        if (user == null) return SocialResult.NOT_FOUND;
        if (clean.length() < 2) return SocialResult.INVALID_INPUT;
        if (cloudActive()) {
            JsonObject changes = new JsonObject();
            changes.addProperty("display_name", clean);
            changes.addProperty("bio", clean(bio, 160));
            return socialResult(cloud.write(bearer -> cloud.api().updateProfile(bearer, "eq." + actorUserId, changes)));
        }
        user.displayName = clean;
        user.bio = clean(bio, 160);
        user.initial = clean.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        dao.upsertUser(user);
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult setProfileImage(String actorUserId, String kind, @androidx.annotation.Nullable byte[] jpeg) {
        if (!"avatar".equals(kind) && !"banner".equals(kind)) return SocialResult.INVALID_INPUT;
        if (dao.getUser(actorUserId) == null) return SocialResult.NOT_FOUND;
        String url = null;
        if (cloudActive()) {
            if (jpeg != null) {
                url = cloud.uploadProfileImage(actorUserId, kind, jpeg);
                if (url == null) return SocialResult.UNAVAILABLE;
            }
            JsonObject changes = new JsonObject();
            if (url == null) changes.add(kind + "_url", com.google.gson.JsonNull.INSTANCE);
            else changes.addProperty(kind + "_url", url);
            okhttp3.RequestBody json = okhttp3.RequestBody.create(changes.toString(), okhttp3.MediaType.get("application/json"));
            return socialResult(cloud.write(bearer -> cloud.api().updateProfileJson(bearer, "eq." + actorUserId, json)));
        }
        if (jpeg != null) {
            if (mediaDir == null) return SocialResult.UNAVAILABLE;
            java.io.File file = new java.io.File(mediaDir, actorUserId.replaceAll("[^A-Za-z0-9_-]", "_") + "-" + kind
                    + "-" + System.currentTimeMillis() + ".jpg");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                out.write(jpeg);
            } catch (java.io.IOException e) {
                return SocialResult.UNAVAILABLE;
            }
            url = android.net.Uri.fromFile(file).toString();
        }
        SocialUserEntity user = dao.getUser(actorUserId);
        if ("avatar".equals(kind)) user.avatarUrl = url; else user.bannerUrl = url;
        dao.upsertUser(user);
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult toggleFollow(String actorUserId, String targetUserId) {
        if (actorUserId.equals(targetUserId) || dao.getUser(targetUserId) == null) return SocialResult.INVALID_INPUT;
        if (cloudActive()) {
            if (dao.hasFollow(actorUserId, targetUserId) > 0) {
                return socialResult(cloud.write(bearer -> cloud.api().unfollow(bearer, "eq." + actorUserId, "eq." + targetUserId)));
            }
            JsonObject follow = new JsonObject();
            follow.addProperty("followed_user_id", targetUserId);
            return socialResult(cloud.write(bearer -> cloud.api().follow(bearer, follow)));
        }
        if (dao.hasFollow(actorUserId, targetUserId) > 0) {
            dao.deleteFollow(actorUserId, targetUserId);
        } else {
            FollowEntity row = new FollowEntity();
            row.followerUserId = actorUserId;
            row.followedUserId = targetUserId;
            row.createdAt = Instant.now();
            dao.insertFollow(row);
        }
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult joinCommunity(String actorUserId, String communityId) {
        if (dao.getUser(actorUserId) == null || dao.getCommunity(communityId) == null) return SocialResult.NOT_FOUND;
        if (dao.getMember(communityId, actorUserId) != null) return SocialResult.ALREADY_EXISTS;
        CommunityMemberEntity row = new CommunityMemberEntity();
        row.communityId = communityId;
        row.userId = actorUserId;
        row.role = MemberRole.MEMBER.key();
        row.joinedAt = Instant.now();
        dao.upsertMember(row);
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult leaveCommunity(String actorUserId, String communityId) {
        CommunityMemberEntity member = dao.getMember(communityId, actorUserId);
        if (member == null) return SocialResult.NOT_MEMBER;
        if (MemberRole.fromKey(member.role) == MemberRole.OWNER) return SocialResult.PERMISSION_DENIED;
        dao.deleteMember(communityId, actorUserId);
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult createCommunity(String actorUserId, String name, String description) {
        String cleanName = clean(name, 50);
        if (dao.getUser(actorUserId) == null) return SocialResult.NOT_FOUND;
        if (cleanName.length() < 3) return SocialResult.INVALID_INPUT;
        String id = "community-" + UUID.randomUUID();
        Instant now = Instant.now();
        database.runInTransaction(() -> {
            CommunityEntity c = new CommunityEntity();
            c.id = id;
            c.name = cleanName;
            c.description = clean(description, 240);
            c.ownerUserId = actorUserId;
            c.membersCanPost = true;
            c.membersCanChat = true;
            c.membersCanInvite = false;
            c.createdAt = now;
            dao.upsertCommunity(c);
            CommunityMemberEntity owner = new CommunityMemberEntity();
            owner.communityId = id;
            owner.userId = actorUserId;
            owner.role = MemberRole.OWNER.key();
            owner.joinedAt = now;
            dao.upsertMember(owner);
        });
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult createPost(String actorUserId, String communityId, String body) {
        String clean = clean(body, 1_000);
        if (clean.isEmpty()) return SocialResult.INVALID_INPUT;
        CommunityEntity c = dao.getCommunity(communityId);
        CommunityMemberEntity membership = dao.getMember(communityId, actorUserId);
        if (c == null) return SocialResult.NOT_FOUND;
        if (membership == null) return SocialResult.NOT_MEMBER;
        if (!SocialPolicy.can(MemberRole.fromKey(membership.role), permissions(c), CommunityAction.POST)) {
            return SocialResult.PERMISSION_DENIED;
        }
        CommunityPostEntity post = new CommunityPostEntity();
        post.id = "post-" + UUID.randomUUID();
        post.communityId = communityId;
        post.authorUserId = actorUserId;
        post.body = clean;
        post.createdAt = Instant.now();
        dao.insertPost(post);
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult toggleLike(String actorUserId, String postId) {
        if (dao.getPost(postId) == null || dao.getUser(actorUserId) == null) return SocialResult.NOT_FOUND;
        if (dao.hasLike(postId, actorUserId) > 0) {
            dao.deleteLike(postId, actorUserId);
        } else {
            PostLikeEntity like = new PostLikeEntity();
            like.postId = postId;
            like.userId = actorUserId;
            like.createdAt = Instant.now();
            dao.insertLike(like);
        }
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult sendMessage(String actorUserId, String communityId, String body) {
        String clean = clean(body, 2_000);
        if (clean.isEmpty()) return SocialResult.INVALID_INPUT;
        CommunityEntity c = dao.getCommunity(communityId);
        CommunityMemberEntity membership = dao.getMember(communityId, actorUserId);
        if (c == null) return SocialResult.NOT_FOUND;
        if (membership == null) return SocialResult.NOT_MEMBER;
        if (!SocialPolicy.can(MemberRole.fromKey(membership.role), permissions(c), CommunityAction.CHAT)) {
            return SocialResult.PERMISSION_DENIED;
        }
        CommunityMessageEntity message = new CommunityMessageEntity();
        message.id = "message-" + UUID.randomUUID();
        message.communityId = communityId;
        message.senderUserId = actorUserId;
        message.body = clean;
        message.createdAt = Instant.now();
        dao.insertMessage(message);
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult updatePermissions(String actorUserId, String communityId, CommunityPermissions permissions) {
        CommunityEntity c = dao.getCommunity(communityId);
        CommunityMemberEntity actor = dao.getMember(communityId, actorUserId);
        if (c == null) return SocialResult.NOT_FOUND;
        if (actor == null || !SocialPolicy.can(MemberRole.fromKey(actor.role), permissions(c), CommunityAction.MANAGE_PERMISSIONS)) {
            return SocialResult.PERMISSION_DENIED;
        }
        c.membersCanPost = permissions.membersCanPost();
        c.membersCanChat = permissions.membersCanChat();
        c.membersCanInvite = permissions.membersCanInvite();
        dao.upsertCommunity(c);
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult setRole(String actorUserId, String communityId, String targetUserId, MemberRole requested) {
        CommunityMemberEntity actor = dao.getMember(communityId, actorUserId);
        CommunityMemberEntity target = dao.getMember(communityId, targetUserId);
        if (actor == null || target == null) return SocialResult.NOT_MEMBER;
        if (!SocialPolicy.canAssignRole(MemberRole.fromKey(actor.role), MemberRole.fromKey(target.role), requested)) {
            return SocialResult.PERMISSION_DENIED;
        }
        target.role = requested.key();
        dao.upsertMember(target);
        return SocialResult.SUCCESS;
    }

    /** Ids of the sample rows seeded by earlier builds; removed from existing installs. */
    private static final List<String> LEGACY_USERS = java.util.Arrays.asList("user-maya", "user-arjun", "user-nina");
    private static final List<String> LEGACY_COMMUNITIES =
            java.util.Arrays.asList("community-running", "community-focus", "community-reading");
    private static final List<String> LEGACY_POSTS = java.util.Arrays.asList("post-maya", "post-arjun", "post-nina");

    private void purgeLegacySampleData() {
        dao.purgeLikes(LEGACY_POSTS, LEGACY_USERS);
        dao.purgePosts(LEGACY_POSTS, LEGACY_COMMUNITIES, LEGACY_USERS);
        dao.purgeMessages(LEGACY_COMMUNITIES, LEGACY_USERS);
        dao.purgeMembers(LEGACY_COMMUNITIES, LEGACY_USERS);
        dao.purgeFollows(LEGACY_USERS);
        dao.purgeCommunities(LEGACY_COMMUNITIES);
        dao.purgeUsers(LEGACY_USERS);
    }

    private void member(String communityId, String userId, MemberRole role, Instant at) {
        CommunityMemberEntity row = new CommunityMemberEntity(); row.communityId = communityId; row.userId = userId;
        row.role = role.key(); row.joinedAt = at; dao.upsertMember(row);
    }




    private static SocialUserEntity newUser(String id, String name, String handle, String bio) {
        SocialUserEntity row = new SocialUserEntity(); row.userId = id; row.displayName = name; row.handle = handle;
        row.bio = bio; row.initial = name.substring(0, 1).toUpperCase(java.util.Locale.ROOT); return row;
    }

    private static CommunityEntity newCommunity(String id, String name, String description, String owner,
                                                 boolean post, boolean chat, boolean invite) {
        CommunityEntity row = new CommunityEntity(); row.id = id; row.name = name; row.description = description;
        row.ownerUserId = owner; row.membersCanPost = post; row.membersCanChat = chat;
        row.membersCanInvite = invite; row.createdAt = Instant.EPOCH; return row;
    }

    private static String clean(String input, int max) {
        String value = input == null ? "" : input.trim().replaceAll("\\s+", " ");
        return value.length() <= max ? value : value.substring(0, max).trim();
    }

    private static CommunityPermissions permissions(CommunityEntity c) {
        return new CommunityPermissions(c.membersCanPost, c.membersCanChat, c.membersCanInvite);
    }

    private static SocialUser user(SocialUserEntity r) {
        return new SocialUser(r.userId, r.displayName, r.handle, r.bio, r.initial, r.avatarUrl, r.bannerUrl);
    }
    private static Community community(CommunityEntity r) { return new Community(r.id, r.name, r.description, r.ownerUserId, permissions(r), r.createdAt); }
    private static CommunityMember member(CommunityMemberEntity r) { return new CommunityMember(r.communityId, r.userId, MemberRole.fromKey(r.role), r.joinedAt); }
    private static CommunityPost post(PostRow r) { return new CommunityPost(r.id, r.communityId, r.authorUserId, r.body, r.createdAt, r.likeCount, r.likedByMe); }
    private static Follow follow(FollowEntity r) { return new Follow(r.followerUserId, r.followedUserId, r.createdAt); }
    private static CommunityMessage message(CommunityMessageEntity r) { return new CommunityMessage(r.id, r.communityId, r.senderUserId, r.body, r.createdAt); }

    private interface Mapper<A, B> { B map(A value); }
    private static <A, B> List<B> map(List<A> rows, Mapper<A, B> mapper) {
        List<B> result = new ArrayList<>(rows.size());
        for (A row : rows) result.add(mapper.map(row));
        return result;
    }
}
