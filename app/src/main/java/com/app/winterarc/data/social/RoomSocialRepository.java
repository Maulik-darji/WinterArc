package com.app.winterarc.data.social;

import androidx.lifecycle.LiveData;
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

    @Inject
    public RoomSocialRepository(SocialDatabase database, SocialDao dao) {
        this.database = database;
        this.dao = dao;
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
    public void ensureSeeded(String userId) {
        if (userId == null || userId.isBlank()) return;
        currentUserId.postValue(userId);
        database.runInTransaction(() -> {
            if (dao.userCount() == 0) seed(userId);
            if (dao.getUser(userId) == null) dao.upsertUser(newUser(userId, "WinterArc member", "@winterarc", "Building one day at a time."));
        });
    }

    @Override
    public SocialResult updateProfile(String actorUserId, String displayName, String bio) {
        SocialUserEntity user = dao.getUser(actorUserId);
        String clean = clean(displayName, 50);
        if (user == null) return SocialResult.NOT_FOUND;
        if (clean.length() < 2) return SocialResult.INVALID_INPUT;
        user.displayName = clean;
        user.bio = clean(bio, 160);
        user.initial = clean.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        dao.upsertUser(user);
        return SocialResult.SUCCESS;
    }

    @Override
    public SocialResult toggleFollow(String actorUserId, String targetUserId) {
        if (actorUserId.equals(targetUserId) || dao.getUser(targetUserId) == null) return SocialResult.INVALID_INPUT;
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

    private void seed(String currentUser) {
        dao.upsertUser(newUser(currentUser, "WinterArc member", "@winterarc", "Building one day at a time."));
        dao.upsertUser(newUser("user-maya", "Maya R.", "@mayaruns", "Sunrise runner · steady over perfect."));
        dao.upsertUser(newUser("user-arjun", "Arjun S.", "@deepworkarjun", "Focused work, calmer days."));
        dao.upsertUser(newUser("user-nina", "Nina K.", "@ninareads", "Always carrying one more book."));

        CommunityEntity running = newCommunity("community-running", "Morning Runners",
                "A supportive place for early miles, patient progress, and honest check-ins.", "user-maya", true, true, true);
        CommunityEntity focus = newCommunity("community-focus", "Deep Work Club",
                "Build a sustainable focus habit without glorifying burnout.", "user-arjun", true, true, false);
        CommunityEntity reading = newCommunity("community-reading", "Read Together",
                "Share what you are reading and keep the pages turning.", "user-nina", true, false, true);
        dao.upsertCommunity(running);
        dao.upsertCommunity(focus);
        dao.upsertCommunity(reading);
        member(running.id, "user-maya", MemberRole.OWNER, Instant.parse("2026-01-01T00:00:00Z"));
        member(running.id, currentUser, MemberRole.MEMBER, Instant.parse("2026-09-20T00:00:00Z"));
        member(running.id, "user-nina", MemberRole.MEMBER, Instant.parse("2026-09-22T00:00:00Z"));
        member(focus.id, "user-arjun", MemberRole.OWNER, Instant.parse("2026-01-05T00:00:00Z"));
        member(reading.id, "user-nina", MemberRole.OWNER, Instant.parse("2026-02-01T00:00:00Z"));

        seedPost("post-maya", running.id, "user-maya",
                "Week three of showing up before sunrise. Today was slower, but I still finished all 5 km — consistency is finally starting to feel natural.",
                Instant.parse("2026-10-04T06:15:00Z"));
        seedPost("post-arjun", focus.id, "user-arjun",
                "Closed my laptop after a focused 45-minute session instead of pushing until I burned out. Small, repeatable wins are the plan this time.",
                Instant.parse("2026-10-04T05:30:00Z"));
        seedPost("post-nina", reading.id, "user-nina",
                "Finished the final chapter today. That makes 14 reading days in a row. What are you reading this week?",
                Instant.parse("2026-10-04T04:00:00Z"));
        like("post-maya", "user-arjun"); like("post-maya", "user-nina");
        like("post-nina", "user-maya");
        seedMessage("message-1", running.id, "user-maya", "Welcome! What is everyone aiming for tomorrow morning?", Instant.parse("2026-10-04T05:00:00Z"));
        seedMessage("message-2", running.id, "user-nina", "A relaxed 3 km for me — keeping it easy today.", Instant.parse("2026-10-04T05:04:00Z"));
    }

    private void member(String communityId, String userId, MemberRole role, Instant at) {
        CommunityMemberEntity row = new CommunityMemberEntity(); row.communityId = communityId; row.userId = userId;
        row.role = role.key(); row.joinedAt = at; dao.upsertMember(row);
    }

    private void seedPost(String id, String communityId, String authorId, String body, Instant at) {
        CommunityPostEntity row = new CommunityPostEntity(); row.id = id; row.communityId = communityId;
        row.authorUserId = authorId; row.body = body; row.createdAt = at; dao.insertPost(row);
    }

    private void seedMessage(String id, String communityId, String senderId, String body, Instant at) {
        CommunityMessageEntity row = new CommunityMessageEntity(); row.id = id; row.communityId = communityId;
        row.senderUserId = senderId; row.body = body; row.createdAt = at; dao.insertMessage(row);
    }

    private void like(String postId, String userId) {
        PostLikeEntity row = new PostLikeEntity(); row.postId = postId; row.userId = userId; row.createdAt = Instant.EPOCH;
        dao.insertLike(row);
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

    private static SocialUser user(SocialUserEntity r) { return new SocialUser(r.userId, r.displayName, r.handle, r.bio, r.initial); }
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
