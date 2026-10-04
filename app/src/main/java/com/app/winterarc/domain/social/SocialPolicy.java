package com.app.winterarc.domain.social;

/** One source of truth for community RBAC. Repository mutations and UI both call this policy. */
public final class SocialPolicy {
    private SocialPolicy() {}

    public static boolean can(MemberRole role, CommunityPermissions permissions, CommunityAction action) {
        if (role == null) return false;
        if (role == MemberRole.OWNER) return true;
        if (role == MemberRole.ADMIN) return action != CommunityAction.MANAGE_PERMISSIONS;
        return switch (action) {
            case POST -> permissions.membersCanPost();
            case CHAT -> permissions.membersCanChat();
            case INVITE -> permissions.membersCanInvite();
            case MANAGE_MEMBERS, MANAGE_PERMISSIONS -> false;
        };
    }

    public static boolean canAssignRole(MemberRole actor, MemberRole currentTarget, MemberRole requested) {
        return actor == MemberRole.OWNER && currentTarget != MemberRole.OWNER && requested != MemberRole.OWNER;
    }
}
