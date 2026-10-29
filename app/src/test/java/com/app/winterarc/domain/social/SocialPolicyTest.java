package com.app.winterarc.domain.social;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SocialPolicyTest {
    private final CommunityPermissions locked = new CommunityPermissions(false, false, false);
    private final CommunityPermissions open = new CommunityPermissions(true, true, true);

    @Test
    public void ownerCanPerformEveryActionRegardlessOfMemberDefaults() {
        for (CommunityAction action : CommunityAction.values()) {
            assertTrue(action.name(), SocialPolicy.can(MemberRole.OWNER, locked, action));
        }
    }

    @Test
    public void adminCanModerateButCannotChangeOwnerPermissions() {
        assertTrue(SocialPolicy.can(MemberRole.ADMIN, locked, CommunityAction.POST));
        assertTrue(SocialPolicy.can(MemberRole.ADMIN, locked, CommunityAction.CHAT));
        assertTrue(SocialPolicy.can(MemberRole.ADMIN, locked, CommunityAction.INVITE));
        assertTrue(SocialPolicy.can(MemberRole.ADMIN, locked, CommunityAction.MANAGE_MEMBERS));
        assertFalse(SocialPolicy.can(MemberRole.ADMIN, locked, CommunityAction.MANAGE_PERMISSIONS));
    }

    @Test
    public void memberCapabilitiesFollowCommunitySettings() {
        assertFalse(SocialPolicy.can(MemberRole.MEMBER, locked, CommunityAction.POST));
        assertFalse(SocialPolicy.can(MemberRole.MEMBER, locked, CommunityAction.CHAT));
        assertFalse(SocialPolicy.can(MemberRole.MEMBER, locked, CommunityAction.INVITE));
        assertTrue(SocialPolicy.can(MemberRole.MEMBER, open, CommunityAction.POST));
        assertTrue(SocialPolicy.can(MemberRole.MEMBER, open, CommunityAction.CHAT));
        assertTrue(SocialPolicy.can(MemberRole.MEMBER, open, CommunityAction.INVITE));
        assertFalse(SocialPolicy.can(MemberRole.MEMBER, open, CommunityAction.MANAGE_MEMBERS));
        assertFalse(SocialPolicy.can(null, open, CommunityAction.CHAT));
    }

    @Test
    public void onlyOwnerCanPromoteAndOwnershipCannotBeReassigned() {
        assertTrue(SocialPolicy.canAssignRole(MemberRole.OWNER, MemberRole.MEMBER, MemberRole.ADMIN));
        assertTrue(SocialPolicy.canAssignRole(MemberRole.OWNER, MemberRole.ADMIN, MemberRole.MEMBER));
        assertFalse(SocialPolicy.canAssignRole(MemberRole.ADMIN, MemberRole.MEMBER, MemberRole.ADMIN));
        assertFalse(SocialPolicy.canAssignRole(MemberRole.OWNER, MemberRole.OWNER, MemberRole.MEMBER));
        assertFalse(SocialPolicy.canAssignRole(MemberRole.OWNER, MemberRole.MEMBER, MemberRole.OWNER));
    }
}
