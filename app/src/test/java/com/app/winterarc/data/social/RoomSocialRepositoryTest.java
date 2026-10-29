package com.app.winterarc.data.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.app.winterarc.data.social.db.CommunityEntity;
import com.app.winterarc.data.social.db.CommunityMemberEntity;
import com.app.winterarc.data.social.db.CommunityMessageEntity;
import com.app.winterarc.data.social.db.SocialDao;
import com.app.winterarc.data.social.db.SocialUserEntity;
import com.app.winterarc.data.social.db.SocialDatabase;
import com.app.winterarc.domain.social.CommunityPermissions;
import com.app.winterarc.domain.social.MemberRole;
import com.app.winterarc.domain.social.SocialResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.Instant;

/** Integration tests that exercise the repository against a real in-memory Room database. */
@RunWith(AndroidJUnit4.class)
@Config(application = Application.class)
public class RoomSocialRepositoryTest {
    private static final String ME = "test-current-user";
    private SocialDatabase database;
    private SocialDao dao;
    private RoomSocialRepository repository;

    @Before
    public void setUp() {
        database = Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(), SocialDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.socialDao();
        repository = new RoomSocialRepository(database, dao);
        repository.ensureProfile(ME, "Me", "@me");
        insertFixtures();
    }

    /** Test-only communities, people and messages (the app itself no longer ships sample data). */
    private void insertFixtures() {
        user("user-maya", "Maya");
        user("user-arjun", "Arjun");
        user("user-nina", "Nina");
        community("community-running", "user-maya", true, true, true);
        community("community-focus", "user-arjun", true, true, false);
        community("community-reading", "user-nina", true, false, true);
        member("community-running", "user-maya", MemberRole.OWNER);
        member("community-running", ME, MemberRole.MEMBER);
        member("community-running", "user-nina", MemberRole.MEMBER);
        member("community-focus", "user-arjun", MemberRole.OWNER);
        member("community-reading", "user-nina", MemberRole.OWNER);
        message("m1", "community-running", "user-maya");
        message("m2", "community-running", "user-nina");
    }

    private void user(String id, String name) {
        SocialUserEntity row = new SocialUserEntity();
        row.userId = id; row.displayName = name; row.handle = "@" + id; row.bio = ""; row.initial = name.substring(0, 1);
        dao.upsertUser(row);
    }

    private void community(String id, String owner, boolean post, boolean chat, boolean invite) {
        CommunityEntity row = new CommunityEntity();
        row.id = id; row.name = id; row.description = ""; row.ownerUserId = owner;
        row.membersCanPost = post; row.membersCanChat = chat; row.membersCanInvite = invite; row.createdAt = Instant.EPOCH;
        dao.upsertCommunity(row);
    }

    private void member(String communityId, String userId, MemberRole role) {
        CommunityMemberEntity row = new CommunityMemberEntity();
        row.communityId = communityId; row.userId = userId; row.role = role.key(); row.joinedAt = Instant.EPOCH;
        dao.upsertMember(row);
    }

    private void message(String id, String communityId, String senderId) {
        CommunityMessageEntity row = new CommunityMessageEntity();
        row.id = id; row.communityId = communityId; row.senderUserId = senderId; row.body = "hi"; row.createdAt = Instant.EPOCH;
        dao.insertMessage(row);
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void freshInstallHasOnlyTheUsersOwnProfile() {
        SocialDatabase fresh = Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(), SocialDatabase.class)
                .allowMainThreadQueries()
                .build();
        new RoomSocialRepository(fresh, fresh.socialDao()).ensureProfile(ME, "Me", "@me");
        assertNotNull(fresh.socialDao().getUser(ME));
        assertEquals(1, fresh.socialDao().userCount());
        assertNull(fresh.socialDao().getCommunity("community-running"));
        fresh.close();
    }

    @Test
    public void sampleDataFromEarlierBuildsIsPurged() {
        // The fixtures use the same ids the old seed did; opening the app again removes them.
        repository.ensureProfile(ME, "Me", "@me");
        assertNull(dao.getUser("user-maya"));
        assertNull(dao.getCommunity("community-running"));
        assertEquals(0, dao.messageCount("community-running"));
        assertNotNull(dao.getUser(ME));
    }

    @Test
    public void memberCanPostAndChatOnlyWhenCommunityAllowsIt() {
        int runningPosts = dao.postCount("community-running");
        int runningMessages = dao.messageCount("community-running");
        assertEquals(SocialResult.SUCCESS,
                repository.createPost(ME, "community-running", "  A steady five kilometres today.  "));
        assertEquals(SocialResult.SUCCESS,
                repository.sendMessage(ME, "community-running", "See everyone at sunrise."));
        assertEquals(runningPosts + 1, dao.postCount("community-running"));
        assertEquals(runningMessages + 1, dao.messageCount("community-running"));

        assertEquals(SocialResult.SUCCESS, repository.joinCommunity(ME, "community-reading"));
        assertEquals(SocialResult.PERMISSION_DENIED,
                repository.sendMessage(ME, "community-reading", "This must be rejected."));
        assertEquals(0, dao.messageCount("community-reading"));
    }

    @Test
    public void nonMemberCannotPostOrReadWriteThroughMutationApi() {
        assertEquals(SocialResult.NOT_MEMBER,
                repository.createPost(ME, "community-focus", "Not joined yet."));
        assertEquals(SocialResult.NOT_MEMBER,
                repository.sendMessage(ME, "community-focus", "Not joined yet."));
    }

    @Test
    public void ownerControlsPermissionsAndRoles() {
        assertEquals(SocialResult.PERMISSION_DENIED,
                repository.updatePermissions(ME, "community-running", new CommunityPermissions(false, false, false)));

        assertEquals(SocialResult.SUCCESS,
                repository.updatePermissions("user-maya", "community-running", new CommunityPermissions(false, false, false)));
        assertEquals(SocialResult.PERMISSION_DENIED,
                repository.createPost(ME, "community-running", "Posting has been disabled."));
        assertEquals(SocialResult.PERMISSION_DENIED,
                repository.sendMessage(ME, "community-running", "Chat has been disabled."));

        assertEquals(SocialResult.SUCCESS,
                repository.setRole("user-maya", "community-running", ME, MemberRole.ADMIN));
        assertEquals(MemberRole.ADMIN.key(), dao.getMember("community-running", ME).role);
        assertEquals(SocialResult.SUCCESS,
                repository.sendMessage(ME, "community-running", "Admins retain moderation access."));
        assertEquals(SocialResult.PERMISSION_DENIED,
                repository.setRole(ME, "community-running", "user-nina", MemberRole.ADMIN));
        assertEquals(SocialResult.PERMISSION_DENIED,
                repository.setRole("user-maya", "community-running", "user-nina", MemberRole.OWNER));
    }

    @Test
    public void followingJoiningLeavingAndOwnerSafetyArePersistent() {
        assertEquals(SocialResult.SUCCESS, repository.toggleFollow(ME, "user-maya"));
        assertEquals(1, dao.hasFollow(ME, "user-maya"));
        assertEquals(SocialResult.SUCCESS, repository.toggleFollow(ME, "user-maya"));
        assertEquals(0, dao.hasFollow(ME, "user-maya"));

        assertEquals(SocialResult.SUCCESS, repository.joinCommunity(ME, "community-focus"));
        assertNotNull(dao.getMember("community-focus", ME));
        assertEquals(SocialResult.SUCCESS, repository.leaveCommunity(ME, "community-focus"));
        assertEquals(null, dao.getMember("community-focus", ME));
        assertEquals(SocialResult.PERMISSION_DENIED,
                repository.leaveCommunity("user-arjun", "community-focus"));
    }

    @Test
    public void validatesAndSanitizesUntrustedInput() {
        assertEquals(SocialResult.INVALID_INPUT, repository.createCommunity(ME, "x", "too short"));
        assertEquals(SocialResult.INVALID_INPUT, repository.createPost(ME, "community-running", "   "));
        assertEquals(SocialResult.INVALID_INPUT, repository.toggleFollow(ME, ME));
        assertEquals(SocialResult.SUCCESS,
                repository.updateProfile(ME, "  Snow   Walker  ", "  Consistency   wins.  "));
        assertEquals("Snow Walker", dao.getUser(ME).displayName);
        assertEquals("Consistency wins.", dao.getUser(ME).bio);
    }
}
